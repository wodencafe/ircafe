package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerEditorAuthPolicyTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void effectiveAuthModeDisablesTraditionalAuthForMatrixAndQuasselProfiles() {
    assertEquals(
        ServerEditorAuthMode.DISABLED,
        ServerEditorAuthPolicy.effectiveAuthMode(
            BACKEND_PROFILES.profileForBackendId("matrix"), ServerEditorAuthMode.SASL));
    assertEquals(
        ServerEditorAuthMode.DISABLED,
        ServerEditorAuthPolicy.effectiveAuthMode(
            BACKEND_PROFILES.profileForBackendId("quassel-core"), ServerEditorAuthMode.NICKSERV));
  }

  @Test
  void seedMatrixAuthModePrefersPasswordWhenSeedUsesMatrixPasswordMechanism() {
    IrcProperties.Server seed =
        new IrcProperties.Server(
            "matrix",
            "https://matrix.example.org",
            443,
            true,
            "",
            "",
            "",
            "",
            new IrcProperties.Server.Sasl(
                true,
                "alice",
                "secret",
                ServerEditorAuthPolicy.MATRIX_PASSWORD_AUTH_MECHANISM,
                true),
            null,
            List.of(),
            List.of(),
            null,
            "matrix");

    assertEquals(
        ServerEditorMatrixAuthMode.USERNAME_PASSWORD,
        ServerEditorAuthPolicy.seedMatrixAuthMode(
            BACKEND_PROFILES.profileForBackendId("matrix"), seed));
  }

  @Test
  void resolveLoginPrefersMatrixUsernameThenNickThenFallback() {
    ServerEditorBackendProfile matrix = BACKEND_PROFILES.profileForBackendId("matrix");
    ServerEditorBackendProfile irc = BACKEND_PROFILES.profileForBackendId("irc");

    assertEquals(
        "alice",
        ServerEditorAuthPolicy.resolveLogin(
            matrix, "", "nick", "alice", ServerEditorMatrixAuthMode.USERNAME_PASSWORD));
    assertEquals(
        "nick",
        ServerEditorAuthPolicy.resolveLogin(
            irc, "", "nick", "", ServerEditorMatrixAuthMode.ACCESS_TOKEN));
    assertEquals(
        "quassel-user",
        ServerEditorAuthPolicy.resolveLogin(
            BACKEND_PROFILES.profileForBackendId("quassel-core"),
            "",
            "",
            "",
            ServerEditorMatrixAuthMode.ACCESS_TOKEN));
  }

  @Test
  void buildSaslUsesMatrixPasswordMechanismAndClearsServerPassword() {
    ServerEditorAuthPolicy.SaslBuildResult sasl =
        ServerEditorAuthPolicy.buildSasl(
            BACKEND_PROFILES.profileForBackendId("matrix"),
            ServerEditorAuthMode.DISABLED,
            ServerEditorMatrixAuthMode.USERNAME_PASSWORD,
            "matrix-secret",
            "alice",
            "",
            "",
            "",
            true);

    assertEquals("", sasl.serverPassword());
    assertEquals(ServerEditorAuthMode.DISABLED, sasl.authMode());
    assertEquals(ServerEditorAuthPolicy.MATRIX_PASSWORD_AUTH_MECHANISM, sasl.sasl().mechanism());
    assertEquals("alice", sasl.sasl().username());
  }

  @Test
  void buildNickservRequiresPasswordWhenEnabled() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ServerEditorAuthPolicy.buildNickserv(
                    ServerEditorAuthMode.NICKSERV, "NickServ", "", true));

    assertEquals("NickServ password is required when NickServ is enabled", error.getMessage());
    assertFalse(
        ServerEditorAuthPolicy.buildNickserv(ServerEditorAuthMode.DISABLED, "NickServ", "", true)
            .enabled());
  }
}
