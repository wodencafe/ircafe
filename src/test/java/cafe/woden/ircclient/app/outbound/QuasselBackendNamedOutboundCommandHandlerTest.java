package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    assertTrue(handler.supports("quasselsetup"));
    assertTrue(handler.supports("/quasselnet"));
    assertTrue(handler.supports("quasselnetmanager"));
    assertTrue(handler.supports("QuasselSetup"));
    assertFalse(handler.supports("join"));
  }

  @Test
  void handleQuasselSetupDelegatesToService() {
    handler.handle(disposables, "quasselsetup", "core");
    verify(quasselOutboundCommandService).handleQuasselSetup(disposables, "core");
  }

  @Test
  void handleQuasselNetworkDelegatesToService() {
    handler.handle(disposables, "quasselnet", "list");
    verify(quasselOutboundCommandService).handleQuasselNetwork(disposables, "list");
  }

  @Test
  void handleQuasselNetworkManagerDelegatesToService() {
    handler.handle(disposables, "quasselnetmanager", "core");
    verify(quasselOutboundCommandService).handleQuasselNetworkManager(disposables, "core");
  }

  @Test
  void handleIgnoresUnknownCommands() {
    handler.handle(disposables, "unknown", "x");
    verifyNoInteractions(quasselOutboundCommandService);
  }
}
