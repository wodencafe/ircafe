package cafe.woden.ircclient.app.outbound;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Handles Quassel named commands routed from backend command parsing. */
@Component
final class QuasselBackendNamedOutboundCommandHandler implements BackendNamedOutboundCommandHandler {

  static final String QUASSEL_SETUP_COMMAND = "quasselsetup";
  static final String QUASSEL_NETWORK_COMMAND = "quasselnet";
  static final String QUASSEL_NETWORK_MANAGER_COMMAND = "quasselnetmanager";

  private final QuasselOutboundCommandService quasselOutboundCommandService;

  QuasselBackendNamedOutboundCommandHandler(QuasselOutboundCommandService quasselOutboundCommandService) {
    this.quasselOutboundCommandService =
        Objects.requireNonNull(quasselOutboundCommandService, "quasselOutboundCommandService");
  }

  @Override
  public boolean supports(String commandName) {
    String name = normalizeCommandName(commandName);
    return QUASSEL_SETUP_COMMAND.equals(name)
        || QUASSEL_NETWORK_COMMAND.equals(name)
        || QUASSEL_NETWORK_MANAGER_COMMAND.equals(name);
  }

  @Override
  public void handle(CompositeDisposable disposables, String commandName, String args) {
    String name = normalizeCommandName(commandName);
    if (QUASSEL_SETUP_COMMAND.equals(name)) {
      quasselOutboundCommandService.handleQuasselSetup(disposables, args);
      return;
    }
    if (QUASSEL_NETWORK_COMMAND.equals(name)) {
      quasselOutboundCommandService.handleQuasselNetwork(disposables, args);
      return;
    }
    if (QUASSEL_NETWORK_MANAGER_COMMAND.equals(name)) {
      quasselOutboundCommandService.handleQuasselNetworkManager(disposables, args);
    }
  }

  private static String normalizeCommandName(String commandName) {
    String name = Objects.toString(commandName, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (name.startsWith("/")) name = name.substring(1).trim();
    return name;
  }
}
