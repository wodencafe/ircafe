package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import cafe.woden.ircclient.app.outbound.chathistory.OutboundChatHistoryCommandService;
import cafe.woden.ircclient.app.outbound.monitor.OutboundMonitorCommandService;
import cafe.woden.ircclient.app.outbound.support.OutboundRawLineCorrelationService;
import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundCommandSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3LabeledResponseRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageMutationRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageTagsRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3MonitorCommandRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.matrix.MatrixIrcv3RuntimeSupport;
import cafe.woden.ircclient.irc.pircbotx.client.PircbotxBotFactory;
import cafe.woden.ircclient.irc.pircbotx.listener.PircbotxBridgeListenerFactory;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.quassel.QuasselIrcv3RuntimeSupport;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

class Ircv3RuntimeWiringArchitectureTest {

  @Test
  void springCatalogBundleContainsAllRuntimeCatalogs() {
    assertThat(Ircv3RuntimeCatalogs.class.isAnnotationPresent(Component.class)).isTrue();
    assertThat(Ircv3RuntimeCatalogs.class.isRecord()).isTrue();
    assertThat(
            Arrays.stream(Ircv3RuntimeCatalogs.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList())
        .containsExactlyInAnyOrder(
            Ircv3InboundCommandSignalRuntimeCatalog.class,
            Ircv3InboundTagSignalRuntimeCatalog.class,
            Ircv3OutboundCommandRuntimeCatalog.class,
            Ircv3MessageMutationRuntimeCatalog.class,
            Ircv3MessageTagsRuntimeCatalog.class);
  }

  @Test
  void transportsInjectTheCanonicalRuntimeCatalogBundle() {
    for (Class<?> type :
        List.of(
            QuasselIrcv3RuntimeSupport.class,
            MatrixIrcv3RuntimeSupport.class,
            PircbotxInputParserHookInstaller.class,
            PircbotxBridgeListenerFactory.class,
            PircbotxBotFactory.class)) {
      assertSpringConstructorInjects(type, Ircv3RuntimeCatalogs.class);
    }
  }

  @Test
  void outboundServicesInjectRuntimeSupport() {
    assertSpringConstructorInjects(
        OutboundChatHistoryCommandService.class, Ircv3ChatHistoryRuntimeSupport.class);
    assertSpringConstructorInjects(
        OutboundMonitorCommandService.class, Ircv3MonitorCommandRuntimeSupport.class);
    assertSpringConstructorInjects(
        OutboundRawLineCorrelationService.class,
        Ircv3OutboundCommandRuntimeCatalog.class,
        Ircv3LabeledResponseRuntimeSupport.class);
  }

  private static void assertSpringConstructorInjects(Class<?> type, Class<?>... dependencies) {
    var constructors =
        Arrays.stream(type.getConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .toList();
    assertThat(constructors).as("explicit Spring constructor of %s", type.getName()).hasSize(1);
    assertThat(constructors.getFirst().getParameterTypes()).contains(dependencies);
  }
}
