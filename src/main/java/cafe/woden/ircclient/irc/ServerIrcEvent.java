package cafe.woden.ircclient.irc;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** Wraps an {@link IrcEvent} with the originating server id. */
@ValueObject
public record ServerIrcEvent(String serverId, IrcEvent event) {
  public ServerIrcEvent {
    serverId = Objects.requireNonNull(serverId, "serverId").trim();
    Objects.requireNonNull(event, "event");
    if (serverId.isEmpty()) throw new IllegalArgumentException("serverId is blank");
  }
}
