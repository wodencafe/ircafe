package cafe.woden.ircclient.ui.settings;

import java.util.List;

public record UiSettings(
    String theme,
    String chatFontFamily,
    int chatFontSize,

    boolean autoConnectOnStart,

    boolean trayEnabled,

    boolean trayCloseToTray,

    boolean trayMinimizeToTray,

    boolean trayStartMinimized,

    boolean trayNotifyHighlights,

    boolean trayNotifyPrivateMessages,

    boolean trayNotifyConnectionState,

    boolean trayNotifyOnlyWhenUnfocused,

    boolean trayNotifyOnlyWhenMinimizedOrHidden,

    boolean trayNotifySuppressWhenTargetActive,

    boolean trayLinuxDbusActionsEnabled,

    boolean imageEmbedsEnabled,
    boolean imageEmbedsCollapsedByDefault,

    int imageEmbedsMaxWidthPx,

    int imageEmbedsMaxHeightPx,

    boolean imageEmbedsAnimateGifs,

    boolean linkPreviewsEnabled,
    boolean linkPreviewsCollapsedByDefault,

    boolean presenceFoldsEnabled,

    boolean ctcpRequestsInActiveTargetEnabled,

    boolean typingIndicatorsEnabled,

    boolean timestampsEnabled,

    String timestampFormat,

    boolean timestampsIncludeChatMessages,
    boolean timestampsIncludePresenceMessages,

    int chatHistoryInitialLoadLines,

    int chatHistoryPageSize,

    int commandHistoryMaxSize,

    boolean clientLineColorEnabled,

    String clientLineColor,

    // --- Hostmask discovery / USERHOST anti-flood ---

    boolean userhostDiscoveryEnabled,

    int userhostMinIntervalSeconds,

    int userhostMaxCommandsPerMinute,

    int userhostNickCooldownMinutes,

    int userhostMaxNicksPerCommand,

    // --- General user info enrichment (fallback) ---

    boolean userInfoEnrichmentEnabled,

    int userInfoEnrichmentUserhostMinIntervalSeconds,

    int userInfoEnrichmentUserhostMaxCommandsPerMinute,

    int userInfoEnrichmentUserhostNickCooldownMinutes,

    int userInfoEnrichmentUserhostMaxNicksPerCommand,

    boolean userInfoEnrichmentWhoisFallbackEnabled,

    int userInfoEnrichmentWhoisMinIntervalSeconds,

    int userInfoEnrichmentWhoisNickCooldownMinutes,

    boolean userInfoEnrichmentPeriodicRefreshEnabled,

    int userInfoEnrichmentPeriodicRefreshIntervalSeconds,

    int userInfoEnrichmentPeriodicRefreshNicksPerTick,

    int notificationRuleCooldownSeconds,

    List<NotificationRule> notificationRules
) {

  public UiSettings {
    // Preferred default theme (A): Darcula.
    if (theme == null || theme.isBlank()) theme = "darcula";
    if (chatFontFamily == null || chatFontFamily.isBlank()) chatFontFamily = "Monospaced";
    if (chatFontSize <= 0) chatFontSize = 12;

    // Tray settings: if the tray is disabled, ensure tray behaviors are off too.
    if (!trayEnabled) {
      trayCloseToTray = false;
      trayMinimizeToTray = false;
      trayStartMinimized = false;

      trayNotifyHighlights = false;
      trayNotifyPrivateMessages = false;
      trayNotifyConnectionState = false;

      trayNotifyOnlyWhenUnfocused = false;
      trayNotifyOnlyWhenMinimizedOrHidden = false;
      trayNotifySuppressWhenTargetActive = false;

      trayLinuxDbusActionsEnabled = false;
    }

    if (imageEmbedsMaxWidthPx < 0) imageEmbedsMaxWidthPx = 0;
    if (imageEmbedsMaxHeightPx < 0) imageEmbedsMaxHeightPx = 0;

    if (timestampFormat == null || timestampFormat.isBlank()) timestampFormat = "HH:mm:ss";

    if (chatHistoryInitialLoadLines < 0) chatHistoryInitialLoadLines = 0;
    if (chatHistoryPageSize <= 0) chatHistoryPageSize = 200;

    if (commandHistoryMaxSize <= 0) commandHistoryMaxSize = 500;
    if (commandHistoryMaxSize > 500) commandHistoryMaxSize = 500;

    clientLineColor = normalizeHexOrDefault(clientLineColor, "#6AA2FF");

    // Hostmask discovery / USERHOST anti-flood
    if (userhostMinIntervalSeconds <= 0) userhostMinIntervalSeconds = 7;
    if (userhostMaxCommandsPerMinute <= 0) userhostMaxCommandsPerMinute = 6;
    if (userhostNickCooldownMinutes <= 0) userhostNickCooldownMinutes = 30;
    if (userhostMaxNicksPerCommand <= 0) userhostMaxNicksPerCommand = 5;
    if (userhostMaxNicksPerCommand > 5) userhostMaxNicksPerCommand = 5;

    // General user info enrichment (fallback)
    if (userInfoEnrichmentUserhostMinIntervalSeconds <= 0) userInfoEnrichmentUserhostMinIntervalSeconds = 15;
    if (userInfoEnrichmentUserhostMaxCommandsPerMinute <= 0) userInfoEnrichmentUserhostMaxCommandsPerMinute = 3;
    if (userInfoEnrichmentUserhostNickCooldownMinutes <= 0) userInfoEnrichmentUserhostNickCooldownMinutes = 60;
    if (userInfoEnrichmentUserhostMaxNicksPerCommand <= 0) userInfoEnrichmentUserhostMaxNicksPerCommand = 5;
    if (userInfoEnrichmentUserhostMaxNicksPerCommand > 5) userInfoEnrichmentUserhostMaxNicksPerCommand = 5;

    if (userInfoEnrichmentWhoisMinIntervalSeconds <= 0) userInfoEnrichmentWhoisMinIntervalSeconds = 45;
    if (userInfoEnrichmentWhoisNickCooldownMinutes <= 0) userInfoEnrichmentWhoisNickCooldownMinutes = 120;

    if (userInfoEnrichmentPeriodicRefreshIntervalSeconds <= 0) userInfoEnrichmentPeriodicRefreshIntervalSeconds = 300;
    if (userInfoEnrichmentPeriodicRefreshNicksPerTick <= 0) userInfoEnrichmentPeriodicRefreshNicksPerTick = 2;
    if (userInfoEnrichmentPeriodicRefreshNicksPerTick > 10) userInfoEnrichmentPeriodicRefreshNicksPerTick = 10;

    if (notificationRuleCooldownSeconds < 0) notificationRuleCooldownSeconds = 15;
    if (notificationRuleCooldownSeconds > 3600) notificationRuleCooldownSeconds = 3600;

    if (notificationRules == null) notificationRules = List.of();
  }

  /**
   * Back-compat constructor to keep older call sites compiling.
   *
   * <p>Newer user info enrichment fields are defaulted to conservative values and disabled by default.
   */
  public UiSettings(
      String theme,
      String chatFontFamily,
      int chatFontSize,
      boolean autoConnectOnStart,

      boolean imageEmbedsEnabled,
      boolean imageEmbedsCollapsedByDefault,

      int imageEmbedsMaxWidthPx,

      int imageEmbedsMaxHeightPx,

      boolean imageEmbedsAnimateGifs,

      boolean linkPreviewsEnabled,
      boolean linkPreviewsCollapsedByDefault,

      boolean presenceFoldsEnabled,

      boolean ctcpRequestsInActiveTargetEnabled,

      boolean timestampsEnabled,

      String timestampFormat,

      boolean timestampsIncludeChatMessages,

      int chatHistoryInitialLoadLines,

      int chatHistoryPageSize,

      boolean clientLineColorEnabled,

      String clientLineColor,

      boolean userhostDiscoveryEnabled,

      int userhostMinIntervalSeconds,

      int userhostMaxCommandsPerMinute,

      int userhostNickCooldownMinutes,

      int userhostMaxNicksPerCommand
  ) {
    this(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        // Tray defaults (older call sites)
        true, true, false, false,
        true, true, false,
        true, false, true,
        true,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        true,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, true,
        chatHistoryInitialLoadLines, chatHistoryPageSize, 500,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        // New fields
        false, 15, 3, 60, 5,
        false, 45, 120,
        false, 300, 2,
        15,
        List.of());
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

  public UiSettings withTheme(String nextTheme) {
    return new UiSettings(nextTheme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withChatFontFamily(String family) {
    return new UiSettings(theme, family, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withChatFontSize(int size) {
    return new UiSettings(theme, chatFontFamily, size, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withImageEmbedsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        enabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withImageEmbedsCollapsedByDefault(boolean collapsed) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, collapsed, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withImageEmbedsMaxWidthPx(int maxWidthPx) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, Math.max(0, maxWidthPx), imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withImageEmbedsMaxHeightPx(int maxHeightPx) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, Math.max(0, maxHeightPx), imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withLinkPreviewsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        enabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withLinkPreviewsCollapsedByDefault(boolean collapsed) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, collapsed,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withPresenceFoldsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        enabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withTypingIndicatorsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        enabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withTimestampsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        enabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withTimestampFormat(String format) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, format, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withTimestampsIncludeChatMessages(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, enabled, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  public UiSettings withTimestampsIncludePresenceMessages(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, enabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }

  @Deprecated
  public UiSettings withChatMessageTimestampsEnabled(boolean enabled) {
    return withTimestampsIncludeChatMessages(enabled);
  }

  public UiSettings withAutoConnectOnStart(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, enabled,
        trayEnabled, trayCloseToTray, trayMinimizeToTray, trayStartMinimized,
        trayNotifyHighlights, trayNotifyPrivateMessages, trayNotifyConnectionState, trayNotifyOnlyWhenUnfocused, trayNotifyOnlyWhenMinimizedOrHidden, trayNotifySuppressWhenTargetActive, trayLinuxDbusActionsEnabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, ctcpRequestsInActiveTargetEnabled,
        typingIndicatorsEnabled,
        timestampsEnabled, timestampFormat, timestampsIncludeChatMessages, timestampsIncludePresenceMessages,
        chatHistoryInitialLoadLines, chatHistoryPageSize, commandHistoryMaxSize,
        clientLineColorEnabled, clientLineColor,

        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand,

        userInfoEnrichmentEnabled, userInfoEnrichmentUserhostMinIntervalSeconds, userInfoEnrichmentUserhostMaxCommandsPerMinute,
        userInfoEnrichmentUserhostNickCooldownMinutes, userInfoEnrichmentUserhostMaxNicksPerCommand,
        userInfoEnrichmentWhoisFallbackEnabled, userInfoEnrichmentWhoisMinIntervalSeconds, userInfoEnrichmentWhoisNickCooldownMinutes,
        userInfoEnrichmentPeriodicRefreshEnabled, userInfoEnrichmentPeriodicRefreshIntervalSeconds, userInfoEnrichmentPeriodicRefreshNicksPerTick,
        notificationRuleCooldownSeconds,
        notificationRules);
  }
}
