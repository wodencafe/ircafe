package cafe.woden.ircclient.ui.settings;

/**
 * Runtime UI settings.
 */
public record UiSettings(
    String theme,
    String chatFontFamily,
    int chatFontSize,

    boolean imageEmbedsEnabled,
    boolean imageEmbedsCollapsedByDefault,

    boolean linkPreviewsEnabled,
    boolean linkPreviewsCollapsedByDefault,

    boolean presenceFoldsEnabled,

    /** If enabled, prepend timestamps to regular user chat messages (not just status/notice lines). */
    boolean chatMessageTimestampsEnabled
) {

  public UiSettings {
    if (theme == null || theme.isBlank()) theme = "dark";
    if (chatFontFamily == null || chatFontFamily.isBlank()) chatFontFamily = "Monospaced";
    if (chatFontSize <= 0) chatFontSize = 12;
  }

  public UiSettings withTheme(String nextTheme) {
    return new UiSettings(nextTheme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withChatFontFamily(String family) {
    return new UiSettings(theme, family, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withChatFontSize(int size) {
    return new UiSettings(theme, chatFontFamily, size,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withImageEmbedsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        enabled, imageEmbedsCollapsedByDefault,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withImageEmbedsCollapsedByDefault(boolean collapsed) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, collapsed,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withLinkPreviewsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault,
        enabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withLinkPreviewsCollapsedByDefault(boolean collapsed) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault,
        linkPreviewsEnabled, collapsed,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withPresenceFoldsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        enabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withChatMessageTimestampsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, enabled);
  }
}
