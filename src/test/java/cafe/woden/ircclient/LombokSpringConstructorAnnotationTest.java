package cafe.woden.ircclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;

class LombokSpringConstructorAnnotationTest {

  @Test
  void generatedConstructorCopiesSpringAnnotationsToParameters() {
    Constructor<?> constructor = AnnotatedSpringDependencies.class.getDeclaredConstructors()[0];
    Parameter[] parameters = constructor.getParameters();

    assertEquals(2, parameters.length);

    Qualifier qualifier = parameters[0].getAnnotation(Qualifier.class);
    assertNotNull(qualifier);
    assertEquals("primaryPort", qualifier.value());
    assertTrue(parameters[1].isAnnotationPresent(Lazy.class));
  }

  @Test
  void generatedConstructorRetainsNonNullChecks() {
    assertThrows(NullPointerException.class, () -> new AnnotatedSpringDependencies(null, () -> {}));
    assertThrows(NullPointerException.class, () -> new AnnotatedSpringDependencies(() -> {}, null));
  }

  @RequiredArgsConstructor
  private static final class AnnotatedSpringDependencies {
    @NonNull
    @Qualifier("primaryPort")
    private final Runnable qualifiedPort;

    @NonNull @Lazy private final Runnable lazyPort;
  }
}
