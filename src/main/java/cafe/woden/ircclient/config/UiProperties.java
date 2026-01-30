package cafe.woden.ircclient.config;

import java.awt.Font;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI configuration.
 *
 * <p>These values can be overridden by the runtime YAML file imported via
 * {@code spring.config.import}.
 */
@ConfigurationProperties(prefix = "ircafe.ui")
public record UiProperties(
    String theme,
    String chatFontFamily,
    int chatFontSize,
    Timestamps timestamps,

    /** If enabled, prepend timestamps to regular user chat messages (not just status/notice lines). */
    Boolean chatMessageTimestampsEnabled,

    /** Enable embedding inline image previews from direct image links. Default: false. */
    Boolean imageEmbedsEnabled,

    /** If enabled, newly inserted inline images start collapsed (you can expand per-image). Default: false. */
    Boolean imageEmbedsCollapsedByDefault,

    /** Enable Discord/Signal-style link preview "cards" for regular web pages. Default: false. */
    Boolean linkPreviewsEnabled,

    /** If enabled, newly inserted link preview cards start collapsed (you can expand per-card). Default: false. */
    Boolean linkPreviewsCollapsedByDefault,

    /** Enable per-nick coloring in chat + user list. */
    Boolean nickColoringEnabled,

    /** Enable folding/collapsing of presence noise in channel transcripts (join/part/quit/nick). */
    Boolean presenceFoldsEnabled,

    /** Minimum contrast ratio against the current background (WCAG-style). */
    double nickColorMinContrast,

    /** Palette used for deterministic nick colors (hex strings like "#RRGGBB"). */
    List<String> nickColors,

    /** Optional per-nick color overrides (case-insensitive keys, hex color values). */
    Map<String, String> nickColorOverrides
) {

  /**
   * Chat/status timestamp settings.
   *
   * <p>{@code format} uses Java time patterns (same general style as
   * {@link java.text.SimpleDateFormat}, but powered by {@link java.time.format.DateTimeFormatter}).
   */
  public record Timestamps(boolean enabled, String format) {
    public Timestamps {
      if (format == null || format.isBlank()) {
        format = "HH:mm:ss";
      }
    }
  }

  public UiProperties {
    if (theme == null || theme.isBlank()) {
      theme = "dark";
    }
    if (chatFontFamily == null || chatFontFamily.isBlank()) {
      chatFontFamily = Font.MONOSPACED;
    }
    if (chatFontSize <= 0) {
      chatFontSize = 12;
    }
    if (timestamps == null) {
      timestamps = new Timestamps(true, "HH:mm:ss");
    }

    // Default: disabled (preserve prior behavior where user messages have no timestamp prefix).
    if (chatMessageTimestampsEnabled == null) {
      chatMessageTimestampsEnabled = false;
    }

    // Image embeds default: disabled.
    if (imageEmbedsEnabled == null) {
      imageEmbedsEnabled = false;
    }

    // Image embeds collapsed-by-default default: false (preserve current behavior).
    if (imageEmbedsCollapsedByDefault == null) {
      imageEmbedsCollapsedByDefault = false;
    }

    // Link previews default: disabled (privacy + extra network traffic).
    if (linkPreviewsEnabled == null) {
      linkPreviewsEnabled = false;
    }

    // Link previews collapsed-by-default default: false (preserve current behavior).
    if (linkPreviewsCollapsedByDefault == null) {
      linkPreviewsCollapsedByDefault = false;
    }
    // Nick coloring defaults.
    if (nickColorMinContrast <= 0) {
      nickColorMinContrast = 3.0;
    }
    // Default: enabled.
    if (nickColoringEnabled == null) {
      nickColoringEnabled = true;
    }

    // Presence fold default: enabled.
    if (presenceFoldsEnabled == null) {
      presenceFoldsEnabled = true;
    }
    if (nickColors == null) {
      nickColors = List.of();
    }
    if (nickColorOverrides == null) {
      nickColorOverrides = Map.of();
    }
  }
}
