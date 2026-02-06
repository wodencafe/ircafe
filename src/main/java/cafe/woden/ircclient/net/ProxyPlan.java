package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;

/**
 * Immutable resolved proxy plan for a single network operation.
 *
 * <p>This intentionally includes the resolved {@link Proxy} plus the timeout values that
 * callers should use for connect/read operations.
 */
public record ProxyPlan(
    IrcProperties.Proxy cfg,
    Proxy proxy,
    int connectTimeoutMs,
    int readTimeoutMs
) {

  public static ProxyPlan direct() {
    IrcProperties.Proxy cfg = NetProxyContext.normalize(null);
    return new ProxyPlan(cfg, Proxy.NO_PROXY, (int) cfg.connectTimeoutMs(), (int) cfg.readTimeoutMs());
  }

  public boolean enabled() {
    return cfg != null && cfg.enabled() && proxy != null && proxy != Proxy.NO_PROXY;
  }

  public static ProxyPlan from(IrcProperties.Proxy cfg) {
    cfg = NetProxyContext.normalize(cfg);

    if (cfg.enabled()) {
      String host = Objects.toString(cfg.host(), "").trim();
      int port = cfg.port();
      if (!host.isEmpty() && port > 0) {
        NetProxyContext.registerSocksAuth(cfg);
        Proxy p = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
        return new ProxyPlan(cfg, p, (int) cfg.connectTimeoutMs(), (int) cfg.readTimeoutMs());
      }
    }

    return new ProxyPlan(cfg, Proxy.NO_PROXY, (int) cfg.connectTimeoutMs(), (int) cfg.readTimeoutMs());
  }
}
