package cafe.woden.ircclient.ui.servers;

/** Pure orchestration rules for auth-mode selection and card choice in the server editor. */
final class ServerEditorAuthModePresentationPolicy {
  private ServerEditorAuthModePresentationPolicy() {}

  static AuthModePresentationState presentationState(
      ServerEditorBackendProfile profile, ServerEditorAuthMode requestedAuthMode) {
    ServerEditorAuthMode effectiveAuthMode =
        ServerEditorAuthPolicy.effectiveAuthMode(profile, requestedAuthMode);
    return new AuthModePresentationState(
        profile.directAuthEnabled(), effectiveAuthMode, authCardFor(effectiveAuthMode));
  }

  private static ServerEditorAuthCard authCardFor(ServerEditorAuthMode authMode) {
    return switch (authMode) {
      case SASL -> ServerEditorAuthCard.SASL;
      case NICKSERV -> ServerEditorAuthCard.NICKSERV;
      default -> ServerEditorAuthCard.DISABLED;
    };
  }

  enum ServerEditorAuthCard {
    DISABLED,
    SASL,
    NICKSERV
  }

  record AuthModePresentationState(
      boolean authModeEnabled, ServerEditorAuthMode authMode, ServerEditorAuthCard authCard) {}
}
