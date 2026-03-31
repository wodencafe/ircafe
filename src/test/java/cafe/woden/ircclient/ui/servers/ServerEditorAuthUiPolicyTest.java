package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerEditorAuthUiPolicyTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void matrixUiStateForIrcBackendKeepsTraditionalAuthControls() {
    ServerEditorAuthUiPolicy.MatrixUiState state =
        ServerEditorAuthUiPolicy.matrixUiState(
            BACKEND_PROFILES.profileForBackendId("irc"), ServerEditorMatrixAuthMode.ACCESS_TOKEN);

    assertTrue(state.authModeControlsVisible());
    assertTrue(state.authModeCardVisible());
    assertFalse(state.matrixAuthControlsVisible());
    assertFalse(state.matrixAuthUserVisible());
    assertNull(state.hint());
  }

  @Test
  void matrixUiStateForPasswordModeShowsUsernameFieldAndPasswordLabel() {
    ServerEditorAuthUiPolicy.MatrixUiState state =
        ServerEditorAuthUiPolicy.matrixUiState(
            BACKEND_PROFILES.profileForBackendId("matrix"),
            ServerEditorMatrixAuthMode.USERNAME_PASSWORD);

    assertFalse(state.authModeCardVisible());
    assertTrue(state.matrixAuthControlsVisible());
    assertTrue(state.matrixAuthUserVisible());
    assertEquals("Password", state.serverPasswordLabel());
    assertEquals("matrix account password", state.serverPasswordPlaceholder());
  }

  @Test
  void saslUiStateForExternalDisablesSecretField() {
    ServerEditorAuthUiPolicy.SaslUiState state =
        ServerEditorAuthUiPolicy.saslUiState(ServerEditorAuthMode.SASL, "EXTERNAL");

    assertTrue(state.hintVisible());
    assertTrue(state.userEnabled());
    assertFalse(state.secretEnabled());
    assertEquals("(ignored)", state.secretPlaceholder());
  }

  @Test
  void saslUiStateForDisabledAuthKeepsControlsDisabled() {
    ServerEditorAuthUiPolicy.SaslUiState state =
        ServerEditorAuthUiPolicy.saslUiState(ServerEditorAuthMode.DISABLED, "SCRAM-SHA-256");

    assertFalse(state.hintVisible());
    assertFalse(state.mechanismEnabled());
    assertFalse(state.continueOnFailureEnabled());
    assertFalse(state.userEnabled());
    assertFalse(state.secretEnabled());
    assertEquals("password", state.secretPlaceholder());
  }

  @Test
  void nickservUiStateIncludesHintAndEnabledFlag() {
    ServerEditorAuthUiPolicy.NickservUiState enabled =
        ServerEditorAuthUiPolicy.nickservUiState(ServerEditorAuthMode.NICKSERV);
    ServerEditorAuthUiPolicy.NickservUiState disabled =
        ServerEditorAuthUiPolicy.nickservUiState(ServerEditorAuthMode.DISABLED);

    assertTrue(enabled.enabled());
    assertFalse(disabled.enabled());
    assertTrue(enabled.hint().contains("NickServ identify"));
  }
}
