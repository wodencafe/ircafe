package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import javax.net.SocketFactory;

/** SocketFactory that routes connections through a SOCKS5 proxy. */
public final class SocksProxySocketFactory extends SocketFactory {

  private final IrcProperties.Proxy cfg;
  private final Proxy proxy;

  public SocksProxySocketFactory(IrcProperties.Proxy cfg) {
    this.cfg = cfg;
    NetProxyContext.registerSocksAuth(cfg);
    this.proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(cfg.host(), cfg.port()));
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
  public Socket createSocket() {
    return proxiedSocket();
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    Socket s = proxiedSocket();
    s.connect(target(host, port), (int) cfg.connectTimeoutMs());
    s.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    return s;
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
    Socket s = proxiedSocket();
    s.bind(new InetSocketAddress(localHost, localPort));
    s.connect(target(host, port), (int) cfg.connectTimeoutMs());
    s.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    return s;
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    Socket s = proxiedSocket();
    s.connect(new InetSocketAddress(host, port), (int) cfg.connectTimeoutMs());
    s.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    return s;
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    Socket s = proxiedSocket();
    s.bind(new InetSocketAddress(localAddress, localPort));
    s.connect(new InetSocketAddress(address, port), (int) cfg.connectTimeoutMs());
    s.setSoTimeout((int) Math.max(0, cfg.readTimeoutMs()));
    return s;
  }
}
