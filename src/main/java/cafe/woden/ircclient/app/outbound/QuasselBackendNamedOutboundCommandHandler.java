package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Handles Quassel named commands routed from backend command parsing. */
@Component
final class QuasselBackendNamedOutboundCommandHandler
    implements BackendNamedOutboundCommandHandler {

  private final QuasselOutboundCommandService quasselOutboundCommandService;

  QuasselBackendNamedOutboundCommandHandler(
      QuasselOutboundCommandService quasselOutboundCommandService) {
    this.quasselOutboundCommandService =
        Objects.requireNonNull(quasselOutboundCommandService, "quasselOutboundCommandService");
  }

  @Override
  public Set<String> supportedCommandNames() {
    return Set.of(
        BackendNamedCommandNames.QUASSEL_SETUP,
        BackendNamedCommandNames.QUASSEL_NETWORK,
        BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER);
  }

  @Override
  public void handle(CompositeDisposable disposables, ParsedInput.BackendNamed command) {
    if (command == null) return;
    switch (command.command()) {
      case BackendNamedCommandNames.QUASSEL_SETUP ->
          quasselOutboundCommandService.handleQuasselSetup(disposables, command.args());
      case BackendNamedCommandNames.QUASSEL_NETWORK ->
          quasselOutboundCommandService.handleQuasselNetwork(disposables, command.args());
      case BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER ->
          quasselOutboundCommandService.handleQuasselNetworkManager(disposables, command.args());
      default -> {
        // No-op: this handler only serves Quassel commands declared in supportedCommandNames().
      }
    }
  }
}
