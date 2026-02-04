package cafe.woden.ircclient.ui.settings;

public record UiSettings(
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

    
    boolean chatMessageTimestampsEnabled,

    
    int chatHistoryInitialLoadLines,

    
    int chatHistoryPageSize,

    
    boolean clientLineColorEnabled,

    
    String clientLineColor,

    // --- Hostmask discovery / USERHOST anti-flood ---

    boolean userhostDiscoveryEnabled,

    int userhostMinIntervalSeconds,

    int userhostMaxCommandsPerMinute,

    int userhostNickCooldownMinutes,

    int userhostMaxNicksPerCommand
) {

  public UiSettings {
    if (theme == null || theme.isBlank()) theme = "dark";
    if (chatFontFamily == null || chatFontFamily.isBlank()) chatFontFamily = "Monospaced";
    if (chatFontSize <= 0) chatFontSize = 12;
    if (imageEmbedsMaxWidthPx < 0) imageEmbedsMaxWidthPx = 0;
    if (imageEmbedsMaxHeightPx < 0) imageEmbedsMaxHeightPx = 0;

    if (chatHistoryInitialLoadLines < 0) chatHistoryInitialLoadLines = 0;
    if (chatHistoryPageSize <= 0) chatHistoryPageSize = 200;

    clientLineColor = normalizeHexOrDefault(clientLineColor, "#6AA2FF");

    if (userhostMinIntervalSeconds <= 0) userhostMinIntervalSeconds = 7;
    if (userhostMaxCommandsPerMinute <= 0) userhostMaxCommandsPerMinute = 6;
    if (userhostNickCooldownMinutes <= 0) userhostNickCooldownMinutes = 30;
    if (userhostMaxNicksPerCommand <= 0) userhostMaxNicksPerCommand = 5;
    if (userhostMaxNicksPerCommand > 5) userhostMaxNicksPerCommand = 5;
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
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withChatFontFamily(String family) {
    return new UiSettings(theme, family, chatFontSize, autoConnectOnStart,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withChatFontSize(int size) {
    return new UiSettings(theme, chatFontFamily, size, autoConnectOnStart,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withImageEmbedsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        enabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withImageEmbedsCollapsedByDefault(boolean collapsed) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        imageEmbedsEnabled, collapsed, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withImageEmbedsMaxWidthPx(int maxWidthPx) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, Math.max(0, maxWidthPx), imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withImageEmbedsMaxHeightPx(int maxHeightPx) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, Math.max(0, maxHeightPx), imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withLinkPreviewsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        enabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withLinkPreviewsCollapsedByDefault(boolean collapsed) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, collapsed,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withPresenceFoldsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        enabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withChatMessageTimestampsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, autoConnectOnStart,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, enabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }

  public UiSettings withAutoConnectOnStart(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, enabled,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled,
        chatHistoryInitialLoadLines, chatHistoryPageSize,
        clientLineColorEnabled, clientLineColor,
        userhostDiscoveryEnabled, userhostMinIntervalSeconds, userhostMaxCommandsPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand);
  }
}
