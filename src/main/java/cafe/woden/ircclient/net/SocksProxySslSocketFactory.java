package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import javax.net.ssl.SSLSocketFactory;

/**
 * SSLSocketFactory that connects the underlying TCP socket through a SOCKS5 proxy before layering
 * TLS on top.
 */
public final class SocksProxySslSocketFactory extends SSLSocketFactory {

  private final IrcProperties.Proxy cfg;
  private final Proxy proxy;
  private final SSLSocketFactory delegate;

  public SocksProxySslSocketFactory(IrcProperties.Proxy cfg, SSLSocketFactory delegate) {
    this.cfg = cfg;
    NetProxyContext.registerSocksAuth(cfg);
    this.proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(cfg.host(), cfg.port()));
    this.delegate = delegate;
  }

  private InetSocketAddress target(String host, int port) {
    return cfg.remoteDns()
        ? InetSocketAddress.createUnresolved(host, port)
        : new InetSocketAddress(host, port);
  }

  private Socket proxiedSocket() {
    return new Socket(proxy);
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return delegate.getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }

  @Override
  public Socket createSocket() throws IOException {
    // Best-effort: return a delegate socket. If callers later connect it, it may or may not
    // honor proxy settings depending on the underlying implementation. Most code paths used
    // by PircBotX call the host/port overloads below.
    return delegate.createSocket();
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    Socket tcp = proxiedSocket();
    tcp.connect(target(host, port), (int) cfg.connectTimeoutMs());
    tcp.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    Socket tls = delegate.createSocket(tcp, host, port, true);
    tls.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    return tls;
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException {
    Socket tcp = proxiedSocket();
    tcp.bind(new InetSocketAddress(localHost, localPort));
    tcp.connect(target(host, port), (int) cfg.connectTimeoutMs());
    tcp.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    Socket tls = delegate.createSocket(tcp, host, port, true);
    tls.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    return tls;
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    Socket tcp = proxiedSocket();
    tcp.connect(new InetSocketAddress(host, port), (int) cfg.connectTimeoutMs());
    tcp.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    Socket tls = delegate.createSocket(tcp, host.getHostName(), port, true);
    tls.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    return tls;
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
      throws IOException {
    Socket tcp = proxiedSocket();
    tcp.bind(new InetSocketAddress(localAddress, localPort));
    tcp.connect(new InetSocketAddress(address, port), (int) cfg.connectTimeoutMs());
    tcp.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    Socket tls = delegate.createSocket(tcp, address.getHostName(), port, true);
    tls.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    return tls;
  }

  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose)
      throws IOException {
    return delegate.createSocket(s, host, port, autoClose);
  }
}
