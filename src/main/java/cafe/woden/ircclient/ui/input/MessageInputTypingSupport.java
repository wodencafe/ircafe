package cafe.woden.ircclient.ui.input;

import cafe.woden.ircclient.ui.settings.UiSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles IRCv3 typing indicators (local state emissions and remote hint banner).
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Emit local typing state changes (active/paused/done), including throttling and pause timer.
 *   <li>Gate emissions based on user preferences (typing indicators enabled).
 *   <li>Show/hide a small remote "is typing…" banner with auto-expire.
 * </ul>
 *
 * <p>This class is Swing/EDT oriented and uses {@link javax.swing.Timer}.
 */
final class MessageInputTypingSupport {

  private static final Logger log = LoggerFactory.getLogger(MessageInputTypingSupport.class);

  private static final int TYPING_PAUSE_MS = 2000;

  // IRCv3 typing indicators are ephemeral; many clients expire "active" after ~5–6 seconds.
  // We throttle "active" emissions to avoid spamming while still keeping the indicator alive.
  // IMPORTANT: do not schedule repeating "active" keepalives; that causes "active" to continue
  // after the user stops typing (until PAUSED fires). Emit "active" only from user edits.
  private static final int TYPING_ACTIVE_THROTTLE_MS = 3000;

  // Keep incoming typing hints visible a bit longer than the common remote refresh cadence
  // so short network delays do not cause the banner to blink out before "done" arrives.
  private static final int REMOTE_TYPING_HINT_MS = 8000;

  private final JTextField input;
  private final JPanel typingBanner;
  private final JLabel typingBannerLabel;
  private final TypingDotsIndicator typingDotsIndicator;
  private final TypingSignalIndicator typingSignalIndicator;
  private final Supplier<UiSettings> settingsSupplier;
  private final MessageInputUiHooks hooks;

  private volatile Consumer<String> onTypingStateChanged = s -> {};

  private final Timer typingPauseTimer;
  private final Timer remoteTypingHintTimer;

  private String lastEmittedTypingState = "done";
  private final LinkedHashMap<String, RemoteTypingEntry> remoteTypingByNick = new LinkedHashMap<>();

  private long lastActiveSentAtMs = 0L;

  // Track the last applied settings so we can react specifically to "toggle OFF".
  private boolean lastTypingSendEnabled = true;
  private boolean lastTypingReceiveEnabled = true;
  private boolean lastTypingTranscriptEnabled = true;
  private boolean lastTypingSendSignalEnabled = true;
  private boolean typingSignalAvailable;

  MessageInputTypingSupport(
      JTextField input,
      JPanel typingBanner,
      JLabel typingBannerLabel,
      TypingDotsIndicator typingDotsIndicator,
      TypingSignalIndicator typingSignalIndicator,
      Supplier<UiSettings> settingsSupplier,
      MessageInputUiHooks hooks) {
    this.input = Objects.requireNonNull(input, "input");
    this.typingBanner = Objects.requireNonNull(typingBanner, "typingBanner");
    this.typingBannerLabel = Objects.requireNonNull(typingBannerLabel, "typingBannerLabel");
    this.typingDotsIndicator = Objects.requireNonNull(typingDotsIndicator, "typingDotsIndicator");
    this.typingSignalIndicator =
        Objects.requireNonNull(typingSignalIndicator, "typingSignalIndicator");
    this.settingsSupplier = settingsSupplier != null ? settingsSupplier : () -> null;
    this.hooks = hooks;

    this.typingPauseTimer = new Timer(TYPING_PAUSE_MS, e -> onTypingPauseElapsed());
    this.typingPauseTimer.setRepeats(false);

    this.remoteTypingHintTimer = new Timer(REMOTE_TYPING_HINT_MS, e -> onRemoteTypingHintElapsed());
    this.remoteTypingHintTimer.setRepeats(false);

    UiSettings initial = this.settingsSupplier.get();
    this.lastTypingSendEnabled = initial == null || initial.typingIndicatorsEnabled();
    this.lastTypingReceiveEnabled = initial == null || initial.typingIndicatorsReceiveEnabled();
    this.lastTypingTranscriptEnabled =
        initial == null || initial.typingIndicatorsTranscriptEnabled();
    this.lastTypingSendSignalEnabled =
        initial == null || initial.typingIndicatorsSendSignalEnabled();
    this.typingSignalAvailable = false;
    refreshTypingSignalAvailability();
  }

  void setOnTypingStateChanged(Consumer<String> onTypingStateChanged) {
    this.onTypingStateChanged = onTypingStateChanged == null ? s -> {} : onTypingStateChanged;
  }

  void setTypingSignalAvailable(boolean available) {
    typingSignalAvailable = available;
    refreshTypingSignalAvailability();
  }

  void onLocalTypingIndicatorSent(String state) {
    if (!typingSignalAvailable || !typingSignalDisplayEnabled()) return;
    String s = normalizeTypingState(state);
    if (s.isEmpty()) return;
    typingSignalIndicator.pulse(s);
  }

  /** Call from the input document listener (only when the edit was user-driven). */
  void onUserEdit(boolean programmaticEdit) {
    if (programmaticEdit) return;
    fireTypingStateFromUserEdit();
  }

  /**
   * Called when the draft text is swapped programmatically (e.g., switching buffers). This should
   * not emit anything; it just resets the local timers so we don't keep sending PAUSED for an old
   * buffer.
   */
  void onDraftTextSetProgrammatically() {
    typingPauseTimer.stop();

    lastActiveSentAtMs = 0L;
  }

  /**
   * Called when the user switches away from the current chat buffer.
   *
   * <p>If there is still draft text, emit PAUSED instead of DONE so peers can distinguish "still
   * composing, temporarily away" from "finished typing".
   */
  void flushTypingForBufferSwitch() {
    typingPauseTimer.stop();

    lastActiveSentAtMs = 0L;

    String text = input.getText();
    if (text != null && !text.isBlank() && !isTypingSuppressedSlashCommand(text)) {
      emitTypingState("paused");
      return;
    }

    emitTypingState("done");
  }

  void flushTypingDone() {
    typingPauseTimer.stop();

    lastActiveSentAtMs = 0L;
    emitTypingState("done");
  }

  void onSettingsApplied(UiSettings s) {
    if (s == null) return;
    boolean sendEnabledNow = s.typingIndicatorsEnabled();
    boolean receiveEnabledNow = s.typingIndicatorsReceiveEnabled();
    boolean transcriptEnabledNow = s.typingIndicatorsTranscriptEnabled();
    boolean sendSignalEnabledNow = s.typingIndicatorsSendSignalEnabled();

    // If outgoing typing was just toggled OFF, immediately emit DONE (cleanup).
    if (lastTypingSendEnabled && !sendEnabledNow) {
      flushTypingDone();
    }
    // If incoming typing was just toggled OFF, hide any remote banner.
    if (lastTypingReceiveEnabled && !receiveEnabledNow) {
      clearRemoteTypingIndicator();
    }
    if (lastTypingTranscriptEnabled && !transcriptEnabledNow) {
      clearRemoteTypingIndicator();
    }
    if (lastTypingSendSignalEnabled && !sendSignalEnabledNow) {
      typingSignalIndicator.pulse("done");
    }
    lastTypingSendEnabled = sendEnabledNow;
    lastTypingReceiveEnabled = receiveEnabledNow;
    lastTypingTranscriptEnabled = transcriptEnabledNow;
    lastTypingSendSignalEnabled = sendSignalEnabledNow;
    refreshTypingSignalAvailability();
  }

  void showRemoteTypingIndicator(String nick, String state) {
    if (!typingIndicatorsReceiveEnabled() || !typingIndicatorsTranscriptEnabled()) return;

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) return;

    String s = normalizeTypingState(state);
    if (s.isEmpty()) s = "active";

    long now = System.currentTimeMillis();
    pruneExpiredRemoteTypers(now);

    if ("done".equals(s)) {
      remoteTypingByNick.remove(n);
      refreshRemoteTypingBanner(now);
      return;
    }

    // Keep last-updated nick near the end for a stable "most recently active" ordering.
    remoteTypingByNick.remove(n);
    remoteTypingByNick.put(n, new RemoteTypingEntry(s, now + REMOTE_TYPING_HINT_MS));
    refreshRemoteTypingBanner(now);
  }

  void clearRemoteTypingIndicator() {
    remoteTypingHintTimer.stop();
    remoteTypingByNick.clear();
    typingBannerLabel.setText("");
    typingDotsIndicator.stopAnimation();
    typingDotsIndicator.setVisible(false);
    typingBanner.setVisible(false);
    typingBanner.revalidate();
    typingBanner.repaint();

    // Ensure hint/completion UI can re-appear immediately after banner changes.
    if (hooks != null) {
      try {
        hooks.refreshHintAndCompletion();
      } catch (Exception ex) {
        log.warn("[MessageInputTypingSupport] hooks.refreshHintAndCompletion failed", ex);
      }
    }
  }

  void onRemoveNotify() {
    typingPauseTimer.stop();
    remoteTypingHintTimer.stop();
    remoteTypingByNick.clear();
    typingBannerLabel.setText("");
    typingDotsIndicator.stopAnimation();
    typingDotsIndicator.setVisible(false);
    typingBanner.setVisible(false);

    lastActiveSentAtMs = 0L;
  }

  private void fireTypingStateFromUserEdit() {
    if (!input.isEnabled() || !input.isEditable()) return;

    long now = System.currentTimeMillis();
    String text = input.getText();

    if (text == null || text.isBlank()) {
      typingPauseTimer.stop();

      lastActiveSentAtMs = 0L;
      emitTypingState("done");
      return;
    }

    // Slash commands should not broadcast typing indicators, except /me action text.
    if (isTypingSuppressedSlashCommand(text)) {
      typingPauseTimer.stop();

      lastActiveSentAtMs = 0L;
      emitTypingState("done");
      return;
    }

    // Emit ACTIVE only from user edits, throttled. This prevents queued/periodic ACTIVE messages
    // from being sent after the user stops typing (before PAUSED fires).
    if (!"active".equals(lastEmittedTypingState)) {
      emitTypingState("active");
      lastActiveSentAtMs = now;
    } else if ((now - lastActiveSentAtMs) >= TYPING_ACTIVE_THROTTLE_MS) {
      emitTypingState("active", true);
      lastActiveSentAtMs = now;
    }

    typingPauseTimer.restart();
  }

  private void onTypingPauseElapsed() {
    if (!input.isEnabled() || !input.isEditable()) return;

    String text = input.getText();
    if (text == null || text.isBlank()) {

      lastActiveSentAtMs = 0L;
      emitTypingState("done");
      return;
    }

    if (isTypingSuppressedSlashCommand(text)) {

      lastActiveSentAtMs = 0L;
      emitTypingState("done");
      return;
    }

    emitTypingState("paused");
  }

  private boolean typingIndicatorsSendEnabled() {
    try {
      UiSettings s = settingsSupplier.get();
      // Default: enabled if settings are not available yet.
      return s == null || s.typingIndicatorsEnabled();
    } catch (Exception ex) {
      return true;
    }
  }

  private boolean typingIndicatorsReceiveEnabled() {
    try {
      UiSettings s = settingsSupplier.get();
      // Default: enabled if settings are not available yet.
      return s == null || s.typingIndicatorsReceiveEnabled();
    } catch (Exception ex) {
      return true;
    }
  }

  private boolean typingIndicatorsTranscriptEnabled() {
    try {
      UiSettings s = settingsSupplier.get();
      return s == null || s.typingIndicatorsTranscriptEnabled();
    } catch (Exception ex) {
      return true;
    }
  }

  private boolean typingIndicatorsSendSignalEnabled() {
    try {
      UiSettings s = settingsSupplier.get();
      return s == null || s.typingIndicatorsSendSignalEnabled();
    } catch (Exception ex) {
      return true;
    }
  }

  private boolean typingSignalDisplayEnabled() {
    return typingIndicatorsSendEnabled() && typingIndicatorsSendSignalEnabled();
  }

  private void refreshTypingSignalAvailability() {
    typingSignalIndicator.setAvailable(typingSignalAvailable && typingSignalDisplayEnabled());
  }

  private void emitTypingState(String state) {
    emitTypingState(state, false);
  }

  private void emitTypingState(String state, boolean allowRepeat) {
    String normalized = normalizeTypingState(state);
    if (normalized.isEmpty()) return;

    // Preferences gating: if typing indicators are disabled, never emit ACTIVE/PAUSED.
    // Still allow DONE to flow through as a cleanup signal so we can clear any existing
    // indicator on the server / other clients.
    if (!typingIndicatorsSendEnabled() && !"done".equals(normalized)) {
      return;
    }

    if (!allowRepeat && normalized.equals(lastEmittedTypingState)) return;

    lastEmittedTypingState = normalized;
    try {
      onTypingStateChanged.accept(normalized);
    } catch (Exception ex) {
      log.warn("[MessageInputTypingSupport] onTypingStateChanged failed", ex);
    }
  }

  private static String normalizeTypingState(String state) {
    String s = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return "";
    return switch (s) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "";
    };
  }

  private static boolean isTypingSuppressedSlashCommand(String text) {
    String t = Objects.toString(text, "");
    if (!t.startsWith("/")) return false;
    return !isMeCommand(t);
  }

  private static boolean isMeCommand(String text) {
    if (text == null || !text.regionMatches(true, 0, "/me", 0, 3)) {
      return false;
    }
    if (text.length() == 3) return true;
    return Character.isWhitespace(text.charAt(3));
  }

  private void onRemoteTypingHintElapsed() {
    long now = System.currentTimeMillis();
    pruneExpiredRemoteTypers(now);
    refreshRemoteTypingBanner(now);
  }

  private void pruneExpiredRemoteTypers(long now) {
    if (remoteTypingByNick.isEmpty()) return;
    remoteTypingByNick.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
  }

  private void refreshRemoteTypingBanner(long now) {
    boolean wasVisible = typingBanner.isVisible();

    if (remoteTypingByNick.isEmpty()) {
      remoteTypingHintTimer.stop();
      typingBannerLabel.setText("");
      typingDotsIndicator.stopAnimation();
      typingDotsIndicator.setVisible(false);
      typingBanner.setVisible(false);
      typingBanner.revalidate();
      typingBanner.repaint();
      if (wasVisible) {
        refreshHintAndCompletionUi();
      }
      return;
    }

    typingBannerLabel.setText(buildRemoteTypingHintText());
    boolean hasActive = hasActiveRemoteTypers();
    if (hasActive) {
      typingDotsIndicator.setVisible(true);
      typingDotsIndicator.startAnimation();
    } else {
      typingDotsIndicator.stopAnimation();
      typingDotsIndicator.setVisible(false);
    }
    typingBanner.setVisible(true);
    typingBanner.revalidate();
    typingBanner.repaint();
    scheduleNextRemoteTypingExpiry(now);
  }

  private void scheduleNextRemoteTypingExpiry(long now) {
    if (remoteTypingByNick.isEmpty()) {
      remoteTypingHintTimer.stop();
      return;
    }
    long nextExpiry = Long.MAX_VALUE;
    for (RemoteTypingEntry e : remoteTypingByNick.values()) {
      if (e.expiresAtMs() < nextExpiry) {
        nextExpiry = e.expiresAtMs();
      }
    }
    long delay = Math.max(100L, nextExpiry - now);
    int delayMs = (int) Math.min(Integer.MAX_VALUE, delay);
    remoteTypingHintTimer.setInitialDelay(delayMs);
    remoteTypingHintTimer.restart();
  }

  private String buildRemoteTypingHintText() {
    List<String> active = new ArrayList<>();
    List<String> paused = new ArrayList<>();
    for (Map.Entry<String, RemoteTypingEntry> e : remoteTypingByNick.entrySet()) {
      if ("paused".equals(e.getValue().state())) {
        paused.add(e.getKey());
      } else {
        active.add(e.getKey());
      }
    }

    if (active.isEmpty() && paused.isEmpty()) {
      return "";
    }
    if (paused.isEmpty()) {
      return formatTypingClause(active, "is typing", "are typing");
    }
    if (active.isEmpty()) {
      return formatTypingClause(paused, "paused typing", "paused typing");
    }
    return formatTypingClause(active, "is typing", "are typing")
        + " | "
        + formatTypingClause(paused, "paused typing", "paused typing");
  }

  private static String formatTypingClause(
      List<String> nicks, String singularSuffix, String pluralSuffix) {
    if (nicks == null || nicks.isEmpty()) return "";
    String names = formatNickList(nicks);
    if (names.isEmpty()) return "";
    return names + " " + (nicks.size() == 1 ? singularSuffix : pluralSuffix);
  }

  private static String formatNickList(List<String> nicks) {
    if (nicks == null || nicks.isEmpty()) return "";

    int maxShown = 3;
    int showCount = Math.min(maxShown, nicks.size());
    List<String> shown = nicks.subList(0, showCount);
    int others = nicks.size() - showCount;

    String base;
    if (shown.size() == 1) {
      base = shown.get(0);
    } else if (shown.size() == 2) {
      base = shown.get(0) + " and " + shown.get(1);
    } else {
      base = shown.get(0) + ", " + shown.get(1) + ", and " + shown.get(2);
    }

    if (others <= 0) return base;
    return base + ", and " + others + " other" + (others == 1 ? "" : "s");
  }

  private boolean hasActiveRemoteTypers() {
    for (RemoteTypingEntry entry : remoteTypingByNick.values()) {
      if (!"paused".equals(entry.state())) {
        return true;
      }
    }
    return false;
  }

  private void refreshHintAndCompletionUi() {
    if (hooks == null) return;
    try {
      hooks.refreshHintAndCompletion();
    } catch (Exception ex) {
      log.warn("[MessageInputTypingSupport] hooks.refreshHintAndCompletion failed", ex);
    }
  }

  private record RemoteTypingEntry(String state, long expiresAtMs) {}
}
