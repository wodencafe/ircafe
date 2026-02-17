package cafe.woden.ircclient.app;

import java.util.Locale;
import java.util.Objects;

/**
 * Identifies a chat "target" within a server.
 *
 * <p>IRC targets (channels + nicks) are case-insensitive for identity purposes.
 * To avoid accidental duplicates (e.g. "##Llamas" vs "##llamas"), equality and
 * hashing are performed using a folded (lowercased) key.
 *
 * <p>The original {@link #target()} is preserved for display/persistence.
 */
public final class TargetRef {

  public static final String NOTIFICATIONS_TARGET = "__notifications__";
  public static final String CHANNEL_LIST_TARGET = "__channel_list__";
  public static final String DCC_TRANSFERS_TARGET = "__dcc_transfers__";

  private final String serverId;
  private final String target;
  private final String key;

  public TargetRef(String serverId, String target) {
    this.serverId = norm(serverId);
    this.target = norm(target);
    if (this.serverId.isEmpty()) throw new IllegalArgumentException("serverId must not be blank");
    if (this.target.isEmpty()) throw new IllegalArgumentException("target must not be blank");
    this.key = foldKey(this.target);
  }

  public static TargetRef notifications(String serverId) {
    return new TargetRef(serverId, NOTIFICATIONS_TARGET);
  }

  public static TargetRef channelList(String serverId) {
    return new TargetRef(serverId, CHANNEL_LIST_TARGET);
  }

  public static TargetRef dccTransfers(String serverId) {
    return new TargetRef(serverId, DCC_TRANSFERS_TARGET);
  }

  public String serverId() {
    return serverId;
  }

  /**
   * Display/persistence target string.
   *
   * <p>Do not use this for identity comparisons. Use {@link #matches(String)} or
   * rely on {@link #equals(Object)}.
   */
  public String target() {
    return target;
  }

  /**
   * Case-folded identity key.
   *
   * <p>Mostly intended for debugging.
   */
  public String key() {
    return key;
  }

  public boolean isStatus() {
    return "status".equals(key);
  }

  public boolean isNotifications() {
    return NOTIFICATIONS_TARGET.equals(key);
  }

  public boolean isChannelList() {
    return CHANNEL_LIST_TARGET.equals(key);
  }

  public boolean isDccTransfers() {
    return DCC_TRANSFERS_TARGET.equals(key);
  }

  public boolean isUiOnly() {
    // UI-only targets are pseudo-buffers that do not represent a real IRC target.
    // "status" is a real transcript buffer in ircafe (and can accept raw server input).
    return isNotifications() || isChannelList() || isDccTransfers();
  }

  public boolean isChannel() {
    return target.startsWith("#") || target.startsWith("&");
  }

  /** True if this ref refers to the same target as {@code otherTarget} (case-insensitive). */
  public boolean matches(String otherTarget) {
    return key.equals(foldKey(otherTarget));
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }

  private static String foldKey(String target) {
    String t = norm(target);
    if (t.isEmpty()) return "";
    if (NOTIFICATIONS_TARGET.equals(t)) return NOTIFICATIONS_TARGET;
    if (CHANNEL_LIST_TARGET.equals(t)) return CHANNEL_LIST_TARGET;
    if (DCC_TRANSFERS_TARGET.equals(t)) return DCC_TRANSFERS_TARGET;
    return t.toLowerCase(Locale.ROOT);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TargetRef other)) return false;
    return Objects.equals(serverId, other.serverId) && Objects.equals(key, other.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serverId, key);
  }

  @Override
  public String toString() {
    return "TargetRef{" + serverId + ":" + target + "}";
  }
}
