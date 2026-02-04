package cafe.woden.ircclient.app;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Join-burst mode suppression.
 *
 * <p>When joining a channel, some networks send a quick burst of channel-flag MODEs
 * (e.g. {@code +nt}, {@code +r}, etc.). Rather than printing each one individually,
 * we buffer the simple flag sets and print a single summary line shortly after the
 * burst settles.
 */
@Component
public final class JoinModeBurstService {
  private final UiPort ui;
  private final ModeFormattingService modeFormattingService;

  private final ConcurrentHashMap<ModeKey, JoinModeBuffer> joinModeBuffers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ModeKey, Long> joinModeSummaryPrintedMs = new ConcurrentHashMap<>();

  public JoinModeBurstService(UiPort ui, ModeFormattingService modeFormattingService) {
    this.ui = ui;
    this.modeFormattingService = modeFormattingService;
  }

  
  public void startJoinModeBuffer(String serverId, String channel) {
    if (channel == null || channel.isBlank()) return;

    ModeKey key = ModeKey.of(serverId, channel);

    // Always overwrite: the latest join wins.
    joinModeSummaryPrintedMs.remove(key);

    joinModeBuffers.put(key, new JoinModeBuffer());

    // Fallback flush: if we already collected any join-burst flags, print soon after join.
    // IMPORTANT: do NOT discard an empty buffer here; some networks delay MODE for a couple seconds.
    javax.swing.Timer t = new javax.swing.Timer(1500, e -> flushJoinModesIfAny(serverId, channel, false));
    t.setRepeats(false);
    t.start();

    // Cleanup: if we never receive join-burst modes, don't keep the empty buffer forever.
    javax.swing.Timer cleanup = new javax.swing.Timer(15000, e -> flushJoinModesIfAny(serverId, channel, true));
    cleanup.setRepeats(false);
    cleanup.start();
  }

  /**
   * Called on {@code ChannelModeChanged}. If the mode change is a simple join-burst flag set,
   * this consumes it and schedules a debounced summary flush.
   *
   * @return true if the event was consumed (caller should return early); false if the caller
   *     should proceed with normal MODE printing.
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
    if (removed != null) removed.cancelFlushTimer();
  }

  
  public boolean shouldSuppressModesListedSummary(String serverId, String channel, boolean outputIsChannel) {
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

    // Don't finalize early when we haven't seen any join-burst modes yet (topic/NAMES can arrive first).
    if (buf.isEmpty()) {
      if (finalizeIfEmpty) {
        joinModeBuffers.remove(key, buf);
        buf.cancelFlushTimer();
      }
      return;
    }

    // We have something to print; finalize this join-burst.
    joinModeBuffers.remove(key, buf);
    buf.cancelFlushTimer();

    TargetRef chanTarget = new TargetRef(serverId, channel);
    ui.ensureTargetExists(chanTarget);

    String summary = modeFormattingService.describeBufferedJoinModes(buf.plus, buf.minus);
    if (summary == null || summary.isBlank()) return;

    joinModeSummaryPrintedMs.put(key, System.currentTimeMillis());
    ui.appendNotice(chanTarget, "(mode)", summary);
  }

  /** Clears all join-burst state for a server (call on disconnect). */
  public void clearServer(String serverId) {
    if (serverId == null) return;
    String sid = serverId.trim();

    for (Map.Entry<ModeKey, JoinModeBuffer> e : joinModeBuffers.entrySet()) {
      if (sid.equals(e.getKey().serverId())) {
        JoinModeBuffer buf = joinModeBuffers.remove(e.getKey());
        if (buf != null) buf.cancelFlushTimer();
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

  /**
   * Buffers the initial join-burst of simple channel-state modes (no args) so we can print
   * a single summary line after the join feels complete.
   */
  private static final class JoinModeBuffer {
    private final java.util.LinkedHashSet<Character> plus = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Character> minus = new java.util.LinkedHashSet<>();

    // Debounce flush so we print once shortly after the join-mode burst settles.
    private javax.swing.Timer flushTimer;

    void bumpFlush(Runnable flush) {
      if (flush == null) return;
      if (flushTimer != null) flushTimer.stop();
      flushTimer = new javax.swing.Timer(200, e -> flush.run());
      flushTimer.setRepeats(false);
      flushTimer.start();
    }

    void cancelFlushTimer() {
      if (flushTimer != null) {
        flushTimer.stop();
        flushTimer = null;
      }
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
