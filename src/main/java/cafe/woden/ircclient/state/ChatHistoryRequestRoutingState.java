package cafe.woden.ircclient.state;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Tracks explicit user-triggered CHATHISTORY requests so arriving batches can be rendered live. */
@Component
@ApplicationLayer
public class ChatHistoryRequestRoutingState implements ChatHistoryRequestRoutingPort {

  private final ConcurrentHashMap<RequestKey, ChatHistoryRequestRoutingPort.PendingRequest>
      pendingByTarget = new ConcurrentHashMap<>();

  public void remember(
      String serverId,
      String target,
      TargetRef originTarget,
      int limit,
      String selector,
      Instant requestedAt) {
    remember(
        serverId,
        target,
        originTarget,
        limit,
        selector,
        requestedAt,
        ChatHistoryRequestRoutingPort.QueryMode.BEFORE);
  }

  public void remember(
      String serverId,
      String target,
      TargetRef originTarget,
      int limit,
      String selector,
      Instant requestedAt,
      ChatHistoryRequestRoutingPort.QueryMode queryMode) {
    String sid = normalizeServer(serverId);
    String tgt = normalizeTarget(target);
    if (sid.isEmpty() || tgt.isEmpty() || originTarget == null) return;
    if (!sid.equals(normalizeServer(originTarget.serverId()))) return;

    pendingByTarget.put(
        new RequestKey(sid, tgt),
        new ChatHistoryRequestRoutingPort.PendingRequest(
            originTarget, requestedAt, limit, selector, queryMode));
  }

  /**
   * Consume a recent pending request for this target.
   *
   * <p>Returns {@code null} when none exists or it is stale.
   */
  public ChatHistoryRequestRoutingPort.PendingRequest consumeIfFresh(
      String serverId, String target, Duration maxAge) {
    RequestKey key = new RequestKey(serverId, target);
    ChatHistoryRequestRoutingPort.PendingRequest pending = pendingByTarget.remove(key);
    if (pending == null) return null;

    Duration age = (maxAge == null || maxAge.isNegative()) ? Duration.ZERO : maxAge;
    if (!age.isZero()) {
      Instant cutoff = Instant.now().minus(age);
      Instant at = pending.requestedAt() == null ? Instant.EPOCH : pending.requestedAt();
      if (at.isBefore(cutoff)) return null;
    }
    return pending;
  }

  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    if (sid.isEmpty()) return;
    pendingByTarget.keySet().removeIf(k -> sid.equals(k.serverId));
  }

  private static String normalizeServer(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeTarget(String target) {
    return Objects.toString(target, "").trim().toLowerCase(Locale.ROOT);
  }

  private record RequestKey(String serverId, String targetLower) {
    RequestKey {
      serverId = normalizeServer(serverId);
      targetLower = normalizeTarget(targetLower);
    }
  }
}
