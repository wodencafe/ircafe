package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.api.BackendUiMode;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import org.junit.jupiter.api.Test;

class ServerEditorBackendProfilesTest {

  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  @Test
  void builtInProfilesExposeDistinctBackendDefaults() {
    ServerEditorBackendProfiles profiles = ServerEditorBackendProfiles.builtIns();

    ServerEditorBackendProfile irc =
        profiles.profileForBackendId(BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC));
    ServerEditorBackendProfile quassel =
        profiles.profileForBackendId(
            BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.QUASSEL_CORE));
    ServerEditorBackendProfile matrix =
        profiles.profileForBackendId(
            BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.MATRIX));

    assertEquals("irc", irc.backendId());
    assertEquals("IRC", irc.displayName());
    assertEquals(BackendUiMode.IRC, irc.uiMode());
    assertEquals(6667, irc.defaultPort(false));
    assertEquals(6697, irc.defaultPort(true));
    assertTrue(irc.directAuthEnabled());
    assertTrue(irc.requiresNick());

    assertEquals("Quassel Core", quassel.displayName());
    assertEquals(4242, quassel.defaultPort(false));
    assertEquals(4243, quassel.defaultPort(true));
    assertTrue(quassel.supportsQuasselCoreCommands());
    assertFalse(quassel.directAuthEnabled());
    assertEquals("quassel-user", quassel.defaultLoginFallback());

    assertEquals("Matrix", matrix.displayName());
    assertEquals(BackendUiMode.MATRIX, matrix.uiMode());
    assertEquals(80, matrix.defaultPort(false));
    assertEquals(443, matrix.defaultPort(true));
    assertTrue(matrix.matrixAuthSupported());
    assertFalse(matrix.directAuthEnabled());
    assertFalse(matrix.requiresNick());
  }

  @Test
  void customBackendIdsStaySelectableAndUseIrcFallbackDefaults() {
    ServerEditorBackendProfiles profiles = ServerEditorBackendProfiles.builtIns();

    ServerEditorBackendProfile custom = profiles.profileForBackendId("Plugin-Backend");

    assertEquals("plugin-backend", custom.backendId());
    assertEquals("plugin-backend", custom.displayName());
    assertEquals(BackendUiMode.IRC, custom.uiMode());
    assertEquals(6667, custom.defaultPort(false));
    assertEquals(6697, custom.defaultPort(true));
    assertTrue(profiles.selectableBackendIds("Plugin-Backend").contains("plugin-backend"));
  }

  @Test
  void availablePluginBackendIdsAppearInSelectionList() {
    ServerEditorBackendProfiles profiles =
        ServerEditorBackendProfiles.forAvailableBackendIds(java.util.List.of("plugin-backend"));

    assertTrue(
        profiles.selectableBackendIds(profiles.defaultBackendId()).contains("plugin-backend"));
  }

  @Test
  void explicitPluginProfileOverridesFallbackDefaults() {
    ServerEditorBackendProfiles profiles =
        ServerEditorBackendProfiles.forAvailableBackends(
            java.util.List.of("plugin-backend"), java.util.List.of(pluginProfile()));

    ServerEditorBackendProfile plugin = profiles.profileForBackendId("plugin-backend");

    assertEquals("Plugin Backend", plugin.displayName());
    assertEquals(BackendUiMode.IRC, plugin.uiMode());
    assertEquals(7000, plugin.defaultPort(false));
    assertEquals(7443, plugin.defaultPort(true));
    assertFalse(plugin.directAuthEnabled());
    assertFalse(plugin.requiresNick());
  }

  private static BackendEditorProfileSpec pluginProfile() {
    return new BackendEditorProfileSpec(
        "plugin-backend",
        "Plugin Backend",
        7000,
        7443,
        false,
        false,
        false,
        false,
        false,
        "",
        "Endpoint",
        "Credential",
        "Nick",
        "Login",
        "Display name",
        "Use TLS",
        "Plugin backend connection.",
        "Plugin backend credentials are configured here.",
        "plugin secret",
        "plugin.example.net",
        "plugin-user",
        "PluginNick",
        "Plugin User");
  }
}
