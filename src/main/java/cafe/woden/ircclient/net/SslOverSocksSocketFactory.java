package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

/**
 * Creates TLS sockets routed through a SOCKS5 proxy.
 *
 * <p>This is primarily used for IRC-over-TLS connections where the client library accepts a {@link
 * SocketFactory}.
 */
public final class SslOverSocksSocketFactory extends SocketFactory {

  private final IrcProperties.Proxy cfg;
  private final SSLSocketFactory ssl;
  private final Proxy proxy;

  public SslOverSocksSocketFactory(IrcProperties.Proxy cfg, SSLSocketFactory ssl) {
    this.cfg =
        (cfg != null)
            ? cfg
            : new IrcProperties.Proxy(false, "", 0, null, null, true, 10_000, 30_000);
    this.ssl = (ssl != null) ? ssl : NetTlsContext.sslSocketFactory();

    // Use an explicit Proxy to support per-server overrides.
    // Do NOT rely on JVM-global socksProxyHost/socksProxyPort system properties.
    this.proxy =
        (this.cfg.enabled()
                && this.cfg.host() != null
                && !this.cfg.host().isBlank()
                && this.cfg.port() > 0)
            ? new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(this.cfg.host(), this.cfg.port()))
            : Proxy.NO_PROXY;

    NetProxyContext.registerSocksAuth(this.cfg);
  }

  private Socket connectSocks(String host, int port) throws IOException {
    Socket base = new Socket(proxy);

    InetSocketAddress target =
        cfg.remoteDns()
            ? InetSocketAddress.createUnresolved(host, port)
            : new InetSocketAddress(host, port);

    int connectTimeoutMs = (int) Math.max(1, cfg.connectTimeoutMs());

    base.connect(target, connectTimeoutMs);
    int readTimeoutMs = (int) Math.max(1, cfg.readTimeoutMs());
    base.setSoTimeout(readTimeoutMs);
    return base;
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    Socket base = connectSocks(host, port);
    return ssl.createSocket(base, host, port, true);
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException {
    Socket base = new Socket(proxy);
    base.bind(new InetSocketAddress(localHost, localPort));

    InetSocketAddress target =
        cfg.remoteDns()
            ? InetSocketAddress.createUnresolved(host, port)
            : new InetSocketAddress(host, port);

    base.connect(target, (int) Math.max(1, cfg.connectTimeoutMs()));
    base.setSoTimeout((int) Math.max(1, cfg.readTimeoutMs()));
    return ssl.createSocket(base, host, port, true);
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return createSocket(host.getHostName(), port);
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
      throws IOException {
    return createSocket(address.getHostName(), port, localAddress, localPort);
  }

  @Override
  public Socket createSocket() throws IOException {
    // Fallback. Most callers use createSocket(host,port). Without host/port we cannot connect
    // through SOCKS explicitly, so return a plain SSL socket.
    return ssl.createSocket();
  }
}
