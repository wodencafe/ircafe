package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.app.api.BackendUiMode;
import java.util.Locale;
import java.util.Objects;

/** Backend-specific presentation and validation defaults for {@link ServerEditorDialog}. */
record ServerEditorBackendProfile(
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

  ServerEditorBackendProfile {
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

  int defaultPort(boolean tls) {
    return tls ? defaultTlsPort : defaultPlainPort;
  }
}
