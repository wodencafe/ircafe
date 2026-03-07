package cafe.woden.ircclient.app.commands;

import org.springframework.stereotype.Component;

/**
 * Parses backend-named slash commands that are intentionally scoped to a specific backend family.
 *
 * <p>This keeps backend command naming out of the main semantic parser flow.
 */
@Component
public class BackendNamedCommandParser {

  public ParsedInput parse(String line) {
    if (matchesCommand(line, "/quasselsetup") || matchesCommand(line, "/qsetup")) {
      String serverId =
          matchesCommand(line, "/quasselsetup")
              ? argAfter(line, "/quasselsetup")
              : argAfter(line, "/qsetup");
      return new ParsedInput.QuasselSetup(serverId);
    }

    if (matchesCommand(line, "/quasselnet") || matchesCommand(line, "/qnet")) {
      String args =
          matchesCommand(line, "/quasselnet")
              ? argAfter(line, "/quasselnet")
              : argAfter(line, "/qnet");
      return new ParsedInput.QuasselNetwork(args);
    }

    return null;
  }

  private static String argAfter(String line, String cmd) {
    if (line == null || cmd == null) return "";
    if (line.equalsIgnoreCase(cmd)) return "";
    if (line.length() <= cmd.length()) return "";
    String rest = line.substring(cmd.length());
    return rest.trim();
  }

  private static boolean matchesCommand(String line, String command) {
    if (line == null || command == null || line.isBlank() || command.isBlank()) return false;
    if (line.equalsIgnoreCase(command)) return true;
    return line.regionMatches(true, 0, command, 0, command.length())
        && line.length() > command.length()
        && Character.isWhitespace(line.charAt(command.length()));
  }
}
