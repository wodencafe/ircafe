package cafe.woden.ircclient.ui.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcBackendModePort;
import org.junit.jupiter.api.Test;

class BackendUiProfileProviderTest {

  @Test
  void profileForServerUsesMatrixModeFromBackendPort() {
    IrcBackendModePort backendMode = mock(IrcBackendModePort.class);
    when(backendMode.isMatrixBackendServer("matrix")).thenReturn(true);
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode);

    BackendUiProfile profile = provider.profileForServer(" matrix ");

    assertEquals("matrix", profile.serverId());
    assertTrue(profile.isMatrixServer());
    verify(backendMode).isMatrixBackendServer("matrix");
  }

  @Test
  void profileForServerNormalizesBlankAndSkipsBackendLookup() {
    IrcBackendModePort backendMode = mock(IrcBackendModePort.class);
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode);

    BackendUiProfile profile = provider.profileForServer("   ");

    assertEquals("", profile.serverId());
    assertFalse(profile.isMatrixServer());
    verifyNoInteractions(backendMode);
  }

  @Test
  void backendUiContextDelegatesMatrixLookupToBackendPort() {
    IrcBackendModePort backendMode = mock(IrcBackendModePort.class);
    when(backendMode.isMatrixBackendServer("matrix")).thenReturn(true);
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode);

    BackendUiContext backendUiContext = provider.backendUiContext();

    assertTrue(backendUiContext.isMatrixServer(" matrix "));
    assertFalse(backendUiContext.isMatrixServer("libera"));
    verify(backendMode).isMatrixBackendServer("matrix");
    verify(backendMode).isMatrixBackendServer("libera");
  }
}
