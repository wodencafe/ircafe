package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerEditorServerBuildPolicyTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void buildAssemblesListsAndProxyOverrideForIrcProfile() {
    IrcProperties.Server server =
        ServerEditorServerBuildPolicy.build(
            new ServerEditorServerBuildPolicy.ServerBuildRequest(
                BACKEND_PROFILES.profileForBackendId("irc"),
                "irc",
                "libera",
                "irc.libera.chat",
                "6697",
                true,
                "",
                ServerEditorMatrixAuthMode.ACCESS_TOKEN,
                "",
                "ircafe",
                "",
                "",
                ServerEditorAuthMode.SASL,
                "ircafe-user",
                "sasl-secret",
                "SCRAM-SHA-256",
                true,
                "NickServ",
                "",
                true,
                "#irc\n#java",
                "alice\nbob",
                "/join #project\n/mode +B",
                true,
                true,
                "proxy.example.org",
                "1080",
                "proxy-user",
                "proxy-secret",
                false,
                "15000",
                "25000"));

    assertEquals("libera", server.id());
    assertEquals("ircafe", server.nick());
    assertEquals("ircafe", server.login());
    assertEquals("ircafe", server.realName());
    assertTrue(server.sasl().enabled());
    assertEquals("SCRAM-SHA-256", server.sasl().mechanism());
    assertFalse(server.nickserv().enabled());
    assertEquals(List.of("#irc", "#java", "query:alice", "query:bob"), server.autoJoin());
    assertEquals(List.of("/join #project", "/mode +B"), server.perform());
    assertTrue(server.proxy().enabled());
    assertEquals("proxy.example.org", server.proxy().host());
    assertEquals(1080, server.proxy().port());
    assertEquals(15_000, server.proxy().connectTimeoutMs());
    assertEquals(25_000, server.proxy().readTimeoutMs());
  }

  @Test
  void buildUsesMatrixPasswordFlowAndClearsServerPassword() {
    IrcProperties.Server server =
        ServerEditorServerBuildPolicy.build(
            new ServerEditorServerBuildPolicy.ServerBuildRequest(
                BACKEND_PROFILES.profileForBackendId("matrix"),
                "matrix",
                "matrix",
                "https://matrix.example.org",
                "443",
                true,
                "matrix-password",
                ServerEditorMatrixAuthMode.USERNAME_PASSWORD,
                "alice",
                "",
                "",
                "",
                ServerEditorAuthMode.DISABLED,
                "",
                "",
                "PLAIN",
                true,
                "NickServ",
                "",
                true,
                "",
                "",
                "",
                false,
                false,
                "",
                "",
                "",
                "",
                true,
                "",
                ""));

    assertEquals("", server.serverPassword());
    assertEquals("alice", server.login());
    assertTrue(server.sasl().enabled());
    assertEquals(ServerEditorAuthPolicy.MATRIX_PASSWORD_AUTH_MECHANISM, server.sasl().mechanism());
    assertEquals("alice", server.sasl().username());
    assertEquals("matrix-password", server.sasl().password());
  }

  @Test
  void buildRejectsServerPasswordWithNewlines() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ServerEditorServerBuildPolicy.build(
                    new ServerEditorServerBuildPolicy.ServerBuildRequest(
                        BACKEND_PROFILES.profileForBackendId("irc"),
                        "irc",
                        "libera",
                        "irc.libera.chat",
                        "6697",
                        true,
                        "bad\npassword",
                        ServerEditorMatrixAuthMode.ACCESS_TOKEN,
                        "",
                        "ircafe",
                        "",
                        "",
                        ServerEditorAuthMode.DISABLED,
                        "",
                        "",
                        "PLAIN",
                        true,
                        "NickServ",
                        "",
                        true,
                        "",
                        "",
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        "",
                        true,
                        "",
                        "")));

    assertEquals("Server/Core password must not contain newlines", error.getMessage());
  }
}
