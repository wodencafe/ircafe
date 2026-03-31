package cafe.woden.ircclient.irc.pircbotx.state;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Tracks active lag probes and recent lag samples for a single connection. */
final class PircbotxLagProbeState {
  private static final long LAG_SAMPLE_STALE_AFTER_MS = 120_000L;
  private static final long MAX_PASSIVE_LAG_SAMPLE_MS = TimeUnit.MINUTES.toMillis(5);

  private final AtomicReference<String> probeToken = new AtomicReference<>("");
  private final AtomicLong probeSentAtMs = new AtomicLong(0L);
  private final AtomicLong lastMeasuredMs = new AtomicLong(-1L);
  private final AtomicLong lastMeasuredAtMs = new AtomicLong(0L);

  void beginProbe(String token, long sentAtMs) {
    String normalizedToken = normalizeToken(token);
    if (normalizedToken.isEmpty()) {
      return;
    }
    long sent = sentAtMs > 0 ? sentAtMs : System.currentTimeMillis();
    probeToken.set(normalizedToken);
    probeSentAtMs.set(sent);
  }

  boolean observePong(String token, long observedAtMs) {
    String observedToken = normalizeToken(token);
    if (observedToken.isEmpty()) {
      return false;
    }
    String expected = probeToken.get();
    if (expected.isEmpty()) {
      return false;
    }
    if (!Objects.equals(expected, observedToken)) {
      return false;
    }
    if (!probeToken.compareAndSet(expected, "")) {
      return false;
    }

    long sentAt = probeSentAtMs.getAndSet(0L);
    long now = observedAtMs > 0 ? observedAtMs : System.currentTimeMillis();
    long lagMs = Math.max(0L, now - sentAt);
    lastMeasuredMs.set(lagMs);
    lastMeasuredAtMs.set(now);
    return true;
  }

  void observePassiveSample(long lagMs, long observedAtMs) {
    long sample = Math.max(0L, lagMs);
    if (sample > MAX_PASSIVE_LAG_SAMPLE_MS) {
      return;
    }
    long now = observedAtMs > 0 ? observedAtMs : System.currentTimeMillis();
    lastMeasuredMs.set(sample);
    lastMeasuredAtMs.set(now);
  }

  long lagMsIfFresh(long nowMs) {
    long measuredAt = lastMeasuredAtMs.get();
    if (measuredAt <= 0) {
      return -1L;
    }
    long now = nowMs > 0 ? nowMs : System.currentTimeMillis();
    if (now - measuredAt > LAG_SAMPLE_STALE_AFTER_MS) {
      return -1L;
    }
    return Math.max(-1L, lastMeasuredMs.get());
  }

  void reset() {
    probeToken.set("");
    probeSentAtMs.set(0L);
    lastMeasuredMs.set(-1L);
    lastMeasuredAtMs.set(0L);
  }

  String currentProbeToken() {
    return probeToken.get();
  }

  long currentProbeSentAtMs() {
    return probeSentAtMs.get();
  }

  long currentMeasuredLagMs() {
    return lastMeasuredMs.get();
  }

  long currentMeasuredAtMs() {
    return lastMeasuredAtMs.get();
  }

  private static String normalizeToken(String raw) {
    return Objects.toString(raw, "").trim();
  }
}
