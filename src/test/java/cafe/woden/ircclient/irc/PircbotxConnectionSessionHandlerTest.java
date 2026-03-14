package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import java.util.ArrayList;
import java.util.List;
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
    conn.localTimeoutEmitted.set(true);
    PircbotxConnectionSessionHandler handler = newHandler(conn, new ArrayList<>(), null, null);

    handler.recordInboundActivity();

    assertFalse(conn.localTimeoutEmitted.get());
    assertTrue(conn.lastInboundMs.get() > 0L);
  }

  @Test
  void onConnectEmitsConnectedAndResetsReconnectState() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.reconnectAttempts.set(7);
    conn.manualDisconnect.set(true);
    conn.localTimeoutEmitted.set(true);
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxConnectionSessionHandler handler = newHandler(conn, events, null, null);
    ConnectEvent event = mock(ConnectEvent.class);
    PircBotX bot = mock(PircBotX.class);
    when(event.getBot()).thenReturn(bot);
    when(bot.getServerHostname()).thenReturn("irc.example.net");
    when(bot.getServerPort()).thenReturn(6697);
    when(bot.getNick()).thenReturn("me");

    handler.onConnect(event);

    assertEquals(0L, conn.reconnectAttempts.get());
    assertFalse(conn.manualDisconnect.get());
    assertFalse(conn.localTimeoutEmitted.get());
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
    conn.botRef.set(bot);
    conn.disconnectReasonOverride.set("Auth failed");
    conn.sojuNetworksByNetId.put("123", mock(BouncerDiscoveredNetwork.class));
    conn.genericBouncerNetworksById.put("neta", mock(BouncerDiscoveredNetwork.class));

    handler.onDisconnect(event);

    assertEquals(1, heartbeatStops.get());
    assertEquals("Auth failed", reconnectReason.get());
    assertEquals(null, conn.botRef.get());
    assertTrue(conn.sojuNetworksByNetId.isEmpty());
    assertTrue(conn.genericBouncerNetworksById.isEmpty());
    assertEquals(1, events.size());
    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, events.getFirst().event());
    assertEquals("Auth failed", disconnected.reason());
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
    conn.botRef.set(bot);
    conn.suppressAutoReconnectOnce.set(true);

    handler.onDisconnect(event);

    assertEquals(null, reconnectReason.get());
    assertFalse(conn.suppressAutoReconnectOnce.get());
    assertEquals(1, events.size());
  }

  private static PircbotxConnectionSessionHandler newHandler(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      java.util.function.Consumer<PircbotxConnectionState> heartbeatStopper,
      java.util.function.BiConsumer<PircbotxConnectionState, String> reconnectScheduler) {
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            false,
            false,
            new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
            BouncerDiscoveryEventPort.noOp());
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
