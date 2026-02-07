package cafe.woden.ircclient.app;

import java.util.Objects;

public record TargetRef(String serverId, String target) {

  /**
   * Reserved, UI-only pseudo-target for per-server notifications/mentions.
   *
   * <p>Intentionally uses a name that cannot collide with real IRC channels or nicks.</p>
   */
  public static final String NOTIFICATIONS_TARGET = "__notifications__";

  /** Convenience constructor for the per-server notifications pseudo-target. */
  public static TargetRef notifications(String serverId) {
    return new TargetRef(serverId, NOTIFICATIONS_TARGET);
  }

  public TargetRef {
    serverId = Objects.requireNonNull(serverId, "serverId").trim();
    target = Objects.requireNonNull(target, "target").trim();
    if (serverId.isEmpty()) throw new IllegalArgumentException("serverId is blank");
    if (target.isEmpty()) throw new IllegalArgumentException("target is blank");
  }

  public boolean isStatus() {
    return "status".equalsIgnoreCase(target);
  }

  /** True when this target is the UI-only per-server notifications pseudo-target. */
  public boolean isNotifications() {
    return NOTIFICATIONS_TARGET.equalsIgnoreCase(target);
  }

  /** True when this target should never be treated as a real IRC destination. */
  public boolean isUiOnly() {
    return isNotifications();
  }

  public boolean isChannel() {
    return target.startsWith("#") || target.startsWith("&");
  }
}
