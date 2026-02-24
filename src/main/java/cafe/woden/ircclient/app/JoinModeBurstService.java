package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.util.RestartableRxTimer;
import cafe.woden.ircclient.config.ExecutorConfig;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Join-burst mode suppression.
 *
 * <p>When joining a channel, some networks send a quick burst of channel-flag MODEs (e.g. {@code
 * +nt}, {@code +r}, etc.). Rather than printing each one individually, we buffer the simple flag
 * sets and print a single summary line shortly after the burst settles.
 */
@Component
public final class JoinModeBurstService {
  private static final Logger log = LoggerFactory.getLogger(JoinModeBurstService.class);

  private final UiPort ui;
  private final ModeFormattingService modeFormattingService;

  // Keep scheduling out of the EDT. SwingUiPort already marshals UI calls onto the EDT.
  private final ScheduledExecutorService joinModeExec;
  private final Scheduler joinModeScheduler;

  private final ConcurrentHashMap<ModeKey, JoinModeBuffer> joinModeBuffers =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ModeKey, Long> joinModeSummaryPrintedMs =
      new ConcurrentHashMap<>();

  public JoinModeBurstService(
      UiPort ui,
      ModeFormattingService modeFormattingService,
      @Qualifier(ExecutorConfig.JOIN_MODE_BURST_SCHEDULER) ScheduledExecutorService joinModeExec) {
    this.ui = Objects.requireNonNull(ui, "ui");
    this.modeFormattingService =
        Objects.requireNonNull(modeFormattingService, "modeFormattingService");
    this.joinModeExec = Objects.requireNonNull(joinModeExec, "joinModeExec");
    this.joinModeScheduler = Schedulers.from(joinModeExec);
  }

  @PreDestroy
  void shutdown() {
    // Best-effort cleanup.
    try {
      for (JoinModeBuffer b : joinModeBuffers.values()) {
        if (b != null) b.dispose();
      }
      joinModeBuffers.clear();
      joinModeSummaryPrintedMs.clear();
    } catch (Exception ignored) {
    }
  }

  public void startJoinModeBuffer(String serverId, String channel) {
    if (channel == null || channel.isBlank()) return;

    ModeKey key = ModeKey.of(serverId, channel);

    // Always overwrite: the latest join wins.
    joinModeSummaryPrintedMs.remove(key);

    JoinModeBuffer next = new JoinModeBuffer(joinModeScheduler);
    JoinModeBuffer prev = joinModeBuffers.put(key, next);
    if (prev != null) prev.dispose();

    // Fallback flush: if we already collected any join-burst flags, print soon after join.
    // IMPORTANT: do NOT discard an empty buffer here; some networks delay MODE for a couple
    // seconds.
    next.scheduleFallback(() -> flushJoinModesIfAny(serverId, channel, false));

    // Cleanup: if we never receive join-burst modes, don't keep the empty buffer forever.
    next.scheduleCleanup(() -> flushJoinModesIfAny(serverId, channel, true));
  }

  /**
   * Called on {@code ChannelModeChanged}. If the mode change is a simple join-burst flag set, this
   * consumes it and schedules a debounced summary flush.
   *
   * @return true if the event was consumed (caller should return early); false if the caller should
   *     proceed with normal MODE printing.
   */
  public boolean handleChannelModeChanged(String serverId, String channel, String details) {
    if (channel == null || channel.isBlank()) return false;

    JoinModeBuffer joinBuf = joinModeBuffers.get(ModeKey.of(serverId, channel));
    if (joinBuf == null) return false;

    if (joinBuf.tryAdd(details)) {
      // Print quickly (debounced) instead of waiting for TOPIC/NAMES.
      joinBuf.bumpFlush(() -> flushJoinModesIfAny(serverId, channel, true));
      return true;
    }

    // As soon as we see something else, flush the buffered summary so the join feels complete.
    flushJoinModesIfAny(serverId, channel, true);
    return false;
  }

  public void discardJoinModeBuffer(String serverId, String channel) {
    if (channel == null || channel.isBlank()) return;
    ModeKey key = ModeKey.of(serverId, channel);
    JoinModeBuffer removed = joinModeBuffers.remove(key);
    if (removed != null) removed.dispose();
  }

  public void clearChannel(String serverId, String channel) {
    if (channel == null || channel.isBlank()) return;
    ModeKey key = ModeKey.of(serverId, channel);
    JoinModeBuffer removed = joinModeBuffers.remove(key);
    if (removed != null) removed.dispose();
    joinModeSummaryPrintedMs.remove(key);
  }

  public boolean shouldSuppressModesListedSummary(
      String serverId, String channel, boolean outputIsChannel) {
    if (channel == null || channel.isBlank()) return false;
    ModeKey key = ModeKey.of(serverId, channel);

    if (outputIsChannel) {
      Long printedMs = joinModeSummaryPrintedMs.remove(key);
      return printedMs != null && (System.currentTimeMillis() - printedMs) < 4000L;
    }

    // Clean up any stale marker.
    joinModeSummaryPrintedMs.remove(key);
    return false;
  }

  public void flushJoinModesIfAny(String serverId, String channel, boolean finalizeIfEmpty) {
    if (channel == null || channel.isBlank()) return;

    ModeKey key = ModeKey.of(serverId, channel);

    JoinModeBuffer buf = joinModeBuffers.get(key);
    if (buf == null) return;

    // Don't finalize early when we haven't seen any join-burst modes yet (topic/NAMES can arrive
    // first).
    if (buf.isEmpty()) {
      if (finalizeIfEmpty) {
        joinModeBuffers.remove(key, buf);
        buf.dispose();
      }
      return;
    }

    // We have something to print; finalize this join-burst.
    joinModeBuffers.remove(key, buf);
    buf.dispose();

    TargetRef chanTarget = new TargetRef(serverId, channel);
    ui.ensureTargetExists(chanTarget);

    String summary = modeFormattingService.describeBufferedJoinModes(buf.plus, buf.minus);
    if (summary == null || summary.isBlank()) return;

    joinModeSummaryPrintedMs.put(key, System.currentTimeMillis());
    ui.appendNotice(chanTarget, "(mode)", summary);
  }

  public void clearServer(String serverId) {
    if (serverId == null) return;
    String sid = serverId.trim();

    for (Map.Entry<ModeKey, JoinModeBuffer> e : joinModeBuffers.entrySet()) {
      if (sid.equals(e.getKey().serverId())) {
        JoinModeBuffer buf = joinModeBuffers.remove(e.getKey());
        if (buf != null) buf.dispose();
      }
    }

    for (ModeKey key : joinModeSummaryPrintedMs.keySet()) {
      if (sid.equals(key.serverId())) {
        joinModeSummaryPrintedMs.remove(key);
      }
    }
  }

  private record ModeKey(String serverId, String channelLower) {
    ModeKey {
      serverId = (serverId == null) ? "" : serverId.trim();
      channelLower = (channelLower == null) ? "" : channelLower.trim().toLowerCase(Locale.ROOT);
    }

    static ModeKey of(String serverId, String channel) {
      return new ModeKey(serverId, channel);
    }
  }

  private static final class JoinModeBuffer {
    private final java.util.LinkedHashSet<Character> plus = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Character> minus = new java.util.LinkedHashSet<>();

    private final RestartableRxTimer debouncedFlush;
    private final RestartableRxTimer fallbackFlush;
    private final RestartableRxTimer cleanupFlush;

    JoinModeBuffer(Scheduler scheduler) {
      // If the scheduler rejects work during shutdown, just drop it quietly.
      this.debouncedFlush =
          new RestartableRxTimer(
              scheduler, err -> log.debug("[JoinModeBurst] flush timer error", err));
      this.fallbackFlush =
          new RestartableRxTimer(
              scheduler, err -> log.debug("[JoinModeBurst] fallback timer error", err));
      this.cleanupFlush =
          new RestartableRxTimer(
              scheduler, err -> log.debug("[JoinModeBurst] cleanup timer error", err));
    }

    void bumpFlush(Runnable flush) {
      if (flush == null) return;
      debouncedFlush.restart(200, flush);
    }

    void scheduleFallback(Runnable flush) {
      if (flush == null) return;
      fallbackFlush.restart(1500, flush);
    }

    void scheduleCleanup(Runnable flush) {
      if (flush == null) return;
      cleanupFlush.restart(15000, flush);
    }

    void dispose() {
      debouncedFlush.stop();
      fallbackFlush.stop();
      cleanupFlush.stop();
    }

    boolean tryAdd(String details) {
      if (details == null) return false;
      String d = details.trim();
      if (d.isEmpty()) return false;

      // Only accept simple flag sets with no args (no spaces).
      int sp = d.indexOf(' ');
      if (sp >= 0) return false;

      char sign = d.charAt(0);
      if (sign != '+' && sign != '-') return false;

      for (int i = 1; i < d.length(); i++) {
        char c = d.charAt(i);
        if (!Character.isLetterOrDigit(c)) return false;
      }

      java.util.LinkedHashSet<Character> target = (sign == '+') ? plus : minus;
      for (int i = 1; i < d.length(); i++) {
        target.add(d.charAt(i));
      }

      return true;
    }

    boolean isEmpty() {
      return plus.isEmpty() && minus.isEmpty();
    }
  }
}
