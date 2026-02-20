package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.settings.UiSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handles IRCv3 typing indicators (local state emissions and remote hint banner).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Emit local typing state changes (active/paused/done), including throttling and pause timer.</li>
 *   <li>Gate emissions based on user preferences (typing indicators enabled).</li>
 *   <li>Show/hide a small remote "is typing…" banner with auto-expire.</li>
 * </ul>
 *
 * <p>This class is Swing/EDT oriented and uses {@link javax.swing.Timer}.</p>
 */
final class MessageInputTypingSupport {

  private static final Logger log = LoggerFactory.getLogger(MessageInputTypingSupport.class);

  private static final int TYPING_PAUSE_MS = 5000;

  // IRCv3 typing indicators are ephemeral; many clients expire "active" after ~5–6 seconds.
  // We throttle "active" emissions to avoid spamming while still keeping the indicator alive.
  // IMPORTANT: do not schedule repeating "active" keepalives; that causes "active" to continue
  // after the user stops typing (until PAUSED fires). Emit "active" only from user edits.
  private static final int TYPING_ACTIVE_THROTTLE_MS = 3000;

  private static final int REMOTE_TYPING_HINT_MS = 5000;

  private final JTextField input;
  private final JPanel typingBanner;
  private final JLabel typingBannerLabel;
  private final Supplier<UiSettings> settingsSupplier;
  private final MessageInputUiHooks hooks;

  private volatile Consumer<String> onTypingStateChanged = s -> {};

  private final Timer typingPauseTimer;
  private final Timer remoteTypingHintTimer;

  private String lastEmittedTypingState = "done";
  private String remoteTypingHint = "";

  private long lastUserEditAtMs = 0L;
  private long lastActiveSentAtMs = 0L;

  // Track the last applied setting so we can react specifically to "toggle OFF".
  private boolean lastTypingIndicatorsEnabled = true;

  MessageInputTypingSupport(
      JTextField input,
      JPanel typingBanner,
      JLabel typingBannerLabel,
      Supplier<UiSettings> settingsSupplier,
      MessageInputUiHooks hooks
  ) {
    this.input = Objects.requireNonNull(input, "input");
    this.typingBanner = Objects.requireNonNull(typingBanner, "typingBanner");
    this.typingBannerLabel = Objects.requireNonNull(typingBannerLabel, "typingBannerLabel");
    this.settingsSupplier = settingsSupplier != null ? settingsSupplier : () -> null;
    this.hooks = hooks;

    this.typingPauseTimer = new Timer(TYPING_PAUSE_MS, e -> onTypingPauseElapsed());
    this.typingPauseTimer.setRepeats(false);

    this.remoteTypingHintTimer = new Timer(REMOTE_TYPING_HINT_MS, e -> clearRemoteTypingIndicator());
    this.remoteTypingHintTimer.setRepeats(false);

    UiSettings initial = this.settingsSupplier.get();
    this.lastTypingIndicatorsEnabled = initial != null && initial.typingIndicatorsEnabled();
  }

  void setOnTypingStateChanged(Consumer<String> onTypingStateChanged) {
    this.onTypingStateChanged = onTypingStateChanged == null ? s -> {} : onTypingStateChanged;
  }

  /**
   * Call from the input document listener (only when the edit was user-driven).
   */
  void onUserEdit(boolean programmaticEdit) {
    if (programmaticEdit) return;
    fireTypingStateFromUserEdit();
  }

  /**
   * Called when the draft text is swapped programmatically (e.g., switching buffers).
   * This should not emit anything; it just resets the local timers so we don't keep
   * sending PAUSED for an old buffer.
   */
  void onDraftTextSetProgrammatically() {
    typingPauseTimer.stop();
    lastUserEditAtMs = 0L;
    lastActiveSentAtMs = 0L;
  }

  void flushTypingDone() {
    typingPauseTimer.stop();
    lastUserEditAtMs = 0L;
    lastActiveSentAtMs = 0L;
    emitTypingState("done");
  }

  void onSettingsApplied(UiSettings s) {
    if (s == null) return;
    boolean enabledNow = s.typingIndicatorsEnabled();
    // If typing indicators were just toggled OFF, immediately emit DONE (cleanup)
    // and hide any remote banner.
    if (lastTypingIndicatorsEnabled && !enabledNow) {
      flushTypingDone();
      clearRemoteTypingIndicator();
    }
    lastTypingIndicatorsEnabled = enabledNow;
  }

  void showRemoteTypingIndicator(String nick, String state) {
    if (!typingIndicatorsEnabled()) return;

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) return;

    String s = normalizeTypingState(state);
    if (s.isEmpty()) s = "active";

    if ("done".equals(s)) {
      clearRemoteTypingIndicator();
      return;
    }

    remoteTypingHint = "paused".equals(s) ? (n + " paused typing…") : (n + " is typing…");
    remoteTypingHintTimer.restart();
    typingBannerLabel.setText(remoteTypingHint);
    typingBanner.setVisible(true);
    typingBanner.revalidate();
    typingBanner.repaint();
  }

  void clearRemoteTypingIndicator() {
    remoteTypingHintTimer.stop();
    remoteTypingHint = "";
    typingBannerLabel.setText("");
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
    remoteTypingHint = "";
    typingBannerLabel.setText("");
    typingBanner.setVisible(false);
    lastUserEditAtMs = 0L;
    lastActiveSentAtMs = 0L;
  }

  private void fireTypingStateFromUserEdit() {
    if (!input.isEnabled() || !input.isEditable()) return;

    long now = System.currentTimeMillis();
    String text = input.getText();

    if (text == null || text.isBlank()) {
      typingPauseTimer.stop();
      lastUserEditAtMs = 0L;
      lastActiveSentAtMs = 0L;
      emitTypingState("done");
      return;
    }

    // Slash commands should not broadcast typing indicators.
    // (User request: do not send typing indicators when the first character is '/'.)
    if (text.startsWith("/")) {
      typingPauseTimer.stop();
      lastUserEditAtMs = 0L;
      lastActiveSentAtMs = 0L;
      emitTypingState("done");
      return;
    }

    lastUserEditAtMs = now;

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
      lastUserEditAtMs = 0L;
      lastActiveSentAtMs = 0L;
      emitTypingState("done");
      return;
    }

    if (text.startsWith("/")) {
      lastUserEditAtMs = 0L;
      lastActiveSentAtMs = 0L;
      emitTypingState("done");
      return;
    }

    emitTypingState("paused");
  }

  private boolean typingIndicatorsEnabled() {
    try {
      UiSettings s = settingsSupplier.get();
      // Default: enabled if settings are not available yet.
      return s == null || s.typingIndicatorsEnabled();
    } catch (Exception ex) {
      return true;
    }
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
    if (!typingIndicatorsEnabled() && !"done".equals(normalized)) {
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
}
