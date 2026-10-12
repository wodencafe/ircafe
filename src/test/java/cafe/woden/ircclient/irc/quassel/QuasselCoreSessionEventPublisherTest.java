package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import io.reactivex.rxjava3.disposables.Disposable;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSessionEventPublisherTest {
  private final List<ServerIrcEvent> events = new CopyOnWriteArrayList<>();
  private final List<IrcEvent> readinessEvents = new CopyOnWriteArrayList<>();
  private final QuasselCoreSession session = session();
  private final QuasselCoreSessionEventPublisher publisher =
      new QuasselCoreSessionEventPublisher(events::add);

  @ParameterizedTest
  @CsvSource({
    "protocol-negotiated, '', quassel-phase=protocol-negotiated",
    "' protocol-negotiated ', ' detail ', quassel-phase=protocol-negotiated;detail=detail",
    "setup-required, 'requires setup', quassel-phase=setup-required;detail=requires setup"
  })
  void phaseDiagnosticsPreserveTrimmedSourceServerAndTimestamp(
      String phase, String detail, String source) {
    Instant before = Instant.now();
    publisher.emitConnectionPhase(session, phase, detail);
    Instant after = Instant.now();
    assertEquals(1, events.size());
    assertEquals("core", events.getFirst().serverId());
    var event =
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.getFirst().event());
    assertEquals(source, event.source());
    assertFalse(event.at().isBefore(before));
    assertFalse(event.at().isAfter(after));
    assertFalse(session.disconnectedEmitted.get());
  }

  @Test
  void diagnosticDetailsReplaceEmbeddedCrAndLfWithSpacesAfterTrimming() {
    publisher.emitConnectionPhase(session, "setup-required", "\r\n first\nsecond\rthird \r\n");
    var event =
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.getFirst().event());
    assertEquals("quassel-phase=setup-required;detail=first second third", event.source());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void absentDiagnosticDetailOmitsTheDetailSuffix(String detail) {
    publisher.emitConnectionPhase(session, "protocol-negotiated", detail);
    var event =
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.getFirst().event());
    assertEquals("quassel-phase=protocol-negotiated", event.source());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void absentPhaseDoesNotPublishOrCancelReadiness(String phase) {
    var fallback = Disposable.empty();
    session.readiness.fallbackTask.set(fallback);
    publisher.emitConnectionPhase(session, phase, "detail");
    assertTrue(events.isEmpty());
    assertFalse(fallback.isDisposed());
    assertSame(fallback, session.readiness.fallbackTask.get());
    session.readiness.close();
  }

  @Test
  void missingSessionIsANoopForPhaseAndDisconnectPublication() {
    publisher.emitConnecting(null);
    publisher.emitConnected(null);
    publisher.emitError(null, "failure", new IllegalStateException("failure"));
    publisher.emitConnectionPhase(null, "setup-required", "detail");
    publisher.emitDisconnectedOnce(null, "reason");
    assertTrue(events.isEmpty());
  }

  @Test
  void errorNotificationPreservesCauseReasonTimestampAndSessionState() {
    var cause = new IllegalStateException("failure");
    var fallback = Disposable.empty();
    session.readiness.fallbackTask.set(fallback);
    var phase = session.phase.get();
    Instant before = Instant.now();
    publisher.emitError(session, " raw reason ", cause);
    Instant after = Instant.now();
    assertEquals(1, events.size());
    assertEquals("core", events.getFirst().serverId());
    var event = assertInstanceOf(IrcEvent.Error.class, events.getFirst().event());
    assertEquals(" raw reason ", event.message());
    assertSame(cause, event.cause());
    assertFalse(event.at().isBefore(before));
    assertFalse(event.at().isAfter(after));
    assertEquals(phase, session.phase.get());
    assertFalse(session.closeRequested.get());
    assertFalse(session.disconnectedEmitted.get());
    assertSame(fallback, session.readiness.fallbackTask.get());
    assertFalse(fallback.isDisposed());
    session.readiness.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"connecting", "connected"})
  void connectionNotificationsPreservePayloadTimestampAndSessionState(String kind) {
    session.nicks.observe(-1, "observedNick", Instant.now());
    readinessEvents.clear();
    var phase =
        "connecting".equals(kind)
            ? QuasselSessionPhase.TRANSPORT_CONNECTING
            : QuasselSessionPhase.TRANSPORT_CONNECTED;
    session.phase.set(phase);
    Socket socket = session.socketRef.get();
    var fallback = Disposable.empty();
    session.readiness.fallbackTask.set(fallback);
    Instant before = Instant.now();
    publishConnection(publisher, kind);
    Instant after = Instant.now();
    assertEquals(1, events.size());
    assertEquals("core", events.getFirst().serverId());
    Instant at;
    if ("connecting".equals(kind)) {
      var event = assertInstanceOf(IrcEvent.Connecting.class, events.getFirst().event());
      assertEquals("core.example", event.serverHost());
      assertEquals(4242, event.serverPort());
      assertEquals("alice", event.nick());
      at = event.at();
    } else {
      var event = assertInstanceOf(IrcEvent.Connected.class, events.getFirst().event());
      assertEquals("core.example", event.serverHost());
      assertEquals(4242, event.serverPort());
      assertEquals("observedNick", event.nick());
      at = event.at();
    }
    assertFalse(at.isBefore(before));
    assertFalse(at.isAfter(after));
    assertEquals(phase, session.phase.get());
    assertSame(socket, session.socketRef.get());
    assertSame(fallback, session.readiness.fallbackTask.get());
    assertFalse(fallback.isDisposed());
    assertFalse(session.closeRequested.get());
    assertFalse(session.disconnectedEmitted.get());
    assertTrue(readinessEvents.isEmpty());
    session.readiness.close();
  }

  @Test
  void connectionNotificationsRemainOrderedAndReadTheObservedNickAtPublicationTime() {
    publisher.emitConnecting(session);
    publisher.emitConnected(session);
    session.nicks.observe(-1, "newNick", Instant.now());
    publisher.emitConnected(session);
    publisher.emitConnecting(session);
    assertEquals(
        "alice", assertInstanceOf(IrcEvent.Connecting.class, events.get(0).event()).nick());
    assertEquals("alice", assertInstanceOf(IrcEvent.Connected.class, events.get(1).event()).nick());
    assertEquals(
        "newNick", assertInstanceOf(IrcEvent.Connected.class, events.get(2).event()).nick());
    assertEquals(
        "alice", assertInstanceOf(IrcEvent.Connecting.class, events.get(3).event()).nick());
  }

  @Test
  void connectedNotificationUsesTheLastObservedNickEvenWhenCurrentPrimaryNickDiffers() {
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 1, List.of(1), java.util.Map.of()));
    session.networks.observe(1, "primary");
    session.nicks.observe(1, "primaryNick", Instant.now());
    session.nicks.observe(-1, "lastObserved", Instant.now());
    assertEquals("primaryNick", session.nicks.current());
    publisher.emitConnected(session);
    var event = assertInstanceOf(IrcEvent.Connected.class, events.getFirst().event());
    assertEquals("lastObserved", event.nick());
  }

  @ParameterizedTest
  @ValueSource(strings = {"connecting", "connected"})
  void connectionPublicationFailuresPropagateWithoutChangingPhaseOrReadiness(String kind) {
    var attempts = new AtomicInteger();
    var failure = new IllegalStateException("observer failed");
    var rejecting =
        new QuasselCoreSessionEventPublisher(
            event -> {
              attempts.incrementAndGet();
              throw failure;
            });
    var fallback = Disposable.empty();
    session.readiness.fallbackTask.set(fallback);
    var phase = session.phase.get();
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> publishConnection(rejecting, kind)));
    assertEquals(1, attempts.get());
    assertEquals(phase, session.phase.get());
    assertFalse(session.disconnectedEmitted.get());
    assertFalse(session.closeRequested.get());
    assertSame(fallback, session.readiness.fallbackTask.get());
    assertFalse(fallback.isDisposed());
    session.readiness.close();
  }

  private void publishConnection(QuasselCoreSessionEventPublisher target, String kind) {
    if ("connecting".equals(kind)) target.emitConnecting(session);
    else target.emitConnected(session);
  }

  @Test
  void phaseDiagnosticsRemainOrderedAndRepeatable() {
    publisher.emitConnectionPhase(session, "protocol-negotiated", "probe");
    publisher.emitConnectionPhase(session, "setup-required", "setup");
    publisher.emitConnectionPhase(session, "setup-required", "setup");
    assertEquals(
        List.of(
            "quassel-phase=protocol-negotiated;detail=probe",
            "quassel-phase=setup-required;detail=setup",
            "quassel-phase=setup-required;detail=setup"),
        events.stream()
            .map(event -> ((IrcEvent.ConnectionFeaturesUpdated) event.event()).source())
            .toList());
  }

  @ParameterizedTest
  @CsvSource({
    "2, 17, 3, quassel-probe protocol=datastream proto-features=0x0011 conn-features=0x03",
    "1, 0, 0, quassel-probe protocol=legacy proto-features=0x0000 conn-features=0x00",
    "7, 65535, 255, quassel-probe protocol=unknown(7) proto-features=0xffff conn-features=0xff",
    "2, 65536, 256, quassel-probe protocol=datastream proto-features=0x0000 conn-features=0x00"
  })
  void probeDiagnosticsPreserveLabelsAndMaskedFeatureWidths(
      int protocol, int features, int connectionFeatures, String source) {
    assertEquals(
        source,
        QuasselCoreSessionEventPublisher.renderProbeSource(
            new QuasselCoreProtocolProbe.ProbeSelection(
                0, protocol, features, connectionFeatures)));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void absentDisconnectReasonUsesTheExistingClientDefault(String reason) {
    publisher.emitDisconnectedOnce(session, reason);
    var event = assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event());
    assertEquals("Client requested disconnect", event.reason());
    assertEquals("core", events.getFirst().serverId());
    assertTrue(session.disconnectedEmitted.get());
  }

  @Test
  void disconnectKeepsTheFirstTrimmedReasonAndCurrentTimestamp() {
    Instant before = Instant.now();
    publisher.emitDisconnectedOnce(session, " Peer closed socket ");
    Instant after = Instant.now();
    publisher.emitDisconnectedOnce(session, "second reason");
    assertEquals(1, events.size());
    var event = assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event());
    assertEquals("Peer closed socket", event.reason());
    assertFalse(event.at().isBefore(before));
    assertFalse(event.at().isAfter(after));
  }

  @Test
  void readinessFallbackIsDetachedAndDisposedBeforeDisconnectObserversRun() {
    var trace = new ArrayList<String>();
    var fallback =
        Disposable.fromRunnable(
            () -> {
              assertNull(session.readiness.fallbackTask.get());
              trace.add("cancel readiness");
            });
    session.readiness.fallbackTask.set(fallback);
    var observing =
        new QuasselCoreSessionEventPublisher(
            event -> {
              assertTrue(fallback.isDisposed());
              assertNull(session.readiness.fallbackTask.get());
              assertTrue(session.disconnectedEmitted.get());
              trace.add("disconnect");
              events.add(event);
            });
    observing.emitDisconnectedOnce(session, "closed");
    session.readiness.observeSync();
    session.readiness.emitIfReady();
    assertEquals(List.of("cancel readiness", "disconnect"), trace);
    assertTrue(readinessEvents.isEmpty());
  }

  @Test
  void alreadyPublishedDisconnectStillClosesReadiness() {
    session.disconnectedEmitted.set(true);
    var fallback = Disposable.empty();
    session.readiness.fallbackTask.set(fallback);
    publisher.emitDisconnectedOnce(session, "closed again");
    assertTrue(fallback.isDisposed());
    assertNull(session.readiness.fallbackTask.get());
    session.readiness.observeSync();
    session.readiness.emitIfReady();
    assertTrue(events.isEmpty());
    assertTrue(readinessEvents.isEmpty());
  }

  @Test
  void disconnectPublicationFailureDoesNotUndoTheGuardOrRetryTheEvent() {
    var attempts = new AtomicInteger();
    var failure = new IllegalStateException("observer failed");
    var rejecting =
        new QuasselCoreSessionEventPublisher(
            event -> {
              attempts.incrementAndGet();
              throw failure;
            });
    var fallback = Disposable.empty();
    session.readiness.fallbackTask.set(fallback);
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class, () -> rejecting.emitDisconnectedOnce(session, "closed")));
    rejecting.emitDisconnectedOnce(session, "retry");
    assertEquals(1, attempts.get());
    assertTrue(session.disconnectedEmitted.get());
    assertTrue(fallback.isDisposed());
  }

  @Test
  void disconnectGuardBelongsToTheSessionAndAllowsAnEventAfterReconnect() {
    var replacement = session();
    publisher.emitDisconnectedOnce(session, "first session");
    publisher.emitDisconnectedOnce(replacement, "replacement session");
    publisher.emitDisconnectedOnce(session, "duplicate");
    assertEquals(
        List.of("first session", "replacement session"),
        events.stream().map(event -> ((IrcEvent.Disconnected) event.event()).reason()).toList());
  }

  @Test
  void concurrentDisconnectAttemptsPublishOnlyOneEventAndCancelReadinessOnce() throws Exception {
    var cancellations = new AtomicInteger();
    session.readiness.fallbackTask.set(Disposable.fromRunnable(cancellations::incrementAndGet));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> attempts = new ArrayList<>();
      for (int index = 0; index < 32; index++) {
        attempts.add(
            executor.submit(
                () -> {
                  assertTrue(start.await(5, TimeUnit.SECONDS));
                  publisher.emitDisconnectedOnce(session, "closed");
                  return null;
                }));
      }
      start.countDown();
      for (var attempt : attempts) attempt.get(5, TimeUnit.SECONDS);
    }
    assertEquals(1, events.size());
    assertEquals(1, cancellations.get());
    assertTrue(session.disconnectedEmitted.get());
    assertEquals("closed", ((IrcEvent.Disconnected) events.getFirst().event()).reason());
  }

  private QuasselCoreSession session() {
    var session =
        new QuasselCoreSession(
            " core ",
            "alice",
            "core.example",
            4242,
            (socket, operation) -> fail("Event publication must not write to the transport"),
            ignored -> fail("Event publication must not publish identities"),
            readinessEvents::add);
    session.socketRef.set(new Socket());
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    return session;
  }
}
