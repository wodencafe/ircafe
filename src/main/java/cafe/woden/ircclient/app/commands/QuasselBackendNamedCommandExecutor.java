package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.app.outbound.backend.QuasselOutboundCommandService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles runtime execution for Quassel backend named commands. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class QuasselBackendNamedCommandExecutor implements BackendNamedCommandExecutor {

  private final QuasselOutboundCommandService quasselOutboundCommandService;

  public QuasselBackendNamedCommandExecutor(
      QuasselOutboundCommandService quasselOutboundCommandService) {
    this.quasselOutboundCommandService =
        Objects.requireNonNull(quasselOutboundCommandService, "quasselOutboundCommandService");
  }

  @Override
  public Set<String> handledCommandNames() {
    return Set.of(
        BackendNamedCommandNames.QUASSEL_SETUP,
        BackendNamedCommandNames.QUASSEL_NETWORK,
        BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER);
  }

  @Override
  public boolean handle(
      BackendNamedCommandExecutionContext context,
      CompositeDisposable disposables,
      ParsedInput.BackendNamed command) {
    if (disposables == null || command == null) {
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
