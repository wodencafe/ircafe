package cafe.woden.ircclient.config;

import java.util.Objects;

public record ServerEntry(IrcProperties.Server server, boolean ephemeral, String originId) {

  public ServerEntry {
    Objects.requireNonNull(server, "server");
    originId = normalize(originId);
  }

  public String id() {
    return server.id();
  }

  public static ServerEntry persistent(IrcProperties.Server server) {
    return new ServerEntry(server, false, null);
  }

  public static ServerEntry ephemeral(IrcProperties.Server server, String originId) {
    return new ServerEntry(server, true, originId);
  }

  private static String normalize(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? null : v;
  }
}
