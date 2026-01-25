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
public record IrcProperties(List<Server> servers) {

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
