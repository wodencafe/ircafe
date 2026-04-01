package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QuasselBackendNamedCommandHandlerTest {

  private final QuasselBackendNamedCommandHandler handler = new QuasselBackendNamedCommandHandler();

  @Test
  void exposesSupportedCommandNames() {
    Set<String> commandNames = handler.supportedCommandNames();
    assertTrue(commandNames.contains("quasselsetup"));
    assertTrue(commandNames.contains("qsetup"));
    assertTrue(commandNames.contains("quasselnet"));
    assertTrue(commandNames.contains("qnet"));
  }

  @Test
  void parsesQuasselSetupAliasToCanonicalCommand() {
    ParsedInput parsed = handler.parse("/qsetup core", "qsetup");

    assertTrue(parsed instanceof ParsedInput.BackendNamed);
    assertEquals(
        BackendNamedCommandNames.QUASSEL_SETUP, ((ParsedInput.BackendNamed) parsed).command());
    assertEquals("core", ((ParsedInput.BackendNamed) parsed).args());
  }

  @Test
  void parsesQuasselNetworkAliasToCanonicalCommand() {
    ParsedInput parsed = handler.parse("/qnet list", "qnet");

    assertTrue(parsed instanceof ParsedInput.BackendNamed);
    assertEquals(
        BackendNamedCommandNames.QUASSEL_NETWORK, ((ParsedInput.BackendNamed) parsed).command());
    assertEquals("list", ((ParsedInput.BackendNamed) parsed).args());
  }
}
