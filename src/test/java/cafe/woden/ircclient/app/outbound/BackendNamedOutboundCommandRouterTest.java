package cafe.woden.ircclient.app.outbound;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BackendNamedOutboundCommandRouterTest {

  private final BackendNamedOutboundCommandHandler first = mock(BackendNamedOutboundCommandHandler.class);
  private final BackendNamedOutboundCommandHandler second = mock(BackendNamedOutboundCommandHandler.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final UiPort ui = mock(UiPort.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void routesToFirstSupportingHandler() {
    BackendNamedOutboundCommandRouter router =
        new BackendNamedOutboundCommandRouter(List.of(first, second), targetCoordinator, ui);
    ParsedInput.BackendNamed command =
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "core");
    when(first.supports(BackendNamedCommandNames.QUASSEL_SETUP)).thenReturn(false);
    when(second.supports(BackendNamedCommandNames.QUASSEL_SETUP)).thenReturn(true);

    router.handle(disposables, command);

    verify(second).handle(disposables, BackendNamedCommandNames.QUASSEL_SETUP, "core");
  }

  @Test
  void unknownCommandUsesActiveTargetForStatus() {
    BackendNamedOutboundCommandRouter router =
        new BackendNamedOutboundCommandRouter(List.of(), targetCoordinator, ui);
    ParsedInput.BackendNamed command = new ParsedInput.BackendNamed("unknown", "arg");
    TargetRef active = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);

    router.handle(disposables, command);

    verify(ui).appendStatus(active, "(system)", "Unknown command: /unknown");
  }

  @Test
  void unknownCommandFallsBackToSafeStatusTarget() {
    BackendNamedOutboundCommandRouter router =
        new BackendNamedOutboundCommandRouter(List.of(), targetCoordinator, ui);
    ParsedInput.BackendNamed command = new ParsedInput.BackendNamed("unknown", "arg");
    TargetRef safe = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(safe);

    router.handle(disposables, command);

    verify(ui).appendStatus(safe, "(system)", "Unknown command: /unknown");
  }
}
