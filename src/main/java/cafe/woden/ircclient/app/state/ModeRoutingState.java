package cafe.woden.ircclient.app.state;

import cafe.woden.ircclient.app.TargetRef;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks pending MODE queries so inbound mode summaries (e.g., numeric 324) can be routed
 * back to the tab where the query originated.
 *
 * <p>Note: join-burst mode suppression/buffering currently remains in {@code IrcMediator}
 * because it relies on package-private helpers in {@code cafe.woden.ircclient.app}.
 * We'll extract that in a later step once we decide whether to (a) make those helpers public,
 * or (b) move the buffering logic into the app package.
 */
@Component
public class ModeRoutingState {

  private final ConcurrentHashMap<ModeKey, TargetRef> pendingModeTargets = new ConcurrentHashMap<>();

  /** Record that a MODE query for {@code channel} should route output to {@code target}. */
  public void putPendingModeTarget(String serverId, String channel, TargetRef target) {
    if (target == null) return;
    pendingModeTargets.put(ModeKey.of(serverId, channel), target);
  }

  /** Remove and return the routing target for {@code channel}, or {@code null} if none. */
  public TargetRef removePendingModeTarget(String serverId, String channel) {
    return pendingModeTargets.remove(ModeKey.of(serverId, channel));
  }

  /** Peek the routing target for {@code channel}, or {@code null} if none. */
  public TargetRef getPendingModeTarget(String serverId, String channel) {
    return pendingModeTargets.get(ModeKey.of(serverId, channel));
  }

  /** Drop all pending MODE entries for a server (e.g., on disconnect). */
  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    pendingModeTargets.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
  }

  private static String normalizeServer(String serverId) {
    return (serverId == null) ? "" : serverId.trim();
  }

  private record ModeKey(String serverId, String channelLower) {
    ModeKey {
      serverId = normalizeServer(serverId);
      channelLower = (channelLower == null) ? "" : channelLower.trim().toLowerCase(Locale.ROOT);
    }

    static ModeKey of(String serverId, String channel) {
      return new ModeKey(serverId, channel);
    }
  }
}
