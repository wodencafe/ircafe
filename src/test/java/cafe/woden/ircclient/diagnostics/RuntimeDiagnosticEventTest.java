package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RuntimeDiagnosticEventTest {

  @Test
  void normalizesBlankFieldsAndInvalidLevel() {
    RuntimeDiagnosticEvent event = new RuntimeDiagnosticEvent(null, " noisy ", "  ", "  ", null);

    assertNotNull(event.at());
    assertEquals("INFO", event.level());
    assertEquals("Event", event.type());
    assertEquals("", event.summary());
    assertEquals("", event.details());
  }

  @Test
  void acceptsKnownLevelAndKeepsProvidedValues() {
    Instant at = Instant.parse("2026-01-01T00:00:00Z");
    RuntimeDiagnosticEvent event =
        new RuntimeDiagnosticEvent(at, "warn", "TypeA", "Summary", "Details");

    assertEquals(at, event.at());
    assertEquals("WARN", event.level());
    assertEquals("TypeA", event.type());
    assertEquals("Summary", event.summary());
    assertEquals("Details", event.details());
  }
}
