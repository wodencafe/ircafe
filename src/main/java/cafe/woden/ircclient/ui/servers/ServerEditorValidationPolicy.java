package cafe.woden.ircclient.ui.servers;

import java.util.Objects;

/** Aggregates server-editor validation state from the focused policy helpers. */
final class ServerEditorValidationPolicy {
  private ServerEditorValidationPolicy() {}

  static ValidationState validate(ValidationRequest request) {
    ServerEditorConnectionPolicy.ConnectionValidation connectionValidation =
        ServerEditorConnectionPolicy.validation(
            request.profile(), request.id(), request.host(), request.portText(), request.nick());

    ServerEditorAuthPolicy.MatrixValidation matrixValidation =
        ServerEditorAuthPolicy.matrixValidation(
            request.profile(),
            request.matrixAuthMode(),
            request.serverPassword(),
            request.matrixAuthUser());

    ServerEditorAuthMode authMode =
        ServerEditorAuthPolicy.effectiveAuthMode(request.profile(), request.selectedAuthMode());
    ServerEditorAuthPolicy.SaslValidation saslValidation =
        ServerEditorAuthPolicy.saslValidation(
            authMode,
            request.saslMechanism(),
            request.saslUser(),
            Objects.toString(request.saslSecret(), ""));

    ServerEditorAuthPolicy.NickservValidation nickservValidation =
        ServerEditorAuthPolicy.nickservValidation(authMode, request.nickservPassword());

    ServerEditorProxyValidationPolicy.ProxyValidation proxyValidation =
        ServerEditorProxyValidationPolicy.validate(
            request.proxyOverrideSelected(),
            request.proxyEnabled(),
            request.proxyHost(),
            request.proxyPort(),
            request.proxyUser(),
            request.proxyPassword(),
            request.proxyConnectTimeoutMs(),
            request.proxyReadTimeoutMs());

    boolean saveEnabled =
        !connectionValidation.idBad()
            && !connectionValidation.hostBad()
            && !connectionValidation.portBad()
            && !connectionValidation.nickBad()
            && !matrixValidation.credentialBad()
            && !matrixValidation.usernameBad()
            && !saslValidation.userBad()
            && !saslValidation.secretBad()
            && !nickservValidation.passwordBad()
            && !proxyValidation.hostBad()
            && !proxyValidation.portBad();

    return new ValidationState(
        connectionValidation,
        matrixValidation,
        authMode,
        saslValidation,
        nickservValidation,
        proxyValidation,
        saveEnabled);
  }

  record ValidationRequest(
      ServerEditorBackendProfile profile,
      String id,
      String host,
      String portText,
      String nick,
      ServerEditorMatrixAuthMode matrixAuthMode,
      String serverPassword,
      String matrixAuthUser,
      ServerEditorAuthMode selectedAuthMode,
      String saslMechanism,
      String saslUser,
      String saslSecret,
      String nickservPassword,
      boolean proxyOverrideSelected,
      boolean proxyEnabled,
      String proxyHost,
      String proxyPort,
      String proxyUser,
      String proxyPassword,
      String proxyConnectTimeoutMs,
      String proxyReadTimeoutMs) {}

  record ValidationState(
      ServerEditorConnectionPolicy.ConnectionValidation connectionValidation,
      ServerEditorAuthPolicy.MatrixValidation matrixValidation,
      ServerEditorAuthMode authMode,
      ServerEditorAuthPolicy.SaslValidation saslValidation,
      ServerEditorAuthPolicy.NickservValidation nickservValidation,
      ServerEditorProxyValidationPolicy.ProxyValidation proxyValidation,
      boolean saveEnabled) {}
}
