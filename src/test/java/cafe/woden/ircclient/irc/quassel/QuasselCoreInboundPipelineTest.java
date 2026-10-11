package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreInboundPipelineTest {
  private static final Instant AT = Instant.parse("2026-10-08T12:00:00Z");
  private static final BufferInfoValue BUFFER = new BufferInfoValue(22, 8, 2, -1, "#room");
  private static final String TARGET = "#room{net:work}";
  private static final QuasselIrcv3RuntimeSupport RUNTIME =
      new QuasselIrcv3RuntimeSupport(Ircv3RuntimeCatalogs.applicationClasspath());
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final Socket socket = mock(Socket.class);
  private final OutputStream output = mock(OutputStream.class);
  private final List<ServerIrcEvent> events = new ArrayList<>();
  private final List<String> trace = new ArrayList<>();
  private final List<Socket> writes = new ArrayList<>();
  private final Thread caller = Thread.currentThread();
  private Consumer<ServerIrcEvent> onEvent = ignored -> {};
  private IOException writeFailure;
  private final QuasselCoreSession session = session("core");
  private final QuasselCoreTargetResolver targets =
      new QuasselCoreTargetResolver(
          new QuasselCoreTargetResolver.SessionPort() {
            @Override
            public void observeNetwork(QuasselCoreSession observed, int networkId) {
              stateObservations.observeNetwork(observed, networkId, "");
            }

            @Override
            public boolean isSelfNick(QuasselCoreSession observed, String nick, int networkId) {
              return observed.nicks.isSelf(nick, networkId);
            }
          });
  private final QuasselCoreStateObservationCoordinator stateObservations =
      new QuasselCoreStateObservationCoordinator(
          targets, event -> trace.add(event.serverId() + ":" + event.source()));
  private final QuasselCoreInboundPipeline pipeline = pipeline(session);

  @BeforeEach
  void establishSession() {
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 7, List.of(7, 8), Map.of()));
    session.networks.observe(7, "Home");
    session.networks.observe(8, "Work");
    session.nicks.seedPrimaryNetwork(7);
    session.nicks.observe(8, "me-work", AT);
    session.socketRef.set(socket);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
  }

  @Test
  void statusRpcPublishesOnCallerThreadWithServerIdentityWithoutReadiness() throws Exception {
    pipeline.dispatch(rpc("2displayStatusMsg(QString,QString)", List.of("Work", "hello")));
    assertEquals(List.of("ServerResponseLine"), eventTypes());
    assertEquals("core", events.getFirst().serverId());
    var response = assertInstanceOf(IrcEvent.ServerResponseLine.class, events.getFirst().event());
    assertEquals("Work: hello", response.message());
    assertFalse(session.readiness.syncObserved.get());
    assertTrue(writes.isEmpty());
    assertNull(session.readiness.fallbackTask.get());
    assertNull(session.readLoopTask.get());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 0x80})
  void pendingMarkerResolvesBeforeIrcv3TagsAndLiveOrBacklogDelivery(int flags) throws Exception {
    seedBufferAndMarker();
    events.clear();
    onEvent =
        event -> {
          assertEquals(AT.toEpochMilli(), session.history.exactTimestampForMsgId(TARGET, 42));
          assertEquals(8, session.targetNetworkHints.networkIdForTarget(TARGET));
        };
    pipeline.dispatch(
        rpc(
            "2displayMsg(Message)",
            List.of(
                message(
                    42,
                    flags,
                    BUFFER,
                    "@+draft/reply=9;+draft/react=smile PRIVMSG #room :hello"))));
    assertEquals(
        List.of(
            "ReadMarkerObserved",
            "MessageReplyObserved",
            "MessageReactObserved",
            flags == 0 ? "ChannelMessage" : "ChatHistoryBatchReceived"),
        eventTypes());
    var marker = assertInstanceOf(IrcEvent.ReadMarkerObserved.class, events.getFirst().event());
    assertEquals(TARGET, marker.target());
    assertEquals("me-work", marker.from());
    assertEquals("timestamp=2026-10-08T12:00:00.000Z", marker.marker());
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
    assertTrue(events.stream().allMatch(event -> event.serverId().equals("core")));
    assertTrue(writes.isEmpty());
  }

  @Test
  void nativeBacklogObservesHistoryAndMarkersBeforePublishingStableOrderedBatch() throws Exception {
    seedBufferAndMarker();
    events.clear();
    pipeline.dispatch(
        sync(
            SIGNAL_PROXY_SYNC,
            "BacklogManager",
            "",
            "receiveBacklog",
            List.of(
                22,
                List.of(
                    message(43, 0, BUFFER, "later"),
                    message(42, 0, BUFFER, "first"),
                    message(42, 0, BUFFER, "duplicate")))));
    assertEquals(List.of("ReadMarkerObserved", "ChatHistoryBatchReceived"), eventTypes());
    var batch = assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getLast().event());
    assertEquals(TARGET, batch.target());
    assertEquals("quassel-backlog-sync-1", batch.batchId());
    assertEquals(
        List.of("42", "42", "43"),
        batch.entries().stream().map(entry -> entry.messageId()).toList());
    assertEquals(
        List.of("first", "duplicate", "later"),
        batch.entries().stream().map(entry -> entry.text()).toList());
    assertEquals(AT.toEpochMilli(), session.history.exactTimestampForMsgId(TARGET, 42));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
    events.clear();
    pipeline.dispatch(
        sync(SIGNAL_PROXY_SYNC, "BacklogManager", "", "receiveBacklog", List.of(22, List.of())));
    var empty =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getFirst().event());
    assertTrue(empty.entries().isEmpty());
    assertEquals("quassel-backlog-sync-2", empty.batchId());
    assertTrue(writes.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {SIGNAL_PROXY_SYNC, SIGNAL_PROXY_INIT_DATA})
  void networkStateAppliesMembershipCapabilitiesAndNickBeforeReadinessOnce(int type)
      throws Exception {
    session.buffers.merge(new BufferInfoValue(11, 7, 2, -1, "#home"));
    var state =
        sync(
            type,
            "Network",
            "7",
            "init",
            List.of(
                Map.of(
                    "networkName",
                    "Home",
                    "isConnected",
                    true,
                    "capsEnabled",
                    List.of("message-tags"),
                    "myNick",
                    "homeNick")));
    onEvent =
        event -> {
          if (event.event() instanceof IrcEvent.ConnectionReady) {
            assertEquals("homeNick", session.nicks.current());
            assertTrue(session.features.hasAnyCapability("message-tags"));
            assertEquals(7, session.targetNetworkHints.networkIdForTarget("#home"));
          }
        };
    pipeline.dispatch(state);
    assertEquals(
        List.of(
            "JoinedChannel",
            "Ircv3CapabilityChanged",
            "ConnectionFeaturesUpdated",
            "NickChanged",
            "ConnectionReady",
            "ConnectionFeaturesUpdated"),
        eventTypes());
    var joined = assertInstanceOf(IrcEvent.JoinedChannel.class, events.getFirst().event());
    assertEquals("#home{net:home}", joined.channel());
    assertEquals("homeNick", session.nicks.current());
    events.clear();
    pipeline.dispatch(state);
    assertTrue(events.isEmpty());
    assertTrue(session.readiness.connectionReadyEmitted.get());
    assertTrue(writes.isEmpty());
  }

  @Test
  void bufferUpdateRpcMergesMetadataAndNetworkRemovalDiscardsDeferredMarkerAndLateBacklog()
      throws Exception {
    seedBufferAndMarker();
    events.clear();
    trace.clear();
    pipeline.dispatch(
        rpc("2bufferInfoUpdated(BufferInfo)", List.of(new BufferInfoValue(22, -1, 0, -1, ""))));
    assertEquals(BUFFER, session.buffers.get(22));
    assertEquals(8, session.targetNetworkHints.networkIdForTarget(TARGET));
    pipeline.dispatch(
        rpc("2networkRemoved(NetworkId)", List.of(new UserTypeValue("NetworkId", 8))));
    assertNull(session.buffers.get(22));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget(TARGET));
    assertEquals("core:forget-known-network", trace.getLast());
    pipeline.dispatch(
        sync(
            SIGNAL_PROXY_SYNC,
            "BacklogManager",
            "",
            "receiveBacklog",
            List.of(22, List.of(message(42, 0, BUFFER, "late")))));
    assertTrue(events.isEmpty());
  }

  @Test
  void createdNetworkRpcClaimsPendingNameAndFullLifecycleStateUpdatesCapabilities()
      throws Exception {
    session.networks.rememberCreatedName("New Network");
    pipeline.dispatch(
        rpc("2networkCreated(NetworkId)", List.of(new UserTypeValue("NetworkId", 9))));
    assertEquals("New Network", session.networks.displayNames().get(9));
    assertEquals("", session.networks.claimCreatedName());
    assertEquals(List.of("core:observe-known-network"), trace);
    pipeline.dispatch(
        rpc(
            "2networkUpdated(NetworkInfo)",
            List.of(
                new UserTypeValue(
                    "NetworkInfo",
                    Map.of(
                        "networkId",
                        9,
                        "networkName",
                        "Renamed",
                        "capsEnabled",
                        List.of("message-tags"))))));
    assertEquals("Renamed", session.networks.displayNames().get(9));
    assertTrue(session.features.hasAnyCapability("message-tags"));
    assertFalse(session.readiness.syncObserved.get());
  }

  @Test
  void userAndChannelSyncObserveNetworkAndPublishQualifiedEventsThroughPipelinePorts()
      throws Exception {
    pipeline.dispatch(
        sync(
            SIGNAL_PROXY_INIT_DATA,
            "IrcUser",
            "8/bob",
            "init",
            List.of(
                Map.of(
                    "networkId",
                    8,
                    "networkName",
                    "Work",
                    "nick",
                    "bob",
                    "user",
                    "u",
                    "host",
                    "h",
                    "realName",
                    "Bob"))));
    assertEquals(
        List.of(
            "UserHostChanged",
            "UserSetNameObserved",
            "ConnectionReady",
            "ConnectionFeaturesUpdated"),
        eventTypes());
    events.clear();
    pipeline.dispatch(
        sync(
            SIGNAL_PROXY_SYNC,
            "IrcChannel",
            "8/#room",
            "init",
            List.of(Map.of("networkId", 8, "name", "#room", "topic", "A topic"))));
    assertEquals(List.of("ChannelTopicUpdated"), eventTypes());
    var topic = assertInstanceOf(IrcEvent.ChannelTopicUpdated.class, events.getFirst().event());
    assertEquals(TARGET, topic.channel());
    assertEquals("A topic", topic.topic());
    assertEquals("core:observe-known-network", trace.get(trace.size() - 2));
  }

  @Test
  void eventConsumerFailurePropagatesBeforeReadinessAndRetryCanComplete() throws Exception {
    var failure = new IllegalStateException("consumer failed");
    onEvent =
        ignored -> {
          throw failure;
        };
    var frame = sync(SIGNAL_PROXY_INIT_DATA, "BufferSyncer", "", "init", List.of(BUFFER));
    assertSame(failure, assertThrows(IllegalStateException.class, () -> pipeline.dispatch(frame)));
    assertFalse(session.readiness.connectionReadyEmitted.get());
    assertTrue(session.readiness.syncObserved.get());
    assertNull(session.buffers.get(22));
    onEvent = ignored -> {};
    events.clear();
    pipeline.dispatch(frame);
    assertEquals(BUFFER, session.buffers.get(22));
    assertEquals(List.of("ConnectionReady", "ConnectionFeaturesUpdated"), eventTypes());
  }

  @Test
  void separatePipelinesKeepPendingMarkersAndHistoryWithinTheirSession() throws Exception {
    seedBufferAndMarker();
    var other = session("other");
    other.networks.observe(8, "Work");
    other.buffers.merge(BUFFER);
    var otherPipeline = pipeline(other);
    events.clear();
    otherPipeline.dispatch(
        rpc("2displayMsg(Message)", List.of(message(42, 0x80, BUFFER, "other"))));
    assertEquals(List.of("ChatHistoryBatchReceived"), eventTypes());
    assertEquals("other", events.getFirst().serverId());
    assertEquals(
        QuasselCoreHistorySupport.UNKNOWN_MSG_ID,
        session.history.exactTimestampForMsgId(TARGET, 42));
    events.clear();
    pipeline.dispatch(rpc("2displayMsg(Message)", List.of(message(42, 0x80, BUFFER, "core"))));
    assertEquals(List.of("ReadMarkerObserved", "ChatHistoryBatchReceived"), eventTypes());
    assertTrue(events.stream().allMatch(event -> event.serverId().equals("core")));
    var batch = assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getLast().event());
    assertEquals("quassel-backlog-42-1", batch.batchId());
  }

  @Test
  void heartbeatAndReplyUseSessionTransportAndLagWithoutDataOrReadinessEvents() throws Exception {
    var token = session.lag.beginProbe();
    pipeline.dispatch(sync(SIGNAL_PROXY_HEARTBEAT_REPLY, "", "", "", List.of(token)));
    assertTrue(session.lag.lastMeasuredLagMs().isPresent());
    pipeline.dispatch(sync(SIGNAL_PROXY_HEARTBEAT, "", "", "", List.of(token)));
    assertEquals(List.of(socket), writes);
    verify(codec).writeSignalProxyHeartBeatReply(output, token);
    assertTrue(events.isEmpty());
    assertFalse(session.readiness.syncObserved.get());
  }

  @Test
  void heartbeatWriteFailurePropagatesToReadLoopOwner() {
    writeFailure = new IOException("write failed");
    var frame =
        sync(
            SIGNAL_PROXY_HEARTBEAT, "", "", "", List.of(utcDateTimeFromEpochMs(AT.toEpochMilli())));
    assertSame(writeFailure, assertThrows(IOException.class, () -> pipeline.dispatch(frame)));
    assertTrue(events.isEmpty());
    verifyNoInteractions(codec);
  }

  @Test
  void nullAndUnsupportedFramesDoNotMutateOrPublish() throws Exception {
    pipeline.dispatch(null);
    pipeline.dispatch(
        sync(
            SIGNAL_PROXY_INIT_REQUEST, "Network", "7", "init", List.of(Map.of("myNick", "wrong"))));
    assertEquals("me", session.nicks.current());
    assertFalse(session.readiness.syncObserved.get());
    assertTrue(events.isEmpty());
    assertTrue(trace.isEmpty());
    assertTrue(writes.isEmpty());
  }

  private void seedBufferAndMarker() throws IOException {
    pipeline.dispatch(sync(SIGNAL_PROXY_INIT_DATA, "BufferSyncer", "", "init", List.of(BUFFER)));
    pipeline.dispatch(
        sync(SIGNAL_PROXY_SYNC, "BufferSyncer", "", "setMarkerLine", List.of(22, 42)));
    assertTrue(session.nativeReadMarkerSupportObserved.get());
  }

  private QuasselCoreSession session(String id) {
    return new QuasselCoreSession(
        id,
        "me",
        "core.example",
        4242,
        (captured, operation) -> {
          assertSame(caller, Thread.currentThread());
          writes.add(captured);
          if (writeFailure != null) throw writeFailure;
          operation.write(codec, output);
        },
        ignored -> {},
        event -> observe(new ServerIrcEvent(id, event)));
  }

  private QuasselCoreInboundPipeline pipeline(QuasselCoreSession owner) {
    return new QuasselCoreInboundPipeline(
        owner, targets, stateObservations, RUNTIME, this::observe);
  }

  private void observe(ServerIrcEvent event) {
    assertSame(caller, Thread.currentThread());
    events.add(event);
    trace.add(event.event().getClass().getSimpleName());
    onEvent.accept(event);
  }

  private List<String> eventTypes() {
    return events.stream().map(event -> event.event().getClass().getSimpleName()).toList();
  }

  private static MessageValue message(long id, int flags, BufferInfoValue buffer, String text) {
    return new MessageValue(id, AT.getEpochSecond(), 1, flags, buffer, "bob!u@h", text);
  }

  private static SignalProxyMessage rpc(String slot, List<Object> params) {
    return sync(SIGNAL_PROXY_RPC_CALL, "", "", slot, params);
  }

  private static SignalProxyMessage sync(
      int type, String clazz, String object, String slot, List<Object> params) {
    return new SignalProxyMessage(type, clazz, object, slot, params);
  }
}
