package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.NotificationRule;
import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort.EmbedLoadPolicySnapshot;
import cafe.woden.ircclient.irc.backend.IrcHeartbeatMaintenanceService;
import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.model.UserCommandAlias;
import cafe.woden.ircclient.net.NetHeartbeatContext;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.pushy.PushySettingsBus;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettings;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettingsBus;
import cafe.woden.ircclient.ui.SwingEdt;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettings;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import cafe.woden.ircclient.ui.chat.embed.EmbedLoadPolicyBus;
import cafe.woden.ircclient.ui.filter.FilterSettings;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.ThemeAccentSettings;
import cafe.woden.ircclient.ui.settings.theme.ThemeAccentSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.ThemeIdUtils;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import cafe.woden.ircclient.ui.settings.theme.ThemeTweakSettings;
import cafe.woden.ircclient.ui.settings.theme.ThemeTweakSettingsBus;
import cafe.woden.ircclient.ui.shell.LagIndicatorService;
import cafe.woden.ircclient.ui.shell.UpdateNotifierService;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.DialogCloseableScopeDecorator;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import com.formdev.flatlaf.FlatClientProperties;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumn;
import net.miginfocom.swing.MigLayout;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
@Lazy
public class PreferencesDialog {
  static final String DENSITY_TOOLTIP =
      "Changes the overall UI spacing / row height. Changes preview live; Apply/OK saves.";
  static final String CORNER_RADIUS_TOOLTIP =
      "Controls rounded corner radius for buttons/fields/etc. Changes preview live; Apply/OK saves.";
  private static final String FLAT_ONLY_TOOLTIP = "Available for FlatLaf-based themes only.";
  static final String UI_FONT_OVERRIDE_TOOLTIP =
      "Overrides the global Swing UI font family and size for controls, menus, tabs, and dialogs.";
  private static final String DEFAULT_GENERIC_BOUNCER_LOGIN_TEMPLATE = "{base}/{network}";
  private static final boolean DEFAULT_GENERIC_BOUNCER_PREFER_LOGIN_HINT = true;

  private final UiSettingsBus settingsBus;
  private final EmbedCardStyleBus embedCardStyleBus;
  private final ThemeManager themeManager;
  private final ThemeAccentSettingsBus accentSettingsBus;
  private final ThemeTweakSettingsBus tweakSettingsBus;
  private final ChatThemeSettingsBus chatThemeSettingsBus;
  private final SpellcheckSettingsBus spellcheckSettingsBus;
  private final RuntimeConfigStore runtimeConfig;
  private final LogProperties logProps;
  private final NickColorSettingsBus nickColorSettingsBus;
  private final NickColorService nickColorService;
  private final NickColorOverridesDialog nickColorOverridesDialog;
  private final EmbedLoadPolicyDialog embedLoadPolicyDialog;
  private final EmbedLoadPolicyBus embedLoadPolicyBus;
  private final IrcHeartbeatMaintenanceService ircHeartbeatMaintenancePort;
  private final FilterSettingsBus filterSettingsBus;
  private final TranscriptRebuildService transcriptRebuildService;
  private final ActiveTargetPort targetCoordinator;
  private final TrayService trayService;
  private final TrayNotificationService trayNotificationService;
  private final UpdateNotifierService updateNotifierService;
  private final LagIndicatorService lagIndicatorService;
  private final GnomeDbusNotificationBackend gnomeDbusBackend;
  private final NotificationSoundSettingsBus notificationSoundSettingsBus;
  private final PushySettingsBus pushySettingsBus;
  private final PushyNotificationService pushyNotificationService;
  private final IrcEventNotificationRulesBus ircEventNotificationRulesBus;
  private final UserCommandAliasesBus userCommandAliasesBus;
  private final NotificationSoundService notificationSoundService;
  private final ServerDialogs serverDialogs;
  private final ExecutorService pushyTestExecutor;
  private final ExecutorService notificationRuleTestExecutor;

  private JDialog dialog;

  public PreferencesDialog(
      UiSettingsBus settingsBus,
      EmbedCardStyleBus embedCardStyleBus,
      ThemeManager themeManager,
      ThemeAccentSettingsBus accentSettingsBus,
      ThemeTweakSettingsBus tweakSettingsBus,
      ChatThemeSettingsBus chatThemeSettingsBus,
      SpellcheckSettingsBus spellcheckSettingsBus,
      RuntimeConfigStore runtimeConfig,
      LogProperties logProps,
      NickColorSettingsBus nickColorSettingsBus,
      NickColorService nickColorService,
      NickColorOverridesDialog nickColorOverridesDialog,
      EmbedLoadPolicyDialog embedLoadPolicyDialog,
      EmbedLoadPolicyBus embedLoadPolicyBus,
      IrcHeartbeatMaintenanceService ircHeartbeatMaintenancePort,
      FilterSettingsBus filterSettingsBus,
      TranscriptRebuildService transcriptRebuildService,
      ActiveTargetPort targetCoordinator,
      TrayService trayService,
      TrayNotificationService trayNotificationService,
      UpdateNotifierService updateNotifierService,
      LagIndicatorService lagIndicatorService,
      GnomeDbusNotificationBackend gnomeDbusBackend,
      NotificationSoundSettingsBus notificationSoundSettingsBus,
      PushySettingsBus pushySettingsBus,
      PushyNotificationService pushyNotificationService,
      IrcEventNotificationRulesBus ircEventNotificationRulesBus,
      UserCommandAliasesBus userCommandAliasesBus,
      NotificationSoundService notificationSoundService,
      ServerDialogs serverDialogs,
      @Qualifier(ExecutorConfig.PREFERENCES_PUSHY_TEST_EXECUTOR) ExecutorService pushyTestExecutor,
      @Qualifier(ExecutorConfig.PREFERENCES_NOTIFICATION_RULE_TEST_EXECUTOR)
          ExecutorService notificationRuleTestExecutor) {
    this.settingsBus = settingsBus;
    this.embedCardStyleBus = embedCardStyleBus;
    this.themeManager = themeManager;
    this.accentSettingsBus = accentSettingsBus;
    this.tweakSettingsBus = tweakSettingsBus;
    this.chatThemeSettingsBus = chatThemeSettingsBus;
    this.spellcheckSettingsBus = spellcheckSettingsBus;
    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;
    this.nickColorSettingsBus = nickColorSettingsBus;
    this.nickColorService = nickColorService;
    this.nickColorOverridesDialog = nickColorOverridesDialog;
    this.embedLoadPolicyDialog = embedLoadPolicyDialog;
    this.embedLoadPolicyBus = embedLoadPolicyBus;
    this.ircHeartbeatMaintenancePort = ircHeartbeatMaintenancePort;
    this.filterSettingsBus = filterSettingsBus;
    this.transcriptRebuildService = transcriptRebuildService;
    this.targetCoordinator = targetCoordinator;
    this.trayService = trayService;
    this.trayNotificationService = trayNotificationService;
    this.updateNotifierService = updateNotifierService;
    this.lagIndicatorService = lagIndicatorService;
    this.gnomeDbusBackend = gnomeDbusBackend;
    this.notificationSoundSettingsBus = notificationSoundSettingsBus;
    this.pushySettingsBus = pushySettingsBus;
    this.pushyNotificationService = pushyNotificationService;
    this.ircEventNotificationRulesBus = ircEventNotificationRulesBus;
    this.userCommandAliasesBus = userCommandAliasesBus;
    this.notificationSoundService = notificationSoundService;
    this.serverDialogs = serverDialogs;
    this.pushyTestExecutor = Objects.requireNonNull(pushyTestExecutor, "pushyTestExecutor");
    this.notificationRuleTestExecutor =
        Objects.requireNonNull(notificationRuleTestExecutor, "notificationRuleTestExecutor");
    if (this.pushyTestExecutor.isShutdown()) {
      throw new IllegalArgumentException("pushyTestExecutor must be active");
    }
    if (this.notificationRuleTestExecutor.isShutdown()) {
      throw new IllegalArgumentException("notificationRuleTestExecutor must be active");
    }
  }

  public void open(Window owner) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> open(owner));
      return;
    }

    if (dialog != null && dialog.isShowing()) {
      dialog.toFront();
      dialog.requestFocus();
      return;
    }

    UiSettings current = settingsBus.get();

    Map<String, String> themeLabelById = buildThemeLabelById();

    List<AutoCloseable> closeables = new ArrayList<>();

    ThemeControls theme = AppearanceControlsSupport.buildThemeControls(current, themeLabelById);
    FontControls fonts = AppearanceControlsSupport.buildFontControls(current, closeables);
    ThemeAccentSettings initialAccent =
        accentSettingsBus != null
            ? accentSettingsBus.get()
            : new ThemeAccentSettings(
                UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);
    AccentControls accent = AppearanceControlsSupport.buildAccentControls(initialAccent);
    ThemeTweakSettings initialTweaks =
        tweakSettingsBus != null
            ? tweakSettingsBus.get()
            : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);
    TweakControls tweaks = AppearanceControlsSupport.buildTweakControls(initialTweaks, closeables);

    ChatThemeSettings initialChatTheme =
        chatThemeSettingsBus != null
            ? chatThemeSettingsBus.get()
            : new ChatThemeSettings(
                ChatThemeSettings.Preset.DEFAULT,
                null,
                null,
                null,
                35,
                null,
                null,
                null,
                null,
                null);
    ChatThemeControls chatTheme =
        AppearanceControlsSupport.buildChatThemeControls(initialChatTheme);

    // Allow mousewheel selection cycling for Appearance-tab combos.
    try {
      closeables.add(MouseWheelDecorator.decorateComboBoxSelection(theme.combo));
    } catch (Exception ignored) {
    }
    try {
      closeables.add(MouseWheelDecorator.decorateComboBoxSelection(chatTheme.preset));
    } catch (Exception ignored) {
    }
    try {
      closeables.add(MouseWheelDecorator.decorateComboBoxSelection(tweaks.density));
    } catch (Exception ignored) {
    }

    // Live preview snapshot: used to rollback any preview-only changes on Cancel / window close.
    final java.util.concurrent.atomic.AtomicReference<String> committedThemeId =
        new java.util.concurrent.atomic.AtomicReference<>(
            normalizeThemeIdInternal(current != null ? current.theme() : null));
    final java.util.concurrent.atomic.AtomicReference<String> lastPreviewThemeId =
        new java.util.concurrent.atomic.AtomicReference<>(committedThemeId.get());
    final java.util.concurrent.atomic.AtomicReference<UiSettings> committedUiSettings =
        new java.util.concurrent.atomic.AtomicReference<>(current);
    final java.util.concurrent.atomic.AtomicReference<EmbedLoadPolicySnapshot>
        pendingEmbedLoadPolicy =
            new java.util.concurrent.atomic.AtomicReference<>(
                embedLoadPolicyBus != null
                    ? embedLoadPolicyBus.get()
                    : runtimeConfig.readEmbedLoadPolicy());
    final java.util.concurrent.atomic.AtomicReference<ThemeAccentSettings> committedAccentSettings =
        new java.util.concurrent.atomic.AtomicReference<>(
            initialAccent != null
                ? initialAccent
                : new ThemeAccentSettings(
                    UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH));
    final java.util.concurrent.atomic.AtomicReference<ThemeTweakSettings> committedTweakSettings =
        new java.util.concurrent.atomic.AtomicReference<>(
            initialTweaks != null
                ? initialTweaks
                : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10));
    final java.util.concurrent.atomic.AtomicReference<ChatThemeSettings>
        committedChatThemeSettings =
            new java.util.concurrent.atomic.AtomicReference<>(
                initialChatTheme != null
                    ? initialChatTheme
                    : new ChatThemeSettings(
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

    final java.util.concurrent.atomic.AtomicBoolean suppressLivePreview =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    final java.util.concurrent.atomic.AtomicReference<String> lastValidAccentHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedAccentSettings.get() != null
                ? committedAccentSettings.get().accentColor()
                : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatTimestampHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedChatThemeSettings.get() != null
                ? committedChatThemeSettings.get().timestampColor()
                : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatSystemHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedChatThemeSettings.get() != null
                ? committedChatThemeSettings.get().systemColor()
                : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatMentionHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedChatThemeSettings.get() != null
                ? committedChatThemeSettings.get().mentionBgColor()
                : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatMessageHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedChatThemeSettings.get() != null
                ? committedChatThemeSettings.get().messageColor()
                : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatNoticeHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedChatThemeSettings.get() != null
                ? committedChatThemeSettings.get().noticeColor()
                : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatActionHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedChatThemeSettings.get() != null
                ? committedChatThemeSettings.get().actionColor()
                : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatErrorHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedChatThemeSettings.get() != null
                ? committedChatThemeSettings.get().errorColor()
                : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatPresenceHex =
        new java.util.concurrent.atomic.AtomicReference<>(
            committedChatThemeSettings.get() != null
                ? committedChatThemeSettings.get().presenceColor()
                : null);

    // Debounced live preview to avoid spamming full UI refreshes while sliders are dragged.
    final Runnable applyLafPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          if (themeManager == null) return;

          String sel = normalizeThemeIdInternal(String.valueOf(theme.combo.getSelectedItem()));
          if (sel.isBlank()) return;

          if (tweakSettingsBus != null) {
            DensityOption opt = (DensityOption) tweaks.density.getSelectedItem();
            String densityId = opt != null ? opt.id : "auto";
            String uiFontFamily =
                Objects.toString(tweaks.uiFontFamily.getSelectedItem(), "").trim();
            if (uiFontFamily.isBlank()) uiFontFamily = ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY;
            ThemeTweakSettings nextTweaks =
                new ThemeTweakSettings(
                    ThemeTweakSettings.ThemeDensity.from(densityId),
                    tweaks.cornerRadius.getValue(),
                    tweaks.uiFontOverrideEnabled.isSelected(),
                    uiFontFamily,
                    ((Number) tweaks.uiFontSize.getValue()).intValue());
            tweakSettingsBus.set(nextTweaks);
          }

          if (accentSettingsBus != null) {
            String hex = null;
            if (accent.enabled.isSelected()) {
              String raw = accent.hex.getText();
              raw = raw != null ? raw.trim() : "";

              if (raw.isBlank()) {
                lastValidAccentHex.set(null);
                hex = null;
              } else {
                Color c = parseHexColorLenient(raw);
                if (c != null) {
                  hex = toHex(c);
                  lastValidAccentHex.set(hex);
                } else {
                  // If the user is mid-typing an invalid value, keep the last valid color.
                  hex = lastValidAccentHex.get();
                }
              }
            }

            ThemeAccentSettings nextAccent =
                new ThemeAccentSettings(hex, accent.strength.getValue());
            accentSettingsBus.set(nextAccent);
          }

          if (!java.util.Objects.equals(sel, lastPreviewThemeId.get())) {
            themeManager.applyTheme(sel);
            lastPreviewThemeId.set(sel);
          } else {
            themeManager.applyAppearance(true);
          }

          // Theme switches can change the "Theme" accent color; refresh the preview pill.
          try {
            accent.updateChip.run();
          } catch (Exception ignored) {
          }
        };
    final RxDebouncedEdtTrigger lafPreviewDebounce =
        new RxDebouncedEdtTrigger(140, applyLafPreview);
    final Runnable scheduleLafPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          lafPreviewDebounce.trigger();
        };

    final java.util.function.BiFunction<
            JTextField, java.util.concurrent.atomic.AtomicReference<String>, String>
        parseOptionalHex =
            (field, lastRef) -> {
              String raw = field != null ? field.getText() : null;
              raw = raw != null ? raw.trim() : "";
              if (raw.isBlank()) {
                lastRef.set(null);
                return null;
              }
              Color c = parseHexColorLenient(raw);
              if (c == null) {
                return lastRef.get();
              }
              String hex = toHex(c);
              lastRef.set(hex);
              return hex;
            };
    final Runnable applyChatPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          if (themeManager == null) return;
          if (chatThemeSettingsBus == null) return;

          ChatThemeSettings.Preset presetV =
              (chatTheme.preset.getSelectedItem() instanceof ChatThemeSettings.Preset p)
                  ? p
                  : ChatThemeSettings.Preset.DEFAULT;

          String tsHexV =
              parseOptionalHex.apply(chatTheme.timestamp.hex, lastValidChatTimestampHex);
          String sysHexV = parseOptionalHex.apply(chatTheme.system.hex, lastValidChatSystemHex);
          String menHexV = parseOptionalHex.apply(chatTheme.mention.hex, lastValidChatMentionHex);
          String msgHexV = parseOptionalHex.apply(chatTheme.message.hex, lastValidChatMessageHex);
          String noticeHexV = parseOptionalHex.apply(chatTheme.notice.hex, lastValidChatNoticeHex);
          String actionHexV = parseOptionalHex.apply(chatTheme.action.hex, lastValidChatActionHex);
          String errHexV = parseOptionalHex.apply(chatTheme.error.hex, lastValidChatErrorHex);
          String presenceHexV =
              parseOptionalHex.apply(chatTheme.presence.hex, lastValidChatPresenceHex);
          int mentionStrengthV = chatTheme.mentionStrength.getValue();

          ChatThemeSettings nextChatTheme =
              new ChatThemeSettings(
                  presetV,
                  tsHexV,
                  sysHexV,
                  menHexV,
                  mentionStrengthV,
                  msgHexV,
                  noticeHexV,
                  actionHexV,
                  errHexV,
                  presenceHexV);
          chatThemeSettingsBus.set(nextChatTheme);
          themeManager.refreshChatStyles();
        };
    final RxDebouncedEdtTrigger chatPreviewDebounce =
        new RxDebouncedEdtTrigger(120, applyChatPreview);
    final Runnable scheduleChatPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          chatPreviewDebounce.trigger();
        };

    final Runnable applyFontPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          UiSettings base = settingsBus != null ? settingsBus.get() : null;
          if (base == null) return;

          String fam = java.util.Objects.toString(fonts.fontFamily.getSelectedItem(), "").trim();
          if (fam.isBlank()) fam = "Monospaced";
          int size = ((Number) fonts.fontSize.getValue()).intValue();
          if (size < 8) size = 8;
          if (size > 48) size = 48;

          settingsBus.set(base.withChatFontFamily(fam).withChatFontSize(size));
        };
    final RxDebouncedEdtTrigger fontPreviewDebounce =
        new RxDebouncedEdtTrigger(120, applyFontPreview);
    final Runnable scheduleFontPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          fontPreviewDebounce.trigger();
        };

    closeables.add(
        () -> {
          lafPreviewDebounce.close();
          chatPreviewDebounce.close();
          fontPreviewDebounce.close();
        });

    final ThemeAccentSettings defaultAccentSettings =
        new ThemeAccentSettings(
            UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);
    final ThemeTweakSettings defaultTweakSettings =
        new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);
    final ChatThemeSettings defaultChatThemeSettings =
        new ChatThemeSettings(
            ChatThemeSettings.Preset.DEFAULT, null, null, null, 35, null, null, null, null, null);

    final Runnable restoreCommittedAppearance =
        () -> {
          if (themeManager == null) return;
          suppressLivePreview.set(true);
          try {
            lafPreviewDebounce.cancelPending();
            chatPreviewDebounce.cancelPending();
            fontPreviewDebounce.cancelPending();

            UiSettings committedUi = committedUiSettings.get();
            UiSettings liveUi = settingsBus != null ? settingsBus.get() : null;
            ThemeAccentSettings targetAccent =
                committedAccentSettings.get() != null
                    ? committedAccentSettings.get()
                    : defaultAccentSettings;
            ThemeAccentSettings liveAccent =
                accentSettingsBus != null ? accentSettingsBus.get() : null;
            ThemeTweakSettings targetTweaks =
                committedTweakSettings.get() != null
                    ? committedTweakSettings.get()
                    : defaultTweakSettings;
            ThemeTweakSettings liveTweaks =
                tweakSettingsBus != null ? tweakSettingsBus.get() : null;
            ChatThemeSettings targetChatTheme =
                committedChatThemeSettings.get() != null
                    ? committedChatThemeSettings.get()
                    : defaultChatThemeSettings;
            ChatThemeSettings liveChatTheme =
                chatThemeSettingsBus != null ? chatThemeSettingsBus.get() : null;
            String committedTheme = committedThemeId.get();
            String liveTheme = normalizeThemeIdInternal(liveUi != null ? liveUi.theme() : null);

            AppearanceRollbackPlan rollbackPlan =
                planAppearanceRollback(
                    committedTheme,
                    liveTheme,
                    committedUi,
                    liveUi,
                    accentSettingsBus != null,
                    targetAccent,
                    liveAccent,
                    tweakSettingsBus != null,
                    targetTweaks,
                    liveTweaks,
                    chatThemeSettingsBus != null,
                    targetChatTheme,
                    liveChatTheme);

            if (!rollbackPlan.hasAnyWork()) {
              lastPreviewThemeId.set(committedTheme);
              return;
            }
            if (rollbackPlan.restoreUiSettings() && committedUi != null) {
              settingsBus.set(committedUi);
            }
            if (rollbackPlan.restoreAccentSettings() && accentSettingsBus != null) {
              accentSettingsBus.set(targetAccent);
            }
            if (rollbackPlan.restoreTweakSettings() && tweakSettingsBus != null) {
              tweakSettingsBus.set(targetTweaks);
            }
            if (rollbackPlan.restoreChatThemeSettings() && chatThemeSettingsBus != null) {
              chatThemeSettingsBus.set(targetChatTheme);
            }

            if (rollbackPlan.applyTheme()) {
              themeManager.applyTheme(committedTheme);
              lastPreviewThemeId.set(committedTheme);
            } else if (rollbackPlan.applyAppearance()) {
              // Cancel/close should restore quickly and avoid an extra transition flash.
              themeManager.applyAppearance(false);
              lastPreviewThemeId.set(committedTheme);
            } else if (rollbackPlan.refreshChatStyles()) {
              themeManager.refreshChatStyles();
            }
          } finally {
            suppressLivePreview.set(false);
          }
        };

    final Runnable updateFlatTweakCapabilityUi =
        () -> {
          Object selectedTheme = theme.combo.getSelectedItem();
          String selectedThemeId = selectedTheme != null ? selectedTheme.toString() : "";
          boolean flatTweakCapable = supportsFlatLafTweaksInternal(selectedThemeId);
          tweaks.density.setEnabled(flatTweakCapable);
          tweaks.cornerRadius.setEnabled(flatTweakCapable);
          tweaks.density.setToolTipText(flatTweakCapable ? DENSITY_TOOLTIP : FLAT_ONLY_TOOLTIP);
          tweaks.cornerRadius.setToolTipText(
              flatTweakCapable ? CORNER_RADIUS_TOOLTIP : FLAT_ONLY_TOOLTIP);
        };
    updateFlatTweakCapabilityUi.run();

    final boolean[] ignoreThemeComboEvents = new boolean[] {true};
    theme.combo.addActionListener(
        e -> {
          if (ignoreThemeComboEvents[0]) return;
          updateFlatTweakCapabilityUi.run();
          scheduleLafPreview.run();
        });
    ignoreThemeComboEvents[0] = false;

    // LAF + accent/tweak preview
    accent.enabled.addActionListener(e -> scheduleLafPreview.run());
    accent.preset.addActionListener(e -> scheduleLafPreview.run());
    accent.strength.addChangeListener(e -> scheduleLafPreview.run());
    accent
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = accent.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidAccentHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidAccentHex.set(toHex(c));
                  }
                  scheduleLafPreview.run();
                }));
    tweaks.density.addActionListener(e -> scheduleLafPreview.run());
    tweaks.cornerRadius.addChangeListener(e -> scheduleLafPreview.run());
    tweaks.uiFontOverrideEnabled.addActionListener(
        e -> {
          tweaks.applyUiFontEnabledState.run();
          scheduleLafPreview.run();
        });
    tweaks.uiFontFamily.addActionListener(e -> scheduleLafPreview.run());
    tweaks.uiFontFamily.addItemListener(
        e -> {
          if (e != null && e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
            scheduleLafPreview.run();
          }
        });
    java.awt.Component uiFontFamilyEditor =
        tweaks.uiFontFamily.getEditor() != null
            ? tweaks.uiFontFamily.getEditor().getEditorComponent()
            : null;
    if (uiFontFamilyEditor instanceof JTextField tf) {
      tf.getDocument().addDocumentListener(new SimpleDocListener(scheduleLafPreview));
    }
    tweaks.uiFontSize.addChangeListener(e -> scheduleLafPreview.run());

    // Chat theme preview (transcript-only)
    chatTheme.preset.addActionListener(e -> scheduleChatPreview.run());
    chatTheme.mentionStrength.addChangeListener(e -> scheduleChatPreview.run());
    chatTheme
        .timestamp
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = chatTheme.timestamp.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidChatTimestampHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidChatTimestampHex.set(toHex(c));
                  }
                  scheduleChatPreview.run();
                }));
    chatTheme
        .system
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = chatTheme.system.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidChatSystemHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidChatSystemHex.set(toHex(c));
                  }
                  scheduleChatPreview.run();
                }));
    chatTheme
        .mention
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = chatTheme.mention.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidChatMentionHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidChatMentionHex.set(toHex(c));
                  }
                  scheduleChatPreview.run();
                }));
    chatTheme
        .message
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = chatTheme.message.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidChatMessageHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidChatMessageHex.set(toHex(c));
                  }
                  scheduleChatPreview.run();
                }));
    chatTheme
        .notice
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = chatTheme.notice.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidChatNoticeHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidChatNoticeHex.set(toHex(c));
                  }
                  scheduleChatPreview.run();
                }));
    chatTheme
        .action
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = chatTheme.action.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidChatActionHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidChatActionHex.set(toHex(c));
                  }
                  scheduleChatPreview.run();
                }));
    chatTheme
        .error
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = chatTheme.error.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidChatErrorHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidChatErrorHex.set(toHex(c));
                  }
                  scheduleChatPreview.run();
                }));
    chatTheme
        .presence
        .hex
        .getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  String raw = chatTheme.presence.hex.getText();
                  raw = raw != null ? raw.trim() : "";
                  if (raw.isBlank()) {
                    lastValidChatPresenceHex.set(null);
                  } else {
                    Color c = parseHexColorLenient(raw);
                    if (c != null) lastValidChatPresenceHex.set(toHex(c));
                  }
                  scheduleChatPreview.run();
                }));

    // Chat font preview
    fonts.fontFamily.addActionListener(e -> scheduleFontPreview.run());
    fonts.fontFamily.addItemListener(
        e -> {
          if (e != null && e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
            scheduleFontPreview.run();
          }
        });
    java.awt.Component ffEditor =
        fonts.fontFamily.getEditor() != null
            ? fonts.fontFamily.getEditor().getEditorComponent()
            : null;
    if (ffEditor instanceof JTextField tf) {
      tf.getDocument().addDocumentListener(new SimpleDocListener(scheduleFontPreview));
    }
    fonts.fontSize.addChangeListener(e -> scheduleFontPreview.run());
    JCheckBox autoConnectOnStart = buildAutoConnectCheckbox(current);
    LaunchJvmControls launchJvm = buildLaunchJvmControls();
    NotificationSoundSettings soundSettings =
        notificationSoundSettingsBus != null
            ? notificationSoundSettingsBus.get()
            : new NotificationSoundSettings(true, BuiltInSound.NOTIF_1.name(), false, null);
    PushyProperties pushySettings =
        pushySettingsBus != null
            ? pushySettingsBus.get()
            : new PushyProperties(false, null, null, null, null, null, null, null);
    TrayControls trayControls = buildTrayControls(current, soundSettings, pushySettings);

    EmbedCardStyle currentEmbedCardStyle =
        embedCardStyleBus != null ? embedCardStyleBus.get() : EmbedCardStyle.DEFAULT;
    ImageEmbedControls imageEmbeds = buildImageEmbedControls(current, closeables);
    LinkPreviewControls linkPreviews = buildLinkPreviewControls(current, currentEmbedCardStyle);
    JButton advancedEmbedPolicyButton =
        buildAdvancedEmbedPolicyButton(owner, pendingEmbedLoadPolicy);
    TimestampControls timestamps = buildTimestampControls(current);
    JComboBox<MemoryUsageDisplayMode> memoryUsageDisplayMode =
        buildMemoryUsageDisplayModeCombo(current);
    JSpinner memoryUsageRefreshIntervalMs =
        buildMemoryUsageRefreshIntervalSpinner(current, closeables);
    MemoryWarningControls memoryWarnings = buildMemoryWarningControls(current, closeables);

    JCheckBox presenceFolds = buildPresenceFoldsCheckbox(current);
    JCheckBox ctcpRequestsInActiveTarget = buildCtcpRequestsInActiveTargetCheckbox(current);
    JTextField defaultQuitMessage = buildDefaultQuitMessageField();
    SpellcheckSettings initialSpellcheck =
        spellcheckSettingsBus != null ? spellcheckSettingsBus.get() : SpellcheckSettings.defaults();
    SpellcheckControls spellcheck = buildSpellcheckControls(initialSpellcheck);
    CtcpAutoReplyControls ctcpAutoReplies = buildCtcpAutoReplyControls();
    JCheckBox typingIndicatorsSendEnabled = buildTypingIndicatorsSendCheckbox(current);
    JCheckBox typingIndicatorsReceiveEnabled = buildTypingIndicatorsReceiveCheckbox(current);
    JCheckBox typingIndicatorsTreeDisplayEnabled =
        buildTypingIndicatorsTreeDisplayCheckbox(current);
    JCheckBox typingIndicatorsUsersListDisplayEnabled =
        buildTypingIndicatorsUsersListDisplayCheckbox(current);
    JCheckBox typingIndicatorsTranscriptDisplayEnabled =
        buildTypingIndicatorsTranscriptDisplayCheckbox(current);
    JCheckBox typingIndicatorsSendSignalDisplayEnabled =
        buildTypingIndicatorsSendSignalDisplayCheckbox(current);
    JComboBox<TypingTreeIndicatorStyleOption> typingTreeIndicatorStyle =
        buildTypingTreeIndicatorStyleCombo(current);
    JComboBox<MatrixUserListNameDisplayModeOption> matrixUserListNameDisplayMode =
        buildMatrixUserListNameDisplayModeCombo(current);
    JCheckBox serverTreeNotificationBadgesEnabled =
        buildServerTreeNotificationBadgesCheckbox(current);
    JSpinner serverTreeUnreadBadgeScalePercent = buildServerTreeUnreadBadgeScalePercentSpinner();
    Ircv3CapabilitiesControls ircv3Capabilities =
        Ircv3PanelSupport.buildCapabilitiesControls(runtimeConfig);
    NickColorControls nickColors = buildNickColorControls(owner, closeables);

    try {
      closeables.add(MouseWheelDecorator.decorateComboBoxSelection(memoryUsageDisplayMode));
    } catch (Exception ignored) {
    }

    HistoryControls history = buildHistoryControls(current, closeables);
    LoggingControls logging = buildLoggingControls(logProps, closeables);

    OutgoingColorControls outgoing = buildOutgoingColorControls(current);
    JCheckBox outgoingDeliveryIndicators = buildOutgoingDeliveryIndicatorsCheckbox(current);
    NetworkAdvancedControls network = buildNetworkAdvancedControls(current, closeables);
    ProxyControls proxy = network.proxy;
    UserhostControls userhost = network.userhost;
    UserInfoEnrichmentControls enrichment = network.enrichment;
    HeartbeatControls heartbeat = network.heartbeat;
    BouncerControls bouncer = network.bouncer;
    JSpinner monitorIsonPollIntervalSeconds = network.monitorIsonPollIntervalSeconds;
    JCheckBox trustAllTlsCertificates = network.trustAllTlsCertificates;
    JCheckBox genericBouncerPreferLoginHint = bouncer.preferLoginHint;
    JTextField genericBouncerLoginTemplate = bouncer.loginTemplate;

    JPanel networkPanel = network.networkPanel;
    JPanel userLookupsPanel = network.userLookupsPanel;

    NotificationRulesControls notifications = buildNotificationRulesControls(current, closeables);
    IrcEventNotificationControls ircEventNotifications =
        buildIrcEventNotificationControls(
            ircEventNotificationRulesBus != null
                ? ircEventNotificationRulesBus.get()
                : IrcEventNotificationRule.defaults());

    FilterControls filters = buildFilterControls(filterSettingsBus.get(), closeables);
    UserCommandAliasesControls userCommands =
        buildUserCommandAliasesControls(
            userCommandAliasesBus != null ? userCommandAliasesBus.get() : List.of(),
            userCommandAliasesBus != null
                ? userCommandAliasesBus.unknownCommandAsRawEnabled()
                : runtimeConfig.readUnknownCommandAsRawEnabled(false));
    DiagnosticsControls diagnostics = buildDiagnosticsControls();

    AppearanceServerTreeControls appearanceServerTree =
        AppearanceControlsSupport.buildServerTreeControls(current);
    JPanel appearancePanel =
        AppearancePanelSupport.buildPanel(
            theme, accent, chatTheme, fonts, tweaks, appearanceServerTree);
    JPanel memoryPanel =
        buildMemoryPanel(memoryUsageDisplayMode, memoryUsageRefreshIntervalMs, memoryWarnings);
    JPanel startupPanel = buildStartupPanel(autoConnectOnStart, launchJvm);
    JPanel trayPanel = buildTrayNotificationsPanel(trayControls);
    JPanel chatPanel =
        buildChatPanel(
            presenceFolds,
            ctcpRequestsInActiveTarget,
            defaultQuitMessage,
            spellcheck,
            nickColors,
            timestamps,
            outgoing,
            outgoingDeliveryIndicators);
    JPanel ctcpRepliesPanel = buildCtcpRepliesPanel(ctcpAutoReplies);
    JPanel ircv3Panel =
        Ircv3PanelSupport.buildPanel(
            typingIndicatorsSendEnabled,
            typingIndicatorsReceiveEnabled,
            typingIndicatorsTreeDisplayEnabled,
            typingIndicatorsUsersListDisplayEnabled,
            typingIndicatorsTranscriptDisplayEnabled,
            typingIndicatorsSendSignalDisplayEnabled,
            typingTreeIndicatorStyle,
            matrixUserListNameDisplayMode,
            serverTreeNotificationBadgesEnabled,
            serverTreeUnreadBadgeScalePercent,
            ircv3Capabilities);
    JPanel embedsPanel =
        buildEmbedsAndPreviewsPanel(imageEmbeds, linkPreviews, advancedEmbedPolicyButton);
    JPanel historyStoragePanel = buildHistoryAndStoragePanel(logging, history);
    JPanel notificationsPanel = buildNotificationsPanel(notifications, ircEventNotifications);
    JPanel commandsPanel = buildUserCommandsPanel(userCommands);
    JPanel diagnosticsPanel = buildDiagnosticsPanel(diagnostics);
    JPanel filtersPanel = buildFiltersPanel(filters);

    JButton apply = new JButton("Apply");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");

    apply.setIcon(SvgIcons.action("check", 16));
    apply.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    ok.setIcon(SvgIcons.action("check", 16));
    ok.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    cancel.setIcon(SvgIcons.action("close", 16));
    cancel.setDisabledIcon(SvgIcons.actionDisabled("close", 16));

    attachNotificationRuleValidation(notifications, apply, ok);

    Runnable doApply =
        () -> {
          String t = String.valueOf(theme.combo.getSelectedItem());
          String fam = String.valueOf(fonts.fontFamily.getSelectedItem());
          int size = ((Number) fonts.fontSize.getValue()).intValue();

          ThemeTweakSettings prevTweaks =
              tweakSettingsBus != null
                  ? tweakSettingsBus.get()
                  : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);
          DensityOption densityOpt = (DensityOption) tweaks.density.getSelectedItem();
          String densityIdV = densityOpt != null ? densityOpt.id : "auto";
          int cornerRadiusV = tweaks.cornerRadius.getValue();
          String uiFontFamilyV = Objects.toString(tweaks.uiFontFamily.getSelectedItem(), "").trim();
          if (uiFontFamilyV.isBlank()) uiFontFamilyV = ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY;
          int uiFontSizeV = ((Number) tweaks.uiFontSize.getValue()).intValue();
          ThemeTweakSettings nextTweaks =
              new ThemeTweakSettings(
                  ThemeTweakSettings.ThemeDensity.from(densityIdV),
                  cornerRadiusV,
                  tweaks.uiFontOverrideEnabled.isSelected(),
                  uiFontFamilyV,
                  uiFontSizeV);
          boolean tweaksChanged = !java.util.Objects.equals(prevTweaks, nextTweaks);

          ThemeAccentSettings prevAccent =
              accentSettingsBus != null
                  ? accentSettingsBus.get()
                  : new ThemeAccentSettings(
                      UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);
          boolean accentOverrideEnabledV = accent.enabled.isSelected();
          int accentStrengthV = accent.strength.getValue();
          String accentHexV = null;
          if (accentOverrideEnabledV) {
            Color parsed = parseHexColorLenient(accent.hex.getText());
            if (parsed == null) {
              JOptionPane.showMessageDialog(
                  dialog,
                  "Accent color must be a hex value like #RRGGBB.",
                  "Invalid accent color",
                  JOptionPane.ERROR_MESSAGE);
              return;
            }
            accentHexV = toHex(parsed);
          }
          ThemeAccentSettings nextAccent = new ThemeAccentSettings(accentHexV, accentStrengthV);
          boolean accentChanged = !java.util.Objects.equals(prevAccent, nextAccent);

          ChatThemeSettings prevChatTheme =
              chatThemeSettingsBus != null
                  ? chatThemeSettingsBus.get()
                  : new ChatThemeSettings(
                      ChatThemeSettings.Preset.DEFAULT,
                      null,
                      null,
                      null,
                      35,
                      null,
                      null,
                      null,
                      null,
                      null);

          ChatThemeSettings.Preset presetV =
              (ChatThemeSettings.Preset) chatTheme.preset.getSelectedItem();
          if (presetV == null) presetV = ChatThemeSettings.Preset.DEFAULT;

          String tsHexV;
          String sysHexV;
          String menHexV;
          String msgHexV;
          String noticeHexV;
          String actionHexV;
          String errHexV;
          String presenceHexV;
          try {
            tsHexV =
                normalizeOptionalHexForApply(
                    chatTheme.timestamp.hex.getText(), "Chat timestamp color");
            sysHexV =
                normalizeOptionalHexForApply(chatTheme.system.hex.getText(), "Chat system color");
            menHexV =
                normalizeOptionalHexForApply(
                    chatTheme.mention.hex.getText(), "Mention highlight color");
            msgHexV =
                normalizeOptionalHexForApply(chatTheme.message.hex.getText(), "User message color");
            noticeHexV =
                normalizeOptionalHexForApply(
                    chatTheme.notice.hex.getText(), "Notice message color");
            actionHexV =
                normalizeOptionalHexForApply(
                    chatTheme.action.hex.getText(), "Action message color");
            errHexV =
                normalizeOptionalHexForApply(chatTheme.error.hex.getText(), "Error message color");
            presenceHexV =
                normalizeOptionalHexForApply(
                    chatTheme.presence.hex.getText(), "Presence message color");
          } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                dialog, ex.getMessage(), "Invalid chat message color", JOptionPane.ERROR_MESSAGE);
            return;
          }

          int mentionStrengthV = chatTheme.mentionStrength.getValue();
          ChatThemeSettings nextChatTheme =
              new ChatThemeSettings(
                  presetV,
                  tsHexV,
                  sysHexV,
                  menHexV,
                  mentionStrengthV,
                  msgHexV,
                  noticeHexV,
                  actionHexV,
                  errHexV,
                  presenceHexV);
          boolean chatThemeChanged = !java.util.Objects.equals(prevChatTheme, nextChatTheme);

          boolean autoConnectV = autoConnectOnStart.isSelected();

          boolean trayEnabledV = trayControls.enabled.isSelected();
          boolean trayCloseToTrayV = trayEnabledV && trayControls.closeToTray.isSelected();
          boolean trayMinimizeToTrayV = trayEnabledV && trayControls.minimizeToTray.isSelected();
          boolean trayStartMinimizedV = trayEnabledV && trayControls.startMinimized.isSelected();

          boolean trayNotifyHighlightsV =
              trayEnabledV && trayControls.notifyHighlights.isSelected();
          boolean trayNotifyPrivateMessagesV =
              trayEnabledV && trayControls.notifyPrivateMessages.isSelected();
          boolean trayNotifyConnectionStateV =
              trayEnabledV && trayControls.notifyConnectionState.isSelected();

          boolean trayNotifyOnlyWhenUnfocusedV =
              trayEnabledV && trayControls.notifyOnlyWhenUnfocused.isSelected();
          boolean trayNotifyOnlyWhenMinimizedOrHiddenV =
              trayEnabledV && trayControls.notifyOnlyWhenMinimizedOrHidden.isSelected();
          boolean trayNotifySuppressWhenTargetActiveV =
              trayEnabledV && trayControls.notifySuppressWhenTargetActive.isSelected();

          boolean trayLinuxDbusActionsEnabledV =
              trayEnabledV && trayControls.linuxDbusActions.isSelected();
          NotificationBackendMode trayNotificationBackendModeV =
              trayControls.notificationBackend.getSelectedItem()
                      instanceof NotificationBackendMode mode
                  ? mode
                  : NotificationBackendMode.AUTO;

          boolean trayNotificationSoundsEnabledV =
              trayEnabledV && trayControls.notificationSoundsEnabled.isSelected();
          boolean updateNotifierEnabledV = trayControls.updateNotifierEnabled.isSelected();
          boolean lagIndicatorEnabledV = trayControls.lagIndicatorEnabled.isSelected();
          BuiltInSound selectedSoundV =
              (BuiltInSound) trayControls.notificationSound.getSelectedItem();
          String trayNotificationSoundIdV =
              selectedSoundV != null ? selectedSoundV.name() : BuiltInSound.NOTIF_1.name();

          boolean trayNotificationSoundUseCustomV =
              trayControls.notificationSoundUseCustom.isSelected();
          String trayNotificationSoundCustomPathV =
              trayControls.notificationSoundCustomPath.getText();
          trayNotificationSoundCustomPathV =
              trayNotificationSoundCustomPathV != null
                  ? trayNotificationSoundCustomPathV.trim()
                  : "";
          if (trayNotificationSoundCustomPathV.isBlank()) trayNotificationSoundCustomPathV = null;
          if (trayNotificationSoundUseCustomV && trayNotificationSoundCustomPathV == null) {
            trayNotificationSoundUseCustomV = false;
          }

          boolean pushyEnabledV = trayControls.pushyEnabled.isSelected();
          String pushyEndpointV = Objects.toString(trayControls.pushyEndpoint.getText(), "").trim();
          String pushyApiKeyV = new String(trayControls.pushyApiKey.getPassword()).trim();
          PushyTargetMode pushyTargetModeV =
              trayControls.pushyTargetMode.getSelectedItem() instanceof PushyTargetMode mode
                  ? mode
                  : PushyTargetMode.DEVICE_TOKEN;
          String pushyTargetValueV =
              Objects.toString(trayControls.pushyTargetValue.getText(), "").trim();
          String pushyTitlePrefixV =
              Objects.toString(trayControls.pushyTitlePrefix.getText(), "").trim();
          int pushyConnectTimeoutSecondsV =
              ((Number) trayControls.pushyConnectTimeoutSeconds.getValue()).intValue();
          int pushyReadTimeoutSecondsV =
              ((Number) trayControls.pushyReadTimeoutSeconds.getValue()).intValue();

          String pushyValidationErrorV =
              validatePushyInputs(
                  pushyEnabledV, pushyEndpointV, pushyApiKeyV, pushyTargetModeV, pushyTargetValueV);
          if (pushyValidationErrorV != null) {
            JOptionPane.showMessageDialog(
                dialog, pushyValidationErrorV, "Invalid Pushy settings", JOptionPane.ERROR_MESSAGE);
            return;
          }

          String pushyDeviceTokenV =
              pushyTargetModeV == PushyTargetMode.DEVICE_TOKEN && !pushyTargetValueV.isBlank()
                  ? pushyTargetValueV
                  : null;
          String pushyTopicV =
              pushyTargetModeV == PushyTargetMode.TOPIC && !pushyTargetValueV.isBlank()
                  ? pushyTargetValueV
                  : null;

          PushyProperties pushyNext =
              new PushyProperties(
                  pushyEnabledV,
                  pushyEndpointV.isBlank() ? null : pushyEndpointV,
                  pushyApiKeyV.isBlank() ? null : pushyApiKeyV,
                  pushyDeviceTokenV,
                  pushyTopicV,
                  pushyTitlePrefixV.isBlank() ? null : pushyTitlePrefixV,
                  pushyConnectTimeoutSecondsV,
                  pushyReadTimeoutSecondsV);

          boolean timestampsEnabledV = timestamps.enabled.isSelected();
          boolean timestampsIncludeChatMessagesV = timestamps.includeChatMessages.isSelected();
          boolean timestampsIncludePresenceMessagesV =
              timestamps.includePresenceMessages.isSelected();
          String timestampFormatV =
              timestamps.format.getText() != null ? timestamps.format.getText().trim() : "";
          if (timestampFormatV.isBlank()) timestampFormatV = "HH:mm:ss";
          try {
            var unused = DateTimeFormatter.ofPattern(timestampFormatV);
          } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(
                dialog,
                "Invalid timestamp format: "
                    + timestampFormatV
                    + "\n\nUse a java.time DateTimeFormatter pattern (e.g. HH:mm:ss)",
                "Invalid timestamp format",
                javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
          }
          timestamps.format.setText(timestampFormatV);

          boolean presenceFoldsV = presenceFolds.isSelected();
          boolean ctcpRequestsInActiveTargetV = ctcpRequestsInActiveTarget.isSelected();
          String defaultQuitMessageV =
              Objects.toString(defaultQuitMessage.getText(), "")
                  .replace('\r', ' ')
                  .replace('\n', ' ')
                  .trim();
          if (defaultQuitMessageV.isEmpty()) {
            defaultQuitMessageV = RuntimeConfigStore.DEFAULT_QUIT_MESSAGE;
          }
          defaultQuitMessage.setText(defaultQuitMessageV);
          boolean spellcheckEnabledV = spellcheck.enabled.isSelected();
          boolean spellcheckUnderlineEnabledV = spellcheck.underlineEnabled.isSelected();
          boolean spellcheckSuggestOnTabEnabledV = spellcheck.suggestOnTabEnabled.isSelected();
          boolean spellcheckHoverSuggestionsEnabledV =
              spellcheck.hoverSuggestionsEnabled.isSelected();
          String spellcheckLanguageTagV =
              SpellcheckControlsSupport.languageTagValue(spellcheck.languageTag);
          List<String> spellcheckCustomDictionaryV =
              SpellcheckControlsSupport.parseCustomDictionary(
                  spellcheck.customDictionary.getText());
          String spellcheckCompletionPresetV =
              SpellcheckControlsSupport.completionPresetValue(spellcheck.completionPreset);
          int spellcheckCustomMinPrefixCompletionTokenLengthV =
              ((Number) spellcheck.customMinPrefixCompletionTokenLength.getValue()).intValue();
          int spellcheckCustomMaxPrefixCompletionExtraCharsV =
              ((Number) spellcheck.customMaxPrefixCompletionExtraChars.getValue()).intValue();
          int spellcheckCustomMaxPrefixLexiconCandidatesV =
              ((Number) spellcheck.customMaxPrefixLexiconCandidates.getValue()).intValue();
          int spellcheckCustomPrefixCompletionBonusScoreV =
              ((Number) spellcheck.customPrefixCompletionBonusScore.getValue()).intValue();
          int spellcheckCustomSourceOrderWeightV =
              ((Number) spellcheck.customSourceOrderWeight.getValue()).intValue();
          boolean ctcpAutoRepliesEnabledV = ctcpAutoReplies.enabled.isSelected();
          boolean ctcpAutoReplyVersionEnabledV = ctcpAutoReplies.version.isSelected();
          boolean ctcpAutoReplyPingEnabledV = ctcpAutoReplies.ping.isSelected();
          boolean ctcpAutoReplyTimeEnabledV = ctcpAutoReplies.time.isSelected();
          boolean typingIndicatorsSendEnabledV = typingIndicatorsSendEnabled.isSelected();
          boolean typingIndicatorsReceiveEnabledV = typingIndicatorsReceiveEnabled.isSelected();
          boolean typingIndicatorsTreeDisplayEnabledV =
              typingIndicatorsTreeDisplayEnabled.isSelected();
          boolean typingIndicatorsUsersListDisplayEnabledV =
              typingIndicatorsUsersListDisplayEnabled.isSelected();
          boolean typingIndicatorsTranscriptDisplayEnabledV =
              typingIndicatorsTranscriptDisplayEnabled.isSelected();
          boolean typingIndicatorsSendSignalDisplayEnabledV =
              typingIndicatorsSendSignalDisplayEnabled.isSelected();
          String typingIndicatorsTreeStyleV =
              typingTreeIndicatorStyleValue(typingTreeIndicatorStyle);
          String matrixUserListNameDisplayModeV =
              matrixUserListNameDisplayModeValue(matrixUserListNameDisplayMode);
          boolean serverTreeNotificationBadgesEnabledV =
              serverTreeNotificationBadgesEnabled.isSelected();
          int serverTreeUnreadBadgeScalePercentV =
              ((Number) serverTreeUnreadBadgeScalePercent.getValue()).intValue();
          if (serverTreeUnreadBadgeScalePercentV < 50) serverTreeUnreadBadgeScalePercentV = 50;
          if (serverTreeUnreadBadgeScalePercentV > 150) serverTreeUnreadBadgeScalePercentV = 150;
          Map<String, Boolean> ircv3CapabilitiesV = ircv3Capabilities.snapshot();

          boolean nickColoringEnabledV = nickColors.enabled.isSelected();
          double nickColorMinContrastV = ((Number) nickColors.minContrast.getValue()).doubleValue();
          if (nickColorMinContrastV <= 0) nickColorMinContrastV = 3.0;

          int maxImageW = ((Number) imageEmbeds.maxWidth.getValue()).intValue();
          int maxImageH = ((Number) imageEmbeds.maxHeight.getValue()).intValue();
          EmbedCardStyle embedCardStyleV =
              linkPreviews.cardStyle.getSelectedItem() instanceof EmbedCardStyle style
                  ? style
                  : EmbedCardStyle.DEFAULT;
          EmbedCardStyle prevEmbedCardStyle =
              embedCardStyleBus != null ? embedCardStyleBus.get() : EmbedCardStyle.DEFAULT;
          boolean embedCardStyleChanged =
              !java.util.Objects.equals(prevEmbedCardStyle, embedCardStyleV);

          int historyInitialLoadV = ((Number) history.initialLoadLines.getValue()).intValue();
          int historyPageSizeV = ((Number) history.pageSize.getValue()).intValue();
          int historyAutoLoadWheelDebounceMsV =
              ((Number) history.autoLoadWheelDebounceMs.getValue()).intValue();
          boolean historySmoothWheelScrollingEnabledV =
              history.smoothWheelScrollingEnabled.isSelected();
          int historyLoadOlderChunkSizeV =
              ((Number) history.loadOlderChunkSize.getValue()).intValue();
          int historyLoadOlderChunkDelayMsV =
              ((Number) history.loadOlderChunkDelayMs.getValue()).intValue();
          int historyLoadOlderChunkEdtBudgetMsV =
              ((Number) history.loadOlderChunkEdtBudgetMs.getValue()).intValue();
          boolean historyDeferRichTextDuringBatchV = history.deferRichTextDuringBatch.isSelected();
          boolean historyLockViewportDuringLoadOlderV =
              history.lockViewportDuringLoadOlder.isSelected();
          int historyRemoteRequestTimeoutSecondsV =
              ((Number) history.remoteRequestTimeoutSeconds.getValue()).intValue();
          int historyRemoteZncPlaybackTimeoutSecondsV =
              ((Number) history.remoteZncPlaybackTimeoutSeconds.getValue()).intValue();
          int historyRemoteZncPlaybackWindowMinutesV =
              ((Number) history.remoteZncPlaybackWindowMinutes.getValue()).intValue();
          int commandHistoryMaxSizeV =
              ((Number) history.commandHistoryMaxSize.getValue()).intValue();
          int chatTranscriptMaxLinesPerTargetV =
              ((Number) history.chatTranscriptMaxLinesPerTarget.getValue()).intValue();
          MemoryUsageDisplayMode memoryUsageDisplayModeV =
              memoryUsageDisplayMode.getSelectedItem() instanceof MemoryUsageDisplayMode mode
                  ? mode
                  : MemoryUsageDisplayMode.LONG;
          int memoryUsageRefreshIntervalMsV =
              ((Number) memoryUsageRefreshIntervalMs.getValue()).intValue();
          if (memoryUsageRefreshIntervalMsV < 250) memoryUsageRefreshIntervalMsV = 250;
          if (memoryUsageRefreshIntervalMsV > 60_000) memoryUsageRefreshIntervalMsV = 60_000;
          int memoryWarningNearMaxPercentV =
              ((Number) memoryWarnings.nearMaxPercent.getValue()).intValue();
          boolean memoryWarningTooltipEnabledV = memoryWarnings.tooltipEnabled.isSelected();
          boolean memoryWarningToastEnabledV = memoryWarnings.toastEnabled.isSelected();
          boolean memoryWarningPushyEnabledV = memoryWarnings.pushyEnabled.isSelected();
          boolean memoryWarningSoundEnabledV = memoryWarnings.soundEnabled.isSelected();
          String launchJavaCommandV =
              Objects.toString(launchJvm.javaCommand().getText(), "").trim();
          if (launchJavaCommandV.isBlank()) launchJavaCommandV = "java";
          int launchXmsMiBV = ((Number) launchJvm.xmsMiB().getValue()).intValue();
          int launchXmxMiBV = ((Number) launchJvm.xmxMiB().getValue()).intValue();
          if (launchXmsMiBV < 0) launchXmsMiBV = 0;
          if (launchXmxMiBV < 0) launchXmxMiBV = 0;
          if (launchXmsMiBV > 262_144) launchXmsMiBV = 262_144;
          if (launchXmxMiBV > 262_144) launchXmxMiBV = 262_144;
          if (launchXmxMiBV > 0 && launchXmsMiBV > 0 && launchXmxMiBV < launchXmsMiBV) {
            launchXmxMiBV = launchXmsMiBV;
          }
          String launchGcV =
              LaunchJvmControlsSupport.gcIdValue((LaunchGcOption) launchJvm.gc().getSelectedItem());
          List<String> launchArgsV = parseMultiLineArgs(launchJvm.extraArgs().getText());
          IrcProperties.Proxy proxyCfg;
          try {
            boolean proxyEnabledV = proxy.enabled.isSelected();
            String proxyHostV = proxy.host.getText() != null ? proxy.host.getText().trim() : "";
            int proxyPortV = ((Number) proxy.port.getValue()).intValue();
            String proxyUserV =
                proxy.username.getText() != null ? proxy.username.getText().trim() : "";
            String proxyPassV = new String(proxy.password.getPassword());
            boolean proxyRemoteDnsV = proxy.remoteDns.isSelected();

            int connectTimeoutSecondsV =
                ((Number) proxy.connectTimeoutSeconds.getValue()).intValue();
            int readTimeoutSecondsV = ((Number) proxy.readTimeoutSeconds.getValue()).intValue();

            if (proxyEnabledV) {
              if (proxyHostV.isBlank())
                throw new IllegalArgumentException("Proxy host is required when proxy is enabled.");
              if (proxyPortV <= 0 || proxyPortV > 65535)
                throw new IllegalArgumentException("Proxy port must be 1..65535.");
            }

            proxyCfg =
                new IrcProperties.Proxy(
                    proxyEnabledV,
                    proxyHostV,
                    proxyPortV,
                    proxyUserV,
                    proxyPassV,
                    proxyRemoteDnsV,
                    Math.max(1L, connectTimeoutSecondsV) * 1000L,
                    Math.max(1L, readTimeoutSecondsV) * 1000L);
          } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(
                dialog,
                "Invalid SOCKS proxy settings:\n\n" + ex.getMessage(),
                "Invalid proxy settings",
                javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
          }
          IrcProperties.Heartbeat heartbeatCfg;
          try {
            boolean hbEnabledV = heartbeat.enabled.isSelected();
            int hbCheckSecondsV = ((Number) heartbeat.checkPeriodSeconds.getValue()).intValue();
            int hbTimeoutSecondsV = ((Number) heartbeat.timeoutSeconds.getValue()).intValue();

            hbCheckSecondsV = Math.max(1, hbCheckSecondsV);
            hbTimeoutSecondsV = Math.max(1, hbTimeoutSecondsV);
            if (hbEnabledV && hbTimeoutSecondsV <= hbCheckSecondsV) {
              throw new IllegalArgumentException("Timeout must be greater than check period.");
            }

            heartbeatCfg =
                new IrcProperties.Heartbeat(
                    hbEnabledV, hbCheckSecondsV * 1000L, hbTimeoutSecondsV * 1000L);
          } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(
                dialog,
                "Invalid heartbeat settings:\n\n" + ex.getMessage(),
                "Invalid heartbeat settings",
                javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
          }

          boolean userhostEnabledV = userhost.enabled.isSelected();
          int userhostMinIntervalV = ((Number) userhost.minIntervalSeconds.getValue()).intValue();
          int userhostMaxPerMinuteV = ((Number) userhost.maxPerMinute.getValue()).intValue();
          int userhostNickCooldownV = ((Number) userhost.nickCooldownMinutes.getValue()).intValue();
          int userhostMaxNicksV = ((Number) userhost.maxNicksPerCommand.getValue()).intValue();

          boolean userInfoEnrichmentEnabledV = enrichment.enabled.isSelected();
          int uieUserhostMinIntervalV =
              ((Number) enrichment.userhostMinIntervalSeconds.getValue()).intValue();
          int uieUserhostMaxPerMinuteV =
              ((Number) enrichment.userhostMaxPerMinute.getValue()).intValue();
          int uieUserhostNickCooldownV =
              ((Number) enrichment.userhostNickCooldownMinutes.getValue()).intValue();
          int uieUserhostMaxNicksV =
              ((Number) enrichment.userhostMaxNicksPerCommand.getValue()).intValue();

          boolean uieWhoisFallbackEnabledRawV = enrichment.whoisFallbackEnabled.isSelected();
          int uieWhoisMinIntervalV =
              ((Number) enrichment.whoisMinIntervalSeconds.getValue()).intValue();
          int uieWhoisNickCooldownV =
              ((Number) enrichment.whoisNickCooldownMinutes.getValue()).intValue();

          boolean uiePeriodicRefreshEnabledRawV = enrichment.periodicRefreshEnabled.isSelected();
          int uiePeriodicRefreshIntervalV =
              ((Number) enrichment.periodicRefreshIntervalSeconds.getValue()).intValue();
          int uiePeriodicRefreshNicksPerTickV =
              ((Number) enrichment.periodicRefreshNicksPerTick.getValue()).intValue();
          boolean uieWhoisFallbackEnabledV =
              userInfoEnrichmentEnabledV && uieWhoisFallbackEnabledRawV;
          boolean uiePeriodicRefreshEnabledV =
              userInfoEnrichmentEnabledV && uiePeriodicRefreshEnabledRawV;
          int monitorIsonPollIntervalSecondsV =
              ((Number) monitorIsonPollIntervalSeconds.getValue()).intValue();
          if (monitorIsonPollIntervalSecondsV < 5) monitorIsonPollIntervalSecondsV = 5;
          if (monitorIsonPollIntervalSecondsV > 600) monitorIsonPollIntervalSecondsV = 600;
          boolean genericBouncerPreferLoginHintV = genericBouncerPreferLoginHint.isSelected();
          String genericBouncerLoginTemplateV =
              Objects.toString(genericBouncerLoginTemplate.getText(), "").trim();

          UiSettings prev = settingsBus.get();
          boolean outgoingColorEnabledV = outgoing.enabled.isSelected();
          String outgoingHexV =
              UiSettings.normalizeHexOrDefault(outgoing.hex.getText(), prev.clientLineColor());
          outgoing.hex.setText(outgoingHexV);
          boolean outgoingDeliveryIndicatorsEnabledV = outgoingDeliveryIndicators.isSelected();
          String serverTreeUnreadChannelColorV;
          String serverTreeHighlightChannelColorV;
          try {
            serverTreeUnreadChannelColorV =
                normalizeOptionalHexForApply(
                    appearanceServerTree.unreadChannelColor.hex.getText(),
                    "Unread channel color must be blank or a hex value like #RRGGBB.");
            serverTreeHighlightChannelColorV =
                normalizeOptionalHexForApply(
                    appearanceServerTree.highlightChannelColor.hex.getText(),
                    "Highlight channel color must be blank or a hex value like #RRGGBB.");
          } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                dialog, ex.getMessage(), "Invalid server tree color", JOptionPane.ERROR_MESSAGE);
            return;
          }
          boolean preserveDockLayoutBetweenSessionsV =
              appearanceServerTree.preserveDockLayoutBetweenSessions.isSelected();

          if (notifications.table.isEditing()) {
            try {
              notifications.table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          if (ircEventNotifications.table().isEditing()) {
            try {
              ircEventNotifications.table().getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          if (userCommands.table().isEditing()) {
            try {
              userCommands.table().getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }

          ValidationError notifErr = notifications.model.firstValidationError();
          if (notifErr != null) {
            refreshNotificationRuleValidation(notifications);
            JOptionPane.showMessageDialog(
                dialog,
                notifErr.formatForDialog(),
                "Invalid notification rule",
                JOptionPane.ERROR_MESSAGE);
            return;
          }

          UserCommandAliasValidationError aliasErr = userCommands.model().firstValidationError();
          if (aliasErr != null) {
            JOptionPane.showMessageDialog(
                dialog,
                aliasErr.formatForDialog(),
                "Invalid command alias",
                JOptionPane.ERROR_MESSAGE);
            return;
          }

          int notificationRuleCooldownSecondsV =
              ((Number) notifications.cooldownSeconds.getValue()).intValue();
          if (notificationRuleCooldownSecondsV < 0) notificationRuleCooldownSecondsV = 15;
          if (notificationRuleCooldownSecondsV > 3600) notificationRuleCooldownSecondsV = 3600;
          List<NotificationRule> notificationRulesV = notifications.model.snapshot();
          List<IrcEventNotificationRule> ircEventNotificationRulesV =
              ircEventNotifications.model().snapshot();
          List<UserCommandAlias> userCommandAliasesV = userCommands.model().snapshot();
          boolean unknownCommandAsRawEnabledV = userCommands.unknownCommandAsRaw().isSelected();
          boolean diagnosticsAssertjSwingEnabledV = diagnostics.assertjSwingEnabled().isSelected();
          boolean diagnosticsAssertjSwingFreezeWatchdogEnabledV =
              diagnostics.assertjSwingFreezeWatchdogEnabled().isSelected();
          int diagnosticsAssertjSwingFreezeThresholdMsV =
              ((Number) diagnostics.assertjSwingFreezeThresholdMs().getValue()).intValue();
          if (diagnosticsAssertjSwingFreezeThresholdMsV < 500)
            diagnosticsAssertjSwingFreezeThresholdMsV = 500;
          if (diagnosticsAssertjSwingFreezeThresholdMsV > 120_000)
            diagnosticsAssertjSwingFreezeThresholdMsV = 120_000;
          int diagnosticsAssertjSwingWatchdogPollMsV =
              ((Number) diagnostics.assertjSwingWatchdogPollMs().getValue()).intValue();
          if (diagnosticsAssertjSwingWatchdogPollMsV < 100)
            diagnosticsAssertjSwingWatchdogPollMsV = 100;
          if (diagnosticsAssertjSwingWatchdogPollMsV > 10_000)
            diagnosticsAssertjSwingWatchdogPollMsV = 10_000;
          int diagnosticsAssertjSwingFallbackViolationReportMsV =
              ((Number) diagnostics.assertjSwingFallbackViolationReportMs().getValue()).intValue();
          if (diagnosticsAssertjSwingFallbackViolationReportMsV < 250) {
            diagnosticsAssertjSwingFallbackViolationReportMsV = 250;
          }
          if (diagnosticsAssertjSwingFallbackViolationReportMsV > 120_000) {
            diagnosticsAssertjSwingFallbackViolationReportMsV = 120_000;
          }
          boolean diagnosticsAssertjSwingOnIssuePlaySoundV =
              diagnostics.assertjSwingOnIssuePlaySound().isSelected();
          boolean diagnosticsAssertjSwingOnIssueShowNotificationV =
              diagnostics.assertjSwingOnIssueShowNotification().isSelected();
          boolean diagnosticsJhiccupEnabledV = diagnostics.jhiccupEnabled().isSelected();
          String diagnosticsJhiccupJarPathV =
              Objects.toString(diagnostics.jhiccupJarPath().getText(), "").trim();
          String diagnosticsJhiccupJavaCommandRawV =
              Objects.toString(diagnostics.jhiccupJavaCommand().getText(), "").trim();
          String diagnosticsJhiccupJavaCommandEffectiveV =
              diagnosticsJhiccupJavaCommandRawV.isEmpty()
                  ? "java"
                  : diagnosticsJhiccupJavaCommandRawV;
          List<String> diagnosticsJhiccupArgsV =
              parseMultiLineArgs(diagnostics.jhiccupArgs().getText());

          boolean diagnosticsChangedV =
              runtimeConfig.readAppDiagnosticsAssertjSwingEnabled(true)
                      != diagnosticsAssertjSwingEnabledV
                  || runtimeConfig.readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(true)
                      != diagnosticsAssertjSwingFreezeWatchdogEnabledV
                  || runtimeConfig.readAppDiagnosticsAssertjSwingFreezeThresholdMs(2500)
                      != diagnosticsAssertjSwingFreezeThresholdMsV
                  || runtimeConfig.readAppDiagnosticsAssertjSwingWatchdogPollMs(500)
                      != diagnosticsAssertjSwingWatchdogPollMsV
                  || runtimeConfig.readAppDiagnosticsAssertjSwingFallbackViolationReportMs(5000)
                      != diagnosticsAssertjSwingFallbackViolationReportMsV
                  || runtimeConfig.readAppDiagnosticsAssertjSwingIssuePlaySound(false)
                      != diagnosticsAssertjSwingOnIssuePlaySoundV
                  || runtimeConfig.readAppDiagnosticsAssertjSwingIssueShowNotification(false)
                      != diagnosticsAssertjSwingOnIssueShowNotificationV
                  || runtimeConfig.readAppDiagnosticsJhiccupEnabled(false)
                      != diagnosticsJhiccupEnabledV
                  || !Objects.equals(
                      runtimeConfig.readAppDiagnosticsJhiccupJarPath(""),
                      diagnosticsJhiccupJarPathV)
                  || !Objects.equals(
                      runtimeConfig.readAppDiagnosticsJhiccupJavaCommand("java"),
                      diagnosticsJhiccupJavaCommandEffectiveV)
                  || !Objects.equals(
                      runtimeConfig.readAppDiagnosticsJhiccupArgs(List.of()),
                      diagnosticsJhiccupArgsV);

          UiSettings next =
              new UiSettings(
                  t,
                  fam,
                  size,
                  autoConnectV,
                  trayEnabledV,
                  trayCloseToTrayV,
                  trayMinimizeToTrayV,
                  trayStartMinimizedV,
                  trayNotifyHighlightsV,
                  trayNotifyPrivateMessagesV,
                  trayNotifyConnectionStateV,
                  trayNotifyOnlyWhenUnfocusedV,
                  trayNotifyOnlyWhenMinimizedOrHiddenV,
                  trayNotifySuppressWhenTargetActiveV,
                  trayLinuxDbusActionsEnabledV,
                  trayNotificationBackendModeV,
                  imageEmbeds.enabled.isSelected(),
                  imageEmbeds.collapsed.isSelected(),
                  maxImageW,
                  maxImageH,
                  imageEmbeds.animateGifs.isSelected(),
                  linkPreviews.enabled.isSelected(),
                  linkPreviews.collapsed.isSelected(),
                  presenceFoldsV,
                  ctcpRequestsInActiveTargetV,
                  typingIndicatorsSendEnabledV,
                  typingIndicatorsReceiveEnabledV,
                  typingIndicatorsTreeStyleV,
                  typingIndicatorsTreeDisplayEnabledV,
                  typingIndicatorsUsersListDisplayEnabledV,
                  typingIndicatorsTranscriptDisplayEnabledV,
                  typingIndicatorsSendSignalDisplayEnabledV,
                  timestampsEnabledV,
                  timestampFormatV,
                  timestampsIncludeChatMessagesV,
                  timestampsIncludePresenceMessagesV,
                  historyInitialLoadV,
                  historyPageSizeV,
                  historyAutoLoadWheelDebounceMsV,
                  historyLoadOlderChunkSizeV,
                  historyLoadOlderChunkDelayMsV,
                  historyLoadOlderChunkEdtBudgetMsV,
                  historyDeferRichTextDuringBatchV,
                  historyRemoteRequestTimeoutSecondsV,
                  historyRemoteZncPlaybackTimeoutSecondsV,
                  historyRemoteZncPlaybackWindowMinutesV,
                  commandHistoryMaxSizeV,
                  chatTranscriptMaxLinesPerTargetV,
                  outgoingColorEnabledV,
                  outgoingHexV,
                  outgoingDeliveryIndicatorsEnabledV,
                  serverTreeNotificationBadgesEnabledV,
                  userhostEnabledV,
                  userhostMinIntervalV,
                  userhostMaxPerMinuteV,
                  userhostNickCooldownV,
                  userhostMaxNicksV,
                  userInfoEnrichmentEnabledV,
                  uieUserhostMinIntervalV,
                  uieUserhostMaxPerMinuteV,
                  uieUserhostNickCooldownV,
                  uieUserhostMaxNicksV,
                  uieWhoisFallbackEnabledV,
                  uieWhoisMinIntervalV,
                  uieWhoisNickCooldownV,
                  uiePeriodicRefreshEnabledV,
                  uiePeriodicRefreshIntervalV,
                  uiePeriodicRefreshNicksPerTickV,
                  monitorIsonPollIntervalSecondsV,
                  notificationRuleCooldownSecondsV,
                  memoryUsageDisplayModeV,
                  memoryUsageRefreshIntervalMsV,
                  memoryWarningNearMaxPercentV,
                  memoryWarningTooltipEnabledV,
                  memoryWarningToastEnabledV,
                  memoryWarningPushyEnabledV,
                  memoryWarningSoundEnabledV,
                  notificationRulesV,
                  serverTreeUnreadChannelColorV,
                  serverTreeHighlightChannelColorV,
                  preserveDockLayoutBetweenSessionsV,
                  matrixUserListNameDisplayModeV);
          SpellcheckSettings nextSpellcheck =
              new SpellcheckSettings(
                  spellcheckEnabledV,
                  spellcheckUnderlineEnabledV,
                  spellcheckSuggestOnTabEnabledV,
                  spellcheckHoverSuggestionsEnabledV,
                  spellcheckLanguageTagV,
                  spellcheckCustomDictionaryV,
                  spellcheckCompletionPresetV,
                  spellcheckCustomMinPrefixCompletionTokenLengthV,
                  spellcheckCustomMaxPrefixCompletionExtraCharsV,
                  spellcheckCustomMaxPrefixLexiconCandidatesV,
                  spellcheckCustomPrefixCompletionBonusScoreV,
                  spellcheckCustomSourceOrderWeightV);

          boolean themeChanged = !next.theme().equalsIgnoreCase(prev.theme());

          runtimeConfig.rememberServerTreeUnreadBadgeScalePercent(
              serverTreeUnreadBadgeScalePercentV);
          runtimeConfig.rememberServerTreeNotificationBadgesEnabled(
              serverTreeNotificationBadgesEnabledV);
          runtimeConfig.rememberServerTreeUnreadChannelColor(serverTreeUnreadChannelColorV);
          runtimeConfig.rememberServerTreeHighlightChannelColor(serverTreeHighlightChannelColorV);
          runtimeConfig.rememberPreserveDockLayout(preserveDockLayoutBetweenSessionsV);
          settingsBus.set(next);
          settingsBus.setChatSmoothWheelScrollingEnabled(historySmoothWheelScrollingEnabledV);
          if (spellcheckSettingsBus != null) {
            spellcheckSettingsBus.set(nextSpellcheck);
          }

          if (accentSettingsBus != null) {
            accentSettingsBus.set(nextAccent);
          }
          runtimeConfig.beginMutationBatch();
          try {
            runtimeConfig.rememberAccentColor(nextAccent.accentColor());
            runtimeConfig.rememberAccentStrength(nextAccent.strength());

            if (tweakSettingsBus != null) {
              tweakSettingsBus.set(nextTweaks);
            }

            if (chatThemeSettingsBus != null && chatThemeChanged) {
              chatThemeSettingsBus.set(nextChatTheme);
            }
            runtimeConfig.rememberUiDensity(nextTweaks.densityId());
            runtimeConfig.rememberCornerRadius(nextTweaks.cornerRadius());
            runtimeConfig.rememberUiFontOverrideEnabled(nextTweaks.uiFontOverrideEnabled());
            runtimeConfig.rememberUiFontFamily(nextTweaks.uiFontFamily());
            runtimeConfig.rememberUiFontSize(nextTweaks.uiFontSize());

            runtimeConfig.rememberUiSettings(
                next.theme(), next.chatFontFamily(), next.chatFontSize());
            runtimeConfig.rememberMemoryUsageDisplayMode(next.memoryUsageDisplayMode().token());
            runtimeConfig.rememberMemoryUsageRefreshIntervalMs(next.memoryUsageRefreshIntervalMs());
            runtimeConfig.rememberMemoryUsageWarningNearMaxPercent(
                next.memoryUsageWarningNearMaxPercent());
            runtimeConfig.rememberMemoryUsageWarningTooltipEnabled(
                next.memoryUsageWarningTooltipEnabled());
            runtimeConfig.rememberMemoryUsageWarningToastEnabled(
                next.memoryUsageWarningToastEnabled());
            runtimeConfig.rememberMemoryUsageWarningPushyEnabled(
                next.memoryUsageWarningPushyEnabled());
            runtimeConfig.rememberMemoryUsageWarningSoundEnabled(
                next.memoryUsageWarningSoundEnabled());
            // Chat theme (transcript-only palette)
            runtimeConfig.rememberChatThemePreset(nextChatTheme.preset().name());
            runtimeConfig.rememberChatTimestampColor(nextChatTheme.timestampColor());
            runtimeConfig.rememberChatSystemColor(nextChatTheme.systemColor());
            runtimeConfig.rememberChatMessageColor(nextChatTheme.messageColor());
            runtimeConfig.rememberChatNoticeColor(nextChatTheme.noticeColor());
            runtimeConfig.rememberChatActionColor(nextChatTheme.actionColor());
            runtimeConfig.rememberChatErrorColor(nextChatTheme.errorColor());
            runtimeConfig.rememberChatPresenceColor(nextChatTheme.presenceColor());
            runtimeConfig.rememberChatMentionBgColor(nextChatTheme.mentionBgColor());
            runtimeConfig.rememberChatMentionStrength(nextChatTheme.mentionStrength());
            runtimeConfig.rememberAutoConnectOnStart(next.autoConnectOnStart());
            runtimeConfig.rememberLaunchJvmJavaCommand(launchJavaCommandV);
            runtimeConfig.rememberLaunchJvmXmsMiB(launchXmsMiBV);
            runtimeConfig.rememberLaunchJvmXmxMiB(launchXmxMiBV);
            runtimeConfig.rememberLaunchJvmGc(launchGcV);
            runtimeConfig.rememberLaunchJvmArgs(launchArgsV);
            runtimeConfig.rememberTrayEnabled(next.trayEnabled());
            runtimeConfig.rememberTrayCloseToTray(next.trayCloseToTray());
            runtimeConfig.rememberTrayMinimizeToTray(next.trayMinimizeToTray());
            runtimeConfig.rememberTrayStartMinimized(next.trayStartMinimized());
            runtimeConfig.rememberTrayNotifyHighlights(next.trayNotifyHighlights());
            runtimeConfig.rememberTrayNotifyPrivateMessages(next.trayNotifyPrivateMessages());
            runtimeConfig.rememberTrayNotifyConnectionState(next.trayNotifyConnectionState());
            runtimeConfig.rememberTrayNotifyOnlyWhenUnfocused(next.trayNotifyOnlyWhenUnfocused());
            runtimeConfig.rememberTrayNotifyOnlyWhenMinimizedOrHidden(
                next.trayNotifyOnlyWhenMinimizedOrHidden());
            runtimeConfig.rememberTrayNotifySuppressWhenTargetActive(
                next.trayNotifySuppressWhenTargetActive());
            runtimeConfig.rememberTrayLinuxDbusActionsEnabled(next.trayLinuxDbusActionsEnabled());
            runtimeConfig.rememberTrayNotificationBackend(
                next.trayNotificationBackendMode().token());

            if (notificationSoundSettingsBus != null) {
              notificationSoundSettingsBus.set(
                  new NotificationSoundSettings(
                      trayNotificationSoundsEnabledV,
                      trayNotificationSoundIdV,
                      trayNotificationSoundUseCustomV,
                      trayNotificationSoundCustomPathV));
            }
            runtimeConfig.rememberTrayNotificationSoundsEnabled(trayNotificationSoundsEnabledV);
            runtimeConfig.rememberTrayNotificationSound(trayNotificationSoundIdV);
            runtimeConfig.rememberTrayNotificationSoundUseCustom(trayNotificationSoundUseCustomV);
            runtimeConfig.rememberTrayNotificationSoundCustomPath(trayNotificationSoundCustomPathV);
            runtimeConfig.rememberUpdateNotifierEnabled(updateNotifierEnabledV);
            runtimeConfig.rememberLagIndicatorEnabled(lagIndicatorEnabledV);
            if (updateNotifierService != null) {
              updateNotifierService.setEnabled(updateNotifierEnabledV);
            }
            if (lagIndicatorService != null) {
              lagIndicatorService.setEnabled(lagIndicatorEnabledV);
            }
            if (pushySettingsBus != null) {
              pushySettingsBus.set(pushyNext);
            }
            runtimeConfig.rememberPushySettings(pushyNext);

            if (trayService != null) {
              trayService.applySettings();
            }
            runtimeConfig.rememberImageEmbedsEnabled(next.imageEmbedsEnabled());
            runtimeConfig.rememberImageEmbedsCollapsedByDefault(
                next.imageEmbedsCollapsedByDefault());
            runtimeConfig.rememberImageEmbedsMaxWidthPx(next.imageEmbedsMaxWidthPx());
            runtimeConfig.rememberImageEmbedsMaxHeightPx(next.imageEmbedsMaxHeightPx());
            runtimeConfig.rememberImageEmbedsAnimateGifs(next.imageEmbedsAnimateGifs());
            runtimeConfig.rememberEmbedCardStyle(embedCardStyleV.token());
            if (embedCardStyleBus != null) {
              embedCardStyleBus.set(embedCardStyleV);
            }
            runtimeConfig.rememberLinkPreviewsEnabled(next.linkPreviewsEnabled());
            runtimeConfig.rememberLinkPreviewsCollapsedByDefault(
                next.linkPreviewsCollapsedByDefault());
            EmbedLoadPolicySnapshot embedPolicyV =
                pendingEmbedLoadPolicy.get() != null
                    ? pendingEmbedLoadPolicy.get()
                    : EmbedLoadPolicySnapshot.defaults();
            runtimeConfig.rememberEmbedLoadPolicy(embedPolicyV);
            if (embedLoadPolicyBus != null) {
              embedLoadPolicyBus.set(embedPolicyV);
            }
            runtimeConfig.rememberPresenceFoldsEnabled(next.presenceFoldsEnabled());
            runtimeConfig.rememberCtcpRequestsInActiveTargetEnabled(
                next.ctcpRequestsInActiveTargetEnabled());
            runtimeConfig.rememberDefaultQuitMessage(defaultQuitMessageV);
            runtimeConfig.rememberCtcpAutoRepliesEnabled(ctcpAutoRepliesEnabledV);
            runtimeConfig.rememberCtcpAutoReplyVersionEnabled(ctcpAutoReplyVersionEnabledV);
            runtimeConfig.rememberCtcpAutoReplyPingEnabled(ctcpAutoReplyPingEnabledV);
            runtimeConfig.rememberCtcpAutoReplyTimeEnabled(ctcpAutoReplyTimeEnabledV);
            runtimeConfig.rememberTypingIndicatorsEnabled(next.typingIndicatorsEnabled());
            runtimeConfig.rememberTypingIndicatorsReceiveEnabled(
                next.typingIndicatorsReceiveEnabled());
            runtimeConfig.rememberTypingTreeIndicatorStyle(next.typingIndicatorsTreeStyle());
            runtimeConfig.rememberTypingIndicatorsTreeEnabled(next.typingIndicatorsTreeEnabled());
            runtimeConfig.rememberTypingIndicatorsUsersListEnabled(
                next.typingIndicatorsUsersListEnabled());
            runtimeConfig.rememberMatrixUserListNameDisplayMode(
                next.matrixUserListNameDisplayMode());
            runtimeConfig.rememberTypingIndicatorsTranscriptEnabled(
                next.typingIndicatorsTranscriptEnabled());
            runtimeConfig.rememberTypingIndicatorsSendSignalEnabled(
                next.typingIndicatorsSendSignalEnabled());
            runtimeConfig.rememberSpellcheckEnabled(nextSpellcheck.enabled());
            runtimeConfig.rememberSpellcheckUnderlineEnabled(nextSpellcheck.underlineEnabled());
            runtimeConfig.rememberSpellcheckSuggestOnTabEnabled(
                nextSpellcheck.suggestOnTabEnabled());
            runtimeConfig.rememberSpellcheckHoverSuggestionsEnabled(
                nextSpellcheck.hoverSuggestionsEnabled());
            runtimeConfig.rememberSpellcheckLanguageTag(nextSpellcheck.languageTag());
            runtimeConfig.rememberSpellcheckCustomDictionary(nextSpellcheck.customDictionary());
            runtimeConfig.rememberSpellcheckCompletionPreset(nextSpellcheck.completionPreset());
            runtimeConfig.rememberSpellcheckCustomMinPrefixCompletionTokenLength(
                nextSpellcheck.customMinPrefixCompletionTokenLength());
            runtimeConfig.rememberSpellcheckCustomMaxPrefixCompletionExtraChars(
                nextSpellcheck.customMaxPrefixCompletionExtraChars());
            runtimeConfig.rememberSpellcheckCustomMaxPrefixLexiconCandidates(
                nextSpellcheck.customMaxPrefixLexiconCandidates());
            runtimeConfig.rememberSpellcheckCustomPrefixCompletionBonusScore(
                nextSpellcheck.customPrefixCompletionBonusScore());
            runtimeConfig.rememberSpellcheckCustomSourceOrderWeight(
                nextSpellcheck.customSourceOrderWeight());
            Ircv3PanelSupport.persistCapabilities(runtimeConfig, ircv3CapabilitiesV);

            if (nickColorSettingsBus != null) {
              nickColorSettingsBus.set(
                  new NickColorSettings(nickColoringEnabledV, nickColorMinContrastV));
            }
            runtimeConfig.rememberNickColoringEnabled(nickColoringEnabledV);
            runtimeConfig.rememberNickColorMinContrast(nickColorMinContrastV);
            runtimeConfig.rememberTimestampsEnabled(next.timestampsEnabled());
            runtimeConfig.rememberTimestampFormat(next.timestampFormat());
            runtimeConfig.rememberTimestampsIncludeChatMessages(
                next.timestampsIncludeChatMessages());
            runtimeConfig.rememberTimestampsIncludePresenceMessages(
                next.timestampsIncludePresenceMessages());

            runtimeConfig.rememberChatHistoryInitialLoadLines(next.chatHistoryInitialLoadLines());
            runtimeConfig.rememberChatHistoryPageSize(next.chatHistoryPageSize());
            runtimeConfig.rememberChatHistoryAutoLoadWheelDebounceMs(
                next.chatHistoryAutoLoadWheelDebounceMs());
            runtimeConfig.rememberChatSmoothWheelScrollingEnabled(
                historySmoothWheelScrollingEnabledV);
            runtimeConfig.rememberChatHistoryLoadOlderChunkSize(
                next.chatHistoryLoadOlderChunkSize());
            runtimeConfig.rememberChatHistoryLoadOlderChunkDelayMs(
                next.chatHistoryLoadOlderChunkDelayMs());
            runtimeConfig.rememberChatHistoryLoadOlderChunkEdtBudgetMs(
                next.chatHistoryLoadOlderChunkEdtBudgetMs());
            runtimeConfig.rememberChatHistoryDeferRichTextDuringBatch(
                next.chatHistoryDeferRichTextDuringBatch());
            runtimeConfig.rememberChatHistoryLockViewportDuringLoadOlder(
                historyLockViewportDuringLoadOlderV);
            runtimeConfig.rememberChatHistoryRemoteRequestTimeoutSeconds(
                next.chatHistoryRemoteRequestTimeoutSeconds());
            runtimeConfig.rememberChatHistoryRemoteZncPlaybackTimeoutSeconds(
                next.chatHistoryRemoteZncPlaybackTimeoutSeconds());
            runtimeConfig.rememberChatHistoryRemoteZncPlaybackWindowMinutes(
                next.chatHistoryRemoteZncPlaybackWindowMinutes());
            runtimeConfig.rememberCommandHistoryMaxSize(next.commandHistoryMaxSize());
            runtimeConfig.rememberChatTranscriptMaxLinesPerTarget(
                next.chatTranscriptMaxLinesPerTarget());

            applyFilterSettingsFromUi(filters);
            runtimeConfig.rememberChatLoggingEnabled(logging.enabled.isSelected());
            runtimeConfig.rememberChatLoggingLogSoftIgnoredLines(
                logging.logSoftIgnored.isSelected());
            runtimeConfig.rememberChatLoggingLogPrivateMessages(
                logging.logPrivateMessages.isSelected());
            runtimeConfig.rememberChatLoggingSavePrivateMessageList(
                logging.savePrivateMessageList.isSelected());
            runtimeConfig.rememberChatLoggingDbFileBaseName(logging.dbBaseName.getText());
            runtimeConfig.rememberChatLoggingDbNextToRuntimeConfig(
                logging.dbNextToConfig.isSelected());

            runtimeConfig.rememberChatLoggingKeepForever(logging.keepForever.isSelected());
            runtimeConfig.rememberChatLoggingRetentionDays(
                ((Number) logging.retentionDays.getValue()).intValue());
            runtimeConfig.rememberChatLoggingWriterQueueMax(
                ((Number) logging.writerQueueMax.getValue()).intValue());
            runtimeConfig.rememberChatLoggingWriterBatchSize(
                ((Number) logging.writerBatchSize.getValue()).intValue());

            runtimeConfig.rememberClientLineColorEnabled(next.clientLineColorEnabled());
            runtimeConfig.rememberClientLineColor(next.clientLineColor());
            runtimeConfig.rememberOutgoingDeliveryIndicatorsEnabled(
                next.outgoingDeliveryIndicatorsEnabled());

            runtimeConfig.rememberNotificationRuleCooldownSeconds(
                next.notificationRuleCooldownSeconds());
            runtimeConfig.rememberNotificationRules(notificationRulesV);
            runtimeConfig.rememberIrcEventNotificationRules(ircEventNotificationRulesV);
            if (ircEventNotificationRulesBus != null) {
              ircEventNotificationRulesBus.set(ircEventNotificationRulesV);
            }
            runtimeConfig.rememberUserCommandAliases(userCommandAliasesV);
            runtimeConfig.rememberUnknownCommandAsRawEnabled(unknownCommandAsRawEnabledV);
            if (userCommandAliasesBus != null) {
              userCommandAliasesBus.set(userCommandAliasesV);
              userCommandAliasesBus.setUnknownCommandAsRawEnabled(unknownCommandAsRawEnabledV);
            }
            runtimeConfig.rememberAppDiagnosticsAssertjSwingEnabled(
                diagnosticsAssertjSwingEnabledV);
            runtimeConfig.rememberAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(
                diagnosticsAssertjSwingFreezeWatchdogEnabledV);
            runtimeConfig.rememberAppDiagnosticsAssertjSwingFreezeThresholdMs(
                diagnosticsAssertjSwingFreezeThresholdMsV);
            runtimeConfig.rememberAppDiagnosticsAssertjSwingWatchdogPollMs(
                diagnosticsAssertjSwingWatchdogPollMsV);
            runtimeConfig.rememberAppDiagnosticsAssertjSwingFallbackViolationReportMs(
                diagnosticsAssertjSwingFallbackViolationReportMsV);
            runtimeConfig.rememberAppDiagnosticsAssertjSwingIssuePlaySound(
                diagnosticsAssertjSwingOnIssuePlaySoundV);
            runtimeConfig.rememberAppDiagnosticsAssertjSwingIssueShowNotification(
                diagnosticsAssertjSwingOnIssueShowNotificationV);
            runtimeConfig.rememberAppDiagnosticsJhiccupEnabled(diagnosticsJhiccupEnabledV);
            runtimeConfig.rememberAppDiagnosticsJhiccupJarPath(diagnosticsJhiccupJarPathV);
            runtimeConfig.rememberAppDiagnosticsJhiccupJavaCommand(
                diagnosticsJhiccupJavaCommandRawV);
            runtimeConfig.rememberAppDiagnosticsJhiccupArgs(diagnosticsJhiccupArgsV);
            if (diagnosticsChangedV) {
              JOptionPane.showMessageDialog(
                  dialog,
                  "Diagnostics settings were saved.\nRestart IRCafe to apply AssertJ Swing / jHiccup startup changes.",
                  "Restart required",
                  JOptionPane.INFORMATION_MESSAGE);
            }

            runtimeConfig.rememberUserhostDiscoveryEnabled(next.userhostDiscoveryEnabled());
            runtimeConfig.rememberUserhostMinIntervalSeconds(next.userhostMinIntervalSeconds());
            runtimeConfig.rememberUserhostMaxCommandsPerMinute(next.userhostMaxCommandsPerMinute());
            runtimeConfig.rememberUserhostNickCooldownMinutes(next.userhostNickCooldownMinutes());
            runtimeConfig.rememberUserhostMaxNicksPerCommand(next.userhostMaxNicksPerCommand());
            runtimeConfig.rememberUserInfoEnrichmentEnabled(next.userInfoEnrichmentEnabled());
            runtimeConfig.rememberUserInfoEnrichmentWhoisFallbackEnabled(
                next.userInfoEnrichmentWhoisFallbackEnabled());

            runtimeConfig.rememberUserInfoEnrichmentUserhostMinIntervalSeconds(
                next.userInfoEnrichmentUserhostMinIntervalSeconds());
            runtimeConfig.rememberUserInfoEnrichmentUserhostMaxCommandsPerMinute(
                next.userInfoEnrichmentUserhostMaxCommandsPerMinute());
            runtimeConfig.rememberUserInfoEnrichmentUserhostNickCooldownMinutes(
                next.userInfoEnrichmentUserhostNickCooldownMinutes());
            runtimeConfig.rememberUserInfoEnrichmentUserhostMaxNicksPerCommand(
                next.userInfoEnrichmentUserhostMaxNicksPerCommand());

            runtimeConfig.rememberUserInfoEnrichmentWhoisMinIntervalSeconds(
                next.userInfoEnrichmentWhoisMinIntervalSeconds());
            runtimeConfig.rememberUserInfoEnrichmentWhoisNickCooldownMinutes(
                next.userInfoEnrichmentWhoisNickCooldownMinutes());

            runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshEnabled(
                next.userInfoEnrichmentPeriodicRefreshEnabled());
            runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshIntervalSeconds(
                next.userInfoEnrichmentPeriodicRefreshIntervalSeconds());
            runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshNicksPerTick(
                next.userInfoEnrichmentPeriodicRefreshNicksPerTick());
            runtimeConfig.rememberMonitorIsonPollIntervalSeconds(
                next.monitorIsonFallbackPollIntervalSeconds());
            runtimeConfig.rememberGenericBouncerPreferLoginHint(genericBouncerPreferLoginHintV);
            runtimeConfig.rememberGenericBouncerLoginTemplate(genericBouncerLoginTemplateV);
            runtimeConfig.rememberClientProxy(proxyCfg);
            NetProxyContext.configure(proxyCfg);
            runtimeConfig.rememberClientHeartbeat(heartbeatCfg);
            NetHeartbeatContext.configure(heartbeatCfg);
            if (ircHeartbeatMaintenancePort != null) {
              ircHeartbeatMaintenancePort.rescheduleActiveHeartbeats();
            }
            boolean trustAllTlsV = trustAllTlsCertificates.isSelected();
            runtimeConfig.rememberClientTlsTrustAllCertificates(trustAllTlsV);
            NetTlsContext.configure(trustAllTlsV);
          } finally {
            runtimeConfig.endMutationBatch();
          }

          if (themeManager != null) {
            if (themeChanged || accentChanged || tweaksChanged) {
              // Full UI refresh (also triggers a chat restyle)
              themeManager.applyTheme(next.theme());
            } else if (chatThemeChanged) {
              // Only the transcript palette changed
              themeManager.refreshChatStyles();
            }
          }
          if (embedCardStyleChanged) {
            try {
              TargetRef active = targetCoordinator.getActiveTarget();
              if (active != null) transcriptRebuildService.rebuild(active);
            } catch (Exception ignored) {
              // best-effort
            }
          }

          // Update committed snapshot for live preview rollback (Cancel/window close).
          committedThemeId.set(normalizeThemeIdInternal(next.theme()));
          committedUiSettings.set(next);
          committedAccentSettings.set(nextAccent);
          committedTweakSettings.set(nextTweaks);
          committedChatThemeSettings.set(nextChatTheme);
          lastValidAccentHex.set(nextAccent.accentColor());
          lastValidChatTimestampHex.set(nextChatTheme.timestampColor());
          lastValidChatSystemHex.set(nextChatTheme.systemColor());
          lastValidChatMentionHex.set(nextChatTheme.mentionBgColor());
          lastValidChatMessageHex.set(nextChatTheme.messageColor());
          lastValidChatNoticeHex.set(nextChatTheme.noticeColor());
          lastValidChatActionHex.set(nextChatTheme.actionColor());
          lastValidChatErrorHex.set(nextChatTheme.errorColor());
          lastValidChatPresenceHex.set(nextChatTheme.presenceColor());
        };

    apply.addActionListener(e -> doApply.run());
    final JDialog d = createDialog(owner);
    this.dialog = d;
    final AtomicBoolean rollbackOnClose = new AtomicBoolean(true);
    final AtomicBoolean rollbackScheduled = new AtomicBoolean(false);
    d.addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosed(java.awt.event.WindowEvent e) {
            if (!rollbackOnClose.get()) return;
            if (!rollbackScheduled.compareAndSet(false, true)) return;
            SwingUtilities.invokeLater(
                () -> {
                  if (rollbackOnClose.get()) restoreCommittedAppearance.run();
                });
          }
        });

    final CloseableScope scope = DialogCloseableScopeDecorator.install(d);
    closeables.forEach(scope::add);
    scope.addCleanup(
        () -> {
          if (this.dialog == d) this.dialog = null;
        });

    ok.addActionListener(
        e -> {
          doApply.run();
          rollbackOnClose.set(false);
          d.dispose();
        });
    cancel.addActionListener(
        e -> {
          d.dispose();
        });

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
    buttons.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
    buttons.add(apply);
    buttons.add(ok);
    buttons.add(cancel);
    JTabbedPane tabs = new DynamicTabbedPane();

    tabs.addTab("Appearance", wrapTab(appearancePanel));
    tabs.addTab("Memory", wrapTab(memoryPanel));
    tabs.addTab("Startup", wrapTab(startupPanel));
    tabs.addTab("Tray & Notifications", wrapTab(trayPanel));
    tabs.addTab("Chat", wrapTab(chatPanel));
    tabs.addTab("CTCP Replies", wrapTab(ctcpRepliesPanel));
    tabs.addTab("IRCv3", wrapTab(ircv3Panel));
    tabs.addTab("Embeds & Previews", wrapTab(embedsPanel));
    tabs.addTab("History & Storage", wrapTab(historyStoragePanel));
    tabs.addTab("Notifications", wrapTab(notificationsPanel));
    tabs.addTab("Commands", wrapTab(commandsPanel));
    tabs.addTab("Diagnostics", wrapTab(diagnosticsPanel));
    tabs.addTab("Filters", wrapTab(filtersPanel));
    tabs.addTab("Network", wrapTab(networkPanel));
    tabs.addTab("User lookups", wrapTab(userLookupsPanel));

    d.setLayout(new BorderLayout());
    d.add(tabs, BorderLayout.CENTER);
    d.add(buttons, BorderLayout.SOUTH);
    // A tiny minimum size makes "dynamic tab sizing" feel jarring (some tabs would shrink the whole
    // dialog
    // to near-nothing). Keep a comfortable baseline so tabs like Network don't open comically
    // short.
    d.setMinimumSize(new Dimension(680, 540));
    installDynamicTabSizing(d, tabs, owner);
    d.setLocationRelativeTo(owner);
    d.setVisible(true);
  }

  private static void installDynamicTabSizing(JDialog d, JTabbedPane tabs, Window owner) {
    ChangeListener listener =
        e -> {
          packClampAndKeepCenter(d, owner);
          // Some tabs with nested panels/subtabs report final preferred sizes only after
          // the first layout pass on selection.
          SwingUtilities.invokeLater(
              () -> {
                if (!d.isDisplayable()) return;
                packClampAndKeepCenter(d, owner);
              });
        };
    tabs.addChangeListener(listener);
    packClampAndKeepCenter(d, owner);
    // Run one more pass after the dialog is realized so viewport measurements are final.
    SwingUtilities.invokeLater(
        () -> {
          if (!d.isDisplayable()) return;
          packClampAndKeepCenter(d, owner);
        });
  }

  private static void packClampAndKeepCenter(JDialog d, Window owner) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> packClampAndKeepCenter(d, owner));
      return;
    }

    Point center = new Point(d.getX() + d.getWidth() / 2, d.getY() + d.getHeight() / 2);
    d.pack();
    d.validate();

    Rectangle usable = usableBounds(owner, d);
    int margin = 32;
    int maxW = Math.max(usable.width - margin, d.getMinimumSize().width);
    int maxH = Math.max(usable.height - margin, d.getMinimumSize().height);

    // First clamp to usable bounds + minimum size.
    Dimension size = d.getSize();
    int w = Math.max(d.getMinimumSize().width, Math.min(size.width, maxW));
    int h = Math.max(d.getMinimumSize().height, Math.min(size.height, maxH));
    if (w != size.width || h != size.height) {
      d.setSize(w, h);
      d.validate();
    }

    // Some tabs (especially those wrapped in JScrollPane and/or containing wrapped help text)
    // can slightly under-report their preferred height, resulting in an unnecessary vertical
    // scrollbar. Nudge after the clamp so the viewport width/word-wrapping is final.
    nudgeToAvoidUnnecessaryVerticalScroll(d, maxH);

    // Re-clamp in case the nudge bumped us over the usable bounds.
    size = d.getSize();
    w = Math.max(d.getMinimumSize().width, Math.min(size.width, maxW));
    h = Math.max(d.getMinimumSize().height, Math.min(size.height, maxH));
    if (w != size.width || h != size.height) {
      d.setSize(w, h);
      d.validate();
    }

    // Keep the dialog centered as the user switches tabs.
    int nx = center.x - d.getWidth() / 2;
    int ny = center.y - d.getHeight() / 2;
    nx = Math.max(usable.x, Math.min(nx, usable.x + usable.width - d.getWidth()));
    ny = Math.max(usable.y, Math.min(ny, usable.y + usable.height - d.getHeight()));
    d.setLocation(nx, ny);
  }

  private static void nudgeToAvoidUnnecessaryVerticalScroll(JDialog d, int maxDialogHeight) {
    if (d == null || !d.isShowing()) return;
    java.awt.Container root = d.getContentPane();
    if (root == null) return;

    JTabbedPane tabs = null;
    for (java.awt.Component c : root.getComponents()) {
      if (c instanceof JTabbedPane t) {
        tabs = t;
        break;
      }
    }
    if (tabs == null) return;

    java.awt.Component selected = tabs.getSelectedComponent();
    if (!(selected instanceof JScrollPane sp)) return;

    java.awt.Component view = sp.getViewport() != null ? sp.getViewport().getView() : null;
    if (view == null) return;

    // Force layout so viewport sizes are current.
    sp.doLayout();
    if (sp.getViewport() != null) sp.getViewport().doLayout();
    view.doLayout();

    Dimension viewPref = view.getPreferredSize();
    Dimension extent = sp.getViewport() != null ? sp.getViewport().getExtentSize() : null;
    if (viewPref == null || extent == null) return;

    int missing = viewPref.height - extent.height;
    if (missing <= 0) return;

    // Only nudge if the missing amount is small-ish (we're fixing "almost fits" cases).
    // If the view is truly huge, we keep the scroll.
    if (missing > 220) return;

    Dimension dialogSize = d.getSize();
    int targetH = Math.min(maxDialogHeight, dialogSize.height + missing);
    if (targetH > dialogSize.height) {
      d.setSize(dialogSize.width, targetH);
      d.validate();
    }
  }

  private static Rectangle usableBounds(Window owner, Window fallback) {
    try {
      var gc =
          owner != null ? owner.getGraphicsConfiguration() : fallback.getGraphicsConfiguration();
      if (gc == null)
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

      Rectangle b = gc.getBounds();
      Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
      return new Rectangle(
          b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    } catch (Exception ignored) {
      return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    }
  }

  private Map<String, String> buildThemeLabelById() {
    Map<String, String> themeLabelById = new LinkedHashMap<>();
    for (ThemeManager.ThemeOption opt : themeManager.supportedThemes()) {
      themeLabelById.put(opt.id(), opt.label());
    }
    return themeLabelById;
  }

  /**
   * Wrap a settings tab inside a scroll pane.
   *
   * <p>Important: we use a Scrollable wrapper that tracks the viewport width. Without this, when
   * the dialog is resized larger and then smaller again, Swing can keep the tab view at the larger
   * width (no horizontal scrollbar), making controls appear to "stick" expanded instead of
   * shrinking.
   */
  private static JScrollPane wrapTab(JPanel panel) {
    ScrollableViewportWidthPanel wrapper = new ScrollableViewportWidthPanel(new BorderLayout());
    wrapper.add(panel, BorderLayout.NORTH);

    JScrollPane scroll =
        new JScrollPane(
            wrapper,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(null);
    scroll.setViewportBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    return scroll;
  }

  static JPanel padSubTab(JComponent panel) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    wrapper.add(panel, BorderLayout.NORTH);
    return wrapper;
  }

  /**
   * A lightweight view wrapper for JScrollPane that always tracks viewport width. This prevents
   * "expanded" components from not shrinking back down when the parent dialog is resized smaller.
   */
  private static final class ScrollableViewportWidthPanel extends JPanel implements Scrollable {
    private ScrollableViewportWidthPanel(LayoutManager layout) {
      super(layout);
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension d = super.getMinimumSize();
      return new Dimension(0, d != null ? d.height : 0);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      if (orientation == SwingConstants.VERTICAL) {
        return Math.max(32, visibleRect.height - 32);
      }
      return Math.max(32, visibleRect.width - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  static JLabel tabTitle(String text) {
    JLabel l = new JLabel(text);
    l.putClientProperty(FlatClientProperties.STYLE, "font:+4");
    Font f = l.getFont();
    if (f != null) {
      l.setFont(f.deriveFont(Font.BOLD, f.getSize2D() + 4f));
    }
    l.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    return l;
  }

  static JLabel sectionTitle(String text) {
    JLabel l = new JLabel(text);
    l.putClientProperty(FlatClientProperties.STYLE, "font:+2");
    Font f = l.getFont();
    if (f != null) {
      l.setFont(f.deriveFont(Font.BOLD));
    }
    l.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
    return l;
  }

  static JPanel captionPanel(String title, String layout, String columns, String rows) {
    return captionPanelWithPadding(title, layout, columns, rows, 6, 8, 8, 8);
  }

  static JPanel captionPanelWithPadding(
      String title,
      String layout,
      String columns,
      String rows,
      int top,
      int left,
      int bottom,
      int right) {
    JPanel panel = new JPanel(new MigLayout(layout, columns, rows));
    panel.setOpaque(false);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title),
            BorderFactory.createEmptyBorder(top, left, bottom, right)));
    return panel;
  }

  static JTextArea helpText(String text) {
    JTextArea t = new JTextArea(text);
    t.setEditable(false);
    t.setLineWrap(true);
    t.setWrapStyleWord(true);
    t.setOpaque(false);
    t.setFocusable(false);
    t.setBorder(null);
    t.setFont(UIManager.getFont("Label.font"));
    t.setForeground(UIManager.getColor("Label.foreground"));
    Dimension pref = t.getPreferredSize();
    t.setMinimumSize(new Dimension(0, pref != null ? pref.height : 0));
    return t;
  }

  static JTextArea subtleInfoText() {
    JTextArea t = new JTextArea();
    t.setEditable(false);
    t.setLineWrap(true);
    t.setWrapStyleWord(true);
    t.setOpaque(false);
    t.setFocusable(false);
    t.setBorder(null);

    Font f = UIManager.getFont("Label.font");
    if (f != null) {
      t.setFont(f.deriveFont(Font.ITALIC));
    } else {
      t.setFont(t.getFont().deriveFont(Font.ITALIC));
    }

    Color hintColor = UIManager.getColor("Label.disabledForeground");
    if (hintColor != null) t.setForeground(hintColor);

    Dimension pref = t.getPreferredSize();
    t.setMinimumSize(new Dimension(0, pref != null ? pref.height : 0));
    return t;
  }

  private static JTextArea buttonWrapText(String text) {
    JTextArea t = new JTextArea(text);
    t.setEditable(false);
    t.setLineWrap(true);
    t.setWrapStyleWord(true);
    t.setOpaque(false);
    t.setFocusable(false);
    t.setBorder(null);

    Font f = UIManager.getFont("CheckBox.font");
    if (f == null) f = UIManager.getFont("Button.font");
    if (f == null) f = UIManager.getFont("Label.font");
    if (f != null) t.setFont(f);

    Color c = UIManager.getColor("CheckBox.foreground");
    if (c == null) c = UIManager.getColor("Label.foreground");
    if (c != null) t.setForeground(c);

    Dimension pref = t.getPreferredSize();
    t.setMinimumSize(new Dimension(0, pref != null ? pref.height : 0));
    return t;
  }

  static JComponent wrapCheckBox(JCheckBox box, String labelText) {
    box.setText("");
    JPanel row = new JPanel(new MigLayout("insets 0, fillx", "[]6[grow,fill]", "[]"));
    row.setOpaque(false);

    JTextArea label = buttonWrapText(labelText);
    label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    label.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (box.isEnabled()) box.doClick();
          }
        });

    row.add(box, "aligny top");
    row.add(label, "growx, pushx, wmin 0");
    return row;
  }

  private static JLabel subtleInfoLabel() {
    JLabel l = new JLabel();
    l.setFont(l.getFont().deriveFont(Font.ITALIC));
    Color hintColor = UIManager.getColor("Label.disabledForeground");
    if (hintColor != null) l.setForeground(hintColor);
    return l;
  }

  private static void showHelpDialog(java.awt.Component parent, String title, String message) {
    JTextArea area = new JTextArea(message);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    area.setFont(UIManager.getFont("Label.font"));

    JScrollPane scroll = new JScrollPane(area);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setPreferredSize(new Dimension(460, 240));

    javax.swing.JOptionPane.showMessageDialog(
        parent, scroll, title, javax.swing.JOptionPane.INFORMATION_MESSAGE);
  }

  static JButton whyHelpButton(String title, String message) {
    JButton b = new JButton("?");
    b.putClientProperty("JButton.buttonType", "help");
    b.setFocusable(false);
    b.setMargin(new Insets(0, 8, 0, 8));
    b.setToolTipText("Why do I need this?");
    b.addActionListener(e -> showHelpDialog(SwingUtilities.getWindowAncestor(b), title, message));
    return b;
  }

  private void addPlaceholderTab(JTabbedPane tabs, String title, String message) {
    JPanel placeholder = new JPanel(new MigLayout("insets 12, fill", "[grow]", "[]12[grow]"));

    JLabel header = new JLabel(title);
    header.putClientProperty(FlatClientProperties.STYLE, "font:+4");

    JTextArea body =
        new JTextArea(
            message + "\n\n" + "This tab is a placeholder. Controls will move here later.");
    body.setLineWrap(true);
    body.setWrapStyleWord(true);
    body.setEditable(false);
    body.setFocusable(false);
    body.setOpaque(false);
    body.setBorder(BorderFactory.createEmptyBorder());

    placeholder.add(header, "growx, wrap");
    placeholder.add(body, "grow");

    JScrollPane scroll = new JScrollPane(placeholder);
    scroll.setBorder(null);
    tabs.addTab(title, scroll);
  }

  private JCheckBox buildAutoConnectCheckbox(UiSettings current) {
    JCheckBox autoConnectOnStart = new JCheckBox("Auto-connect to servers on startup");
    autoConnectOnStart.setSelected(current.autoConnectOnStart());
    autoConnectOnStart.setToolTipText(
        "If enabled, IRCafe will connect to all configured servers automatically after the UI loads.\n"
            + "If disabled, IRCafe starts disconnected and you can connect manually using the Connect button.");
    return autoConnectOnStart;
  }

  private String importNotificationSoundFileToRuntimeDir(File source) throws Exception {
    if (source == null) return null;

    String name = Objects.toString(source.getName(), "").trim();
    if (name.isBlank()) throw new IllegalArgumentException("Invalid file name");

    String lower = name.toLowerCase(Locale.ROOT);
    boolean mp3 = lower.endsWith(".mp3");
    boolean wav = lower.endsWith(".wav");
    if (!mp3 && !wav) {
      throw new IllegalArgumentException("Only .mp3 and .wav are supported");
    }

    Path cfg = runtimeConfig != null ? runtimeConfig.runtimeConfigPath() : null;
    Path base = cfg != null ? cfg.getParent() : null;
    if (base == null) {
      throw new IllegalStateException("Runtime config directory is unavailable");
    }

    Path soundsDir = base.resolve("sounds");
    Files.createDirectories(soundsDir);

    // Sanitize filename for portability.
    String sanitized = name.replaceAll("[^A-Za-z0-9._-]+", "_");
    if (sanitized.isBlank()) {
      sanitized = mp3 ? "notification.mp3" : "notification.wav";
    }

    String ext = mp3 ? "mp3" : "wav";
    String baseName = sanitized;
    int dot = sanitized.lastIndexOf('.');
    if (dot > 0) {
      baseName = sanitized.substring(0, dot);
    }

    Path dest = soundsDir.resolve(baseName + "." + ext);
    int i = 2;
    while (Files.exists(dest)) {
      dest = soundsDir.resolve(baseName + "-" + i + "." + ext);
      i++;
    }

    Files.copy(source.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

    // Store relative to the runtime config directory.
    return "sounds/" + dest.getFileName();
  }

  private TrayControls buildTrayControls(
      UiSettings current, NotificationSoundSettings soundSettings, PushyProperties pushySettings) {
    if (soundSettings == null) {
      soundSettings = new NotificationSoundSettings(true, BuiltInSound.NOTIF_1.name(), false, null);
    }
    if (pushySettings == null) {
      pushySettings = new PushyProperties(false, null, null, null, null, null, null, null);
    }
    JCheckBox enabled = new JCheckBox("Enable system tray icon", current.trayEnabled());
    JCheckBox closeToTray =
        new JCheckBox("Close button hides to tray instead of exiting", current.trayCloseToTray());
    JCheckBox minimizeToTray =
        new JCheckBox("Minimize button hides to tray", current.trayMinimizeToTray());
    JCheckBox startMinimized =
        new JCheckBox("Start minimized to tray", current.trayStartMinimized());

    JCheckBox notifyHighlights =
        new JCheckBox("Desktop notifications for highlights", current.trayNotifyHighlights());
    JCheckBox notifyPrivateMessages =
        new JCheckBox(
            "Desktop notifications for private messages", current.trayNotifyPrivateMessages());
    JCheckBox notifyConnectionState =
        new JCheckBox(
            "Desktop notifications for connection state", current.trayNotifyConnectionState());

    JCheckBox notifyOnlyWhenUnfocused =
        new JCheckBox(
            "Only notify when IRCafe is not focused", current.trayNotifyOnlyWhenUnfocused());
    JCheckBox notifyOnlyWhenMinimizedOrHidden =
        new JCheckBox(
            "Only notify when minimized or hidden to tray",
            current.trayNotifyOnlyWhenMinimizedOrHidden());
    JCheckBox notifySuppressWhenTargetActive =
        new JCheckBox(
            "Don't notify for the active buffer", current.trayNotifySuppressWhenTargetActive());
    JCheckBox updateNotifierEnabled =
        new JCheckBox(
            "Show update notifier in status bar", runtimeConfig.readUpdateNotifierEnabled(true));
    updateNotifierEnabled.setToolTipText(
        "Checks GitHub releases in the background and alerts when a newer IRCafe version exists.");
    JCheckBox lagIndicatorEnabled =
        new JCheckBox(
            "Show lag indicator in status bar", runtimeConfig.readLagIndicatorEnabled(true));
    lagIndicatorEnabled.setToolTipText(
        "Shows measured round-trip server lag for the active server in the status bar.");

    boolean linuxTmp = false;
    boolean linuxActionsSupportedTmp = false;
    try {
      linuxTmp = gnomeDbusBackend != null && gnomeDbusBackend.isLinux();
      if (linuxTmp) {
        GnomeDbusNotificationBackend.ProbeResult pr = gnomeDbusBackend.probe();
        linuxActionsSupportedTmp = pr != null && pr.sessionBusReachable() && pr.actionsSupported();
      }
    } catch (Exception ignored) {
    }

    final boolean linux = linuxTmp;
    final boolean linuxActionsSupported = linuxActionsSupportedTmp;

    JCheckBox linuxDbusActions =
        new JCheckBox(
            "Use Linux D-Bus notifications (click-to-open)",
            linux && linuxActionsSupported && current.trayLinuxDbusActionsEnabled());
    linuxDbusActions.setToolTipText(
        linux
            ? (linuxActionsSupported
                ? "Uses org.freedesktop.Notifications over D-Bus so clicking a notification can open IRCafe."
                : "Click actions aren't available in this session (no D-Bus notification actions support detected).")
            : "Linux only.");

    JComboBox<NotificationBackendMode> notificationBackend =
        new JComboBox<>(NotificationBackendMode.values());
    notificationBackend.setSelectedItem(current.trayNotificationBackendMode());
    notificationBackend.setToolTipText(
        "Select how desktop notifications are delivered: native backends, two-slices fallback, or two-slices only.");

    JButton testNotification = new JButton("Test notification");
    testNotification.setToolTipText(
        "Send a test desktop notification (click to open IRCafe).\n"
            + "This does not require highlight/PM notifications to be enabled.");
    testNotification.addActionListener(
        e -> {
          try {
            if (trayNotificationService != null) {
              trayNotificationService.notifyTest();
            }
          } catch (Throwable ignored) {
          }
        });

    notifyHighlights.setToolTipText(
        "Show a desktop notification when someone mentions your nick in a channel.");
    notifyPrivateMessages.setToolTipText(
        "Show a desktop notification when you receive a private message.");
    notifyConnectionState.setToolTipText(
        "Show a desktop notification when connecting/disconnecting.");

    notifyOnlyWhenUnfocused.setToolTipText(
        "Common HexChat behavior: only notify when IRCafe isn't the active window.");
    notifyOnlyWhenMinimizedOrHidden.setToolTipText(
        "Only notify when IRCafe is minimized or hidden to tray.");
    notifySuppressWhenTargetActive.setToolTipText(
        "If the message is in the currently selected buffer, suppress the notification.");

    JCheckBox notificationSoundsEnabled =
        new JCheckBox("Play sound with desktop notifications", soundSettings.enabled());
    notificationSoundsEnabled.setToolTipText(
        "Plays a short sound whenever IRCafe shows a desktop notification.");

    JCheckBox notificationSoundUseCustom =
        new JCheckBox("Use custom sound file", soundSettings.useCustom());
    notificationSoundUseCustom.setToolTipText(
        "If enabled, IRCafe will play a custom file stored next to your runtime config.\n"
            + "Supported formats: MP3, WAV.");

    JTextField notificationSoundCustomPath =
        new JTextField(Objects.toString(soundSettings.customPath(), ""));
    notificationSoundCustomPath.setEditable(false);
    notificationSoundCustomPath.setToolTipText(
        "Custom sound path (relative to the runtime config directory).\n"
            + "Click Browse... to import a file.");

    JComboBox<BuiltInSound> notificationSound = new JComboBox<>(BuiltInSound.valuesForUi());
    configureBuiltInSoundCombo(notificationSound);
    notificationSound.setSelectedItem(BuiltInSound.fromId(soundSettings.soundId()));
    notificationSound.setToolTipText("Choose which bundled sound to use for notifications.");

    JButton browseCustomSound = new JButton("Browse...");
    browseCustomSound.setToolTipText(
        "Choose an MP3 or WAV file and copy it into IRCafe's runtime config directory.");
    browseCustomSound.addActionListener(
        e -> {
          try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose notification sound (MP3 or WAV)");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setAcceptAllFileFilterUsed(true);
            chooser.addChoosableFileFilter(
                new FileNameExtensionFilter("Audio files (MP3, WAV)", "mp3", "wav"));
            int result =
                chooser.showOpenDialog(SwingUtilities.getWindowAncestor(browseCustomSound));
            if (result != JFileChooser.APPROVE_OPTION) return;

            File f = chooser.getSelectedFile();
            if (f == null) return;
            String rel = importNotificationSoundFileToRuntimeDir(f);
            if (rel != null && !rel.isBlank()) {
              notificationSoundCustomPath.setText(rel);
              notificationSoundUseCustom.setSelected(true);
            }
          } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(browseCustomSound),
                "Could not import sound file.\n\n" + ex.getMessage(),
                "Import failed",
                javax.swing.JOptionPane.ERROR_MESSAGE);
          }
        });

    JButton clearCustomSound = new JButton("Clear");
    clearCustomSound.setToolTipText("Stop using a custom file and revert to bundled sounds.");
    clearCustomSound.addActionListener(
        e -> {
          notificationSoundUseCustom.setSelected(false);
          notificationSoundCustomPath.setText("");
        });

    JButton testSound = new JButton("Test sound");
    testSound.setToolTipText("Play the selected sound.");
    testSound.addActionListener(
        e -> {
          try {
            if (notificationSoundService != null) {
              if (notificationSoundUseCustom.isSelected()) {
                String rel = notificationSoundCustomPath.getText();
                rel = rel != null ? rel.trim() : "";
                if (!rel.isBlank()) {
                  notificationSoundService.previewCustom(rel);
                }
              } else {
                BuiltInSound s = (BuiltInSound) notificationSound.getSelectedItem();
                notificationSoundService.preview(s);
              }
            }
          } catch (Throwable ignored) {
          }
        });

    JCheckBox pushyEnabled =
        new JCheckBox(
            "Forward matched IRC event notifications to Pushy",
            Boolean.TRUE.equals(pushySettings.enabled()));
    pushyEnabled.setToolTipText(
        "Sends notifications for matching IRC event rules to Pushy (device token or topic).");

    JTextField pushyEndpoint =
        new JTextField(Objects.toString(pushySettings.endpoint(), "https://api.pushy.me/push"));
    pushyEndpoint.setToolTipText("Pushy API endpoint URL.");

    JPasswordField pushyApiKey = new JPasswordField(Objects.toString(pushySettings.apiKey(), ""));
    pushyApiKey.setToolTipText("Pushy Secret API key.");

    PushyTargetMode pushyInitialTargetMode =
        pushySettings.deviceToken() != null && !pushySettings.deviceToken().isBlank()
            ? PushyTargetMode.DEVICE_TOKEN
            : PushyTargetMode.TOPIC;
    String pushyInitialTargetValue =
        pushyInitialTargetMode == PushyTargetMode.DEVICE_TOKEN
            ? Objects.toString(pushySettings.deviceToken(), "")
            : Objects.toString(pushySettings.topic(), "");

    JComboBox<PushyTargetMode> pushyTargetMode = new JComboBox<>(PushyTargetMode.values());
    pushyTargetMode.setSelectedItem(pushyInitialTargetMode);
    pushyTargetMode.setToolTipText("Choose destination type for Pushy notifications.");

    JTextField pushyTargetValue = new JTextField(pushyInitialTargetValue);
    pushyTargetValue.setToolTipText("Destination value for selected target mode.");

    JTextField pushyTitlePrefix =
        new JTextField(Objects.toString(pushySettings.titlePrefix(), "IRCafe"));
    pushyTitlePrefix.setToolTipText("Prefix prepended to Pushy notification titles.");

    JSpinner pushyConnectTimeoutSeconds =
        new JSpinner(
            new SpinnerNumberModel(
                Integer.valueOf(pushySettings.connectTimeoutSeconds()),
                Integer.valueOf(1),
                Integer.valueOf(30),
                Integer.valueOf(1)));
    JSpinner pushyReadTimeoutSeconds =
        new JSpinner(
            new SpinnerNumberModel(
                Integer.valueOf(pushySettings.readTimeoutSeconds()),
                Integer.valueOf(1),
                Integer.valueOf(60),
                Integer.valueOf(1)));

    JButton pushyTest = new JButton("Test Pushy");
    pushyTest.setToolTipText("Send a real test notification to the configured Pushy destination.");
    JLabel pushyValidationLabel = new JLabel(" ");
    pushyValidationLabel.setForeground(errorForeground());
    JLabel pushyTestStatus = new JLabel(" ");

    Runnable refreshPushyValidation =
        () -> {
          PushyTargetMode mode =
              pushyTargetMode.getSelectedItem() instanceof PushyTargetMode m
                  ? m
                  : PushyTargetMode.DEVICE_TOKEN;
          String endpoint = Objects.toString(pushyEndpoint.getText(), "").trim();
          String apiKey = new String(pushyApiKey.getPassword()).trim();
          String target = Objects.toString(pushyTargetValue.getText(), "").trim();
          String err =
              validatePushyInputs(pushyEnabled.isSelected(), endpoint, apiKey, mode, target);
          if (err == null) {
            pushyValidationLabel.setText(" ");
            pushyValidationLabel.setVisible(false);
            pushyTest.setEnabled(pushyEnabled.isSelected());
          } else {
            pushyValidationLabel.setText(err);
            pushyValidationLabel.setVisible(true);
            pushyTest.setEnabled(false);
          }
        };

    pushyTest.addActionListener(
        e -> {
          PushyTargetMode mode =
              pushyTargetMode.getSelectedItem() instanceof PushyTargetMode m
                  ? m
                  : PushyTargetMode.DEVICE_TOKEN;
          String endpoint = Objects.toString(pushyEndpoint.getText(), "").trim();
          String apiKey = new String(pushyApiKey.getPassword()).trim();
          String target = Objects.toString(pushyTargetValue.getText(), "").trim();
          String titlePrefix = Objects.toString(pushyTitlePrefix.getText(), "").trim();
          int connectSeconds = ((Number) pushyConnectTimeoutSeconds.getValue()).intValue();
          int readSeconds = ((Number) pushyReadTimeoutSeconds.getValue()).intValue();

          String err =
              validatePushyInputs(pushyEnabled.isSelected(), endpoint, apiKey, mode, target);
          if (err != null) {
            pushyTestStatus.setText(err);
            pushyTestStatus.setForeground(errorForeground());
            return;
          }

          String deviceToken = mode == PushyTargetMode.DEVICE_TOKEN ? target : null;
          String topic = mode == PushyTargetMode.TOPIC ? target : null;
          PushyProperties draft =
              new PushyProperties(
                  pushyEnabled.isSelected(),
                  endpoint.isBlank() ? null : endpoint,
                  apiKey.isBlank() ? null : apiKey,
                  deviceToken,
                  topic,
                  titlePrefix.isBlank() ? null : titlePrefix,
                  connectSeconds,
                  readSeconds);

          pushyTest.setEnabled(false);
          pushyTestStatus.setText("Sending test push…");
          pushyTestStatus.setForeground(UIManager.getColor("Label.foreground"));

          pushyTestExecutor.submit(
              () -> {
                PushyNotificationService.PushResult result =
                    pushyNotificationService != null
                        ? pushyNotificationService.sendTestNotification(
                            draft, "IRCafe Test", "This is a Pushy test notification from IRCafe.")
                        : PushyNotificationService.PushResult.failed(
                            "Pushy service is unavailable.");
                SwingUtilities.invokeLater(
                    () -> {
                      pushyTestStatus.setText(
                          result.message() == null || result.message().isBlank()
                              ? (result.success() ? "Push sent." : "Push failed.")
                              : result.message());
                      pushyTestStatus.setForeground(
                          result.success()
                              ? UIManager.getColor("Label.foreground")
                              : errorForeground());
                      refreshPushyValidation.run();
                    });
              });
        });

    Runnable refreshPushyDestinationState =
        () -> {
          PushyTargetMode mode =
              pushyTargetMode.getSelectedItem() instanceof PushyTargetMode m
                  ? m
                  : PushyTargetMode.DEVICE_TOKEN;
          if (mode == PushyTargetMode.DEVICE_TOKEN) {
            pushyTargetValue.setToolTipText("Single-device destination token.");
          } else {
            pushyTargetValue.setToolTipText("Topic destination for fan-out delivery.");
          }
        };

    Runnable refreshPushyState =
        () -> {
          boolean en = pushyEnabled.isSelected();
          pushyEndpoint.setEnabled(en);
          pushyApiKey.setEnabled(en);
          pushyTargetMode.setEnabled(en);
          pushyTargetValue.setEnabled(en);
          pushyTitlePrefix.setEnabled(en);
          pushyConnectTimeoutSeconds.setEnabled(en);
          pushyReadTimeoutSeconds.setEnabled(en);
          refreshPushyDestinationState.run();
          refreshPushyValidation.run();
        };
    pushyEnabled.addActionListener(e -> refreshPushyState.run());
    pushyTargetMode.addActionListener(e -> refreshPushyState.run());
    pushyEndpoint.getDocument().addDocumentListener(new SimpleDocListener(refreshPushyValidation));
    pushyApiKey.getDocument().addDocumentListener(new SimpleDocListener(refreshPushyValidation));
    pushyTargetValue
        .getDocument()
        .addDocumentListener(new SimpleDocListener(refreshPushyValidation));
    refreshPushyState.run();

    Runnable refreshEnabled =
        () -> {
          boolean en = enabled.isSelected();
          closeToTray.setEnabled(en);
          minimizeToTray.setEnabled(en);
          startMinimized.setEnabled(en);
          notifyHighlights.setEnabled(en);
          notifyPrivateMessages.setEnabled(en);
          notifyConnectionState.setEnabled(en);

          notifyOnlyWhenUnfocused.setEnabled(en);
          notifyOnlyWhenMinimizedOrHidden.setEnabled(en);
          notifySuppressWhenTargetActive.setEnabled(en);

          linuxDbusActions.setEnabled(en && linux && linuxActionsSupported);
          notificationBackend.setEnabled(en);
          testNotification.setEnabled(en);

          notificationSoundsEnabled.setEnabled(en);

          boolean snd = en && notificationSoundsEnabled.isSelected();
          notificationSoundUseCustom.setEnabled(snd);

          boolean useCustom = snd && notificationSoundUseCustom.isSelected();
          notificationSoundCustomPath.setEnabled(snd);
          browseCustomSound.setEnabled(snd);

          String rel = notificationSoundCustomPath.getText();
          rel = rel != null ? rel.trim() : "";
          clearCustomSound.setEnabled(snd && !rel.isBlank());

          notificationSound.setEnabled(snd && !useCustom);
          testSound.setEnabled(snd);

          if (!en) {
            closeToTray.setSelected(false);
            minimizeToTray.setSelected(false);
            startMinimized.setSelected(false);
            notifyHighlights.setSelected(false);
            notifyPrivateMessages.setSelected(false);
            notifyConnectionState.setSelected(false);

            notifyOnlyWhenUnfocused.setSelected(false);
            notifyOnlyWhenMinimizedOrHidden.setSelected(false);
            notifySuppressWhenTargetActive.setSelected(false);

            linuxDbusActions.setSelected(false);

            notificationSoundsEnabled.setSelected(false);
          }

          if (!(linux && linuxActionsSupported)) {
            linuxDbusActions.setSelected(false);
          }
        };

    enabled.addActionListener(e -> refreshEnabled.run());
    notificationSoundsEnabled.addActionListener(e -> refreshEnabled.run());
    notificationSoundUseCustom.addActionListener(e -> refreshEnabled.run());
    refreshEnabled.run();

    TrayControls controls =
        new TrayControls(
            enabled,
            closeToTray,
            minimizeToTray,
            startMinimized,
            notifyHighlights,
            notifyPrivateMessages,
            notifyConnectionState,
            notifyOnlyWhenUnfocused,
            notifyOnlyWhenMinimizedOrHidden,
            notifySuppressWhenTargetActive,
            updateNotifierEnabled,
            lagIndicatorEnabled,
            linuxDbusActions,
            notificationBackend,
            testNotification,
            notificationSoundsEnabled,
            notificationSoundUseCustom,
            notificationSoundCustomPath,
            browseCustomSound,
            clearCustomSound,
            notificationSound,
            testSound,
            pushyEnabled,
            pushyEndpoint,
            pushyApiKey,
            pushyTargetMode,
            pushyTargetValue,
            pushyTitlePrefix,
            pushyConnectTimeoutSeconds,
            pushyReadTimeoutSeconds,
            pushyValidationLabel,
            pushyTest,
            pushyTestStatus);
    controls.panel =
        TrayNotificationsPanelSupport.buildTabsPanel(
            controls, runtimeConfig, linux, linuxActionsSupported);
    return controls;
  }

  private static String validatePushyInputs(
      boolean enabled,
      String endpoint,
      String apiKey,
      PushyTargetMode targetMode,
      String targetValue) {
    if (!enabled) return null;

    String key = Objects.toString(apiKey, "").trim();
    if (key.isEmpty()) return "Pushy API key is required.";

    String target = Objects.toString(targetValue, "").trim();
    if (target.isEmpty()) {
      return switch (targetMode) {
        case TOPIC -> "Pushy topic is required.";
        case DEVICE_TOKEN -> "Pushy device token is required.";
      };
    }

    String ep = Objects.toString(endpoint, "").trim();
    if (!ep.isEmpty() && !isValidPushyEndpoint(ep)) {
      return "Pushy endpoint must be a valid http(s) URL.";
    }

    return null;
  }

  private static boolean isValidPushyEndpoint(String endpoint) {
    try {
      URI uri = URI.create(Objects.toString(endpoint, "").trim());
      String scheme = Objects.toString(uri.getScheme(), "").trim().toLowerCase(Locale.ROOT);
      String host = Objects.toString(uri.getHost(), "").trim();
      return ("https".equals(scheme) || "http".equals(scheme)) && !host.isBlank();
    } catch (Exception ignored) {
      return false;
    }
  }

  private JCheckBox buildPresenceFoldsCheckbox(UiSettings current) {
    JCheckBox presenceFolds = new JCheckBox("Fold join/part/quit spam into a compact block");
    presenceFolds.setSelected(current.presenceFoldsEnabled());
    presenceFolds.setToolTipText(
        "When enabled, runs of join/part/quit/nick-change events are folded into a single expandable block.\n"
            + "When disabled, each event is shown as its own status line.");
    return presenceFolds;
  }

  private JCheckBox buildCtcpRequestsInActiveTargetCheckbox(UiSettings current) {
    JCheckBox ctcp = new JCheckBox("Show inbound CTCP requests in the currently active chat tab");
    ctcp.setSelected(current.ctcpRequestsInActiveTargetEnabled());
    ctcp.setToolTipText(
        "When enabled, inbound CTCP requests (e.g. VERSION, PING) are announced in the currently active chat tab.\n"
            + "When disabled, CTCP requests are routed to the target they came from (channel or PM).");
    return ctcp;
  }

  private JTextField buildDefaultQuitMessageField() {
    JTextField field = new JTextField(runtimeConfig.readDefaultQuitMessage());
    field.setToolTipText(
        "Used when /quit has no explicit reason, and when IRCafe closes IRC connections during shutdown.");
    return field;
  }

  private JCheckBox buildOutgoingDeliveryIndicatorsCheckbox(UiSettings current) {
    JCheckBox cb =
        new JCheckBox("Show send-status indicators for my outgoing messages (spinner + green dot)");
    cb.setSelected(current.outgoingDeliveryIndicatorsEnabled());
    cb.setToolTipText(
        "When enabled, outgoing messages show a pending spinner and a brief green confirmation dot when server echo reconciliation completes.\n"
            + "When disabled, these visual indicators are hidden; message send/reconcile behavior is unchanged.");
    return cb;
  }

  private JCheckBox buildTypingIndicatorsSendCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Send typing indicators (IRCv3)");
    cb.setSelected(current.typingIndicatorsEnabled());
    cb.setToolTipText(
        "When enabled, IRCafe will send your IRCv3 typing state (active/paused/done) when the server supports it.");
    return cb;
  }

  private JCheckBox buildTypingIndicatorsReceiveCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Display incoming typing indicators (IRCv3)");
    cb.setSelected(current.typingIndicatorsReceiveEnabled());
    cb.setToolTipText(
        "When enabled, IRCafe will display incoming IRCv3 typing indicators from other users.");
    return cb;
  }

  private JCheckBox buildTypingIndicatorsTreeDisplayCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Show typing marker next to channels");
    cb.setSelected(current.typingIndicatorsTreeEnabled());
    cb.setToolTipText(
        "Controls typing markers in the server tree channel list.\n"
            + "Typing transport behavior is unchanged.");
    return cb;
  }

  private JCheckBox buildTypingIndicatorsUsersListDisplayCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Show typing marker next to users");
    cb.setSelected(current.typingIndicatorsUsersListEnabled());
    cb.setToolTipText(
        "Controls typing markers beside nicknames in the channel user list.\n"
            + "Typing transport behavior is unchanged.");
    return cb;
  }

  private JCheckBox buildTypingIndicatorsTranscriptDisplayCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Show typing status in the transcript input area");
    cb.setSelected(current.typingIndicatorsTranscriptEnabled());
    cb.setToolTipText(
        "Controls the incoming typing banner above the input field (\"X is typing\").\n"
            + "Typing transport behavior is unchanged.");
    return cb;
  }

  private JCheckBox buildTypingIndicatorsSendSignalDisplayCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Show local typing-send arrows near Send");
    cb.setSelected(current.typingIndicatorsSendSignalEnabled());
    cb.setToolTipText(
        "Controls the local send telemetry arrows near the Send button.\n"
            + "Typing transport behavior is unchanged.");
    return cb;
  }

  private JComboBox<TypingTreeIndicatorStyleOption> buildTypingTreeIndicatorStyleCombo(
      UiSettings current) {
    TypingTreeIndicatorStyleOption[] options =
        new TypingTreeIndicatorStyleOption[] {
          new TypingTreeIndicatorStyleOption("dots", "3 dots (ellipsis)"),
          new TypingTreeIndicatorStyleOption("keyboard", "Keyboard glyph"),
          new TypingTreeIndicatorStyleOption("glow-dot", "Glowing green dot")
        };
    JComboBox<TypingTreeIndicatorStyleOption> combo = new JComboBox<>(options);
    combo.setToolTipText("Choose how typing activity appears in the server tree for channels.");
    combo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel c =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            if (value instanceof TypingTreeIndicatorStyleOption o) {
              c.setText(o.label());
            }
            return c;
          }
        });

    String configured = current != null ? current.typingIndicatorsTreeStyle() : null;
    String normalized = UiSettings.normalizeTypingTreeIndicatorStyle(configured);
    for (TypingTreeIndicatorStyleOption option : options) {
      if (option.id().equalsIgnoreCase(normalized)) {
        combo.setSelectedItem(option);
        break;
      }
    }
    return combo;
  }

  private JComboBox<MatrixUserListNameDisplayModeOption> buildMatrixUserListNameDisplayModeCombo(
      UiSettings current) {
    MatrixUserListNameDisplayModeOption[] options =
        new MatrixUserListNameDisplayModeOption[] {
          new MatrixUserListNameDisplayModeOption("compact", "Display name only (compact)"),
          new MatrixUserListNameDisplayModeOption(
              "verbose", "Display name + Matrix user ID (verbose)")
        };
    JComboBox<MatrixUserListNameDisplayModeOption> combo = new JComboBox<>(options);
    combo.setToolTipText(
        "Controls how Matrix users are shown in the channel user list (display name only or display name with Matrix user ID).");
    combo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel c =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            if (value instanceof MatrixUserListNameDisplayModeOption o) {
              c.setText(o.label());
            }
            return c;
          }
        });

    String configured = current != null ? current.matrixUserListNameDisplayMode() : null;
    String normalized = UiSettings.normalizeMatrixUserListNameDisplayMode(configured);
    for (MatrixUserListNameDisplayModeOption option : options) {
      if (option.id().equalsIgnoreCase(normalized)) {
        combo.setSelectedItem(option);
        break;
      }
    }
    return combo;
  }

  private JCheckBox buildServerTreeNotificationBadgesCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Show unread/highlight badges in the server tree");
    cb.setSelected(current.serverTreeNotificationBadgesEnabled());
    cb.setToolTipText(
        "When enabled, the server tree shows numeric unread/highlight badges next to targets.\n"
            + "When disabled, badge counts are hidden but unread/highlight tracking still runs.");
    return cb;
  }

  private JSpinner buildServerTreeUnreadBadgeScalePercentSpinner() {
    int current = runtimeConfig.readServerTreeUnreadBadgeScalePercent(100);
    SpinnerNumberModel model = new SpinnerNumberModel(current, 50, 150, 5);
    JSpinner spinner = new JSpinner(model);
    spinner.setToolTipText(
        "Scale for unread/highlight count badges in the server tree. Lower values make badges and numbers smaller.");
    return spinner;
  }

  private SpellcheckControls buildSpellcheckControls(SpellcheckSettings settings) {
    return SpellcheckControlsSupport.buildControls(settings);
  }

  static void configureBuiltInSoundCombo(JComboBox<BuiltInSound> combo) {
    if (combo == null) return;
    combo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof BuiltInSound sound) {
              setText(sound.displayNameForUi());
            }
            return this;
          }
        });
  }

  private static String typingTreeIndicatorStyleValue(
      JComboBox<TypingTreeIndicatorStyleOption> combo) {
    Object selected = combo != null ? combo.getSelectedItem() : null;
    if (selected instanceof TypingTreeIndicatorStyleOption o) {
      return UiSettings.normalizeTypingTreeIndicatorStyle(o.id());
    }
    return "dots";
  }

  private static String matrixUserListNameDisplayModeValue(
      JComboBox<MatrixUserListNameDisplayModeOption> combo) {
    Object selected = combo != null ? combo.getSelectedItem() : null;
    if (selected instanceof MatrixUserListNameDisplayModeOption o) {
      return UiSettings.normalizeMatrixUserListNameDisplayMode(o.id());
    }
    return "compact";
  }

  private NickColorControls buildNickColorControls(Window owner, List<AutoCloseable> closeables) {
    return NickColorControlsSupport.buildControls(
        owner,
        closeables,
        nickColorService,
        nickColorOverridesDialog,
        nickColorSettingsBus != null ? nickColorSettingsBus.get() : null);
  }

  private ImageEmbedControls buildImageEmbedControls(
      UiSettings current, List<AutoCloseable> closeables) {
    return ChatDisplayControlsSupport.buildImageEmbedControls(current, closeables);
  }

  private LinkPreviewControls buildLinkPreviewControls(
      UiSettings current, EmbedCardStyle currentEmbedCardStyle) {
    return ChatDisplayControlsSupport.buildLinkPreviewControls(current, currentEmbedCardStyle);
  }

  private TimestampControls buildTimestampControls(UiSettings current) {
    return ChatDisplayControlsSupport.buildTimestampControls(current);
  }

  private JComboBox<MemoryUsageDisplayMode> buildMemoryUsageDisplayModeCombo(UiSettings current) {
    JComboBox<MemoryUsageDisplayMode> combo = new JComboBox<>(MemoryUsageDisplayMode.values());
    MemoryUsageDisplayMode selected =
        current != null && current.memoryUsageDisplayMode() != null
            ? current.memoryUsageDisplayMode()
            : MemoryUsageDisplayMode.LONG;
    combo.setSelectedItem(selected);
    combo.setToolTipText("Controls the memory widget shown on the far right side of the menu bar.");
    return combo;
  }

  private JSpinner buildMemoryUsageRefreshIntervalSpinner(
      UiSettings current, List<AutoCloseable> closeables) {
    int refreshIntervalMs =
        current != null && current.memoryUsageRefreshIntervalMs() > 0
            ? current.memoryUsageRefreshIntervalMs()
            : 1000;
    JSpinner spinner = numberSpinner(refreshIntervalMs, 250, 60_000, 250, closeables);
    spinner.setToolTipText(
        "How often the memory widget refreshes (milliseconds). Lower values cost more CPU/wakeups.");
    return spinner;
  }

  private MemoryWarningControls buildMemoryWarningControls(
      UiSettings current, List<AutoCloseable> closeables) {
    int nearMaxPercent =
        current != null && current.memoryUsageWarningNearMaxPercent() > 0
            ? current.memoryUsageWarningNearMaxPercent()
            : 5;

    JSpinner nearMaxPercentSpinner = numberSpinner(nearMaxPercent, 1, 50, 1, closeables);
    nearMaxPercentSpinner.setToolTipText(
        "Trigger warning actions when heap usage is within this percent of the JVM max.");

    JCheckBox tooltipEnabled = new JCheckBox("Show warning tooltip near memory widget");
    tooltipEnabled.setSelected(current == null || current.memoryUsageWarningTooltipEnabled());
    tooltipEnabled.setToolTipText(
        "Shows a transient warning tooltip near the memory widget when threshold is crossed.");

    JCheckBox toastEnabled = new JCheckBox("Show desktop toast warning");
    toastEnabled.setSelected(current != null && current.memoryUsageWarningToastEnabled());
    toastEnabled.setToolTipText(
        "Uses the existing tray notification pipeline for memory threshold alerts.");

    JCheckBox pushyEnabled = new JCheckBox("Send Pushy warning");
    pushyEnabled.setSelected(current != null && current.memoryUsageWarningPushyEnabled());
    pushyEnabled.setToolTipText(
        "Sends a Pushy notification when configured and the warning threshold is crossed.");

    JCheckBox soundEnabled = new JCheckBox("Play warning sound");
    soundEnabled.setSelected(current != null && current.memoryUsageWarningSoundEnabled());
    soundEnabled.setToolTipText("Plays the configured notification sound on memory warning.");

    return new MemoryWarningControls(
        nearMaxPercentSpinner, tooltipEnabled, toastEnabled, pushyEnabled, soundEnabled);
  }

  private HistoryControls buildHistoryControls(UiSettings current, List<AutoCloseable> closeables) {
    return HistoryControlsSupport.buildControls(
        current,
        closeables,
        settingsBus == null || settingsBus.chatSmoothWheelScrollingEnabled(),
        runtimeConfig == null || runtimeConfig.readChatHistoryLockViewportDuringLoadOlder(true));
  }

  private LoggingControls buildLoggingControls(
      LogProperties logProps, List<AutoCloseable> closeables) {
    return LoggingControlsSupport.buildControls(logProps, closeables, serverDialogs, dialog);
  }

  private OutgoingColorControls buildOutgoingColorControls(UiSettings current) {
    return OutgoingColorControlsSupport.buildControls(dialog, current);
  }

  private NetworkAdvancedControls buildNetworkAdvancedControls(
      UiSettings current, List<AutoCloseable> closeables) {
    IrcProperties.Proxy p = NetProxyContext.settings();
    if (p == null) p = new IrcProperties.Proxy(false, "", 1080, "", "", true, 10_000, 30_000);
    IrcProperties.Heartbeat hb = NetHeartbeatContext.settings();
    if (hb == null) hb = new IrcProperties.Heartbeat(true, 15_000, 360_000);
    boolean preferLoginHintDefault =
        runtimeConfig == null
            ? DEFAULT_GENERIC_BOUNCER_PREFER_LOGIN_HINT
            : runtimeConfig.readGenericBouncerPreferLoginHint(
                DEFAULT_GENERIC_BOUNCER_PREFER_LOGIN_HINT);
    String loginTemplateDefault =
        runtimeConfig == null
            ? DEFAULT_GENERIC_BOUNCER_LOGIN_TEMPLATE
            : runtimeConfig.readGenericBouncerLoginTemplate(DEFAULT_GENERIC_BOUNCER_LOGIN_TEMPLATE);
    NetworkConnectionPanelControls connection =
        NetworkConnectionPanelSupport.buildControls(
            p,
            hb,
            closeables,
            NetTlsContext.trustAllCertificates(),
            preferLoginHintDefault,
            loginTemplateDefault);
    UserLookupsPanelControls userLookups =
        UserLookupsPanelSupport.buildControls(current, closeables);

    return new NetworkAdvancedControls(
        connection.proxy,
        userLookups.userhost,
        userLookups.enrichment,
        connection.heartbeat,
        connection.bouncer,
        userLookups.monitorIsonPollIntervalSeconds,
        connection.trustAllTlsCertificates,
        connection.panel,
        userLookups.panel);
  }

  private JPanel buildMemoryPanel(
      JComboBox<MemoryUsageDisplayMode> memoryUsageDisplayMode,
      JSpinner memoryUsageRefreshIntervalMs,
      MemoryWarningControls memoryWarnings) {
    return MemoryPanelSupport.buildPanel(
        memoryUsageDisplayMode, memoryUsageRefreshIntervalMs, memoryWarnings);
  }

  private LaunchJvmControls buildLaunchJvmControls() {
    return LaunchJvmControlsSupport.buildControls(runtimeConfig);
  }

  private JPanel buildStartupPanel(JCheckBox autoConnectOnStart, LaunchJvmControls launchJvm) {
    return StartupPanelSupport.buildPanel(autoConnectOnStart, launchJvm);
  }

  private JPanel buildTrayNotificationsPanel(TrayControls trayControls) {
    JPanel form =
        new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]10[]6[grow,fill]"));
    form.add(tabTitle("Tray & Notifications"), "growx, wrap");
    form.add(sectionTitle("Categories"), "growx, wmin 0, wrap");
    form.add(
        helpText(
            "Use the sub-tabs below to configure tray behavior, desktop notifications, notification sounds, and Linux integration."),
        "growx, wmin 0, wrap");
    form.add(trayControls.panel, "grow, push, wmin 0");
    return form;
  }

  private JPanel buildChatPanel(
      JCheckBox presenceFolds,
      JCheckBox ctcpRequestsInActiveTarget,
      JTextField defaultQuitMessage,
      SpellcheckControls spellcheck,
      NickColorControls nickColors,
      TimestampControls timestamps,
      OutgoingColorControls outgoing,
      JCheckBox outgoingDeliveryIndicators) {
    return ChatPanelSupport.buildPanel(
        presenceFolds,
        ctcpRequestsInActiveTarget,
        defaultQuitMessage,
        spellcheck,
        nickColors,
        timestamps,
        outgoing,
        outgoingDeliveryIndicators);
  }

  private CtcpAutoReplyControls buildCtcpAutoReplyControls() {
    return CtcpAutoReplySupport.buildControls(
        runtimeConfig.readCtcpAutoRepliesEnabled(true),
        runtimeConfig.readCtcpAutoReplyVersionEnabled(true),
        runtimeConfig.readCtcpAutoReplyPingEnabled(true),
        runtimeConfig.readCtcpAutoReplyTimeEnabled(true));
  }

  private JPanel buildCtcpRepliesPanel(CtcpAutoReplyControls controls) {
    return CtcpAutoReplySupport.buildPanel(controls);
  }

  static JTextArea subtleInfoTextWith(String text) {
    JTextArea t = subtleInfoText();
    t.setText(text);
    return t;
  }

  private JButton buildAdvancedEmbedPolicyButton(
      Window owner,
      java.util.concurrent.atomic.AtomicReference<EmbedLoadPolicySnapshot> pendingEmbedLoadPolicy) {
    JButton advanced = new JButton("Advanced Policy...");
    advanced.setToolTipText(
        "Open advanced allow/deny controls for embed/link loading by user, channel, URL/domain, and network.");
    advanced.addActionListener(
        e -> {
          if (embedLoadPolicyDialog == null || pendingEmbedLoadPolicy == null) return;
          EmbedLoadPolicySnapshot current =
              pendingEmbedLoadPolicy.get() != null
                  ? pendingEmbedLoadPolicy.get()
                  : EmbedLoadPolicySnapshot.defaults();
          embedLoadPolicyDialog.open(owner, current).ifPresent(pendingEmbedLoadPolicy::set);
        });
    return advanced;
  }

  private JPanel buildEmbedsAndPreviewsPanel(
      ImageEmbedControls image, LinkPreviewControls links, JButton advancedPolicyButton) {
    return EmbedsAndPreviewsPanelSupport.buildPanel(image, links, advancedPolicyButton);
  }

  private JPanel buildHistoryAndStoragePanel(LoggingControls logging, HistoryControls history) {
    return HistoryStoragePanelSupport.buildPanel(logging, history);
  }

  private NotificationRulesControls buildNotificationRulesControls(
      UiSettings current, List<AutoCloseable> closeables) {
    return NotificationRulesControlsSupport.buildControls(
        current, closeables, notificationRuleTestExecutor);
  }

  private IrcEventNotificationControls buildIrcEventNotificationControls(
      List<IrcEventNotificationRule> initialRules) {
    IrcEventNotificationTableModel model = new IrcEventNotificationTableModel(initialRules);
    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(Math.max(22, table.getRowHeight()));
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.getTableHeader().setReorderingAllowed(false);
    // Force dialog-only editing flow (no inline cell editor).
    table.setDefaultEditor(Object.class, null);
    table.setDefaultEditor(Boolean.class, null);
    table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);

    TableColumn enabledCol =
        table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_ENABLED);
    enabledCol.setMaxWidth(80);
    enabledCol.setPreferredWidth(70);

    TableColumn eventCol =
        table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_EVENT);
    eventCol.setPreferredWidth(220);

    TableColumn sourceCol =
        table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_SOURCE_SUMMARY);
    sourceCol.setPreferredWidth(300);

    TableColumn channelCol =
        table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_CHANNEL_SUMMARY);
    channelCol.setPreferredWidth(240);

    TableColumn actionsCol =
        table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_ACTIONS_SUMMARY);
    actionsCol.setPreferredWidth(300);

    return new IrcEventNotificationControls(table, model);
  }

  private JPanel buildIrcEventNotificationsTab(IrcEventNotificationControls controls) {
    return IrcEventNotificationsTabSupport.buildTab(
        controls, dialog, this::promptIrcEventNotificationRuleDialog);
  }

  private IrcEventNotificationRule promptIrcEventNotificationRuleDialog(
      String title, IrcEventNotificationRule seed) {
    Window owner = dialog != null ? dialog : null;
    return IrcEventNotificationRuleDialogSupport.promptIrcEventNotificationRuleDialog(
        owner,
        title,
        seed,
        notificationSoundService,
        this::importNotificationSoundFileToRuntimeDir);
  }

  private JPanel buildNotificationsPanel(
      NotificationRulesControls notifications, IrcEventNotificationControls ircEventNotifications) {
    return NotificationsPanelSupport.buildPanel(
        notifications,
        buildIrcEventNotificationsTab(ircEventNotifications),
        dialog,
        this::promptNotificationRuleDialog,
        this::refreshNotificationRuleValidation);
  }

  private UserCommandAliasesControls buildUserCommandAliasesControls(
      List<UserCommandAlias> initial, boolean unknownCommandAsRawEnabled) {
    return UserCommandAliasesControlsSupport.buildControls(
        initial, unknownCommandAsRawEnabled, dialog);
  }

  private JPanel buildUserCommandsPanel(UserCommandAliasesControls controls) {
    return UserCommandsPanelSupport.buildPanel(controls);
  }

  private DiagnosticsControls buildDiagnosticsControls() {
    return DiagnosticsControlsSupport.buildControls(runtimeConfig);
  }

  private JPanel buildDiagnosticsPanel(DiagnosticsControls controls) {
    return DiagnosticsPanelSupport.buildPanel(controls);
  }

  private static List<String> parseMultiLineArgs(String text) {
    String raw = Objects.toString(text, "");
    if (raw.isBlank()) return List.of();

    List<String> out = new ArrayList<>();
    for (String line : raw.split("\\R")) {
      String arg = line != null ? line.trim() : "";
      if (!arg.isEmpty()) out.add(arg);
    }
    return List.copyOf(out);
  }

  private void attachNotificationRuleValidation(
      NotificationRulesControls notifications, JButton apply, JButton ok) {
    Runnable refresh =
        () -> {
          boolean valid = refreshNotificationRuleValidation(notifications);
          apply.setEnabled(valid);
          ok.setEnabled(valid);
        };

    notifications.model.addTableModelListener(e -> refresh.run());
    refresh.run();
  }

  private boolean refreshNotificationRuleValidation(NotificationRulesControls notifications) {
    ValidationError err = notifications.model.firstValidationError();
    if (err == null) {
      notifications.validationLabel.setText(" ");
      notifications.validationLabel.setVisible(false);
      return true;
    }
    notifications.validationLabel.setText(err.formatForInline());
    notifications.validationLabel.setVisible(true);
    return false;
  }

  private static Color errorForeground() {
    Color c = UIManager.getColor("Label.errorForeground");
    if (c != null) return c;
    c = UIManager.getColor("Component.errorColor");
    if (c != null) return c;
    c = UIManager.getColor("Component.error.outlineColor");
    if (c != null) return c;
    c = UIManager.getColor("Component.error.borderColor");
    if (c != null) return c;
    c = UIManager.getColor("Component.error.focusedBorderColor");
    if (c != null) return c;
    return new Color(180, 0, 0);
  }

  static AppearanceRollbackPlan planAppearanceRollback(
      String committedThemeId,
      String liveThemeId,
      UiSettings committedUi,
      UiSettings liveUi,
      boolean accentBusAvailable,
      ThemeAccentSettings committedAccent,
      ThemeAccentSettings liveAccent,
      boolean tweakBusAvailable,
      ThemeTweakSettings committedTweaks,
      ThemeTweakSettings liveTweaks,
      boolean chatThemeBusAvailable,
      ChatThemeSettings committedChatTheme,
      ChatThemeSettings liveChatTheme) {
    boolean themeChanged = !sameThemeInternal(committedThemeId, liveThemeId);
    boolean uiChanged = committedUi != null && !Objects.equals(committedUi, liveUi);
    boolean accentChanged = accentBusAvailable && !Objects.equals(committedAccent, liveAccent);
    boolean tweakChanged = tweakBusAvailable && !Objects.equals(committedTweaks, liveTweaks);
    boolean chatThemeChanged =
        chatThemeBusAvailable && !Objects.equals(committedChatTheme, liveChatTheme);

    boolean applyTheme = themeChanged;
    boolean applyAppearance = !applyTheme && (uiChanged || accentChanged || tweakChanged);
    boolean refreshChatStyles = !applyTheme && !applyAppearance && chatThemeChanged;
    return new AppearanceRollbackPlan(
        uiChanged,
        accentChanged,
        tweakChanged,
        chatThemeChanged,
        applyTheme,
        applyAppearance,
        refreshChatStyles);
  }

  static record AppearanceRollbackPlan(
      boolean restoreUiSettings,
      boolean restoreAccentSettings,
      boolean restoreTweakSettings,
      boolean restoreChatThemeSettings,
      boolean applyTheme,
      boolean applyAppearance,
      boolean refreshChatStyles) {
    boolean hasAnyWork() {
      return restoreUiSettings
          || restoreAccentSettings
          || restoreTweakSettings
          || restoreChatThemeSettings
          || applyTheme
          || applyAppearance
          || refreshChatStyles;
    }
  }

  private static String normalizeThemeIdInternal(String id) {
    return ThemeIdUtils.normalizeThemeId(id);
  }

  private static boolean sameThemeInternal(String a, String b) {
    return ThemeIdUtils.sameTheme(a, b);
  }

  private static boolean supportsFlatLafTweaksInternal(String themeId) {
    return ThemeIdUtils.isLikelyFlatTarget(themeId);
  }

  static void configureIconOnlyButton(JButton button, String iconName, String tooltip) {
    if (button == null) return;
    button.setText("");
    button.setIcon(SvgIcons.action(iconName, 16));
    button.setDisabledIcon(SvgIcons.actionDisabled(iconName, 16));
    button.setMargin(new Insets(2, 6, 2, 6));
    button.setToolTipText(tooltip);
    button.setFocusable(false);
  }

  private static JDialog createDialog(Window owner) {
    final JDialog d = new JDialog(owner, "Preferences", JDialog.ModalityType.APPLICATION_MODAL);
    d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    return d;
  }

  static JSpinner numberSpinner(
      int value, int min, int max, int step, List<AutoCloseable> closeables) {
    JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, step));
    AutoCloseable ac = MouseWheelDecorator.decorateNumberSpinner(s);
    if (ac != null) closeables.add(ac);
    return s;
  }

  private static JSpinner doubleSpinner(
      double value, double min, double max, double step, List<AutoCloseable> closeables) {
    JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, step));
    AutoCloseable ac = MouseWheelDecorator.decorateNumberSpinner(s);
    if (ac != null) closeables.add(ac);
    return s;
  }

  private static String toHex(Color c) {
    return SettingsColorSupport.toHex(c);
  }

  private static Color parseHexColor(String raw) {
    return SettingsColorSupport.parseHexColor(raw);
  }

  private static Color parseHexColorLenient(String raw) {
    return SettingsColorSupport.parseHexColorLenient(raw);
  }

  static String normalizeOptionalHexForApply(String raw, String fieldLabel) {
    return SettingsColorSupport.normalizeOptionalHexForApply(raw, fieldLabel);
  }

  private static Color contrastTextColor(Color bg) {
    return SettingsColorSupport.contrastTextColor(bg);
  }

  private static Color preferredPreviewBackground() {
    return SettingsColorSupport.preferredPreviewBackground();
  }

  private static Icon createColorSwatchIcon(Color color, int w, int h) {
    return SettingsColorSupport.createColorSwatchIcon(color, w, h);
  }

  private NotificationRule promptNotificationRuleDialog(String title, NotificationRule seed) {
    Window owner = dialog != null ? dialog : null;
    return NotificationRuleDialogSupport.promptNotificationRuleDialog(owner, title, seed);
  }

  private static Color showColorPickerDialog(
      Window owner, String title, Color initial, Color previewBackground) {
    return SettingsColorPickerDialogSupport.showColorPickerDialog(
        owner, title, initial, previewBackground);
  }

  private record TypingTreeIndicatorStyleOption(String id, String label) {}

  private record MatrixUserListNameDisplayModeOption(String id, String label) {}

  private record NetworkAdvancedControls(
      ProxyControls proxy,
      UserhostControls userhost,
      UserInfoEnrichmentControls enrichment,
      HeartbeatControls heartbeat,
      BouncerControls bouncer,
      JSpinner monitorIsonPollIntervalSeconds,
      JCheckBox trustAllTlsCertificates,
      JPanel networkPanel,
      JPanel userLookupsPanel) {}

  static final class SimpleDocListener implements DocumentListener {
    private final Runnable onChange;

    SimpleDocListener(Runnable onChange) {
      this.onChange = onChange;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      onChange.run();
    }
  }

  // ------------------------------
  // Filters UI (Step 6.2)
  // ------------------------------

  private FilterControls buildFilterControls(
      FilterSettings current, List<AutoCloseable> closeables) {
    return FilterControlsSupport.buildControls(
        current,
        dialog,
        closeables,
        filterSettingsBus,
        runtimeConfig,
        targetCoordinator,
        transcriptRebuildService);
  }

  private JPanel buildFiltersPanel(FilterControls c) {
    return FiltersPanelSupport.buildPanel(c);
  }

  private static final class RxDebouncedEdtTrigger implements AutoCloseable {
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Subject<Long> signals = PublishSubject.<Long>create().toSerialized();
    private final Disposable subscription;

    private RxDebouncedEdtTrigger(long debounceMs, Runnable action) {
      Runnable safeAction = action == null ? () -> {} : action;
      this.subscription =
          signals
              .debounce(Math.max(0L, debounceMs), TimeUnit.MILLISECONDS)
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  seq -> {
                    if (closed.get()) return;
                    if (seq.longValue() != sequence.get()) return;
                    try {
                      safeAction.run();
                    } catch (Exception ignored) {
                    }
                  },
                  err -> {});
    }

    void trigger() {
      if (closed.get()) return;
      signals.onNext(sequence.incrementAndGet());
    }

    void cancelPending() {
      sequence.incrementAndGet();
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) return;
      sequence.incrementAndGet();
      try {
        subscription.dispose();
      } catch (Exception ignored) {
      }
      try {
        signals.onComplete();
      } catch (Exception ignored) {
      }
    }
  }

  private void applyFilterSettingsFromUi(FilterControls c) {
    FilterSettingsApplySupport.applyFromUi(
        c, filterSettingsBus, runtimeConfig, targetCoordinator, transcriptRebuildService);
  }
}
