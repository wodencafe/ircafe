package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import io.reactivex.rxjava3.disposables.Disposable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSessionTest {
  private final List<IrcEvent> events = new ArrayList<>();
  private final List<String> observations = new ArrayList<>();
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (socket, operation) -> fail("state operations must not send"),
          id -> observations.add("identity:" + id),
          events::add);

  @Test
  void initializationKeepsNetworkIdentityAndBufferObservationOrderBeforeEstablishedPhase() {
    var first = buffer(11, 1, "#same");
    var second = buffer(22, 2, "#same");
    var auth =
        new QuasselCoreAuthHandshake.AuthResult(
            "core",
            2,
            List.of(2, 1),
            Map.of(11, first, 22, second),
            Map.of(42, Map.of("identityId", 42, "identityName", "Work")));
    session.phase.set(QuasselSessionPhase.AUTHENTICATING);
    session.networks.observe(9, "old");
    session.networks.forget(2);
    session.networks.rememberCreatedName("old pending name");
    session.identities.observe(9, "old identity");
    session.buffers.merge(buffer(99, 9, "#old"));
    session.pendingReadMarkers.defer(99, 123);
    session.targetNetworkHints.observe("#same", 9);
    session.nicks.observe(9, "old nick", Instant.now());
    session.features.observeCapabilities(9, Map.of("capsEnabled", List.of("message-tags")));
    session.nativeReadMarkerSupportObserved.set(true);
    session.readiness.syncObserved.set(true);
    session.readiness.connectionReadyEmitted.set(true);
    session.reconnectScheduled.set(true);
    session.lag.observeReply(List.of(session.lag.beginProbe()));
    observations.clear();
    events.clear();
    session.initialize(
        auth,
        id -> {
          assertEquals(QuasselSessionPhase.AUTHENTICATING, session.phase.get());
          assertSame(auth, session.authResult.get());
          assertNull(session.buffers.get(99));
          assertFalse(session.features.hasObservedCapabilities());
          session.networks.observe(id, "");
          observations.add("network:" + id);
        },
        info -> {
          assertEquals("alice", session.nicks.forNetwork(2));
          assertEquals(
              "network-" + info.networkId(), session.networks.displayNames().get(info.networkId()));
          session.targetNetworkHints.seed(
              info.bufferName(),
              info.networkId(),
              () -> session.networks.firstKnownNetworkId(auth, session.buffers.values()));
          observations.add("buffer:" + info.bufferId());
        });
    assertEquals(List.of("network:2", "network:1", "identity:core"), observations.subList(0, 3));
    assertEquals(7, observations.size());
    for (int index = 3; index < observations.size(); index += 2) {
      assertTrue(observations.get(index).startsWith("network:"));
      assertTrue(observations.get(index + 1).startsWith("buffer:"));
    }
    assertEquals(Set.of(1, 2), session.networks.knownIds(auth, session.buffers.values()));
    assertEquals(Set.of(42), session.identities.knownIds());
    assertEquals("alice", session.nicks.current());
    assertEquals("old nick", session.nicks.lastObservedNick());
    assertEquals("alice", session.nicks.forNetwork(9));
    assertEquals(2, session.targetNetworkHints.networkIdForTarget("#same"));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(123));
    assertEquals("", session.networks.claimCreatedName());
    assertFalse(session.nativeReadMarkerSupportObserved.get());
    assertFalse(session.readiness.syncObserved.get());
    assertFalse(session.readiness.connectionReadyEmitted.get());
    assertFalse(session.reconnectScheduled.get());
    assertTrue(session.lag.lastMeasuredLagMs().isEmpty());
    assertTrue(events.isEmpty());
    assertEquals(QuasselSessionPhase.AUTHENTICATING, session.phase.get());
  }

  @ParameterizedTest
  @CsvSource({"2,2", "-1,1", "99,1"})
  void initializationSeedsNickForTheResolvedPrimaryNetwork(int primary, int resolved) {
    var auth = new QuasselCoreAuthHandshake.AuthResult("core", primary, List.of(1, 2), Map.of());
    session.nicks.observe(-1, "fallback", Instant.now());
    events.clear();
    session.initialize(auth, id -> session.networks.observe(id, ""), info -> fail("no buffers"));
    assertEquals("alice", session.nicks.forNetwork(resolved));
    assertEquals("alice", session.nicks.current());
    assertEquals("fallback", session.nicks.lastObservedNick());
    assertTrue(events.isEmpty());
  }

  @Test
  void initializationCanDiscoverNetworkFromBufferWhenAuthNetworkListIsEmpty() {
    var info = buffer(11, 3, "#buffer-only");
    var auth = new QuasselCoreAuthHandshake.AuthResult("core", -1, List.of(), Map.of(11, info));
    session.nicks.observe(-1, "fallback", Instant.now());
    session.initialize(
        auth,
        id -> session.networks.observe(id, ""),
        seed -> {
          assertSame(info, seed);
          session.targetNetworkHints.seed(
              seed.bufferName(),
              seed.networkId(),
              () -> session.networks.firstKnownNetworkId(auth, session.buffers.values()));
        });
    assertEquals("alice", session.nicks.current());
    assertEquals("fallback", session.nicks.lastObservedNick());
    assertEquals("network-3", session.networks.displayNames().get(3));
    assertEquals(3, session.targetNetworkHints.networkIdForTarget("#buffer-only"));
  }

  @Test
  void cleanupClearsObservationsButPreservesLifecycleAndRemovedNetworkTombstones() {
    var auth = new QuasselCoreAuthHandshake.AuthResult("core", 1, List.of(1, 2), Map.of());
    session.authResult.set(auth);
    session.networks.observe(1, "one");
    session.networks.observe(2, "two");
    session.networks.forget(1);
    session.networks.rememberCreatedName("pending");
    session.identities.observe(42, "work");
    session.buffers.merge(buffer(22, 2, "#same"));
    session.pendingReadMarkers.defer(22, 100);
    session.history.observe("#same", 100, Instant.ofEpochSecond(1_700_000_000L));
    session.targetNetworkHints.observe("#same", 2);
    session.membership.observeJoin(Instant.now(), "#same", 2);
    session.nicks.observe(2, "workNick", Instant.now());
    session.nicks.observe(-1, "fallback", Instant.now());
    assertEquals("workNick", session.nicks.forNetwork(2));
    session.features.observeCapabilities(2, Map.of("capsEnabled", List.of("message-tags")));
    session.features.observeMonitor(2, true, 10);
    session.lag.observeReply(List.of(session.lag.beginProbe()));
    session.nativeReadMarkerSupportObserved.set(true);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    session.closeRequested.set(true);
    session.closeReason.set("requested reason");
    session.disconnectedEmitted.set(true);
    events.clear();
    session.clearObservedState();
    session.clearObservedState();
    assertTrue(session.buffers.values().isEmpty());
    assertNull(session.pendingReadMarkers.takeBufferForMessage(100));
    assertEquals(-1, session.history.timestampForMsgId("#same", 100));
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#same"));
    assertTrue(session.networks.displayNames().isEmpty());
    assertEquals("", session.networks.claimCreatedName());
    assertEquals(Set.of(2), session.networks.knownIds(auth, session.buffers.values()));
    assertTrue(session.identities.knownIds().isEmpty());
    assertEquals("fallback", session.nicks.forNetwork(2));
    assertEquals("fallback", session.nicks.lastObservedNick());
    assertFalse(session.features.hasObservedCapabilities());
    assertFalse(session.features.hasMonitorState());
    assertFalse(session.nativeReadMarkerSupportObserved.get());
    assertTrue(session.lag.lastMeasuredLagMs().isEmpty());
    assertTrue(events.isEmpty());
    assertTrue(session.closeRequested.get());
    assertTrue(session.disconnectedEmitted.get());
    assertEquals("requested reason", session.closeReason.get());
    assertSame(auth, session.authResult.get());
    assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
    session.membership.observeJoin(Instant.now(), "#same", 2);
    assertEquals(1, events.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"readiness", "read"})
  void taskDisposalDetachesBeforeCallingAndCanBeRepeated(String kind) {
    var ref = taskRef(kind);
    var calls = new AtomicInteger();
    ref.set(
        Disposable.fromRunnable(
            () -> {
              assertNull(ref.get());
              calls.incrementAndGet();
              throw new IllegalStateException("disposal failed");
            }));
    dispose(kind);
    dispose(kind);
    assertNull(ref.get());
    assertEquals(1, calls.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"readiness", "read"})
  void disposedTaskIsDetachedWithoutCallingDisposeAgain(String kind) {
    var ref = taskRef(kind);
    ref.set(
        new Disposable() {
          @Override
          public void dispose() {
            fail("already disposed");
          }

          @Override
          public boolean isDisposed() {
            return true;
          }
        });
    dispose(kind);
    assertNull(ref.get());
  }

  @Test
  void concurrentTaskDisposalInvokesOnlyOneCancellation() throws Exception {
    var calls = new AtomicInteger();
    session.readiness.fallbackTask.set(Disposable.fromRunnable(calls::incrementAndGet));
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first =
          workers.submit(
              () -> {
                start.await();
                session.readiness.cancelFallback();
                return null;
              });
      var second =
          workers.submit(
              () -> {
                start.await();
                session.readiness.cancelFallback();
                return null;
              });
      start.countDown();
      first.get(2, TimeUnit.SECONDS);
      second.get(2, TimeUnit.SECONDS);
    }
    assertNull(session.readiness.fallbackTask.get());
    assertEquals(1, calls.get());
  }

  private AtomicReference<Disposable> taskRef(String kind) {
    return "readiness".equals(kind) ? session.readiness.fallbackTask : session.readLoopTask;
  }

  private void dispose(String kind) {
    if ("readiness".equals(kind)) session.readiness.cancelFallback();
    else session.disposeReadLoopTask();
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,true", "true,false"})
  void readinessRequiresSocketAndNoCloseRequest(boolean socketPresent, boolean closeRequested) {
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    if (socketPresent) session.socketRef.set(org.mockito.Mockito.mock(java.net.Socket.class));
    session.closeRequested.set(closeRequested);
    session.readiness.observeSync();
    session.readiness.emitIfReady();
    assertEquals(socketPresent && !closeRequested ? 2 : 0, events.size());
  }

  @Test
  void lateAuthenticationInitializationDoesNotReopenClosedReadiness() {
    session.readiness.close();
    session.phase.set(QuasselSessionPhase.AUTHENTICATING);
    session.initialize(
        new QuasselCoreAuthHandshake.AuthResult("core", -1, List.of(), Map.of()),
        id -> fail("no networks"),
        info -> fail("no buffers"));
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    session.socketRef.set(org.mockito.Mockito.mock(java.net.Socket.class));
    session.readiness.observeSync();
    session.readiness.emitIfReady();
    session.readiness.scheduleFallback();
    assertTrue(events.isEmpty());
    assertNull(session.readiness.fallbackTask.get());
  }

  private static QuasselCoreDatastreamCodec.BufferInfoValue buffer(
      int id, int network, String name) {
    return new QuasselCoreDatastreamCodec.BufferInfoValue(id, network, 0x02, -1, name);
  }
}
