package cafe.woden.ircclient.app.state;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

@Component
@ApplicationLayer
public class CtcpRoutingState {

  private final ConcurrentHashMap<CtcpKey, PendingCtcp> pending = new ConcurrentHashMap<>();

  /**
   * Record a pending CTCP for {@code nick}/{@code command}, routing the reply to {@code target}.
   */
  public void put(String serverId, String nick, String command, String token, TargetRef target) {
    if (target == null) return;
    pending.put(
        new CtcpKey(serverId, nick, command, token),
        new PendingCtcp(target, System.currentTimeMillis()));
  }

  /** Remove and return the pending CTCP routing state, or {@code null} if none. */
  public PendingCtcp remove(String serverId, String nick, String command, String token) {
    return pending.remove(new CtcpKey(serverId, nick, command, token));
  }

  /** Peek the pending CTCP routing state, or {@code null} if none. */
  public PendingCtcp get(String serverId, String nick, String command, String token) {
    return pending.get(new CtcpKey(serverId, nick, command, token));
  }

  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    pending.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
  }

  public record PendingCtcp(TargetRef target, long startedMs) {}

  private static String normalizeServer(String serverId) {
    return (serverId == null) ? "" : serverId.trim();
  }

  private record CtcpKey(String serverId, String nickLower, String commandUpper, String token) {
    CtcpKey {
      serverId = normalizeServer(serverId);
      nickLower = (nickLower == null) ? "" : nickLower.trim().toLowerCase(Locale.ROOT);
      commandUpper = (commandUpper == null) ? "" : commandUpper.trim().toUpperCase(Locale.ROOT);
      token = (token == null) ? null : token.trim();
    }
  }
}
