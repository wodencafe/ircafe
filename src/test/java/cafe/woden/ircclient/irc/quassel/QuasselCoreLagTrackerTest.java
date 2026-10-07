package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.QtDateTimeValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class QuasselCoreLagTrackerTest {
  private static final long START_MS = 1_791_370_800_123L;
  private final AtomicLong nowMs = new AtomicLong(START_MS);
  private final QuasselCoreLagTracker tracker = new QuasselCoreLagTracker(nowMs::get);

  @Test
  void sampleIsAbsentUntilTheRequestedTokenIsObserved() {
    assertEquals(OptionalLong.empty(), tracker.lastMeasuredLagMs());
    tracker.observeReply(List.of(token(START_MS)));
    assertEquals(OptionalLong.empty(), tracker.lastMeasuredLagMs());
    tracker.beginProbe();
    assertEquals(OptionalLong.empty(), tracker.lastMeasuredLagMs());
  }

  @Test
  void matchingTokenByValueRecordsElapsedMilliseconds() {
    QtDateTimeValue requested = tracker.beginProbe();
    assertEquals(token(START_MS), requested);
    nowMs.addAndGet(42L);
    tracker.observeReply(List.of(token(START_MS)));
    assertEquals(OptionalLong.of(42L), tracker.lastMeasuredLagMs());
  }

  @Test
  void malformedAndMismatchedRepliesDoNotConsumePendingProbe() {
    QtDateTimeValue requested = tracker.beginProbe();
    tracker.observeReply(null);
    tracker.observeReply(List.of());
    tracker.observeReply(Arrays.asList((Object) null));
    tracker.observeReply(List.of("invalid"));
    tracker.observeReply(List.of("invalid", requested));
    tracker.observeReply(List.of(token(START_MS - 1L)));
    assertEquals(OptionalLong.empty(), tracker.lastMeasuredLagMs());
    nowMs.addAndGet(10L);
    tracker.observeReply(List.of(requested, "ignored"));
    assertEquals(OptionalLong.of(10L), tracker.lastMeasuredLagMs());
  }

  @Test
  void duplicateReplyDoesNotChangeSampleOrRefreshItsAge() {
    QtDateTimeValue requested = tracker.beginProbe();
    nowMs.addAndGet(42L);
    tracker.observeReply(List.of(requested));
    nowMs.addAndGet(TimeUnit.MINUTES.toMillis(2) + 1L);
    tracker.observeReply(List.of(requested));
    assertEquals(OptionalLong.empty(), tracker.lastMeasuredLagMs());
  }

  @Test
  void newProbeReplacesPendingTokenWhilePreviousSampleRemainsAvailable() {
    measure(10L);
    QtDateTimeValue replaced = tracker.beginProbe();
    nowMs.addAndGet(1L);
    QtDateTimeValue current = tracker.beginProbe();
    nowMs.addAndGet(20L);
    tracker.observeReply(List.of(replaced));
    assertEquals(OptionalLong.of(10L), tracker.lastMeasuredLagMs());
    tracker.observeReply(List.of(current));
    assertEquals(OptionalLong.of(20L), tracker.lastMeasuredLagMs());
  }

  @Test
  void canceledProbeCannotOverwriteLastSuccessfulSample() {
    measure(10L);
    QtDateTimeValue canceled = tracker.beginProbe();
    tracker.cancelProbe();
    nowMs.addAndGet(50L);
    tracker.observeReply(List.of(canceled));
    assertEquals(OptionalLong.of(10L), tracker.lastMeasuredLagMs());
    measure(20L);
    assertEquals(OptionalLong.of(20L), tracker.lastMeasuredLagMs());
  }

  @Test
  void clearingSessionErasesSampleAndPendingProbe() {
    measure(10L);
    QtDateTimeValue pending = tracker.beginProbe();
    tracker.clear();
    nowMs.addAndGet(50L);
    tracker.observeReply(List.of(pending));
    assertEquals(OptionalLong.empty(), tracker.lastMeasuredLagMs());
    measure(20L);
    assertEquals(OptionalLong.of(20L), tracker.lastMeasuredLagMs());
  }

  @Test
  void freshnessIncludesTwoMinuteBoundaryAndExpiresOneMillisecondLater() {
    measure(42L);
    nowMs.addAndGet(TimeUnit.MINUTES.toMillis(2));
    assertEquals(OptionalLong.of(42L), tracker.lastMeasuredLagMs());
    nowMs.incrementAndGet();
    assertEquals(OptionalLong.empty(), tracker.lastMeasuredLagMs());
  }

  @Test
  void backwardClockAdjustmentClampsNegativeRttAndSampleAge() {
    QtDateTimeValue requested = tracker.beginProbe();
    nowMs.addAndGet(-100L);
    tracker.observeReply(List.of(requested));
    nowMs.addAndGet(-100L);
    assertEquals(OptionalLong.of(0L), tracker.lastMeasuredLagMs());
  }

  @Test
  void nonpositiveSentTimeUsesWireTimestampFallback() {
    nowMs.set(-100L);
    QtDateTimeValue requested = tracker.beginProbe();
    nowMs.set(100L);
    tracker.observeReply(List.of(requested));
    assertEquals(OptionalLong.of(200L), tracker.lastMeasuredLagMs());
  }

  @Test
  void measurementAtEpochZeroRemainsUnavailable() {
    nowMs.set(-100L);
    QtDateTimeValue requested = tracker.beginProbe();
    nowMs.set(0L);
    tracker.observeReply(List.of(requested));
    nowMs.set(100L);
    assertEquals(OptionalLong.empty(), tracker.lastMeasuredLagMs());
  }

  @Test
  void concurrentDuplicateRepliesConsumePendingProbeOnlyOnce() throws Exception {
    AtomicInteger clockReads = new AtomicInteger();
    QuasselCoreLagTracker concurrent =
        new QuasselCoreLagTracker(
            () -> {
              clockReads.incrementAndGet();
              return nowMs.get();
            });
    QtDateTimeValue requested = concurrent.beginProbe();
    nowMs.addAndGet(42L);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> tasks = new ArrayList<>();
      for (int index = 0; index < 32; index++) {
        tasks.add(
            executor.submit(
                () -> {
                  assertTrue(start.await(2, TimeUnit.SECONDS));
                  concurrent.observeReply(List.of(requested));
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> task : tasks) task.get(2, TimeUnit.SECONDS);
    }
    assertEquals(2, clockReads.get(), "only one reply may obtain a measurement timestamp");
    assertEquals(OptionalLong.of(42L), concurrent.lastMeasuredLagMs());
  }

  private void measure(long elapsedMs) {
    QtDateTimeValue requested = tracker.beginProbe();
    nowMs.addAndGet(elapsedMs);
    tracker.observeReply(List.of(requested));
  }

  private static QtDateTimeValue token(long atMs) {
    return QuasselCoreDatastreamCodec.utcDateTimeFromEpochMs(atMs);
  }
}
