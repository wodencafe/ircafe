package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.util.VirtualThreads;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Best-effort coordinator for capturing a single in-flight ZNC Playback module request.
 *
 * <p>ZNC playback replays old lines as normal IRC messages; we need to group those lines into a
 * logical "batch" so the UI can treat them as scrollback rather than live traffic.
 *
 * <p>This coordinator does not itself intercept messages; it only tracks capture state, completion,
 * and emits {@link IrcEvent.ZncPlaybackBatchReceived} when a capture completes.
 */
public final class ZncPlaybackCaptureCoordinator {

  private static final Logger log = LoggerFactory.getLogger(ZncPlaybackCaptureCoordinator.class);

  /** How long to wait after the last observed playback activity before auto-completing. */
  private static final Duration QUIET_TIME = Duration.ofMillis(1500);

  /** Safety cap to prevent an in-flight capture from hanging forever. */
  private static final Duration MAX_CAPTURE_TIME = Duration.ofSeconds(15);

  private static final ScheduledExecutorService scheduler =
      VirtualThreads.newSingleThreadScheduledExecutor("znc-playback-capture");

  private final AtomicReference<CaptureSession> active = new AtomicReference<>(null);

  /** Start a new capture session. Only one capture may be active at a time per connection. */
  public void start(
      String serverId,
      String target,
      Instant fromInclusive,
      Instant toInclusive,
      Consumer<ServerIrcEvent> emit
  ) {
    Objects.requireNonNull(serverId, "serverId");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(emit, "emit");

    CaptureSession session = new CaptureSession(serverId, target, fromInclusive, toInclusive);
    CaptureSession prev = active.getAndSet(session);
    if (prev != null) {
      // Prefer failing fast; the UI/service should gate concurrency.
      active.compareAndSet(session, prev); // restore
      throw new IllegalStateException("ZNC playback capture already active for: " + prev.target);
    }

    session.startedAt = Instant.now();
    session.lastActivity = session.startedAt;
    session.emit = emit;

    session.maxTimeout =
        scheduler.schedule(() -> timeoutIfStillActive(session, "max-time"), MAX_CAPTURE_TIME.toMillis(), TimeUnit.MILLISECONDS);

    scheduleQuietTimeout(session);

    log.debug("[{}] ZNC playback capture started target={} from={} to={}",
        serverId, target, fromInclusive, toInclusive);
  }

  /** True if a capture is currently active. */
  public boolean isActive() {
    return active.get() != null;
  }

  /** Current capture target if active. */
  public Optional<String> activeTarget() {
    CaptureSession s = active.get();
    return s == null ? Optional.empty() : Optional.ofNullable(s.target);
  }

  /**
   * True if an inbound line for {@code target} at {@code at} should be captured.
   *
   * <p>This is intentionally conservative: we only capture when the target matches the active
   * capture target (case-insensitive) and the timestamp falls within the requested window. When the
   * requested window ended "now" (omitted 'to'), we trim a small tail to avoid swallowing truly-live
   * messages that arrive during playback.
   */
  public boolean shouldCapture(String target, Instant at) {
    CaptureSession s = active.get();
    if (s == null) return false;
    if (target == null || target.isBlank()) return false;
    if (at == null) return false;

    String t = target.trim();
    String st = (s.target == null) ? "" : s.target.trim();
    if (st.isEmpty() || !t.equalsIgnoreCase(st)) return false;

    Instant lower = s.fromInclusive;
    if (lower != null && at.isBefore(lower)) return false;

    Instant upper = effectiveUpperBound(s);
    if (upper != null && at.isAfter(upper)) return false;

    return true;
  }

  private static Instant effectiveUpperBound(CaptureSession s) {
    if (s == null) return null;
    Instant upper = s.toInclusive;
    if (upper == null) return null;

    // If the range was effectively "until now" (toInclusive ~= startedAt), trim a tiny tail so we
    // don't accidentally swallow truly-live messages.
    Instant started = s.startedAt;
    if (started != null && upper.isAfter(started.minusMillis(750))) {
      return started.minusMillis(500);
    }
    return upper;
  }

  /**
   * Record a playback control line from {@code *playback}. We keep these lines visible; this merely
   * updates activity and detects completion phrases.
   */
  public void onPlaybackControlLine(String line) {
    CaptureSession s = active.get();
    if (s == null) return;

    touch(s);

    String l = line == null ? "" : line.toLowerCase(Locale.ROOT);
    if (l.contains("playback complete") || l.contains("playback finished") || l.contains("playback done")) {
      completeActive("control-complete");
    }
  }

  /** Touch last-activity timestamp (for quiet-time completion). */
  public void touch() {
    CaptureSession s = active.get();
    if (s == null) return;
    touch(s);
  }

  /** Add a replayed history entry (used by the upcoming R5.2c intercept step). */
  public void addEntry(ChatHistoryEntry entry) {
    CaptureSession s = active.get();
    if (s == null) return;
    if (entry != null) {
      s.entries.add(entry);
      touch(s);
    }
  }

  /** Complete the active capture (if present) and emit a {@link IrcEvent.ZncPlaybackBatchReceived}. */
  public void completeActive(String reason) {
    CaptureSession s = active.get();
    if (s == null) return;
    completeActiveInternal(s, reason);
  }

  /** Cancel the active capture (if present) without emitting any event. */
  public void cancelActive(String reason) {
    CaptureSession s = active.get();
    if (s == null) return;
    cancelInternal(s, reason);
  }


  private void timeoutIfStillActive(CaptureSession s, String reason) {
    CaptureSession cur = active.get();
    if (cur != s) return;
    completeActiveInternal(s, reason);
  }

  private void touch(CaptureSession s) {
    s.lastActivity = Instant.now();
    scheduleQuietTimeout(s);
  }

  private void scheduleQuietTimeout(CaptureSession s) {
    ScheduledFuture<?> prev = s.quietTimeout;
    if (prev != null) prev.cancel(false);

    s.quietTimeout =
        scheduler.schedule(() -> timeoutIfStillActive(s, "quiet-time"), QUIET_TIME.toMillis(), TimeUnit.MILLISECONDS);
  }

  
  private void cancelInternal(CaptureSession s, String reason) {
    if (!active.compareAndSet(s, null)) return;

    if (s.quietTimeout != null) s.quietTimeout.cancel(false);
    if (s.maxTimeout != null) s.maxTimeout.cancel(false);

    log.debug("[{}] ZNC playback capture cancelled target={} reason={}", s.serverId, s.target, reason);
  }

private void completeActiveInternal(CaptureSession s, String reason) {
    // Ensure we're still active and clear first to avoid re-entrancy issues.
    if (!active.compareAndSet(s, null)) return;

    if (s.quietTimeout != null) s.quietTimeout.cancel(false);
    if (s.maxTimeout != null) s.maxTimeout.cancel(false);

    List<ChatHistoryEntry> entries = new ArrayList<>(s.entries);
    long earliest = 0L;
    long latest = 0L;
    if (!entries.isEmpty()) {
      earliest = entries.stream().mapToLong(e -> e.at().toEpochMilli()).min().orElse(0L);
      latest = entries.stream().mapToLong(e -> e.at().toEpochMilli()).max().orElse(0L);
    }

    try {
      IrcEvent.ZncPlaybackBatchReceived ev =
          new IrcEvent.ZncPlaybackBatchReceived(
              Instant.now(),
              s.target,
              s.fromInclusive,
              s.toInclusive,
              entries
          );

      // Emit through the normal event pipeline; mediator will publish to ZncPlaybackBus.
      s.emit.accept(new ServerIrcEvent(s.serverId, ev));

      log.debug("[{}] ZNC playback capture completed target={} entries={} reason={}",
          s.serverId, s.target, entries.size(), reason);
    } catch (Exception ex) {
      log.warn("[{}] failed to emit ZNC playback batch target={} reason={}", s.serverId, s.target, reason, ex);
    }
  }

  private static final class CaptureSession {
    final String serverId;
    final String target;
    final Instant fromInclusive;
    final Instant toInclusive;

    final CopyOnWriteArrayList<ChatHistoryEntry> entries = new CopyOnWriteArrayList<>();

    volatile Instant startedAt;
    volatile Instant lastActivity;
    volatile ScheduledFuture<?> quietTimeout;
    volatile ScheduledFuture<?> maxTimeout;
    volatile Consumer<ServerIrcEvent> emit;

    CaptureSession(String serverId, String target, Instant fromInclusive, Instant toInclusive) {
      this.serverId = serverId;
      this.target = target;
      this.fromInclusive = fromInclusive;
      this.toInclusive = toInclusive;
    }
  }
}
