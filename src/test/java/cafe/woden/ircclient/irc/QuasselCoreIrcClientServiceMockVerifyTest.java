package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class QuasselCoreIrcClientServiceMockVerifyTest {

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void connectInvokesConnectorProbeAndHandshakeInOrder() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    IrcProperties.Server server = server();
    BlockingSocket socket = new BlockingSocket();
    QuasselCoreProtocolProbe.ProbeSelection probe =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probe);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitCount(3);

    InOrder inOrder = inOrder(connector, protocolProbe, authHandshake);
    inOrder.verify(connector).connect(server);
    inOrder.verify(protocolProbe).negotiate(socket);
    inOrder.verify(authHandshake).authenticate(socket, server);
  }

  @Test
  void replayedHeartbeatAndSyncFramesTriggerReplyAndConnectionReadyInOrder() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = mock(QuasselCoreDatastreamCodec.class);
    IrcProperties.Server server = server();
    ScriptedSocket socket = new ScriptedSocket();
    QuasselCoreProtocolProbe.ProbeSelection probe =
        new QuasselCoreProtocolProbe.ProbeSelection(
            0x00000002, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 0, 0);
    QuasselCoreDatastreamCodec.QtDateTimeValue token =
        QuasselCoreDatastreamCodec.utcDateTimeFromEpochMs(1_700_000_123_456L);

    when(serverCatalog.require("quassel")).thenReturn(server);
    when(connector.connect(server)).thenReturn(socket);
    when(protocolProbe.negotiate(socket)).thenReturn(probe);
    when(authHandshake.authenticate(socket, server))
        .thenReturn(new QuasselCoreAuthHandshake.AuthResult("quassel", 1, List.of(1), Map.of()));
    when(datastreamCodec.readSignalProxyMessage(any(InputStream.class)))
        .thenReturn(
            new QuasselCoreDatastreamCodec.SignalProxyMessage(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT, "", "", "", List.of(token)))
        .thenReturn(
            new QuasselCoreDatastreamCodec.SignalProxyMessage(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "BufferSyncer",
                "global",
                "sync()",
                List.of()))
        .thenThrow(new EOFException("end of replay"));

    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            serverCatalog, connector, protocolProbe, authHandshake, datastreamCodec);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.connect("quassel").blockingAwait();
    events.awaitDone(2, TimeUnit.SECONDS);

    verify(datastreamCodec).writeSignalProxyHeartBeatReply(any(OutputStream.class), eq(token));
    verify(datastreamCodec, org.mockito.Mockito.atLeast(2)).readSignalProxyMessage(any(InputStream.class));

    assertEquals(
        1,
        events.values().stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.ConnectionReady.class::isInstance)
            .count());
    verify(datastreamCodec, never()).writeSignalProxyHeartBeat(any(OutputStream.class), any());
  }

  @Test
  void unsupportedProtocolDoesNotInvokeHandshake() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
    QuasselCoreProtocolProbe protocolProbe = mock(QuasselCoreProtocolProbe.class);
    QuasselCoreAuthHandshake authHandshake = mock(QuasselCoreAuthHandshake.class);
    QuasselCoreDatastreamCodec datastreamCodec = mock(QuasselCoreDatastreamCodec.class);
    IrcProperties.Server server = server();
    ScriptedSocket socket = new ScriptedSocket();

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
    assertInstanceOf(IrcEvent.Error.class, events.values().get(1).event());

    InOrder io = inOrder(connector, protocolProbe);
    io.verify(connector).connect(server);
    io.verify(protocolProbe).negotiate(socket);
    verify(authHandshake, never()).authenticate(any(Socket.class), any(IrcProperties.Server.class));
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

  private static final class ScriptedSocket extends Socket {
    private final InputStream input = new ByteArrayInputStream(new byte[0]);
    private final OutputStream output = new ByteArrayOutputStream();

    @Override
    public InputStream getInputStream() {
      return input;
    }

    @Override
    public OutputStream getOutputStream() {
      return output;
    }
  }

  private static final class BlockingSocket extends Socket {
    private final java.io.PipedInputStream input;
    private final java.io.PipedOutputStream inputWriter;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private BlockingSocket() throws IOException {
      this.input = new java.io.PipedInputStream();
      this.inputWriter = new java.io.PipedOutputStream(input);
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
      inputWriter.close();
      input.close();
    }
  }
}
