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

    /** Maximum inline image embed width in pixels. <=0 disables extra cap. */
    int imageEmbedsMaxWidthPx,

    /** Maximum inline image embed height in pixels. <=0 disables extra cap. */
    int imageEmbedsMaxHeightPx,

    /** If false, animated GIFs render as a still first frame. */
    boolean imageEmbedsAnimateGifs,

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
    if (imageEmbedsMaxWidthPx < 0) imageEmbedsMaxWidthPx = 0;
    if (imageEmbedsMaxHeightPx < 0) imageEmbedsMaxHeightPx = 0;
  }

  public UiSettings withTheme(String nextTheme) {
    return new UiSettings(nextTheme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withChatFontFamily(String family) {
    return new UiSettings(theme, family, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withChatFontSize(int size) {
    return new UiSettings(theme, chatFontFamily, size,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withImageEmbedsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        enabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withImageEmbedsCollapsedByDefault(boolean collapsed) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, collapsed, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withImageEmbedsMaxWidthPx(int maxWidthPx) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, Math.max(0, maxWidthPx), imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withImageEmbedsMaxHeightPx(int maxHeightPx) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, Math.max(0, maxHeightPx), imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withLinkPreviewsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        enabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withLinkPreviewsCollapsedByDefault(boolean collapsed) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, collapsed,
        presenceFoldsEnabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withPresenceFoldsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        enabled, chatMessageTimestampsEnabled);
  }

  public UiSettings withChatMessageTimestampsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize,
        imageEmbedsEnabled, imageEmbedsCollapsedByDefault, imageEmbedsMaxWidthPx, imageEmbedsMaxHeightPx, imageEmbedsAnimateGifs,
        linkPreviewsEnabled, linkPreviewsCollapsedByDefault,
        presenceFoldsEnabled, enabled);
  }
}
