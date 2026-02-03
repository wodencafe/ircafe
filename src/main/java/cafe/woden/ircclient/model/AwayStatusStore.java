package cafe.woden.ircclient.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory cache of user away state, keyed by server + lowercase nick. */
@Component
public class AwayStatusStore {

  /** Snapshot of a user's away state as last observed. */
  public record AwayStatus(boolean isAway, String message, Instant at) {
    public AwayStatus {
      message = Objects.toString(message, "").trim();
      at = at == null ? Instant.EPOCH : at;
    }
  }

  private final Map<String, Map<String, AwayStatus>> awayByServerAndNickLower = new ConcurrentHashMap<>();

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }

  private static String nickKey(String nick) {
    String n = norm(nick);
    return n.isEmpty() ? "" : n.toLowerCase(Locale.ROOT);
  }

  public Optional<AwayStatus> get(String serverId, String nick) {
    String sid = norm(serverId);
    String nk = nickKey(nick);
    if (sid.isEmpty() || nk.isEmpty()) return Optional.empty();

    Map<String, AwayStatus> byNick = awayByServerAndNickLower.get(sid);
    if (byNick == null) return Optional.empty();
    return Optional.ofNullable(byNick.get(nk));
  }

  public boolean isAway(String serverId, String nick) {
    return get(serverId, nick).map(AwayStatus::isAway).orElse(false);
  }

  /**
   * Store an observed away state.
   *
   * @return true if the cached value changed.
   */
  public boolean put(String serverId, String nick, boolean isAway, String message, Instant at) {
    String sid = norm(serverId);
    String nk = nickKey(nick);
    if (sid.isEmpty() || nk.isEmpty()) return false;

    AwayStatus next = new AwayStatus(isAway, message, at);

    Map<String, AwayStatus> byNick = awayByServerAndNickLower.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
    AwayStatus prev = byNick.put(nk, next);
    return !Objects.equals(prev, next);
  }

  /** Remove a cached entry, if present. */
  public boolean clear(String serverId, String nick) {
    String sid = norm(serverId);
    String nk = nickKey(nick);
    if (sid.isEmpty() || nk.isEmpty()) return false;

    Map<String, AwayStatus> byNick = awayByServerAndNickLower.get(sid);
    if (byNick == null) return false;
    return byNick.remove(nk) != null;
  }

  public void clearServer(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    awayByServerAndNickLower.remove(sid);
  }
}
