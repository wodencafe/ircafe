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

    Boolean autoConnectOnStart,

    Timestamps timestamps,

    
    Boolean chatMessageTimestampsEnabled,

    Integer chatHistoryInitialLoadLines,

    Integer chatHistoryPageSize,

    Boolean clientLineColorEnabled,

    
    String clientLineColor,

    Boolean imageEmbedsEnabled,

    Boolean imageEmbedsCollapsedByDefault,

    Integer imageEmbedsMaxWidthPx,

    Integer imageEmbedsMaxHeightPx,

    Boolean imageEmbedsAnimateGifs,

    /**
     * Hostmask discovery settings.
     *
     * <p>IRCafe prefers the IRCv3 {@code userhost-in-names} capability (free, no extra traffic).
     * If hostmasks are still missing and you have hostmask-based ignore rules configured, IRCafe can
     * carefully use {@code USERHOST} with conservative anti-flood limits.
     */
    HostmaskDiscovery hostmaskDiscovery,

    Boolean linkPreviewsEnabled,

    Boolean linkPreviewsCollapsedByDefault,

    Boolean nickColoringEnabled,

    Boolean presenceFoldsEnabled,

    double nickColorMinContrast,

    List<String> nickColors,

    Map<String, String> nickColorOverrides,

    Layout layout
) {

  /**
   * Docking/layout defaults.
   *
   * <p>These sizes are used as a best-effort "first open" hint. After the user drags split dividers,
   * those new sizes are preserved by the split-pane lock logic.
   */
  public record Layout(
      Integer serverDockWidthPx,
      Integer userDockWidthPx,
      Integer inputDockHeightPx
  ) {
    public Layout {
      if (serverDockWidthPx == null || serverDockWidthPx <= 0) serverDockWidthPx = 280;
      if (userDockWidthPx == null || userDockWidthPx <= 0) userDockWidthPx = 240;
      // The input dock is primarily height-locked; keep a reasonable default.
      if (inputDockHeightPx == null || inputDockHeightPx <= 0) inputDockHeightPx = 140;
    }
  }

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

    // Default: true (preserve prior behavior where IRCafe auto-connects on startup).
    if (autoConnectOnStart == null) {
      autoConnectOnStart = true;
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

    if (layout == null) {
      layout = new Layout(null, null, null);
    }

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
