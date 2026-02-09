package cafe.woden.ircclient.app.state;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks the last time we observed a status-mode change (e.g. +v/+o) per channel.
 *
 * <p>Some stacks will immediately query MODE after a status change; the reply can look
 * like a fresh MODE event containing the full channel flags (e.g. +Cnst), even though
 * nothing changed. We use this as a weak signal to suppress that echo when we don't yet
 * have channel-flag state seeded.
 */
@Component
public class RecentStatusModeState {

  private final ConcurrentHashMap<ModeKey, Long> lastStatusModeMs = new ConcurrentHashMap<>();

  public void markStatusMode(String serverId, String channel) {
    if (serverId == null || channel == null) return;
    lastStatusModeMs.put(ModeKey.of(serverId, channel), System.currentTimeMillis());
  }

  public boolean isRecent(String serverId, String channel, long withinMs) {
    if (serverId == null || channel == null) return false;
    Long ms = lastStatusModeMs.get(ModeKey.of(serverId, channel));
    if (ms == null) return false;
    return (System.currentTimeMillis() - ms.longValue()) <= withinMs;
  }

  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    lastStatusModeMs.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
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
