package cafe.woden.ircclient.app.api;

import java.util.Locale;
import java.util.Objects;

/** Backend-specific server-editor defaults and copy exposed to the UI layer. */
public record BackendEditorProfileSpec(
    String backendId,
    String displayName,
    BackendUiMode uiMode,
    int defaultPlainPort,
    int defaultTlsPort,
    boolean directAuthEnabled,
    boolean matrixAuthSupported,
    boolean requiresNick,
    boolean usesNickAsDefaultLogin,
    boolean supportsQuasselCoreCommands,
    String defaultLoginFallback,
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
    String realNamePlaceholder) {

  public BackendEditorProfileSpec {
    backendId = Objects.requireNonNull(backendId, "backendId").trim().toLowerCase(Locale.ROOT);
    if (backendId.isEmpty()) {
      throw new IllegalArgumentException("backendId must not be blank");
    }
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(uiMode, "uiMode");
    Objects.requireNonNull(defaultLoginFallback, "defaultLoginFallback");
    Objects.requireNonNull(hostLabel, "hostLabel");
    Objects.requireNonNull(serverPasswordLabel, "serverPasswordLabel");
    Objects.requireNonNull(nickLabel, "nickLabel");
    Objects.requireNonNull(loginLabel, "loginLabel");
    Objects.requireNonNull(realNameLabel, "realNameLabel");
    Objects.requireNonNull(tlsToggleLabel, "tlsToggleLabel");
    Objects.requireNonNull(connectionHint, "connectionHint");
    Objects.requireNonNull(authDisabledHint, "authDisabledHint");
    Objects.requireNonNull(serverPasswordPlaceholder, "serverPasswordPlaceholder");
    Objects.requireNonNull(hostPlaceholder, "hostPlaceholder");
    Objects.requireNonNull(loginPlaceholder, "loginPlaceholder");
    Objects.requireNonNull(nickPlaceholder, "nickPlaceholder");
    Objects.requireNonNull(realNamePlaceholder, "realNamePlaceholder");
  }

  public int defaultPort(boolean tls) {
    return tls ? defaultTlsPort : defaultPlainPort;
  }

  public BackendEditorProfileSpec(
      String backendId,
      String displayName,
      int defaultPlainPort,
      int defaultTlsPort,
      boolean directAuthEnabled,
      boolean matrixAuthSupported,
      boolean requiresNick,
      boolean usesNickAsDefaultLogin,
      boolean supportsQuasselCoreCommands,
      String defaultLoginFallback,
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
      String realNamePlaceholder) {
    this(
        backendId,
        displayName,
        matrixAuthSupported ? BackendUiMode.MATRIX : BackendUiMode.IRC,
        defaultPlainPort,
        defaultTlsPort,
        directAuthEnabled,
        matrixAuthSupported,
        requiresNick,
        usesNickAsDefaultLogin,
        supportsQuasselCoreCommands,
        defaultLoginFallback,
        hostLabel,
        serverPasswordLabel,
        nickLabel,
        loginLabel,
        realNameLabel,
        tlsToggleLabel,
        connectionHint,
        authDisabledHint,
        serverPasswordPlaceholder,
        hostPlaceholder,
        loginPlaceholder,
        nickPlaceholder,
        realNamePlaceholder);
  }
}
