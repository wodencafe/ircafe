package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSessionOperationsTest {
  private final Map<String, QuasselCoreSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> reasons = new ConcurrentHashMap<>();
  private final List<String> lookups = new CopyOnWriteArrayList<>();
  private final List<Socket> sockets = new CopyOnWriteArrayList<>();
  private final QuasselCoreInputCoordinator inputs = mock(QuasselCoreInputCoordinator.class);
  private final QuasselCoreSessionQueries queries =
      new QuasselCoreSessionQueries(
          serverId -> {
            lookups.add("session:" + serverId);
            return sessions.get(serverId);
          },
          serverId -> {
            lookups.add("reason:" + serverId);
            return reasons.get(serverId);
          });
  private final QuasselCoreSessionOperations operations =
      new QuasselCoreSessionOperations(queries, inputs);

  @AfterEach
  void closeSockets() throws Exception {
    for (Socket socket : sockets) socket.close();
  }

  @Test
  void actionIsDeferredAndUsesTheCurrentSessionOnIoForEachSubscription() {
    var observed = new CopyOnWriteArrayList<QuasselCoreSession>();
    var thread = new AtomicReference<Thread>();
    Completable command =
        operations.withEstablishedSession(
            " core ",
            "network operation",
            (serverId, session) -> {
              assertEquals("core", serverId);
              thread.set(Thread.currentThread());
              observed.add(session);
            });
    assertTrue(lookups.isEmpty());
    assertTrue(observed.isEmpty());
    var first = establish();
    complete(command);
    assertNotSame(Thread.currentThread(), thread.get());
    var second = establish();
    complete(command);
    assertEquals(List.of(first, second), observed);
    assertEquals(List.of("session:core", "session:core"), lookups);
    verifyNoInteractions(inputs);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankServerIdFailsAtSubscriptionBeforeLookupAndBeforeRawValidation(String serverId) {
    var invoked = new AtomicInteger();
    Completable command =
        operations.withEstablishedSession(
            serverId, "test", (sid, session) -> invoked.incrementAndGet());
    assertTrue(lookups.isEmpty());
    command
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(QuasselCoreSessionOperationsTest::blankId);
    operations
        .sendRaw(serverId, null)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(QuasselCoreSessionOperationsTest::blankId);
    assertTrue(lookups.isEmpty());
    assertEquals(0, invoked.get());
    verifyNoInteractions(inputs);
  }

  @Test
  void unavailableSessionCarriesTheOperationServerAndLatestReasonWithoutRunningAction() {
    reasons.put("core", "old reason");
    Completable command =
        operations.withEstablishedSession(
            " core ", "update network", (sid, session) -> fail("action must not run"));
    reasons.put("core", " setup required ");
    command
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error -> {
              assertInstanceOf(BackendNotAvailableException.class, error);
              var unavailable = (BackendNotAvailableException) error;
              assertEquals("quassel-core", unavailable.backendId());
              assertEquals("update network", unavailable.operation());
              assertEquals("core", unavailable.serverId());
              assertEquals("setup required", unavailable.detail());
              return true;
            });
    verifyNoInteractions(inputs);
  }

  @Test
  void directSessionRequirementRejectsIncompleteHandshakeAndMissingSocket() {
    var session = establish();
    session.phase.set(QuasselSessionPhase.AUTHENTICATING);
    var failure =
        assertThrows(
            BackendNotAvailableException.class,
            () -> operations.requireEstablishedSession(" core ", "history request"));
    assertEquals("history request", failure.operation());
    assertEquals(
        "Quassel protocol negotiated, but login/session handshake is not complete",
        failure.detail());
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    session.socketRef.set(null);
    assertThrows(
        BackendNotAvailableException.class,
        () -> operations.requireEstablishedSession("core", "lag probe"));
    verifyNoInteractions(inputs);
  }

  @Test
  void actionFailureIsReportedWithoutAnAutomaticRetry() {
    establish();
    var failure = new IOException("write failed");
    var calls = new AtomicInteger();
    operations
        .withEstablishedSession(
            "core",
            "send typing",
            (sid, session) -> {
              calls.incrementAndGet();
              throw failure;
            })
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(failure);
    assertEquals(1, calls.get());
  }

  @Test
  void plannedInputIsComputedOnceAtInvocationAndWrittenOnIoAgainstTheCurrentSession()
      throws Exception {
    var plans = new AtomicInteger();
    var plannerThread = new AtomicReference<Thread>();
    var writerThread = new AtomicReference<Thread>();
    Completable command =
        operations.sendPlannedInput(
            " core ",
            () -> {
              plans.incrementAndGet();
              plannerThread.set(Thread.currentThread());
              return QuasselCoreUserInput.sendToChannel(" #room ", " hello ");
            });
    assertEquals(1, plans.get());
    assertSame(Thread.currentThread(), plannerThread.get());
    assertTrue(lookups.isEmpty());
    verifyNoInteractions(inputs);
    doAnswer(
            invocation -> {
              writerThread.set(Thread.currentThread());
              return null;
            })
        .when(inputs)
        .sendInput(any(), anyString(), anyInt(), anyString(), anyString());
    var first = establish();
    complete(command);
    var second = establish();
    complete(command);
    assertEquals(1, plans.get());
    assertNotSame(Thread.currentThread(), writerThread.get());
    verify(inputs).sendInput(first, "send message to channel", 0x02, "#room", "hello");
    verify(inputs).sendInput(second, "send message to channel", 0x02, "#room", "hello");
  }

  @Test
  void invalidPlannedInputReturnsAnErrorBeforeBlankServerValidationOrLookup() {
    Completable command =
        assertDoesNotThrow(
            () -> operations.sendPlannedInput(" ", () -> QuasselCoreUserInput.changeNick(" ")));
    command
        .test()
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && "new nick is blank".equals(error.getMessage()));
    assertTrue(lookups.isEmpty());
    verifyNoInteractions(inputs);
  }

  @Test
  void onlyIllegalArgumentPlannerFailuresAreConvertedIntoCompletableErrors() {
    var invalid = new IllegalArgumentException("invalid plan");
    Completable command =
        assertDoesNotThrow(
            () ->
                operations.sendPlannedInput(
                    "core",
                    () -> {
                      throw invalid;
                    }));
    command.test().assertError(invalid);
    var unexpected = new IllegalStateException("planner failed");
    assertSame(
        unexpected,
        assertThrows(
            IllegalStateException.class,
            () ->
                operations.sendPlannedInput(
                    "core",
                    () -> {
                      throw unexpected;
                    })));
    assertTrue(lookups.isEmpty());
    verifyNoInteractions(inputs);
  }

  @Test
  void plannedInputWriteFailureIsPreservedAndDoesNotRepeatTheWrite() throws Exception {
    var session = establish();
    var failure = new IOException("input write failed");
    doThrow(failure).when(inputs).sendInput(session, "join channel", 0x01, "", "/JOIN #room");
    operations
        .sendPlannedInput("core", () -> QuasselCoreUserInput.joinChannel("#room"))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(failure);
    verify(inputs).sendInput(session, "join channel", 0x01, "", "/JOIN #room");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankRawLineFailsOnSubscriptionBeforeSessionLookup(String rawLine) {
    Completable command = assertDoesNotThrow(() -> operations.sendRaw("core", rawLine));
    assertTrue(lookups.isEmpty());
    command
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && "raw line is blank".equals(error.getMessage()));
    assertTrue(lookups.isEmpty());
    verifyNoInteractions(inputs);
  }

  @ParameterizedTest
  @ValueSource(strings = {"WHOIS alice\rPRIVMSG #room :oops", "WHOIS alice\nPRIVMSG #room :oops"})
  void rawLineWithEmbeddedNewlineFailsBeforeSessionLookup(String rawLine) {
    operations
        .sendRaw("core", rawLine)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && "raw line contains CR/LF".equals(error.getMessage()));
    assertTrue(lookups.isEmpty());
    verifyNoInteractions(inputs);
  }

  @Test
  void rawInputUsesUnicodeStripAndPreservesTheCommandEnvelopeWhenSentOnIo() throws Exception {
    var thread = new AtomicReference<Thread>();
    String raw = "PRIVMSG #room :\u0001ACTION waves\u0001";
    Completable command = operations.sendRaw(" core ", "\u2003" + raw + "\u2003");
    assertTrue(lookups.isEmpty());
    verifyNoInteractions(inputs);
    var session = establish();
    doAnswer(
            invocation -> {
              thread.set(Thread.currentThread());
              return null;
            })
        .when(inputs)
        .sendRaw(session, "send raw", raw);
    complete(command);
    verify(inputs).sendRaw(session, "send raw", raw);
    assertNotSame(Thread.currentThread(), thread.get());
  }

  @Test
  void rawInputWriteFailureIsPropagatedWithoutRetry() throws Exception {
    var session = establish();
    var failure = new IOException("raw write failed");
    doThrow(failure).when(inputs).sendRaw(session, "send raw", "WHOIS alice");
    operations
        .sendRaw("core", "WHOIS alice")
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(failure);
    verify(inputs).sendRaw(session, "send raw", "WHOIS alice");
  }

  private QuasselCoreSession establish() {
    var session =
        new QuasselCoreSession(
            "core",
            "alice",
            "core.example",
            4242,
            mock(QuasselCoreSignalProxySender.class),
            identity -> {},
            event -> {});
    var socket = new Socket();
    sockets.add(socket);
    session.socketRef.set(socket);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    sessions.put("core", session);
    return session;
  }

  private static boolean blankId(Throwable error) {
    return error instanceof IllegalArgumentException
        && "server id is blank".equals(error.getMessage());
  }

  private static void complete(Completable command) {
    command.test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors();
  }
}
