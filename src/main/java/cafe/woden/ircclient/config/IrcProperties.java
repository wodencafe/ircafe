package cafe.woden.ircclient.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IRC client configuration.
 *
 * <p>Supports multiple servers via {@code irc.servers}.
 */
@ConfigurationProperties(prefix = "irc")
public record IrcProperties(Client client, List<Server> servers) {


  /**
   * Global IRC client identity/settings.
   *
   * <p>Example YAML:
   * <pre>
   * irc:
   *   client:
   *     version: "IRCafe 1.2.3"
   * </pre>
   */
  public record Client(String version, Reconnect reconnect) {
    public Client {
      if (version == null || version.isBlank()) {
        version = "IRCafe";
      }
      if (reconnect == null) {
        reconnect = new Reconnect(true, 1_000, 120_000, 2.0, 0.20, 0);
      }
    }
  }

  public record Reconnect(
      boolean enabled,
      long initialDelayMs,
      long maxDelayMs,
      double multiplier,
      double jitterPct,
      int maxAttempts
  ) {
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
      List<String> autoJoin
  ) {
    public record Sasl(
        boolean enabled,
        String username,
        String password,
        String mechanism
    ) {}

    public Server {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("irc.servers[].id is required");
      }
      if (serverPassword == null) {
        serverPassword = "";
      }
      if (sasl == null) {
        sasl = new Sasl(false, "", "", "PLAIN");
      }
      if (autoJoin == null) {
        autoJoin = List.of();
      }
    }
  }

  public IrcProperties {
    if (client == null) {
      client = new Client("IRCafe", null);
    }
    if (servers == null) {
      servers = List.of();
    }
  }

  public Map<String, Server> byId() {
    return servers.stream().collect(Collectors.toUnmodifiableMap(
        s -> s.id().trim(),
        Function.identity(),
        (a, b) -> {
          throw new IllegalStateException("Duplicate server id: " + a.id());
        }
    ));
  }
}
