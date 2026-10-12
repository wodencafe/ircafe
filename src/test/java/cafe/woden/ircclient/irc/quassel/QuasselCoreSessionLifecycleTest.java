package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSessionLifecycleTest {
  private final Map<String, QuasselCoreSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> reasons = new ConcurrentHashMap<>();
  private final List<String> trace = new ArrayList<>();
  private final List<ServerIrcEvent> events = new ArrayList<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean();
  private final QuasselCoreReconnectCoordinator reconnects =
      mock(QuasselCoreReconnectCoordinator.class);
  private final Consumer<ServerIrcEvent> publish =
      event -> {
        assertEquals("core", event.serverId());
        assertNotNull(reasons.get("core"));
        trace.add(event.event() instanceof IrcEvent.Error ? "error" : "disconnected");
        events.add(event);
      };
  private final QuasselCoreSessionEventPublisher sessionEvents =
      new QuasselCoreSessionEventPublisher(publish);
  private final QuasselCoreSessionLifecycle lifecycle =
      new QuasselCoreSessionLifecycle(
          sessions,
          (serverId, reason) -> {
            trace.add("availability");
            reasons.put(serverId, reason);
          },
          shuttingDown::get,
          reconnects,
          sessionEvents,
          publish);
  private final QuasselCoreSession session = session();
  private final Socket socket = mock(Socket.class);

  @Test
  void explicitCloseSetsIntentThenCancelsReadinessAndReadTaskBeforeSocketAndStateCleanup()
      throws Exception {
    seed();
    session.socketRef.set(socket);
    session.readiness.fallbackTask.set(
        Disposable.fromRunnable(
            () -> {
              assertTrue(session.closeRequested.get());
              assertEquals("user close", session.closeReason.get());
              assertSame(socket, session.socketRef.get());
              assertNotNull(session.readLoopTask.get());
              trace.add("readiness");
            }));
    session.readLoopTask.set(
        Disposable.fromRunnable(
            () -> {
              assertNull(session.readiness.fallbackTask.get());
              assertSame(socket, session.socketRef.get());
              assertFalse(session.buffers.values().isEmpty());
              trace.add("read task");
            }));
    doAnswer(
            invocation -> {
              assertNull(session.socketRef.get());
              assertNull(session.readLoopTask.get());
              assertFalse(session.buffers.values().isEmpty());
              trace.add("socket");
              return null;
            })
        .when(socket)
        .close();
    lifecycle.closeSession(session, " user close ", true);
    assertEquals(
        List.of("readiness", "read task", "socket", "availability", "disconnected"), trace);
    assertTrue(session.buffers.values().isEmpty());
    assertEquals(-1, session.history.timestampForMsgId("#room", 99));
    assertFalse(session.features.hasObservedCapabilities());
    assertSame(session, sessions.get("core"));
    assertEquals("user close", reasons.get("core"));
    assertEquals(
        "user close",
        assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event()).reason());
    verifyNoInteractions(reconnects);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankCloseReasonUsesTheDefaultAndSilentCloseStillUpdatesAvailability(String reason) {
    lifecycle.closeSession(session, reason, false);
    assertTrue(session.closeRequested.get());
    assertEquals("Client requested disconnect", session.closeReason.get());
    assertEquals("Client requested disconnect", reasons.get("core"));
    assertTrue(events.isEmpty());
    verifyNoInteractions(reconnects);
  }

  @Test
  void closeSurvivesTaskAndSocketFailuresAndEmitsOnlyOneDisconnect() throws Exception {
    seed();
    session.socketRef.set(socket);
    session.readiness.fallbackTask.set(
        Disposable.fromRunnable(
            () -> {
              throw new IllegalStateException("readiness cancellation failed");
            }));
    var task =
        Disposable.fromRunnable(
            () -> {
              throw new IllegalStateException("read cancellation failed");
            });
    session.readLoopTask.set(task);
    doThrow(new IOException("socket close failed")).when(socket).close();
    assertDoesNotThrow(() -> lifecycle.closeSession(session, "close", true));
    lifecycle.closeSession(session, "close", true);
    lifecycle.finishReadLoop(session);
    assertNull(session.socketRef.get());
    assertNull(session.readLoopTask.get());
    assertNull(session.readiness.fallbackTask.get());
    assertTrue(task.isDisposed());
    assertTrue(session.buffers.values().isEmpty());
    assertFalse(sessions.containsKey("core"));
    assertEquals(1, events.size());
    verify(socket).close();
    verifyNoInteractions(reconnects);
  }

  @Test
  void nullCloseAndReconnectRequestsAreNoOps() {
    lifecycle.closeSession(null, "close", true);
    lifecycle.scheduleReconnectIfEligible(null, "failed");
    QuasselCoreSessionLifecycle.closeQuietly(null);
    assertTrue(trace.isEmpty());
    assertTrue(reasons.isEmpty());
    verifyNoInteractions(reconnects);
  }

  @Test
  void readCompletionClosesReadinessAndSocketAndRemovesOnlyThatSessionWithoutClearingObservations()
      throws Exception {
    seed();
    session.socketRef.set(socket);
    var task = Disposable.empty();
    session.readLoopTask.set(task);
    session.readiness.fallbackTask.set(Disposable.fromRunnable(() -> trace.add("readiness")));
    lifecycle.finishReadLoop(session);
    assertEquals(List.of("readiness"), trace);
    assertNull(session.socketRef.get());
    assertNull(session.readiness.fallbackTask.get());
    assertFalse(sessions.containsKey("core"));
    assertFalse(session.closeRequested.get());
    assertSame(task, session.readLoopTask.get());
    assertFalse(task.isDisposed());
    assertFalse(session.buffers.values().isEmpty());
    assertTrue(reasons.isEmpty());
    assertTrue(events.isEmpty());
    verify(socket).close();
    verifyNoInteractions(reconnects);
  }

  @Test
  void lateReadCompletionCannotRemoveAReplacementSessionOrCloseItsSocket() throws Exception {
    session.socketRef.set(socket);
    var replacement = session();
    Socket replacementSocket = mock(Socket.class);
    replacement.socketRef.set(replacementSocket);
    sessions.put("core", replacement);
    lifecycle.finishReadLoop(session);
    assertSame(replacement, sessions.get("core"));
    assertSame(replacementSocket, replacement.socketRef.get());
    assertFalse(replacement.closeRequested.get());
    verify(socket).close();
    verifyNoInteractions(replacementSocket, reconnects);
  }

  @Test
  void requestedReadCompletionNormalizesReasonAndPublishesAfterSessionRemoval() throws Exception {
    sessions.put("core", session);
    session.socketRef.set(socket);
    session.closeRequested.set(true);
    session.closeReason.set(" finished close ");
    lifecycle.finishReadLoop(session);
    assertFalse(sessions.containsKey("core"));
    assertEquals(List.of("availability", "disconnected"), trace);
    assertEquals("finished close", reasons.get("core"));
    assertEquals(
        "finished close",
        assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event()).reason());
    verify(socket).close();
    verifyNoInteractions(reconnects);
  }

  @Test
  void readFailureClosesReadinessThenRecordsReasonEmitsErrorDisconnectAndSchedulesOnce() {
    seed();
    session.socketRef.set(socket);
    session.readiness.fallbackTask.set(Disposable.fromRunnable(() -> trace.add("readiness")));
    var cause = new IOException("bad frame");
    doAnswer(
            invocation -> {
              assertTrue(session.reconnectScheduled.get());
              assertTrue(session.disconnectedEmitted.get());
              assertEquals("Connection error", reasons.get("core"));
              trace.add("reconnect");
              return null;
            })
        .when(reconnects)
        .schedule("core", "Connection error");
    lifecycle.handleReadFailure(
        session, new QuasselCoreReadLoop.Failure("Connection error", cause));
    assertEquals(List.of("readiness", "availability", "error", "disconnected", "reconnect"), trace);
    var error = assertInstanceOf(IrcEvent.Error.class, events.getFirst().event());
    assertSame(cause, error.cause());
    assertSame(socket, session.socketRef.get());
    assertSame(session, sessions.get("core"));
    assertFalse(session.buffers.values().isEmpty());
    assertFalse(session.closeRequested.get());
    lifecycle.scheduleReconnectIfEligible(session, "duplicate failure");
    verify(reconnects).schedule("core", "Connection error");
    verifyNoMoreInteractions(reconnects);
    verifyNoInteractions(socket);
  }

  @Test
  void eofFailureEmitsNoErrorAndReadCompletionDoesNotDuplicateTheDisconnect() throws Exception {
    sessions.put("core", session);
    session.socketRef.set(socket);
    lifecycle.handleReadFailure(
        session, new QuasselCoreReadLoop.Failure("connection closed", null));
    lifecycle.finishReadLoop(session);
    assertEquals(1, events.size());
    assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event());
    assertEquals("connection closed", reasons.get("core"));
    assertFalse(sessions.containsKey("core"));
    assertEquals(List.of("availability", "disconnected"), trace);
    verify(reconnects).schedule("core", "connection closed");
    verify(socket).close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"shutdown", "requested"})
  void readFailureStillReportsDiagnosticEventsButDoesNotReconnectDuringTeardown(String state) {
    if ("shutdown".equals(state)) shuttingDown.set(true);
    else session.closeRequested.set(true);
    lifecycle.handleReadFailure(
        session, new QuasselCoreReadLoop.Failure("read failed", new IOException("closed")));
    assertEquals(2, events.size());
    assertInstanceOf(IrcEvent.Error.class, events.getFirst().event());
    assertInstanceOf(IrcEvent.Disconnected.class, events.getLast().event());
    assertFalse(session.reconnectScheduled.get());
    verifyNoInteractions(reconnects);
  }

  @Test
  void reconnectEligibilityReadsShutdownAtCallTimeAndClaimsOnlyOneSchedule() {
    shuttingDown.set(true);
    lifecycle.scheduleReconnectIfEligible(session, "shutdown");
    assertFalse(session.reconnectScheduled.get());
    shuttingDown.set(false);
    session.closeRequested.set(true);
    lifecycle.scheduleReconnectIfEligible(session, "requested");
    assertFalse(session.reconnectScheduled.get());
    session.closeRequested.set(false);
    lifecycle.scheduleReconnectIfEligible(session, "first failure");
    lifecycle.scheduleReconnectIfEligible(session, "second failure");
    assertTrue(session.reconnectScheduled.get());
    verify(reconnects).schedule("core", "first failure");
    verifyNoMoreInteractions(reconnects);
  }

  private void seed() {
    sessions.put("core", session);
    session.buffers.merge(new QuasselCoreDatastreamCodec.BufferInfoValue(1, 1, 0x02, -1, "#room"));
    session.history.observe("#room", 99, Instant.ofEpochSecond(1_700_000_000L));
    session.features.observeCapabilities(1, Map.of("capsEnabled", List.of("message-tags")));
  }

  private static QuasselCoreSession session() {
    var session =
        new QuasselCoreSession(
            "core",
            "alice",
            "host",
            4242,
            (socket, operation) -> fail("lifecycle must not send"),
            id -> {},
            event -> {});
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    return session;
  }
}
