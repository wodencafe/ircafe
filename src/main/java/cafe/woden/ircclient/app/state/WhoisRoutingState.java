package cafe.woden.ircclient.app.state;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks pending WHOIS requests so inbound WHOIS numerics can be routed to the tab where the
 * request originated.
 *
 * <p>This is intentionally a small state-holder to keep {@code IrcMediator} from accumulating more
 * correlation maps.
 */
@Component
public class WhoisRoutingState {

  private final ConcurrentHashMap<WhoisKey, TargetRef> pendingWhoisTargets =
      new ConcurrentHashMap<>();

  /** Record a pending WHOIS for {@code nick}, routing the reply to {@code target}. */
  public void put(String serverId, String nick, TargetRef target) {
    if (target == null) return;
    pendingWhoisTargets.put(new WhoisKey(serverId, nick), target);
  }

  /** Remove and return the routing target for {@code nick}, or {@code null} if none. */
  public TargetRef remove(String serverId, String nick) {
    return pendingWhoisTargets.remove(new WhoisKey(serverId, nick));
  }

  /** Peek the routing target for {@code nick}, or {@code null} if none. */
  public TargetRef get(String serverId, String nick) {
    return pendingWhoisTargets.get(new WhoisKey(serverId, nick));
  }

  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    pendingWhoisTargets.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
  }

  private static String normalizeServer(String serverId) {
    return (serverId == null) ? "" : serverId.trim();
  }

  private record WhoisKey(String serverId, String nickLower) {
    WhoisKey {
      serverId = normalizeServer(serverId);
      nickLower = (nickLower == null) ? "" : nickLower.trim().toLowerCase(Locale.ROOT);
    }
  }
}
