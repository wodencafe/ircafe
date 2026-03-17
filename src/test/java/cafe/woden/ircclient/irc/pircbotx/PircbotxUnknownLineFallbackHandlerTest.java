package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
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
    conn.zncPlaybackCapture.start(
        "libera", "#ircafe", Instant.now().minusSeconds(60), null, events::add);

    emitter.handle(
        unknown(null),
        ":alice!ident@host PRIVMSG #ircafe :replayed line",
        ":alice!ident@host PRIVMSG #ircafe :replayed line");

    assertEquals(0, events.size());
    conn.zncPlaybackCapture.cancelActive("test");
  }

  private static PircbotxUnknownLineFallbackHandler newEmitter(List<ServerIrcEvent> events) {
    return newEmitter(new PircbotxConnectionState("libera"), events);
  }

  private static PircbotxUnknownLineFallbackHandler newEmitter(
      PircbotxConnectionState conn, List<ServerIrcEvent> events) {
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            false,
            true,
            new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
            BouncerDiscoveryEventPort.noOp());
    PircbotxChatHistoryBatchCollector batches =
        new PircbotxChatHistoryBatchCollector("libera", events::add);
    PircbotxServerResponseEmitter serverResponses =
        new PircbotxServerResponseEmitter("libera", events::add);
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
        bot -> "");
  }

  private static UnknownEvent unknown(PircBotX bot) {
    UnknownEvent event = mock(UnknownEvent.class);
    when(event.getBot()).thenReturn(bot);
    return event;
  }
}
