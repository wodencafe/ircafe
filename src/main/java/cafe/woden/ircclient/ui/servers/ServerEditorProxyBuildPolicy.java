package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;

/** Pure assembly rules for the server-editor per-server proxy override. */
final class ServerEditorProxyBuildPolicy {
  private static final long DEFAULT_CONNECT_TIMEOUT_MS = 20_000;
  private static final long DEFAULT_READ_TIMEOUT_MS = 30_000;

  private ServerEditorProxyBuildPolicy() {}

  static IrcProperties.Proxy buildOverride(
      boolean overrideSelected,
      boolean proxyEnabled,
      String host,
      String port,
      String user,
      String password,
      boolean remoteDns,
      String connectTimeoutMs,
      String readTimeoutMs) {
    if (!overrideSelected) {
      return null;
    }

    long resolvedConnectTimeoutMs =
        parseLongOrDefault(connectTimeoutMs, DEFAULT_CONNECT_TIMEOUT_MS);
    long resolvedReadTimeoutMs = parseLongOrDefault(readTimeoutMs, DEFAULT_READ_TIMEOUT_MS);

    if (!proxyEnabled) {
      return new IrcProperties.Proxy(
          false, "", 0, "", "", true, resolvedConnectTimeoutMs, resolvedReadTimeoutMs);
    }

    int resolvedPort;
    try {
      resolvedPort = Integer.parseInt(trim(port));
    } catch (Exception e) {
      throw new IllegalArgumentException("Proxy port must be a number");
    }

    return new IrcProperties.Proxy(
        true,
        trim(host),
        resolvedPort,
        trim(user),
        Objects.toString(password, ""),
        remoteDns,
        resolvedConnectTimeoutMs,
        resolvedReadTimeoutMs);
  }

  private static long parseLongOrDefault(String value, long defaultValue) {
    try {
      long parsed = Long.parseLong(trim(value));
      return parsed > 0 ? parsed : defaultValue;
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private static String trim(String value) {
    return Objects.toString(value, "").trim();
  }
}
