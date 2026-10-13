package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreConnectionRequestsTest {
  private final ServerCatalog catalog = mock(ServerCatalog.class);
  private final QuasselCoreReconnectCoordinator reconnects =
      mock(QuasselCoreReconnectCoordinator.class);
  private final Map<String, QuasselCoreSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> reasons = new ConcurrentHashMap<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean();
  private final List<String> trace = new CopyOnWriteArrayList<>();
  private final List<QuasselCoreSession> created = new CopyOnWriteArrayList<>();
  private final List<QuasselCoreSession> admitted = new CopyOnWriteArrayList<>();
  private final IrcProperties.Server server =
      IrcPropertiesTestFixtures.serverBuilder("core").port(4242).build();
  private final BiFunction<String, IrcProperties.Server, QuasselCoreSession> factory =
      (sid, configured) -> {
        assertEquals("core", sid);
        assertSame(server, configured);
        trace.add("create");
        var session = session(sid);
        created.add(session);
        return session;
      };
  private final BiConsumer<IrcProperties.Server, QuasselCoreSession> start =
      (configured, session) -> {
        assertSame(server, configured);
        assertSame(session, sessions.get("core"));
        assertEquals("Quassel transport is connecting", reasons.get("core"));
        trace.add("start");
        admitted.add(session);
      };
  private final QuasselCoreConnectionRequests requests = requests(factory, start);

  @BeforeEach
  void recordBookkeepingAndCatalogLookup() {
    doAnswer(
            invocation -> {
              trace.add("cancel");
              return null;
            })
        .when(reconnects)
        .cancel("core", false);
    doAnswer(
            invocation -> {
              trace.add("reset");
              return null;
            })
        .when(reconnects)
        .reset("core");
    when(catalog.require("core"))
        .thenAnswer(
            invocation -> {
              assertEquals("Quassel transport is connecting", reasons.get("core"));
              trace.add("lookup");
              return server;
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void registersBeforeEstablishmentAndOnlyManualRequestsResetAttempts(boolean manual) {
    requests.connect(" core ", manual);
    assertEquals(
        manual
            ? List.of("cancel", "reset", "availability", "lookup", "create", "start")
            : List.of("cancel", "availability", "lookup", "create", "start"),
        trace);
    assertEquals(1, sessions.size());
    assertEquals(1, created.size());
    assertEquals(created, admitted);
    assertSame(created.getFirst(), sessions.get("core"));
    verify(reconnects).cancel("core", false);
    if (manual) verify(reconnects).reset("core");
    verifyNoMoreInteractions(reconnects);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "core"})
  void shutdownRejectsRequestsBeforeValidationOrBookkeeping(String serverId) {
    shuttingDown.set(true);
    var existing = session("core");
    sessions.put("core", existing);
    requests.connect(serverId, true);
    assertSame(existing, sessions.get("core"));
    assertTrue(trace.isEmpty());
    assertTrue(reasons.isEmpty());
    verifyNoInteractions(catalog, reconnects);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankServerIdFailsBeforeBookkeepingOrConstruction(String serverId) {
    var failure =
        assertThrows(IllegalArgumentException.class, () -> requests.connect(serverId, true));
    assertEquals(SERVER_ID_BLANK, failure.getMessage());
    assertTrue(trace.isEmpty());
    assertTrue(sessions.isEmpty());
    assertTrue(reasons.isEmpty());
    verifyNoInteractions(catalog, reconnects);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void duplicateRequestsStillUpdateBookkeepingAndValidateTheCatalog(boolean manual) {
    var existing = session("core");
    existing.closeRequested.set(true);
    sessions.put("core", existing);
    requests.connect(" core ", manual);
    assertEquals(
        manual
            ? List.of("cancel", "reset", "availability", "lookup")
            : List.of("cancel", "availability", "lookup"),
        trace);
    assertSame(existing, sessions.get("core"));
    assertTrue(existing.closeRequested.get());
    assertTrue(created.isEmpty());
    assertTrue(admitted.isEmpty());
    verify(catalog).require("core");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void catalogFailurePropagatesEvenWhenASessionIsAlreadyRegistered(boolean existingSession) {
    var existing = session("core");
    if (existingSession) sessions.put("core", existing);
    var failure = new IllegalArgumentException("server removed");
    doThrow(failure).when(catalog).require("core");
    assertSame(
        failure,
        assertThrows(IllegalArgumentException.class, () -> requests.connect("core", true)));
    assertEquals(List.of("cancel", "reset", "availability"), trace);
    assertEquals("Quassel transport is connecting", reasons.get("core"));
    assertSame(existingSession ? existing : null, sessions.get("core"));
    assertTrue(created.isEmpty());
    assertTrue(admitted.isEmpty());
  }

  @Test
  void losingRegistrationDoesNotStartEstablishmentOrReplaceTheWinningSession() {
    var winner = session("core");
    var competing =
        requests(
            (sid, configured) -> {
              var next = factory.apply(sid, configured);
              sessions.put(sid, winner);
              return next;
            },
            start);
    competing.connect("core", true);
    assertSame(winner, sessions.get("core"));
    assertEquals(1, created.size());
    assertTrue(admitted.isEmpty());
    assertNull(created.getFirst().socketRef.get());
    assertNull(created.getFirst().readLoopTask.get());
    assertEquals(List.of("cancel", "reset", "availability", "lookup", "create"), trace);
  }

  @Test
  void concurrentRequestsAdmitOnlyOneSession() throws Exception {
    var constructed = new CyclicBarrier(2);
    var concurrent =
        requests(
            (sid, configured) -> {
              var next = factory.apply(sid, configured);
              assertDoesNotThrow(() -> constructed.await(5, TimeUnit.SECONDS));
              return next;
            },
            start);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> concurrent.connect("core", true));
      var second = executor.submit(() -> concurrent.connect("core", false));
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
    }
    assertEquals(2, created.size());
    assertEquals(1, admitted.size());
    assertEquals(1, sessions.size());
    assertSame(admitted.getFirst(), sessions.get("core"));
    verify(reconnects, times(2)).cancel("core", false);
    verify(reconnects).reset("core");
    verifyNoMoreInteractions(reconnects);
  }

  @Test
  void constructionFailurePropagatesWithoutRegisteringASession() {
    var failure = new IllegalStateException("construction failed");
    var rejecting =
        requests(
            (sid, configured) -> {
              throw failure;
            },
            start);
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> rejecting.connect("core", true)));
    assertEquals(List.of("cancel", "reset", "availability", "lookup"), trace);
    assertTrue(sessions.isEmpty());
    assertTrue(admitted.isEmpty());
  }

  @Test
  void establishmentCallbackFailurePropagatesAfterRegistration() {
    var failure = new IllegalStateException("scheduling failed");
    var rejecting =
        requests(
            factory,
            (configured, session) -> {
              assertSame(session, sessions.get("core"));
              throw failure;
            });
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> rejecting.connect("core", true)));
    assertEquals(List.of("cancel", "reset", "availability", "lookup", "create"), trace);
    assertSame(created.getFirst(), sessions.get("core"));
    assertTrue(admitted.isEmpty());
  }

  private QuasselCoreConnectionRequests requests(
      BiFunction<String, IrcProperties.Server, QuasselCoreSession> createSession,
      BiConsumer<IrcProperties.Server, QuasselCoreSession> startEstablishment) {
    return new QuasselCoreConnectionRequests(
        catalog,
        sessions,
        (sid, reason) -> {
          trace.add("availability");
          reasons.put(sid, reason);
        },
        shuttingDown::get,
        reconnects,
        createSession,
        startEstablishment);
  }

  private QuasselCoreSession session(String serverId) {
    return new QuasselCoreSession(
        serverId,
        "alice",
        server.host(),
        server.port(),
        (socket, operation) -> fail("admission must not write to the transport"),
        ignored -> {},
        ignored -> {});
  }
}
