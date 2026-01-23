package cafe.woden.ircclient.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "irc.server")
public record IrcServerProperties(
    String host,
    int port,
    boolean tls,
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

  public IrcServerProperties {
    if (sasl == null) {
      sasl = new Sasl(false, "", "", "PLAIN");
    }
    if (autoJoin == null) {
      autoJoin = List.of();
    }
  }
}
