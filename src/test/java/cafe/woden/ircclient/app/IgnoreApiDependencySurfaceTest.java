package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.OutboundChatCommandService;
import cafe.woden.ircclient.app.outbound.OutboundIgnoreCommandService;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class IgnoreApiDependencySurfaceTest {

  @Test
  void targetCoordinatorDependsOnIgnoreQueryPortOnly() {
    assertConstructorIncludes(TargetCoordinator.class, IgnoreListQueryPort.class);
    assertNoIgnoreInternalsInTypeSurface(TargetCoordinator.class);
  }

  @Test
  void outboundChatCommandServiceDependsOnIgnoreCommandPortOnly() {
    assertConstructorIncludes(OutboundChatCommandService.class, IgnoreListCommandPort.class);
    assertNoIgnoreInternalsInTypeSurface(OutboundChatCommandService.class);
  }

  @Test
  void outboundIgnoreCommandServiceDependsOnSplitIgnorePortsOnly() {
    assertConstructorIncludes(OutboundIgnoreCommandService.class, IgnoreListQueryPort.class);
    assertConstructorIncludes(OutboundIgnoreCommandService.class, IgnoreListCommandPort.class);
    assertNoIgnoreInternalsInTypeSurface(OutboundIgnoreCommandService.class);
  }

  private static void assertConstructorIncludes(Class<?> type, Class<?> dependency) {
    boolean found =
        Arrays.stream(type.getDeclaredConstructors())
            .map(Constructor::getParameterTypes)
            .anyMatch(params -> Arrays.asList(params).contains(dependency));
    assertTrue(found, () -> type.getSimpleName() + " should depend on " + dependency.getSimpleName());
  }

  private static void assertNoIgnoreInternalsInTypeSurface(Class<?> type) {
    for (Field field : type.getDeclaredFields()) {
      Class<?> fieldType = field.getType();
      String name = fieldType.getName();
      if (!name.startsWith("cafe.woden.ircclient.ignore")) continue;
      assertTrue(
          name.startsWith("cafe.woden.ircclient.ignore.api"),
          () -> type.getSimpleName() + " should not reference ignore internals: " + name);
    }
  }
}

