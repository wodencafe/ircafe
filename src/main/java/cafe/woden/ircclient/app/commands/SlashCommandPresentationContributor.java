package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Contributes slash-command autocomplete and help presentation metadata. */
@ApplicationLayer
public interface SlashCommandPresentationContributor {

  default List<SlashCommandDescriptor> autocompleteCommands() {
    return List.of();
  }

  default void appendGeneralHelp(TargetRef out) {}

  default Map<String, Consumer<TargetRef>> topicHelpHandlers() {
    return Map.of();
  }
}
