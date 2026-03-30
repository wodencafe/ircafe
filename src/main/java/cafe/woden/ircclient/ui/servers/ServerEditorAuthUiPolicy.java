package cafe.woden.ircclient.ui.servers;

/** Pure UI-state rules for server-editor auth controls. */
final class ServerEditorAuthUiPolicy {
  private ServerEditorAuthUiPolicy() {}

  static MatrixUiState matrixUiState(
      ServerEditorBackendProfile profile, ServerEditorMatrixAuthMode matrixAuthMode) {
    boolean directAuthControlsVisible = profile != null && profile.directAuthEnabled();
    boolean matrixAuthBackend = profile != null && profile.matrixAuthSupported();
    if (!matrixAuthBackend) {
      return new MatrixUiState(directAuthControlsVisible, true, false, false, false, "", "", null);
    }

    boolean usernamePassword = matrixAuthMode == ServerEditorMatrixAuthMode.USERNAME_PASSWORD;
    String hint;
    String passwordLabel;
    String passwordPlaceholder;
    if (usernamePassword) {
      passwordLabel = "Password";
      passwordPlaceholder = "matrix account password";
      hint = "Username/password mode signs in via /login. Username and password are required.";
    } else {
      passwordLabel = "Access token";
      passwordPlaceholder = "matrix access token";
      hint = "Access-token mode uses the token directly for Matrix API requests.";
    }

    return new MatrixUiState(
        directAuthControlsVisible,
        false,
        true,
        usernamePassword,
        usernamePassword,
        passwordLabel,
        passwordPlaceholder,
        hint);
  }

  static SaslUiState saslUiState(ServerEditorAuthMode authMode, String mechanism) {
    boolean enabled = authMode == ServerEditorAuthMode.SASL;
    ServerEditorAuthPolicy.SaslMechanismMetadata metadata =
        ServerEditorAuthPolicy.saslMechanismMetadata(mechanism);

    return new SaslUiState(
        enabled,
        enabled,
        enabled,
        enabled,
        enabled && metadata.secretEnabled(),
        metadata.secretPlaceholder(),
        metadata.hint());
  }

  static NickservUiState nickservUiState(ServerEditorAuthMode authMode) {
    String hint =
        "NickServ identify runs after connect. Use this when the server doesn't offer SASL."
            + " This is an alternative auth path; don't enable it together with SASL.";
    return new NickservUiState(authMode == ServerEditorAuthMode.NICKSERV, hint);
  }

  record MatrixUiState(
      boolean authModeControlsVisible,
      boolean authModeCardVisible,
      boolean matrixAuthControlsVisible,
      boolean matrixAuthUserVisible,
      boolean matrixAuthUserEnabled,
      String serverPasswordLabel,
      String serverPasswordPlaceholder,
      String hint) {}

  record SaslUiState(
      boolean hintVisible,
      boolean mechanismEnabled,
      boolean continueOnFailureEnabled,
      boolean userEnabled,
      boolean secretEnabled,
      String secretPlaceholder,
      String hint) {}

  record NickservUiState(boolean enabled, String hint) {}
}
