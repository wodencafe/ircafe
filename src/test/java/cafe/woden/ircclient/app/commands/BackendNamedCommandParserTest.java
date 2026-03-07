package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BackendNamedCommandParserTest {

  private final BackendNamedCommandParser parser =
      new BackendNamedCommandParser(List.of(new QuasselBackendNamedCommandHandler()));

  @Test
  void parsesQuasselSetupCommands() {
    ParsedInput full = parser.parse("/quasselsetup core");
    assertTrue(full instanceof ParsedInput.BackendNamed);
    assertEquals("quasselsetup", ((ParsedInput.BackendNamed) full).command());
    assertEquals("core", ((ParsedInput.BackendNamed) full).args());

    ParsedInput alias = parser.parse("/qsetup");
    assertTrue(alias instanceof ParsedInput.BackendNamed);
    assertEquals("quasselsetup", ((ParsedInput.BackendNamed) alias).command());
    assertEquals("", ((ParsedInput.BackendNamed) alias).args());
  }

  @Test
  void parsesQuasselNetworkCommands() {
    ParsedInput full = parser.parse("/quasselnet connect libera");
    assertTrue(full instanceof ParsedInput.BackendNamed);
    assertEquals("quasselnet", ((ParsedInput.BackendNamed) full).command());
    assertEquals("connect libera", ((ParsedInput.BackendNamed) full).args());

    ParsedInput alias = parser.parse("/qnet list");
    assertTrue(alias instanceof ParsedInput.BackendNamed);
    assertEquals("quasselnet", ((ParsedInput.BackendNamed) alias).command());
    assertEquals("list", ((ParsedInput.BackendNamed) alias).args());
  }

  @Test
  void returnsNullForNonBackendCommands() {
    assertNull(parser.parse("/join #ircafe"));
  }

  @Test
  void delegatesToRegisteredHandlers() {
    BackendNamedCommandHandler custom =
        line ->
            BackendNamedCommandParser.matchesCommand(line, "/backendping")
                ? new ParsedInput.Help("backendping")
                : null;
    BackendNamedCommandParser parser = new BackendNamedCommandParser(List.of(custom));

    ParsedInput parsed = parser.parse("/backendping");
    assertTrue(parsed instanceof ParsedInput.Help);
    assertEquals("backendping", ((ParsedInput.Help) parsed).topic());
  }
}
