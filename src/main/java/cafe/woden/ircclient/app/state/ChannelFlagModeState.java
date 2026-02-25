package cafe.woden.ircclient.app.state;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks arg-less channel flag modes (e.g. +nt, +s, +C) per server/channel.
 *
 * <p>Used to suppress "no-op" MODE echoes where the server repeats the already-known channel flags
 * (common after status-mode changes like +v/+o).
 */
@Component
public class ChannelFlagModeState {

  private final ConcurrentHashMap<ModeKey, Set<Character>> channelFlags = new ConcurrentHashMap<>();

  /**
   * Apply a mode delta like "+nt" or "-s" (no args / no spaces).
   *
   * @return true if the tracked flag state changed; false if it was a no-op.
   */
  public boolean applyDelta(String serverId, String channel, String details) {
    if (serverId == null || channel == null || details == null) return false;

    String d = details.trim();
    if (d.isEmpty()) return false;

    // Only track arg-less flag MODEs. If there are args, ignore here.
    if (d.indexOf(' ') >= 0) return false;

    ModeKey key = ModeKey.of(serverId, channel);
    Set<Character> flags = channelFlags.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());

    boolean changed = false;
    char sign = '+';
    for (int i = 0; i < d.length(); i++) {
      char c = d.charAt(i);
      if (c == '+' || c == '-') {
        sign = c;
        continue;
      }
      if (!Character.isLetterOrDigit(c)) continue;

      if (sign == '+') {
        if (flags.add(c)) changed = true;
      } else {
        if (flags.remove(c)) changed = true;
      }
    }

    return changed;
  }

  public boolean hasAnyState(String serverId, String channel) {
    if (serverId == null || channel == null) return false;
    Set<Character> s = channelFlags.get(ModeKey.of(serverId, channel));
    return s != null && !s.isEmpty();
  }

  /** Returns a normalized {@code +modes} summary (for example {@code +Cnst}), or empty. */
  public String snapshotModeSummary(String serverId, String channel) {
    if (serverId == null || channel == null) return "";
    Set<Character> flags = channelFlags.get(ModeKey.of(serverId, channel));
    if (flags == null || flags.isEmpty()) return "";

    java.util.ArrayList<Character> sorted = new java.util.ArrayList<>(flags);
    java.util.Collections.sort(sorted);
    StringBuilder out = new StringBuilder(sorted.size() + 1);
    out.append('+');
    for (Character flag : sorted) {
      if (flag == null) continue;
      out.append(flag.charValue());
    }
    return out.length() <= 1 ? "" : out.toString();
  }

  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    channelFlags.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
  }

  public void clearChannel(String serverId, String channel) {
    if (serverId == null || channel == null) return;
    channelFlags.remove(ModeKey.of(serverId, channel));
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
