package cafe.woden.ircclient.ui.servers;

import java.util.Locale;
import java.util.Objects;

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
    String mechanismUpper = Objects.toString(mechanism, "PLAIN").trim().toUpperCase(Locale.ROOT);

    boolean userEnabled = enabled;
    boolean secretEnabled = enabled;
    String hint;
    String secretPlaceholder;

    switch (mechanismUpper) {
      case "EXTERNAL" -> {
        secretEnabled = false;
        secretPlaceholder = "(ignored)";
        hint =
            "EXTERNAL uses your TLS client certificate. Secret is ignored; username is optional.";
      }
      case "ECDSA-NIST256P-CHALLENGE" -> {
        secretPlaceholder = "base64 PKCS#8 EC private key";
        hint =
            "ECDSA challenge-response. Secret should be a base64 PKCS#8 EC private key. Username is usually required.";
      }
      case "SCRAM-SHA-256" -> {
        secretPlaceholder = "password";
        hint = "SCRAM-SHA-256 (recommended). Secret = password.";
      }
      case "SCRAM-SHA-1" -> {
        secretPlaceholder = "password";
        hint = "SCRAM-SHA-1. Secret = password.";
      }
      case "AUTO" -> {
        secretPlaceholder = "password (leave blank for EXTERNAL)";
        hint =
            "AUTO prefers SCRAM (256/1) or PLAIN when a secret is provided, and falls back to EXTERNAL when secret is blank.";
      }
      default -> {
        secretPlaceholder = "password";
        hint = "PLAIN. Secret = password.";
      }
    }

    return new SaslUiState(
        enabled, enabled, enabled, userEnabled, secretEnabled, secretPlaceholder, hint);
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
