package cafe.woden.ircclient.irc.pircbotx.listener;

import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.chatHistoryBatches;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.runtime;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.serverResponses;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.whoEvents;
import static cafe.woden.ircclient.irc.pircbotx.listener.PircbotxListenerRuntimeTestFixtures.isupportObserver;
import static cafe.woden.ircclient.irc.pircbotx.listener.PircbotxListenerRuntimeTestFixtures.saslFailures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.spi.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxWhoEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxPresenceSignalSupport;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.UnknownEvent;

class PircbotxUnknownLineFallbackHandlerTest {

  @Test
  void handleEmitsAwayNotifyObservation() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxUnknownLineFallbackHandler emitter = newEmitter(events);

    emitter.handle(
        unknown(null),
        ":alice!ident@host AWAY :gone for lunch",
        ":alice!ident@host AWAY :gone for lunch");

    assertEquals(1, events.size());
    IrcEvent.UserAwayStateObserved away =
        assertInstanceOf(IrcEvent.UserAwayStateObserved.class, events.getFirst().event());
    assertEquals("alice", away.nick());
    assertEquals(IrcEvent.AwayState.AWAY, away.awayState());
  }

  @Test
  void handleEmitsChannelModeObservationFor324Fallback() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxUnknownLineFallbackHandler emitter = newEmitter(events);

    emitter.handle(unknown(null), ":server 324 me #ircafe +nt", ":server 324 me #ircafe +nt");

    assertEquals(1, events.size());
    IrcEvent.ChannelModeObserved observed =
        assertInstanceOf(IrcEvent.ChannelModeObserved.class, events.getFirst().event());
    assertEquals("#ircafe", observed.channel());
    assertEquals("+nt", observed.details());
  }

  @Test
  void handleEmitsAwayStatusChangedFor306Fallback() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxUnknownLineFallbackHandler emitter = newEmitter(events);

    emitter.handle(
        unknown(null),
        ":server 306 me :You have been marked as being away",
        ":server 306 me :You have been marked as being away");

    assertEquals(1, events.size());
    IrcEvent.AwayStatusChanged away =
        assertInstanceOf(IrcEvent.AwayStatusChanged.class, events.getFirst().event());
    assertEquals(true, away.away());
  }

  @Test
  void handleSuppressesUnknownReplayLinesCapturedForPlayback() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxUnknownLineFallbackHandler emitter = newEmitter(conn, events);
    conn.startZncPlaybackCapture(
        "libera", "#ircafe", Instant.now().minusSeconds(60), null, events::add);

    emitter.handle(
        unknown(null),
        ":alice!ident@host PRIVMSG #ircafe :replayed line",
        ":alice!ident@host PRIVMSG #ircafe :replayed line");

    assertEquals(0, events.size());
    conn.cancelZncPlaybackCapture("test");
  }

  private static PircbotxUnknownLineFallbackHandler newEmitter(List<ServerIrcEvent> events) {
    return newEmitter(new PircbotxConnectionState("libera"), events);
  }

  private static PircbotxUnknownLineFallbackHandler newEmitter(
      PircbotxConnectionState conn, List<ServerIrcEvent> events) {
    var testRuntime = runtime();
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            false,
            true,
            new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
            BouncerDiscoveryEventPort.noOp());
    PircbotxChatHistoryBatchCollector batches =
        chatHistoryBatches("libera", events::add, testRuntime);
    PircbotxServerResponseEmitter serverResponses =
        serverResponses("libera", events::add, testRuntime);
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
    return new PircbotxUnknownLineFallbackHandler(
        "libera",
        conn,
        bouncerDiscovery,
        batches,
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
  }

  private static UnknownEvent unknown(PircBotX bot) {
    UnknownEvent event = mock(UnknownEvent.class);
    when(event.getBot()).thenReturn(bot);
    return event;
  }
}
