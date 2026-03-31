package cafe.woden.ircclient.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class BackendEditorProfileCatalogTest {

  @Test
  void builtInsFallbackToIrcProfileForUnknownBackendId() {
    BackendEditorProfileCatalog catalog = BackendEditorProfileCatalog.builtIns();

    assertEquals("plugin-backend", catalog.displayName("plugin-backend"));
    assertEquals(BackendUiMode.IRC, catalog.uiModeForBackendId("plugin-backend"));
    assertFalse(catalog.supportsQuasselCoreCommands("plugin-backend"));
  }

  @Test
  void pluginProfilesOverrideBuiltInMetadataByBackendId() {
    AvailableBackendIdsPort backendMetadata = mock(AvailableBackendIdsPort.class);
    when(backendMetadata.availableBackendEditorProfiles())
        .thenReturn(
            List.of(
                new BackendEditorProfileSpec(
                    "matrix",
                    "Plugin Matrix",
                    BackendUiMode.IRC,
                    9000,
                    9443,
                    false,
                    false,
                    false,
                    false,
                    true,
                    "",
                    "Host",
                    "Password",
                    "Nick",
                    "Login",
                    "Real name",
                    "Use TLS",
                    "Plugin override.",
                    "Plugin override auth.",
                    "",
                    "plugin.example.org",
                    "",
                    "",
                    "")));

    BackendEditorProfileCatalog catalog = BackendEditorProfileCatalog.from(backendMetadata);

    assertEquals("Plugin Matrix", catalog.displayName("matrix"));
    assertEquals(BackendUiMode.IRC, catalog.uiModeForBackendId("matrix"));
    assertTrue(catalog.supportsQuasselCoreCommands("matrix"));
  }

  @Test
  void pluginProfilesAddCustomBackendMetadata() {
    AvailableBackendIdsPort backendMetadata = mock(AvailableBackendIdsPort.class);
    when(backendMetadata.availableBackendEditorProfiles())
        .thenReturn(List.of(pluginProfile("plugin-matrix", BackendUiMode.MATRIX, false)));

    BackendEditorProfileCatalog catalog = BackendEditorProfileCatalog.from(backendMetadata);

    assertEquals("Plugin Matrix", catalog.displayName(" plugin-matrix "));
    assertEquals(BackendUiMode.MATRIX, catalog.uiModeForBackendId(" plugin-matrix "));
    assertFalse(catalog.supportsQuasselCoreCommands(" plugin-matrix "));
  }

  private static BackendEditorProfileSpec pluginProfile(
      String backendId, BackendUiMode uiMode, boolean supportsQuasselCoreCommands) {
    return new BackendEditorProfileSpec(
        backendId,
        "Plugin Matrix",
        uiMode,
        8448,
        8448,
        false,
        false,
        false,
        false,
        supportsQuasselCoreCommands,
        "",
        "Homeserver",
        "Credential",
        "Nick",
        "Login",
        "Display name",
        "Use TLS",
        "Plugin backend.",
        "Plugin auth.",
        "token",
        "https://plugin.example.org",
        "@alice:plugin.example.org",
        "PluginNick",
        "Plugin User");
  }
}
