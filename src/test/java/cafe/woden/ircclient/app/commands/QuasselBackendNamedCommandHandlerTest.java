package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.app.outbound.backend.QuasselOutboundCommandService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QuasselBackendNamedCommandHandlerTest {

  private final QuasselOutboundCommandService quasselOutboundCommandService =
      mock(QuasselOutboundCommandService.class);
  private final QuasselBackendNamedCommandHandler handler =
      new QuasselBackendNamedCommandHandler(quasselOutboundCommandService);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void exposesHandledCommandNames() {
    Set<String> commandNames = handler.handledCommandNames();
    assertTrue(commandNames.contains(BackendNamedCommandNames.QUASSEL_SETUP));
    assertTrue(commandNames.contains(BackendNamedCommandNames.QUASSEL_NETWORK));
    assertTrue(commandNames.contains(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER));
    assertFalse(commandNames.contains("join"));
  }

  @Test
  void handleQuasselSetupDelegatesToService() {
    boolean handled =
        handler.handle(
            null,
            disposables,
            new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "core"));

    assertTrue(handled);
    verify(quasselOutboundCommandService).handleQuasselSetup(disposables, "core");
  }

  @Test
  void handleQuasselNetworkDelegatesToService() {
    boolean handled =
        handler.handle(
            null,
            disposables,
            new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK, "list"));

    assertTrue(handled);
    verify(quasselOutboundCommandService).handleQuasselNetwork(disposables, "list");
  }

  @Test
  void handleQuasselNetworkManagerDelegatesToService() {
    boolean handled =
        handler.handle(
            null,
            disposables,
            new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "core"));

    assertTrue(handled);
    verify(quasselOutboundCommandService).handleQuasselNetworkManager(disposables, "core");
  }

  @Test
  void handleIgnoresUnknownCommands() {
    boolean handled =
        handler.handle(null, disposables, new ParsedInput.BackendNamed("unknown", "x"));

    assertFalse(handled);
    verifyNoInteractions(quasselOutboundCommandService);
  }
}
