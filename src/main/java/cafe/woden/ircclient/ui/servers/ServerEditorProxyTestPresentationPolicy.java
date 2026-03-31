package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;

/** Pure presentation rules for server-editor proxy test feedback. */
final class ServerEditorProxyTestPresentationPolicy {
  private ServerEditorProxyTestPresentationPolicy() {}

  static ProxyTestSuccessPresentation successPresentation(
      boolean tls, IrcProperties.Proxy proxy, long elapsedMs) {
    return new ProxyTestSuccessPresentation(
        "OK (" + elapsedMs + " ms)",
        "Connection test succeeded.\n\n"
            + "TLS: "
            + (tls ? "yes" : "no")
            + "\n"
            + "Proxy: "
            + proxySummary(proxy)
            + "\n"
            + "Time: "
            + elapsedMs
            + " ms");
  }

  static ProxyTestFailurePresentation failurePresentation(String shortMessage, String longMessage) {
    return new ProxyTestFailurePresentation(
        "Failed: " + shortMessage, "Connection test failed.\n\n" + longMessage);
  }

  static ProxyTestFailurePresentation unexpectedFailurePresentation(String longMessage) {
    return new ProxyTestFailurePresentation("Failed", "Connection test failed.\n\n" + longMessage);
  }

  private static String proxySummary(IrcProperties.Proxy proxy) {
    return proxy.enabled() ? proxy.host() + ":" + proxy.port() : "disabled";
  }

  record ProxyTestSuccessPresentation(String statusText, String dialogMessage) {}

  record ProxyTestFailurePresentation(String statusText, String dialogMessage) {}
}
