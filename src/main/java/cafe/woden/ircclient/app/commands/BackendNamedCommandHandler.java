package cafe.woden.ircclient.app.commands;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** ServiceLoader-backed contribution for backend-scoped named slash commands. */
@SecondaryPort
@ApplicationLayer
public interface BackendNamedCommandHandler {

  Set<String> supportedCommandNames();

  ParsedInput parse(String line, String matchedCommandName);

  default Set<String> handledCommandNames() {
    return Set.of();
  }

  default boolean handle(
      BackendNamedCommandExecutionContext context,
      CompositeDisposable disposables,
      ParsedInput.BackendNamed command) {
    return false;
  }

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
