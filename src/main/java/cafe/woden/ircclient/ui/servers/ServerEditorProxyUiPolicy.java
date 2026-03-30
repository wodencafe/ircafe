package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;

/** Pure UI-state rules for the server-editor proxy controls. */
final class ServerEditorProxyUiPolicy {
  private ServerEditorProxyUiPolicy() {}

  static ProxyUiState uiState(
      boolean overrideSelected, boolean proxyEnabled, IrcProperties.Proxy globalProxy) {
    String hint =
        !overrideSelected
            ? globalProxy.enabled()
                ? "Inheriting global proxy from Preferences (enabled: "
                    + globalProxy.host()
                    + ":"
                    + globalProxy.port()
                    + ")"
                : "Inheriting global proxy from Preferences (disabled)"
            : "Override the global proxy for this server.\n";

    boolean proxyDetailsEnabled = overrideSelected && proxyEnabled;
    return new ProxyUiState(
        hint,
        overrideSelected,
        proxyDetailsEnabled,
        overrideSelected,
        overrideSelected,
        overrideSelected);
  }

  record ProxyUiState(
      String hint,
      boolean proxyEnabledToggleEnabled,
      boolean proxyDetailsEnabled,
      boolean remoteDnsEnabled,
      boolean connectTimeoutEnabled,
      boolean readTimeoutEnabled) {}
}
