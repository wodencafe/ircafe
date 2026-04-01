package cafe.woden.ircclient.app.commands;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles Quassel backend command parsing and startup-safe presentation metadata. */
@Component
@SecondaryAdapter
@ApplicationLayer
@AutoService(BackendNamedCommandHandler.class)
public final class QuasselBackendNamedCommandHandler implements BackendNamedCommandHandler {

  @Override
  public Set<String> supportedCommandNames() {
    return Set.of("quasselsetup", "qsetup", "quasselnet", "qnet");
  }

  @Override
  public ParsedInput parse(String line, String matchedCommandName) {
    String commandToken = "/" + matchedCommandName;
    return switch (matchedCommandName) {
      case "quasselsetup", "qsetup" ->
          new ParsedInput.BackendNamed(
              BackendNamedCommandNames.QUASSEL_SETUP,
              BackendNamedCommandParser.argAfter(line, commandToken));
      case "quasselnet", "qnet" ->
          new ParsedInput.BackendNamed(
              BackendNamedCommandNames.QUASSEL_NETWORK,
              BackendNamedCommandParser.argAfter(line, commandToken));
      default -> null;
    };
  }

  @Override
  public List<SlashCommandDescriptor> autocompleteCommands() {
    return List.of(
        new SlashCommandDescriptor("/quasselsetup", "Complete pending Quassel Core setup"),
        new SlashCommandDescriptor("/qsetup", "Alias: /quasselsetup"),
        new SlashCommandDescriptor("/quasselnet", "Manage Quassel networks"),
        new SlashCommandDescriptor("/qnet", "Alias: /quasselnet"));
  }

  @Override
  public List<String> generalHelpLines() {
    return List.of(
        "/quasselsetup [serverId] (complete pending Quassel Core setup)",
        "/quasselnet [serverId] list|connect|disconnect|remove|add|edit ... (manage Quassel networks)");
  }
}
