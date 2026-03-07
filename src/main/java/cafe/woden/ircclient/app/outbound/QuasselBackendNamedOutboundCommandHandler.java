package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Handles Quassel named commands routed from backend command parsing. */
@Component
final class QuasselBackendNamedOutboundCommandHandler implements BackendNamedOutboundCommandHandler {

  private final QuasselOutboundCommandService quasselOutboundCommandService;

  QuasselBackendNamedOutboundCommandHandler(QuasselOutboundCommandService quasselOutboundCommandService) {
    this.quasselOutboundCommandService =
        Objects.requireNonNull(quasselOutboundCommandService, "quasselOutboundCommandService");
  }

  @Override
  public boolean supports(String commandName) {
    String name = normalizeCommandName(commandName);
    return BackendNamedCommandNames.QUASSEL_SETUP.equals(name)
        || BackendNamedCommandNames.QUASSEL_NETWORK.equals(name)
        || BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER.equals(name);
  }

  @Override
  public void handle(CompositeDisposable disposables, String commandName, String args) {
    String name = normalizeCommandName(commandName);
    if (BackendNamedCommandNames.QUASSEL_SETUP.equals(name)) {
      quasselOutboundCommandService.handleQuasselSetup(disposables, args);
      return;
    }
    if (BackendNamedCommandNames.QUASSEL_NETWORK.equals(name)) {
      quasselOutboundCommandService.handleQuasselNetwork(disposables, args);
      return;
    }
    if (BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER.equals(name)) {
      quasselOutboundCommandService.handleQuasselNetworkManager(disposables, args);
    }
  }

  private static String normalizeCommandName(String commandName) {
    String name = Objects.toString(commandName, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (name.startsWith("/")) name = name.substring(1).trim();
    return name;
  }
}
