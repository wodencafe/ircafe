package cafe.woden.ircclient.net;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/**
 * A lightweight {@link ProxySelector} that routes HTTP(S) traffic through the currently configured
 * SOCKS proxy (if enabled).
 *
 * <p>This is intentionally global + dynamic: {@link NetProxyContext#configure} may be called on
 * startup (and potentially later), and callers can keep using the same selector instance.
 */
public final class NetProxySelector extends ProxySelector {

  /** Reusable singleton selector. */
  public static final NetProxySelector INSTANCE = new NetProxySelector();

  private NetProxySelector() {
  }

  @Override
  public List<Proxy> select(URI uri) {
    if (uri == null) return List.of(Proxy.NO_PROXY);

    // Only proxy HTTP(S) link-preview traffic.
    String scheme = uri.getScheme();
    if (scheme == null) return List.of(Proxy.NO_PROXY);
    scheme = scheme.toLowerCase();
    if (!scheme.equals("http") && !scheme.equals("https")) {
      return List.of(Proxy.NO_PROXY);
    }

    Proxy p = NetProxyContext.proxy();
    if (p == null || p == Proxy.NO_PROXY) {
      return List.of(Proxy.NO_PROXY);
    }

    return List.of(p);
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    // Best-effort only; preview and image fetchers already log failures.
  }
}
