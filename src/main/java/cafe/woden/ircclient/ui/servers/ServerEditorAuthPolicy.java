package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Locale;
import java.util.Objects;

/** Backend-specific auth rules for the server editor dialog. */
final class ServerEditorAuthPolicy {
  static final String MATRIX_PASSWORD_AUTH_MECHANISM = "MATRIX_PASSWORD";
  static final String DEFAULT_NICKSERV_SERVICE = "NickServ";

  private ServerEditorAuthPolicy() {}

  static ServerEditorMatrixAuthMode seedMatrixAuthMode(
      ServerEditorBackendProfile profile, IrcProperties.Server seed) {
    if (seed == null || profile == null || !profile.matrixAuthSupported()) {
      return ServerEditorMatrixAuthMode.ACCESS_TOKEN;
    }
    return isMatrixPasswordAuthMode(seed.sasl())
        ? ServerEditorMatrixAuthMode.USERNAME_PASSWORD
        : ServerEditorMatrixAuthMode.ACCESS_TOKEN;
  }

  static boolean isMatrixPasswordAuthMode(IrcProperties.Server.Sasl sasl) {
    if (sasl == null || !sasl.enabled()) return false;
    String mechanism = Objects.toString(sasl.mechanism(), "").trim();
    return MATRIX_PASSWORD_AUTH_MECHANISM.equalsIgnoreCase(mechanism);
  }

  static boolean isMatrixPasswordMode(
      ServerEditorBackendProfile profile, ServerEditorMatrixAuthMode mode) {
    return profile != null
        && profile.matrixAuthSupported()
        && mode == ServerEditorMatrixAuthMode.USERNAME_PASSWORD;
  }

  static boolean disablesTraditionalAuthMode(ServerEditorBackendProfile profile) {
    return profile != null
        && (profile.supportsQuasselCoreCommands() || profile.matrixAuthSupported());
  }

  static ServerEditorAuthMode effectiveAuthMode(
      ServerEditorBackendProfile profile, ServerEditorAuthMode requestedMode) {
    ServerEditorAuthMode requested =
        requestedMode == null ? ServerEditorAuthMode.DISABLED : requestedMode;
    if (profile == null) {
      return requested;
    }
    if (!profile.directAuthEnabled() || disablesTraditionalAuthMode(profile)) {
      return ServerEditorAuthMode.DISABLED;
    }
    return requested;
  }

  static void validateMatrixCredentials(
      ServerEditorBackendProfile profile,
      ServerEditorMatrixAuthMode matrixAuthMode,
      String serverPassword,
      String matrixAuthUser) {
    if (profile == null || !profile.matrixAuthSupported()) {
      return;
    }
    if (trim(serverPassword).isEmpty()) {
      throw new IllegalArgumentException(
          isMatrixPasswordMode(profile, matrixAuthMode)
              ? "Matrix password is required"
              : "Matrix access token is required");
    }
    if (isMatrixPasswordMode(profile, matrixAuthMode) && trim(matrixAuthUser).isEmpty()) {
      throw new IllegalArgumentException("Matrix username is required");
    }
  }

  static String resolveLogin(
      ServerEditorBackendProfile profile,
      String login,
      String nick,
      String matrixAuthUser,
      ServerEditorMatrixAuthMode matrixAuthMode) {
    String resolved = trim(login);
    if (profile != null && isMatrixPasswordMode(profile, matrixAuthMode) && resolved.isEmpty()) {
      resolved = trim(matrixAuthUser);
    }
    if (profile != null && resolved.isEmpty() && profile.usesNickAsDefaultLogin()) {
      resolved = trim(nick);
    }
    if (profile != null && resolved.isEmpty()) {
      resolved = profile.defaultLoginFallback();
    }
    return resolved;
  }

  static SaslBuildResult buildSasl(
      ServerEditorBackendProfile profile,
      ServerEditorAuthMode requestedAuthMode,
      ServerEditorMatrixAuthMode matrixAuthMode,
      String serverPassword,
      String matrixAuthUser,
      String saslUser,
      String saslSecret,
      String saslMechanism,
      boolean saslContinueOnFailure) {
    ServerEditorAuthMode authMode = effectiveAuthMode(profile, requestedAuthMode);
    if (isMatrixPasswordMode(profile, matrixAuthMode)) {
      return new SaslBuildResult(
          "",
          new IrcProperties.Server.Sasl(
              true,
              trim(matrixAuthUser),
              Objects.toString(serverPassword, ""),
              MATRIX_PASSWORD_AUTH_MECHANISM,
              true),
          authMode);
    }
    if (authMode != ServerEditorAuthMode.SASL) {
      return new SaslBuildResult(
          serverPassword, new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null), authMode);
    }

    String username = trim(saslUser);
    String secret = Objects.toString(saslSecret, "");
    String mechanism = Objects.toString(saslMechanism, "PLAIN").trim();
    String mechanismUpper = mechanism.toUpperCase(Locale.ROOT);
    boolean hasSecret = !secret.isBlank();
    boolean needsUser =
        switch (mechanismUpper) {
          case "EXTERNAL" -> false;
          case "AUTO" -> hasSecret;
          default -> true;
        };
    boolean needsSecret =
        switch (mechanismUpper) {
          case "EXTERNAL" -> false;
          case "AUTO" -> false;
          default -> true;
        };

    if (needsUser && username.isEmpty()) {
      throw new IllegalArgumentException(
          "SASL username is required for mechanism " + mechanismUpper);
    }
    if (needsSecret && secret.isBlank()) {
      throw new IllegalArgumentException("SASL secret is required for mechanism " + mechanismUpper);
    }

    return new SaslBuildResult(
        serverPassword,
        new IrcProperties.Server.Sasl(true, username, secret, mechanism, !saslContinueOnFailure),
        authMode);
  }

  static IrcProperties.Server.Nickserv buildNickserv(
      ServerEditorAuthMode authMode,
      String service,
      String password,
      boolean delayJoinUntilIdentified) {
    if (authMode != ServerEditorAuthMode.NICKSERV) {
      return new IrcProperties.Server.Nickserv(false, "", DEFAULT_NICKSERV_SERVICE, true);
    }
    String resolvedService = trim(service);
    if (resolvedService.isEmpty()) {
      resolvedService = DEFAULT_NICKSERV_SERVICE;
    }
    String resolvedPassword = Objects.toString(password, "");
    if (resolvedPassword.isBlank()) {
      throw new IllegalArgumentException("NickServ password is required when NickServ is enabled");
    }
    return new IrcProperties.Server.Nickserv(
        true, resolvedPassword, resolvedService, delayJoinUntilIdentified);
  }

  static String trim(String value) {
    return Objects.toString(value, "").trim();
  }

  record SaslBuildResult(
      String serverPassword, IrcProperties.Server.Sasl sasl, ServerEditorAuthMode authMode) {}
}
