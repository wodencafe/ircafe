package cafe.woden.ircclient.ui.settings;

/**
 * Runtime UI settings.
 */
public record UiSettings(
    String theme,
    String chatFontFamily,
    int chatFontSize,
    boolean imageEmbedsEnabled,
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
    return new UiSettings(nextTheme, chatFontFamily, chatFontSize, imageEmbedsEnabled, presenceFoldsEnabled,
        chatMessageTimestampsEnabled);
  }

  public UiSettings withChatFontFamily(String family) {
    return new UiSettings(theme, family, chatFontSize, imageEmbedsEnabled, presenceFoldsEnabled,
        chatMessageTimestampsEnabled);
  }

  public UiSettings withChatFontSize(int size) {
    return new UiSettings(theme, chatFontFamily, size, imageEmbedsEnabled, presenceFoldsEnabled,
        chatMessageTimestampsEnabled);
  }


  public UiSettings withImageEmbedsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, enabled, presenceFoldsEnabled,
        chatMessageTimestampsEnabled);
  }

  public UiSettings withPresenceFoldsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, imageEmbedsEnabled, enabled,
        chatMessageTimestampsEnabled);
  }

  public UiSettings withChatMessageTimestampsEnabled(boolean enabled) {
    return new UiSettings(theme, chatFontFamily, chatFontSize, imageEmbedsEnabled, presenceFoldsEnabled, enabled);
  }
}
