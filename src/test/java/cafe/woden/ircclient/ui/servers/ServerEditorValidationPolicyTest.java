package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerEditorValidationPolicyTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void validateAcceptsCompleteIrcConfiguration() {
    ServerEditorValidationPolicy.ValidationState state =
        ServerEditorValidationPolicy.validate(
            new ServerEditorValidationPolicy.ValidationRequest(
                BACKEND_PROFILES.profileForBackendId("irc"),
                "freenode",
                "irc.example.com",
                "6697",
                "alice",
                ServerEditorMatrixAuthMode.ACCESS_TOKEN,
                "",
                "",
                ServerEditorAuthMode.SASL,
                "SCRAM-SHA-256",
                "alice",
                "secret",
                "",
                true,
                true,
                "proxy.example.com",
                "1080",
                "proxy-user",
                "proxy-pass",
                "5000",
                "10000"));

    assertTrue(state.saveEnabled());
    assertFalse(state.connectionValidation().nickBad());
    assertFalse(state.saslValidation().secretBad());
    assertFalse(state.proxyValidation().hostBad());
    assertFalse(state.proxyValidation().portBad());
  }

  @Test
  void validateRejectsMatrixPasswordModeWithoutUsername() {
    ServerEditorValidationPolicy.ValidationState state =
        ServerEditorValidationPolicy.validate(
            new ServerEditorValidationPolicy.ValidationRequest(
                BACKEND_PROFILES.profileForBackendId("matrix"),
                "matrix-home",
                "matrix.example.com",
                "443",
                "",
                ServerEditorMatrixAuthMode.USERNAME_PASSWORD,
                "secret",
                "",
                ServerEditorAuthMode.DISABLED,
                "PLAIN",
                "",
                "",
                "",
                false,
                false,
                "",
                "",
                "",
                "",
                "",
                ""));

    assertFalse(state.saveEnabled());
    assertTrue(state.matrixValidation().applicable());
    assertTrue(state.matrixValidation().usernameBad());
    assertFalse(state.matrixValidation().credentialBad());
    assertFalse(state.connectionValidation().nickBad());
  }

  @Test
  void validateRejectsInvalidProxyOverrideWithoutBlockingWarningsOnly() {
    ServerEditorValidationPolicy.ValidationState state =
        ServerEditorValidationPolicy.validate(
            new ServerEditorValidationPolicy.ValidationRequest(
                BACKEND_PROFILES.profileForBackendId("irc"),
                "libera",
                "irc.libera.chat",
                "6697",
                "alice",
                ServerEditorMatrixAuthMode.ACCESS_TOKEN,
                "",
                "",
                ServerEditorAuthMode.DISABLED,
                "PLAIN",
                "",
                "",
                "",
                true,
                true,
                "",
                "99999",
                "proxy-user",
                "",
                "bad-timeout",
                ""));

    assertFalse(state.saveEnabled());
    assertTrue(state.proxyValidation().applicable());
    assertTrue(state.proxyValidation().proxyDetailsApplicable());
    assertTrue(state.proxyValidation().hostBad());
    assertTrue(state.proxyValidation().portBad());
    assertTrue(state.proxyValidation().connectTimeoutWarning());
    assertTrue(state.proxyValidation().authMismatch());
  }
}
