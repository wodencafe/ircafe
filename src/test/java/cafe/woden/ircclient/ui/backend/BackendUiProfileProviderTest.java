package cafe.woden.ircclient.ui.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.api.BackendUiMode;
import cafe.woden.ircclient.irc.backend.IrcBackendModePort;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackendUiProfileProviderTest {

  @Test
  void profileForServerUsesMatrixModeFromBackendMetadata() {
    IrcBackendModePort backendMode = mock(IrcBackendModePort.class);
    AvailableBackendIdsPort backendMetadata = mock(AvailableBackendIdsPort.class);
    when(backendMode.backendIdForServer("matrix")).thenReturn("plugin-matrix");
    when(backendMetadata.availableBackendEditorProfiles())
        .thenReturn(List.of(matrixProfile("plugin-matrix")));
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode, backendMetadata);

    BackendUiProfile profile = provider.profileForServer(" matrix ");

    assertEquals("matrix", profile.serverId());
    assertTrue(profile.isMatrixServer());
    verify(backendMode).backendIdForServer("matrix");
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
    AvailableBackendIdsPort backendMetadata = mock(AvailableBackendIdsPort.class);
    when(backendMode.backendIdForServer("matrix")).thenReturn("plugin-matrix");
    when(backendMetadata.availableBackendEditorProfiles())
        .thenReturn(List.of(matrixProfile("plugin-matrix")));
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode, backendMetadata);

    BackendUiContext backendUiContext = provider.backendUiContext();

    assertTrue(backendUiContext.isMatrixServer(" matrix "));
    assertFalse(backendUiContext.isMatrixServer("libera"));
    verify(backendMode).backendIdForServer("matrix");
    verify(backendMode).backendIdForServer("libera");
  }

  @Test
  void supportsQuasselCommandsUsesBackendProfileMetadata() {
    IrcBackendModePort backendMode = mock(IrcBackendModePort.class);
    when(backendMode.backendIdForServer("quassel")).thenReturn("quassel-core");
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode);

    assertTrue(provider.supportsQuasselCoreCommands(" quassel "));
    assertFalse(provider.supportsQuasselCoreCommands("libera"));
    verify(backendMode).backendIdForServer("quassel");
    verify(backendMode).backendIdForServer("libera");
  }

  @Test
  void backendDisplayNameForServerUsesBackendMetadataPort() {
    IrcBackendModePort backendMode = mock(IrcBackendModePort.class);
    AvailableBackendIdsPort backendMetadata = mock(AvailableBackendIdsPort.class);
    when(backendMode.backendIdForServer("plugin")).thenReturn("plugin-backend");
    when(backendMetadata.backendDisplayName("plugin-backend")).thenReturn("Fancy Plugin");
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode, backendMetadata);

    assertEquals("Fancy Plugin", provider.backendDisplayNameForServer(" plugin "));
    verify(backendMode).backendIdForServer("plugin");
    verify(backendMetadata).backendDisplayName("plugin-backend");
  }

  @Test
  void backendDisplayNameForServerFallsBackToBuiltInMetadata() {
    IrcBackendModePort backendMode = mock(IrcBackendModePort.class);
    when(backendMode.backendIdForServer("quassel")).thenReturn("quassel-core");
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode);

    assertEquals("Quassel Core", provider.backendDisplayNameForServer(" quassel "));
    verify(backendMode).backendIdForServer("quassel");
  }

  @Test
  void backendIdForServerNormalizesBackendIdFromPort() {
    IrcBackendModePort backendMode = mock(IrcBackendModePort.class);
    when(backendMode.backendIdForServer("plugin")).thenReturn(" Plugin-Backend ");
    BackendUiProfileProvider provider = new BackendUiProfileProvider(backendMode);

    assertEquals("plugin-backend", provider.backendIdForServer(" plugin "));
    verify(backendMode).backendIdForServer("plugin");
  }

  private static BackendEditorProfileSpec matrixProfile(String backendId) {
    return new BackendEditorProfileSpec(
        backendId,
        "Plugin Matrix",
        BackendUiMode.MATRIX,
        8448,
        8448,
        false,
        false,
        false,
        false,
        false,
        "",
        "Homeserver",
        "Credential",
        "Nick",
        "Login",
        "Display name",
        "Use TLS",
        "Plugin matrix backend.",
        "Plugin matrix auth.",
        "token",
        "https://plugin.example.org",
        "@alice:plugin.example.org",
        "PluginNick",
        "Plugin User");
  }
}
