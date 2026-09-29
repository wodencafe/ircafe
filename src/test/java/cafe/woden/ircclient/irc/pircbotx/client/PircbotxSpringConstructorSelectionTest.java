package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigChatBehaviorAdapter;
import cafe.woden.ircclient.config.RuntimeConfigCtcpReplyAdapter;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.config.properties.ZncProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundCommandSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageMutationRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageTagsRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageTagsRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.ircv3.Ircv3ServerTimeRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.playback.PlaybackCursorProvider;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PircbotxSpringConstructorSelectionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              PircbotxBridgeListenerFactory.class,
              PircbotxIrcClientService.class,
              RuntimeConfigChatBehaviorAdapter.class,
              RuntimeConfigCtcpReplyAdapter.class)
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
          .withBean(
              Ircv3InboundCommandSignalRuntimeCatalog.class,
              () -> mock(Ircv3InboundCommandSignalRuntimeCatalog.class))
          .withBean(
              Ircv3InboundTagSignalRuntimeCatalog.class,
              () -> mock(Ircv3InboundTagSignalRuntimeCatalog.class))
          .withBean(
              Ircv3OutboundCommandRuntimeCatalog.class,
              () -> mock(Ircv3OutboundCommandRuntimeCatalog.class))
          .withBean(
              Ircv3ServerTimeRuntimeSupport.class, () -> mock(Ircv3ServerTimeRuntimeSupport.class))
          .withBean(
              Ircv3MessageTagsRuntimeSupport.class,
              () -> mock(Ircv3MessageTagsRuntimeSupport.class))
          .withBean(
              Ircv3RuntimeCatalogs.class,
              () ->
                  new Ircv3RuntimeCatalogs(
                      mock(Ircv3InboundCommandSignalRuntimeCatalog.class),
                      mock(Ircv3InboundTagSignalRuntimeCatalog.class),
                      mock(Ircv3OutboundCommandRuntimeCatalog.class),
                      mock(Ircv3MessageMutationRuntimeCatalog.class),
                      mock(Ircv3MessageTagsRuntimeCatalog.class)))
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

  @Test
  void outerCompositionRootsExposeOnlyExplicitRuntimeConstructors() {
    assertExplicitRuntimeConstructors(PircbotxBotFactory.class, 2);
    assertExplicitRuntimeConstructors(PircbotxBridgeListenerFactory.class, 2);
    assertExplicitRuntimeConstructors(PircbotxInputParserHookInstaller.class, 2);
  }

  private static void assertExplicitRuntimeConstructors(Class<?> type, int expectedCount) {
    Constructor<?>[] constructors = type.getConstructors();
    assertEquals(expectedCount, constructors.length, type.getSimpleName());
    assertEquals(
        1L,
        Arrays.stream(constructors)
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .count(),
        type.getSimpleName() + " Spring constructor count");
    for (Constructor<?> constructor : constructors) {
      assertFalse(
          constructor.isAnnotationPresent(Deprecated.class),
          constructor + " should not expose a classpath compatibility path");
      assertTrue(
          Arrays.stream(constructor.getParameterTypes())
              .anyMatch(
                  parameterType ->
                      parameterType == Ircv3RuntimeCatalogs.class
                          || parameterType.getSimpleName().endsWith("RuntimeCatalog")),
          constructor + " should require explicit IRCv3 runtime composition");
    }
  }
}
