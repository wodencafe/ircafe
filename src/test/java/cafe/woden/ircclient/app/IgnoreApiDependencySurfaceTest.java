package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.ignore.OutboundIgnoreCommandService;
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
  void outboundIgnoreCommandBeansDependOnSplitIgnorePortsOnly() {
    Class<?> ignoreHardCommandSupport =
        type("cafe.woden.ircclient.app.outbound.ignore.IgnoreHardCommandSupport");
    Class<?> ignoreSoftCommandSupport =
        type("cafe.woden.ircclient.app.outbound.ignore.IgnoreSoftCommandSupport");

    assertConstructorIncludes(OutboundIgnoreCommandService.class, ignoreHardCommandSupport);
    assertConstructorIncludes(OutboundIgnoreCommandService.class, ignoreSoftCommandSupport);
    assertConstructorIncludes(ignoreHardCommandSupport, IgnoreListQueryPort.class);
    assertConstructorIncludes(ignoreHardCommandSupport, IgnoreListCommandPort.class);
    assertConstructorIncludes(ignoreSoftCommandSupport, IgnoreListQueryPort.class);
    assertConstructorIncludes(ignoreSoftCommandSupport, IgnoreListCommandPort.class);
    assertNoIgnoreInternalsInTypeSurface(OutboundIgnoreCommandService.class);
    assertNoIgnoreInternalsInTypeSurface(ignoreHardCommandSupport);
    assertNoIgnoreInternalsInTypeSurface(ignoreSoftCommandSupport);
  }

  private static void assertConstructorIncludes(Class<?> type, Class<?> dependency) {
    boolean found =
        Arrays.stream(type.getDeclaredConstructors())
            .map(Constructor::getParameterTypes)
            .anyMatch(params -> Arrays.asList(params).contains(dependency));
    assertTrue(
        found, () -> type.getSimpleName() + " should depend on " + dependency.getSimpleName());
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

  private static Class<?> type(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException ex) {
      throw new AssertionError("Expected class to exist: " + className, ex);
    }
  }
}
