package cafe.woden.ircclient.app.state;

import cafe.woden.ircclient.app.TargetRef;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Remembers where the user initiated a {@code /join} so that join-failure numerics
 * (e.g. 471-477) can be surfaced in the correct UI buffer.
 */
@Component
public class JoinRoutingState {

  private record Key(String serverId, String channelLower) {}
  private record RecentTarget(TargetRef target, Instant at) {}

  private final ConcurrentHashMap<Key, RecentTarget> recentJoinTargets = new ConcurrentHashMap<>();

  private static String normServer(String serverId) {
    return (serverId == null) ? "" : serverId.trim();
  }

  private static String normChannelLower(String channel) {
    String ch = (channel == null) ? "" : channel.trim();
    return ch.toLowerCase(Locale.ROOT);
  }

  public void rememberOrigin(String serverId, String channel, TargetRef origin) {
    if (origin == null) return;
    String sid = normServer(serverId);
    String chLower = normChannelLower(channel);
    if (sid.isEmpty() || chLower.isEmpty()) return;
    recentJoinTargets.put(new Key(sid, chLower), new RecentTarget(origin, Instant.now()));
  }

  public TargetRef recentOriginIfFresh(String serverId, String channel, Duration maxAge) {
    Objects.requireNonNull(maxAge, "maxAge");
    String sid = normServer(serverId);
    String chLower = normChannelLower(channel);
    if (sid.isEmpty() || chLower.isEmpty()) return null;

    RecentTarget rt = recentJoinTargets.get(new Key(sid, chLower));
    if (rt == null) return null;
    if (Duration.between(rt.at(), Instant.now()).compareTo(maxAge) <= 0) {
      return rt.target();
    }
    return null;
  }

  public void clear(String serverId, String channel) {
    String sid = normServer(serverId);
    String chLower = normChannelLower(channel);
    if (sid.isEmpty() || chLower.isEmpty()) return;
    recentJoinTargets.remove(new Key(sid, chLower));
  }

  public void clearServer(String serverId) {
    String sid = normServer(serverId);
    if (sid.isEmpty()) return;
    recentJoinTargets.keySet().removeIf(k -> Objects.equals(k.serverId(), sid));
  }
}
