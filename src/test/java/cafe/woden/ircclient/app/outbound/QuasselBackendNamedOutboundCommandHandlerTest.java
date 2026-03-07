package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Set;
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
    Set<String> commandNames = handler.supportedCommandNames();
    assertTrue(commandNames.contains(BackendNamedCommandNames.QUASSEL_SETUP));
    assertTrue(commandNames.contains(BackendNamedCommandNames.QUASSEL_NETWORK));
    assertTrue(commandNames.contains(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER));
    assertFalse(commandNames.contains("join"));
  }

  @Test
  void handleQuasselSetupDelegatesToService() {
    handler.handle(
        disposables, new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "core"));
    verify(quasselOutboundCommandService).handleQuasselSetup(disposables, "core");
  }

  @Test
  void handleQuasselNetworkDelegatesToService() {
    handler.handle(
        disposables, new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK, "list"));
    verify(quasselOutboundCommandService).handleQuasselNetwork(disposables, "list");
  }

  @Test
  void handleQuasselNetworkManagerDelegatesToService() {
    handler.handle(
        disposables,
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "core"));
    verify(quasselOutboundCommandService).handleQuasselNetworkManager(disposables, "core");
  }

  @Test
  void handleIgnoresUnknownCommands() {
    handler.handle(disposables, new ParsedInput.BackendNamed("unknown", "x"));
    verifyNoInteractions(quasselOutboundCommandService);
  }
}
