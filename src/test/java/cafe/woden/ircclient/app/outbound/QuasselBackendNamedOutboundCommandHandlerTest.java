package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QuasselBackendNamedOutboundCommandHandlerTest {

  private final QuasselOutboundCommandService quasselOutboundCommandService =
      mock(QuasselOutboundCommandService.class);
  private final QuasselBackendNamedOutboundCommandHandler handler =
      new QuasselBackendNamedOutboundCommandHandler(quasselOutboundCommandService);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void supportsQuasselCommandNames() {
    assertTrue(handler.supports(BackendNamedCommandNames.QUASSEL_SETUP));
    assertTrue(handler.supports("/" + BackendNamedCommandNames.QUASSEL_NETWORK));
    assertTrue(handler.supports(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER));
    assertTrue(handler.supports("QuasselSetup"));
    assertFalse(handler.supports("join"));
  }

  @Test
  void handleQuasselSetupDelegatesToService() {
    handler.handle(disposables, BackendNamedCommandNames.QUASSEL_SETUP, "core");
    verify(quasselOutboundCommandService).handleQuasselSetup(disposables, "core");
  }

  @Test
  void handleQuasselNetworkDelegatesToService() {
    handler.handle(disposables, BackendNamedCommandNames.QUASSEL_NETWORK, "list");
    verify(quasselOutboundCommandService).handleQuasselNetwork(disposables, "list");
  }

  @Test
  void handleQuasselNetworkManagerDelegatesToService() {
    handler.handle(disposables, BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "core");
    verify(quasselOutboundCommandService).handleQuasselNetworkManager(disposables, "core");
  }

  @Test
  void handleIgnoresUnknownCommands() {
    handler.handle(disposables, "unknown", "x");
    verifyNoInteractions(quasselOutboundCommandService);
  }
}
