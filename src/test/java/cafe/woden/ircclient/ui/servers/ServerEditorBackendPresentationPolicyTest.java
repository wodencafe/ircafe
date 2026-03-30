package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerEditorBackendPresentationPolicyTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void directAuthBackendKeepsSelectedAuthModeAndProfileLabels() {
    ServerEditorBackendPresentationPolicy.BackendPresentationState state =
        ServerEditorBackendPresentationPolicy.presentationState(
            BACKEND_PROFILES.profileForBackendId("irc"), ServerEditorAuthMode.SASL);

    assertEquals("Host", state.hostLabel());
    assertEquals("Server password", state.serverPasswordLabel());
    assertEquals("Use TLS (SSL)", state.tlsToggleLabel());
    assertEquals("(optional)", state.serverPasswordPlaceholder());
    assertTrue(state.authModeEnabled());
    assertEquals(ServerEditorAuthMode.SASL, state.authMode());
  }

  @Test
  void backendWithoutDirectAuthForcesDisabledAuthMode() {
    ServerEditorBackendPresentationPolicy.BackendPresentationState state =
        ServerEditorBackendPresentationPolicy.presentationState(
            BACKEND_PROFILES.profileForBackendId("quassel-core"), ServerEditorAuthMode.NICKSERV);

    assertFalse(state.authModeEnabled());
    assertEquals(ServerEditorAuthMode.DISABLED, state.authMode());
    assertTrue(state.connectionHint().contains("Quassel"));
  }
}
