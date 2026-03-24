package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.irc.pircbotx.*;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.playback.PlaybackCursorProvider;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PircbotxSpringConstructorSelectionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              PircbotxBridgeListenerFactory.class, PircbotxIrcClientService.class)
          .withBean(
              IrcProperties.class,
              () ->
                  new IrcProperties(
                      new IrcProperties.Client("IRCafe test", null, null, null, null), List.of()))
          .withBean(ServerCatalog.class, () -> mock(ServerCatalog.class))
          .withBean(
              PircbotxInputParserHookInstaller.class,
              () -> mock(PircbotxInputParserHookInstaller.class))
          .withBean(PircbotxBotFactory.class, () -> mock(PircbotxBotFactory.class))
          .withBean(RuntimeConfigStore.class, () -> mock(RuntimeConfigStore.class))
          .withBean(Ircv3StsPolicyService.class, () -> mock(Ircv3StsPolicyService.class))
          .withBean(BouncerBackendRegistry.class, () -> mock(BouncerBackendRegistry.class))
          .withBean(BouncerDiscoveryEventPort.class, () -> mock(BouncerDiscoveryEventPort.class))
          .withBean(PircbotxConnectionTimersRx.class, () -> mock(PircbotxConnectionTimersRx.class))
          .withBean(ServerIsupportStatePort.class, () -> mock(ServerIsupportStatePort.class))
          .withBean(PlaybackCursorProvider.class, () -> mock(PlaybackCursorProvider.class))
          .withBean(SojuProperties.class, () -> new SojuProperties(Map.of(), null))
          .withBean(ZncProperties.class, () -> new ZncProperties(Map.of(), null));

  @Test
  void createsPircbotxBeansThroughExplicitSpringConstructors() {
    runner.run(
        ctx -> {
          assertNotNull(ctx.getBean(PircbotxBridgeListenerFactory.class));
          assertNotNull(ctx.getBean(PircbotxIrcClientService.class));
        });
  }
}
