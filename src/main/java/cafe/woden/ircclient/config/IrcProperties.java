package cafe.woden.ircclient.config;

import cafe.woden.ircclient.util.AppVersion;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * IRC client configuration.
 *
 * <p>Supports multiple servers via {@code irc.servers}.
 */
@ConfigurationProperties(prefix = "irc")
@InfrastructureLayer
public record IrcProperties(Client client, List<Server> servers) {

  /**
   * Global IRC client identity/settings.
   *
   * <p>Example YAML:
   *
   * <pre>
   * irc:
   *   client:
   *     version: "IRCafe 1.2.3"
   * </pre>
   */
  public record Client(
      String version, Reconnect reconnect, Heartbeat heartbeat, Proxy proxy, Tls tls) {

    /** TLS settings for outbound connections (IRC-over-TLS and HTTPS fetching). */
    public record Tls(boolean trustAllCertificates) {
      public Tls {
        // default false
      }
    }

    public Client {
      version = AppVersion.decorateIfDefaultName(version);
      if (reconnect == null) {
        reconnect = new Reconnect(true, 1_000, 120_000, 2.0, 0.20, 0);
      }
      if (heartbeat == null) {
        heartbeat = new Heartbeat(true, 15_000, 360_000);
      }
      if (proxy == null) {
        proxy = new Proxy(false, "", 0, "", "", true, 20_000, 30_000);
      }
      if (tls == null) {
        tls = new Tls(false);
      }
    }
  }

  /**
   * SOCKS5 proxy settings used for IRC connections and outbound HTTP fetching (link previews, image
   * embeds, etc.).
   */
  public record Proxy(
      boolean enabled,
      String host,
      int port,
      String username,
      String password,
      boolean remoteDns,
      long connectTimeoutMs,
      long readTimeoutMs) {
    public Proxy {
      if (host == null) host = "";
      if (username == null) username = "";
      if (password == null) password = "";
      if (connectTimeoutMs <= 0) connectTimeoutMs = 20_000;
      if (readTimeoutMs <= 0) readTimeoutMs = 30_000;

      if (enabled) {
        if (host.isBlank()) {
          throw new IllegalArgumentException("irc.client.proxy.enabled=true but host is blank");
        }
        if (port <= 0 || port > 65535) {
          throw new IllegalArgumentException(
              "irc.client.proxy.enabled=true but port is invalid: " + port);
        }
      }
    }

    public boolean hasAuth() {
      return username != null && !username.isBlank();
    }
  }

  public record Reconnect(
      boolean enabled,
      long initialDelayMs,
      long maxDelayMs,
      double multiplier,
      double jitterPct,
      int maxAttempts) {
    public Reconnect {
      if (initialDelayMs <= 0) initialDelayMs = 1_000;
      if (maxDelayMs <= 0) maxDelayMs = 120_000;
      if (maxDelayMs < initialDelayMs) maxDelayMs = initialDelayMs;
      if (multiplier < 1.1) multiplier = 2.0;
      if (jitterPct < 0) jitterPct = 0;
      if (jitterPct > 0.75) jitterPct = 0.75;
      // maxAttempts == 0 means "infinite".
      if (maxAttempts < 0) maxAttempts = 0;
    }
  }

  public record Heartbeat(boolean enabled, long checkPeriodMs, long timeoutMs) {
    public Heartbeat {
      if (checkPeriodMs <= 0) checkPeriodMs = 15_000;
      if (timeoutMs <= 0) timeoutMs = 360_000;
      if (timeoutMs < checkPeriodMs) timeoutMs = Math.max(checkPeriodMs * 2, checkPeriodMs);
    }
  }

  public record Server(
      String id,
      String host,
      int port,
      boolean tls,
      String serverPassword,
      String nick,
      String login,
      String realName,
      Sasl sasl,
      Nickserv nickserv,
      List<String> autoJoin,
      /**
       * Optional list of commands to run after connecting, similar to HexChat's "Perform" list.
       *
       * <p>Each entry is a single line. Later steps may add variable substitution and /sleep.
       */
      List<String> perform,
      /**
       * Optional per-server proxy override.
       *
       * <p>If {@code null}, the server inherits {@code irc.client.proxy}. If non-null and {@code
       * enabled} is {@code false}, the server explicitly disables proxying.
       */
      Proxy proxy,
      Backend backend) {
    /** Transport backend used for this server entry. */
    public enum Backend {
      IRC("irc"),
      QUASSEL_CORE("quassel-core"),
      MATRIX("matrix");

      private final String token;

      Backend(String token) {
        this.token = token;
      }

      public String token() {
        return token;
      }
    }

    public record Sasl(
        boolean enabled,
        String username,
        String password,
        String mechanism,
        /**
         * If true, a SASL failure (e.g. wrong password) is treated as a hard connect failure. The
         * client will disconnect and surface the error. If false, the client may remain connected
         * without SASL (useful for networks where SASL is optional).
         *
         * <p>If omitted, defaults to {@code true}.
         */
        Boolean disconnectOnFailure) {
      public Sasl {
        if (mechanism == null || mechanism.isBlank()) {
          mechanism = "PLAIN";
        }
        if (disconnectOnFailure == null) {
          // Strict default.
          disconnectOnFailure = true;
        }
      }
    }

    public record Nickserv(
        boolean enabled,
        String password,
        String service,
        /**
         * If true, delay auto-join channels until NickServ identification is confirmed by a notice.
         *
         * <p>If omitted, defaults to {@code true}.
         */
        Boolean delayJoinUntilIdentified) {
      public Nickserv {
        if (password == null) password = "";
        if (service == null || service.isBlank()) {
          service = "NickServ";
        }
        if (delayJoinUntilIdentified == null) {
          delayJoinUntilIdentified = true;
        }
      }
    }

    @ConstructorBinding
    public Server {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("irc.servers[].id is required");
      }
      if (serverPassword == null) {
        serverPassword = "";
      }
      if (sasl == null) {
        sasl = new Sasl(false, "", "", "PLAIN", null);
      }
      if (nickserv == null) {
        nickserv = new Nickserv(false, "", "NickServ", null);
      }
      if (autoJoin == null) {
        autoJoin = List.of();
      }
      if (perform == null) {
        perform = List.of();
      }
      if (backend == null) {
        backend = Backend.IRC;
      }
    }

    // Legacy constructor kept for call sites that don't set backend explicitly.
    public Server(
        String id,
        String host,
        int port,
        boolean tls,
        String serverPassword,
        String nick,
        String login,
        String realName,
        Sasl sasl,
        Nickserv nickserv,
        List<String> autoJoin,
        List<String> perform,
        Proxy proxy) {
      this(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          autoJoin,
          perform,
          proxy,
          Backend.IRC);
    }

    public Server(
        String id,
        String host,
        int port,
        boolean tls,
        String serverPassword,
        String nick,
        String login,
        String realName,
        Sasl sasl,
        List<String> autoJoin,
        List<String> perform,
        Proxy proxy) {
      this(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          null,
          autoJoin,
          perform,
          proxy,
          Backend.IRC);
    }

    public Server withAutoJoin(List<String> nextAutoJoin) {
      List<String> value = nextAutoJoin == null ? List.of() : List.copyOf(nextAutoJoin);
      return new Server(
          id,
          host,
          port,
          tls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          value,
          perform,
          proxy,
          backend);
    }

    public Server withTransport(int nextPort, boolean nextTls) {
      return new Server(
          id,
          host,
          nextPort,
          nextTls,
          serverPassword,
          nick,
          login,
          realName,
          sasl,
          nickserv,
          autoJoin,
          perform,
          proxy,
          backend);
    }
  }

  public IrcProperties {
    if (client == null) {
      client = new Client("IRCafe", null, null, null, null);
    }
    if (servers == null) {
      servers = List.of();
    }
  }

  public Map<String, Server> byId() {
    return servers.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                s -> s.id().trim(),
                Function.identity(),
                (a, b) -> {
                  throw new IllegalStateException("Duplicate server id: " + a.id());
                }));
  }
}
