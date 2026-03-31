package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServersDialogTest {

  @Test
  void serverListLabelUsesBackendDisplayNameFromProfiles() {
    ServerEditorBackendProfiles backendProfiles =
        ServerEditorBackendProfiles.forAvailableBackends(
            List.of("plugin-backend"), List.of(pluginProfile("plugin-backend", "Fancy Plugin")));

    String label =
        ServersDialog.serverListLabel(
            backendProfiles,
            new IrcProperties.Server(
                "plugin",
                "irc.example.net",
                6697,
                true,
                "",
                "tester",
                "tester",
                "Tester",
                null,
                null,
                List.of(),
                List.of(),
                null,
                "plugin-backend"));

    assertTrue(label.contains("Backend:</span> Fancy Plugin"));
    assertTrue(label.contains("· Nick:</span> tester"));
  }

  private static BackendEditorProfileSpec pluginProfile(String backendId, String displayName) {
    return new BackendEditorProfileSpec(
        backendId,
        displayName,
        6667,
        6697,
        true,
        false,
        true,
        true,
        false,
        "",
        "Host",
        "Password",
        "Nick",
        "Login",
        "Real name",
        "Use TLS",
        "",
        "",
        "",
        "",
        "",
        "",
        "");
  }
}
