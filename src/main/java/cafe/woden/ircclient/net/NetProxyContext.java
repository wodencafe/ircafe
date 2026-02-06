package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the current <em>default</em> SOCKS proxy settings in a globally accessible place.
 *
 * <p><b>Per-server overrides:</b> IRCafe supports configuring different SOCKS proxies per server.
 * Because JVM-wide SOCKS system properties ({@code socksProxyHost/socksProxyPort}) are global,
 * this class intentionally does <b>not</b> set them. All networking that should be proxied must
 * use an explicit {@link java.net.Proxy} (see {@link ProxyPlan} / {@link ServerProxyResolver}).
 */
public final class NetProxyContext {

  private static final IrcProperties.Proxy DEFAULT =
      new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000);

  private static volatile IrcProperties.Proxy settings = DEFAULT;
  private static volatile Proxy proxy = Proxy.NO_PROXY;

  /** SOCKS credentials keyed by proxy host/port. */
  private static final ConcurrentHashMap<SocksKey, PasswordAuthentication> SOCKS_AUTH =
      new ConcurrentHashMap<>();

  /** Ensures we install our Authenticator once. */
  private static final AtomicBoolean AUTH_INSTALLED = new AtomicBoolean(false);

  private NetProxyContext() {
  }

  public static void configure(IrcProperties.Proxy cfg) {
    cfg = normalize(cfg);
    settings = cfg;

    if (!cfg.enabled()) {
      proxy = Proxy.NO_PROXY;
      return;
    }

    proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(cfg.host(), cfg.port()));
    registerSocksAuth(cfg);
  }

  /** Normalizes null configs to a safe default. */
  public static IrcProperties.Proxy normalize(IrcProperties.Proxy cfg) {
    return (cfg == null) ? DEFAULT : cfg;
  }

  /** Returns the currently configured default proxy settings (never null). */
  public static IrcProperties.Proxy settings() {
    return settings;
  }

  /** Returns the current default SOCKS proxy (or {@link Proxy#NO_PROXY}). */
  public static Proxy proxy() {
    return proxy;
  }

  /**
   * Registers SOCKS proxy credentials for later use by the JVM's default {@link Authenticator}.
   *
   * <p>This is intentionally tolerant: it accepts repeated registrations and simply overwrites
   * the stored credentials for the same host/port.
   */
  public static void registerSocksAuth(IrcProperties.Proxy cfg) {
    if (cfg == null) return;
    if (!cfg.enabled()) return;
    if (!cfg.hasAuth()) return;

    String host = Objects.toString(cfg.host(), "").trim();
    int port = cfg.port();
    if (host.isEmpty() || port <= 0) return;

    installAuthenticatorIfNeeded();
    SOCKS_AUTH.put(new SocksKey(host, port),
        new PasswordAuthentication(cfg.username(), cfg.password().toCharArray()));
  }

  /** Returns an immutable view of currently registered SOCKS credential keys (for diagnostics). */
  public static Map<String, Integer> registeredSocksAuthKeys() {
    // Avoid exposing usernames/passwords.
    // Key format: "host:port" -> port (value is redundant but convenient for quick inspection).
    return SOCKS_AUTH.keySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
        k -> k.host + ":" + k.port, k -> k.port, (a, b) -> a));
  }

  private static void installAuthenticatorIfNeeded() {
    if (!AUTH_INSTALLED.compareAndSet(false, true)) return;

    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() != RequestorType.PROXY) return null;

        String reqHost = getRequestingHost();
        int reqPort = getRequestingPort();

        // Prefer exact host+port match.
        if (reqHost != null && !reqHost.isBlank() && reqPort > 0) {
          PasswordAuthentication pa = SOCKS_AUTH.get(new SocksKey(reqHost, reqPort));
          if (pa != null) return pa;
        }

        // Some JDK paths may provide only the port. If the port uniquely identifies a
        // registered proxy, use it. If ambiguous, return null.
        if (reqPort > 0) {
          PasswordAuthentication match = null;
          for (Map.Entry<SocksKey, PasswordAuthentication> e : SOCKS_AUTH.entrySet()) {
            if (e.getKey().port != reqPort) continue;
            if (match != null) return null; // ambiguous
            match = e.getValue();
          }
          if (match != null) return match;
        }

        return null;
      }
    });
  }

  private record SocksKey(String host, int port) {
    SocksKey {
      host = Objects.toString(host, "").trim();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SocksKey other)) return false;
      return port == other.port && host.equalsIgnoreCase(other.host);
    }

    @Override
    public int hashCode() {
      return Objects.hash(host.toLowerCase(), port);
    }
  }
}
