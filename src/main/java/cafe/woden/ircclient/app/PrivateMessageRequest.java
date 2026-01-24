package cafe.woden.ircclient.app;

import java.util.Objects;

/** User asked to open a private message conversation on a specific server. */
public record PrivateMessageRequest(String serverId, String nick) {
  public PrivateMessageRequest {
    serverId = Objects.requireNonNull(serverId, "serverId").trim();
    nick = Objects.requireNonNull(nick, "nick").trim();
    if (serverId.isEmpty()) throw new IllegalArgumentException("serverId is blank");
    if (nick.isEmpty()) throw new IllegalArgumentException("nick is blank");
  }
}
