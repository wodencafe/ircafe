package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.playback.PlaybackCursorProvider;
import cafe.woden.ircclient.state.ServerIsupportState;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.UnknownEvent;

class PircbotxChannelMode324DedupTest {

  @Test
  void registrationHandlerThenUnknownFallbackEmitSingleSnapshot() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxRegistrationLifecycleHandler handler = newHandler(conn, events);
    PircbotxUnknownLineFallbackHandler emitter = newEmitter(conn, events);

    String line = ":server 324 me #ircafe +ntC";

    assertTrue(handler.maybeHandle(324, null, line));
    emitter.handle(unknown(null), line, line);

    assertSingleSnapshot(events, "#ircafe", "+ntC");
  }

  @Test
  void unknownFallbackThenRegistrationHandlerEmitSingleSnapshot() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxRegistrationLifecycleHandler handler = newHandler(conn, events);
    PircbotxUnknownLineFallbackHandler emitter = newEmitter(conn, events);

    String line = ":server 324 me #ircafe +ntC";

    emitter.handle(unknown(null), line, line);
    assertTrue(handler.maybeHandle(324, null, line));

    assertSingleSnapshot(events, "#ircafe", "+ntC");
  }

  private static void assertSingleSnapshot(
      List<ServerIrcEvent> events, String expectedChannel, String expectedDetails) {
    assertEquals(1, events.size());
    IrcEvent.ChannelModeObserved observed =
        assertInstanceOf(IrcEvent.ChannelModeObserved.class, events.getFirst().event());
    assertEquals(expectedChannel, observed.channel());
    assertEquals(expectedDetails, observed.details());
    assertEquals(IrcEvent.ChannelModeProvenance.NUMERIC_324, observed.provenance());
  }

  private static PircbotxRegistrationLifecycleHandler newHandler(
      PircbotxConnectionState conn, List<ServerIrcEvent> events) {
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            false,
            true,
            new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
            BouncerDiscoveryEventPort.noOp());
    PircbotxServerResponseEmitter serverResponses =
        new PircbotxServerResponseEmitter("libera", events::add);
    return new PircbotxRegistrationLifecycleHandler(
        "libera",
        conn,
        new NoOpPlaybackCursorProvider(),
        bouncerDiscovery,
        serverResponses,
        events::add);
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

  private static final class NoOpPlaybackCursorProvider implements PlaybackCursorProvider {
    @Override
    public OptionalLong lastSeenEpochSeconds(String serverId) {
      return OptionalLong.empty();
    }
  }
}
