package cafe.woden.ircclient.app.commands;

import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Parses backend-named slash commands that are intentionally scoped to a specific backend family.
 *
 * <p>This keeps backend command naming out of the main semantic parser flow.
 */
@Component
@ApplicationLayer
public class BackendNamedCommandParser {

  private final BackendNamedCommandCatalog commandCatalog;

  @Autowired
  public BackendNamedCommandParser(BackendNamedCommandCatalog commandCatalog) {
    this.commandCatalog = Objects.requireNonNull(commandCatalog, "commandCatalog");
  }

  BackendNamedCommandParser(List<BackendNamedCommandHandler> handlers) {
    this(BackendNamedCommandCatalog.fromHandlers(handlers));
  }

  public ParsedInput parse(String line) {
    return commandCatalog.parse(line);
  }

  static String argAfter(String line, String cmd) {
    if (line == null || cmd == null) return "";
    if (line.equalsIgnoreCase(cmd)) return "";
    if (line.length() <= cmd.length()) return "";
    String rest = line.substring(cmd.length());
    return rest.trim();
  }

  static boolean matchesCommand(String line, String command) {
    if (line == null || command == null || line.isBlank() || command.isBlank()) return false;
    if (line.equalsIgnoreCase(command)) return true;
    return line.regionMatches(true, 0, command, 0, command.length())
        && line.length() > command.length()
        && Character.isWhitespace(line.charAt(command.length()));
  }
}
