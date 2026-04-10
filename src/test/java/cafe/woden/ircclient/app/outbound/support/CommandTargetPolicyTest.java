package cafe.woden.ircclient.app.outbound.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.api.BackendUiMode;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommandTargetPolicyTest {

  @Test
  void builtInMatrixBackendTreatsRoomIdAsChannelLike() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(server("matrix", IrcProperties.Server.Backend.MATRIX)));

    CommandTargetPolicy policy =
        cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);

    assertTrue(policy.isChannelLikeTargetForServer("matrix", "!room:matrix.example.org"));
  }

  @Test
  void builtInIrcBackendDoesNotTreatRoomIdAsChannelLike() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    when(serverCatalog.find("irc"))
        .thenReturn(Optional.of(server("irc", IrcProperties.Server.Backend.IRC)));

    CommandTargetPolicy policy =
        cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);

    assertFalse(policy.isChannelLikeTargetForServer("irc", "!room:matrix.example.org"));
  }

  @Test
  void pluginMatrixModeBackendTreatsRoomIdAsChannelLike() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    AvailableBackendIdsPort backendMetadata = mock(AvailableBackendIdsPort.class);
    when(serverCatalog.find("plugin")).thenReturn(Optional.of(server("plugin", "plugin-matrix")));
    when(backendMetadata.availableBackendEditorProfiles())
        .thenReturn(List.of(matrixProfile("plugin-matrix")));

    CommandTargetPolicy policy =
        cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(
            serverCatalog, backendMetadata);

    assertTrue(policy.isChannelLikeTargetForServer("plugin", "!room:plugin.example.org"));
  }

  @Test
  void pluginIrcModeBackendDoesNotTreatRoomIdAsChannelLike() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    AvailableBackendIdsPort backendMetadata = mock(AvailableBackendIdsPort.class);
    when(serverCatalog.find("plugin")).thenReturn(Optional.of(server("plugin", "plugin-irc")));
    when(backendMetadata.availableBackendEditorProfiles())
        .thenReturn(List.of(ircProfile("plugin-irc")));

    CommandTargetPolicy policy =
        cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(
            serverCatalog, backendMetadata);

    assertFalse(policy.isChannelLikeTargetForServer("plugin", "!room:plugin.example.org"));
  }

  private static IrcProperties.Server server(String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
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
        backend);
  }

  private static IrcProperties.Server server(String id, String backendId) {
    return new IrcProperties.Server(
        id,
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
        backendId);
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

  private static BackendEditorProfileSpec ircProfile(String backendId) {
    return new BackendEditorProfileSpec(
        backendId,
        "Plugin IRC",
        BackendUiMode.IRC,
        6667,
        6697,
        true,
        false,
        true,
        true,
        false,
        "",
        "Host",
        "Server password",
        "Nick",
        "Login",
        "Real name",
        "Use TLS",
        "Plugin IRC backend.",
        "Plugin IRC auth.",
        "password",
        "irc.example.net",
        "plugin-user",
        "PluginNick",
        "Plugin User");
  }
}
