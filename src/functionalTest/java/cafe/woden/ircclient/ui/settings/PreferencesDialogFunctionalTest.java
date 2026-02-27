package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.UserCommandAlias;
import cafe.woden.ircclient.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.pushy.PushySettingsBus;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettings;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettingsBus;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import cafe.woden.ircclient.ui.filter.FilterSettings;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
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
            () -> PreferencesDialog.normalizeOptionalHexForApply("#12GG34", "User message color"));
    assertEquals(
        "User message color must be a hex value like #RRGGBB (or blank for default).",
        ex.getMessage());
    assertNull(PreferencesDialog.normalizeOptionalHexForApply("   ", "User message color"));
    assertEquals(
        "#AABBCC", PreferencesDialog.normalizeOptionalHexForApply("#abc", "User message color"));
  }

  @Test
  void filtersPanelExposesSubTabsAndHistoryRunCapToggle() throws Exception {
    PreferencesDialog dialog = newPreferencesDialog();
    List<AutoCloseable> closeables = new ArrayList<>();
    FilterSettings filters =
        new FilterSettings(true, true, true, 3, 250, 12, 10, false, List.of(), List.of());

    Object controls = invoke(dialog, "buildFilterControls", filters, closeables);
    JPanel panel = (JPanel) invoke(dialog, "buildFiltersPanel", controls);

    assertNotNull(findTabbedPaneWithTab(panel, "General"));
    assertNotNull(findTabbedPaneWithTab(panel, "Placeholders"));
    assertNotNull(findTabbedPaneWithTab(panel, "History"));
    assertNotNull(findTabbedPaneWithTab(panel, "Overrides"));
    assertNotNull(findTabbedPaneWithTab(panel, "Rules"));

    JCheckBox historyEnabled =
        (JCheckBox) readField(controls, "historyPlaceholdersEnabledByDefault");
    JSpinner historyMaxRuns = (JSpinner) readField(controls, "historyPlaceholderMaxRunsPerBatch");
    assertFalse(historyMaxRuns.isEnabled());
    historyEnabled.doClick();
    assertTrue(historyMaxRuns.isEnabled());

    closeAll(closeables);
  }

  @Test
  void notificationsPanelIncludesRulesTestAndIrcEventsTabs() throws Exception {
    PreferencesDialog dialog = newPreferencesDialog();
    List<AutoCloseable> closeables = new ArrayList<>();

    Object notifications =
        invoke(dialog, "buildNotificationRulesControls", testUiSettings(), closeables);
    Object ircEvents =
        invoke(dialog, "buildIrcEventNotificationControls", IrcEventNotificationRule.defaults());

    JPanel panel = (JPanel) invoke(dialog, "buildNotificationsPanel", notifications, ircEvents);
    assertNotNull(findTabbedPaneWithTab(panel, "Rules"));
    assertNotNull(findTabbedPaneWithTab(panel, "Test"));
    assertNotNull(findTabbedPaneWithTab(panel, "IRC Events"));

    closeAll(closeables);
  }

  @Test
  void commandsPanelIncludesAliasImportAndUnknownFallbackToggle() throws Exception {
    PreferencesDialog dialog = newPreferencesDialog();
    Object controls =
        invoke(
            dialog,
            "buildUserCommandAliasesControls",
            List.of(new UserCommandAlias(true, "greet", "/msg %1 hello")),
            true);
    JPanel panel = (JPanel) invoke(dialog, "buildUserCommandsPanel", controls);

    JButton importHexChat = (JButton) readField(controls, "importHexChat");
    assertNotNull(importHexChat);
    assertTrue(
        String.valueOf(importHexChat.getToolTipText()).contains("Import aliases from HexChat"));
    JCheckBox unknownFallback = (JCheckBox) readField(controls, "unknownCommandAsRaw");
    assertTrue(unknownFallback.isSelected());
  }

  @Test
  void trayPanelSoundsTabExposesCustomSoundPathControls() throws Exception {
    PreferencesDialog dialog = newPreferencesDialog();
    List<AutoCloseable> closeables = new ArrayList<>();

    Object trayControls =
        invoke(
            dialog,
            "buildTrayControls",
            testUiSettings(),
            new NotificationSoundSettings(true, "NOTIF_1", true, "sounds/custom.wav"),
            new PushyProperties(false, null, null, null, null, null, null, null),
            closeables);
    JPanel trayPanel = (JPanel) invoke(dialog, "buildTrayNotificationsPanel", trayControls);
    assertNotNull(findTabbedPaneWithTab(trayPanel, "Sounds"));

    JCheckBox soundsEnabled = (JCheckBox) readField(trayControls, "notificationSoundsEnabled");
    JCheckBox useCustom = (JCheckBox) readField(trayControls, "notificationSoundUseCustom");
    JTextField customPath = (JTextField) readField(trayControls, "notificationSoundCustomPath");
    JButton browse = (JButton) readField(trayControls, "browseCustomSound");
    JButton clear = (JButton) readField(trayControls, "clearCustomSound");

    assertEquals("sounds/custom.wav", customPath.getText());
    assertTrue(browse.isEnabled());
    soundsEnabled.doClick();
    assertFalse(browse.isEnabled());
    soundsEnabled.doClick();
    if (!useCustom.isSelected()) {
      useCustom.doClick();
    }
    assertTrue(browse.isEnabled());
    clear.doClick();
    assertEquals("", customPath.getText());

    closeAll(closeables);
  }

  @Test
  void constructorRejectsShutdownExecutors() {
    ExecutorService shutdownPushy = mock(ExecutorService.class);
    when(shutdownPushy.isShutdown()).thenReturn(true);
    ExecutorService activeRules = mock(ExecutorService.class);
    when(activeRules.isShutdown()).thenReturn(false);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newPreferencesDialog(shutdownPushy, activeRules));
    assertEquals("pushyTestExecutor must be active", ex.getMessage());
  }

  @Test
  void ircv3PanelIncludesUnreadBadgeSizeControl() throws Exception {
    PreferencesDialog dialog = newPreferencesDialog();
    UiSettings current = testUiSettings();

    JCheckBox send = (JCheckBox) invoke(dialog, "buildTypingIndicatorsSendCheckbox", current);
    JCheckBox receive = (JCheckBox) invoke(dialog, "buildTypingIndicatorsReceiveCheckbox", current);
    @SuppressWarnings("unchecked")
    JComboBox<Object> style =
        (JComboBox<Object>) invoke(dialog, "buildTypingTreeIndicatorStyleCombo", current);
    JSpinner badgeScale = new JSpinner(new javax.swing.SpinnerNumberModel(100, 50, 150, 5));
    Object capabilities = invoke(dialog, "buildIrcv3CapabilitiesControls");

    JPanel panel =
        (JPanel)
            invoke(
                dialog,
                "buildIrcv3CapabilitiesPanel",
                send,
                receive,
                style,
                badgeScale,
                capabilities);
    assertNotNull(findLabel(panel, "Unread badge size"));
  }

  @Test
  void notificationRuleCloseablesDoNotShutdownSharedExecutors() throws Exception {
    ExecutorService pushyExec = mock(ExecutorService.class);
    ExecutorService rulesExec = mock(ExecutorService.class);
    when(pushyExec.isShutdown()).thenReturn(false);
    when(rulesExec.isShutdown()).thenReturn(false);
    PreferencesDialog dialog = newPreferencesDialog(pushyExec, rulesExec);
    List<AutoCloseable> closeables = new ArrayList<>();

    invoke(dialog, "buildNotificationRulesControls", testUiSettings(), closeables);
    closeAll(closeables);

    verify(rulesExec, never()).shutdownNow();
    verify(pushyExec, never()).shutdownNow();
  }

  private static AppearanceFixture buildAppearanceFixture(ChatThemeSettings chatTheme)
      throws Exception {
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
    ExecutorService pushyTestExecutor = mock(ExecutorService.class);
    ExecutorService notificationRuleTestExecutor = mock(ExecutorService.class);
    when(pushyTestExecutor.isShutdown()).thenReturn(false);
    when(notificationRuleTestExecutor.isShutdown()).thenReturn(false);
    return newPreferencesDialog(pushyTestExecutor, notificationRuleTestExecutor);
  }

  private static PreferencesDialog newPreferencesDialog(
      ExecutorService pushyTestExecutor, ExecutorService notificationRuleTestExecutor) {
    return new PreferencesDialog(
        mock(UiSettingsBus.class),
        mock(ThemeManager.class),
        mock(ThemeAccentSettingsBus.class),
        mock(ThemeTweakSettingsBus.class),
        mock(ChatThemeSettingsBus.class),
        mock(SpellcheckSettingsBus.class),
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
        mock(ServerDialogs.class),
        pushyTestExecutor,
        notificationRuleTestExecutor);
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

  private static JTextField chatThemeHex(Object chatThemeControls, String fieldName)
      throws Exception {
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
        if (!isParameterCompatible(params[i], arg)) {
          match = false;
          break;
        }
      }
      if (match) return m;
    }
    return null;
  }

  private static boolean isParameterCompatible(Class<?> parameterType, Object arg) {
    if (arg == null) return true;
    Class<?> argType = arg.getClass();
    if (!parameterType.isPrimitive()) {
      return parameterType.isAssignableFrom(argType);
    }
    return switch (parameterType.getName()) {
      case "boolean" -> Boolean.class.isAssignableFrom(argType);
      case "byte" -> Byte.class.isAssignableFrom(argType);
      case "char" -> Character.class.isAssignableFrom(argType);
      case "short" -> Short.class.isAssignableFrom(argType);
      case "int" -> Integer.class.isAssignableFrom(argType);
      case "long" -> Long.class.isAssignableFrom(argType);
      case "float" -> Float.class.isAssignableFrom(argType);
      case "double" -> Double.class.isAssignableFrom(argType);
      default -> false;
    };
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

  private static void closeAll(List<AutoCloseable> closeables) {
    if (closeables == null) return;
    for (AutoCloseable closeable : closeables) {
      if (closeable == null) continue;
      try {
        closeable.close();
      } catch (Exception ignored) {
      }
    }
  }

  private record AppearanceFixture(JPanel appearancePanel, Object chatThemeControls) {}
}
