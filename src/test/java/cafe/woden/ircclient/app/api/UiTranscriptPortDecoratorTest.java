package cafe.woden.ircclient.app.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiTranscriptPortDecoratorTest {

  @Test
  void explicitlyOverridesEveryUiTranscriptPortMethod() {
    List<String> missing =
        UiPortDecoratorTest.missingOverrides(
            UiTranscriptPort.class, UiTranscriptPortDecorator.class);
    missing.sort(Comparator.naturalOrder());
    assertTrue(
        missing.isEmpty(),
        () ->
            "UiTranscriptPortDecorator is missing explicit forwarders for: "
                + String.join(", ", missing));
  }
}
