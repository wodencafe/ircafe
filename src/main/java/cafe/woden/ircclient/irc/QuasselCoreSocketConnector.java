package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Opens raw transport sockets for Quassel Core sessions (direct/proxied, optional TLS). */
@Component
@InfrastructureLayer
public class QuasselCoreSocketConnector {
  private final ServerProxyResolver proxyResolver;

  public QuasselCoreSocketConnector(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  public Socket connect(IrcProperties.Server server) throws IOException {
    IrcProperties.Server s = Objects.requireNonNull(server, "server");
    String host = Objects.toString(s.host(), "").trim();
    if (host.isEmpty()) {
      throw new IllegalArgumentException("server host is blank for id '" + s.id() + "'");
    }
    int port = s.port();
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("invalid server port " + port + " for id '" + s.id() + "'");
    }

    ProxyPlan plan = proxyResolver.planForServer(s.id());
    Socket tcp = openTcpSocket(plan, host, port);
    if (!s.tls()) {
      return tcp;
    }

    SSLSocketFactory sslFactory = NetTlsContext.sslSocketFactory();
    Socket tls = null;
    try {
      tls = sslFactory.createSocket(tcp, host, port, true);
      int readTimeoutMs = Math.max(0, plan.readTimeoutMs());
      if (readTimeoutMs > 0) {
        tls.setSoTimeout(readTimeoutMs);
      }
      if (tls instanceof SSLSocket sslSocket) {
        sslSocket.startHandshake();
      }
      return tls;
    } catch (IOException ioe) {
      try {
        if (tls != null) tls.close();
      } catch (Exception ignored) {
      }
      try {
        tcp.close();
      } catch (Exception ignored) {
      }
      throw ioe;
    }
  }

  private static Socket openTcpSocket(ProxyPlan plan, String host, int port) throws IOException {
    ProxyPlan resolved = (plan == null) ? ProxyPlan.direct() : plan;
    int connectTimeoutMs = Math.max(1, resolved.connectTimeoutMs());
    int readTimeoutMs = Math.max(0, resolved.readTimeoutMs());

    Socket socket =
        resolved.proxy() == null || resolved.proxy() == java.net.Proxy.NO_PROXY
            ? new Socket()
            : new Socket(resolved.proxy());

    InetSocketAddress remote =
        resolved.enabled() && resolved.cfg().remoteDns()
            ? InetSocketAddress.createUnresolved(host, port)
            : new InetSocketAddress(host, port);

    try {
      socket.connect(remote, connectTimeoutMs);
      if (readTimeoutMs > 0) {
        socket.setSoTimeout(readTimeoutMs);
      }
      return socket;
    } catch (IOException ioe) {
      try {
        socket.close();
      } catch (Exception ignored) {
      }
      throw ioe;
    }
  }
}
