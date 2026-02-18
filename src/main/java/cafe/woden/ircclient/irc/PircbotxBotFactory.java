package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.net.SocksProxySocketFactory;
import cafe.woden.ircclient.net.SocksProxySslSocketFactory;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Factory for building a configured {@link PircBotX} instance for a given server.
 */
@Component
public class PircbotxBotFactory {

  private static final long DEFAULT_MESSAGE_DELAY_MS = 200L;
  private static final List<String> BASE_CAPABILITIES = List.of(
      "multi-prefix",
      "cap-notify",
      "away-notify",
      "account-notify",
      "extended-join",
      "setname",
      "chghost",
      "message-tags",
      "server-time",
      "standard-replies",
      "echo-message",
      "labeled-response",
      "draft/reply",
      "draft/react",
      "draft/message-edit",
      "message-edit",
      "draft/message-redaction",
      "message-redaction",
      "typing",
      "read-marker",
      "batch",
      "chathistory",
      "draft/chathistory",
      "znc.in/playback",
      "account-tag",
      "userhost-in-names"
  );

  private final ServerProxyResolver proxyResolver;
  private final SojuProperties sojuProps;

  public PircbotxBotFactory(ServerProxyResolver proxyResolver, SojuProperties sojuProps) {
    this.proxyResolver = proxyResolver;
    this.sojuProps = sojuProps;
  }

  public PircBotX build(IrcProperties.Server s, String version, ListenerAdapter listener) {
    ProxyPlan plan = proxyResolver.planForServer(s.id());

    SSLSocketFactory ssl = NetTlsContext.sslSocketFactory();

    SocketFactory socketFactory;
    if (plan.enabled()) {
      socketFactory = s.tls()
          ? new SocksProxySslSocketFactory(plan.cfg(), ssl)
          : new SocksProxySocketFactory(plan.cfg());
    } else {
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
        .setCapEnabled(true)
        .setAutoNickChange(true)
        .setAutoReconnect(false)
        .addListener(listener);
    configureCapHandlers(builder);
    applyMessageDelay(builder, DEFAULT_MESSAGE_DELAY_MS);

    if (s.serverPassword() != null && !s.serverPassword().isBlank()) {
      builder.setServerPassword(s.serverPassword());
    }
    for (String chan : s.autoJoin()) {
      String ch = chan == null ? "" : chan.trim();
      if (!ch.isEmpty()) builder.addAutoJoinChannel(ch);
    }
    if (s.sasl() != null && s.sasl().enabled()) {
      String mech = (s.sasl().mechanism() == null) ? "PLAIN" : s.sasl().mechanism().trim();
      String user = (s.sasl().username() == null) ? "" : s.sasl().username();
      String secret = (s.sasl().password() == null) ? "" : s.sasl().password();
      String mechUpper = mech.toUpperCase(Locale.ROOT);
      boolean hasSecret = secret != null && !secret.isBlank();
      boolean needsUser = switch (mechUpper) {
        case "EXTERNAL" -> false;
        case "AUTO" -> hasSecret;
        default -> true;
      };
      boolean needsSecret = switch (mechUpper) {
        case "EXTERNAL" -> false;
        case "AUTO" -> false;
        default -> true;
      };

      if (needsUser && user.isBlank()) {
        throw new IllegalStateException("SASL enabled but username not set");
      }
      if (needsSecret && secret.isBlank()) {
        throw new IllegalStateException("SASL enabled but secret not set");
      }
      builder.setCapEnabled(true);
      builder.addCapHandler(new MultiSaslCapHandler(
          user,
          secret,
          mech,
          s.sasl().disconnectOnFailure()
      ));
    }

    return new PircBotX(builder.buildConfiguration());
  }

  private void configureCapHandlers(Configuration.Builder builder) {
    builder.getCapHandlers().clear();
    List<String> caps = new ArrayList<>(BASE_CAPABILITIES);
    // Optional: soju network discovery.
    if (sojuProps.discovery().enabled()) {
      caps.add("soju.im/bouncer-networks");
    }
    builder.addCapHandler(new BatchedEnableCapHandler(caps));
  }

  private static void applyMessageDelay(Configuration.Builder builder, long delayMs) {
    try {
      Method m = builder.getClass().getMethod("setMessageDelay", long.class);
      m.invoke(builder, delayMs);
      return;
    } catch (ReflectiveOperationException ignored) {
    }
    try {
      Class<?> delayIface = Class.forName("org.pircbotx.delay.Delay");
      Method m = builder.getClass().getMethod("setMessageDelay", delayIface);
      Object delayObj;
      if (delayIface.isInterface()) {
        delayObj = java.lang.reflect.Proxy.newProxyInstance(
            delayIface.getClassLoader(),
            new Class<?>[] { delayIface },
            constantDelayHandler(delayMs)
        );
      } else {
        delayObj = tryConstructDelay(delayMs, delayIface);
      }

      if (delayObj != null) m.invoke(builder, delayObj);
    } catch (ReflectiveOperationException ignored) {
    }
  }

  private static InvocationHandler constantDelayHandler(long delayMs) {
    return (proxy, method, args) -> {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "toString" -> "ConstantDelay(" + delayMs + "ms)";
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
          default -> null;
        };
      }

      Class<?> rt = method.getReturnType();
      if (rt == long.class || rt == Long.class) return delayMs;
      if (rt == int.class || rt == Integer.class) return (int) Math.min(Integer.MAX_VALUE, delayMs);
      if (rt == Duration.class) return Duration.ofMillis(delayMs);
      return null;
    };
  }

  private static Object tryConstructDelay(long delayMs, Class<?> delayType) {
    try {
      return delayType.getConstructor(long.class).newInstance(delayMs);
    } catch (ReflectiveOperationException ignored) {
    }
    try {
      return delayType.getConstructor(int.class).newInstance((int) Math.min(Integer.MAX_VALUE, delayMs));
    } catch (ReflectiveOperationException ignored) {
    }
    try {
      return delayType.getConstructor(Duration.class).newInstance(Duration.ofMillis(delayMs));
    } catch (ReflectiveOperationException ignored) {
    }
    for (String name : new String[] { "ofMillis", "millis", "fixed", "constant" }) {
      try {
        Method m = delayType.getMethod(name, long.class);
        return m.invoke(null, delayMs);
      } catch (ReflectiveOperationException ignored) {
      }
    }
    return null;
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
      Socket s = ssl.createSocket();
      s.setSoTimeout(readTimeoutMs);
      return s;
    }
  }
}
