package cafe.woden.ircclient.irc.pircbotx.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxMonitorEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxUnknownCtcpEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxWhoEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.hooks.events.UnknownEvent;

class PircbotxUnknownEventRouterTest {

  @Test
  void handleEmitsInviteEventFromUnknownLine() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxUnknownEventRouter router = newRouter(conn, events);

    router.handle(
        new UnknownEvent(
            null,
            "me",
            "alice",
            "INVITE",
            ":alice!ident@host INVITE me #ircafe :join us",
            List.of("me", "#ircafe"),
            ImmutableMap.of()));

    assertEquals(1, events.size());
    IrcEvent.InvitedToChannel invite =
        assertInstanceOf(IrcEvent.InvitedToChannel.class, events.getFirst().event());
    assertEquals("#ircafe", invite.channel());
    assertEquals("alice", invite.from());
    assertEquals("me", invite.invitee());
  }

  @Test
  void handleEmitsWallopsEventFromUnknownLine() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxUnknownEventRouter router = newRouter(conn, events);

    router.handle(
        new UnknownEvent(
            null,
            "*",
            "server",
            "WALLOPS",
            ":server WALLOPS :maintenance soon",
            List.of("*"),
            ImmutableMap.of()));

    assertEquals(1, events.size());
    IrcEvent.WallopsReceived wallops =
        assertInstanceOf(IrcEvent.WallopsReceived.class, events.getFirst().event());
    assertEquals("server", wallops.from());
    assertEquals("maintenance soon", wallops.text());
  }

  @Test
  void handleRemembersSelfNickHintForNumericUnknownLine() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxUnknownEventRouter router = newRouter(conn, events);

    router.handle(
        new UnknownEvent(
            null,
            "me",
            "irc.example",
            "005",
            ":irc.example 005 me PREFIX=(qaohv)!&@%+ :are supported by this server",
            List.of("me", "PREFIX=(qaohv)!&@%+"),
            ImmutableMap.of()));

    assertEquals("me", conn.selfNickHint());
    assertEquals(0, events.size());
  }

  private static PircbotxUnknownEventRouter newRouter(
      PircbotxConnectionState conn, List<ServerIrcEvent> events) {
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            false,
            false,
            new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
            BouncerDiscoveryEventPort.noOp());
    PircbotxServerResponseEmitter serverResponses =
        new PircbotxServerResponseEmitter("libera", events::add);
    PircbotxMonitorEventEmitter monitorEvents =
        new PircbotxMonitorEventEmitter("libera", events::add);
    PircbotxChatHistoryBatchCollector chatHistoryBatches =
        new PircbotxChatHistoryBatchCollector("libera", events::add);
    PircbotxUnknownCtcpEmitter unknownCtcp =
        new PircbotxUnknownCtcpEmitter(
            "libera", events::add, (bot, nick) -> false, (bot, nick) -> false, bot -> "");
    PircbotxSaslFailureHandler saslFailures =
        new PircbotxSaslFailureHandler("libera", conn, events::add, false);
    PircbotxIsupportObserver isupportObserver =
        new PircbotxIsupportObserver(
            "libera",
            conn,
            new ServerIsupportState(),
            events::add,
            bouncerDiscovery::observeSojuBouncerNetId);
    PircbotxWhoEventEmitter whoEvents = new PircbotxWhoEventEmitter("libera", conn, events::add);
    PircbotxUnknownLineFallbackHandler fallback =
        new PircbotxUnknownLineFallbackHandler(
            "libera",
            conn,
            bouncerDiscovery,
            chatHistoryBatches,
            serverResponses,
            saslFailures,
            isupportObserver,
            whoEvents,
            events::add,
            bot -> "");
    return new PircbotxUnknownEventRouter(
        "libera",
        conn::setSelfNickHint,
        bot -> "",
        serverResponses,
        monitorEvents,
        chatHistoryBatches,
        unknownCtcp,
        fallback,
        events::add);
  }
}
