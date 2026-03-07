package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Routes parsed backend-specific command names to backend command handlers. */
@Component
final class BackendNamedOutboundCommandRouter {

  private final List<BackendNamedOutboundCommandHandler> handlers;
  private final TargetCoordinator targetCoordinator;
  private final UiPort ui;

  BackendNamedOutboundCommandRouter(
      List<BackendNamedOutboundCommandHandler> handlers, TargetCoordinator targetCoordinator, UiPort ui) {
    this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.ui = Objects.requireNonNull(ui, "ui");
  }

  void handle(CompositeDisposable disposables, ParsedInput.BackendNamed command) {
    String name = command.command();
    for (BackendNamedOutboundCommandHandler handler : handlers) {
      if (handler.supports(name)) {
        handler.handle(disposables, name, command.args());
        return;
      }
    }
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef out = active != null ? active : targetCoordinator.safeStatusTarget();
    ui.appendStatus(out, "(system)", "Unknown command: /" + name);
  }
}
