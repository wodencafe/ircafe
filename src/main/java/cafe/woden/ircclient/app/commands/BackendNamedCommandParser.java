package cafe.woden.ircclient.app.commands;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Parses backend-named slash commands that are intentionally scoped to a specific backend family.
 *
 * <p>This keeps backend command naming out of the main semantic parser flow.
 */
@Component
public class BackendNamedCommandParser {

  private final List<BackendNamedCommandHandler> handlers;

  public BackendNamedCommandParser(List<BackendNamedCommandHandler> handlers) {
    this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
  }

  public ParsedInput parse(String line) {
    String raw = Objects.toString(line, "").trim();
    if (raw.isEmpty()) return null;

    for (BackendNamedCommandHandler handler : handlers) {
      ParsedInput parsed = handler.parse(raw);
      if (parsed != null) return parsed;
    }

    return null;
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
