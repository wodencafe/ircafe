package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputIRC;
import org.pircbotx.output.OutputRaw;

class PircbotxRegistrationLifecycleHandlerTest {

  @Test
  void maybeHandleRegistrationCompleteEmitsReadyAndRequestsBootstrap() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.zncDetected.set(true);
    conn.zncPlaybackCapAcked.set(true);
    conn.sojuBouncerNetworksCapAcked.set(true);

    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(conn, events, serverId -> OptionalLong.of(20L), true, true);

    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendIRC()).thenReturn(outputIrc);
    when(bot.sendRaw()).thenReturn(outputRaw);
    when(bot.getNick()).thenReturn("me");

    assertTrue(handler.maybeHandle(376, bot, ":server 376 me :End of /MOTD command."));

    assertEquals(4, events.size());
    assertInstanceOf(IrcEvent.ServerResponseLine.class, events.get(0).event());
    assertInstanceOf(IrcEvent.ConnectionReady.class, events.get(1).event());
    IrcEvent.ConnectionFeaturesUpdated features =
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.get(2).event());
    IrcEvent.ServerTimeNotNegotiated serverTime =
        assertInstanceOf(IrcEvent.ServerTimeNotNegotiated.class, events.get(3).event());

    assertEquals("post-registration", features.source());
    assertTrue(serverTime.message().contains("server-time"));
    verify(outputIrc).message("*status", "ListNetworks");
    verify(outputIrc).message("*playback", "play * 19");
    verify(outputRaw).rawLine("BOUNCER LISTNETWORKS");
  }

  @Test
  void maybeHandleMyInfoDetectsZncAndPublishesStatusLine() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(conn, events, serverId -> OptionalLong.empty(), false, true);

    assertTrue(handler.maybeHandle(4, null, ":server 004 me irc.example znc-1.9.1 ao mtov"));

    assertTrue(conn.zncDetected.get());
    assertEquals(1, events.size());
    assertInstanceOf(IrcEvent.ServerResponseLine.class, events.getFirst().event());
  }

  @Test
  void maybeHandleChannelMode324EmitsSnapshotObservation() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(conn, events, serverId -> OptionalLong.empty(), false, false);

    assertTrue(handler.maybeHandle(324, null, ":server 324 me #ircafe +nt"));

    assertEquals(1, events.size());
    IrcEvent.ChannelModeObserved observed =
        assertInstanceOf(IrcEvent.ChannelModeObserved.class, events.getFirst().event());
    assertEquals("#ircafe", observed.channel());
    assertEquals("+nt", observed.details());
    assertEquals(IrcEvent.ChannelModeProvenance.NUMERIC_324, observed.provenance());
  }

  private static PircbotxRegistrationLifecycleHandler newHandler(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      PlaybackCursorProvider playbackCursorProvider,
      boolean sojuDiscoveryEnabled,
      boolean zncDiscoveryEnabled) {
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            sojuDiscoveryEnabled,
            zncDiscoveryEnabled,
            new BouncerBackendRegistry(List.of()),
            BouncerDiscoveryEventPort.noOp());
    PircbotxServerResponseEmitter serverResponses =
        new PircbotxServerResponseEmitter("libera", events::add);
    return new PircbotxRegistrationLifecycleHandler(
        "libera", conn, playbackCursorProvider, bouncerDiscovery, serverResponses, events::add);
  }
}
