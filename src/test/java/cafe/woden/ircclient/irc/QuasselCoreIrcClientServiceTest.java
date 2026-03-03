package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QuasselCoreIrcClientServiceTest {
  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void reportsQuasselCoreBackend() {
    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            mock(ServerCatalog.class),
            mock(QuasselCoreSocketConnector.class),
            mock(QuasselCoreProtocolProbe.class),
            mock(QuasselCoreAuthHandshake.class),
            mock(QuasselCoreDatastreamCodec.class));

    assertEquals(IrcProperties.Server.Backend.QUASSEL_CORE, service.backend());
    assertEquals(
        "Quassel Core backend is not connected", service.backendAvailabilityReason("quassel"));
  }

  @Test
  void connectAndDisconnectEmitLifecycleEvents() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    assertEquals(3, events.values().size());
    IrcEvent.Connecting connecting =
        assertInstanceOf(IrcEvent.Connecting.class, events.values().get(0).event());
    assertEquals("irc.example.net", connecting.serverHost());
    assertEquals(4242, connecting.serverPort());
    IrcEvent.Connected connected =
        assertInstanceOf(IrcEvent.Connected.class, events.values().get(1).event());
    assertEquals("quassel", connected.nick());
    IrcEvent.ConnectionFeaturesUpdated updated =
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.values().get(2).event());
    assertTrue(updated.source().contains("quassel-probe protocol=datastream"));

    assertEquals("quassel", service.currentNick("quassel").orElseThrow());
    assertEquals("", service.backendAvailabilityReason("quassel"));

    service.disconnect("quassel").blockingAwait();
    events.awaitCount(4);

    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, events.values().get(3).event());
    assertEquals("Client requested disconnect", disconnected.reason());
    assertTrue(service.currentNick("quassel").isEmpty());
    verify(connector).connect(server);
    verify(protocolProbe).negotiate(socket);
    verify(authHandshake).authenticate(socket, server);
  }

  @Test
  void sendRawUsesQuasselRpcSendInputAfterSessionHandshake() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("quassel", "WHOIS alice").blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            socket.getOutputStream(),
            "2sendInput(BufferInfo,QString)",
            List.of(
                new QuasselCoreDatastreamCodec.BufferInfoValue(-1, 1, 0x01, -1, ""),
                "/QUOTE WHOIS alice"));
  }

  @Test
  void sendToChannelUsesMatchingBufferNetworkWhenAvailable() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue net2Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(20, 2, 0x02, -1, "#other");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1, 2), Map.of(20, net2Buffer)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.sendToChannel("quassel", "#other", "hello net2").blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            socket.getOutputStream(),
            "2sendInput(BufferInfo,QString)",
            List.of(net2Buffer, "hello net2"));
  }

  @Test
  void qualifiedTargetRoutesSendAndHistoryToNamedNetworkBuffer() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue net1Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#dupe");
    QuasselCoreDatastreamCodec.BufferInfoValue net2Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(22, 2, 0x02, -1, "#dupe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1, 2), Map.of(11, net1Buffer, 22, net2Buffer)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.sendToChannel("quassel", "#dupe{net:network-2}", "reply net2").blockingAwait();
    service
        .requestChatHistoryBefore("quassel", "#dupe{net:network-2}", "msgid=100", 20)
        .blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            socket.getOutputStream(),
            "2sendInput(BufferInfo,QString)",
            List.of(net2Buffer, "reply net2"));
    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BacklogManager",
            "global",
            "requestBacklog(BufferId,MsgId,MsgId,int,int)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 22),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", -1),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 99),
                20,
                0));
  }

  @Test
  void capabilityFlagsReflectQuasselParityDefaults() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    assertFalse(service.isChatHistoryAvailable("quassel"));
    assertFalse(service.isEchoMessageAvailable("quassel"));

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);
    long deadline = System.currentTimeMillis() + 2_000L;
    while (!service.isChatHistoryAvailable("quassel") && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L);
    }

    assertTrue(service.isChatHistoryAvailable("quassel"));
    assertTrue(service.isEchoMessageAvailable("quassel"));
    assertTrue(service.isDraftReplyAvailable("quassel"));
    assertTrue(service.isDraftReactAvailable("quassel"));
    assertTrue(service.isDraftUnreactAvailable("quassel"));
    assertTrue(service.isMessageEditAvailable("quassel"));
    assertTrue(service.isMessageRedactionAvailable("quassel"));
    assertTrue(service.isTypingAvailable("quassel"));
    assertTrue(service.isReadMarkerAvailable("quassel"));
    assertTrue(service.isLabeledResponseAvailable("quassel"));
    assertTrue(service.isStandardRepliesAvailable("quassel"));
    assertFalse(service.isMonitorAvailable("quassel"));
    assertEquals("", service.typingAvailabilityReason("quassel"));
  }

  @Test
  void sendRawRoutesTagmsgToTargetBufferAndStripsNetworkQualifier() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue net1Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#dupe");
    QuasselCoreDatastreamCodec.BufferInfoValue net2Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(22, 2, 0x02, -1, "#dupe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1, 2), Map.of(11, net1Buffer, 22, net2Buffer)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("quassel", "TAGMSG #dupe{net:network-2}").blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            socket.getOutputStream(),
            "2sendInput(BufferInfo,QString)",
            List.of(net2Buffer, "/QUOTE TAGMSG #dupe"));
  }

  @Test
  void sendTypingUsesTargetAwareRoutingAndNormalizedState() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue net1Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#dupe");
    QuasselCoreDatastreamCodec.BufferInfoValue net2Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(22, 2, 0x02, -1, "#dupe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1, 2), Map.of(11, net1Buffer, 22, net2Buffer)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);
    assertTrue(service.isTypingAvailable("quassel"));

    service.sendTyping("quassel", "#dupe{net:network-2}", "composing").blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            socket.getOutputStream(),
            "2sendInput(BufferInfo,QString)",
            List.of(net2Buffer, "/QUOTE @+typing=active TAGMSG #dupe"));
  }

  @Test
  void sendReadMarkerPrefersBufferSyncerWhenMessageAnchorIsKnown() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    100L, 1_700_000_000L, 0x0001, 0, chan, "alice!u@h", "older"))));
    events.awaitCount(4);

    service
        .sendReadMarker("quassel", "#ircafe", java.time.Instant.ofEpochSecond(1_700_000_001L))
        .blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BufferSyncer",
            "global",
            "requestSetMarkerLine(BufferId,MsgId)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 100)));
    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BufferSyncer",
            "global",
            "requestSetLastSeenMsg(BufferId,MsgId)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 100)));
  }

  @Test
  void sendReadMarkerFallsBackToMarkreadRawWhenNoAnchorExists() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service
        .sendReadMarker("quassel", "#ircafe", java.time.Instant.ofEpochSecond(1_700_000_001L))
        .blockingAwait();

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<Object>> paramsCaptor =
        (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            any(OutputStream.class), any(String.class), paramsCaptor.capture());
    List<Object> params = paramsCaptor.getValue();
    assertEquals(2, params.size());
    assertEquals(chan, params.get(0));
    assertTrue(
        Objects.toString(params.get(1), "").startsWith("/QUOTE MARKREAD #ircafe timestamp="));
  }

  @Test
  void networkCapabilitySnapshotControlsAdvancedIrcv3Availability() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "Network".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("capsEnabled", List.of("batch")))));
    events.awaitCount(5);

    assertFalse(service.isDraftReplyAvailable("quassel"));
    assertFalse(service.isTypingAvailable("quassel"));
    assertFalse(service.isReadMarkerAvailable("quassel"));
    assertEquals(
        "message-tags not negotiated in Quassel backend network state",
        service.typingAvailabilityReason("quassel"));
  }

  @Test
  void bufferSyncerMarkerSyncEmitsReadMarkerObservedEvent() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "BufferSyncer".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "global".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "setMarkerLine(BufferId,MsgId)".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 42))));

    events.awaitDone(2, TimeUnit.SECONDS);
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.ReadMarkerObserved marker
                        && "#ircafe".equals(marker.target())
                        && marker.marker().startsWith("timestamp=")));
  }

  @Test
  void bufferSyncerMarkerUsesObservedMessageTimestampWhenKnown() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    long knownEpochSeconds = 1_700_000_500L;
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    4242L, knownEpochSeconds, 0x0001, 0, chan, "alice!u@h", "known ts"))));
    events.awaitCount(4);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "BufferSyncer".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "global".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "setMarkerLine(BufferId,MsgId)".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 4242))));

    events.awaitDone(2, TimeUnit.SECONDS);
    IrcEvent.ReadMarkerObserved markerEvent =
        events.values().stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.ReadMarkerObserved.class::isInstance)
            .map(IrcEvent.ReadMarkerObserved.class::cast)
            .reduce((first, second) -> second)
            .orElseThrow();
    String token = markerEvent.marker();
    assertTrue(token.startsWith("timestamp="));
    java.time.Instant parsed = java.time.Instant.parse(token.substring("timestamp=".length()));
    assertEquals(knownEpochSeconds, parsed.getEpochSecond());
  }

  @Test
  void inboundTaggedPrivmsgCarriesIrcv3TagsAndEmitsReplyObservation() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    501L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0001,
                    0,
                    chan,
                    "alice!u@h",
                    "@+draft/reply=42 :alice!u@h PRIVMSG #ircafe :hello tagged"))));

    events.awaitDone(2, TimeUnit.SECONDS);
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.ChannelMessage msg
                        && "#ircafe".equals(msg.channel())
                        && "hello tagged".equals(msg.text())
                        && "42".equals(msg.ircv3Tags().get("draft/reply"))));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MessageReplyObserved reply
                        && "alice".equals(reply.from())
                        && "#ircafe".equals(reply.target())
                        && "42".equals(reply.replyToMsgId())));
  }

  @Test
  void inboundTaggedTagmsgEmitsSignalsWithoutChannelMessage() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    502L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0001,
                    0,
                    chan,
                    "alice!u@h",
                    "@+typing=active;+draft/react=thumbsup;+draft/reply=42;+draft/unreact=thumbsup;+draft/delete=99;+draft/read-marker=timestamp=2026-03-03T12:00:00.000Z :alice!u@h TAGMSG #ircafe"))));

    events.awaitDone(2, TimeUnit.SECONDS);
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(ev -> ev instanceof IrcEvent.UserTypingObserved));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MessageReactObserved react
                        && "thumbsup".equals(react.reaction())
                        && "42".equals(react.messageId())));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MessageUnreactObserved unreact
                        && "thumbsup".equals(unreact.reaction())
                        && "42".equals(unreact.messageId())));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MessageRedactionObserved redaction
                        && "99".equals(redaction.messageId())));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.ReadMarkerObserved marker
                        && marker.marker().contains("timestamp=")));
    assertFalse(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev -> ev instanceof IrcEvent.ChannelMessage msg && "502".equals(msg.messageId())));
  }

  @Test
  void inboundRedactCommandLineEmitsMessageRedactionObserved() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    503L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0001,
                    0,
                    chan,
                    "alice!u@h",
                    ":alice!u@h REDACT #ircafe 777 :cleanup"))));

    events.awaitDone(2, TimeUnit.SECONDS);
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MessageRedactionObserved redaction
                        && "#ircafe".equals(redaction.target())
                        && "777".equals(redaction.messageId())));
  }

  @Test
  void requestLagProbeUsesSignalProxyHeartbeatAndRecordsRtt() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.requestLagProbe("quassel").blockingAwait();

    ArgumentCaptor<QuasselCoreDatastreamCodec.QtDateTimeValue> probeCaptor =
        ArgumentCaptor.forClass(QuasselCoreDatastreamCodec.QtDateTimeValue.class);
    verify(datastreamCodec)
        .writeSignalProxyHeartBeat(any(OutputStream.class), probeCaptor.capture());
    QuasselCoreDatastreamCodec.QtDateTimeValue token = probeCaptor.getValue();

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT_REPLY, token)));

    long deadline = System.currentTimeMillis() + 2_000L;
    while (service.lastMeasuredLagMs("quassel").isEmpty()
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L);
    }
    assertTrue(service.lastMeasuredLagMs("quassel").isPresent());
  }

  @Test
  void requestChatHistoryBeforeSelectorUsesBacklogSyncCall() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.requestChatHistoryBefore("quassel", "#ircafe", "msgid=100", 25).blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BacklogManager",
            "global",
            "requestBacklog(BufferId,MsgId,MsgId,int,int)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", -1),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 99),
                25,
                0));
  }

  @Test
  void requestChatHistoryLatestAroundAndBetweenUseBacklogSyncCalls() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.requestChatHistoryLatest("quassel", "#ircafe", "msgid=100", 30).blockingAwait();
    service.requestChatHistoryAround("quassel", "#ircafe", "msgid=100", 20).blockingAwait();
    service
        .requestChatHistoryBetween("quassel", "#ircafe", "msgid=80", "msgid=100", 40)
        .blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BacklogManager",
            "global",
            "requestBacklog(BufferId,MsgId,MsgId,int,int)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 101),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", -1),
                30,
                0));
    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BacklogManager",
            "global",
            "requestBacklog(BufferId,MsgId,MsgId,int,int)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 90),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 110),
                20,
                0));
    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BacklogManager",
            "global",
            "requestBacklog(BufferId,MsgId,MsgId,int,int)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 80),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 100),
                40,
                0));
  }

  @Test
  void requestChatHistoryLatestWildcardUsesDefaultWindowAndClampedLimit() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.requestChatHistoryLatest("quassel", "#ircafe", "*", 999).blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BacklogManager",
            "global",
            "requestBacklog(BufferId,MsgId,MsgId,int,int)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", -1),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", -1),
                200,
                0));
  }

  @Test
  void requestChatHistoryBeforeTimestampSelectorUsesObservedMessageAnchor() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    100L, 1_700_000_000L, 0x0001, 0, chan, "alice!u@h", "older"))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    200L, 1_700_000_100L, 0x0001, 0, chan, "alice!u@h", "newer"))));
    events.awaitCount(5);

    String selector = "timestamp=" + java.time.Instant.ofEpochSecond(1_700_000_050L);
    service.requestChatHistoryBefore("quassel", "#ircafe", selector, 25).blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxySync(
            socket.getOutputStream(),
            "BacklogManager",
            "global",
            "requestBacklog(BufferId,MsgId,MsgId,int,int)",
            List.of(
                new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", -1),
                new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 99),
                25,
                0));
  }

  @Test
  void requestChatHistoryRejectsInvalidTimestampSelector() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service
                    .requestChatHistoryBefore(
                        "quassel", "#ircafe", "timestamp=not-a-real-instant", 10)
                    .blockingAwait());
    assertTrue(err.getMessage().contains("ISO-8601"));
  }

  @Test
  void inboundDisplayMsgAndStatusMsgAreBridgedToIrcEvents() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue knownChannelBuffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1), Map.of(11, knownChannelBuffer)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    QuasselCoreDatastreamCodec.MessageValue message =
        new QuasselCoreDatastreamCodec.MessageValue(
            42L,
            1_700_000_000L,
            0x0001,
            0,
            new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0, -1, ""),
            "alice!user@example.net",
            "hello from core");

    socket.writeInbound(encodeRpcCall(datastreamCodec, "2displayMsg(Message)", List.of(message)));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayStatusMsg(QString,QString)",
            List.of("libera", "connected via bouncer")));

    events.awaitCount(5);

    IrcEvent.ChannelMessage channelMessage =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("#ircafe", channelMessage.channel());
    assertEquals("alice", channelMessage.from());
    assertEquals("hello from core", channelMessage.text());
    assertEquals("42", channelMessage.messageId());

    IrcEvent.ServerResponseLine status =
        assertInstanceOf(IrcEvent.ServerResponseLine.class, events.values().get(4).event());
    assertEquals("libera: connected via bouncer", status.message());

    service.disconnect("quassel").blockingAwait();
  }

  @Test
  void numericServerRepliesPreserveIrcCodeAndRawLine() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue status =
        new QuasselCoreDatastreamCodec.BufferInfoValue(3, 1, 0x01, -1, "status");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(3, status)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    44L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    ":irc.example.net 401 quassel Ghost :No such nick/channel"))));

    events.awaitCount(4);
    IrcEvent.ServerResponseLine response =
        assertInstanceOf(IrcEvent.ServerResponseLine.class, events.values().get(3).event());
    assertEquals(401, response.code());
    assertEquals("No such nick/channel", response.message());
    assertEquals(":irc.example.net 401 quassel Ghost :No such nick/channel", response.rawLine());
  }

  @Test
  void syncFrameEmitsConnectionReadyLifecycleEvent() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "BufferSyncer".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "global".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

    events.awaitCount(5);
    assertInstanceOf(IrcEvent.ConnectionReady.class, events.values().get(3).event());
    IrcEvent.ConnectionFeaturesUpdated updated =
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.values().get(4).event());
    assertEquals("quassel-phase=sync-ready;detail=quassel-sync", updated.source());
  }

  @Test
  void typedDisplayMessagesMapToStructuredIrcEvents() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(1, 0x0020, 0, "quassel!u@h", "joined", 11, ""))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(2, 0x0020, 0, "alice!u@h", "joined", 11, ""))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(3, 0x0040, 0, "alice!u@h", "(bye)", 11, ""))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                message(4, 0x0008, 0, "quassel!u@h", "quassel is now known as quassel2", 11, ""))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(5, 0x4000, 0, "alice!u@h", "changed topic to \"new topic\"", 11, ""))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(6, 0x0010, 0, "ops!u@h", "set mode +o alice", 11, ""))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(7, 0x0100, 0, "ops!u@h", "kicked quassel2 (gone)", 11, ""))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(8, 0x20000, 0, "ops!u@h", "invited you to #other", 11, ""))));

    events.awaitCount(12);

    assertInstanceOf(IrcEvent.JoinedChannel.class, events.values().get(3).event());
    assertInstanceOf(IrcEvent.UserJoinedChannel.class, events.values().get(4).event());
    assertInstanceOf(IrcEvent.UserPartedChannel.class, events.values().get(5).event());
    assertInstanceOf(IrcEvent.UserNickChangedChannel.class, events.values().get(6).event());
    IrcEvent.NickChanged nickChanged =
        assertInstanceOf(IrcEvent.NickChanged.class, events.values().get(7).event());
    assertEquals("quassel", nickChanged.oldNick());
    assertEquals("quassel2", nickChanged.newNick());
    IrcEvent.ChannelTopicUpdated topic =
        assertInstanceOf(IrcEvent.ChannelTopicUpdated.class, events.values().get(8).event());
    assertEquals("new topic", topic.topic());
    IrcEvent.ChannelModeObserved mode =
        assertInstanceOf(IrcEvent.ChannelModeObserved.class, events.values().get(9).event());
    assertEquals("+o alice", mode.details());
    assertEquals(IrcEvent.ChannelModeKind.DELTA, mode.kind());
    assertEquals(IrcEvent.ChannelModeProvenance.QUASSEL_DISPLAY_MESSAGE, mode.provenance());
    IrcEvent.KickedFromChannel kicked =
        assertInstanceOf(IrcEvent.KickedFromChannel.class, events.values().get(10).event());
    assertEquals("gone", kicked.reason());
    IrcEvent.InvitedToChannel invite =
        assertInstanceOf(IrcEvent.InvitedToChannel.class, events.values().get(11).event());
    assertEquals("#other", invite.channel());
    assertEquals("quassel2", service.currentNick("quassel").orElseThrow());
  }

  @Test
  void syncIrcUserAndIrcChannelStateEmitStructuredUserAndTopicEvents() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "IrcUser".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1/alice".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of(
                    "nick", "alice",
                    "user", "auser",
                    "host", "example.net",
                    "realName", "Alice Liddell",
                    "account", "alice-account",
                    "away", true,
                    "awayMessage", "coffee"))));
    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "IrcChannel".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1/#ircafe".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("name", "#ircafe", "topic", "Topic from sync"))));

    events.awaitDone(2, TimeUnit.SECONDS);

    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.UserHostChanged host
                        && "alice".equals(host.nick())
                        && "auser".equals(host.user())
                        && "example.net".equals(host.host())));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.UserSetNameObserved setName
                        && "alice".equals(setName.nick())
                        && "Alice Liddell".equals(setName.realName())));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.UserAccountStateObserved account
                        && "alice".equals(account.nick())
                        && IrcEvent.AccountState.LOGGED_IN == account.accountState()));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.UserAwayStateObserved away
                        && "alice".equals(away.nick())
                        && IrcEvent.AwayState.AWAY == away.awayState()));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.ChannelTopicUpdated topic
                        && "#ircafe".equals(topic.channel())
                        && "Topic from sync".equals(topic.topic())));
  }

  @Test
  void backlogFlagDisplayMessageBridgesToChatHistoryBatch() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue chan =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#ircafe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of(11, chan)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(91, 0x0001, 0x80, "alice!u@h", "historic line", 11, ""))));

    events.awaitCount(4);
    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals("#ircafe", batch.target());
    assertEquals(1, batch.entries().size());
    assertEquals("historic line", batch.entries().get(0).text());
  }

  @Test
  void connectFailureEmitsErrorAndDisconnectedEvents() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = mock(QuasselCoreDatastreamCodec.class);
    IrcProperties.Server server = server();

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenThrow(new IOException("connection refused"));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    assertEquals(3, events.values().size());
    assertInstanceOf(IrcEvent.Connecting.class, events.values().get(0).event());
    IrcEvent.Error error = assertInstanceOf(IrcEvent.Error.class, events.values().get(1).event());
    assertTrue(error.message().contains("connection refused"));
    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, events.values().get(2).event());
    assertTrue(disconnected.reason().contains("connection refused"));
  }

  @Test
  void coreSetupRequiredEmitsFeatureMarkerAndDoesNotScheduleReconnect() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenThrow(
            new QuasselCoreAuthHandshake.CoreSetupRequiredException(
                "Quassel Core setup is required before login", Map.of("Configured", false)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitDone(2, TimeUnit.SECONDS);

    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.ConnectionFeaturesUpdated updated
                        && updated.source().startsWith("quassel-phase=setup-required")),
        "expected setup-required feature marker");
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .noneMatch(ev -> ev instanceof IrcEvent.Reconnecting),
        "setup-required should not enter reconnect loop");
    assertTrue(service.backendAvailabilityReason("quassel").toLowerCase().contains("setup"));
  }

  @Test
  void connectFailureSchedulesReconnectWhenPolicyIsEnabled() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    IrcProperties props =
        new IrcProperties(
            new IrcProperties.Client(
                "IRCafe", new IrcProperties.Reconnect(true, 10, 10, 2.0, 0.0, 2), null, null, null),
            List.of(server));

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(serverCatalog.containsId("quassel")).thenReturn(true);
    when(connector.connect(server)).thenThrow(new IOException("first failure")).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec, props);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitDone(2, TimeUnit.SECONDS);

    assertTrue(
        events.values().stream().anyMatch(sev -> sev.event() instanceof IrcEvent.Reconnecting),
        "expected reconnect lifecycle event after connect failure");
    verify(connector, times(2)).connect(server);
    service.disconnect("quassel").blockingAwait();
  }

  @Test
  void inboundMessageHintRoutesDuplicateChannelNameToObservedNetwork() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec =
        org.mockito.Mockito.spy(new QuasselCoreDatastreamCodec());
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue net1Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#dupe");
    QuasselCoreDatastreamCodec.BufferInfoValue net2Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(22, 2, 0x02, -1, "#dupe");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1, 2), Map.of(11, net1Buffer, 22, net2Buffer)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    QuasselCoreDatastreamCodec.MessageValue net2Inbound =
        new QuasselCoreDatastreamCodec.MessageValue(
            1001L,
            TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
            0x0001,
            0,
            new QuasselCoreDatastreamCodec.BufferInfoValue(22, 2, 0x02, -1, ""),
            "alice!u@h",
            "hello from network 2");
    socket.writeInbound(
        encodeRpcCall(datastreamCodec, "2displayMsg(Message)", List.of(net2Inbound)));
    events.awaitCount(4);

    service.sendToChannel("quassel", "#dupe", "reply").blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            socket.getOutputStream(),
            "2sendInput(BufferInfo,QString)",
            List.of(net2Buffer, "reply"));
    service.disconnect("quassel").blockingAwait();
  }

  @Test
  void networkScopedNickStateDrivesSelfJoinDetectionForNonPrimaryNetwork() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probeSelection =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.BufferInfoValue net1Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#one");
    QuasselCoreDatastreamCodec.BufferInfoValue net2Buffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(22, 2, 0x02, -1, "#two");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1, 2), Map.of(11, net1Buffer, 22, net2Buffer)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "Network".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "2".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("myNick", "sidecar"))));

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    99L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0020,
                    0,
                    new QuasselCoreDatastreamCodec.BufferInfoValue(22, 2, 0x02, -1, "#two"),
                    "sidecar!u@h",
                    "joined"))));

    events.awaitCount(6);
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.JoinedChannel joined
                        && "#two{net:network-2}".equals(joined.channel())),
        "expected network-scoped self nick to map JOIN to JoinedChannel");
    assertEquals("quassel", service.currentNick("quassel").orElseThrow());
  }

  @Test
  void unsupportedProtocolSelectionEmitsErrorAndDisconnects() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = mock(QuasselCoreDatastreamCodec.class);
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket))
        .thenReturn(
            new QuasselCoreProtocolProbe.ProbeSelection(
                0x00000001, QuasselCoreProtocolProbe.PROTOCOL_LEGACY, 0, 0));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    assertInstanceOf(IrcEvent.Connecting.class, events.values().get(0).event());
    IrcEvent.Error err = assertInstanceOf(IrcEvent.Error.class, events.values().get(1).event());
    assertTrue(err.message().contains("unsupported protocol"));
    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, events.values().get(2).event());
    assertTrue(disconnected.reason().contains("unsupported protocol"));
  }

  @Test
  void sendOperationsReturnBackendUnavailableWhenSessionIsNotEstablished() {
    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            mock(ServerCatalog.class),
            mock(QuasselCoreSocketConnector.class),
            mock(QuasselCoreProtocolProbe.class),
            mock(QuasselCoreAuthHandshake.class),
            mock(QuasselCoreDatastreamCodec.class));

    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.sendToChannel("quassel", "#chan", "hello").blockingAwait());
    assertEquals(IrcProperties.Server.Backend.QUASSEL_CORE, err.backend());
    assertEquals("send message to channel", err.operation());
    assertEquals("quassel", err.serverId());
  }

  private static IrcProperties.Server server() {
    return new IrcProperties.Server(
        "quassel",
        "irc.example.net",
        4242,
        false,
        "",
        "quassel",
        "quassel",
        "Quassel Test",
        null,
        null,
        List.of(),
        List.of(),
        null,
        IrcProperties.Server.Backend.QUASSEL_CORE);
  }

  private static byte[] encodeRpcCall(
      QuasselCoreDatastreamCodec codec, String slotName, List<Object> params) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    codec.writeSignalProxyRpcCall(out, slotName, params);
    return out.toByteArray();
  }

  private static byte[] encodeSignalProxyFrame(List<Object> payloadItems) throws IOException {
    byte[] payload = QuasselCoreDatastreamCodec.encodeSignalProxyPayload(payloadItems);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write((payload.length >>> 24) & 0xff);
    out.write((payload.length >>> 16) & 0xff);
    out.write((payload.length >>> 8) & 0xff);
    out.write(payload.length & 0xff);
    out.write(payload);
    return out.toByteArray();
  }

  private static QuasselCoreDatastreamCodec.MessageValue message(
      long messageId,
      int typeBits,
      int flags,
      String sender,
      String content,
      int bufferId,
      String bufferName) {
    return new QuasselCoreDatastreamCodec.MessageValue(
        messageId,
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
        typeBits,
        flags,
        new QuasselCoreDatastreamCodec.BufferInfoValue(bufferId, 1, 0x02, -1, bufferName),
        sender,
        content);
  }

  private static final class BlockingSocket extends Socket {
    private final PipedInputStream input;
    private final PipedOutputStream inputWriter;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private volatile boolean closed;

    private BlockingSocket() throws IOException {
      this.input = new PipedInputStream();
      this.inputWriter = new PipedOutputStream(input);
    }

    @Override
    public InputStream getInputStream() {
      return input;
    }

    @Override
    public OutputStream getOutputStream() {
      return output;
    }

    @Override
    public synchronized void close() throws IOException {
      if (closed) return;
      closed = true;
      inputWriter.close();
      input.close();
    }

    @Override
    public boolean isClosed() {
      return closed;
    }

    private void writeInbound(byte[] frame) throws IOException {
      inputWriter.write(frame);
      inputWriter.flush();
    }
  }
}
