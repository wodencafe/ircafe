package cafe.woden.ircclient.app.commands;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** ServiceLoader-backed metadata and parser contribution for backend-scoped named commands. */
@SecondaryPort
@ApplicationLayer
public interface BackendNamedCommandHandler {

  Set<String> supportedCommandNames();

  ParsedInput parse(String line, String matchedCommandName);

  default List<SlashCommandDescriptor> autocompleteCommands() {
    return List.of();
  }

  default List<String> generalHelpLines() {
    return List.of();
  }

  default Map<String, List<String>> topicHelpLines() {
    return Map.of();
  }
}
