package cafe.woden.ircclient.ui.servers;

import java.util.Objects;

/** Computes proxy-test success decoration state for the server editor. */
final class ServerEditorProxyTestDecorationPolicy {
  private ServerEditorProxyTestDecorationPolicy() {}

  static ProxyTestDecorationState decorationState(
      boolean hasLastSuccessfulTest,
      boolean snapshotMatchesLastSuccess,
      boolean proxyEndpointEnabled,
      String connectTimeoutMs,
      String readTimeoutMs) {
    if (!hasLastSuccessfulTest || !snapshotMatchesLastSuccess) {
      return new ProxyTestDecorationState(false, false, false, false, false);
    }

    if (!proxyEndpointEnabled) {
      return new ProxyTestDecorationState(true, false, false, false, false);
    }

    return new ProxyTestDecorationState(
        true,
        true,
        true,
        timeoutDecorationEnabled(connectTimeoutMs),
        timeoutDecorationEnabled(readTimeoutMs));
  }

  private static boolean timeoutDecorationEnabled(String value) {
    String trimmed = Objects.toString(value, "").trim();
    if (trimmed.isEmpty()) {
      return true;
    }
    try {
      return Long.parseLong(trimmed) > 0;
    } catch (Exception e) {
      return false;
    }
  }

  record ProxyTestDecorationState(
      boolean retainLastSuccessfulSnapshot,
      boolean hostSuccess,
      boolean portSuccess,
      boolean connectTimeoutSuccess,
      boolean readTimeoutSuccess) {}
}
