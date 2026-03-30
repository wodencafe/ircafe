package cafe.woden.ircclient.ui.servers;

/** Pure UI-state rules for backend-profile-driven server-editor labels and placeholders. */
final class ServerEditorBackendPresentationPolicy {
  private ServerEditorBackendPresentationPolicy() {}

  static BackendPresentationState presentationState(
      ServerEditorBackendProfile profile, ServerEditorAuthMode selectedAuthMode) {
    ServerEditorAuthMode requestedMode =
        selectedAuthMode == null ? ServerEditorAuthMode.DISABLED : selectedAuthMode;
    boolean authModeEnabled = profile.directAuthEnabled();
    return new BackendPresentationState(
        profile.hostLabel(),
        profile.serverPasswordLabel(),
        profile.nickLabel(),
        profile.loginLabel(),
        profile.realNameLabel(),
        profile.tlsToggleLabel(),
        profile.connectionHint(),
        profile.authDisabledHint(),
        profile.serverPasswordPlaceholder(),
        profile.hostPlaceholder(),
        profile.loginPlaceholder(),
        profile.nickPlaceholder(),
        profile.realNamePlaceholder(),
        authModeEnabled,
        authModeEnabled ? requestedMode : ServerEditorAuthMode.DISABLED);
  }

  record BackendPresentationState(
      String hostLabel,
      String serverPasswordLabel,
      String nickLabel,
      String loginLabel,
      String realNameLabel,
      String tlsToggleLabel,
      String connectionHint,
      String authDisabledHint,
      String serverPasswordPlaceholder,
      String hostPlaceholder,
      String loginPlaceholder,
      String nickPlaceholder,
      String realNamePlaceholder,
      boolean authModeEnabled,
      ServerEditorAuthMode authMode) {}
}
