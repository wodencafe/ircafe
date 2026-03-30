package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerEditorBackendPresentationPolicyTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void directAuthBackendKeepsSelectedAuthModeAndProfileLabels() {
    ServerEditorBackendPresentationPolicy.BackendPresentationState state =
        ServerEditorBackendPresentationPolicy.presentationState(
            BACKEND_PROFILES.profileForBackendId("irc"));

    assertEquals("Host", state.hostLabel());
    assertEquals("Server password", state.serverPasswordLabel());
    assertEquals("Use TLS (SSL)", state.tlsToggleLabel());
    assertEquals("(optional)", state.serverPasswordPlaceholder());
  }

  @Test
  void backendWithoutDirectAuthStillExposesBackendPresentationFields() {
    ServerEditorBackendPresentationPolicy.BackendPresentationState state =
        ServerEditorBackendPresentationPolicy.presentationState(
            BACKEND_PROFILES.profileForBackendId("quassel-core"));

    assertTrue(state.connectionHint().contains("Quassel"));
    assertEquals("Core password", state.serverPasswordLabel());
  }
}
