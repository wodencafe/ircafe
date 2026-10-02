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
import cafe.woden.ircclient.app.translation.MessageTranslationSettingsBus;
import cafe.woden.ircclient.config.PushyPropertiesTestFixtures;
import cafe.woden.ircclient.config.api.ChatAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ChatBehaviorRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ChatHistoryRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ChatLoggingRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ClientTranslationRuntimeConfigPort;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.config.api.DiagnosticsRuntimeConfigPort;
import cafe.woden.ircclient.config.api.DockLayoutRuntimeConfigPort;
import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort;
import cafe.woden.ircclient.config.api.EmbedPreviewRuntimeConfigPort;
import cafe.woden.ircclient.config.api.FilterSettingsConfigPort;
import cafe.woden.ircclient.config.api.Ircv3CapabilityConfigPort;
import cafe.woden.ircclient.config.api.LagIndicatorRuntimeConfigPort;
import cafe.woden.ircclient.config.api.LaunchJvmRuntimeConfigPort;
import cafe.woden.ircclient.config.api.MemoryUsageRuntimeConfigPort;
import cafe.woden.ircclient.config.api.NetworkSettingsRuntimeConfigPort;
import cafe.woden.ircclient.config.api.NickColorRuntimeConfigPort;
import cafe.woden.ircclient.config.api.NotificationRuntimeConfigPort;
import cafe.woden.ircclient.config.api.OutgoingMessageRuntimeConfigPort;
import cafe.woden.ircclient.config.api.PreferencesRuntimeConfigPort;
import cafe.woden.ircclient.config.api.PushyRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.config.api.SpellcheckRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ThemeAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.config.api.TimestampRuntimeConfigPort;
import cafe.woden.ircclient.config.api.TrayRuntimeConfigPort;
import cafe.woden.ircclient.config.api.UpdateNotifierRuntimeConfigPort;
import cafe.woden.ircclient.config.api.UserCommandAliasesConfigPort;
import cafe.woden.ircclient.config.api.UserLookupRuntimeConfigPort;
import cafe.woden.ircclient.config.properties.LogProperties;
import cafe.woden.ircclient.irc.backend.IrcHeartbeatMaintenanceService;
import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionCatalog;
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
import cafe.woden.ircclient.ui.chat.embed.EmbedLoadPolicyBus;
import cafe.woden.ircclient.ui.chat.transcript.rebuild.TranscriptRebuildService;
import cafe.woden.ircclient.ui.filter.FilterSettings;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import cafe.woden.ircclient.ui.filter.FilterSettingsTestFixtures;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.settings.appearance.AppearancePreferencesSection;
import cafe.woden.ircclient.ui.settings.chat.ChatBehaviorControlsSupport;
import cafe.woden.ircclient.ui.settings.commands.UserCommandAliasesControls;
import cafe.woden.ircclient.ui.settings.commands.UserCommandAliasesControlsSupport;
import cafe.woden.ircclient.ui.settings.commands.UserCommandsPanelSupport;
import cafe.woden.ircclient.ui.settings.filters.FilterControls;
import cafe.woden.ircclient.ui.settings.filters.FilterControlsSupport;
import cafe.woden.ircclient.ui.settings.filters.FiltersPanelSupport;
import cafe.woden.ircclient.ui.settings.history.HistoryControls;
import cafe.woden.ircclient.ui.settings.history.HistoryControlsSupport;
import cafe.woden.ircclient.ui.settings.history.HistoryStoragePanelSupport;
import cafe.woden.ircclient.ui.settings.history.LoggingControls;
import cafe.woden.ircclient.ui.settings.history.LoggingControlsSupport;
import cafe.woden.ircclient.ui.settings.ircv3.Ircv3CapabilitiesControls;
import cafe.woden.ircclient.ui.settings.ircv3.Ircv3PanelSupport;
import cafe.woden.ircclient.ui.settings.notifications.IrcEventNotificationControls;
import cafe.woden.ircclient.ui.settings.notifications.IrcEventNotificationsTabSupport;
import cafe.woden.ircclient.ui.settings.notifications.NotificationRulesControls;
import cafe.woden.ircclient.ui.settings.notifications.NotificationRulesControlsSupport;
import cafe.woden.ircclient.ui.settings.notifications.NotificationsPanelSupport;
import cafe.woden.ircclient.ui.settings.spellcheck.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettingsTestFixtures;
import cafe.woden.ircclient.ui.settings.theme.ThemeAccentSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.ThemeAppearanceSettingsTestFixtures;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import cafe.woden.ircclient.ui.settings.theme.ThemeTweakSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.spi.ThemeOption;
import cafe.woden.ircclient.ui.settings.theme.spi.ThemePack;
import cafe.woden.ircclient.ui.settings.theme.spi.ThemeTone;
import cafe.woden.ircclient.ui.settings.tray.TrayControls;
import cafe.woden.ircclient.ui.settings.tray.TrayControlsSupport;
import cafe.woden.ircclient.ui.settings.tray.TrayNotificationsPanelSupport;
import cafe.woden.ircclient.ui.shell.LagIndicatorService;
import cafe.woden.ircclient.ui.shell.UpdateNotifierService;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
    AppearanceFixture fixture = buildAppearanceFixture(ChatThemeSettingsTestFixtures.defaults());

    assertNotNull(findLabel(fixture.appearancePanel, "Message colors"));
    assertNotNull(findLabel(fixture.appearancePanel, "Server/system"));
    assertNotNull(findLabel(fixture.appearancePanel, "User messages"));
    assertNotNull(findLabel(fixture.appearancePanel, "Notice messages"));
    assertNotNull(findLabel(fixture.appearancePanel, "Action messages"));
    assertNotNull(findLabel(fixture.appearancePanel, "Presence messages"));
    assertNotNull(findLabel(fixture.appearancePanel, "Error messages"));
    assertNotNull(findLabel(fixture.appearancePanel, "New message color"));
    assertNotNull(findLabel(fixture.appearancePanel, "Notification color"));
  }

  @Test
  void serverTreeColorsCanBeConfiguredIndependentlyAndClearedToAutomaticDefaults()
      throws Exception {
    AppearanceFixture fixture = buildAppearanceFixture(ChatThemeSettingsTestFixtures.defaults());
    Object controls = readField(fixture.section, "serverTree");
    Object unread = readField(controls, "unreadChannelColor");
    Object notification = readField(controls, "highlightChannelColor");
    JTextField unreadHex = (JTextField) readField(unread, "hex");
    JTextField notificationHex = (JTextField) readField(notification, "hex");
    javax.swing.SwingUtilities.invokeAndWait(
        () -> {
          unreadHex.setText("123abc");
          notificationHex.setText("#DE4567");
        });
    var settings = fixture.section.readServerTreeSettings();
    assertEquals("#123ABC", settings.unreadChannelColor());
    assertEquals("#DE4567", settings.highlightChannelColor());
    javax.swing.SwingUtilities.invokeAndWait(
        () -> {
          try {
            ((JButton) readField(unread, "clear")).doClick();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        });
    assertNull(fixture.section.readServerTreeSettings().unreadChannelColor());
    assertEquals("#DE4567", fixture.section.readServerTreeSettings().highlightChannelColor());
    javax.swing.SwingUtilities.invokeAndWait(
        () -> findButton(fixture.appearancePanel, "Reset to defaults").doClick());
    assertNull(fixture.section.readServerTreeSettings().unreadChannelColor());
    assertNull(fixture.section.readServerTreeSettings().highlightChannelColor());
  }

  @Test
  void resetToDefaultsClearsMessageColorOverrides() throws Exception {
    AppearanceFixture fixture =
        buildAppearanceFixture(
            ChatThemeSettingsTestFixtures.builder()
                .preset(ChatThemeSettings.Preset.ACCENTED)
                .timestampColor("#111111")
                .systemColor("#222222")
                .mentionBgColor("#333333")
                .mentionStrength(60)
                .messageColor("#444444")
                .noticeColor("#555555")
                .actionColor("#666666")
                .errorColor("#777777")
                .presenceColor("#888888")
                .build());

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
                SettingsColorSupport.normalizeOptionalHexForApply("#12GG34", "User message color"));
    assertEquals(
        "User message color must be a hex value like #RRGGBB (or blank for default).",
        ex.getMessage());
    assertNull(SettingsColorSupport.normalizeOptionalHexForApply("   ", "User message color"));
    assertEquals(
        "#AABBCC", SettingsColorSupport.normalizeOptionalHexForApply("#abc", "User message color"));
  }

  @Test
  void filtersPanelExposesSubTabsAndHistoryRunCapToggle() throws Exception {
    List<AutoCloseable> closeables = new ArrayList<>();
    FilterSettings filters =
        FilterSettingsTestFixtures.historyPlaceholdersDisabledBuilder().build();
    FilterSettingsBus filterSettingsBus = new FilterSettingsBus(null);
    filterSettingsBus.set(filters);

    FilterControls controls =
        FilterControlsSupport.buildControls(
            filters,
            null,
            closeables,
            filterSettingsBus,
            mock(FilterSettingsConfigPort.class),
            mock(ActiveTargetPort.class),
            mock(TranscriptRebuildService.class));
    JPanel panel = FiltersPanelSupport.buildPanel(controls);

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
    List<AutoCloseable> closeables = new ArrayList<>();

    NotificationRulesControls notifications =
        NotificationRulesControlsSupport.buildControls(
            testUiSettings(), closeables, mock(ExecutorService.class));
    IrcEventNotificationControls ircEvents =
        IrcEventNotificationsTabSupport.buildControls(IrcEventNotificationRule.defaults());

    JPanel panel =
        NotificationsPanelSupport.buildPanel(
            notifications,
            IrcEventNotificationsTabSupport.buildTab(ircEvents, null, (title, seed) -> seed),
            null,
            (title, seed) -> seed,
            NotificationRulesControlsSupport::refreshValidation);
    assertNotNull(findTabbedPaneWithTab(panel, "Rules"));
    assertNotNull(findTabbedPaneWithTab(panel, "Test"));
    assertNotNull(findTabbedPaneWithTab(panel, "IRC Events"));

    closeAll(closeables);
  }

  @Test
  void historyStoragePanelUsesFocusedSubTabs() throws Exception {
    List<AutoCloseable> closeables = new ArrayList<>();

    HistoryControls history =
        HistoryControlsSupport.buildControls(testUiSettings(), closeables, false, false);
    LoggingControls logging =
        LoggingControlsSupport.buildControls(
            (ChatLoggingRuntimeConfigPort) null,
            (LogProperties) null,
            closeables,
            mock(ServerDialogs.class),
            null);
    JPanel panel = HistoryStoragePanelSupport.buildPanel(logging, history);

    assertNotNull(findTabbedPaneWithTab(panel, "Logging"));
    assertNotNull(findTabbedPaneWithTab(panel, "Scrolling & Loading"));
    assertNotNull(findTabbedPaneWithTab(panel, "Remote & Limits"));

    closeAll(closeables);
  }

  @Test
  void commandsPanelIncludesAliasImportAndUnknownFallbackToggle() throws Exception {
    UserCommandAliasesControls controls =
        UserCommandAliasesControlsSupport.buildControls(
            List.of(new UserCommandAlias(true, "greet", "/msg %1 hello")), true, null);
    JPanel panel = UserCommandsPanelSupport.buildPanel(controls);

    JButton importHexChat = (JButton) readField(controls, "importHexChat");
    assertNotNull(importHexChat);
    assertTrue(
        String.valueOf(importHexChat.getToolTipText()).contains("Import aliases from HexChat"));
    JCheckBox unknownFallback = (JCheckBox) readField(controls, "unknownCommandAsRaw");
    assertTrue(unknownFallback.isSelected());
  }

  @Test
  void trayPanelSoundsTabExposesCustomSoundPathControls() throws Exception {
    TrayControls trayControls =
        TrayControlsSupport.buildControls(
            testUiSettings(),
            new NotificationSoundSettings(true, "NOTIF_1", true, "sounds/custom.wav"),
            PushyPropertiesTestFixtures.disabled(),
            mock(TrayRuntimeConfigPort.class),
            mock(UpdateNotifierRuntimeConfigPort.class),
            mock(LagIndicatorRuntimeConfigPort.class),
            mock(GnomeDbusNotificationBackend.class),
            mock(TrayNotificationService.class),
            mock(NotificationSoundService.class),
            mock(PushyNotificationService.class),
            mock(ExecutorService.class),
            source -> "");
    JPanel trayPanel = TrayNotificationsPanelSupport.buildPanel(trayControls);
    assertNotNull(findTabbedPaneWithTab(trayPanel, "Sounds"));

    JCheckBox soundsEnabled = (JCheckBox) readField(trayControls, "notificationSoundsEnabled");
    JCheckBox useCustom = (JCheckBox) readField(trayControls, "notificationSoundUseCustom");
    JCheckBox lagIndicatorEnabled = (JCheckBox) readField(trayControls, "lagIndicatorEnabled");
    JTextField customPath = (JTextField) readField(trayControls, "notificationSoundCustomPath");
    JButton browse = (JButton) readField(trayControls, "browseCustomSound");
    JButton clear = (JButton) readField(trayControls, "clearCustomSound");

    assertNotNull(lagIndicatorEnabled);
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
    UiSettings current = testUiSettings();
    Ircv3CapabilityConfigPort ircv3CapabilityRuntimeConfig = mock(Ircv3CapabilityConfigPort.class);
    ChatBehaviorRuntimeConfigPort chatBehaviorRuntimeConfig =
        mock(ChatBehaviorRuntimeConfigPort.class);
    when(ircv3CapabilityRuntimeConfig.readIrcv3Capabilities()).thenReturn(Map.of());
    when(chatBehaviorRuntimeConfig.readServerTreeUnreadBadgeScalePercent(100)).thenReturn(100);

    JCheckBox send = ChatBehaviorControlsSupport.buildTypingIndicatorsSendCheckbox(current);
    JCheckBox receive = ChatBehaviorControlsSupport.buildTypingIndicatorsReceiveCheckbox(current);
    JCheckBox treeDisplay =
        ChatBehaviorControlsSupport.buildTypingIndicatorsTreeDisplayCheckbox(current);
    JCheckBox usersDisplay =
        ChatBehaviorControlsSupport.buildTypingIndicatorsUsersListDisplayCheckbox(current);
    JCheckBox transcriptDisplay =
        ChatBehaviorControlsSupport.buildTypingIndicatorsTranscriptDisplayCheckbox(current);
    JCheckBox sendSignalDisplay =
        ChatBehaviorControlsSupport.buildTypingIndicatorsSendSignalDisplayCheckbox(current);
    JComboBox<?> style = ChatBehaviorControlsSupport.buildTypingTreeIndicatorStyleCombo(current);
    JComboBox<?> matrixUserListNameDisplayMode =
        ChatBehaviorControlsSupport.buildMatrixUserListNameDisplayModeCombo(current);
    JCheckBox badgesEnabled =
        ChatBehaviorControlsSupport.buildServerTreeNotificationBadgesCheckbox(current);
    JSpinner badgeScale =
        ChatBehaviorControlsSupport.buildServerTreeUnreadBadgeScalePercentSpinner(
            chatBehaviorRuntimeConfig);
    Ircv3CapabilitiesControls capabilities =
        Ircv3PanelSupport.buildCapabilitiesControls(
            ircv3CapabilityRuntimeConfig, Ircv3ExtensionCatalog.builtInCatalog());

    JPanel panel =
        Ircv3PanelSupport.buildPanel(
            send,
            receive,
            treeDisplay,
            usersDisplay,
            transcriptDisplay,
            sendSignalDisplay,
            style,
            matrixUserListNameDisplayMode,
            badgesEnabled,
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
    List<AutoCloseable> closeables = new ArrayList<>();

    NotificationRulesControlsSupport.buildControls(testUiSettings(), closeables, rulesExec);
    closeAll(closeables);

    verify(rulesExec, never()).shutdownNow();
    verify(pushyExec, never()).shutdownNow();
  }

  private static AppearanceFixture buildAppearanceFixture(ChatThemeSettings chatTheme)
      throws Exception {
    UiSettings current = testUiSettings();
    List<AutoCloseable> closeables = new ArrayList<>();
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(current);
    ThemeManager themeManager = mock(ThemeManager.class);
    when(themeManager.supportedThemes())
        .thenReturn(
            new ThemeOption[] {
              new ThemeOption("darcula", "Darcula", ThemeTone.DARK, ThemePack.FLATLAF, true)
            });
    ThemeAccentSettingsBus accentSettingsBus = mock(ThemeAccentSettingsBus.class);
    when(accentSettingsBus.get())
        .thenReturn(
            ThemeAppearanceSettingsTestFixtures.accent(
                cafe.woden.ircclient.config.properties.UiProperties.DEFAULT_ACCENT_COLOR,
                cafe.woden.ircclient.config.properties.UiProperties.DEFAULT_ACCENT_STRENGTH));
    ThemeTweakSettingsBus tweakSettingsBus = mock(ThemeTweakSettingsBus.class);
    when(tweakSettingsBus.get()).thenReturn(ThemeAppearanceSettingsTestFixtures.tweakDefaults());
    ChatThemeSettingsBus chatThemeSettingsBus = mock(ChatThemeSettingsBus.class);
    when(chatThemeSettingsBus.get()).thenReturn(chatTheme);

    AppearancePreferencesSection section =
        AppearancePreferencesSection.build(
            current,
            closeables,
            settingsBus,
            themeManager,
            accentSettingsBus,
            tweakSettingsBus,
            chatThemeSettingsBus);
    return new AppearanceFixture(section.panel(), readField(section, "chatTheme"), section);
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
        mock(EmbedCardStyleBus.class),
        mock(ThemeManager.class),
        mock(ThemeAccentSettingsBus.class),
        mock(ThemeTweakSettingsBus.class),
        mock(ChatThemeSettingsBus.class),
        mock(SpellcheckSettingsBus.class),
        mock(PreferencesRuntimeConfigPort.class),
        mock(ClientTranslationRuntimeConfigPort.class),
        mock(NetworkSettingsRuntimeConfigPort.class),
        mock(LaunchJvmRuntimeConfigPort.class),
        mock(ThemeAppearanceRuntimeConfigPort.class),
        mock(ChatAppearanceRuntimeConfigPort.class),
        mock(ServerTreeAppearanceRuntimeConfigPort.class),
        mock(DockLayoutRuntimeConfigPort.class),
        mock(ChatBehaviorRuntimeConfigPort.class),
        mock(TrayRuntimeConfigPort.class),
        mock(UpdateNotifierRuntimeConfigPort.class),
        mock(LagIndicatorRuntimeConfigPort.class),
        mock(PushyRuntimeConfigPort.class),
        mock(MemoryUsageRuntimeConfigPort.class),
        mock(ChatLoggingRuntimeConfigPort.class),
        mock(ChatHistoryRuntimeConfigPort.class),
        mock(DiagnosticsRuntimeConfigPort.class),
        mock(FilterSettingsConfigPort.class),
        mock(EmbedPreviewRuntimeConfigPort.class),
        mock(Ircv3CapabilityConfigPort.class),
        mock(EmbedLoadPolicyConfigPort.class),
        mock(CtcpReplyRuntimeConfigPort.class),
        mock(OutgoingMessageRuntimeConfigPort.class),
        mock(TimestampRuntimeConfigPort.class),
        mock(SpellcheckRuntimeConfigPort.class),
        mock(NickColorRuntimeConfigPort.class),
        mock(UserCommandAliasesConfigPort.class),
        mock(NotificationRuntimeConfigPort.class),
        mock(UserLookupRuntimeConfigPort.class),
        mock(LogProperties.class),
        mock(NickColorSettingsBus.class),
        mock(NickColorService.class),
        mock(NickColorOverridesDialog.class),
        mock(EmbedLoadPolicyDialog.class),
        mock(EmbedLoadPolicyBus.class),
        mock(IrcHeartbeatMaintenanceService.class),
        mock(FilterSettingsBus.class),
        mock(TranscriptRebuildService.class),
        mock(ActiveTargetPort.class),
        mock(TrayService.class),
        mock(TrayNotificationService.class),
        mock(UpdateNotifierService.class),
        mock(LagIndicatorService.class),
        mock(GnomeDbusNotificationBackend.class),
        mock(NotificationSoundSettingsBus.class),
        mock(PushySettingsBus.class),
        mock(PushyNotificationService.class),
        mock(IrcEventNotificationRulesBus.class),
        mock(UserCommandAliasesBus.class),
        mock(NotificationSoundService.class),
        mock(ServerDialogs.class),
        mock(MessageTranslationSettingsBus.class),
        pushyTestExecutor,
        notificationRuleTestExecutor);
  }

  private static UiSettings testUiSettings() {
    return UiSettingsTestFixtures.legacyBuilder().build();
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

  private record AppearanceFixture(
      JPanel appearancePanel, Object chatThemeControls, AppearancePreferencesSection section) {}
}
