package cafe.woden.ircclient.app;

import java.util.Objects;

/**
 * Identifies a chat target within a specific server connection.
 *
 */
public record TargetRef(String serverId, String target) {

  public TargetRef {
    serverId = Objects.requireNonNull(serverId, "serverId").trim();
    target = Objects.requireNonNull(target, "target").trim();
    if (serverId.isEmpty()) throw new IllegalArgumentException("serverId is blank");
    if (target.isEmpty()) throw new IllegalArgumentException("target is blank");
  }

  public boolean isStatus() {
    return "status".equalsIgnoreCase(target);
  }

  public boolean isChannel() {
    return target.startsWith("#") || target.startsWith("&");
  }
}
