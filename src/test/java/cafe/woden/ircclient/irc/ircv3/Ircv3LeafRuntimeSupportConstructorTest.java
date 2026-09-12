package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Ircv3LeafRuntimeSupportConstructorTest {

  @Test
  void leafSupportsRequireExplicitCatalogDependencies() {
    assertBoundary(Ircv3SaslRuntimeSupport.class, Ircv3InboundCommandSignalRuntimeCatalog.class);
    assertBoundary(
        Ircv3ZncPlaybackRuntimeSupport.class,
        Ircv3InboundCommandSignalRuntimeCatalog.class,
        Ircv3InboundTagSignalRuntimeCatalog.class);
    assertBoundary(Ircv3EchoMessageRuntimeSupport.class, Ircv3InboundTagSignalRuntimeCatalog.class);
    assertBoundary(
        Ircv3MonitorCommandRuntimeSupport.class, Ircv3OutboundCommandRuntimeCatalog.class);
    assertBoundary(Ircv3ChatHistoryRuntimeSupport.class, Ircv3OutboundCommandRuntimeCatalog.class);
    assertBoundary(
        Ircv3LabeledResponseRuntimeSupport.class, Ircv3InboundTagSignalRuntimeCatalog.class);
    assertBoundary(
        Ircv3IsupportRuntimeSupport.class, Ircv3InboundCommandSignalRuntimeCatalog.class);
  }

  private static void assertBoundary(Class<?> supportType, Class<?>... parameterTypes) {
    assertEquals(1, supportType.getConstructors().length, supportType.getSimpleName());
    assertArrayEquals(
        parameterTypes,
        supportType.getConstructors()[0].getParameterTypes(),
        supportType.getSimpleName());
    assertFalse(
        Arrays.stream(supportType.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("applicationClasspath")),
        supportType.getSimpleName());
  }
}
