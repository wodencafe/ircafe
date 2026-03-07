package cafe.woden.ircclient.app.commands;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Parses backend-named slash commands that are intentionally scoped to a specific backend family.
 *
 * <p>This keeps backend command naming out of the main semantic parser flow.
 */
@Component
public class BackendNamedCommandParser {

  private final Map<String, BackendNamedCommandHandler> handlersByCommandName;

  public BackendNamedCommandParser(List<BackendNamedCommandHandler> handlers) {
    List<BackendNamedCommandHandler> safeHandlers =
        List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    this.handlersByCommandName = indexHandlersByCommandName(safeHandlers);
  }

  public ParsedInput parse(String line) {
    String raw = Objects.toString(line, "").trim();
    if (raw.isEmpty()) return null;
    if (!raw.startsWith("/")) return null;

    String commandName = extractCommandName(raw);
    if (commandName.isEmpty()) return null;
    BackendNamedCommandHandler handler = handlersByCommandName.get(commandName);
    if (handler == null) return null;
    return handler.parse(raw, commandName);
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

  private static Map<String, BackendNamedCommandHandler> indexHandlersByCommandName(
      List<BackendNamedCommandHandler> handlers) {
    LinkedHashMap<String, BackendNamedCommandHandler> index = new LinkedHashMap<>();
    for (BackendNamedCommandHandler handler : handlers) {
      Set<String> commandNames =
          Objects.requireNonNullElse(handler.supportedCommandNames(), Set.<String>of());
      for (String commandName : commandNames) {
        String normalized = normalizeCommandName(commandName);
        BackendNamedCommandHandler previous = index.putIfAbsent(normalized, handler);
        if (previous != null && previous != handler) {
          throw new IllegalStateException(
              "Duplicate backend named parser handler registered for command '" + normalized + "'");
        }
      }
    }
    return Map.copyOf(index);
  }

  private static String extractCommandName(String line) {
    int end = line.indexOf(' ');
    String token = end < 0 ? line : line.substring(0, end);
    return normalizeCommandName(token);
  }

  private static String normalizeCommandName(String commandName) {
    String name = Objects.toString(commandName, "").trim().toLowerCase(Locale.ROOT);
    if (name.startsWith("/")) name = name.substring(1).trim();
    return name;
  }
}
