package cafe.woden.ircclient.app.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiPortDecoratorTest {

  @Test
  void explicitlyOverridesEveryUiPortMethod() {
    List<String> missing = missingOverrides(UiPort.class, UiPortDecorator.class);

    missing.sort(Comparator.naturalOrder());
    assertTrue(
        missing.isEmpty(),
        () -> "UiPortDecorator is missing explicit forwarders for: " + String.join(", ", missing));
  }

  static List<String> missingOverrides(Class<?> portType, Class<?> decoratorType) {
    List<String> missing = new ArrayList<>();
    for (Method method : portType.getMethods()) {
      if (method.getDeclaringClass() == Object.class) continue;
      if (!declaresOverride(decoratorType, method)) {
        missing.add(signature(method));
      }
    }
    return missing;
  }

  private static boolean declaresOverride(Class<?> decoratorType, Method uiMethod) {
    try {
      decoratorType.getDeclaredMethod(uiMethod.getName(), uiMethod.getParameterTypes());
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    }
  }

  private static String signature(Method method) {
    return method.getName()
        + "("
        + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .reduce((a, b) -> a + ", " + b)
            .orElse("")
        + ")";
  }
}
