package cafe.woden.ircclient.app.commands;

import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Small command parser for the input line.
 *
 * <p>Unknown commands return {@link ParsedInput.Unknown}.
 */
@Component
@ApplicationLayer
public class CommandParser {

  private final FilterCommandParser filterCommandParser;
  private final BackendNamedCommandParser backendNamedCommandParser;
  private final List<SlashCommandParseStrategy> strategies;

  public CommandParser(
      FilterCommandParser filterCommandParser,
      BackendNamedCommandParser backendNamedCommandParser) {
    this.filterCommandParser = Objects.requireNonNull(filterCommandParser, "filterCommandParser");
    this.backendNamedCommandParser =
        Objects.requireNonNull(backendNamedCommandParser, "backendNamedCommandParser");
    this.strategies =
        List.of(
            new ConnectionLifecycleSlashCommandParseStrategy(),
            new IdentityMessagingSlashCommandParseStrategy(),
            new ChannelInteractionSlashCommandParseStrategy(),
            new AdvancedFeatureSlashCommandParseStrategy(this.filterCommandParser));
  }

  public ParsedInput parse(String raw) {
    String line = raw == null ? "" : raw.trim();
    if (line.isEmpty()) return new ParsedInput.Say("");

    if (!line.startsWith("/")) {
      return new ParsedInput.Say(line);
    }

    // Escaped slash: "//text" sends a literal message that starts with "/text".
    if (line.startsWith("//")) {
      return new ParsedInput.Say(line.substring(1));
    }

    ParsedInput backendNamed = backendNamedCommandParser.parse(line);
    if (backendNamed != null) return backendNamed;

    for (SlashCommandParseStrategy strategy : strategies) {
      ParsedInput parsed = strategy.tryParse(line);
      if (parsed != null) {
        return parsed;
      }
    }

    return new ParsedInput.Unknown(line);
  }
}
