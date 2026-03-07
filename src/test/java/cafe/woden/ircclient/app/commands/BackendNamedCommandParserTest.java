package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackendNamedCommandParserTest {

  private final BackendNamedCommandParser parser = new BackendNamedCommandParser();

  @Test
  void parsesQuasselSetupCommands() {
    ParsedInput full = parser.parse("/quasselsetup core");
    assertTrue(full instanceof ParsedInput.QuasselSetup);
    assertEquals("core", ((ParsedInput.QuasselSetup) full).serverId());

    ParsedInput alias = parser.parse("/qsetup");
    assertTrue(alias instanceof ParsedInput.QuasselSetup);
    assertEquals("", ((ParsedInput.QuasselSetup) alias).serverId());
  }

  @Test
  void parsesQuasselNetworkCommands() {
    ParsedInput full = parser.parse("/quasselnet connect libera");
    assertTrue(full instanceof ParsedInput.QuasselNetwork);
    assertEquals("connect libera", ((ParsedInput.QuasselNetwork) full).args());

    ParsedInput alias = parser.parse("/qnet list");
    assertTrue(alias instanceof ParsedInput.QuasselNetwork);
    assertEquals("list", ((ParsedInput.QuasselNetwork) alias).args());
  }

  @Test
  void returnsNullForNonBackendCommands() {
    assertNull(parser.parse("/join #ircafe"));
  }
}
