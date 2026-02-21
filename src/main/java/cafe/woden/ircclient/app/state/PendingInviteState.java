package cafe.woden.ircclient.app.state;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Tracks pending channel invitations and collapses short repeated bursts.
 */
@Component
public class PendingInviteState {

  private static final Duration COLLAPSE_WINDOW = Duration.ofSeconds(15);
  private static final int MAX_PENDING_PER_SERVER = 40;

  public record PendingInvite(
      long id,
      Instant firstSeenAt,
      Instant lastSeenAt,
      String serverId,
      String channel,
      String inviterNick,
      String inviteeNick,
      String reason,
      boolean inviteNotify,
      int repeatCount
  ) {
    public PendingInvite {
      firstSeenAt = firstSeenAt == null ? Instant.now() : firstSeenAt;
      lastSeenAt = lastSeenAt == null ? firstSeenAt : lastSeenAt;
      serverId = normalizeToken(serverId);
      channel = normalizeToken(channel);
      inviterNick = normalizeToken(inviterNick);
      inviteeNick = normalizeToken(inviteeNick);
      reason = Objects.toString(reason, "").trim();
      if (repeatCount <= 0) repeatCount = 1;
    }
  }

  public record RecordResult(PendingInvite invite, boolean collapsed) {}

  private record InviteKey(String serverId, String channelLower, String inviterLower, String inviteeLower) {}

  private final AtomicLong nextId = new AtomicLong(0);
  private final LinkedHashMap<Long, PendingInvite> pendingById = new LinkedHashMap<>();
  private final Map<InviteKey, Long> latestIdByKey = new LinkedHashMap<>();

  private volatile boolean inviteAutoJoinEnabled;

  public PendingInviteState(RuntimeConfigStore runtimeConfig) {
    boolean enabled = false;
    try {
      if (runtimeConfig != null) {
        enabled = runtimeConfig.readInviteAutoJoinEnabled(false);
      }
    } catch (Exception ignored) {
      enabled = false;
    }
    this.inviteAutoJoinEnabled = enabled;
  }

  public synchronized RecordResult record(
      Instant at,
      String serverId,
      String channel,
      String inviterNick,
      String inviteeNick,
      String reason,
      boolean inviteNotify
  ) {
    Instant now = at == null ? Instant.now() : at;
    String sid = normalizeToken(serverId);
    String ch = normalizeToken(channel);
    String from = normalizeToken(inviterNick);
    String invitee = normalizeToken(inviteeNick);
    String rsn = Objects.toString(reason, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) {
      PendingInvite p = new PendingInvite(0, now, now, sid, ch, from, invitee, rsn, inviteNotify, 1);
      return new RecordResult(p, false);
    }

    InviteKey key = keyOf(sid, ch, from, invitee);
    Long existingId = latestIdByKey.get(key);
    if (existingId != null) {
      PendingInvite prev = pendingById.get(existingId);
      if (prev != null && isInsideCollapseWindow(prev.lastSeenAt(), now)) {
        String mergedReason = !rsn.isBlank() ? rsn : prev.reason();
        PendingInvite merged = new PendingInvite(
            prev.id(),
            prev.firstSeenAt(),
            now,
            prev.serverId(),
            prev.channel(),
            prev.inviterNick(),
            prev.inviteeNick(),
            mergedReason,
            prev.inviteNotify() || inviteNotify,
            prev.repeatCount() + 1);
        pendingById.put(merged.id(), merged);
        latestIdByKey.put(key, merged.id());
        return new RecordResult(merged, true);
      }
    }

    long id = nextId.incrementAndGet();
    PendingInvite created = new PendingInvite(id, now, now, sid, ch, from, invitee, rsn, inviteNotify, 1);
    pendingById.put(id, created);
    latestIdByKey.put(key, id);
    trimServerToLimit(sid);
    return new RecordResult(created, false);
  }

  public synchronized List<PendingInvite> listForServer(String serverId) {
    String sid = normalizeToken(serverId);
    if (sid.isEmpty()) return List.of();
    ArrayList<PendingInvite> out = new ArrayList<>();
    for (PendingInvite p : pendingById.values()) {
      if (p == null) continue;
      if (!sid.equalsIgnoreCase(p.serverId())) continue;
      out.add(p);
    }
    out.sort((a, b) -> Long.compare(b.id(), a.id()));
    return List.copyOf(out);
  }

  public synchronized List<PendingInvite> listAll() {
    ArrayList<PendingInvite> out = new ArrayList<>(pendingById.values());
    out.removeIf(Objects::isNull);
    out.sort((a, b) -> Long.compare(b.id(), a.id()));
    return List.copyOf(out);
  }

  public synchronized PendingInvite get(long inviteId) {
    return pendingById.get(inviteId);
  }

  public synchronized PendingInvite latestForServer(String serverId) {
    String sid = normalizeToken(serverId);
    if (sid.isEmpty()) return null;
    PendingInvite best = null;
    for (PendingInvite p : pendingById.values()) {
      if (p == null) continue;
      if (!sid.equalsIgnoreCase(p.serverId())) continue;
      if (best == null || p.id() > best.id()) best = p;
    }
    return best;
  }

  public synchronized PendingInvite latestAnyServer() {
    PendingInvite best = null;
    for (PendingInvite p : pendingById.values()) {
      if (p == null) continue;
      if (best == null || p.id() > best.id()) best = p;
    }
    return best;
  }

  public synchronized PendingInvite remove(long inviteId) {
    PendingInvite removed = pendingById.remove(inviteId);
    if (removed == null) return null;

    InviteKey key = keyOf(removed.serverId(), removed.channel(), removed.inviterNick(), removed.inviteeNick());
    Long latest = latestIdByKey.get(key);
    if (latest != null && latest == inviteId) {
      latestIdByKey.remove(key);
    }
    return removed;
  }

  public synchronized void clearServer(String serverId) {
    String sid = normalizeToken(serverId);
    if (sid.isEmpty()) return;

    ArrayList<Long> ids = new ArrayList<>();
    for (Map.Entry<Long, PendingInvite> e : pendingById.entrySet()) {
      PendingInvite p = e.getValue();
      if (p != null && sid.equalsIgnoreCase(p.serverId())) {
        ids.add(e.getKey());
      }
    }
    for (Long id : ids) {
      pendingById.remove(id);
    }
    latestIdByKey.entrySet().removeIf(e -> sid.equalsIgnoreCase(e.getKey().serverId()));
  }

  public synchronized void clearAll() {
    pendingById.clear();
    latestIdByKey.clear();
  }

  public boolean inviteAutoJoinEnabled() {
    return inviteAutoJoinEnabled;
  }

  public void setInviteAutoJoinEnabled(boolean enabled) {
    inviteAutoJoinEnabled = enabled;
  }

  private void trimServerToLimit(String serverId) {
    if (serverId == null || serverId.isBlank()) return;
    while (countForServer(serverId) > MAX_PENDING_PER_SERVER) {
      Long oldest = oldestIdForServer(serverId);
      if (oldest == null) break;
      remove(oldest);
    }
  }

  private int countForServer(String serverId) {
    int count = 0;
    for (PendingInvite p : pendingById.values()) {
      if (p == null) continue;
      if (serverId.equalsIgnoreCase(p.serverId())) count++;
    }
    return count;
  }

  private Long oldestIdForServer(String serverId) {
    for (Map.Entry<Long, PendingInvite> e : pendingById.entrySet()) {
      PendingInvite p = e.getValue();
      if (p == null) continue;
      if (serverId.equalsIgnoreCase(p.serverId())) return e.getKey();
    }
    return null;
  }

  private static boolean isInsideCollapseWindow(Instant then, Instant now) {
    if (then == null || now == null) return false;
    long delta = Math.abs(Duration.between(then, now).toMillis());
    return delta <= COLLAPSE_WINDOW.toMillis();
  }

  private static String normalizeToken(String value) {
    return Objects.toString(value, "").trim();
  }

  private static InviteKey keyOf(String serverId, String channel, String inviterNick, String inviteeNick) {
    return new InviteKey(
        normalizeToken(serverId).toLowerCase(Locale.ROOT),
        normalizeToken(channel).toLowerCase(Locale.ROOT),
        normalizeToken(inviterNick).toLowerCase(Locale.ROOT),
        normalizeToken(inviteeNick).toLowerCase(Locale.ROOT));
  }
}
