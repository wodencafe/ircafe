package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.QtDateTimeValue;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Session-owned matching and freshness of native SignalProxy heartbeat lag samples. */
final class QuasselCoreLagTracker {
  private static final long SAMPLE_STALE_AFTER_MS = TimeUnit.MINUTES.toMillis(2);

  private final LongSupplier nowMs;
  private final AtomicReference<QtDateTimeValue> probeToken = new AtomicReference<>();
  private final AtomicLong probeSentAtMs = new AtomicLong(0L);
  private final AtomicLong lastMeasuredMs = new AtomicLong(-1L);
  private final AtomicLong lastMeasuredAtMs = new AtomicLong(0L);

  QuasselCoreLagTracker() {
    this(System::currentTimeMillis);
  }

  QuasselCoreLagTracker(LongSupplier nowMs) {
    this.nowMs = Objects.requireNonNull(nowMs, "nowMs");
  }

  QtDateTimeValue beginProbe() {
    long sentAtMs = nowMs.getAsLong();
    QtDateTimeValue token = QuasselCoreDatastreamCodec.utcDateTimeFromEpochMs(sentAtMs);
    probeToken.set(token);
    probeSentAtMs.set(sentAtMs);
    return token;
  }

  // A failed write cancels only the pending probe; the last successful sample remains available.
  void cancelProbe() {
    probeToken.set(null);
    probeSentAtMs.set(0L);
  }

  void observeReply(List<Object> params) {
    if (params == null || params.isEmpty()) return;
    Object value = params.getFirst();
    if (!(value instanceof QtDateTimeValue timestamp)) return;
    QtDateTimeValue expected = probeToken.get();
    if (expected == null || !expected.equals(timestamp)) return;
    if (!probeToken.compareAndSet(expected, null)) return;

    long measuredAtMs = nowMs.getAsLong();
    long sentAtMs = probeSentAtMs.getAndSet(0L);
    long fallbackSentMs = QuasselCoreDatastreamCodec.epochMsFromQtDateTime(timestamp);
    long effectiveSentAt = sentAtMs > 0L ? sentAtMs : fallbackSentMs;
    long lagMs = Math.max(0L, measuredAtMs - effectiveSentAt);
    lastMeasuredMs.set(lagMs);
    lastMeasuredAtMs.set(measuredAtMs);
  }

  OptionalLong lastMeasuredLagMs() {
    long measuredAt = lastMeasuredAtMs.get();
    if (measuredAt <= 0L) return OptionalLong.empty();
    long ageMs = Math.max(0L, nowMs.getAsLong() - measuredAt);
    if (ageMs > SAMPLE_STALE_AFTER_MS) return OptionalLong.empty();
    return OptionalLong.of(Math.max(0L, lastMeasuredMs.get()));
  }

  void clear() {
    probeToken.set(null);
    probeSentAtMs.set(0L);
    lastMeasuredMs.set(-1L);
    lastMeasuredAtMs.set(0L);
  }
}
