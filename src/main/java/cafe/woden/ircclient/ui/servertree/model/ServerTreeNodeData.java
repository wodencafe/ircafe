package cafe.woden.ircclient.ui.servertree.model;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Objects;

/** Tree-node user object for target identity, counters, and transient typing metadata. */
public class ServerTreeNodeData {
  public final TargetRef ref;
  public final String label;
  public int unread = 0;
  public int highlightUnread = 0;
  public boolean detached = false;
  public String detachedWarning = "";
  public long typingPulseUntilMs = 0L;
  public long typingDoneFadeStartMs = 0L;

  public ServerTreeNodeData(TargetRef ref, String label) {
    this.ref = ref;
    this.label = label;
  }

  public void copyTypingFrom(ServerTreeNodeData other) {
    if (other == null) return;
    this.typingPulseUntilMs = other.typingPulseUntilMs;
    this.typingDoneFadeStartMs = other.typingDoneFadeStartMs;
  }

  public boolean hasTypingActivity() {
    return typingPulseUntilMs > 0L || typingDoneFadeStartMs > 0L;
  }

  public boolean hasDetachedWarning() {
    return detached && !Objects.toString(detachedWarning, "").trim().isEmpty();
  }

  public boolean clearTypingActivityNow() {
    long prevPulse = typingPulseUntilMs;
    long prevFade = typingDoneFadeStartMs;
    typingPulseUntilMs = 0L;
    typingDoneFadeStartMs = 0L;
    return prevPulse != typingPulseUntilMs || prevFade != typingDoneFadeStartMs;
  }

  public boolean applyTypingState(String state, long now, int holdMs) {
    long prevPulse = typingPulseUntilMs;
    long prevFade = typingDoneFadeStartMs;
    String normalized = normalizeTypingState(state);
    if ("done".equals(normalized)) {
      if (hasTypingActivity()) {
        typingPulseUntilMs = 0L;
        typingDoneFadeStartMs = now;
      }
    } else {
      long until = now + Math.max(500L, holdMs);
      if (until > typingPulseUntilMs) {
        typingPulseUntilMs = until;
      }
      typingDoneFadeStartMs = 0L;
    }
    return prevPulse != typingPulseUntilMs || prevFade != typingDoneFadeStartMs;
  }

  public void clearTypingActivityIfExpired(long now, int fadeMs) {
    int fadeWindow = Math.max(1, fadeMs);
    if (typingDoneFadeStartMs > 0L) {
      if (now - typingDoneFadeStartMs >= fadeWindow) {
        typingPulseUntilMs = 0L;
        typingDoneFadeStartMs = 0L;
      }
      return;
    }
    if (typingPulseUntilMs <= 0L) return;
    if (now - typingPulseUntilMs >= fadeWindow) {
      typingPulseUntilMs = 0L;
    }
  }

  public float typingDotAlpha(long now, int pulseMs, int fadeMs) {
    int pulseWindow = Math.max(300, pulseMs);
    int fadeWindow = Math.max(1, fadeMs);

    if (typingDoneFadeStartMs > 0L) {
      return fadeAlpha(now, typingDoneFadeStartMs, fadeWindow);
    }
    if (typingPulseUntilMs <= 0L) return 0f;
    if (now < typingPulseUntilMs) {
      double phase = (now % pulseWindow) / (double) pulseWindow;
      double wave = 0.5d + (0.5d * Math.sin((phase * (Math.PI * 2.0d)) - (Math.PI / 2.0d)));
      return (float) (0.35d + (0.65d * wave));
    }
    return fadeAlpha(now, typingPulseUntilMs, fadeWindow);
  }

  private static float fadeAlpha(long now, long fadeStartMs, int fadeWindowMs) {
    if (fadeStartMs <= 0L) return 0f;
    long elapsed = now - fadeStartMs;
    if (elapsed <= 0L) return 1f;
    if (elapsed >= fadeWindowMs) return 0f;
    float progress = elapsed / (float) fadeWindowMs;
    return Math.max(0f, 1f - progress);
  }

  private static String normalizeTypingState(String state) {
    String s = Objects.toString(state, "").trim().toLowerCase(java.util.Locale.ROOT);
    return switch (s) {
      case "active", "composing", "paused" -> "active";
      case "done", "inactive" -> "done";
      default -> "active";
    };
  }

  @Override
  public String toString() {
    return label;
  }
}
