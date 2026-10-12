package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkSnapshotEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QuasselCoreStateObservationCoordinatorTest {
  private final List<IrcEvent> events = new ArrayList<>();
  private final List<QuasselCoreNetworkSnapshotEvent> snapshots = new ArrayList<>();
  private final List<String> trace = new ArrayList<>();
  private final QuasselCoreSession session = session(" core ");
  private final QuasselCoreTargetResolver targets =
      new QuasselCoreTargetResolver(
          new QuasselCoreTargetResolver.SessionPort() {
            @Override
            public void observeNetwork(QuasselCoreSession observed, int networkId) {
              coordinator.observeNetwork(observed, networkId, "");
            }

            @Override
            public boolean isSelfNick(QuasselCoreSession observed, String nick, int networkId) {
              return observed.nicks.isSelf(nick, networkId);
            }
          });
  private final QuasselCoreStateObservationCoordinator coordinator =
      new QuasselCoreStateObservationCoordinator(
          targets,
          event -> {
            snapshots.add(event);
            trace.add(event.source());
          });

  @Test
  void networkObservationPublishesNormalizedServerAndCurrentSnapshotWithoutDeduping() {
    coordinator.observeNetwork(session, 0, " Zero ");
    coordinator.observeNetwork(session, 0, "");
    assertEquals(2, snapshots.size());
    for (var snapshot : snapshots) {
      assertEquals("core", snapshot.serverId());
      assertEquals("observe-known-network", snapshot.source());
      assertEquals(1, snapshot.networks().size());
      assertEquals(0, snapshot.networks().getFirst().networkId());
      assertEquals("Zero", snapshot.networks().getFirst().networkName());
    }
    assertTrue(events.isEmpty());
  }

  @Test
  void seedingUsesPrimaryNetworkAndPreservesObservedHintWithSnapshotBeforeHintUpdate() {
    twoNetworks();
    AtomicReference<Integer> hintAtSnapshot = new AtomicReference<>();
    var observing =
        new QuasselCoreStateObservationCoordinator(
            targets,
            event -> hintAtSnapshot.set(session.targetNetworkHints.networkIdForTarget("#room")));
    observing.observeTargetNetwork(session, "#room", 8, false);
    assertEquals(-1, hintAtSnapshot.get());
    assertEquals(7, session.targetNetworkHints.networkIdForTarget("#room"));
    observing.observeTargetNetwork(session, "#room", 8, true);
    assertEquals(7, hintAtSnapshot.get());
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    observing.observeTargetNetwork(session, "#room", 7, false);
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    observing.observeTargetNetwork(session, "", 8, true);
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget(""));
    assertTrue(events.isEmpty());
  }

  @Test
  void fullStateReconcilesMembershipBeforeSnapshotAndCapabilityNotifications() {
    twoNetworks();
    session.buffers.merge(buffer(11, 7, 2, "#room"));
    clearEvents();
    coordinator.observeFullState(
        session,
        7,
        Map.of("isConnected", true, "capsEnabled", List.of("message-tags"), "MONITOR", 100));
    assertEquals(
        List.of(
            "observe-known-network",
            "JoinedChannel",
            "observe-network-state",
            "Ircv3CapabilityChanged",
            "ConnectionFeaturesUpdated"),
        trace);
    var joined = assertInstanceOf(IrcEvent.JoinedChannel.class, events.getFirst());
    assertEquals("#room{net:home}", joined.channel());
    assertEquals(7, session.targetNetworkHints.networkIdForTarget("#room"));
    assertTrue(session.features.hasAnyCapability("message-tags"));
    assertTrue(session.features.monitorAvailable(7));
    assertEquals(100, session.features.monitorLimit(7));
    assertTrue(snapshots.getLast().networks().getFirst().connected());
  }

  @Test
  void scalarSnapshotsMergeAndReconnectMembershipWithoutChangingFeatureState() {
    coordinator.observeNetwork(session, 7, "Home");
    session.buffers.merge(buffer(11, 7, 2, "#room"));
    coordinator.observeState(session, 7, Map.of("isConnected", true, "identity", 3));
    clearEvents();
    coordinator.observeState(session, 7, Map.of("networkName", "renamed"));
    assertTrue(events.isEmpty());
    assertEquals(true, session.networks.state(7).get("isConnected"));
    assertEquals(3, session.networks.state(7).get("identity"));
    coordinator.observeState(session, 7, Map.of("isConnected", false));
    assertTrue(events.isEmpty());
    assertNotNull(session.buffers.get(11));
    coordinator.observeState(session, 7, Map.of("isConnected", true));
    assertEquals(1, events.size());
    assertInstanceOf(IrcEvent.JoinedChannel.class, events.getFirst());
    coordinator.observeState(session, 7, Map.of("capsEnabled", List.of("message-tags")));
    assertFalse(session.features.hasObservedCapabilities());
  }

  @Test
  void unknownNetworkFullStateFallsBackForFeaturesWithoutCreatingNetworkSnapshot() {
    twoNetworks();
    clearEvents();
    coordinator.observeFullState(
        session, -1, Map.of("capsEnabled", List.of("message-tags"), "MONITOR", 25));
    assertTrue(snapshots.isEmpty());
    assertTrue(session.features.hasAnyCapability("message-tags"));
    assertEquals(25, session.features.monitorLimit(7));
    coordinator.forgetNetwork(session, 7);
    assertFalse(session.features.hasAnyCapability("message-tags"));
    assertFalse(session.features.monitorAvailable(8));
  }

  @Test
  void nestedBufferSnapshotsMergeInEncounterOrderAndOnlyJoinConnectedChannelsOnce() {
    twoNetworks();
    coordinator.observeState(session, 8, Map.of("isConnected", true));
    clearEvents();
    BufferInfoValue full = buffer(22, 8, 2, "#work");
    BufferInfoValue partial = buffer(22, -1, 0, "");
    coordinator.applyBufferSnapshot(
        session,
        List.of(
            Map.of("nested", List.of(full, partial)),
            List.of(full),
            buffer(33, 7, 2, "#offline"),
            buffer(44, 8, 4, "alice"),
            buffer(-1, 8, 2, "#transient"),
            "ignored"));
    assertEquals(full, session.buffers.get(22));
    assertEquals(3, session.buffers.values().size());
    assertNull(session.buffers.get(-1));
    assertEquals(1, events.size());
    var joined = assertInstanceOf(IrcEvent.JoinedChannel.class, events.getFirst());
    assertEquals("#work{net:work}", joined.channel());
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#work"));
    assertEquals(7, session.targetNetworkHints.networkIdForTarget("#offline"));
    assertEquals(7, session.targetNetworkHints.networkIdForTarget("alice"));
    assertEquals(13, snapshots.size());
  }

  @Test
  void displayBufferResolutionMergesMetadataAndObservesNetworkWithoutHintOrJoin() {
    twoNetworks();
    BufferInfoValue full = buffer(11, 7, 2, "#room");
    session.buffers.merge(full);
    session.networks.observeState(7, Map.of("isConnected", true));
    clearEvents();
    assertEquals(full, coordinator.resolveBuffer(session, buffer(11, -1, 0, "")));
    assertEquals(full, session.buffers.get(11));
    assertEquals(List.of("observe-known-network"), trace);
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#room"));
    assertTrue(events.isEmpty());
  }

  @Test
  void missingAndTransientDisplayBuffersDoNotMutateSessionCatalogs() {
    twoNetworks();
    clearEvents();
    assertEquals(buffer(-1, 7, 1, ""), coordinator.resolveBuffer(session, null));
    assertEquals(buffer(-1, -1, 1, ""), coordinator.resolveBuffer(null, null));
    BufferInfoValue transientBuffer = buffer(-1, 99, 2, "#transient");
    assertSame(transientBuffer, coordinator.resolveBuffer(session, transientBuffer));
    assertTrue(session.buffers.values().isEmpty());
    assertEquals(2, coordinator.snapshot(session).size());
    assertTrue(trace.isEmpty());
  }

  @Test
  void forgettingNetworkClearsAllDependentStateBeforePublishingAndPreservesOtherNetworks() {
    twoNetworks();
    session.buffers.merge(buffer(11, 7, 2, "#home"));
    session.buffers.merge(buffer(22, 8, 2, "#work"));
    session.pendingReadMarkers.defer(11, 101);
    session.pendingReadMarkers.defer(22, 202);
    session.nicks.observe(7, "homeNick", Instant.now());
    session.nicks.observe(8, "workNick", Instant.now());
    session.features.observeCapabilities(7, Map.of("capsEnabled", List.of("read-marker")));
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("message-tags")));
    session.features.observeMonitor(7, true, 25);
    session.features.observeMonitor(8, true, 50);
    session.networks.observeState(7, Map.of("isConnected", true));
    session.targetNetworkHints.observe("#home", 7);
    session.targetNetworkHints.observe("#work", 8);
    session.membership.observeJoin(Instant.now(), "#home{net:home}", 7);
    session.membership.observeJoin(Instant.now(), "#work{net:work}", 8);
    clearEvents();
    var removing =
        new QuasselCoreStateObservationCoordinator(
            targets,
            event -> {
              assertEquals("forget-known-network", event.source());
              assertEquals(List.of(8), event.networks().stream().map(n -> n.networkId()).toList());
              assertNull(session.buffers.get(11));
              assertNull(session.pendingReadMarkers.takeBufferForMessage(101));
              assertNull(session.networks.state(7));
              assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#home"));
              assertFalse(session.features.hasAnyCapability("read-marker"));
              assertEquals("workNick", session.nicks.forNetwork(7));
            });
    removing.forgetNetwork(session, 7);
    assertNotNull(session.buffers.get(22));
    assertEquals(22, session.pendingReadMarkers.takeBufferForMessage(202));
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#work"));
    assertTrue(session.features.hasAnyCapability("message-tags"));
    assertEquals(50, session.features.monitorLimit(8));
    session.membership.observeJoin(Instant.now(), "#work{net:work}", 8);
    assertTrue(events.isEmpty());
    session.membership.observeJoin(Instant.now(), "#home{net:home}", 7);
    assertEquals(1, events.size());
  }

  @Test
  void sharedCoordinatorKeepsSessionObservationsIsolated() {
    var other = session("other");
    coordinator.observeNetwork(session, 7, "Home");
    coordinator.observeNetwork(other, 7, "Other");
    coordinator.observeTargetNetwork(session, "#room", 7, true);
    coordinator.observeTargetNetwork(other, "#room", 7, true);
    coordinator.forgetNetwork(session, 7);
    assertTrue(coordinator.snapshot(session).isEmpty());
    assertEquals("Other", coordinator.snapshot(other).getFirst().networkName());
    assertEquals(7, other.targetNetworkHints.networkIdForTarget("#room"));
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#room"));
  }

  @Test
  void invalidObservationsAndEmptySnapshotsDoNotPublish() {
    coordinator.observeNetwork(null, 7, "Home");
    coordinator.observeNetwork(session, -1, "Home");
    coordinator.forgetNetwork(null, 7);
    coordinator.forgetNetwork(session, -1);
    coordinator.observeTargetNetwork(null, "#room", 7, true);
    coordinator.observeTargetNetwork(session, "#room", -1, false);
    coordinator.observeState(null, 7, Map.of("isConnected", true));
    coordinator.observeState(session, 7, null);
    coordinator.observeState(session, 7, Map.of());
    coordinator.observeFullState(null, 7, Map.of("capsEnabled", List.of("message-tags")));
    coordinator.applyBufferSnapshot(session, null);
    coordinator.applyBufferSnapshot(session, List.of());
    coordinator.applyBufferSnapshot(session, List.of(Map.of("ignored", "text")));
    assertTrue(coordinator.snapshot(null).isEmpty());
    assertTrue(coordinator.snapshot(session).isEmpty());
    assertTrue(trace.isEmpty());
  }

  private void twoNetworks() {
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 7, List.of(7, 8), Map.of()));
    coordinator.observeNetwork(session, 7, "Home");
    coordinator.observeNetwork(session, 8, "Work");
  }

  private QuasselCoreSession session(String serverId) {
    return new QuasselCoreSession(
        serverId,
        "alice",
        "core.example",
        4242,
        (socket, operation) -> fail("Observations must not write to the socket"),
        ignored -> {},
        event -> {
          events.add(event);
          trace.add(event.getClass().getSimpleName());
        });
  }

  private static BufferInfoValue buffer(int id, int network, int type, String name) {
    return new BufferInfoValue(id, network, type, -1, name);
  }

  private void clearEvents() {
    events.clear();
    snapshots.clear();
    trace.clear();
  }
}
