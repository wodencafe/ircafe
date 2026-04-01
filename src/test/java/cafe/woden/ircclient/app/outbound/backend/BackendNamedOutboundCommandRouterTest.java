package cafe.woden.ircclient.app.outbound.backend;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandExecutionContext;
import cafe.woden.ircclient.app.commands.BackendNamedCommandExecutor;
import cafe.woden.ircclient.app.commands.BackendNamedCommandExecutorCatalog;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BackendNamedOutboundCommandRouterTest {

  private final BackendNamedCommandExecutorCatalog emptyCatalog =
      BackendNamedCommandExecutorCatalog.empty();
  private final TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
  private final ConnectionCoordinator connectionCoordinator =
      Mockito.mock(ConnectionCoordinator.class);
  private final IrcMediatorInteractionPort mediatorIrc =
      Mockito.mock(IrcMediatorInteractionPort.class);
  private final UiPort ui = Mockito.mock(UiPort.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void routesToCatalogHandler() {
    TargetRef active = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    BackendNamedCommandExecutor handler =
        new BackendNamedCommandExecutor() {
          @Override
          public Set<String> handledCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public boolean handle(
              BackendNamedCommandExecutionContext context,
              CompositeDisposable pluginDisposables,
              ParsedInput.BackendNamed command) {
            context.appendStatus(context.activeTargetOrSafeStatusTarget(), "(plugin)", "pong");
            return true;
          }
        };
    BackendNamedOutboundCommandRouter router =
        new BackendNamedOutboundCommandRouter(
            BackendNamedCommandExecutorCatalog.fromExecutors(List.of(handler)),
            targetCoordinator,
            connectionCoordinator,
            mediatorIrc,
            ui);

    router.handle(disposables, new ParsedInput.BackendNamed("backendping", ""));

    verify(ui).appendStatus(active, "(plugin)", "pong");
    verify(ui, never()).appendStatus(active, "(system)", "Unknown command: /backendping");
  }

  @Test
  void unknownCommandUsesActiveTargetForStatus() {
    BackendNamedOutboundCommandRouter router =
        new BackendNamedOutboundCommandRouter(
            emptyCatalog, targetCoordinator, connectionCoordinator, mediatorIrc, ui);
    ParsedInput.BackendNamed command = new ParsedInput.BackendNamed("unknown", "arg");
    TargetRef active = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);

    router.handle(disposables, command);

    verify(ui).appendStatus(active, "(system)", "Unknown command: /unknown");
  }

  @Test
  void unknownCommandFallsBackToSafeStatusTarget() {
    BackendNamedOutboundCommandRouter router =
        new BackendNamedOutboundCommandRouter(
            emptyCatalog, targetCoordinator, connectionCoordinator, mediatorIrc, ui);
    ParsedInput.BackendNamed command = new ParsedInput.BackendNamed("unknown", "arg");
    TargetRef safe = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(safe);

    router.handle(disposables, command);

    verify(ui).appendStatus(safe, "(system)", "Unknown command: /unknown");
  }

  @Test
  void statusTargetContextUsesRequestedServerId() {
    TargetRef safe = new TargetRef("fallback", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(safe);
    BackendNamedCommandExecutor handler =
        new BackendNamedCommandExecutor() {
          @Override
          public Set<String> handledCommandNames() {
            return Set.of("backendstatus");
          }

          @Override
          public boolean handle(
              BackendNamedCommandExecutionContext context,
              CompositeDisposable pluginDisposables,
              ParsedInput.BackendNamed command) {
            context.appendStatus(context.statusTarget("quassel"), "(plugin)", "status");
            return true;
          }
        };
    BackendNamedOutboundCommandRouter router =
        new BackendNamedOutboundCommandRouter(
            BackendNamedCommandExecutorCatalog.fromExecutors(List.of(handler)),
            targetCoordinator,
            connectionCoordinator,
            mediatorIrc,
            ui);

    router.handle(disposables, new ParsedInput.BackendNamed("backendstatus", ""));

    verify(ui).appendStatus(new TargetRef("quassel", "status"), "(plugin)", "status");
    verify(ui, never()).appendStatus(safe, "(system)", "Unknown command: /backendstatus");
  }
}
