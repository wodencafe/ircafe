package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerEditorAuthModePresentationPolicyTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void directAuthBackendKeepsRequestedModeAndSelectsMatchingCard() {
    ServerEditorAuthModePresentationPolicy.AuthModePresentationState state =
        ServerEditorAuthModePresentationPolicy.presentationState(
            BACKEND_PROFILES.profileForBackendId("irc"), ServerEditorAuthMode.SASL);

    assertTrue(state.authModeEnabled());
    assertEquals(ServerEditorAuthMode.SASL, state.authMode());
    assertEquals(
        ServerEditorAuthModePresentationPolicy.ServerEditorAuthCard.SASL, state.authCard());
  }

  @Test
  void nonDirectAuthBackendForcesDisabledModeAndCard() {
    ServerEditorAuthModePresentationPolicy.AuthModePresentationState state =
        ServerEditorAuthModePresentationPolicy.presentationState(
            BACKEND_PROFILES.profileForBackendId("matrix"), ServerEditorAuthMode.NICKSERV);

    assertFalse(state.authModeEnabled());
    assertEquals(ServerEditorAuthMode.DISABLED, state.authMode());
    assertEquals(
        ServerEditorAuthModePresentationPolicy.ServerEditorAuthCard.DISABLED, state.authCard());
  }
}
