package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.app.outbound.backend.QuasselOutboundCommandService;
import com.google.auto.service.AutoService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Handles Quassel backend command parsing, help, autocomplete, and execution wiring. */
@Component
@SecondaryAdapter
@ApplicationLayer
@AutoService(BackendNamedCommandHandler.class)
public final class QuasselBackendNamedCommandHandler implements BackendNamedCommandHandler {

  private final QuasselOutboundCommandService quasselOutboundCommandService;

  public QuasselBackendNamedCommandHandler() {
    this(null);
  }

  @Autowired
  public QuasselBackendNamedCommandHandler(
      QuasselOutboundCommandService quasselOutboundCommandService) {
    this.quasselOutboundCommandService = quasselOutboundCommandService;
  }

  @Override
  public Set<String> supportedCommandNames() {
    return Set.of("quasselsetup", "qsetup", "quasselnet", "qnet");
  }

  @Override
  public Set<String> handledCommandNames() {
    return Set.of(
        BackendNamedCommandNames.QUASSEL_SETUP,
        BackendNamedCommandNames.QUASSEL_NETWORK,
        BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER);
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

  @Override
  public boolean handle(
      BackendNamedCommandExecutionContext context,
      CompositeDisposable disposables,
      ParsedInput.BackendNamed command) {
    if (quasselOutboundCommandService == null || disposables == null || command == null) {
      return false;
    }
    return switch (Objects.toString(command.command(), "")) {
      case BackendNamedCommandNames.QUASSEL_SETUP -> {
        quasselOutboundCommandService.handleQuasselSetup(disposables, command.args());
        yield true;
      }
      case BackendNamedCommandNames.QUASSEL_NETWORK -> {
        quasselOutboundCommandService.handleQuasselNetwork(disposables, command.args());
        yield true;
      }
      case BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER -> {
        quasselOutboundCommandService.handleQuasselNetworkManager(disposables, command.args());
        yield true;
      }
      default -> false;
    };
  }
}
