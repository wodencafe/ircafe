package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;

/** Pure seeding rules for the server-editor proxy controls. */
final class ServerEditorProxySeedPolicy {
  private ServerEditorProxySeedPolicy() {}

  static ProxySeedState seedState(
      IrcProperties.Proxy serverProxy, IrcProperties.Proxy globalProxy) {
    boolean overrideSelected = serverProxy != null;
    IrcProperties.Proxy effectiveProxy = overrideSelected ? serverProxy : globalProxy;
    return new ProxySeedState(
        overrideSelected,
        effectiveProxy.enabled(),
        Objects.toString(effectiveProxy.host(), ""),
        effectiveProxy.port() > 0 ? Integer.toString(effectiveProxy.port()) : "",
        effectiveProxy.remoteDns(),
        Objects.toString(effectiveProxy.username(), ""),
        Objects.toString(effectiveProxy.password(), ""),
        Long.toString(effectiveProxy.connectTimeoutMs()),
        Long.toString(effectiveProxy.readTimeoutMs()));
  }

  record ProxySeedState(
      boolean overrideSelected,
      boolean proxyEnabled,
      String host,
      String portText,
      boolean remoteDns,
      String username,
      String password,
      String connectTimeoutMsText,
      String readTimeoutMsText) {}
}
