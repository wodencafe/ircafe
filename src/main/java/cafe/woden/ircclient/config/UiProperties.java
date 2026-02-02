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

    /**
     * How many historical log lines to load into a transcript when selecting a target.
     *
     * <p>If &lt;= 0, history prefill is disabled.
     */
    Integer chatHistoryInitialLoadLines,

    /**
     * Page size for the in-transcript "Load older messages…" control.
     *
     * <p>If &lt;= 0, the paging control will still render, but loads will fall back to a safe default.
     */
    Integer chatHistoryPageSize,

    /**
     * If enabled, render *outgoing* messages (lines you send that are locally echoed into the transcript)
     * using a custom foreground color. Default: false.
     */
    Boolean clientLineColorEnabled,

    /** Foreground color for outgoing message lines (hex like "#RRGGBB"). */
    String clientLineColor,


    /** Enable embedding inline image previews from direct image links. Default: false. */
    Boolean imageEmbedsEnabled,

    /** If enabled, newly inserted inline images start collapsed (you can expand per-image). Default: false. */
    Boolean imageEmbedsCollapsedByDefault,

    /**
     * Maximum inline image embed width (pixels).
     *
     * <p>If <= 0, no additional cap is applied (images scale down only to fit the chat viewport).
     */
    Integer imageEmbedsMaxWidthPx,

    /**
     * Maximum inline image embed height (pixels).
     *
     * <p>If <= 0, no additional cap is applied (images scale down only to fit the chat viewport).
     */
    Integer imageEmbedsMaxHeightPx,


    /**
     * Enable animated GIF playback for inline image embeds.
     *
     * <p>If false, GIFs are rendered as a still image using the first frame.
     */
    Boolean imageEmbedsAnimateGifs,

    /**
     * Hostmask discovery settings.
     *
     * <p>IRCafe prefers the IRCv3 {@code userhost-in-names} capability (free, no extra traffic).
     * If hostmasks are still missing and you have hostmask-based ignore rules configured, IRCafe can
     * carefully use {@code USERHOST} with conservative anti-flood limits.
     */
    HostmaskDiscovery hostmaskDiscovery,

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

  /**
   * Hostmask discovery configuration.
   *
   * <p>Defaults are intentionally conservative:
   * <ul>
   *   <li>{@code userhostEnabled=true}
   *   <li>{@code userhostMinIntervalSeconds=7}
   *   <li>{@code userhostMaxCommandsPerMinute=6}
   *   <li>{@code userhostNickCooldownMinutes=30}
   *   <li>{@code userhostMaxNicksPerCommand=5} (most servers allow up to 5)
   * </ul>
   */
  public record HostmaskDiscovery(
      Boolean userhostEnabled,
      Integer userhostMinIntervalSeconds,
      Integer userhostMaxCommandsPerMinute,
      Integer userhostNickCooldownMinutes,
      Integer userhostMaxNicksPerCommand
  ) {
    public HostmaskDiscovery {
      if (userhostEnabled == null) userhostEnabled = true;
      if (userhostMinIntervalSeconds == null || userhostMinIntervalSeconds <= 0) userhostMinIntervalSeconds = 7;
      if (userhostMaxCommandsPerMinute == null || userhostMaxCommandsPerMinute <= 0) userhostMaxCommandsPerMinute = 6;
      if (userhostNickCooldownMinutes == null || userhostNickCooldownMinutes <= 0) userhostNickCooldownMinutes = 30;
      if (userhostMaxNicksPerCommand == null || userhostMaxNicksPerCommand <= 0) userhostMaxNicksPerCommand = 5;
      if (userhostMaxNicksPerCommand > 5) userhostMaxNicksPerCommand = 5;
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

    // History defaults: conservative initial load, generous paging.
    if (chatHistoryInitialLoadLines == null) {
      chatHistoryInitialLoadLines = 100;
    }
    if (chatHistoryInitialLoadLines < 0) {
      chatHistoryInitialLoadLines = 0;
    }

    if (chatHistoryPageSize == null || chatHistoryPageSize <= 0) {
      chatHistoryPageSize = 200;
    }

    // Outgoing message color default: disabled.
    if (clientLineColorEnabled == null) {
      clientLineColorEnabled = false;
    }
    // Default outgoing message color if enabled but not set explicitly.
    clientLineColor = normalizeHexOrDefault(clientLineColor, "#6AA2FF");


    // Image embeds default: disabled.
    if (imageEmbedsEnabled == null) {
      imageEmbedsEnabled = false;
    }

    // Image embeds collapsed-by-default default: false (preserve current behavior).
    if (imageEmbedsCollapsedByDefault == null) {
      imageEmbedsCollapsedByDefault = false;
    }

    // Image embed max width default: 0 (no extra cap).
    if (imageEmbedsMaxWidthPx == null || imageEmbedsMaxWidthPx <= 0) {
      imageEmbedsMaxWidthPx = 0;
    }

    // Image embed max height default: 0 (no extra cap).
    if (imageEmbedsMaxHeightPx == null || imageEmbedsMaxHeightPx <= 0) {
      imageEmbedsMaxHeightPx = 0;
    }

    // GIF animation default: enabled.
    if (imageEmbedsAnimateGifs == null) {
      imageEmbedsAnimateGifs = true;
    }

    // Hostmask discovery defaults.
    if (hostmaskDiscovery == null) {
      hostmaskDiscovery = new HostmaskDiscovery(true, 7, 6, 30, 5);
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

  static String normalizeHexOrDefault(String raw, String fallback) {
    String fb = (fallback == null || fallback.isBlank()) ? "#6AA2FF" : fallback.trim();
    if (raw == null) return fb;
    String s = raw.trim();
    if (s.isEmpty()) return fb;
    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
    if (s.length() != 6) return fb;
    try {
      int rgb = Integer.parseInt(s, 16);
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = (rgb) & 0xFF;
      return String.format("#%02X%02X%02X", r, g, b);
    } catch (Exception ignored) {
      return fb;
    }
  }

}
