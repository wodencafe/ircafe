package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.*;
import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSyncDispatcherTest {
  private final List<String> trace = new ArrayList<>();
  private final List<IrcEvent> events = new ArrayList<>();
  private final List<Map<?, ?>> states = new ArrayList<>();
  private final List<List<Object>> buffers = new ArrayList<>();
  private final List<List<Object>> markers = new ArrayList<>();
  private final List<List<Object>> backlog = new ArrayList<>();
  private RuntimeException bufferFailure;
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (socket, write) -> fail("sync routing must not write"),
          id -> trace.add("identity"),
          this::emit);
  private final QuasselCoreNetworkSyncTranslator networks =
      new QuasselCoreNetworkSyncTranslator(
          "core",
          this::observeNetwork,
          (id, state) -> trace.add("snapshot:" + id),
          (id, state) -> {
            trace.add("state:" + id);
            states.add(state);
          },
          (id, nick) -> trace.add("nick:" + id + ":" + nick));
  private final QuasselCoreSyncDispatcher dispatcher =
      new QuasselCoreSyncDispatcher(
          session,
          networks,
          new QuasselCoreSyncDispatcher.SessionPort() {
            @Override
            public void applyBufferInfoSnapshot(List<Object> values) {
              trace.add("buffers");
              if (bufferFailure != null) throw bufferFailure;
              buffers.add(values);
            }

            @Override
            public void observeReadMarkers(String slot, List<Object> values) {
              trace.add("markers:" + slot);
              markers.add(values);
            }

            @Override
            public void receiveBacklog(List<Object> values) {
              trace.add("backlog");
              backlog.add(values);
            }

            @Override
            public void observeNetwork(int id, String name) {
              QuasselCoreSyncDispatcherTest.this.observeNetwork(id, name);
            }

            @Override
            public String qualifyTarget(String target, int id) {
              trace.add("qualify:" + id + ":" + target);
              return target + "{net:n" + id + "}";
            }

            @Override
            public void emit(IrcEvent event) {
              QuasselCoreSyncDispatcherTest.this.emit(event);
            }
          });

  @ParameterizedTest
  @ValueSource(ints = {SIGNAL_PROXY_SYNC, SIGNAL_PROXY_INIT_DATA})
  void bufferSyncerAnnouncesSupportBeforeBuffersAndMarkersAndKeepsParams(int type) {
    List<Object> values = List.of(new BufferInfoValue(11, 7, 2, -1, "#room"));
    dispatcher.dispatch(message(type, " BufferSyncer ", " setMarkerLine ", values));
    assertEquals(List.of("ConnectionFeaturesUpdated", "buffers", "markers:setMarkerLine"), trace);
    assertSame(values, buffers.getFirst());
    assertSame(values, markers.getFirst());
    assertTrue(session.nativeReadMarkerSupportObserved.get());
    assertEquals(
        "quassel-buffer-syncer",
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.getFirst()).source());
    trace.clear();
    dispatcher.dispatch(message(type, "BufferSyncer", "setLastSeenMsg", values));
    assertEquals(List.of("buffers", "markers:setLastSeenMsg"), trace);
    assertEquals(1, events.size());
  }

  @ParameterizedTest
  @NullAndEmptySource
  void emptyBufferSyncerStillAnnouncesSupportAndRoutesBothObservations(List<Object> values) {
    dispatcher.dispatch(message(SIGNAL_PROXY_INIT_DATA, "BufferSyncer", null, values));
    assertEquals(List.of("ConnectionFeaturesUpdated", "buffers", "markers:"), trace);
    assertTrue(buffers.getFirst().isEmpty());
    assertTrue(markers.getFirst().isEmpty());
  }

  @Test
  void bufferViewOnlyAppliesBuffersWithoutClaimingNativeMarkerSupport() {
    dispatcher.dispatch(message(SIGNAL_PROXY_INIT_DATA, "BufferViewConfig", "init", List.of()));
    assertEquals(List.of("buffers"), trace);
    assertFalse(session.nativeReadMarkerSupportObserved.get());
    assertTrue(markers.isEmpty());
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "1,receiveBacklog,true",
    "1,receiveBacklog(BufferId),true",
    "4,receiveBacklog,false",
    "1,ReceiveBacklog,false",
    "1,requestBacklog,false"
  })
  void backlogRequiresSyncAndTheCaseSensitiveReceiveSlot(int type, String slot, boolean routed) {
    List<Object> values = List.of("payload");
    dispatcher.dispatch(message(type, " BacklogManager ", " " + slot + " ", values));
    assertEquals(routed ? List.of("backlog") : List.of(), trace);
    if (routed) assertSame(values, backlog.getFirst());
  }

  @Test
  void coreInfoRoutesNestedIdentitiesWithoutInspectingNetworkState() {
    Map<String, Object> identity = Map.of("identityId", 9, "identityName", "Work");
    dispatcher.dispatch(
        message(
            SIGNAL_PROXY_INIT_DATA,
            "CoreInfo",
            "init",
            List.of(Map.of("Identities", List.of(identity), "networkId", 7))));
    assertTrue(session.identities.isKnown(9));
    assertEquals(identity, session.identities.state(9));
    assertTrue(states.isEmpty());
    assertEquals(List.of("identity"), trace);
  }

  @Test
  void identityUsesObjectIdForItsDirectSnapshot() {
    Map<String, Object> identity = Map.of("identityName", "Work", "nicks", List.of("alice"));
    dispatcher.dispatch(message(SIGNAL_PROXY_INIT_DATA, " Identity ", "init", List.of(identity)));
    assertEquals(identity, session.identities.state(7));
    assertTrue(session.identities.isKnown(7));
    assertEquals(List.of("identity"), trace);
  }

  @Test
  void networkPropertiesPrecedeDirectAndRecursiveSnapshots() {
    Map<String, Object> state = Map.of("networkName", "Mapped", "myNick", "bob");
    dispatcher.dispatch(
        message(SIGNAL_PROXY_SYNC, "Network", " setNetworkName ", List.of("Scalar", state)));
    assertEquals(
        List.of(
            "network:7:Scalar",
            "snapshot:7",
            "network:7:Mapped",
            "state:7",
            "nick:7:bob",
            "network:7:Mapped",
            "state:7"),
        trace);
    assertEquals(List.of(state, state), states);
  }

  @Test
  void networkMembershipPrecedesDirectAndRecursiveSnapshotsAndDeduplicatesJoins() {
    Map<String, Object> state = Map.of("networkName", "Mapped", "myNick", "bob");
    List<Object> values = List.of("#room", state);
    dispatcher.dispatch(message(SIGNAL_PROXY_SYNC, "Network", "addIrcChannel", values));
    assertEquals(
        List.of(
            "qualify:7:#room",
            "JoinedChannel",
            "network:7:Mapped",
            "state:7",
            "nick:7:bob",
            "network:7:Mapped",
            "state:7"),
        trace);
    var join = assertInstanceOf(IrcEvent.JoinedChannel.class, events.getFirst());
    assertEquals("#room{net:n7}", join.channel());
    dispatcher.dispatch(message(SIGNAL_PROXY_SYNC, "Network", "addIrcChannel", values));
    assertEquals(1, events.size());
    dispatcher.dispatch(
        message(SIGNAL_PROXY_SYNC, "Network", "removeIrcChannel", List.of("#room")));
    dispatcher.dispatch(message(SIGNAL_PROXY_SYNC, "Network", "addIrcChannel", List.of("#room")));
    assertEquals(2, events.size());
  }

  @Test
  void networkInfoKeepsDirectOnlySnapshotsAndDoesNotObserveNick() {
    Map<String, Object> nested = Map.of("networkId", 8, "networkName", "Nested");
    Map<String, Object> direct = Map.of("networkName", "Direct", "myNick", "bob", "nested", nested);
    dispatcher.dispatch(
        message(
            SIGNAL_PROXY_INIT_DATA,
            "NetworkInfo",
            "init",
            List.of(direct, new UserTypeValue("NetworkInfo", nested))));
    assertEquals(List.of("network:7:Direct", "state:7"), trace);
    assertEquals(List.of(direct), states);
  }

  @Test
  void ircUserRoutesUserEventsWithoutGenericNetworkOrIdentityObservation() {
    dispatcher.dispatch(
        message(
            SIGNAL_PROXY_INIT_DATA,
            "IrcUser",
            "init",
            List.of(
                Map.of(
                    "networkId",
                    7,
                    "nick",
                    "bob",
                    "user",
                    "u",
                    "host",
                    "h",
                    "away",
                    true,
                    "identityId",
                    9))));
    assertEquals(List.of("network:7:", "UserHostChanged", "UserAwayStateObserved"), trace);
    assertEquals("bob", assertInstanceOf(IrcEvent.UserHostChanged.class, events.getFirst()).nick());
    assertTrue(states.isEmpty());
    assertFalse(session.identities.isKnown(9));
  }

  @Test
  void ircChannelQualifiesTopicTargetBeforePublishing() {
    dispatcher.dispatch(
        message(
            SIGNAL_PROXY_INIT_DATA,
            "IrcChannel",
            "init",
            List.of(Map.of("networkId", 7, "name", "#room", "topic", "Hello"))));
    assertEquals(List.of("network:7:", "qualify:7:#room", "ChannelTopicUpdated"), trace);
    var topic = assertInstanceOf(IrcEvent.ChannelTopicUpdated.class, events.getFirst());
    assertEquals("#room{net:n7}", topic.channel());
    assertEquals("Hello", topic.topic());
    assertTrue(states.isEmpty());
  }

  @Test
  void unknownClassWithIdentityAndNetworkObservesIdentityFirst() {
    Map<String, Object> state =
        Map.of("identityId", 9, "identityName", "Work", "networkId", 7, "networkName", "Mapped");
    dispatcher.dispatch(
        message(SIGNAL_PROXY_INIT_DATA, "CustomNETWORKIDENTITY", "init", List.of(state)));
    assertEquals(List.of("identity", "network:7:Mapped", "state:7"), trace);
    assertTrue(session.identities.isKnown(9));
    assertEquals(List.of(state), states);
  }

  @Test
  void unknownIdentityUsesObjectFallbackThroughNestedPayloads() {
    Map<String, Object> identity = Map.of("identityName", "Work");
    dispatcher.dispatch(
        message(
            SIGNAL_PROXY_INIT_DATA,
            "CustomIdentity",
            "init",
            List.of(Map.of("nested", List.of(identity)))));
    assertTrue(session.identities.isKnown(7));
    assertEquals(identity, session.identities.state(7));
    assertTrue(states.isEmpty());
  }

  @Test
  void lowerCaseNetworkUsesRecursiveFallbackWithoutScalarOrNickHandling() {
    Map<String, Object> state = Map.of("networkName", "Mapped", "myNick", "bob");
    dispatcher.dispatch(
        message(SIGNAL_PROXY_SYNC, "network", "setNetworkName", List.of("Scalar", state)));
    assertEquals(List.of("network:7:Mapped", "state:7"), trace);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"Unknown", "buffersyncer", "ircuser", " ", "BacklogManager"})
  void unrelatedClassesAndCaseVariantsAreIgnored(String className) {
    dispatcher.dispatch(message(SIGNAL_PROXY_INIT_DATA, className, null, null));
    assertTrue(trace.isEmpty());
    assertFalse(session.nativeReadMarkerSupportObserved.get());
  }

  @Test
  void bufferFailureStopsMarkerHandlingAndReadinessWhileSupportRemainsObserved() {
    session.socketRef.set(new Socket());
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    bufferFailure = new IllegalStateException("buffer failed");
    var outer =
        new QuasselCoreSignalProxyDispatcher(
            session, frame -> fail("must not handle RPC"), dispatcher::dispatch);
    assertSame(
        bufferFailure,
        assertThrows(
            IllegalStateException.class,
            () ->
                outer.dispatch(
                    message(SIGNAL_PROXY_INIT_DATA, "BufferSyncer", "init", List.of()))));
    assertEquals(List.of("ConnectionFeaturesUpdated", "buffers"), trace);
    assertTrue(session.nativeReadMarkerSupportObserved.get());
    assertFalse(session.readiness.connectionReadyEmitted.get());
    assertTrue(markers.isEmpty());
  }

  private void observeNetwork(int id, String name) {
    trace.add("network:" + id + ":" + name);
  }

  private void emit(IrcEvent event) {
    trace.add(event.getClass().getSimpleName());
    events.add(event);
  }

  private static SignalProxyMessage message(
      int type, String className, String slot, List<Object> values) {
    return new SignalProxyMessage(type, className, "namespace/7", slot, values);
  }
}
