package cafe.woden.ircclient.app.outbound.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.app.commands.ParsedInput;
import org.junit.jupiter.api.Test;

class OutboundCommandRegistryTest {

  @Test
  void duplicateRegistrationForSameCommandTypeThrows() {
    OutboundCommandRegistry registry = new OutboundCommandRegistry();
    registry.register(ParsedInput.Say.class, (d, cmd) -> {});

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> registry.register(ParsedInput.Say.class, (d, cmd) -> {}));

    assertEquals(
        "Duplicate outbound command handler registered for " + ParsedInput.Say.class.getName(),
        ex.getMessage());
  }
}
