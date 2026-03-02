package cafe.woden.ircclient.irc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.irc.soju.SojuEphemeralNetworkImporter;
import cafe.woden.ircclient.irc.znc.ZncEphemeralNetworkImporter;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputRaw;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class PircbotxIrcClientServiceLagProbeTest {

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void requestLagProbeSkipsPingUntilRegistrationCompletes() throws Exception {
    PircbotxIrcClientService service = newService();
    PircbotxConnectionState c = conn(service, "libera");

    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);
    c.botRef.set(bot);
    c.registrationComplete.set(false);

    service.requestLagProbe("libera").blockingAwait();

    verify(outputRaw, never()).rawLine(argThat(v -> v != null && v.startsWith("PING :")));
  }

  @Test
  void requestLagProbeSendsPingAfterRegistrationCompletes() throws Exception {
    PircbotxIrcClientService service = newService();
    PircbotxConnectionState c = conn(service, "libera");

    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);
    c.botRef.set(bot);
    c.registrationComplete.set(true);

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
    SojuEphemeralNetworkImporter sojuImporter = mock(SojuEphemeralNetworkImporter.class);
    ZncEphemeralNetworkImporter zncImporter = mock(ZncEphemeralNetworkImporter.class);
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);

    ObjectProvider<PlaybackCursorProvider> playbackCursorProviderProvider =
        new StaticListableBeanFactory().getBeanProvider(PlaybackCursorProvider.class);

    return new PircbotxIrcClientService(
        new IrcProperties(null, List.of()),
        serverCatalog,
        inputParserHookInstaller,
        botFactory,
        new SojuProperties(null, null),
        new ZncProperties(null, null),
        runtimeConfig,
        stsPolicies,
        sojuImporter,
        zncImporter,
        timers,
        playbackCursorProviderProvider);
  }

  private static PircbotxConnectionState conn(PircbotxIrcClientService service, String serverId)
      throws Exception {
    Method conn = PircbotxIrcClientService.class.getDeclaredMethod("conn", String.class);
    conn.setAccessible(true);
    return (PircbotxConnectionState) conn.invoke(service, serverId);
  }
}
