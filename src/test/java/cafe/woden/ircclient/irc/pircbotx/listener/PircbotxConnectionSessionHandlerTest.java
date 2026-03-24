package cafe.woden.ircclient.irc.pircbotx.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.bouncer.GenericBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.Ircv3MultilineAccumulator;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.irc.soju.SojuBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.znc.ZncBouncerNetworkMappingStrategy;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;

class PircbotxConnectionSessionHandlerTest {

  @Test
  void recordInboundActivityClearsTimeoutFlagAndUpdatesTimestamp() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.setLocalTimeoutEmitted(true);
    PircbotxConnectionSessionHandler handler = newHandler(conn, new ArrayList<>(), null, null);

    handler.recordInboundActivity();

    assertFalse(conn.localTimeoutEmitted());
    assertTrue(conn.lastInboundActivityMs() > 0L);
  }

  @Test
  void onConnectEmitsConnectedAndResetsReconnectState() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.setReconnectAttempts(7);
    conn.markManualDisconnect();
    conn.setLocalTimeoutEmitted(true);
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxConnectionSessionHandler handler = newHandler(conn, events, null, null);
    ConnectEvent event = mock(ConnectEvent.class);
    PircBotX bot = mock(PircBotX.class);
    when(event.getBot()).thenReturn(bot);
    when(bot.getServerHostname()).thenReturn("irc.example.net");
    when(bot.getServerPort()).thenReturn(6697);
    when(bot.getNick()).thenReturn("me");

    handler.onConnect(event);

    assertEquals(0L, conn.reconnectAttempts());
    assertFalse(conn.manualDisconnectRequested());
    assertFalse(conn.localTimeoutEmitted());
    assertEquals(1, events.size());
    IrcEvent.Connected connected =
        assertInstanceOf(IrcEvent.Connected.class, events.getFirst().event());
    assertEquals("irc.example.net", connected.serverHost());
    assertEquals(6697, connected.serverPort());
    assertEquals("me", connected.nick());
  }

  @Test
  void onDisconnectStopsHeartbeatClearsDiscoveryStateAndSchedulesReconnect() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    AtomicInteger heartbeatStops = new AtomicInteger();
    AtomicReference<String> reconnectReason = new AtomicReference<>();
    PircbotxConnectionSessionHandler handler =
        newHandler(
            conn,
            events,
            c -> heartbeatStops.incrementAndGet(),
            (c, reason) -> reconnectReason.set(reason));
    PircBotX bot = mock(PircBotX.class);
    DisconnectEvent event = mock(DisconnectEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getDisconnectException()).thenReturn(new IllegalStateException("socket closed"));
    conn.setBot(bot);
    conn.overrideDisconnectReason("Auth failed");
    conn.storeSojuDiscoveredNetwork("123", mock(BouncerDiscoveredNetwork.class));
    conn.storeGenericBouncerDiscoveredNetwork("neta", mock(BouncerDiscoveredNetwork.class));

    handler.onDisconnect(event);

    assertEquals(1, heartbeatStops.get());
    assertEquals("Auth failed", reconnectReason.get());
    assertEquals(null, conn.currentBot());
    assertFalse(conn.hasAnySojuDiscoveredNetworks());
    assertFalse(conn.hasAnyGenericBouncerDiscoveredNetworks());
    assertEquals(1, events.size());
    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event());
    assertEquals("Auth failed", disconnected.reason());
  }

  @Test
  void onUnexpectedDisconnectDoesNotRemoveExternallyDiscoveredBouncerNetworks() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    AtomicReference<String> reconnectReason = new AtomicReference<>();
    BouncerBackendRegistry bouncerBackends = mock(BouncerBackendRegistry.class);
    when(bouncerBackends.backendIds())
        .thenReturn(
            Set.of(
                SojuBouncerNetworkMappingStrategy.BACKEND_ID,
                ZncBouncerNetworkMappingStrategy.BACKEND_ID,
                GenericBouncerNetworkMappingStrategy.BACKEND_ID));
    BouncerDiscoveryEventPort bouncerDiscoveryEvents = mock(BouncerDiscoveryEventPort.class);
    PircbotxConnectionSessionHandler handler =
        newHandler(
            conn,
            events,
            c -> {},
            (c, reason) -> reconnectReason.set(reason),
            bouncerBackends,
            bouncerDiscoveryEvents);
    PircBotX bot = mock(PircBotX.class);
    DisconnectEvent event = mock(DisconnectEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getDisconnectException()).thenReturn(new IllegalStateException("socket closed"));
    conn.setBot(bot);

    handler.onDisconnect(event);

    assertEquals("socket closed", reconnectReason.get());
    verifyNoInteractions(bouncerDiscoveryEvents);
  }

  @Test
  void onDisconnectDoesNotReconnectWhenSuppressed() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    AtomicReference<String> reconnectReason = new AtomicReference<>();
    PircbotxConnectionSessionHandler handler =
        newHandler(conn, events, c -> {}, (c, reason) -> reconnectReason.set(reason));
    PircBotX bot = mock(PircBotX.class);
    DisconnectEvent event = mock(DisconnectEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getDisconnectException()).thenReturn(new IllegalStateException("socket closed"));
    conn.setBot(bot);
    conn.suppressAutoReconnectOnce();

    handler.onDisconnect(event);

    assertEquals(null, reconnectReason.get());
    assertFalse(conn.autoReconnectSuppressed());
    assertEquals(1, events.size());
  }

  @Test
  void onDisconnectUsesNestedProxyFailureMessageWhenTopLevelReasonIsDisconnected() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    AtomicReference<String> reconnectReason = new AtomicReference<>();
    PircbotxConnectionSessionHandler handler =
        newHandler(conn, events, c -> {}, (c, reason) -> reconnectReason.set(reason));
    PircBotX bot = mock(PircBotX.class);
    DisconnectEvent event = mock(DisconnectEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getDisconnectException())
        .thenReturn(
            new RuntimeException(
                "Disconnected", new SocketException("SOCKS authentication failed")));
    conn.setBot(bot);

    handler.onDisconnect(event);

    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event());
    assertEquals("SOCKS authentication failed", disconnected.reason());
    assertEquals("SOCKS authentication failed", reconnectReason.get());
  }

  @Test
  void onDisconnectSkipsConnectWrapperMessageWhenCauseIsMoreSpecific() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    AtomicReference<String> reconnectReason = new AtomicReference<>();
    PircbotxConnectionSessionHandler handler =
        newHandler(conn, events, c -> {}, (c, reason) -> reconnectReason.set(reason));
    PircBotX bot = mock(PircBotX.class);
    DisconnectEvent event = mock(DisconnectEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getDisconnectException())
        .thenReturn(
            new RuntimeException(
                "Exception encountered during connect",
                new IOException("Connection refused by SOCKS proxy")));
    conn.setBot(bot);

    handler.onDisconnect(event);

    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event());
    assertEquals("Connection refused by SOCKS proxy", disconnected.reason());
    assertEquals("Connection refused by SOCKS proxy", reconnectReason.get());
  }

  private static PircbotxConnectionSessionHandler newHandler(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      java.util.function.Consumer<PircbotxConnectionState> heartbeatStopper,
      java.util.function.BiConsumer<PircbotxConnectionState, String> reconnectScheduler) {
    return newHandler(
        conn,
        events,
        heartbeatStopper,
        reconnectScheduler,
        new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
        BouncerDiscoveryEventPort.noOp());
  }

  private static PircbotxConnectionSessionHandler newHandler(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      java.util.function.Consumer<PircbotxConnectionState> heartbeatStopper,
      java.util.function.BiConsumer<PircbotxConnectionState, String> reconnectScheduler,
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents) {
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera", conn, false, false, bouncerBackends, bouncerDiscoveryEvents);
    PircbotxChatHistoryBatchCollector chatHistoryBatches =
        new PircbotxChatHistoryBatchCollector("libera", events::add);
    PircbotxServerResponseEmitter serverResponses =
        new PircbotxServerResponseEmitter("libera", events::add);
    return new PircbotxConnectionSessionHandler(
        "libera",
        conn,
        heartbeatStopper == null ? c -> {} : heartbeatStopper,
        reconnectScheduler == null ? (c, reason) -> {} : reconnectScheduler,
        bouncerDiscovery,
        chatHistoryBatches,
        serverResponses,
        new Ircv3MultilineAccumulator(),
        events::add);
  }
}
