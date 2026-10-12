package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryRuntimeSupport.Plan;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandOperation;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreHistoryRequestCoordinatorTest {
  private static final String TARGET = "#room{net:work}";
  private static final Instant BOUNDARY = Instant.ofEpochSecond(2);
  private final QuasselIrcv3RuntimeSupport runtime =
      spy(new QuasselIrcv3RuntimeSupport(Ircv3RuntimeCatalogs.applicationClasspath()));
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final OutputStream output = mock(OutputStream.class);
  private final List<String> lookups = new CopyOnWriteArrayList<>();
  private final List<Socket> writes = new CopyOnWriteArrayList<>();
  private final AtomicReference<Thread> worker = new AtomicReference<>();
  private final QuasselCoreSession original = session();
  private final AtomicReference<QuasselCoreSession> current = new AtomicReference<>(original);
  private final QuasselCoreTargetResolver targets =
      new QuasselCoreTargetResolver(
          new QuasselCoreTargetResolver.SessionPort() {
            @Override
            public void observeNetwork(QuasselCoreSession session, int networkId) {}

            @Override
            public boolean isSelfNick(QuasselCoreSession session, String nick, int networkId) {
              return session.nicks.isSelf(nick, networkId);
            }
          });
  private final QuasselCoreHistoryRequestCoordinator coordinator =
      new QuasselCoreHistoryRequestCoordinator(
          runtime,
          targets,
          (serverId, operation) -> {
            lookups.add(serverId + ":" + operation);
            worker.set(Thread.currentThread());
            QuasselCoreSession session = current.get();
            if (session == null) {
              throw new BackendNotAvailableException(
                  IrcProperties.Server.Backend.QUASSEL_CORE, operation, serverId, "offline");
            }
            return session;
          });

  @ParameterizedTest
  @CsvSource({
    "TIMESTAMP, request chat history, -1, 200, 25",
    "BEFORE, request chat history, -1, 20, 25",
    "LATEST, request latest chat history, 21, -1, 200",
    "BETWEEN, request bounded chat history, 11, 30, 25",
    "AROUND, request surrounding chat history, 8, 32, 25"
  })
  void allModesSendNativeBacklogForTheQualifiedNetworkOnAnIoWorker(
      String mode, String operation, int first, int last, int limit) throws Exception {
    original.history.observe("#room{net:home}", 100, BOUNDARY);
    original.history.observe(TARGET, 200, BOUNDARY);
    Completable request = request(mode);
    assertTrue(lookups.isEmpty());
    assertTrue(writes.isEmpty());
    verifyNoInteractions(runtime);
    Thread caller = Thread.currentThread();
    complete(request);
    assertNotSame(caller, worker.get());
    assertEquals(List.of("core:" + operation), lookups);
    assertEquals(List.of(original.socketRef.get()), writes);
    verifyBacklog(22, first, last, limit);
    verifyNoMoreInteractions(codec);
  }

  @Test
  void repeatedSubscriptionsResolveTheCurrentSessionAndItsCurrentHistory() throws Exception {
    Completable request = coordinator.requestBefore("core", TARGET, BOUNDARY, 25);
    original.history.observe(TARGET, 200, BOUNDARY);
    complete(request);
    QuasselCoreSession replacement = session();
    replacement.history.observe(TARGET, 300, BOUNDARY);
    current.set(replacement);
    complete(request);
    verifyBacklog(22, -1, 200, 25);
    verifyBacklog(22, -1, 300, 25);
    assertEquals(List.of(original.socketRef.get(), replacement.socketRef.get()), writes);
    assertEquals(2, lookups.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"TIMESTAMP", "SELECTOR"})
  void beforeFallbackTimestampIsChosenWhenSubscribed(String overload) {
    Completable request =
        "TIMESTAMP".equals(overload)
            ? coordinator.requestBefore("core", TARGET, (Instant) null, 25)
            : coordinator.requestBefore("core", TARGET, "", 25);
    verifyNoInteractions(runtime);
    Instant before = Instant.now();
    complete(request);
    Instant after = Instant.now();
    var timestamp = org.mockito.ArgumentCaptor.forClass(Instant.class);
    verify(runtime).chatHistoryBefore(eq(TARGET), eq(""), eq(25), timestamp.capture());
    assertFalse(timestamp.getValue().isBefore(before));
    assertFalse(timestamp.getValue().isAfter(after));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankServerFailsAfterRuntimePlanningWithoutSessionLookupOrWrite(String serverId) {
    coordinator
        .requestLatest(serverId, TARGET, "*", 25)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && "server id is blank".equals(error.getMessage()));
    verify(runtime).chatHistoryLatest(TARGET, "*", 25);
    assertTrue(lookups.isEmpty());
    assertTrue(writes.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "BEFORE, *, selector must be msgid=... or timestamp=...",
    "LATEST, msgid=abc, msgid selector must be numeric for Quassel backlog",
    "BETWEEN, msgid=0, msgid selector must be a positive integer",
    "AROUND, timestamp=bad, timestamp selector must be ISO-8601 (UTC)"
  })
  void invalidSelectorsFailWithoutSending(String mode, String selector, String message) {
    Completable request =
        switch (mode) {
          case "BEFORE" -> coordinator.requestBefore("core", TARGET, selector, 25);
          case "LATEST" -> coordinator.requestLatest("core", TARGET, selector, 25);
          case "BETWEEN" -> coordinator.requestBetween("core", TARGET, selector, "msgid=30", 25);
          case "AROUND" -> coordinator.requestAround("core", TARGET, selector, 25);
          default -> throw new AssertionError(mode);
        };
    request
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalArgumentException && message.equals(error.getMessage()));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
  }

  @Test
  void runtimePlanningFailurePrecedesServerValidationAndSessionLookup() {
    var error = new IllegalStateException("provider unavailable");
    doThrow(error).when(runtime).chatHistoryLatest(TARGET, "*", 25);
    Completable request = coordinator.requestLatest("", TARGET, "*", 25);
    verifyNoInteractions(runtime);
    request.test().awaitDone(5, TimeUnit.SECONDS).assertError(error);
    assertTrue(lookups.isEmpty());
    assertTrue(writes.isEmpty());
  }

  @Test
  void missingSessionPreservesBackendErrorContext() {
    current.set(null);
    coordinator
        .requestAround(" core ", TARGET, "msgid=20", 25)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof BackendNotAvailableException unavailable
                    && "core".equals(unavailable.serverId())
                    && "request surrounding chat history".equals(unavailable.operation())
                    && "offline".equals(unavailable.detail()));
    assertTrue(writes.isEmpty());
  }

  @Test
  void missingBufferReportsBackendUnavailabilityWithoutSending() {
    coordinator
        .requestLatest("core", "#missing{net:work}", "*", 25)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof BackendNotAvailableException unavailable
                    && "request latest chat history".equals(unavailable.operation())
                    && "target buffer '#missing' is not known yet".equals(unavailable.detail()));
    assertTrue(writes.isEmpty());
  }

  @Test
  void renderedPlanControlsSelectorsAndLimitOfTheNativeRequest() throws Exception {
    doReturn(
            new Plan(
                Ircv3OutboundCommandOperation.CHAT_HISTORY_LATEST,
                "CHATHISTORY LATEST " + TARGET + " msgid=40 17",
                TARGET,
                "msgid=40",
                "",
                17))
        .when(runtime)
        .chatHistoryLatest(TARGET, "*", 25);
    complete(coordinator.requestLatest("core", TARGET, "*", 25));
    verifyBacklog(22, 41, -1, 17);
  }

  @Test
  void nativeWriteFailurePropagatesWithoutRetry() throws Exception {
    var error = new IOException("write failed");
    doThrow(error)
        .when(codec)
        .writeSignalProxySync(
            eq(output), eq("BacklogManager"), eq(""), eq("requestBacklog"), anyList());
    coordinator
        .requestLatest("core", TARGET, "*", 25)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(error);
    assertEquals(1, writes.size());
    verifyBacklog(22, -1, -1, 25);
    verifyNoMoreInteractions(codec);
  }

  private Completable request(String mode) {
    return switch (mode) {
      case "TIMESTAMP" -> coordinator.requestBefore(" core ", TARGET, BOUNDARY, 25);
      case "BEFORE" -> coordinator.requestBefore(" core ", TARGET, "msgid=20", 25);
      case "LATEST" -> coordinator.requestLatest(" core ", TARGET, "msgid=20", 999);
      case "BETWEEN" -> coordinator.requestBetween(" core ", TARGET, "msgid=30", "msgid=10", 25);
      case "AROUND" -> coordinator.requestAround(" core ", TARGET, "msgid=20", 25);
      default -> throw new AssertionError(mode);
    };
  }

  private QuasselCoreSession session() {
    var session =
        new QuasselCoreSession(
            "core",
            "alice",
            "core.example",
            4242,
            (socket, operation) -> {
              writes.add(socket);
              operation.write(codec, output);
            },
            ignored -> {},
            ignored -> {});
    session.socketRef.set(new Socket());
    session.networks.observe(7, "Home");
    session.networks.observe(8, "Work");
    session.buffers.merge(new BufferInfoValue(11, 7, 2, -1, "#room"));
    session.buffers.merge(new BufferInfoValue(22, 8, 2, -1, "#room"));
    return session;
  }

  private void verifyBacklog(int bufferId, int first, int last, int limit) throws IOException {
    verify(codec)
        .writeSignalProxySync(
            output,
            "BacklogManager",
            "",
            "requestBacklog",
            List.of(
                new UserTypeValue("BufferId", bufferId),
                new UserTypeValue("MsgId", first),
                new UserTypeValue("MsgId", last),
                limit,
                0));
  }

  private static void complete(Completable request) {
    request.test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors();
  }
}
