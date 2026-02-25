package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.app.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.pushy.PushySettingsBus;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettingsBus;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class PreferencesDialogFunctionalTest {

  @Test
  void appearancePanelIncludesMessageColorsSubTabAndExpectedRows() throws Exception {
    AppearanceFixture fixture =
        buildAppearanceFixture(
            new ChatThemeSettings(
                ChatThemeSettings.Preset.DEFAULT,
                null,
                null,
                null,
                35,
                null,
                null,
                null,
                null,
                null));

    JTabbedPane chatTabs = findTabbedPaneWithTab(fixture.appearancePanel, "Message colors");
    assertNotNull(chatTabs, "chat transcript sub-tabs should include Message colors");

    Component messageTab = chatTabs.getComponentAt(chatTabs.indexOfTab("Message colors"));
    assertNotNull(findLabel(messageTab, "Server/system"));
    assertNotNull(findLabel(messageTab, "User messages"));
    assertNotNull(findLabel(messageTab, "Notice messages"));
    assertNotNull(findLabel(messageTab, "Action messages"));
    assertNotNull(findLabel(messageTab, "Presence messages"));
    assertNotNull(findLabel(messageTab, "Error messages"));
  }

  @Test
  void resetToDefaultsClearsMessageColorOverrides() throws Exception {
    AppearanceFixture fixture =
        buildAppearanceFixture(
            new ChatThemeSettings(
                ChatThemeSettings.Preset.ACCENTED,
                "#111111",
                "#222222",
                "#333333",
                60,
                "#444444",
                "#555555",
                "#666666",
                "#777777",
                "#888888"));

    JButton reset = findButton(fixture.appearancePanel, "Reset to defaults");
    assertNotNull(reset, "appearance panel should expose reset action");
    reset.doClick();

    assertEquals("", chatThemeHex(fixture.chatThemeControls, "timestamp").getText());
    assertEquals("", chatThemeHex(fixture.chatThemeControls, "system").getText());
    assertEquals("", chatThemeHex(fixture.chatThemeControls, "mention").getText());
    assertEquals("", chatThemeHex(fixture.chatThemeControls, "message").getText());
    assertEquals("", chatThemeHex(fixture.chatThemeControls, "notice").getText());
    assertEquals("", chatThemeHex(fixture.chatThemeControls, "action").getText());
    assertEquals("", chatThemeHex(fixture.chatThemeControls, "error").getText());
    assertEquals("", chatThemeHex(fixture.chatThemeControls, "presence").getText());

    JSlider mentionStrength = (JSlider) readField(fixture.chatThemeControls, "mentionStrength");
    assertEquals(35, mentionStrength.getValue());
    @SuppressWarnings("unchecked")
    JComboBox<Object> preset = (JComboBox<Object>) readField(fixture.chatThemeControls, "preset");
    assertEquals(ChatThemeSettings.Preset.DEFAULT, preset.getSelectedItem());
  }

  @Test
  void invalidHexIsRejectedByApplyNormalizer() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PreferencesDialog.normalizeOptionalHexForApply(
                    "#12GG34", "User message color"));
    assertEquals(
        "User message color must be a hex value like #RRGGBB (or blank for default).",
        ex.getMessage());
    assertNull(PreferencesDialog.normalizeOptionalHexForApply("   ", "User message color"));
    assertEquals(
        "#AABBCC",
        PreferencesDialog.normalizeOptionalHexForApply("#abc", "User message color"));
  }

  private static AppearanceFixture buildAppearanceFixture(ChatThemeSettings chatTheme) throws Exception {
    PreferencesDialog dialog = newPreferencesDialog();
    UiSettings current = testUiSettings();
    List<AutoCloseable> closeables = new ArrayList<>();
    Map<String, String> themeLabelById = new LinkedHashMap<>();
    themeLabelById.put("darcula", "Darcula");

    Object themeControls = invoke(dialog, "buildThemeControls", current, themeLabelById);
    Object accentControls =
        invoke(
            dialog,
            "buildAccentControls",
            new ThemeAccentSettings(
                cafe.woden.ircclient.config.UiProperties.DEFAULT_ACCENT_COLOR,
                cafe.woden.ircclient.config.UiProperties.DEFAULT_ACCENT_STRENGTH));
    Object chatThemeControls = invoke(dialog, "buildChatThemeControls", chatTheme);
    Object fontControls = invoke(dialog, "buildFontControls", current, closeables);
    Object tweakControls =
        invoke(
            dialog,
            "buildTweakControls",
            new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10),
            closeables);

    JPanel appearancePanel =
        (JPanel)
            invoke(
                dialog,
                "buildAppearancePanel",
                themeControls,
                accentControls,
                chatThemeControls,
                fontControls,
                tweakControls);
    return new AppearanceFixture(appearancePanel, chatThemeControls);
  }

  private static PreferencesDialog newPreferencesDialog() {
    return new PreferencesDialog(
        mock(UiSettingsBus.class),
        mock(ThemeManager.class),
        mock(ThemeAccentSettingsBus.class),
        mock(ThemeTweakSettingsBus.class),
        mock(ChatThemeSettingsBus.class),
        mock(RuntimeConfigStore.class),
        mock(LogProperties.class),
        mock(NickColorSettingsBus.class),
        mock(NickColorService.class),
        mock(NickColorOverridesDialog.class),
        mock(PircbotxIrcClientService.class),
        mock(FilterSettingsBus.class),
        mock(TranscriptRebuildService.class),
        mock(ActiveTargetPort.class),
        mock(TrayService.class),
        mock(TrayNotificationService.class),
        mock(GnomeDbusNotificationBackend.class),
        mock(NotificationSoundSettingsBus.class),
        mock(PushySettingsBus.class),
        mock(PushyNotificationService.class),
        mock(IrcEventNotificationRulesBus.class),
        mock(UserCommandAliasesBus.class),
        mock(NotificationSoundService.class),
        mock(ServerDialogs.class));
  }

  private static UiSettings testUiSettings() {
    return new UiSettings(
        "darcula",
        "Monospaced",
        12,
        true,
        false,
        false,
        0,
        0,
        true,
        false,
        false,
        true,
        true,
        true,
        "HH:mm:ss",
        true,
        100,
        200,
        false,
        "#6AA2FF",
        true,
        7,
        6,
        30,
        5);
  }

  private static JTextField chatThemeHex(Object chatThemeControls, String fieldName) throws Exception {
    Object colorField = readField(chatThemeControls, fieldName);
    return (JTextField) readField(colorField, "hex");
  }

  private static Object invoke(PreferencesDialog dialog, String methodName, Object... args)
      throws Exception {
    Method m = findMethod(methodName, args);
    if (m == null) {
      throw new NoSuchMethodException(methodName);
    }
    m.setAccessible(true);
    try {
      return m.invoke(dialog, args);
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof Exception e) throw e;
      if (cause instanceof Error e) throw e;
      throw ex;
    }
  }

  private static Method findMethod(String methodName, Object[] args) {
    Method[] methods = PreferencesDialog.class.getDeclaredMethods();
    for (Method m : methods) {
      if (!m.getName().equals(methodName)) continue;
      Class<?>[] params = m.getParameterTypes();
      if (params.length != args.length) continue;
      boolean match = true;
      for (int i = 0; i < params.length; i++) {
        Object arg = args[i];
        if (arg == null) continue;
        if (!params[i].isAssignableFrom(arg.getClass())) {
          match = false;
          break;
        }
      }
      if (match) return m;
    }
    return null;
  }

  private static Object readField(Object target, String field) throws Exception {
    Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return f.get(target);
  }

  private static JTabbedPane findTabbedPaneWithTab(Component root, String tabTitle) {
    if (root == null || tabTitle == null) return null;
    if (root instanceof JTabbedPane tabs && tabs.indexOfTab(tabTitle) >= 0) {
      return tabs;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JTabbedPane found = findTabbedPaneWithTab(child, tabTitle);
      if (found != null) return found;
    }
    return null;
  }

  private static JLabel findLabel(Component root, String text) {
    if (root == null || text == null) return null;
    if (root instanceof JLabel label && text.equals(label.getText())) return label;
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JLabel found = findLabel(child, text);
      if (found != null) return found;
    }
    return null;
  }

  private static JButton findButton(Component root, String text) {
    if (root == null || text == null) return null;
    if (root instanceof JButton b && text.equals(b.getText())) return b;
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JButton found = findButton(child, text);
      if (found != null) return found;
    }
    return null;
  }

  private record AppearanceFixture(JPanel appearancePanel, Object chatThemeControls) {}
}
