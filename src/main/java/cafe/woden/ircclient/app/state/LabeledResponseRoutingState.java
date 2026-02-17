package cafe.woden.ircclient.app.state;

import cafe.woden.ircclient.app.TargetRef;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Tracks outbound IRCv3 {@code label=} correlation so numeric/server responses can be routed
 * back to the originating chat buffer.
 */
@Component
public class LabeledResponseRoutingState {
  private static final Duration STALE_RETENTION = Duration.ofMinutes(10);

  public enum Outcome {
    PENDING,
    SUCCESS,
    FAILURE,
    TIMEOUT
  }

  public record PreparedRawLine(String line, String label, boolean injected) {
    public PreparedRawLine {
      line = Objects.toString(line, "").trim();
      label = normalizeLabel(label);
    }
  }

  public record PendingLabeledRequest(
      TargetRef originTarget,
      String requestPreview,
      Instant startedAt,
      Outcome outcome,
      Instant outcomeAt
  ) {
    public PendingLabeledRequest {
      requestPreview = normalizePreview(requestPreview);
      startedAt = (startedAt == null) ? Instant.now() : startedAt;
      outcome = (outcome == null) ? Outcome.PENDING : outcome;
      if (outcome == Outcome.PENDING) outcomeAt = null;
      else outcomeAt = (outcomeAt == null) ? Instant.now() : outcomeAt;
    }

    public PendingLabeledRequest(TargetRef originTarget, String requestPreview, Instant startedAt) {
      this(originTarget, requestPreview, startedAt, Outcome.PENDING, null);
    }

    public boolean terminal() {
      return outcome != Outcome.PENDING;
    }

    public PendingLabeledRequest withOutcome(Outcome nextOutcome, Instant at) {
      Outcome normalized = (nextOutcome == null) ? Outcome.PENDING : nextOutcome;
      if (normalized == Outcome.PENDING) return this;
      Instant ts = (at == null) ? Instant.now() : at;
      return new PendingLabeledRequest(originTarget, requestPreview, startedAt, normalized, ts);
    }
  }

  public record TimedOutLabeledRequest(
      String serverId,
      String label,
      PendingLabeledRequest request,
      Instant timedOutAt
  ) {
    public TimedOutLabeledRequest {
      serverId = normalizeServer(serverId);
      label = normalizeLabel(label);
      timedOutAt = (timedOutAt == null) ? Instant.now() : timedOutAt;
    }
  }

  private final ConcurrentHashMap<LabelKey, PendingLabeledRequest> pendingByLabel =
      new ConcurrentHashMap<>();

  private final AtomicLong labelSequence = new AtomicLong(System.currentTimeMillis());

  /**
   * Ensure an outbound raw IRC line has a {@code label=} tag.
   *
   * <p>If the line already has a label tag, that label is preserved and returned.
   */
  public PreparedRawLine prepareOutgoingRaw(String serverId, String rawLine) {
    String line = Objects.toString(rawLine, "").trim();
    if (line.isEmpty()) return new PreparedRawLine("", "", false);

    if (line.charAt(0) != '@') {
      String label = nextClientLabel(serverId);
      return new PreparedRawLine("@label=" + escapeTagValue(label) + " " + line, label, true);
    }

    int sp = line.indexOf(' ');
    if (sp <= 1) return new PreparedRawLine(line, "", false);

    String tagSection = line.substring(1, sp);
    String existingLabel = extractLabelFromTagSection(tagSection);
    if (!existingLabel.isEmpty()) return new PreparedRawLine(line, existingLabel, false);

    String label = nextClientLabel(serverId);
    String withLabel = "@" + tagSection + ";label=" + escapeTagValue(label) + line.substring(sp);
    return new PreparedRawLine(withLabel, label, true);
  }

  /** Build a client-generated label token for this connection/server. */
  public String nextClientLabel(String serverId) {
    String sid = normalizeServer(serverId).toLowerCase(Locale.ROOT);
    StringBuilder compact = new StringBuilder(Math.max(4, sid.length()));
    for (int i = 0; i < sid.length(); i++) {
      char c = sid.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
        compact.append(c);
      }
    }
    if (compact.length() == 0) compact.append("srv");
    long seq = labelSequence.incrementAndGet();
    return "ircafe-" + compact + "-" + Long.toString(seq, 36);
  }

  /** Remember a labeled request so responses tagged with the same label can be routed back. */
  public void remember(
      String serverId,
      String label,
      TargetRef originTarget,
      String requestPreview,
      Instant startedAt
  ) {
    String sid = normalizeServer(serverId);
    if (sid.isEmpty() && originTarget != null) {
      sid = normalizeServer(originTarget.serverId());
    }
    String lbl = normalizeLabel(label);
    if (sid.isEmpty() || lbl.isEmpty() || originTarget == null) return;

    TargetRef normalizedTarget = normalizeTargetForServer(originTarget, sid);
    if (normalizedTarget == null) return;

    Instant at = (startedAt == null) ? Instant.now() : startedAt;
    pruneStaleEntriesForServer(sid, at.minus(STALE_RETENTION));

    pendingByLabel.put(
        new LabelKey(sid, lbl),
        new PendingLabeledRequest(normalizedTarget, requestPreview, at));
  }

  /**
   * Lookup correlation data for a labeled response if it is still fresh.
   *
   * <p>Entries are not removed on read so multi-line responses with the same label continue to
   * correlate for the lifetime window.
   */
  public PendingLabeledRequest findIfFresh(String serverId, String label, Duration maxAge) {
    String sid = normalizeServer(serverId);
    String lbl = normalizeLabel(label);
    if (sid.isEmpty() || lbl.isEmpty()) return null;

    LabelKey key = new LabelKey(sid, lbl);
    PendingLabeledRequest entry = pendingByLabel.get(key);
    if (entry == null) return null;

    Duration age = (maxAge == null || maxAge.isNegative()) ? Duration.ZERO : maxAge;
    if (!age.isZero()) {
      Instant cutoff = Instant.now().minus(age);
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
  public PendingLabeledRequest markOutcomeIfPending(
      String serverId,
      String label,
      Outcome outcome,
      Instant at
  ) {
    String sid = normalizeServer(serverId);
    String lbl = normalizeLabel(label);
    if (sid.isEmpty() || lbl.isEmpty()) return null;
    Outcome next = (outcome == null) ? Outcome.PENDING : outcome;
    if (next == Outcome.PENDING) return null;

    LabelKey key = new LabelKey(sid, lbl);
    java.util.concurrent.atomic.AtomicReference<PendingLabeledRequest> transitioned =
        new java.util.concurrent.atomic.AtomicReference<>();

    pendingByLabel.computeIfPresent(key, (k, cur) -> {
      if (cur == null) return null;
      Outcome current = cur.outcome();
      boolean shouldTransition;
      if (current == Outcome.PENDING) {
        shouldTransition = true;
      } else {
        shouldTransition = (next == Outcome.FAILURE && current != Outcome.FAILURE);
      }
      if (!shouldTransition) return cur;
      PendingLabeledRequest updated = cur.withOutcome(next, at);
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
  public java.util.List<TimedOutLabeledRequest> collectTimedOut(Duration timeout, int maxCount) {
    Duration to = (timeout == null || timeout.isNegative() || timeout.isZero())
        ? Duration.ofSeconds(30)
        : timeout;
    int cap = Math.max(1, maxCount);
    Instant now = Instant.now();
    Instant cutoff = now.minus(to);
    java.util.ArrayList<TimedOutLabeledRequest> out = new java.util.ArrayList<>();

    for (Map.Entry<LabelKey, PendingLabeledRequest> e : pendingByLabel.entrySet()) {
      if (out.size() >= cap) break;
      LabelKey key = e.getKey();
      PendingLabeledRequest cur = e.getValue();
      if (key == null || cur == null) continue;
      if (cur.terminal()) continue;
      Instant started = (cur.startedAt() == null) ? Instant.EPOCH : cur.startedAt();
      if (!started.isBefore(cutoff)) continue;

      PendingLabeledRequest marked = markOutcomeIfPending(key.serverId, key.label, Outcome.TIMEOUT, now);
      if (marked != null) {
        out.add(new TimedOutLabeledRequest(key.serverId, key.label, marked, now));
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
    pendingByLabel.entrySet().removeIf(e -> {
      if (serverId != null && !serverId.isBlank() && !Objects.equals(e.getKey().serverId, serverId)) return false;
      Instant started = (e.getValue() == null) ? null : e.getValue().startedAt();
      return started == null || started.isBefore(cutoff);
    });
  }

  private static TargetRef normalizeTargetForServer(TargetRef target, String serverId) {
    if (target == null) return null;
    String sid = normalizeServer(serverId);
    if (sid.isEmpty()) return null;
    if (sid.equals(normalizeServer(target.serverId()))) return target;
    return new TargetRef(sid, target.target());
  }

  private static String extractLabelFromTagSection(String tagSection) {
    String section = Objects.toString(tagSection, "");
    if (section.isEmpty()) return "";

    int idx = 0;
    while (idx < section.length()) {
      int next = section.indexOf(';', idx);
      if (next < 0) next = section.length();
      String part = section.substring(idx, next);
      idx = next + 1;

      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      String rawKey = (eq >= 0) ? part.substring(0, eq) : part;
      String key = normalizeTagKey(rawKey);
      if (!"label".equals(key)) continue;

      String rawValue = (eq >= 0) ? part.substring(eq + 1) : "";
      return normalizeLabel(unescapeTagValue(rawValue));
    }
    return "";
  }

  private static String normalizeTagKey(String rawKey) {
    String k = Objects.toString(rawKey, "").trim();
    while (!k.isEmpty() && (k.charAt(0) == '@' || k.charAt(0) == '+')) {
      k = k.substring(1).trim();
    }
    return k.toLowerCase(Locale.ROOT);
  }

  private static String normalizeServer(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeLabel(String label) {
    return Objects.toString(label, "").trim();
  }

  private static String normalizePreview(String preview) {
    String p = Objects.toString(preview, "").trim();
    if (p.isEmpty()) return "";
    p = p.replaceAll("\\s+", " ");
    int max = 220;
    if (p.length() > max) {
      p = p.substring(0, max - 1) + "...";
    }
    return p;
  }

  private static String unescapeTagValue(String raw) {
    if (raw == null || raw.isEmpty() || raw.indexOf('\\') < 0) return raw == null ? "" : raw;
    StringBuilder sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c != '\\') {
        sb.append(c);
        continue;
      }
      if (i + 1 >= raw.length()) break;
      char n = raw.charAt(++i);
      switch (n) {
        case ':' -> sb.append(';');
        case 's' -> sb.append(' ');
        case 'r' -> sb.append('\r');
        case 'n' -> sb.append('\n');
        case '\\' -> sb.append('\\');
        default -> sb.append(n);
      }
    }
    return sb.toString();
  }

  private static String escapeTagValue(String raw) {
    String v = Objects.toString(raw, "");
    if (v.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(v.length() + 8);
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      switch (c) {
        case ';' -> sb.append("\\:");
        case ' ' -> sb.append("\\s");
        case '\r' -> sb.append("\\r");
        case '\n' -> sb.append("\\n");
        case '\\' -> sb.append("\\\\");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  private record LabelKey(String serverId, String label) {
    LabelKey {
      serverId = normalizeServer(serverId);
      label = normalizeLabel(label);
    }
  }
}
