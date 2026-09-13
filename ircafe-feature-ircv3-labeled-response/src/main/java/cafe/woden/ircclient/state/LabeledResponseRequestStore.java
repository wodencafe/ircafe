package cafe.woden.ircclient.state;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-scoped IRCv3 labeled-response tracking, independent of transports and chat buffers. The
 * caller owns the routing context and drives timeout collection and disconnect cleanup. Reads
 * retain entries for multi-line replies; stale retention follows request start times.
 *
 * @param <T> opaque routing context retained until the request is pruned or cleared
 */
public final class LabeledResponseRequestStore<T> {
  private static final Duration STALE_RETENTION = Duration.ofMinutes(10);

  private final ConcurrentHashMap<LabelKey, Request<T>> pendingByLabel = new ConcurrentHashMap<>();

  private final Clock clock;

  public LabeledResponseRequestStore() {
    this(Clock.systemUTC());
  }

  LabeledResponseRequestStore(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Remember a request, replacing any previous entry for the same server and label. */
  public void remember(String serverId, String label, T context, Instant startedAt) {
    String sid = normalizeServer(serverId);
    String lbl = normalizeLabel(label);
    if (sid.isEmpty() || lbl.isEmpty() || context == null) return;

    Instant at = (startedAt == null) ? clock.instant() : startedAt;
    pruneStaleEntriesForServer(sid, at.minus(STALE_RETENTION));

    pendingByLabel.put(new LabelKey(sid, lbl), new Request<>(context, at, Outcome.PENDING, null));
  }

  /**
   * Lookup correlation data for a labeled response if it is still fresh.
   *
   * <p>Entries are not removed on read so multi-line responses with the same label continue to
   * correlate for the lifetime window.
   */
  public Request<T> findIfFresh(String serverId, String label, Duration maxAge) {
    String sid = normalizeServer(serverId);
    String lbl = normalizeLabel(label);
    if (sid.isEmpty() || lbl.isEmpty()) return null;

    LabelKey key = new LabelKey(sid, lbl);
    Request<T> entry = pendingByLabel.get(key);
    if (entry == null) return null;

    Duration age = (maxAge == null || maxAge.isNegative()) ? Duration.ZERO : maxAge;
    if (!age.isZero()) {
      Instant cutoff = clock.instant().minus(age);
      Instant started = (entry.startedAt() == null) ? Instant.EPOCH : entry.startedAt();
      if (started.isBefore(cutoff)) {
        pendingByLabel.remove(key, entry);
        return null;
      }
    }
    return entry;
  }

  /**
   * Marks a pending labeled request as completed (success/failure).
   *
   * @return updated entry only when this call changed state from pending to terminal.
   */
  public Request<T> markOutcomeIfPending(
      String serverId, String label, Outcome outcome, Instant at) {
    String sid = normalizeServer(serverId);
    String lbl = normalizeLabel(label);
    if (sid.isEmpty() || lbl.isEmpty()) return null;
    Outcome next = (outcome == null) ? Outcome.PENDING : outcome;
    if (next == Outcome.PENDING) return null;

    LabelKey key = new LabelKey(sid, lbl);
    java.util.concurrent.atomic.AtomicReference<Request<T>> transitioned =
        new java.util.concurrent.atomic.AtomicReference<>();

    pendingByLabel.computeIfPresent(
        key,
        (k, cur) -> {
          if (cur == null) return null;
          Outcome current = cur.outcome();
          boolean shouldTransition;
          if (current == Outcome.PENDING) {
            shouldTransition = true;
          } else {
            shouldTransition = (next == Outcome.FAILURE && current != Outcome.FAILURE);
          }
          if (!shouldTransition) return cur;
          Request<T> updated =
              new Request<>(
                  cur.context(), cur.startedAt(), next, at == null ? clock.instant() : at);
          transitioned.set(updated);
          return updated;
        });
    return transitioned.get();
  }

  /**
   * Collect and mark pending requests that timed out.
   *
   * <p>Returned entries are transitioned to {@link Outcome#TIMEOUT}; they remain in the map for
   * short-term correlation visibility until stale retention prunes them.
   */
  public java.util.List<TimedOutRequest<T>> collectTimedOut(Duration timeout, int maxCount) {
    Duration to =
        (timeout == null || timeout.isNegative() || timeout.isZero())
            ? Duration.ofSeconds(30)
            : timeout;
    int cap = Math.max(1, maxCount);
    Instant now = clock.instant();
    Instant cutoff = now.minus(to);
    java.util.ArrayList<TimedOutRequest<T>> out = new java.util.ArrayList<>();

    for (Map.Entry<LabelKey, Request<T>> e : pendingByLabel.entrySet()) {
      if (out.size() >= cap) break;
      LabelKey key = e.getKey();
      Request<T> cur = e.getValue();
      if (key == null || cur == null) continue;
      if (cur.terminal()) continue;
      Instant started = (cur.startedAt() == null) ? Instant.EPOCH : cur.startedAt();
      if (!started.isBefore(cutoff)) continue;

      Request<T> marked = markOutcomeIfPending(key.serverId, key.label, Outcome.TIMEOUT, now);
      if (marked != null) {
        out.add(new TimedOutRequest<>(key.serverId, key.label, marked, now));
      }
    }

    if (!out.isEmpty()) {
      pruneStaleEntriesForServer("", now.minus(STALE_RETENTION));
    }
    return out;
  }

  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    if (sid.isEmpty()) return;
    pendingByLabel.keySet().removeIf(k -> Objects.equals(k.serverId, sid));
  }

  private void pruneStaleEntriesForServer(String serverId, Instant cutoff) {
    if (cutoff == null) return;
    pendingByLabel
        .entrySet()
        .removeIf(
            e -> {
              if (serverId != null
                  && !serverId.isBlank()
                  && !Objects.equals(e.getKey().serverId, serverId)) return false;
              Instant started = (e.getValue() == null) ? null : e.getValue().startedAt();
              return started == null || started.isBefore(cutoff);
            });
  }

  private static String normalizeServer(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeLabel(String label) {
    return Objects.toString(label, "").trim();
  }

  public enum Outcome {
    PENDING,
    SUCCESS,
    FAILURE,
    TIMEOUT
  }

  public record Request<T>(T context, Instant startedAt, Outcome outcome, Instant outcomeAt) {
    public boolean terminal() {
      return outcome != Outcome.PENDING;
    }
  }

  public record TimedOutRequest<T>(
      String serverId, String label, Request<T> request, Instant timedOutAt) {}

  private record LabelKey(String serverId, String label) {
    LabelKey {
      serverId = normalizeServer(serverId);
      label = normalizeLabel(label);
    }
  }
}
