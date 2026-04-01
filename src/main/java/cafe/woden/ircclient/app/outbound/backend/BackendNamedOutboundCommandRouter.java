package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandExecutionContext;
import cafe.woden.ircclient.app.commands.BackendNamedCommandExecutorCatalog;
import cafe.woden.ircclient.app.commands.BackendNamedCommandRegistrationSupport;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Routes parsed backend-specific command names to backend command handlers. */
@Component
@ApplicationLayer
public final class BackendNamedOutboundCommandRouter {

  private final BackendNamedCommandExecutorCatalog commandExecutors;
  private final TargetCoordinator targetCoordinator;
  private final ConnectionCoordinator connectionCoordinator;
  private final IrcMediatorInteractionPort mediatorIrc;
  private final UiPort ui;
  private final BackendNamedCommandExecutionContext pluginExecutionContext =
      new RouterCommandExecutionContext();

  BackendNamedOutboundCommandRouter(
      BackendNamedCommandExecutorCatalog commandExecutors,
      TargetCoordinator targetCoordinator,
      ConnectionCoordinator connectionCoordinator,
      @Qualifier("ircMediatorInteractionPort") IrcMediatorInteractionPort mediatorIrc,
      UiPort ui) {
    this.commandExecutors = Objects.requireNonNull(commandExecutors, "commandExecutors");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.mediatorIrc = Objects.requireNonNull(mediatorIrc, "mediatorIrc");
    this.ui = Objects.requireNonNull(ui, "ui");
  }

  public void handle(CompositeDisposable disposables, ParsedInput.BackendNamed command) {
    String name = BackendNamedCommandRegistrationSupport.normalizeCommandName(command.command());
    if (commandExecutors.handle(pluginExecutionContext, disposables, command)) {
      return;
    }
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef out = active != null ? active : targetCoordinator.safeStatusTarget();
    ui.appendStatus(out, "(system)", "Unknown command: /" + name);
  }

  private final class RouterCommandExecutionContext implements BackendNamedCommandExecutionContext {

    @Override
    public TargetRef activeTarget() {
      return targetCoordinator.getActiveTarget();
    }

    @Override
    public TargetRef safeStatusTarget() {
      return targetCoordinator.safeStatusTarget();
    }

    @Override
    public TargetRef statusTarget(String serverId) {
      String sid = Objects.toString(serverId, "").trim();
      return sid.isEmpty() ? safeStatusTarget() : new TargetRef(sid, "status");
    }

    @Override
    public boolean isConnected(String serverId) {
      return connectionCoordinator.isConnected(serverId);
    }

    @Override
    public void appendStatus(TargetRef target, String prefix, String message) {
      ui.appendStatus(target != null ? target : safeStatusTarget(), prefix, message);
    }

    @Override
    public void appendError(TargetRef target, String prefix, String message) {
      ui.appendError(target != null ? target : safeStatusTarget(), prefix, message);
    }

    @Override
    public void ensureTargetExists(TargetRef target) {
      if (target == null) return;
      ui.ensureTargetExists(target);
    }

    @Override
    public void selectTarget(TargetRef target) {
      if (target == null) return;
      ui.selectTarget(target);
    }

    @Override
    public io.reactivex.rxjava3.core.Completable sendRaw(String serverId, String line) {
      return mediatorIrc.sendRaw(serverId, line);
    }
  }
}
