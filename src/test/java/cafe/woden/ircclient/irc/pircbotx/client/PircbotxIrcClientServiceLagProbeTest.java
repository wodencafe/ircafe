package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.*;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputRaw;

class PircbotxIrcClientServiceLagProbeTest {

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void requestLagProbeFailsUntilRegistrationCompletes() throws Exception {
    PircbotxIrcClientService service = newService();
    PircbotxConnectionState c = conn(service, "libera");

    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);
    c.setBot(bot);

    assertThrows(
        IllegalStateException.class, () -> service.requestLagProbe("libera").blockingAwait());

    verify(outputRaw, never()).rawLine(argThat(v -> v != null && v.startsWith("PING :")));
  }

  @Test
  void requestLagProbeSendsPingAfterRegistrationCompletes() throws Exception {
    PircbotxIrcClientService service = newService();
    PircbotxConnectionState c = conn(service, "libera");

    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);
    c.setBot(bot);
    c.markRegistrationComplete();

    service.requestLagProbe("libera").blockingAwait();

    verify(outputRaw).rawLine(argThat(v -> v != null && v.startsWith("PING :ircafe-lag-")));
  }

  private static PircbotxIrcClientService newService() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    PircbotxInputParserHookInstaller inputParserHookInstaller =
        mock(PircbotxInputParserHookInstaller.class);
    PircbotxBotFactory botFactory = mock(PircbotxBotFactory.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    Ircv3StsPolicyService stsPolicies = mock(Ircv3StsPolicyService.class);
    BouncerBackendRegistry bouncerBackends = mock(BouncerBackendRegistry.class);
    BouncerDiscoveryEventPort bouncerDiscoveryEvents = mock(BouncerDiscoveryEventPort.class);
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    when(bouncerBackends.backendIds()).thenReturn(Set.of());
    ServerIsupportState serverIsupportState = new ServerIsupportState();
    PircbotxBridgeListenerFactory bridgeListenerFactory =
        new PircbotxBridgeListenerFactory(
            bouncerBackends,
            bouncerDiscoveryEvents,
            new NoOpPlaybackCursorProvider(),
            serverIsupportState,
            new SojuProperties(null, null),
            new ZncProperties(null, null));

    return new PircbotxIrcClientService(
        new IrcProperties(null, List.of()),
        serverCatalog,
        inputParserHookInstaller,
        botFactory,
        bridgeListenerFactory,
        (CtcpReplyRuntimeConfigPort) runtimeConfig,
        (ChatCommandRuntimeConfigPort) runtimeConfig,
        stsPolicies,
        bouncerBackends,
        bouncerDiscoveryEvents,
        timers,
        serverIsupportState);
  }

  private static PircbotxConnectionState conn(PircbotxIrcClientService service, String serverId)
      throws Exception {
    Method conn = PircbotxIrcClientService.class.getDeclaredMethod("conn", String.class);
    conn.setAccessible(true);
    return (PircbotxConnectionState) conn.invoke(service, serverId);
  }
}
