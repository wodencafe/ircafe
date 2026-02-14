package cafe.woden.ircclient.config;

import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    
    @Deprecated Boolean chatMessageTimestampsEnabled,

    Integer chatHistoryInitialLoadLines,

    Integer chatHistoryPageSize,

    Integer commandHistoryMaxSize,


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

    /**
     * General user info enrichment (fallback) settings.
     *
     * <p>IRCafe prefers IRCv3 (account-notify, extended-join, account-tag, userhost-in-names) because
     * it is accurate and does not require extra traffic.
     *
     * <p>When IRCv3 support is missing, IRCafe can optionally and conservatively enrich user metadata
     * using {@code USERHOST} (hostmask + away flag) and, if explicitly enabled, {@code WHOIS}
     * (account name + away message). These are rate-limited and disabled by default.
     */
    UserInfoEnrichment userInfoEnrichment,

    Boolean linkPreviewsEnabled,

    Boolean linkPreviewsCollapsedByDefault,

    Boolean nickColoringEnabled,

    Boolean presenceFoldsEnabled,

    /**
     * If enabled, inbound CTCP requests are rendered into the currently active chat target (same server).
     * If disabled, they are routed to their origin target (channel/PM) instead.
     */
    Boolean ctcpRequestsInActiveTargetEnabled,

    /**
     * User-configured keyword / regex notification rules.
     *
     * <p>These are evaluated only for channel messages.
     */
    List<NotificationRuleProperties> notificationRules,

    /** Cooldown (seconds) to dedupe repeated rule-match notifications per channel + rule. */
    Integer notificationRuleCooldownSeconds,


    double nickColorMinContrast,

    List<String> nickColors,

    Map<String, String> nickColorOverrides,

    Filters filters,

    Layout layout
) {

  /**
   * WeeChat-style message filters.
   *
   * <p>Filters are evaluated at render time only. They never affect logging.
   */
  public record Filters(
      Boolean enabledByDefault,
      Boolean placeholdersEnabledByDefault,
      Boolean placeholdersCollapsedByDefault,
      Integer placeholderMaxPreviewLines,
      List<FilterRuleProperties> rules,
      List<FilterScopeOverrideProperties> overrides
  ) {
    public Filters {
      if (enabledByDefault == null) enabledByDefault = true;
      if (placeholdersEnabledByDefault == null) placeholdersEnabledByDefault = true;
      if (placeholdersCollapsedByDefault == null) placeholdersCollapsedByDefault = true;

      if (placeholderMaxPreviewLines == null || placeholderMaxPreviewLines < 0) {
        placeholderMaxPreviewLines = 3;
      }
      if (placeholderMaxPreviewLines > 25) {
        placeholderMaxPreviewLines = 25;
      }

      rules = (rules == null) ? List.of() : rules.stream().filter(Objects::nonNull).toList();
      overrides = (overrides == null) ? List.of() : overrides.stream().filter(Objects::nonNull).toList();
    }
  }

  /**
   * Docking/layout defaults.
   *
   * <p>These sizes are used as a best-effort "first open" hint. After the user drags split dividers,
   * those new sizes are preserved by the split-pane lock logic.
   */
  public record Layout(
      Integer serverDockWidthPx,
      Integer userDockWidthPx
  ) {
    public Layout {
      if (serverDockWidthPx == null || serverDockWidthPx <= 0) serverDockWidthPx = 280;
      if (userDockWidthPx == null || userDockWidthPx <= 0) userDockWidthPx = 240;
    }
  }

  /**
   * Chat/status timestamp settings.
   *
   * <p>{@code format} uses Java time patterns (same general style as
   * {@link java.text.SimpleDateFormat}, but powered by {@link java.time.format.DateTimeFormatter}).
   */
  public record Timestamps(
      Boolean enabled,
      String format,
      Boolean includeChatMessages
  ) {
    public Timestamps {
      if (enabled == null) enabled = true;
      if (format == null || format.isBlank()) format = "HH:mm:ss";
      if (includeChatMessages == null) includeChatMessages = false;
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

  /**
   * General user info enrichment configuration.
   *
   * <p>This is a best-effort fallback used when IRCv3 capabilities are unavailable or disabled.
   * Defaults are intentionally conservative and disabled by default:
   * <ul>
   *   <li>{@code enabled=false}
   *   <li>{@code userhostMinIntervalSeconds=15}
   *   <li>{@code userhostMaxCommandsPerMinute=3}
   *   <li>{@code userhostNickCooldownMinutes=60}
   *   <li>{@code userhostMaxNicksPerCommand=5}
   *   <li>{@code whoisFallbackEnabled=false}
   *   <li>{@code whoisMinIntervalSeconds=45}
   *   <li>{@code whoisNickCooldownMinutes=120}
   *   <li>{@code periodicRefreshEnabled=false}
   *   <li>{@code periodicRefreshIntervalSeconds=300}
   *   <li>{@code periodicRefreshNicksPerTick=2}
   * </ul>
   */
  public record UserInfoEnrichment(
      Boolean enabled,
      Integer userhostMinIntervalSeconds,
      Integer userhostMaxCommandsPerMinute,
      Integer userhostNickCooldownMinutes,
      Integer userhostMaxNicksPerCommand,

      Boolean whoisFallbackEnabled,
      Integer whoisMinIntervalSeconds,
      Integer whoisNickCooldownMinutes,

      Boolean periodicRefreshEnabled,
      Integer periodicRefreshIntervalSeconds,
      Integer periodicRefreshNicksPerTick
  ) {
    public UserInfoEnrichment {
      if (enabled == null) enabled = false;

      if (userhostMinIntervalSeconds == null || userhostMinIntervalSeconds <= 0) userhostMinIntervalSeconds = 15;
      if (userhostMaxCommandsPerMinute == null || userhostMaxCommandsPerMinute <= 0) userhostMaxCommandsPerMinute = 3;
      if (userhostNickCooldownMinutes == null || userhostNickCooldownMinutes <= 0) userhostNickCooldownMinutes = 60;
      if (userhostMaxNicksPerCommand == null || userhostMaxNicksPerCommand <= 0) userhostMaxNicksPerCommand = 5;
      if (userhostMaxNicksPerCommand > 5) userhostMaxNicksPerCommand = 5;

      // Must be explicitly enabled (off by default).
      if (whoisFallbackEnabled == null) whoisFallbackEnabled = false;

      if (whoisMinIntervalSeconds == null || whoisMinIntervalSeconds <= 0) whoisMinIntervalSeconds = 45;
      if (whoisNickCooldownMinutes == null || whoisNickCooldownMinutes <= 0) whoisNickCooldownMinutes = 120;

      if (periodicRefreshEnabled == null) periodicRefreshEnabled = false;
      if (periodicRefreshIntervalSeconds == null || periodicRefreshIntervalSeconds <= 0) periodicRefreshIntervalSeconds = 300;
      if (periodicRefreshNicksPerTick == null || periodicRefreshNicksPerTick <= 0) periodicRefreshNicksPerTick = 2;
      if (periodicRefreshNicksPerTick > 10) periodicRefreshNicksPerTick = 10;
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

    // Default: disabled (preserve prior behavior where user messages have no timestamp prefix).
    if (chatMessageTimestampsEnabled == null) {
      chatMessageTimestampsEnabled = false;
    }

    if (timestamps == null) {
      timestamps = new Timestamps(true, "HH:mm:ss", chatMessageTimestampsEnabled);
    } else if (timestamps.includeChatMessages() == null) {
      // Back-compat: if the new nested flag is absent, fall back to the legacy top-level flag.
      timestamps = new Timestamps(timestamps.enabled(), timestamps.format(), chatMessageTimestampsEnabled);
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
      layout = new Layout(null, null);
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

    // User info enrichment defaults (disabled by default).
    if (userInfoEnrichment == null) {
      userInfoEnrichment = new UserInfoEnrichment(false, 15, 3, 60, 5, false, 45, 120, false, 300, 2);
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

    // CTCP request routing default: show in the currently active target.
    if (ctcpRequestsInActiveTargetEnabled == null) {
      ctcpRequestsInActiveTargetEnabled = true;
    }

    // Filter defaults.
    if (filters == null) {
      filters = new Filters(null, null, null, null, null, null);
    }

    if (notificationRules == null) {
      notificationRules = List.of();
    } else {
      notificationRules = notificationRules.stream().filter(Objects::nonNull).toList();
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