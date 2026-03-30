package cafe.woden.ircclient.ui.servers;

/** Coordinates auth-related server-editor UI refresh using the focused policy/applier helpers. */
final class ServerEditorAuthPanelUiApplier {
  private ServerEditorAuthPanelUiApplier() {}

  static void apply(RefreshRequest request, AuthPanelWidgets widgets) {
    ServerEditorAuthModePresentationPolicy.AuthModePresentationState authModeState =
        ServerEditorAuthModePresentationPolicy.presentationState(
            request.profile(), request.requestedAuthMode());
    ServerEditorAuthModeUiApplier.apply(authModeState, widgets.authModeWidgets());

    ServerEditorAuthUiApplier.applyMatrixUi(
        ServerEditorAuthUiPolicy.matrixUiState(request.profile(), request.matrixAuthMode()),
        widgets.matrixAuthWidgets());

    ServerEditorAuthMode effectiveAuthMode = authModeState.authMode();
    ServerEditorAuthUiApplier.applySaslUi(
        ServerEditorAuthUiPolicy.saslUiState(effectiveAuthMode, request.saslMechanism()),
        widgets.saslWidgets());
    ServerEditorAuthUiApplier.applyNickservUi(
        ServerEditorAuthUiPolicy.nickservUiState(effectiveAuthMode), widgets.nickservWidgets());
  }

  record RefreshRequest(
      ServerEditorBackendProfile profile,
      ServerEditorAuthMode requestedAuthMode,
      ServerEditorMatrixAuthMode matrixAuthMode,
      String saslMechanism) {}

  record AuthPanelWidgets(
      ServerEditorAuthModeUiApplier.AuthModeWidgets authModeWidgets,
      ServerEditorAuthUiApplier.MatrixAuthWidgets matrixAuthWidgets,
      ServerEditorAuthUiApplier.SaslWidgets saslWidgets,
      ServerEditorAuthUiApplier.NickservWidgets nickservWidgets) {}
}
