package cafe.woden.ircclient.ui.userlist;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Tracks per-nick typing state and animation alpha for the users list. */
public final class UserListTypingIndicators {

  private static final int TYPING_FADE_IN_MS = 220;
  private static final int TYPING_HOLD_MS = 8000;
  private static final int TYPING_FADE_OUT_MS = 900;
  private static final int TYPING_PULSE_MS = 1200;

  private final Map<String, TypingIndicatorState> typingByNick = new HashMap<>();

  public static String foldNick(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return null;
    return n.toLowerCase(Locale.ROOT);
  }

  public boolean isEmpty() {
    return typingByNick.isEmpty();
  }

  public void clear() {
    typingByNick.clear();
  }

  public Set<String> activeKeysSnapshot() {
    return new LinkedHashSet<>(typingByNick.keySet());
  }

  public float alphaForKey(String key, long now) {
    if (key == null || key.isBlank()) return 0f;
    TypingIndicatorState state = typingByNick.get(key);
    if (state == null) return 0f;
    return state.alpha(now);
  }

  public boolean isPausedKey(String key) {
    if (key == null || key.isBlank()) return false;
    TypingIndicatorState state = typingByNick.get(key);
    return state != null && state.isPaused();
  }

  public void onTyping(String key, String rawState, long now) {
    if (key == null || key.isBlank()) return;
    TypingIndicatorState indicator =
        typingByNick.computeIfAbsent(key, __ -> new TypingIndicatorState());
    indicator.apply(rawState, now);
    indicator.expireIfNeeded(now);
    if (indicator.isFinished(now)) {
      typingByNick.remove(key);
    }
  }

  public boolean pruneToKnownNicks(Set<String> knownNickKeys) {
    if (typingByNick.isEmpty()) return false;
    if (knownNickKeys == null || knownNickKeys.isEmpty()) {
      boolean hadAny = !typingByNick.isEmpty();
      typingByNick.clear();
      return hadAny;
    }
    return typingByNick.keySet().removeIf(k -> k == null || !knownNickKeys.contains(k));
  }

  public TickOutcome tick(long now) {
    boolean visible = false;
    boolean changed = false;

    Iterator<Map.Entry<String, TypingIndicatorState>> it = typingByNick.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, TypingIndicatorState> e = it.next();
      TypingIndicatorState state = e.getValue();
      if (state == null) {
        it.remove();
        changed = true;
        continue;
      }
      state.expireIfNeeded(now);
      if (state.isFinished(now)) {
        it.remove();
        changed = true;
        continue;
      }
      if (state.alpha(now) > 0.01f) {
        visible = true;
      }
    }

    return new TickOutcome(changed, visible, !typingByNick.isEmpty());
  }

  public record TickOutcome(boolean changed, boolean hasVisible, boolean hasIndicators) {}

  private enum TypingVisualState {
    ACTIVE,
    PAUSED,
    FADING
  }

  private static final class TypingIndicatorState {
    private TypingVisualState mode = TypingVisualState.ACTIVE;
    private long visibleSinceMs = 0L;
    private long expiresAtMs = 0L;
    private long fadeStartedMs = 0L;
    private float fadeFromAlpha = 0f;

    void apply(String rawState, long now) {
      String state = normalizeTypingState(rawState);
      if ("done".equals(state)) {
        startFade(now, alpha(now));
        return;
      }

      if (!isVisible(now)) {
        visibleSinceMs = now;
      }
      mode = "paused".equals(state) ? TypingVisualState.PAUSED : TypingVisualState.ACTIVE;
      expiresAtMs = now + TYPING_HOLD_MS;
      fadeStartedMs = 0L;
      fadeFromAlpha = 0f;
    }

    void expireIfNeeded(long now) {
      if (mode == TypingVisualState.FADING) return;
      if (expiresAtMs > 0L && now >= expiresAtMs) {
        startFade(now, alpha(now));
      }
    }

    boolean isFinished(long now) {
      if (mode != TypingVisualState.FADING) return false;
      return now - fadeStartedMs >= TYPING_FADE_OUT_MS;
    }

    boolean isPaused() {
      return mode == TypingVisualState.PAUSED;
    }

    float alpha(long now) {
      if (mode == TypingVisualState.FADING) {
        if (fadeStartedMs <= 0L) return 0f;
        long elapsed = Math.max(0L, now - fadeStartedMs);
        if (elapsed >= TYPING_FADE_OUT_MS) return 0f;
        float progress = elapsed / (float) TYPING_FADE_OUT_MS;
        return Math.max(0f, fadeFromAlpha * (1f - progress));
      }

      float fadeIn = 1f;
      if (visibleSinceMs > 0L) {
        fadeIn = Math.max(0f, Math.min(1f, (now - visibleSinceMs) / (float) TYPING_FADE_IN_MS));
      }
      if (mode == TypingVisualState.PAUSED) {
        return 0.38f * fadeIn;
      }

      double phase = (now % TYPING_PULSE_MS) / (double) TYPING_PULSE_MS;
      double wave = 0.5d + (0.5d * Math.sin((phase * (Math.PI * 2d)) - (Math.PI / 2d)));
      float pulse = (float) (0.45d + (0.55d * wave));
      return Math.max(0f, Math.min(1f, fadeIn * pulse));
    }

    private void startFade(long now, float fromAlpha) {
      mode = TypingVisualState.FADING;
      fadeStartedMs = now;
      expiresAtMs = 0L;
      fadeFromAlpha = Math.max(0f, Math.min(1f, fromAlpha));
    }

    private boolean isVisible(long now) {
      return alpha(now) > 0.01f;
    }
  }

  private static String normalizeTypingState(String state) {
    String s = Objects.toString(state, "").trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "active";
    };
  }
}
