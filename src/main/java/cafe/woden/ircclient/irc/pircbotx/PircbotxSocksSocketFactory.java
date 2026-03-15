package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.NetProxyContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Objects;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

/**
 * SOCKS-aware socket factory for client libraries that call {@link #createSocket()} first and
 * invoke {@link Socket#connect(SocketAddress, int)} later.
 */
final class PircbotxSocksSocketFactory extends SocketFactory {

  private final IrcProperties.Proxy cfg;
  private final Proxy proxy;
  private final String defaultHost;
  private final int defaultPort;
  private final SSLSocketFactory sslFactory;

  PircbotxSocksSocketFactory(IrcProperties.Proxy cfg, String defaultHost, int defaultPort) {
    this(cfg, defaultHost, defaultPort, null);
  }

  PircbotxSocksSocketFactory(
      IrcProperties.Proxy cfg, String defaultHost, int defaultPort, SSLSocketFactory sslFactory) {
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.defaultHost = Objects.toString(defaultHost, "").trim();
    this.defaultPort = defaultPort;
    this.sslFactory = sslFactory;

    NetProxyContext.registerSocksAuth(cfg);
    this.proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(cfg.host(), cfg.port()));
  }

  @Override
  public Socket createSocket() {
    return new DeferredSocket(defaultHost, defaultPort);
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    return createConnectedSocket(Objects.toString(host, ""), port, null, null);
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException {
    return createConnectedSocket(Objects.toString(host, ""), port, localHost, localPort);
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    String connectHost = host != null ? host.getHostName() : "";
    return createConnectedSocket(connectHost, port, null, null);
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
      throws IOException {
    String connectHost = address != null ? address.getHostName() : "";
    return createConnectedSocket(connectHost, port, localAddress, localPort);
  }

  private Socket createConnectedSocket(
      String connectHost, int connectPort, InetAddress localHost, Integer localPort)
      throws IOException {
    DeferredSocket socket = new DeferredSocket(connectHost, connectPort);
    if (localHost != null && localPort != null) {
      socket.bind(new InetSocketAddress(localHost, localPort));
    }
    socket.connect(
        cfg.remoteDns()
            ? InetSocketAddress.createUnresolved(connectHost, connectPort)
            : new InetSocketAddress(connectHost, connectPort),
        configuredConnectTimeoutMs());
    socket.setSoTimeout(configuredReadTimeoutMs());
    return socket;
  }

  private int configuredConnectTimeoutMs() {
    return Math.max(1, (int) Math.min(Integer.MAX_VALUE, cfg.connectTimeoutMs()));
  }

  private int configuredReadTimeoutMs() {
    return Math.max(0, (int) Math.min(Integer.MAX_VALUE, cfg.readTimeoutMs()));
  }

  private final class DeferredSocket extends Socket {
    private final String connectHost;
    private final int connectPort;
    private final Socket transport = new Socket(proxy);
    private Socket activeSocket = transport;
    private int soTimeoutMs = configuredReadTimeoutMs();
    private boolean connected;
    private boolean closed;

    private DeferredSocket(String connectHost, int connectPort) {
      this.connectHost = Objects.toString(connectHost, "").trim();
      this.connectPort = connectPort;
    }

    @Override
    public synchronized void bind(SocketAddress bindpoint) throws IOException {
      requireOpen();
      transport.bind(bindpoint);
    }

    @Override
    public synchronized void connect(SocketAddress endpoint) throws IOException {
      connect(endpoint, configuredConnectTimeoutMs());
    }

    @Override
    public synchronized void connect(SocketAddress endpoint, int timeout) throws IOException {
      requireOpen();
      if (connected) {
        throw new SocketException("Socket is already connected");
      }

      InetSocketAddress requested = requireInetSocketAddress(endpoint);
      String host = resolvedHost(requested);
      int port = resolvedPort(requested);
      SocketAddress remote =
          cfg.remoteDns() ? InetSocketAddress.createUnresolved(host, port) : requested;

      int connectTimeoutMs = timeout > 0 ? timeout : configuredConnectTimeoutMs();
      transport.connect(remote, connectTimeoutMs);
      transport.setSoTimeout(soTimeoutMs);

      if (sslFactory != null) {
        try {
          activeSocket = sslFactory.createSocket(transport, host, port, true);
          activeSocket.setSoTimeout(soTimeoutMs);
        } catch (IOException ioe) {
          transport.close();
          throw ioe;
        }
      }

      connected = true;
    }

    @Override
    public synchronized void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      activeSocket.close();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return activeSocket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      return activeSocket.getOutputStream();
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
      soTimeoutMs = Math.max(0, timeout);
      transport.setSoTimeout(soTimeoutMs);
      if (activeSocket != transport) {
        activeSocket.setSoTimeout(soTimeoutMs);
      }
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
      return activeSocket.getSoTimeout();
    }

    @Override
    public boolean isConnected() {
      return connected && activeSocket.isConnected();
    }

    @Override
    public boolean isClosed() {
      return closed || activeSocket.isClosed();
    }

    @Override
    public InetAddress getInetAddress() {
      return activeSocket.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
      return activeSocket.getLocalAddress();
    }

    @Override
    public int getPort() {
      return activeSocket.getPort();
    }

    @Override
    public int getLocalPort() {
      return activeSocket.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
      return activeSocket.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
      return activeSocket.getLocalSocketAddress();
    }

    @Override
    public void shutdownInput() throws IOException {
      activeSocket.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
      activeSocket.shutdownOutput();
    }

    @Override
    public boolean isInputShutdown() {
      return activeSocket.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
      return activeSocket.isOutputShutdown();
    }

    @Override
    public String toString() {
      return activeSocket.toString();
    }

    private void requireOpen() throws SocketException {
      if (closed) {
        throw new SocketException("Socket is closed");
      }
    }

    private InetSocketAddress requireInetSocketAddress(SocketAddress endpoint) {
      if (endpoint instanceof InetSocketAddress inetSocketAddress) {
        return inetSocketAddress;
      }
      throw new IllegalArgumentException("Unsupported endpoint type: " + endpoint);
    }

    private String resolvedHost(InetSocketAddress requested) {
      if (!connectHost.isEmpty()) {
        return connectHost;
      }
      return requested.getHostString();
    }

    private int resolvedPort(InetSocketAddress requested) {
      return connectPort > 0 ? connectPort : requested.getPort();
    }
  }
}
