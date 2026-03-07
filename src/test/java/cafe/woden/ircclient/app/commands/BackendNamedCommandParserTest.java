package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BackendNamedCommandParserTest {

  private final BackendNamedCommandParser parser =
      new BackendNamedCommandParser(List.of(new QuasselBackendNamedCommandHandler()));

  @Test
  void parsesQuasselSetupCommands() {
    ParsedInput full = parser.parse("/quasselsetup core");
    assertTrue(full instanceof ParsedInput.BackendNamed);
    assertEquals(
        BackendNamedCommandNames.QUASSEL_SETUP, ((ParsedInput.BackendNamed) full).command());
    assertEquals("core", ((ParsedInput.BackendNamed) full).args());

    ParsedInput alias = parser.parse("/qsetup");
    assertTrue(alias instanceof ParsedInput.BackendNamed);
    assertEquals(
        BackendNamedCommandNames.QUASSEL_SETUP, ((ParsedInput.BackendNamed) alias).command());
    assertEquals("", ((ParsedInput.BackendNamed) alias).args());
  }

  @Test
  void parsesQuasselNetworkCommands() {
    ParsedInput full = parser.parse("/quasselnet connect libera");
    assertTrue(full instanceof ParsedInput.BackendNamed);
    assertEquals(
        BackendNamedCommandNames.QUASSEL_NETWORK, ((ParsedInput.BackendNamed) full).command());
    assertEquals("connect libera", ((ParsedInput.BackendNamed) full).args());

    ParsedInput alias = parser.parse("/qnet list");
    assertTrue(alias instanceof ParsedInput.BackendNamed);
    assertEquals(
        BackendNamedCommandNames.QUASSEL_NETWORK, ((ParsedInput.BackendNamed) alias).command());
    assertEquals("list", ((ParsedInput.BackendNamed) alias).args());
  }

  @Test
  void returnsNullForNonBackendCommands() {
    assertNull(parser.parse("/join #ircafe"));
  }

  @Test
  void delegatesToRegisteredHandlers() {
    BackendNamedCommandHandler custom =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return new ParsedInput.Help(matchedCommandName);
          }
        };
    BackendNamedCommandParser parser = new BackendNamedCommandParser(List.of(custom));

    ParsedInput parsed = parser.parse("/backendping");
    assertTrue(parsed instanceof ParsedInput.Help);
    assertEquals("backendping", ((ParsedInput.Help) parsed).topic());
  }

  @Test
  void duplicateCommandRegistrationsFailFast() {
    BackendNamedCommandHandler first =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return null;
          }
        };
    BackendNamedCommandHandler second =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return null;
          }
        };

    assertThrows(
        IllegalStateException.class, () -> new BackendNamedCommandParser(List.of(first, second)));
  }
}
