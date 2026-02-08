package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.net.SocksProxySocketFactory;
import cafe.woden.ircclient.net.SocksProxySslSocketFactory;
import cafe.woden.ircclient.net.NetTlsContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.EnableCapHandler;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Factory for building a configured {@link PircBotX} instance for a given server.
 * <p>
 * This keeps {@link PircbotxIrcClientService} focused on orchestration (connect/disconnect),
 * and isolates the PircbotX configuration details.
 */
@Component
public class PircbotxBotFactory {

  /**
   * PircbotX applies an outgoing-queue throttle ("message delay") to prevent flooding.
   * <p>
   * The historical default in the PircBot/PircBotX family is 1000ms, which can make
   * CAP negotiation + multi-channel JOIN feel sluggish (especially with bouncers).
   * <p>
   * We pick a smaller delay to keep the UI snappy while still providing basic flood
   * protection. If we later add a user-facing knob, this becomes the default.
   */
  private static final long DEFAULT_MESSAGE_DELAY_MS = 200L;

  private final ServerProxyResolver proxyResolver;

  public PircbotxBotFactory(ServerProxyResolver proxyResolver) {
    this.proxyResolver = proxyResolver;
  }

  public PircBotX build(IrcProperties.Server s, String version, ListenerAdapter listener) {
    // Resolve the effective proxy for this server. This honors per-server overrides and
    // falls back to the default proxy settings when no override is set.
    ProxyPlan plan = proxyResolver.planForServer(s.id());

    SSLSocketFactory ssl = NetTlsContext.sslSocketFactory();

    SocketFactory socketFactory;
    if (plan.enabled()) {
      // SOCKS proxy enabled for this server.
      socketFactory = s.tls()
          ? new SocksProxySslSocketFactory(plan.cfg(), ssl)
          : new SocksProxySocketFactory(plan.cfg());
    } else {
      // IMPORTANT: Bypass JVM-wide socksProxyHost/socksProxyPort settings (if any) so
      // servers that explicitly disable proxy actually connect directly.
      socketFactory = s.tls()
          ? new DirectTlsSocketFactory(ssl, plan.connectTimeoutMs(), plan.readTimeoutMs())
          : new DirectSocketFactory(plan.connectTimeoutMs(), plan.readTimeoutMs());
    }

    Configuration.Builder builder = new Configuration.Builder()
        .setName(s.nick())
        .setLogin(s.login())
        .setRealName(s.realName())
        .setVersion(version)
        .addServer(s.host(), s.port())
        .setSocketFactory(socketFactory)
        // Reduce output throttling so CAP/JOIN sequences don't take forever.
        .setMessageDelay(DEFAULT_MESSAGE_DELAY_MS)
        // Enable CAP so we can request low-cost IRCv3 capabilities (e.g. userhost-in-names).
        .setCapEnabled(true)
        // Prefer hostmasks in the initial NAMES list (when supported). If unsupported, ignore.
        .addCapHandler(new EnableCapHandler("userhost-in-names", true))
        // IRCv3 away-notify: server will send user AWAY state changes as raw AWAY commands.
        .addCapHandler(new EnableCapHandler("away-notify", true))
        // IRCv3 account-notify: server will send ACCOUNT updates when users log in/out.
        .addCapHandler(new EnableCapHandler("account-notify", true))
        // IRCv3 extended-join: JOIN includes account name + realname (when supported).
        .addCapHandler(new EnableCapHandler("extended-join", true))
        // IRCv3 message-tags: enables tagged messages (required for account-tag on some networks).
        .addCapHandler(new EnableCapHandler("message-tags", true))
        // IRCv3 server-time: tagged messages include @time=... so backlog/playback keeps real ordering.
        .addCapHandler(new EnableCapHandler("server-time", true))
        // IRCv3 batch: used to group history/playback into batches (required for chathistory).
        .addCapHandler(new EnableCapHandler("batch", true))
        // IRCv3 chathistory (draft): bouncers like soju can serve scrollback for infinite scroll.
        .addCapHandler(new EnableCapHandler("draft/chathistory", true))
        .addCapHandler(new EnableCapHandler("znc.in/playback", true))
        // IRCv3 account-tag: messages will include @account=... tags when supported.
        .addCapHandler(new EnableCapHandler("account-tag", true))
        .setAutoNickChange(true)
        // We manage reconnects ourselves so we can surface status + use backoff.
        .setAutoReconnect(false)
        .addListener(listener);

    if (s.serverPassword() != null && !s.serverPassword().isBlank()) {
      builder.setServerPassword(s.serverPassword());
    }

    // Auto-join channels from config
    for (String chan : s.autoJoin()) {
      String ch = chan == null ? "" : chan.trim();
      if (!ch.isEmpty()) builder.addAutoJoinChannel(ch);
    }

    // SASL (PLAIN)
    if (s.sasl() != null && s.sasl().enabled()) {
      if (!"PLAIN".equalsIgnoreCase(s.sasl().mechanism())) {
        throw new IllegalStateException(
            "Only SASL mechanism PLAIN is supported for now (got: " + s.sasl().mechanism() + ")"
        );
      }
      if (s.sasl().username().isBlank() || s.sasl().password().isBlank()) {
        throw new IllegalStateException("SASL enabled but username/password not set");
      }
      builder.setCapEnabled(true);
      builder.addCapHandler(new SASLCapHandler(s.sasl().username(), s.sasl().password()));
    }

    return new PircBotX(builder.buildConfiguration());
  }

  /** Direct socket factory that bypasses any JVM global SOCKS properties. */
  static final class DirectSocketFactory extends SocketFactory {

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    DirectSocketFactory(int connectTimeoutMs, int readTimeoutMs) {
      this.connectTimeoutMs = Math.max(1, connectTimeoutMs);
      this.readTimeoutMs = Math.max(0, readTimeoutMs);
    }

    private Socket base() {
      return new Socket(Proxy.NO_PROXY);
    }

    private Socket connect(Socket s, InetSocketAddress addr) throws IOException {
      s.connect(addr, connectTimeoutMs);
      s.setSoTimeout(readTimeoutMs);
      return s;
    }

    @Override
    public Socket createSocket() {
      return base();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
      return connect(base(), new InetSocketAddress(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
      Socket s = base();
      s.bind(new InetSocketAddress(localHost, localPort));
      return connect(s, new InetSocketAddress(host, port));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
      return connect(base(), new InetSocketAddress(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      Socket s = base();
      s.bind(new InetSocketAddress(localAddress, localPort));
      return connect(s, new InetSocketAddress(address, port));
    }
  }

  /** Direct TLS-over-TCP factory that bypasses any JVM global SOCKS properties. */
  static final class DirectTlsSocketFactory extends SocketFactory {

    private final SSLSocketFactory ssl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    DirectTlsSocketFactory(SSLSocketFactory ssl, int connectTimeoutMs, int readTimeoutMs) {
      this.ssl = ssl;
      this.connectTimeoutMs = Math.max(1, connectTimeoutMs);
      this.readTimeoutMs = Math.max(0, readTimeoutMs);
    }

    private Socket wrap(Socket tcp, String host, int port) throws IOException {
      Socket tls = ssl.createSocket(tcp, host, port, true);
      tls.setSoTimeout(readTimeoutMs);
      return tls;
    }

    private Socket connectTcp(String host, int port, InetAddress localHost, Integer localPort) throws IOException {
      Socket tcp = new Socket(Proxy.NO_PROXY);
      if (localHost != null && localPort != null) {
        tcp.bind(new InetSocketAddress(localHost, localPort));
      }
      tcp.connect(new InetSocketAddress(host, port), connectTimeoutMs);
      tcp.setSoTimeout(readTimeoutMs);
      return tcp;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
      return wrap(connectTcp(host, port, null, null), host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
      return wrap(connectTcp(host, port, localHost, localPort), host, port);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
      return createSocket(host.getHostName(), port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      return wrap(connectTcp(address.getHostName(), port, localAddress, localPort), address.getHostName(), port);
    }

    @Override
    public Socket createSocket() throws IOException {
      // Not used by PircBotX in the normal code path; provided for completeness.
      Socket s = ssl.createSocket();
      s.setSoTimeout(readTimeoutMs);
      return s;
    }
  }
}
