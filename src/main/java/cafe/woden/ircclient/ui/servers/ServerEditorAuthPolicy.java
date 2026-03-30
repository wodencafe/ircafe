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

  static ServerEditorAuthMode seedAuthMode(IrcProperties.Server seed) {
    if (seed == null) {
      return ServerEditorAuthMode.DISABLED;
    }
    boolean saslEnabled = seed.sasl() != null && seed.sasl().enabled();
    boolean nickservEnabled = seed.nickserv() != null && seed.nickserv().enabled();
    if (saslEnabled) {
      return ServerEditorAuthMode.SASL;
    }
    if (nickservEnabled) {
      return ServerEditorAuthMode.NICKSERV;
    }
    return ServerEditorAuthMode.DISABLED;
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

  static MatrixValidation matrixValidation(
      ServerEditorBackendProfile profile,
      ServerEditorMatrixAuthMode matrixAuthMode,
      String serverPassword,
      String matrixAuthUser) {
    boolean applicable = profile != null && profile.matrixAuthSupported();
    boolean credentialBad = applicable && trim(serverPassword).isEmpty();
    boolean usernameBad =
        isMatrixPasswordMode(profile, matrixAuthMode) && trim(matrixAuthUser).isEmpty();
    return new MatrixValidation(applicable, credentialBad, usernameBad);
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
    MatrixValidation validation =
        matrixValidation(profile, matrixAuthMode, serverPassword, matrixAuthUser);
    if (validation.credentialBad()) {
      throw new IllegalArgumentException(
          validation.usernameBad() || isMatrixPasswordMode(profile, matrixAuthMode)
              ? "Matrix password is required"
              : "Matrix access token is required");
    }
    if (validation.usernameBad()) {
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
    SaslMechanismMetadata metadata = saslMechanismMetadata(saslMechanism);
    SaslRequirements requirements =
        saslRequirements(metadata.normalizedMechanism(), !secret.isBlank());

    if (requirements.userRequired() && username.isEmpty()) {
      throw new IllegalArgumentException(
          "SASL username is required for mechanism " + metadata.normalizedMechanism());
    }
    if (requirements.secretRequired() && secret.isBlank()) {
      throw new IllegalArgumentException(
          "SASL secret is required for mechanism " + metadata.normalizedMechanism());
    }

    return new SaslBuildResult(
        serverPassword,
        new IrcProperties.Server.Sasl(
            true, username, secret, metadata.normalizedMechanism(), !saslContinueOnFailure),
        authMode);
  }

  static SaslValidation saslValidation(
      ServerEditorAuthMode authMode, String mechanism, String username, String secret) {
    if (authMode != ServerEditorAuthMode.SASL) {
      return new SaslValidation(false, false, false);
    }
    SaslMechanismMetadata metadata = saslMechanismMetadata(mechanism);
    SaslRequirements requirements =
        saslRequirements(metadata.normalizedMechanism(), !Objects.toString(secret, "").isBlank());
    boolean userBad = requirements.userRequired() && trim(username).isEmpty();
    boolean secretBad =
        metadata.secretEnabled()
            && requirements.secretRequired()
            && Objects.toString(secret, "").isBlank();
    return new SaslValidation(true, userBad, secretBad);
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

  static NickservValidation nickservValidation(ServerEditorAuthMode authMode, String password) {
    boolean applicable = authMode == ServerEditorAuthMode.NICKSERV;
    boolean passwordBad = applicable && Objects.toString(password, "").isBlank();
    return new NickservValidation(applicable, passwordBad);
  }

  static SaslMechanismMetadata saslMechanismMetadata(String mechanism) {
    String normalizedMechanism =
        Objects.toString(mechanism, "PLAIN").trim().toUpperCase(Locale.ROOT);
    return switch (normalizedMechanism) {
      case "EXTERNAL" ->
          new SaslMechanismMetadata(
              normalizedMechanism,
              false,
              "(ignored)",
              "EXTERNAL uses your TLS client certificate. Secret is ignored; username is optional.");
      case "ECDSA-NIST256P-CHALLENGE" ->
          new SaslMechanismMetadata(
              normalizedMechanism,
              true,
              "base64 PKCS#8 EC private key",
              "ECDSA challenge-response. Secret should be a base64 PKCS#8 EC private key. Username is usually required.");
      case "SCRAM-SHA-256" ->
          new SaslMechanismMetadata(
              normalizedMechanism,
              true,
              "password",
              "SCRAM-SHA-256 (recommended). Secret = password.");
      case "SCRAM-SHA-1" ->
          new SaslMechanismMetadata(
              normalizedMechanism, true, "password", "SCRAM-SHA-1. Secret = password.");
      case "AUTO" ->
          new SaslMechanismMetadata(
              normalizedMechanism,
              true,
              "password (leave blank for EXTERNAL)",
              "AUTO prefers SCRAM (256/1) or PLAIN when a secret is provided, and falls back to EXTERNAL when secret is blank.");
      default ->
          new SaslMechanismMetadata(
              normalizedMechanism, true, "password", "PLAIN. Secret = password.");
    };
  }

  static String trim(String value) {
    return Objects.toString(value, "").trim();
  }

  private static SaslRequirements saslRequirements(String mechanism, boolean hasSecret) {
    return switch (mechanism) {
      case "EXTERNAL" -> new SaslRequirements(false, false);
      case "AUTO" -> new SaslRequirements(hasSecret, false);
      default -> new SaslRequirements(true, true);
    };
  }

  record SaslBuildResult(
      String serverPassword, IrcProperties.Server.Sasl sasl, ServerEditorAuthMode authMode) {}

  record MatrixValidation(boolean applicable, boolean credentialBad, boolean usernameBad) {}

  record SaslValidation(boolean applicable, boolean userBad, boolean secretBad) {}

  record NickservValidation(boolean applicable, boolean passwordBad) {}

  record SaslMechanismMetadata(
      String normalizedMechanism, boolean secretEnabled, String secretPlaceholder, String hint) {}

  private record SaslRequirements(boolean userRequired, boolean secretRequired) {}
}
