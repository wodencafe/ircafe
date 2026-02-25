package cafe.woden.ircclient.ui.settings;

import java.util.Locale;

/**
 * Chat transcript theme overrides.
 *
 * <p>These settings only affect chat rendering (timestamps, status/system lines, per-message
 * colors, mention highlights). They are layered on top of the active Look & Feel.
 */
public record ChatThemeSettings(
    Preset preset,
    String timestampColor,
    String systemColor,
    String mentionBgColor,
    int mentionStrength,
    String messageColor,
    String noticeColor,
    String actionColor,
    String errorColor,
    String presenceColor) {

  public ChatThemeSettings {
    if (preset == null) preset = Preset.DEFAULT;
    mentionStrength = clamp01(mentionStrength);
  }

  public enum Preset {
    /** Follow the Look & Feel defaults (with IRCafe's existing subtle mention highlight). */
    DEFAULT,
    /** Subtle: dimmer timestamps/system lines and a softer mention highlight. */
    SOFT,
    /** Use the app accent/link color for timestamps and mention highlights. */
    ACCENTED,
    /** Stronger contrast for timestamps/system lines and a punchier mention highlight. */
    HIGH_CONTRAST;

    public static Preset from(String raw) {
      if (raw == null) return DEFAULT;
      String s = raw.trim();
      if (s.isEmpty()) return DEFAULT;
      try {
        return Preset.valueOf(s.toUpperCase(Locale.ROOT));
      } catch (Exception ignored) {
        return DEFAULT;
      }
    }
  }

  private static int clamp01(int v) {
    return Math.max(0, Math.min(100, v));
  }
}
