package cafe.woden.ircclient.irc.pircbotx.listener;

import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.chatHistoryBatches;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.monitorEvents;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.runtime;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.serverResponses;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.unknownCtcp;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.whoEvents;
import static cafe.woden.ircclient.irc.pircbotx.listener.PircbotxListenerRuntimeTestFixtures.isupportObserver;
import static cafe.woden.ircclient.irc.pircbotx.listener.PircbotxListenerRuntimeTestFixtures.saslFailures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.spi.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxMonitorEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxUnknownCtcpEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxWhoEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxPresenceSignalSupport;
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
    var testRuntime = runtime();
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            false,
            false,
            new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
            BouncerDiscoveryEventPort.noOp());
    PircbotxServerResponseEmitter serverResponses =
        serverResponses("libera", events::add, testRuntime);
    PircbotxMonitorEventEmitter monitorEvents = monitorEvents("libera", events::add, testRuntime);
    PircbotxChatHistoryBatchCollector chatHistoryBatches =
        chatHistoryBatches("libera", events::add, testRuntime);
    PircbotxUnknownCtcpEmitter unknownCtcp =
        unknownCtcp(
            "libera",
            events::add,
            (bot, nick) -> false,
            (bot, nick) -> false,
            bot -> "",
            testRuntime);
    PircbotxSaslFailureHandler saslFailures =
        saslFailures("libera", conn, events::add, false, testRuntime);
    PircbotxIsupportObserver isupportObserver =
        isupportObserver(
            "libera",
            conn,
            new ServerIsupportState(),
            events::add,
            bouncerDiscovery::observeSojuBouncerNetId,
            testRuntime);
    PircbotxWhoEventEmitter whoEvents = whoEvents("libera", conn, events::add, testRuntime);
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
            bot -> "",
            testRuntime.serverTime(),
            testRuntime.messageTags(),
            new PircbotxPresenceSignalSupport(
                "libera", events::add, testRuntime.catalogs().inboundCommands()),
            testRuntime.zncPlayback());
    return new PircbotxUnknownEventRouter(
        "libera",
        conn::setSelfNickHint,
        bot -> "",
        serverResponses,
        monitorEvents,
        chatHistoryBatches,
        unknownCtcp,
        fallback,
        testRuntime.serverTime(),
        testRuntime.catalogs().inboundCommands(),
        events::add);
  }
}
