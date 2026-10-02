package cafe.woden.ircclient.ui.settings.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.api.ChatAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.config.api.DockLayoutRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ThemeAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettingsTestFixtures;
import cafe.woden.ircclient.ui.settings.theme.ThemeAccentSettings;
import cafe.woden.ircclient.ui.settings.theme.ThemeAppearanceSettingsTestFixtures;
import cafe.woden.ircclient.ui.settings.theme.ThemeTweakSettings;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Test;

class AppearanceControlsSupportTest {

  @Test
  void readTweakSettingsNormalizesFontFamilyAndValues() {
    TweakControls controls = tweakControls("spacious", 15, true, "  ", 99);

    ThemeTweakSettings settings = AppearanceControlsSupport.readTweakSettings(controls);

    assertEquals(ThemeTweakSettings.ThemeDensity.SPACIOUS, settings.density());
    assertEquals(15, settings.cornerRadius());
    assertTrue(settings.uiFontOverrideEnabled());
    assertEquals(ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY, settings.uiFontFamily());
    assertEquals(48, settings.uiFontSize());
  }

  @Test
  void readAccentSettingsNormalizesEnabledAccentColor() throws Exception {
    AccentControls controls = accentControls(true, "abc", 75);

    ThemeAccentSettings settings = AppearanceControlsSupport.readAccentSettings(controls);

    assertEquals("#AABBCC", settings.accentColor());
    assertEquals(75, settings.strength());
  }

  @Test
  void readAccentSettingsIgnoresInvalidColorWhenDisabled() throws Exception {
    AccentControls controls = accentControls(false, "not-a-color", 25);

    ThemeAccentSettings settings = AppearanceControlsSupport.readAccentSettings(controls);

    assertFalse(settings.enabled());
    assertEquals(25, settings.strength());
  }

  @Test
  void readAccentSettingsReportsInvalidEnabledColorWithDialogTitle() {
    AccentControls controls = accentControls(true, "not-a-color", 25);

    AppearanceControlsSupport.AppearanceSettingsException ex =
        assertThrows(
            AppearanceControlsSupport.AppearanceSettingsException.class,
            () -> AppearanceControlsSupport.readAccentSettings(controls));

    assertEquals("Invalid accent color", ex.title());
    assertEquals("Accent color must be a hex value like #RRGGBB.", ex.getMessage());
  }

  @Test
  void readChatThemeSettingsNormalizesOptionalColors() throws Exception {
    ChatThemeControls controls =
        chatThemeControls(
            ChatThemeSettings.Preset.SOFT,
            "abc",
            "",
            "0x102030",
            42,
            "#445566",
            "778899",
            "aabbcc",
            "ddeeff",
            " ");

    ChatThemeSettings settings = AppearanceControlsSupport.readChatThemeSettings(controls);

    assertEquals(ChatThemeSettings.Preset.SOFT, settings.preset());
    assertEquals("#AABBCC", settings.timestampColor());
    assertNull(settings.systemColor());
    assertEquals("#102030", settings.mentionBgColor());
    assertEquals(42, settings.mentionStrength());
    assertEquals("#445566", settings.messageColor());
    assertEquals("#778899", settings.noticeColor());
    assertEquals("#AABBCC", settings.actionColor());
    assertEquals("#DDEEFF", settings.errorColor());
    assertNull(settings.presenceColor());
  }

  @Test
  void readChatThemeSettingsReportsInvalidColorWithDialogTitle() {
    ChatThemeControls controls =
        chatThemeControls(
            ChatThemeSettings.Preset.DEFAULT, "not-a-color", "", "", 35, "", "", "", "", "");

    AppearanceControlsSupport.AppearanceSettingsException ex =
        assertThrows(
            AppearanceControlsSupport.AppearanceSettingsException.class,
            () -> AppearanceControlsSupport.readChatThemeSettings(controls));

    assertEquals("Invalid chat message color", ex.title());
    assertTrue(ex.getMessage().contains("Timestamp color"));
  }

  @Test
  void readServerTreeSettingsNormalizesOptionalColors() throws Exception {
    AppearanceServerTreeControls controls = serverTreeControls(" abc ", "0x102030", true);

    AppearanceControlsSupport.ServerTreeAppearanceSettings settings =
        AppearanceControlsSupport.readServerTreeSettings(controls);

    assertEquals("#AABBCC", settings.unreadChannelColor());
    assertEquals("#102030", settings.highlightChannelColor());
    assertTrue(settings.preserveDockLayoutBetweenSessions());
  }

  @Test
  void readServerTreeSettingsKeepsBlankColorsAsNull() throws Exception {
    AppearanceServerTreeControls controls = serverTreeControls(" ", "", false);

    AppearanceControlsSupport.ServerTreeAppearanceSettings settings =
        AppearanceControlsSupport.readServerTreeSettings(controls);

    assertNull(settings.unreadChannelColor());
    assertNull(settings.highlightChannelColor());
  }

  @Test
  void readServerTreeSettingsReportsInvalidColorWithDialogTitle() {
    AppearanceServerTreeControls controls = serverTreeControls("not-a-color", "", false);

    AppearanceControlsSupport.AppearanceSettingsException ex =
        assertThrows(
            AppearanceControlsSupport.AppearanceSettingsException.class,
            () -> AppearanceControlsSupport.readServerTreeSettings(controls));

    assertEquals("Invalid server tree color", ex.title());
    assertTrue(ex.getMessage().contains("New message color"));
  }

  @Test
  void rememberSettingsPersistsAppearanceValues() {
    ThemeAppearanceRuntimeConfigPort themeRuntimeConfig =
        mock(ThemeAppearanceRuntimeConfigPort.class);
    ChatAppearanceRuntimeConfigPort chatRuntimeConfig = mock(ChatAppearanceRuntimeConfigPort.class);
    ServerTreeAppearanceRuntimeConfigPort serverTreeRuntimeConfig =
        mock(ServerTreeAppearanceRuntimeConfigPort.class);
    DockLayoutRuntimeConfigPort dockLayoutRuntimeConfig = mock(DockLayoutRuntimeConfigPort.class);
    var accentSettings = ThemeAppearanceSettingsTestFixtures.accent("#AABBCC", 80);
    ThemeTweakSettings tweakSettings =
        ThemeAppearanceSettingsTestFixtures.tweakBuilder()
            .density(ThemeTweakSettings.ThemeDensity.COMPACT)
            .cornerRadius(4)
            .uiFontOverrideEnabled(true)
            .uiFontFamily("Dialog")
            .uiFontSize(14)
            .build();
    ChatThemeSettings chatThemeSettings =
        ChatThemeSettingsTestFixtures.builder()
            .preset(ChatThemeSettings.Preset.ACCENTED)
            .timestampColor("#111111")
            .systemColor("#222222")
            .mentionBgColor("#333333")
            .mentionStrength(55)
            .messageColor("#444444")
            .noticeColor("#555555")
            .actionColor("#666666")
            .errorColor("#777777")
            .presenceColor("#888888")
            .build();
    AppearanceControlsSupport.ServerTreeAppearanceSettings settings =
        new AppearanceControlsSupport.ServerTreeAppearanceSettings("#112233", "#445566", true);

    AppearanceControlsSupport.rememberAccentSettings(themeRuntimeConfig, accentSettings);
    AppearanceControlsSupport.rememberTweakSettings(themeRuntimeConfig, tweakSettings);
    AppearanceControlsSupport.rememberChatThemeSettings(chatRuntimeConfig, chatThemeSettings);
    AppearanceControlsSupport.rememberServerTreeSettings(
        serverTreeRuntimeConfig, dockLayoutRuntimeConfig, settings);

    verify(themeRuntimeConfig).rememberAccentColor("#AABBCC");
    verify(themeRuntimeConfig).rememberAccentStrength(80);
    verify(themeRuntimeConfig).rememberUiDensity("compact");
    verify(themeRuntimeConfig).rememberCornerRadius(4);
    verify(themeRuntimeConfig).rememberUiFontOverrideEnabled(true);
    verify(themeRuntimeConfig).rememberUiFontFamily("Dialog");
    verify(themeRuntimeConfig).rememberUiFontSize(14);
    verify(chatRuntimeConfig).rememberChatThemePreset("ACCENTED");
    verify(chatRuntimeConfig).rememberChatTimestampColor("#111111");
    verify(chatRuntimeConfig).rememberChatSystemColor("#222222");
    verify(chatRuntimeConfig).rememberChatMessageColor("#444444");
    verify(chatRuntimeConfig).rememberChatNoticeColor("#555555");
    verify(chatRuntimeConfig).rememberChatActionColor("#666666");
    verify(chatRuntimeConfig).rememberChatErrorColor("#777777");
    verify(chatRuntimeConfig).rememberChatPresenceColor("#888888");
    verify(chatRuntimeConfig).rememberChatMentionBgColor("#333333");
    verify(chatRuntimeConfig).rememberChatMentionStrength(55);
    verify(serverTreeRuntimeConfig).rememberServerTreeUnreadChannelColor("#112233");
    verify(serverTreeRuntimeConfig).rememberServerTreeHighlightChannelColor("#445566");
    verify(dockLayoutRuntimeConfig).rememberPreserveDockLayout(true);
  }

  private static TweakControls tweakControls(
      String densityId,
      int cornerRadius,
      boolean uiFontOverrideEnabled,
      String uiFontFamily,
      int uiFontSize) {
    JComboBox<DensityOption> density =
        new JComboBox<>(new DensityOption[] {new DensityOption(densityId, densityId)});
    density.setSelectedIndex(0);
    JCheckBox uiFontOverride = new JCheckBox();
    uiFontOverride.setSelected(uiFontOverrideEnabled);
    JComboBox<String> fontFamily = new JComboBox<>(new String[] {uiFontFamily});
    fontFamily.setSelectedItem(uiFontFamily);
    return new TweakControls(
        density,
        new JSlider(0, 20, cornerRadius),
        uiFontOverride,
        fontFamily,
        new JSpinner(new SpinnerNumberModel(uiFontSize, -100, 100, 1)),
        () -> {});
  }

  private static AccentControls accentControls(boolean enabled, String hex, int strength) {
    JCheckBox enabledBox = new JCheckBox();
    enabledBox.setSelected(enabled);
    return new AccentControls(
        enabledBox,
        new JComboBox<>(AccentPreset.values()),
        new JTextField(hex),
        new JButton(),
        new JButton(),
        new JSlider(0, 100, strength),
        new JPanel(),
        new JPanel(),
        () -> {},
        () -> {},
        () -> {});
  }

  private static ChatThemeControls chatThemeControls(
      ChatThemeSettings.Preset preset,
      String timestampColor,
      String systemColor,
      String mentionColor,
      int mentionStrength,
      String messageColor,
      String noticeColor,
      String actionColor,
      String errorColor,
      String presenceColor) {
    JComboBox<ChatThemeSettings.Preset> presetCombo =
        new JComboBox<>(ChatThemeSettings.Preset.values());
    presetCombo.setSelectedItem(preset);
    return new ChatThemeControls(
        presetCombo,
        colorField(timestampColor),
        colorField(systemColor),
        colorField(mentionColor),
        colorField(messageColor),
        colorField(noticeColor),
        colorField(actionColor),
        colorField(errorColor),
        colorField(presenceColor),
        new JSlider(0, 100, mentionStrength));
  }

  private static AppearanceServerTreeControls serverTreeControls(
      String unreadColor, String highlightColor, boolean preserveLayout) {
    JCheckBox preserve = new JCheckBox();
    preserve.setSelected(preserveLayout);
    return new AppearanceServerTreeControls(
        colorField(unreadColor), colorField(highlightColor), preserve);
  }

  private static ColorField colorField(String hex) {
    return new ColorField(
        new JTextField(hex), new JButton(), new JButton(), new JPanel(), () -> {});
  }
}
