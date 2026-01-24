package cafe.woden.ircclient.irc;

import java.util.Objects;

/** Wraps an {@link IrcEvent} with the originating server id. */
public record ServerIrcEvent(String serverId, IrcEvent event) {
  public ServerIrcEvent {
    serverId = Objects.requireNonNull(serverId, "serverId").trim();
    event = Objects.requireNonNull(event, "event");
    if (serverId.isEmpty()) throw new IllegalArgumentException("serverId is blank");
  }
}
