package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.*;
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
import java.util.function.Predicate;
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
    assertFalse(service.isDraftReplyAvailable("quassel"));
    assertFalse(service.isDraftReactAvailable("quassel"));
    assertFalse(service.isDraftUnreactAvailable("quassel"));
    assertFalse(service.isMessageEditAvailable("quassel"));
    assertFalse(service.isMessageRedactionAvailable("quassel"));
    assertFalse(service.isTypingAvailable("quassel"));
    assertFalse(service.isReadMarkerAvailable("quassel"));
    assertFalse(service.isLabeledResponseAvailable("quassel"));
    assertFalse(service.isStandardRepliesAvailable("quassel"));
    assertFalse(service.isMonitorAvailable("quassel"));
    assertEquals(
        "typing capability status is not yet available from Quassel backend state",
        service.typingAvailabilityReason("quassel"));
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

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "Network".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("capsEnabled", List.of("message-tags", "typing")))));
    long typingReadyDeadline = System.currentTimeMillis() + 2_000L;
    while (!service.isTypingAvailable("quassel")
        && System.currentTimeMillis() < typingReadyDeadline) {
      Thread.sleep(10L);
    }
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
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "Network".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("capsEnabled", List.of("read-marker")))));
    long readMarkerReadyDeadline = System.currentTimeMillis() + 2_000L;
    while (!service.isReadMarkerAvailable("quassel")
        && System.currentTimeMillis() < readMarkerReadyDeadline) {
      Thread.sleep(10L);
    }

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    100L, 1_700_000_000L, 0x0001, 0, chan, "alice!u@h", "older"))));
    long historyObservedDeadline = System.currentTimeMillis() + 2_000L;
    while (events.values().stream()
            .map(ServerIrcEvent::event)
            .noneMatch(
                ev ->
                    ev instanceof IrcEvent.ChannelMessage msg
                        && "100".equals(msg.messageId())
                        && "#ircafe".equals(msg.channel()))
        && System.currentTimeMillis() < historyObservedDeadline) {
      Thread.sleep(10L);
    }

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

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "Network".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("capsEnabled", List.of("read-marker")))));
    long readMarkerReadyDeadline = System.currentTimeMillis() + 2_000L;
    while (!service.isReadMarkerAvailable("quassel")
        && System.currentTimeMillis() < readMarkerReadyDeadline) {
      Thread.sleep(10L);
    }

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
  void monitorAvailabilityAndLimitAreReadFromNetworkState() throws Exception {
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
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("networkName", "libera", "supportsMonitor", true, "monitorLimit", 250))));

    long deadline = System.currentTimeMillis() + 2_000L;
    while (!service.isMonitorAvailable("quassel") && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L);
    }
    assertTrue(service.isMonitorAvailable("quassel"));
    assertEquals(250, service.negotiatedMonitorLimit("quassel"));
  }

  @Test
  void quasselNetworkListReflectsObservedNetworkInfoState() throws Exception {
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
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1, 2), Map.of()));

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
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "2".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of(
                    "networkName",
                    "libera",
                    "isConnected",
                    true,
                    "identity",
                    7,
                    "ServerList",
                    List.of(Map.of("server", "irc.libera.chat", "port", 6697, "useSSL", true))))));

    long deadline = System.currentTimeMillis() + 2_000L;
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks = List.of();
    while (System.currentTimeMillis() < deadline) {
      networks = service.quasselCoreNetworks("quassel");
      if (networks.stream().anyMatch(n -> n.networkId() == 2 && "libera".equals(n.networkName()))) {
        break;
      }
      Thread.sleep(10L);
    }

    QuasselCoreControlPort.QuasselCoreNetworkSummary libera =
        networks.stream().filter(n -> n.networkId() == 2).findFirst().orElseThrow();
    assertEquals("libera", libera.networkName());
    assertTrue(libera.connected());
    assertEquals(7, libera.identityId());
    assertEquals("irc.libera.chat", libera.serverHost());
    assertEquals(6697, libera.serverPort());
    assertTrue(libera.useTls());
  }

  @Test
  void quasselNetworkListReflectsUnknownNetworkSyncClassState() throws Exception {
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
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", -1, List.of(), Map.of()));

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
                "NetworkConfig".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "global".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of(
                    "networkId",
                    9,
                    "networkName",
                    "libera",
                    "ServerList",
                    List.of(Map.of("server", "irc.libera.chat", "port", 6697, "useSSL", true))))));

    long deadline = System.currentTimeMillis() + 2_000L;
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks = List.of();
    while (System.currentTimeMillis() < deadline) {
      networks = service.quasselCoreNetworks("quassel");
      if (networks.stream().anyMatch(n -> n.networkId() == 9 && "libera".equals(n.networkName()))) {
        break;
      }
      Thread.sleep(10L);
    }

    QuasselCoreControlPort.QuasselCoreNetworkSummary libera =
        networks.stream().filter(n -> n.networkId() == 9).findFirst().orElseThrow();
    assertEquals("libera", libera.networkName());
    assertEquals("irc.libera.chat", libera.serverHost());
    assertEquals(6697, libera.serverPort());
    assertTrue(libera.useTls());
  }

  @Test
  void quasselNetworkListReflectsFlatNetworkInitDataState() throws Exception {
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
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 5, List.of(5), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_INIT_DATA,
                "Network".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "5".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "NetworkName".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "libera",
                "identityId".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                1,
                "ServerList".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                List.of(Map.of("Host", "irc.libera.chat", "Port", 6697, "UseSSL", true)),
                "UseAutoReconnect".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                true,
                "AutoReconnectInterval".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                60,
                "AutoReconnectRetries".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                20)));

    long deadline = System.currentTimeMillis() + 2_000L;
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks = List.of();
    while (System.currentTimeMillis() < deadline) {
      networks = service.quasselCoreNetworks("quassel");
      if (networks.stream().anyMatch(n -> n.networkId() == 5 && "libera".equals(n.networkName()))) {
        break;
      }
      Thread.sleep(10L);
    }

    QuasselCoreControlPort.QuasselCoreNetworkSummary libera =
        networks.stream().filter(n -> n.networkId() == 5).findFirst().orElseThrow();
    assertEquals("libera", libera.networkName());
    assertEquals(1, libera.identityId());
    assertEquals("irc.libera.chat", libera.serverHost());
    assertEquals(6697, libera.serverPort());
    assertTrue(libera.useTls());
  }

  @Test
  void quasselNetworkConnectAndDisconnectUseNetworkRpcCalls() throws Exception {
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

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("networkName", "libera"))));

    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline
        && service.quasselCoreNetworks("quassel").stream()
            .noneMatch(n -> n.networkId() == 1 && "libera".equals(n.networkName()))) {
      Thread.sleep(10L);
    }

    service.quasselCoreConnectNetwork("quassel", "libera").blockingAwait();
    service.quasselCoreDisconnectNetwork("quassel", "1").blockingAwait();

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<Object>> connectParamsCaptor =
        (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    verify(datastreamCodec)
        .writeSignalProxySync(
            eq(socket.getOutputStream()),
            eq("Network"),
            eq("1"),
            eq("requestConnect"),
            connectParamsCaptor.capture());
    List<Object> connectParams = connectParamsCaptor.getValue();
    assertTrue(connectParams.isEmpty());

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<Object>> disconnectParamsCaptor =
        (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    verify(datastreamCodec)
        .writeSignalProxySync(
            eq(socket.getOutputStream()),
            eq("Network"),
            eq("1"),
            eq("requestDisconnect"),
            disconnectParamsCaptor.capture());
    List<Object> disconnectParams = disconnectParamsCaptor.getValue();
    assertTrue(disconnectParams.isEmpty());
  }

  @Test
  void quasselCreateAndRemoveNetworkUseRpcCalls() throws Exception {
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

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("networkName", "existing", "identity", 42))));
    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline
        && service.quasselCoreNetworks("quassel").stream()
            .noneMatch(n -> n.networkId() == 1 && n.identityId() == 42)) {
      Thread.sleep(10L);
    }

    QuasselCoreControlPort.QuasselCoreNetworkCreateRequest createRequest =
        new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
            "libera", "irc.libera.chat", 6697, true, "", true, null, List.of("#ircafe"));
    QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest updateRequest =
        new QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest(
            "", "irc2.libera.chat", 6667, false, "", true, null, null);
    service.quasselCoreCreateNetwork("quassel", createRequest).blockingAwait();
    service.quasselCoreUpdateNetwork("quassel", "1", updateRequest).blockingAwait();
    service.quasselCoreRemoveNetwork("quassel", "1").blockingAwait();

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<Object>> createParamsCaptor =
        (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            eq(socket.getOutputStream()),
            eq("2createNetwork(NetworkInfo,QStringList)"),
            createParamsCaptor.capture());
    List<Object> createParams = createParamsCaptor.getValue();
    assertEquals(2, createParams.size());
    QuasselCoreDatastreamCodec.UserTypeValue networkInfo =
        assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, createParams.get(0));
    assertEquals("NetworkInfo", networkInfo.typeName());
    @SuppressWarnings("unchecked")
    Map<String, Object> infoMap = (Map<String, Object>) networkInfo.value();
    assertEquals("libera", infoMap.get("networkName"));
    QuasselCoreDatastreamCodec.UserTypeValue createIdentityValue =
        assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, infoMap.get("Identity"));
    assertEquals("IdentityId", createIdentityValue.typeName());
    assertEquals(42, ((Number) createIdentityValue.value()).intValue());
    assertEquals(42, ((Number) infoMap.get("identityId")).intValue());
    assertEquals(42, ((Number) infoMap.get("identity")).intValue());

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<Object>> updateParamsCaptor =
        (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    verify(datastreamCodec)
        .writeSignalProxySync(
            eq(socket.getOutputStream()),
            eq("Network"),
            eq("1"),
            eq("requestSetNetworkInfo"),
            updateParamsCaptor.capture());
    List<Object> updateParams = updateParamsCaptor.getValue();
    assertEquals(1, updateParams.size());
    QuasselCoreDatastreamCodec.UserTypeValue updateNetworkInfo =
        assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, updateParams.get(0));
    assertEquals("NetworkInfo", updateNetworkInfo.typeName());
    @SuppressWarnings("unchecked")
    Map<String, Object> updatedInfoMap = (Map<String, Object>) updateNetworkInfo.value();
    assertEquals("existing", updatedInfoMap.get("NetworkName"));
    @SuppressWarnings("unchecked")
    List<Object> serverList = (List<Object>) updatedInfoMap.get("ServerList");
    QuasselCoreDatastreamCodec.UserTypeValue serverEntry =
        assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, serverList.get(0));
    assertEquals("Network::Server", serverEntry.typeName());
    @SuppressWarnings("unchecked")
    Map<String, Object> serverEntryMap = (Map<String, Object>) serverEntry.value();
    assertEquals("irc2.libera.chat", serverEntryMap.get("Host"));
    assertEquals(6667, ((Number) serverEntryMap.get("Port")).intValue());
    assertEquals(false, serverEntryMap.get("UseSSL"));

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<Object>> removeParamsCaptor =
        (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            eq(socket.getOutputStream()),
            eq("2removeNetwork(NetworkId)"),
            removeParamsCaptor.capture());
    List<Object> removeParams = removeParamsCaptor.getValue();
    assertEquals(1, removeParams.size());
    QuasselCoreDatastreamCodec.UserTypeValue removeNetworkId =
        assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, removeParams.get(0));
    assertEquals("NetworkId", removeNetworkId.typeName());
    assertEquals(1, ((Number) removeNetworkId.value()).intValue());
  }

  @Test
  void quasselConnectSkipsConnectWhenIdentityRepairIsNotConfirmedByCore() throws Exception {
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

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of(
                    "networkName",
                    "broken",
                    "identity",
                    0,
                    "ServerList",
                    List.of(
                        new QuasselCoreDatastreamCodec.UserTypeValue(
                            "Network::Server",
                            Map.of("Host", "irc.example.net", "Port", 6667, "UseSSL", false)))))));
    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline
        && service.quasselCoreNetworks("quassel").stream().noneMatch(n -> n.networkId() == 1)) {
      Thread.sleep(10L);
    }

    service.quasselCoreConnectNetwork("quassel", "1").blockingAwait();

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<Object>> updateParamsCaptor =
        (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    verify(datastreamCodec)
        .writeSignalProxySync(
            eq(socket.getOutputStream()),
            eq("Network"),
            eq("1"),
            eq("requestSetNetworkInfo"),
            updateParamsCaptor.capture());
    List<Object> updateParams = updateParamsCaptor.getValue();
    assertEquals(1, updateParams.size());
    QuasselCoreDatastreamCodec.UserTypeValue updateNetworkInfo =
        assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, updateParams.get(0));
    assertEquals("NetworkInfo", updateNetworkInfo.typeName());
    @SuppressWarnings("unchecked")
    Map<String, Object> updatedInfoMap = (Map<String, Object>) updateNetworkInfo.value();
    QuasselCoreDatastreamCodec.UserTypeValue updateIdentityValue =
        assertInstanceOf(
            QuasselCoreDatastreamCodec.UserTypeValue.class, updatedInfoMap.get("Identity"));
    assertEquals("IdentityId", updateIdentityValue.typeName());
    assertEquals(1, ((Number) updateIdentityValue.value()).intValue());
    assertEquals(1, ((Number) updatedInfoMap.get("IdentityId")).intValue());
    assertEquals(1, ((Number) updatedInfoMap.get("identity")).intValue());

    verify(datastreamCodec, times(0))
        .writeSignalProxySync(
            eq(socket.getOutputStream()), eq("Network"), eq("1"), eq("requestConnect"), any());
  }

  @Test
  void quasselConnectRequestsInitStateWhenNetworkSnapshotMissing() throws Exception {
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
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 5, List.of(5), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    service.quasselCoreConnectNetwork("quassel", "5").blockingAwait();

    verify(datastreamCodec)
        .writeSignalProxyInitRequest(
            eq(socket.getOutputStream()), eq("Network"), eq("5"), eq(List.of()));
    verify(datastreamCodec)
        .writeSignalProxySync(
            eq(socket.getOutputStream()), eq("Network"), eq("5"), eq("requestConnect"), any());
  }

  @Test
  void createNetworkUsesSessionInitIdentityWithoutCreateIdentityBootstrap() throws Exception {
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
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel",
                -1,
                List.of(),
                Map.of(),
                Map.of(1, Map.of("identityId", 1, "identityName", "quassel-user"))));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    QuasselCoreControlPort.QuasselCoreNetworkCreateRequest createRequest =
        new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
            "libera", "irc.libera.chat", 6697, true, "", true, null, List.of());
    service.quasselCoreCreateNetwork("quassel", createRequest).blockingAwait();

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<Object>> createParamsCaptor =
        (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    verify(datastreamCodec)
        .writeSignalProxyRpcCall(
            eq(socket.getOutputStream()),
            eq("2createNetwork(NetworkInfo,QStringList)"),
            createParamsCaptor.capture());
    List<Object> createParams = createParamsCaptor.getValue();
    QuasselCoreDatastreamCodec.UserTypeValue networkInfo =
        assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, createParams.get(0));
    @SuppressWarnings("unchecked")
    Map<String, Object> infoMap = (Map<String, Object>) networkInfo.value();
    QuasselCoreDatastreamCodec.UserTypeValue createIdentityValue =
        assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, infoMap.get("Identity"));
    assertEquals("IdentityId", createIdentityValue.typeName());
    assertEquals(1, ((Number) createIdentityValue.value()).intValue());
    assertEquals(1, ((Number) infoMap.get("identityId")).intValue());
    assertEquals(1, ((Number) infoMap.get("identity")).intValue());

    verify(datastreamCodec, times(0))
        .writeSignalProxyRpcCall(
            eq(socket.getOutputStream()), eq("2createIdentity(Identity,QVariantMap)"), any());
  }

  @Test
  void networkCreatedRpcUsesPendingCreateRequestNameAsDisplay() throws Exception {
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

    QuasselCoreControlPort.QuasselCoreNetworkCreateRequest createRequest =
        new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
            "libera", "irc.libera.chat", 6697, true, "", true, null, List.of("#ircafe"));
    service.quasselCoreCreateNetwork("quassel", createRequest).blockingAwait();

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL,
                "2networkCreated(NetworkId)".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 9))));

    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline
        && service.quasselCoreNetworks("quassel").stream()
            .noneMatch(n -> n.networkId() == 9 && "libera".equalsIgnoreCase(n.networkName()))) {
      Thread.sleep(10L);
    }

    assertTrue(
        service.quasselCoreNetworks("quassel").stream()
            .anyMatch(n -> n.networkId() == 9 && "libera".equalsIgnoreCase(n.networkName())));
  }

  @Test
  void networkLifecycleRpcSlotsUpdateObservedNetworkSnapshot() throws Exception {
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
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", -1, List.of(), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL,
                "2networkCreated(NetworkId)".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 9))));

    long observedDeadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < observedDeadline
        && service.quasselCoreNetworks("quassel").stream().noneMatch(n -> n.networkId() == 9)) {
      Thread.sleep(10L);
    }
    assertTrue(service.quasselCoreNetworks("quassel").stream().anyMatch(n -> n.networkId() == 9));

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL,
                "2networkRemoved(NetworkId)".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 9))));

    long removedDeadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < removedDeadline
        && service.quasselCoreNetworks("quassel").stream().anyMatch(n -> n.networkId() == 9)) {
      Thread.sleep(10L);
    }
    assertFalse(service.quasselCoreNetworks("quassel").stream().anyMatch(n -> n.networkId() == 9));
  }

  @Test
  void networkRemovedRpcImmediatelyHidesAuthSeededNetworkId() throws Exception {
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
    QuasselCoreDatastreamCodec.BufferInfoValue statusBuffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(100, 9, 0x01, -1, "status");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 9, List.of(9), Map.of(100, statusBuffer)));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    long observedDeadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < observedDeadline
        && service.quasselCoreNetworks("quassel").stream().noneMatch(n -> n.networkId() == 9)) {
      Thread.sleep(10L);
    }
    assertTrue(service.quasselCoreNetworks("quassel").stream().anyMatch(n -> n.networkId() == 9));

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL,
                "2networkRemoved(NetworkId)".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 9))));

    long removedDeadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < removedDeadline
        && service.quasselCoreNetworks("quassel").stream().anyMatch(n -> n.networkId() == 9)) {
      Thread.sleep(10L);
    }
    assertFalse(service.quasselCoreNetworks("quassel").stream().anyMatch(n -> n.networkId() == 9));
  }

  @Test
  void multilineNegotiatedLimitsAreReadFromCapabilitySnapshot() throws Exception {
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
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of(
                    "capsEnabled",
                    List.of("multiline=max-bytes=4096,max-lines=4", "message-tags", "typing")))));

    long deadline = System.currentTimeMillis() + 2_000L;
    while (!service.isMultilineAvailable("quassel") && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L);
    }
    assertTrue(service.isMultilineAvailable("quassel"));
    assertEquals(4096L, service.negotiatedMultilineMaxBytes("quassel"));
    assertEquals(4, service.negotiatedMultilineMaxLines("quassel"));
  }

  @Test
  void capEnvelopeLinesEmitCapabilityChangedEventsAndUpdateAvailability() throws Exception {
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
                    700L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    ":irc.example.net CAP quassel ACK :message-tags typing"))));

    awaitEvent(events, ev -> ev instanceof IrcEvent.Ircv3CapabilityChanged);
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.Ircv3CapabilityChanged cap
                        && "ACK".equals(cap.subcommand())
                        && "typing".equals(cap.capability())
                        && cap.enabled()));
    assertTrue(service.isTypingAvailable("quassel"));
  }

  @Test
  void standardReplyEnvelopeLineEmitsStructuredStandardReplyEvent() throws Exception {
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

    String raw = ":irc.example.net FAIL PRIVMSG INVALID_TARGET #ircafe :No such channel";
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    701L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    raw))));

    awaitEvent(events, ev -> ev instanceof IrcEvent.StandardReply);
    IrcEvent.StandardReply reply =
        events.values().stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.StandardReply.class::isInstance)
            .map(IrcEvent.StandardReply.class::cast)
            .reduce((first, second) -> second)
            .orElseThrow();
    assertEquals(IrcEvent.StandardReplyKind.FAIL, reply.kind());
    assertEquals("PRIVMSG", reply.command());
    assertEquals("INVALID_TARGET", reply.code());
    assertEquals("#ircafe", reply.context());
    assertEquals("No such channel", reply.description());
    assertEquals(raw, reply.rawLine());
  }

  @Test
  void monitorNumericsEmitStructuredMonitorEventsAndHostmaskObservations() throws Exception {
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

    long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    810L,
                    now,
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    ":irc.example.net 730 quassel :alice!u@h,bob!x@y"))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    811L,
                    now,
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    ":irc.example.net 731 quassel :carol!u@h"))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    812L,
                    now,
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    ":irc.example.net 732 quassel :alice,bob"))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    813L,
                    now,
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    ":irc.example.net 733 quassel :End of MONITOR list"))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    814L,
                    now,
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    ":irc.example.net 734 quassel 100 dave,erin :Monitor list is full"))));

    awaitEvent(events, ev -> ev instanceof IrcEvent.MonitorListFull);
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MonitorOnlineObserved online
                        && online.nicks().contains("alice")
                        && online.nicks().contains("bob")));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MonitorOfflineObserved offline
                        && offline.nicks().contains("carol")));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MonitorListObserved listed
                        && listed.nicks().contains("alice")
                        && listed.nicks().contains("bob")));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(ev -> ev instanceof IrcEvent.MonitorListEnded));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.MonitorListFull full
                        && full.limit() == 100
                        && full.nicks().contains("dave")
                        && full.nicks().contains("erin")));
    assertTrue(
        events.values().stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                ev ->
                    ev instanceof IrcEvent.UserHostmaskObserved hostmask
                        && "alice".equals(hostmask.nick())
                        && "alice!u@h".equals(hostmask.hostmask())));
  }

  @Test
  void monitorSupportCanBeLearnedFromRpl005MonitorToken() throws Exception {
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

    assertFalse(service.isMonitorAvailable("quassel"));
    assertEquals(0, service.negotiatedMonitorLimit("quassel"));

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(
                new QuasselCoreDatastreamCodec.MessageValue(
                    820L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0400,
                    0,
                    status,
                    "irc.example.net",
                    ":irc.example.net 005 quassel MONITOR=150 CHANTYPES=# :are supported by this server"))));

    long deadline = System.currentTimeMillis() + 2_000L;
    while (!service.isMonitorAvailable("quassel") && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L);
    }
    assertTrue(service.isMonitorAvailable("quassel"));
    assertEquals(150, service.negotiatedMonitorLimit("quassel"));
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

    awaitEvent(events, ev -> ev instanceof IrcEvent.ReadMarkerObserved);
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

    awaitEvent(events, ev -> ev instanceof IrcEvent.ReadMarkerObserved);
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

    awaitEvent(
        events, ev -> ev instanceof IrcEvent.ChannelMessage msg && "501".equals(msg.messageId()));
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

    awaitEvent(events, ev -> ev instanceof IrcEvent.MessageRedactionObserved);
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

    awaitEvent(events, ev -> ev instanceof IrcEvent.MessageRedactionObserved);
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
  void networkAddIrcChannelSyncEmitsJoinedChannelAndDedupesDisplayJoin() throws Exception {
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
                "Network".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "addIrcChannel".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "#ircafe")));
    awaitEvent(events, ev -> ev instanceof IrcEvent.JoinedChannel);

    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(101, 0x0020, 0, "quassel!u@h", "joined", 11, ""))));
    socket.writeInbound(
        encodeRpcCall(
            datastreamCodec,
            "2displayMsg(Message)",
            List.of(message(102, 0x0001, 0, "alice!u@h", "hello", 11, ""))));

    awaitEvent(events, ev -> ev instanceof IrcEvent.ChannelMessage);

    long joinedCount =
        events.values().stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.JoinedChannel.class::isInstance)
            .count();
    assertEquals(1L, joinedCount);
    IrcEvent.JoinedChannel joined =
        (IrcEvent.JoinedChannel)
            events.values().stream()
                .map(ServerIrcEvent::event)
                .filter(IrcEvent.JoinedChannel.class::isInstance)
                .findFirst()
                .orElseThrow();
    assertEquals("#ircafe", joined.channel());
  }

  @Test
  void connectedNetworkStateHydratesJoinedChannelsFromKnownBuffers() throws Exception {
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
    QuasselCoreDatastreamCodec.BufferInfoValue channelBuffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#persisted");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1), Map.of(11, channelBuffer)));

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
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("networkName", "libera", "isConnected", true))));

    awaitEvent(
        events,
        ev -> ev instanceof IrcEvent.JoinedChannel joined && "#persisted".equals(joined.channel()));
    long joinedCount =
        events.values().stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.JoinedChannel.class::isInstance)
            .count();
    assertEquals(1L, joinedCount);
  }

  @Test
  void reconnectingNetworkRehydratesJoinedChannelsFromKnownBuffers() throws Exception {
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
    QuasselCoreDatastreamCodec.BufferInfoValue channelBuffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(11, 1, 0x02, -1, "#persisted");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1), Map.of(11, channelBuffer)));

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
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("networkName", "libera", "isConnected", true))));
    awaitEvent(events, ev -> ev instanceof IrcEvent.JoinedChannel);

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("networkName", "libera", "isConnected", false))));

    socket.writeInbound(
        encodeSignalProxyFrame(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "NetworkInfo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "sync()".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("networkName", "libera", "isConnected", true))));

    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline) {
      long joinedCount =
          events.values().stream()
              .map(ServerIrcEvent::event)
              .filter(IrcEvent.JoinedChannel.class::isInstance)
              .count();
      if (joinedCount >= 2L) {
        return;
      }
      Thread.sleep(10L);
    }
    long joinedCount =
        events.values().stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.JoinedChannel.class::isInstance)
            .count();
    assertEquals(2L, joinedCount);
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

    awaitEvent(events, ev -> ev instanceof IrcEvent.ChannelTopicUpdated);

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
  void statusBufferNoticeUsesCanonicalStatusTarget() throws Exception {
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
    QuasselCoreDatastreamCodec.BufferInfoValue statusWithUnexpectedName =
        new QuasselCoreDatastreamCodec.BufferInfoValue(3, 1, 0x01, -1, "title");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1), Map.of(3, statusWithUnexpectedName)));

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
                    902L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0002,
                    0,
                    statusWithUnexpectedName,
                    "server",
                    "Last login from: user@host"))));

    events.awaitCount(4);
    IrcEvent.Notice notice =
        assertInstanceOf(IrcEvent.Notice.class, events.values().get(3).event());
    assertEquals("status", notice.target());
    assertEquals("server", notice.from());
  }

  @Test
  void backlogStatusBufferNoticeUsesCanonicalStatusTarget() throws Exception {
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
    QuasselCoreDatastreamCodec.BufferInfoValue statusWithUnexpectedName =
        new QuasselCoreDatastreamCodec.BufferInfoValue(3, 1, 0x01, -1, "title");

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probeSelection);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(
            new QuasselCoreAuthHandshake.AuthResult(
                "quassel", 1, List.of(1), Map.of(3, statusWithUnexpectedName)));

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
                    903L,
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    0x0002,
                    0x80,
                    statusWithUnexpectedName,
                    "server",
                    "Last login from: user@host"))));

    events.awaitCount(4);
    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals("status", batch.target());
    assertEquals(1, batch.entries().size());
    assertEquals(ChatHistoryEntry.Kind.NOTICE, batch.entries().get(0).kind());
    assertEquals("status", batch.entries().get(0).target());
    assertEquals("server", batch.entries().get(0).from());
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
    awaitEvent(
        events,
        ev ->
            ev instanceof IrcEvent.ConnectionFeaturesUpdated updated
                && updated.source().startsWith("quassel-phase=setup-required"));

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
  void setupRequiredStateIsExposedAndSubmitSetupInvokesHandshake() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket connectSocket = new BlockingSocket();
    BlockingSocket setupSocket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection datastreamProbe =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    Map<String, Object> setupFields =
        Map.of(
            "BackendInfo",
                List.of(Map.of("BackendId", "SQLite"), Map.of("BackendId", "PostgreSQL")),
            "AuthenticatorInfo", List.of(Map.of("AuthenticatorId", "Database")));

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(connectSocket, setupSocket);
    when(protocolProbe.negotiate(connectSocket)).thenReturn(datastreamProbe);
    when(protocolProbe.negotiate(setupSocket)).thenReturn(datastreamProbe);
    when(authHandshake.authenticate(connectSocket, server))
        .thenThrow(
            new QuasselCoreAuthHandshake.CoreSetupRequiredException("setup required", setupFields));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    assertTrue(service.isQuasselCoreSetupPending("quassel"));
    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        service.quasselCoreSetupPrompt("quassel").orElseThrow();
    assertTrue(prompt.storageBackends().contains("SQLite"));
    assertTrue(prompt.authenticators().contains("Database"));

    QuasselCoreControlPort.QuasselCoreSetupRequest request =
        new QuasselCoreControlPort.QuasselCoreSetupRequest(
            "admin", "secret", "SQLite", "Database", Map.of(), Map.of());
    service.submitQuasselCoreSetup("quassel", request).blockingAwait();

    ArgumentCaptor<QuasselCoreAuthHandshake.CoreSetupRequest> setupCaptor =
        ArgumentCaptor.forClass(QuasselCoreAuthHandshake.CoreSetupRequest.class);
    verify(authHandshake).performCoreSetup(eq(setupSocket), setupCaptor.capture());
    assertEquals("admin", setupCaptor.getValue().adminUser());
    assertEquals("SQLite", setupCaptor.getValue().storageBackend());
    assertFalse(service.isQuasselCoreSetupPending("quassel"));
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

  private static void awaitEvent(
      TestSubscriber<ServerIrcEvent> events, Predicate<IrcEvent> predicate) throws Exception {
    long deadline = System.currentTimeMillis() + 2_000L;
    while (System.currentTimeMillis() < deadline) {
      boolean matched = events.values().stream().map(ServerIrcEvent::event).anyMatch(predicate);
      if (matched) return;
      Thread.sleep(10L);
    }
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
