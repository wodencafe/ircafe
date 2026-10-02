package cafe.woden.ircclient.irc.pircbotx.listener;

import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.runtime;
import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.serverResponses;
import static cafe.woden.ircclient.irc.pircbotx.listener.PircbotxListenerRuntimeTestFixtures.registrationLifecycle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandRequest;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputIRC;
import org.pircbotx.output.OutputRaw;
import org.slf4j.LoggerFactory;

class PircbotxRegistrationLifecycleHandlerTest {

  @Test
  void reportsServerBufferPolicyWhenUpstreamVersionDoesNotIdentifyZnc() {
    var conn = new PircbotxConnectionState("znc");
    var handler =
        newHandler(conn, new ArrayList<>(), ignored -> OptionalLong.of(20L), false, false);
    Logger logger = (Logger) LoggerFactory.getLogger(PircbotxRegistrationLifecycleHandler.class);
    Level previous = logger.getLevel();
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
    logger.setLevel(Level.INFO);
    try {
      handler.maybeHandle(4, null, ":irc.example 004 me irc.example solanum-1.0 ao mtov");
      handler.maybeHandle(376, null, ":irc.example 376 me :End of MOTD");
      // A duplicate completion must not repeat the bootstrap summary.
      handler.maybeHandle(422, null, ":irc.example 422 me :No MOTD");
      List<String> summaries =
          logs.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .filter(message -> message.contains("replay bootstrap:"))
              .toList();
      assertEquals(1, summaries.size());
      assertTrue(
          summaries.getFirst().contains("mode=server-buffer-if-available zncDetected=false"));
    } finally {
      logger.detachAppender(logs);
      logger.setLevel(previous);
      logs.stop();
    }
  }

  @Test
  void maybeHandleRegistrationCompleteEmitsReadyAndRequestsBootstrap() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.markZncDetected();
    conn.setZncPlaybackCapAcked(true);
    conn.setSojuBouncerNetworksCapAcked(true);

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
  void registrationRequestsAllPlaybackWhenCursorIsUnknown() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.setZncPlaybackCapAcked(true);
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(conn, new ArrayList<>(), serverId -> OptionalLong.empty(), false, false);
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);

    assertTrue(handler.maybeHandle(422, bot, ":server 422 me :MOTD File is missing"));

    verify(outputIrc).message("*playback", "play * 0");
  }

  @Test
  void registrationRequestsPlaybackOncePerSessionAndAgainAfterReconnectReset() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.setZncPlaybackCapAcked(true);
    PlaybackCursorProvider cursorProvider = mock(PlaybackCursorProvider.class);
    when(cursorProvider.lastSeenEpochSeconds("libera"))
        .thenReturn(OptionalLong.of(20L), OptionalLong.of(30L));
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(conn, new ArrayList<>(), cursorProvider, false, false);
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);

    assertTrue(handler.maybeHandle(376, bot, ":server 376 me :End of /MOTD command."));
    assertTrue(handler.maybeHandle(422, bot, ":server 422 me :MOTD File is missing"));
    verify(outputIrc).message("*playback", "play * 19");
    verify(outputIrc, never()).message("*playback", "play * 29");

    conn.clearZncPlaybackRequest();
    handler = newHandler(conn, new ArrayList<>(), cursorProvider, false, false);
    assertTrue(handler.maybeHandle(376, bot, ":server 376 me :End of /MOTD command."));

    verify(outputIrc).message("*playback", "play * 29");
  }

  @Test
  void registrationDoesNotRequestPlaybackWithoutNegotiatedCapability() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PlaybackCursorProvider cursorProvider = mock(PlaybackCursorProvider.class);
    when(cursorProvider.lastSeenEpochSeconds("libera")).thenReturn(OptionalLong.empty());
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(conn, new ArrayList<>(), cursorProvider, false, false);
    PircBotX bot = mock(PircBotX.class);

    assertTrue(handler.maybeHandle(376, bot, ":server 376 me :End of /MOTD command."));

    verify(cursorProvider, times(1)).lastSeenEpochSeconds("libera");
    verify(bot, never()).sendIRC();
  }

  @Test
  void registrationRequestsRetainedBufferWhenCursorSnapshotFails() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.setZncPlaybackCapAcked(true);
    PlaybackCursorProvider cursorProvider = mock(PlaybackCursorProvider.class);
    when(cursorProvider.lastSeenEpochSeconds("libera"))
        .thenThrow(new IllegalStateException("DB unavailable"));
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(conn, new ArrayList<>(), cursorProvider, false, false);
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);

    assertTrue(handler.maybeHandle(376, bot, ":server 376 me :End of /MOTD command."));

    verify(outputIrc).message("*playback", "play * 0");
    verify(cursorProvider, times(1)).lastSeenEpochSeconds("libera");
  }

  @Test
  void registrationPlaybackUsesInjectedRuntimeProvider() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.setZncPlaybackCapAcked(true);
    Ircv3OutboundCommandProvider provider =
        new Ircv3OutboundCommandProvider() {
          @Override
          public String providerId() {
            return "custom-history";
          }

          @Override
          public Set<Ircv3OutboundCommandOperation> operations() {
            return Set.of(Ircv3OutboundCommandOperation.ZNC_PLAYBACK);
          }

          @Override
          public List<String> build(
              Ircv3OutboundCommandOperation operation, Ircv3OutboundCommandRequest request) {
            return List.of(
                "custom-bootstrap "
                    + request.target()
                    + " "
                    + request.timestamp().getEpochSecond());
          }
        };
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(
            conn,
            new ArrayList<>(),
            serverId -> OptionalLong.of(20L),
            false,
            false,
            Ircv3OutboundCommandRuntimeCatalog.fromProviders(List.of(provider)));

    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);

    assertTrue(handler.maybeHandle(376, bot, ":server 376 me :End of /MOTD command."));

    verify(outputIrc).message("*playback", "custom-bootstrap * 19");
  }

  @Test
  void maybeHandleMyInfoDetectsZncAndPublishesStatusLine() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxRegistrationLifecycleHandler handler =
        newHandler(conn, events, serverId -> OptionalLong.empty(), false, true);

    assertTrue(handler.maybeHandle(4, null, ":server 004 me irc.example znc-1.9.1 ao mtov"));

    assertTrue(conn.isZncDetected());
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
    var testRuntime = runtime();
    return newHandler(
        conn,
        events,
        playbackCursorProvider,
        sojuDiscoveryEnabled,
        zncDiscoveryEnabled,
        testRuntime.catalogs().outboundCommands(),
        testRuntime);
  }

  private static PircbotxRegistrationLifecycleHandler newHandler(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      PlaybackCursorProvider playbackCursorProvider,
      boolean sojuDiscoveryEnabled,
      boolean zncDiscoveryEnabled,
      Ircv3OutboundCommandRuntimeCatalog outboundCommandRuntimeCatalog) {
    return newHandler(
        conn,
        events,
        playbackCursorProvider,
        sojuDiscoveryEnabled,
        zncDiscoveryEnabled,
        outboundCommandRuntimeCatalog,
        runtime());
  }

  private static PircbotxRegistrationLifecycleHandler newHandler(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      PlaybackCursorProvider playbackCursorProvider,
      boolean sojuDiscoveryEnabled,
      boolean zncDiscoveryEnabled,
      Ircv3OutboundCommandRuntimeCatalog outboundCommandRuntimeCatalog,
      Ircv3RuntimeTestFixtures.Runtime testRuntime) {
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            sojuDiscoveryEnabled,
            zncDiscoveryEnabled,
            new BouncerBackendRegistry(List.of()),
            BouncerDiscoveryEventPort.noOp());
    PircbotxServerResponseEmitter serverResponses =
        serverResponses("libera", events::add, testRuntime);
    return registrationLifecycle(
        "libera",
        conn,
        playbackCursorProvider,
        bouncerDiscovery,
        serverResponses,
        events::add,
        outboundCommandRuntimeCatalog,
        testRuntime);
  }
}
