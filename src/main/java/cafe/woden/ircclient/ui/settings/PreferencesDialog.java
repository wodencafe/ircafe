package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.commands.HexChatCommandAliasImporter;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.irc.PircbotxBotFactory;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.FilterScopeOverride;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.NotificationRule;
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
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettings;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import cafe.woden.ircclient.ui.filter.FilterRuleEntryDialog;
import cafe.woden.ircclient.ui.filter.FilterSettings;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.DialogCloseableScopeDecorator;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import cafe.woden.ircclient.util.VirtualThreads;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
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
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import net.miginfocom.swing.MigLayout;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class PreferencesDialog {
  private static final String DENSITY_TOOLTIP =
      "Changes the overall UI spacing / row height. Changes preview live; Apply/OK saves.";
  private static final String CORNER_RADIUS_TOOLTIP =
      "Controls rounded corner radius for buttons/fields/etc. Changes preview live; Apply/OK saves.";
  private static final String FLAT_ONLY_TOOLTIP = "Available for FlatLaf-based themes only.";
  private static final String UI_FONT_OVERRIDE_TOOLTIP =
      "Overrides the global Swing UI font family and size for controls, menus, tabs, and dialogs.";

  private final UiSettingsBus settingsBus;
  private final ThemeManager themeManager;
  private final ThemeAccentSettingsBus accentSettingsBus;
  private final ThemeTweakSettingsBus tweakSettingsBus;
  private final ChatThemeSettingsBus chatThemeSettingsBus;
  private final RuntimeConfigStore runtimeConfig;
  private final LogProperties logProps;
  private final NickColorSettingsBus nickColorSettingsBus;
  private final NickColorService nickColorService;
  private final NickColorOverridesDialog nickColorOverridesDialog;
  private final PircbotxIrcClientService ircClientService;
  private final FilterSettingsBus filterSettingsBus;
  private final TranscriptRebuildService transcriptRebuildService;
  private final ActiveTargetPort targetCoordinator;
  private final TrayService trayService;
  private final TrayNotificationService trayNotificationService;
  private final GnomeDbusNotificationBackend gnomeDbusBackend;
  private final NotificationSoundSettingsBus notificationSoundSettingsBus;
  private final PushySettingsBus pushySettingsBus;
  private final PushyNotificationService pushyNotificationService;
  private final IrcEventNotificationRulesBus ircEventNotificationRulesBus;
  private final UserCommandAliasesBus userCommandAliasesBus;
  private final NotificationSoundService notificationSoundService;
  private final ServerDialogs serverDialogs;

  private JDialog dialog;

  public PreferencesDialog(
      UiSettingsBus settingsBus,
      ThemeManager themeManager,
      ThemeAccentSettingsBus accentSettingsBus,
      ThemeTweakSettingsBus tweakSettingsBus,
      ChatThemeSettingsBus chatThemeSettingsBus,
      RuntimeConfigStore runtimeConfig,
      LogProperties logProps,
      NickColorSettingsBus nickColorSettingsBus,
      NickColorService nickColorService,
      NickColorOverridesDialog nickColorOverridesDialog,
      PircbotxIrcClientService ircClientService,
      FilterSettingsBus filterSettingsBus,
      TranscriptRebuildService transcriptRebuildService,
      ActiveTargetPort targetCoordinator,
      TrayService trayService,
      TrayNotificationService trayNotificationService,
      GnomeDbusNotificationBackend gnomeDbusBackend,
      NotificationSoundSettingsBus notificationSoundSettingsBus,
      PushySettingsBus pushySettingsBus,
      PushyNotificationService pushyNotificationService,
      IrcEventNotificationRulesBus ircEventNotificationRulesBus,
      UserCommandAliasesBus userCommandAliasesBus,
      NotificationSoundService notificationSoundService,
      ServerDialogs serverDialogs) {
    this.settingsBus = settingsBus;
    this.themeManager = themeManager;
    this.accentSettingsBus = accentSettingsBus;
    this.tweakSettingsBus = tweakSettingsBus;
    this.chatThemeSettingsBus = chatThemeSettingsBus;
    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;
    this.nickColorSettingsBus = nickColorSettingsBus;
    this.nickColorService = nickColorService;
    this.nickColorOverridesDialog = nickColorOverridesDialog;
    this.ircClientService = ircClientService;
    this.filterSettingsBus = filterSettingsBus;
    this.transcriptRebuildService = transcriptRebuildService;
    this.targetCoordinator = targetCoordinator;
    this.trayService = trayService;
    this.trayNotificationService = trayNotificationService;
    this.gnomeDbusBackend = gnomeDbusBackend;
    this.notificationSoundSettingsBus = notificationSoundSettingsBus;
    this.pushySettingsBus = pushySettingsBus;
    this.pushyNotificationService = pushyNotificationService;
    this.ircEventNotificationRulesBus = ircEventNotificationRulesBus;
    this.userCommandAliasesBus = userCommandAliasesBus;
    this.notificationSoundService = notificationSoundService;
    this.serverDialogs = serverDialogs;
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

    ThemeControls theme = buildThemeControls(current, themeLabelById);
    FontControls fonts = buildFontControls(current, closeables);
    ThemeAccentSettings initialAccent =
        accentSettingsBus != null
            ? accentSettingsBus.get()
            : new ThemeAccentSettings(
                UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);
    AccentControls accent = buildAccentControls(initialAccent);
    ThemeTweakSettings initialTweaks =
        tweakSettingsBus != null
            ? tweakSettingsBus.get()
            : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);
    TweakControls tweaks = buildTweakControls(initialTweaks, closeables);

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
    ChatThemeControls chatTheme = buildChatThemeControls(initialChatTheme);

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
    final javax.swing.Timer lafPreviewTimer = new javax.swing.Timer(140, null);
    lafPreviewTimer.setRepeats(false);
    final Runnable applyLafPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          if (themeManager == null) return;

          String sel = normalizeThemeIdInternal(String.valueOf(theme.combo.getSelectedItem()));
          if (sel.isBlank()) return;

          if (tweakSettingsBus != null) {
            DensityOption opt = (DensityOption) tweaks.density.getSelectedItem();
            String densityId = opt != null ? opt.id() : "auto";
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
    lafPreviewTimer.addActionListener(e -> applyLafPreview.run());
    final Runnable scheduleLafPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          lafPreviewTimer.restart();
        };

    final javax.swing.Timer chatPreviewTimer = new javax.swing.Timer(120, null);
    chatPreviewTimer.setRepeats(false);
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
    chatPreviewTimer.addActionListener(e -> applyChatPreview.run());
    final Runnable scheduleChatPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          chatPreviewTimer.restart();
        };

    final javax.swing.Timer fontPreviewTimer = new javax.swing.Timer(120, null);
    fontPreviewTimer.setRepeats(false);
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
    fontPreviewTimer.addActionListener(e -> applyFontPreview.run());
    final Runnable scheduleFontPreview =
        () -> {
          if (suppressLivePreview.get()) return;
          fontPreviewTimer.restart();
        };

    closeables.add(
        () -> {
          lafPreviewTimer.stop();
          chatPreviewTimer.stop();
          fontPreviewTimer.stop();
        });

    final Runnable restoreCommittedAppearance =
        () -> {
          if (themeManager == null) return;
          suppressLivePreview.set(true);
          try {
            lafPreviewTimer.stop();
            chatPreviewTimer.stop();
            fontPreviewTimer.stop();

            UiSettings ui = committedUiSettings.get();
            if (ui != null) {
              settingsBus.set(ui);
            }
            if (accentSettingsBus != null) {
              ThemeAccentSettings a = committedAccentSettings.get();
              accentSettingsBus.set(
                  a != null
                      ? a
                      : new ThemeAccentSettings(
                          UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH));
            }
            if (tweakSettingsBus != null) {
              ThemeTweakSettings tw = committedTweakSettings.get();
              tweakSettingsBus.set(
                  tw != null
                      ? tw
                      : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10));
            }
            if (chatThemeSettingsBus != null) {
              ChatThemeSettings ct = committedChatThemeSettings.get();
              chatThemeSettingsBus.set(
                  ct != null
                      ? ct
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
            }

            String committed = committedThemeId.get();
            if (java.util.Objects.equals(committed, lastPreviewThemeId.get())) {
              themeManager.applyAppearance(true);
            } else {
              themeManager.applyTheme(committed);
              lastPreviewThemeId.set(committed);
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
    NotificationSoundSettings soundSettings =
        notificationSoundSettingsBus != null
            ? notificationSoundSettingsBus.get()
            : new NotificationSoundSettings(true, BuiltInSound.NOTIF_1.name(), false, null);
    PushyProperties pushySettings =
        pushySettingsBus != null
            ? pushySettingsBus.get()
            : new PushyProperties(false, null, null, null, null, null, null, null);
    TrayControls trayControls =
        buildTrayControls(current, soundSettings, pushySettings, closeables);

    ImageEmbedControls imageEmbeds = buildImageEmbedControls(current, closeables);
    LinkPreviewControls linkPreviews = buildLinkPreviewControls(current);
    TimestampControls timestamps = buildTimestampControls(current);
    JComboBox<MemoryUsageDisplayMode> memoryUsageDisplayMode =
        buildMemoryUsageDisplayModeCombo(current);
    MemoryWarningControls memoryWarnings = buildMemoryWarningControls(current, closeables);

    JCheckBox presenceFolds = buildPresenceFoldsCheckbox(current);
    JCheckBox ctcpRequestsInActiveTarget = buildCtcpRequestsInActiveTargetCheckbox(current);
    CtcpAutoReplyControls ctcpAutoReplies = buildCtcpAutoReplyControls();
    JCheckBox typingIndicatorsSendEnabled = buildTypingIndicatorsSendCheckbox(current);
    JCheckBox typingIndicatorsReceiveEnabled = buildTypingIndicatorsReceiveCheckbox(current);
    JComboBox<TypingTreeIndicatorStyleOption> typingTreeIndicatorStyle =
        buildTypingTreeIndicatorStyleCombo(current);
    Ircv3CapabilitiesControls ircv3Capabilities = buildIrcv3CapabilitiesControls();
    NickColorControls nickColors = buildNickColorControls(owner, closeables);

    try {
      closeables.add(MouseWheelDecorator.decorateComboBoxSelection(memoryUsageDisplayMode));
    } catch (Exception ignored) {
    }

    HistoryControls history = buildHistoryControls(current, closeables);
    LoggingControls logging = buildLoggingControls(logProps, closeables);

    OutgoingColorControls outgoing = buildOutgoingColorControls(current);
    NetworkAdvancedControls network = buildNetworkAdvancedControls(current, closeables);
    ProxyControls proxy = network.proxy;
    UserhostControls userhost = network.userhost;
    UserInfoEnrichmentControls enrichment = network.enrichment;
    HeartbeatControls heartbeat = network.heartbeat;
    JSpinner monitorIsonPollIntervalSeconds = network.monitorIsonPollIntervalSeconds;
    JCheckBox trustAllTlsCertificates = network.trustAllTlsCertificates;

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

    JPanel appearancePanel = buildAppearancePanel(theme, accent, chatTheme, fonts, tweaks);
    JPanel memoryPanel = buildMemoryPanel(memoryUsageDisplayMode, memoryWarnings);
    JPanel startupPanel = buildStartupPanel(autoConnectOnStart);
    JPanel trayPanel = buildTrayNotificationsPanel(trayControls);
    JPanel chatPanel =
        buildChatPanel(presenceFolds, ctcpRequestsInActiveTarget, nickColors, timestamps, outgoing);
    JPanel ctcpRepliesPanel = buildCtcpRepliesPanel(ctcpAutoReplies);
    JPanel ircv3Panel =
        buildIrcv3CapabilitiesPanel(
            typingIndicatorsSendEnabled,
            typingIndicatorsReceiveEnabled,
            typingTreeIndicatorStyle,
            ircv3Capabilities);
    JPanel embedsPanel = buildEmbedsAndPreviewsPanel(imageEmbeds, linkPreviews);
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
          String densityIdV = densityOpt != null ? densityOpt.id() : "auto";
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
          boolean ctcpAutoRepliesEnabledV = ctcpAutoReplies.enabled.isSelected();
          boolean ctcpAutoReplyVersionEnabledV = ctcpAutoReplies.version.isSelected();
          boolean ctcpAutoReplyPingEnabledV = ctcpAutoReplies.ping.isSelected();
          boolean ctcpAutoReplyTimeEnabledV = ctcpAutoReplies.time.isSelected();
          boolean typingIndicatorsSendEnabledV = typingIndicatorsSendEnabled.isSelected();
          boolean typingIndicatorsReceiveEnabledV = typingIndicatorsReceiveEnabled.isSelected();
          String typingIndicatorsTreeStyleV =
              typingTreeIndicatorStyleValue(typingTreeIndicatorStyle);
          Map<String, Boolean> ircv3CapabilitiesV = ircv3Capabilities.snapshot();

          boolean nickColoringEnabledV = nickColors.enabled.isSelected();
          double nickColorMinContrastV = ((Number) nickColors.minContrast.getValue()).doubleValue();
          if (nickColorMinContrastV <= 0) nickColorMinContrastV = 3.0;

          int maxImageW = ((Number) imageEmbeds.maxWidth.getValue()).intValue();
          int maxImageH = ((Number) imageEmbeds.maxHeight.getValue()).intValue();

          int historyInitialLoadV = ((Number) history.initialLoadLines.getValue()).intValue();
          int historyPageSizeV = ((Number) history.pageSize.getValue()).intValue();
          int commandHistoryMaxSizeV =
              ((Number) history.commandHistoryMaxSize.getValue()).intValue();
          MemoryUsageDisplayMode memoryUsageDisplayModeV =
              memoryUsageDisplayMode.getSelectedItem() instanceof MemoryUsageDisplayMode mode
                  ? mode
                  : MemoryUsageDisplayMode.LONG;
          int memoryWarningNearMaxPercentV =
              ((Number) memoryWarnings.nearMaxPercent.getValue()).intValue();
          boolean memoryWarningTooltipEnabledV = memoryWarnings.tooltipEnabled.isSelected();
          boolean memoryWarningToastEnabledV = memoryWarnings.toastEnabled.isSelected();
          boolean memoryWarningPushyEnabledV = memoryWarnings.pushyEnabled.isSelected();
          boolean memoryWarningSoundEnabledV = memoryWarnings.soundEnabled.isSelected();
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

          UiSettings prev = settingsBus.get();
          boolean outgoingColorEnabledV = outgoing.enabled.isSelected();
          String outgoingHexV =
              UiSettings.normalizeHexOrDefault(outgoing.hex.getText(), prev.clientLineColor());
          outgoing.hex.setText(outgoingHexV);

          if (notifications.table.isEditing()) {
            try {
              notifications.table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          if (ircEventNotifications.table.isEditing()) {
            try {
              ircEventNotifications.table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          if (userCommands.table.isEditing()) {
            try {
              userCommands.table.getCellEditor().stopCellEditing();
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

          UserCommandAliasValidationError aliasErr = userCommands.model.firstValidationError();
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
              ircEventNotifications.model.snapshot();
          List<UserCommandAlias> userCommandAliasesV = userCommands.model.snapshot();
          boolean unknownCommandAsRawEnabledV = userCommands.unknownCommandAsRaw.isSelected();
          boolean diagnosticsAssertjSwingEnabledV = diagnostics.assertjSwingEnabled.isSelected();
          boolean diagnosticsAssertjSwingFreezeWatchdogEnabledV =
              diagnostics.assertjSwingFreezeWatchdogEnabled.isSelected();
          int diagnosticsAssertjSwingFreezeThresholdMsV =
              ((Number) diagnostics.assertjSwingFreezeThresholdMs.getValue()).intValue();
          if (diagnosticsAssertjSwingFreezeThresholdMsV < 500)
            diagnosticsAssertjSwingFreezeThresholdMsV = 500;
          if (diagnosticsAssertjSwingFreezeThresholdMsV > 120_000)
            diagnosticsAssertjSwingFreezeThresholdMsV = 120_000;
          int diagnosticsAssertjSwingWatchdogPollMsV =
              ((Number) diagnostics.assertjSwingWatchdogPollMs.getValue()).intValue();
          if (diagnosticsAssertjSwingWatchdogPollMsV < 100)
            diagnosticsAssertjSwingWatchdogPollMsV = 100;
          if (diagnosticsAssertjSwingWatchdogPollMsV > 10_000)
            diagnosticsAssertjSwingWatchdogPollMsV = 10_000;
          int diagnosticsAssertjSwingFallbackViolationReportMsV =
              ((Number) diagnostics.assertjSwingFallbackViolationReportMs.getValue()).intValue();
          if (diagnosticsAssertjSwingFallbackViolationReportMsV < 250) {
            diagnosticsAssertjSwingFallbackViolationReportMsV = 250;
          }
          if (diagnosticsAssertjSwingFallbackViolationReportMsV > 120_000) {
            diagnosticsAssertjSwingFallbackViolationReportMsV = 120_000;
          }
          boolean diagnosticsAssertjSwingOnIssuePlaySoundV =
              diagnostics.assertjSwingOnIssuePlaySound.isSelected();
          boolean diagnosticsAssertjSwingOnIssueShowNotificationV =
              diagnostics.assertjSwingOnIssueShowNotification.isSelected();
          boolean diagnosticsJhiccupEnabledV = diagnostics.jhiccupEnabled.isSelected();
          String diagnosticsJhiccupJarPathV =
              Objects.toString(diagnostics.jhiccupJarPath.getText(), "").trim();
          String diagnosticsJhiccupJavaCommandRawV =
              Objects.toString(diagnostics.jhiccupJavaCommand.getText(), "").trim();
          String diagnosticsJhiccupJavaCommandEffectiveV =
              diagnosticsJhiccupJavaCommandRawV.isEmpty()
                  ? "java"
                  : diagnosticsJhiccupJavaCommandRawV;
          List<String> diagnosticsJhiccupArgsV =
              parseMultiLineArgs(diagnostics.jhiccupArgs.getText());

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
                  timestampsEnabledV,
                  timestampFormatV,
                  timestampsIncludeChatMessagesV,
                  timestampsIncludePresenceMessagesV,
                  historyInitialLoadV,
                  historyPageSizeV,
                  commandHistoryMaxSizeV,
                  outgoingColorEnabledV,
                  outgoingHexV,
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
                  memoryWarningNearMaxPercentV,
                  memoryWarningTooltipEnabledV,
                  memoryWarningToastEnabledV,
                  memoryWarningPushyEnabledV,
                  memoryWarningSoundEnabledV,
                  notificationRulesV);

          boolean themeChanged = !next.theme().equalsIgnoreCase(prev.theme());

          settingsBus.set(next);

          if (accentSettingsBus != null) {
            accentSettingsBus.set(nextAccent);
          }
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
          runtimeConfig.rememberTrayNotificationBackend(next.trayNotificationBackendMode().token());

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
          if (pushySettingsBus != null) {
            pushySettingsBus.set(pushyNext);
          }
          runtimeConfig.rememberPushySettings(pushyNext);

          if (trayService != null) {
            trayService.applySettings();
          }
          runtimeConfig.rememberImageEmbedsEnabled(next.imageEmbedsEnabled());
          runtimeConfig.rememberImageEmbedsCollapsedByDefault(next.imageEmbedsCollapsedByDefault());
          runtimeConfig.rememberImageEmbedsMaxWidthPx(next.imageEmbedsMaxWidthPx());
          runtimeConfig.rememberImageEmbedsMaxHeightPx(next.imageEmbedsMaxHeightPx());
          runtimeConfig.rememberImageEmbedsAnimateGifs(next.imageEmbedsAnimateGifs());
          runtimeConfig.rememberLinkPreviewsEnabled(next.linkPreviewsEnabled());
          runtimeConfig.rememberLinkPreviewsCollapsedByDefault(
              next.linkPreviewsCollapsedByDefault());
          runtimeConfig.rememberPresenceFoldsEnabled(next.presenceFoldsEnabled());
          runtimeConfig.rememberCtcpRequestsInActiveTargetEnabled(
              next.ctcpRequestsInActiveTargetEnabled());
          runtimeConfig.rememberCtcpAutoRepliesEnabled(ctcpAutoRepliesEnabledV);
          runtimeConfig.rememberCtcpAutoReplyVersionEnabled(ctcpAutoReplyVersionEnabledV);
          runtimeConfig.rememberCtcpAutoReplyPingEnabled(ctcpAutoReplyPingEnabledV);
          runtimeConfig.rememberCtcpAutoReplyTimeEnabled(ctcpAutoReplyTimeEnabledV);
          runtimeConfig.rememberTypingIndicatorsEnabled(next.typingIndicatorsEnabled());
          runtimeConfig.rememberTypingIndicatorsReceiveEnabled(
              next.typingIndicatorsReceiveEnabled());
          runtimeConfig.rememberTypingTreeIndicatorStyle(next.typingIndicatorsTreeStyle());
          persistIrcv3Capabilities(ircv3CapabilitiesV);

          if (nickColorSettingsBus != null) {
            nickColorSettingsBus.set(
                new NickColorSettings(nickColoringEnabledV, nickColorMinContrastV));
          }
          runtimeConfig.rememberNickColoringEnabled(nickColoringEnabledV);
          runtimeConfig.rememberNickColorMinContrast(nickColorMinContrastV);
          runtimeConfig.rememberTimestampsEnabled(next.timestampsEnabled());
          runtimeConfig.rememberTimestampFormat(next.timestampFormat());
          runtimeConfig.rememberTimestampsIncludeChatMessages(next.timestampsIncludeChatMessages());
          runtimeConfig.rememberTimestampsIncludePresenceMessages(
              next.timestampsIncludePresenceMessages());

          runtimeConfig.rememberChatHistoryInitialLoadLines(next.chatHistoryInitialLoadLines());
          runtimeConfig.rememberChatHistoryPageSize(next.chatHistoryPageSize());
          runtimeConfig.rememberCommandHistoryMaxSize(next.commandHistoryMaxSize());

          applyFilterSettingsFromUi(filters);
          runtimeConfig.rememberChatLoggingEnabled(logging.enabled.isSelected());
          runtimeConfig.rememberChatLoggingLogSoftIgnoredLines(logging.logSoftIgnored.isSelected());
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

          runtimeConfig.rememberClientLineColorEnabled(next.clientLineColorEnabled());
          runtimeConfig.rememberClientLineColor(next.clientLineColor());

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
          runtimeConfig.rememberAppDiagnosticsAssertjSwingEnabled(diagnosticsAssertjSwingEnabledV);
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
          runtimeConfig.rememberAppDiagnosticsJhiccupJavaCommand(diagnosticsJhiccupJavaCommandRawV);
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
          runtimeConfig.rememberClientProxy(proxyCfg);
          NetProxyContext.configure(proxyCfg);
          runtimeConfig.rememberClientHeartbeat(heartbeatCfg);
          NetHeartbeatContext.configure(heartbeatCfg);
          if (ircClientService != null) {
            ircClientService.rescheduleActiveHeartbeats();
          }
          boolean trustAllTlsV = trustAllTlsCertificates.isSelected();
          runtimeConfig.rememberClientTlsTrustAllCertificates(trustAllTlsV);
          NetTlsContext.configure(trustAllTlsV);

          if (themeManager != null) {
            if (themeChanged || accentChanged || tweaksChanged) {
              // Full UI refresh (also triggers a chat restyle)
              themeManager.applyTheme(next.theme());
            } else if (chatThemeChanged) {
              // Only the transcript palette changed
              themeManager.refreshChatStyles();
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
    d.addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent e) {
            restoreCommittedAppearance.run();
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
          d.dispose();
        });
    cancel.addActionListener(
        e -> {
          restoreCommittedAppearance.run();
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

  /**
   * JTabbedPane's preferred size is normally the max of all tabs. That works for fixed-size tab
   * UIs, but for settings screens it often causes the dialog to open at the size of the "largest"
   * tab (even if the user never visits it).
   *
   * <p>This variant prefers the currently-selected tab, so the dialog can pack smaller for simple
   * pages and grow for larger ones.
   */
  private static final class DynamicTabbedPane extends JTabbedPane {
    @Override
    public Dimension getPreferredSize() {
      return computeSelectedSize(true);
    }

    @Override
    public Dimension getMinimumSize() {
      return computeSelectedSize(false);
    }

    private Dimension computeSelectedSize(boolean preferred) {
      Dimension base = preferred ? super.getPreferredSize() : super.getMinimumSize();
      if (getTabCount() == 0) return base;

      int maxW = 0;
      int maxH = 0;
      for (int i = 0; i < getTabCount(); i++) {
        java.awt.Component c = getComponentAt(i);
        if (c == null) continue;
        Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
        if (d == null) continue;
        maxW = Math.max(maxW, d.width);
        maxH = Math.max(maxH, d.height);
      }

      java.awt.Component selected = getSelectedComponent();
      if (selected == null) return base;
      Dimension sel = preferred ? selected.getPreferredSize() : selected.getMinimumSize();
      if (sel == null) return base;

      // base already includes tabs/header/insets; swap the "max tab" content for the selected tab
      // content.
      int w = base.width - maxW + sel.width;
      int h = base.height - maxH + sel.height;
      return new Dimension(Math.max(0, w), Math.max(0, h));
    }
  }

  private static void installDynamicTabSizing(JDialog d, JTabbedPane tabs, Window owner) {
    ChangeListener listener = e -> packClampAndKeepCenter(d, owner);
    tabs.addChangeListener(listener);
    packClampAndKeepCenter(d, owner);
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
    if (d == null) return;
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

  private static JPanel padSubTab(JComponent panel) {
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

  private static JLabel tabTitle(String text) {
    JLabel l = new JLabel(text);
    l.putClientProperty(FlatClientProperties.STYLE, "font:+4");
    Font f = l.getFont();
    if (f != null) {
      l.setFont(f.deriveFont(Font.BOLD, f.getSize2D() + 4f));
    }
    l.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    return l;
  }

  private static JLabel sectionTitle(String text) {
    JLabel l = new JLabel(text);
    l.putClientProperty(FlatClientProperties.STYLE, "font:+2");
    Font f = l.getFont();
    if (f != null) {
      l.setFont(f.deriveFont(Font.BOLD));
    }
    l.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
    return l;
  }

  private static JPanel captionPanel(String title, String layout, String columns, String rows) {
    return captionPanelWithPadding(title, layout, columns, rows, 6, 8, 8, 8);
  }

  private static JPanel captionPanelWithPadding(
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

  private static JTextArea helpText(String text) {
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

  private static JTextArea subtleInfoText() {
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

  private static JComponent wrapCheckBox(JCheckBox box, String labelText) {
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

  private static JButton whyHelpButton(String title, String message) {
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

  private ThemeControls buildThemeControls(UiSettings current, Map<String, String> themeLabelById) {
    // Ensure the currently configured theme is always present in the dropdown, even if it's a
    // custom LookAndFeel class name (or an IntelliJ themes-pack id).
    String curTheme = normalizeThemeIdInternal(current != null ? current.theme() : null);
    String matchKey = null;
    for (String k : themeLabelById.keySet()) {
      if (sameThemeInternal(k, curTheme)) {
        matchKey = k;
        break;
      }
    }

    Map<String, String> map = themeLabelById;
    if (matchKey == null && curTheme != null && !curTheme.isBlank()) {
      java.util.LinkedHashMap<String, String> tmp = new java.util.LinkedHashMap<>();
      tmp.put(curTheme, "Custom: " + curTheme);
      tmp.putAll(themeLabelById);
      map = tmp;
      matchKey = curTheme;
    }

    final Map<String, String> labelMap = map;

    JComboBox<String> theme = new JComboBox<>(labelMap.keySet().toArray(String[]::new));
    theme.setRenderer(
        (list, value, index, isSelected, cellHasFocus) -> {
          String key = value != null ? value : "";
          JLabel l = new JLabel(labelMap.getOrDefault(key, key));
          l.setOpaque(true);
          if (isSelected) {
            l.setBackground(list.getSelectionBackground());
            l.setForeground(list.getSelectionForeground());
          } else {
            l.setBackground(list.getBackground());
            l.setForeground(list.getForeground());
          }
          l.setBorder(null);
          return l;
        });
    theme.setSelectedItem(matchKey != null ? matchKey : curTheme);
    return new ThemeControls(theme);
  }

  private AccentControls buildAccentControls(ThemeAccentSettings current) {
    ThemeAccentSettings cur =
        current != null
            ? current
            : new ThemeAccentSettings(
                UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);

    JCheckBox enabled = new JCheckBox("Override theme accent");
    enabled.setToolTipText(
        "If enabled, your chosen accent is blended into the current theme. Changes preview live; Apply/OK saves.");
    enabled.setSelected(cur.enabled());

    // Presets keep this easy for normal users, but we still expose a hex field for power users.
    JComboBox<AccentPreset> preset = new JComboBox<>(AccentPreset.values());
    preset.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel c =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            AccentPreset p = (value instanceof AccentPreset ap) ? ap : AccentPreset.THEME_DEFAULT;
            c.setText(p.label);
            Color col = p.colorOrNull();
            if (col != null) {
              c.setIcon(new ColorSwatch(col, 12, 12));
            } else {
              c.setIcon(null);
            }
            return c;
          }
        });
    preset.setToolTipText("Quick accent presets. 'Custom…' opens a color picker.");

    JTextField hex = new JTextField(cur.accentColor() != null ? cur.accentColor() : "", 10);
    hex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#RRGGBB");

    JButton pick = new JButton("Pick…");
    JButton clear = new JButton("Clear");

    JSlider strength = new JSlider(0, 100, cur.strength());
    strength.setPaintTicks(true);
    strength.setMajorTickSpacing(25);
    strength.setMinorTickSpacing(5);
    strength.setSnapToTicks(false);
    strength.setToolTipText("0 = theme default, 100 = fully your chosen accent");

    // Tiny inline “pill” preview used next to the Accent label.
    JLabel chip = new JLabel();
    chip.setOpaque(true);
    chip.setFont(chip.getFont().deriveFont(Math.max(11f, chip.getFont().getSize2D() - 1f)));
    // FlatLaf styling for JLabel does not support borderWidth/borderColor style keys.
    // Use a single 'border:' style entry (insets, color, thickness, arc) to get a rounded pill.
    chip.putClientProperty(
        FlatClientProperties.STYLE,
        "border: 2,8,2,8, $Component.borderColor, 1, 999; background: $Panel.background;");

    Runnable updatePickIcon =
        () -> {
          Color c = parseHexColorLenient(hex.getText());
          if (c != null) {
            pick.setIcon(new ColorSwatch(c, 14, 14));
            pick.setText("");
            pick.setToolTipText(toHex(c));
          } else {
            pick.setIcon(null);
            pick.setText("Pick…");
            pick.setToolTipText("Pick an accent color");
          }
        };

    Runnable updateChip =
        () -> {
          boolean en = enabled.isSelected();

          // What color should we show?
          Color bg;
          String text;
          String tip;

          if (!en) {
            // Try to show the current theme’s accent color when override is off.
            bg = UIManager.getColor("Component.accentColor");
            if (bg == null) bg = UIManager.getColor("Component.focusColor");
            if (bg == null) bg = UIManager.getColor("Focus.color");
            if (bg == null) bg = UIManager.getColor("Actions.Blue");
            if (bg == null) bg = UIManager.getColor("Button.default.focusColor");
            if (bg == null) bg = parseHexColorLenient(UiProperties.DEFAULT_ACCENT_COLOR);
            if (bg == null) bg = new Color(0x2D6BFF);
            text = "Theme";
            tip = "Theme accent • " + strength.getValue() + "%";
          } else {
            String raw = hex.getText();
            raw = raw != null ? raw.trim() : "";
            Color chosen = parseHexColorLenient(raw);
            bg = chosen != null ? chosen : parseHexColorLenient(UiProperties.DEFAULT_ACCENT_COLOR);
            if (bg == null) bg = new Color(0x2D6BFF);

            AccentPreset p = (AccentPreset) preset.getSelectedItem();
            if (p == null)
              p = AccentPreset.fromHexOrCustom(ThemeAccentSettings.normalizeHexOrNull(raw));

            text =
                switch (p) {
                  case IRCAFE_COBALT -> "Cobalt";
                  case INDIGO -> "Indigo";
                  case VIOLET -> "Violet";
                  case CUSTOM -> "Custom";
                  case THEME_DEFAULT -> "Theme";
                };
            tip =
                "Accent override: "
                    + (chosen != null ? toHex(chosen) : "(invalid)")
                    + " • "
                    + strength.getValue()
                    + "%";
          }

          chip.setText(text);
          chip.setBackground(bg);
          chip.setForeground(contrastTextColor(bg));
          chip.setToolTipText(tip);
        };

    java.util.concurrent.atomic.AtomicBoolean adjusting =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicReference<AccentPreset> lastPreset =
        new java.util.concurrent.atomic.AtomicReference<>();

    Runnable syncPresetFromHex =
        () -> {
          if (adjusting.get()) return;
          if (!enabled.isSelected()) {
            adjusting.set(true);
            try {
              preset.setSelectedItem(AccentPreset.THEME_DEFAULT);
            } finally {
              adjusting.set(false);
            }
            lastPreset.set(AccentPreset.THEME_DEFAULT);
            return;
          }

          String norm = ThemeAccentSettings.normalizeHexOrNull(hex.getText());
          AccentPreset next = AccentPreset.fromHexOrCustom(norm);
          adjusting.set(true);
          try {
            preset.setSelectedItem(next);
          } finally {
            adjusting.set(false);
          }
          lastPreset.set(next);
        };

    // Initialize preset based on current value.
    if (!cur.enabled()) {
      preset.setSelectedItem(AccentPreset.THEME_DEFAULT);
      lastPreset.set(AccentPreset.THEME_DEFAULT);
    } else {
      String norm = ThemeAccentSettings.normalizeHexOrNull(cur.accentColor());
      AccentPreset init = AccentPreset.fromHexOrCustom(norm);
      preset.setSelectedItem(init);
      lastPreset.set(init);
    }

    Runnable applyEnabledState =
        () -> {
          boolean en = enabled.isSelected();
          hex.setEnabled(en);
          pick.setEnabled(en);
          clear.setEnabled(en);
          strength.setEnabled(en);
          if (!en) {
            pick.setIcon(null);
            pick.setText("Pick…");
          } else {
            updatePickIcon.run();
          }
          updateChip.run();
        };

    pick.addActionListener(
        e -> {
          Color initial = parseHexColorLenient(hex.getText());
          Color chosen =
              showColorPickerDialog(
                  SwingUtilities.getWindowAncestor(pick),
                  "Choose Accent Color",
                  initial,
                  preferredPreviewBackground());
          if (chosen != null) {
            hex.setText(toHex(chosen));
            updatePickIcon.run();
            syncPresetFromHex.run();
            updateChip.run();
          }
        });

    clear.addActionListener(
        e -> {
          adjusting.set(true);
          try {
            enabled.setSelected(false);
            preset.setSelectedItem(AccentPreset.THEME_DEFAULT);
            hex.setText("");
          } finally {
            adjusting.set(false);
          }
          updatePickIcon.run();
          applyEnabledState.run();
          updateChip.run();
        });

    preset.addActionListener(
        e -> {
          if (adjusting.get()) return;
          AccentPreset p = (AccentPreset) preset.getSelectedItem();
          if (p == null) return;

          // Preserve prior preset to revert if user cancels "Custom…".
          AccentPreset prev =
              lastPreset.get() != null ? lastPreset.get() : AccentPreset.THEME_DEFAULT;
          boolean prevEnabled = enabled.isSelected();
          String prevHex = hex.getText();

          adjusting.set(true);
          try {
            if (p == AccentPreset.THEME_DEFAULT) {
              enabled.setSelected(false);
              // Keep hex around for convenience, but we won't apply it while disabled.
            } else if (p == AccentPreset.CUSTOM) {
              enabled.setSelected(true);
            } else {
              enabled.setSelected(true);
              if (p.hex != null) {
                hex.setText(p.hex);
              }
            }
          } finally {
            adjusting.set(false);
          }

          applyEnabledState.run();

          if (p == AccentPreset.CUSTOM) {
            Color initial = parseHexColorLenient(hex.getText());
            Color chosen =
                showColorPickerDialog(
                    SwingUtilities.getWindowAncestor(preset),
                    "Choose Accent Color",
                    initial,
                    preferredPreviewBackground());
            if (chosen == null) {
              // Revert selection if canceled.
              adjusting.set(true);
              try {
                preset.setSelectedItem(prev);
                enabled.setSelected(prevEnabled);
                hex.setText(prevHex);
              } finally {
                adjusting.set(false);
              }
              lastPreset.set(prev);
              applyEnabledState.run();
              updateChip.run();
              return;
            }
            hex.setText(toHex(chosen));
            updatePickIcon.run();
            syncPresetFromHex.run();
            updateChip.run();
          } else {
            // Keep "last" in sync for cancel logic.
            lastPreset.set(p);
            updateChip.run();
          }
        });

    enabled.addActionListener(e -> applyEnabledState.run());
    enabled.addActionListener(e -> syncPresetFromHex.run());
    enabled.addActionListener(e -> updateChip.run());
    hex.getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  updatePickIcon.run();
                  syncPresetFromHex.run();
                  updateChip.run();
                }));
    strength.addChangeListener(e -> updateChip.run());

    JPanel row = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]"));
    row.setOpaque(false);

    JPanel top = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]10[grow,fill]", "[]"));
    top.setOpaque(false);
    top.add(enabled, "growx");
    top.add(preset, "growx, wmin 0");
    row.add(top, "growx, wrap");

    JPanel bottom = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]6[]", "[]"));
    bottom.setOpaque(false);
    bottom.add(hex, "w 110!");
    bottom.add(pick);
    bottom.add(clear);
    row.add(bottom, "growx");

    applyEnabledState.run();
    updateChip.run();

    return new AccentControls(
        enabled,
        preset,
        hex,
        pick,
        clear,
        strength,
        chip,
        row,
        applyEnabledState,
        syncPresetFromHex,
        updateChip);
  }

  private ChatThemeControls buildChatThemeControls(ChatThemeSettings current) {
    JComboBox<ChatThemeSettings.Preset> preset = new JComboBox<>(ChatThemeSettings.Preset.values());
    preset.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel c =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            ChatThemeSettings.Preset p =
                (value instanceof ChatThemeSettings.Preset pr)
                    ? pr
                    : ChatThemeSettings.Preset.DEFAULT;
            c.setText(
                switch (p) {
                  case DEFAULT -> "Default (follow theme)";
                  case SOFT -> "Soft";
                  case ACCENTED -> "Accented";
                  case HIGH_CONTRAST -> "High contrast";
                });
            return c;
          }
        });
    preset.setSelectedItem(current != null ? current.preset() : ChatThemeSettings.Preset.DEFAULT);

    ColorField timestamp =
        buildOptionalColorField(
            current != null ? current.timestampColor() : null, "Pick a timestamp color");
    ColorField system =
        buildOptionalColorField(
            current != null ? current.systemColor() : null, "Pick a system/status color");
    ColorField mention =
        buildOptionalColorField(
            current != null ? current.mentionBgColor() : null, "Pick a mention highlight color");
    ColorField message =
        buildOptionalColorField(
            current != null ? current.messageColor() : null, "Pick a user message color");
    ColorField notice =
        buildOptionalColorField(
            current != null ? current.noticeColor() : null, "Pick a notice message color");
    ColorField action =
        buildOptionalColorField(
            current != null ? current.actionColor() : null, "Pick an action message color");
    ColorField error =
        buildOptionalColorField(
            current != null ? current.errorColor() : null, "Pick an error message color");
    ColorField presence =
        buildOptionalColorField(
            current != null ? current.presenceColor() : null, "Pick a presence message color");

    int ms = current != null ? current.mentionStrength() : 35;
    JSlider mentionStrength = new JSlider(0, 100, Math.max(0, Math.min(100, ms)));
    // Keep this compact (no tick labels) to avoid forcing scrollbars in the Appearance tab.
    mentionStrength.setMajorTickSpacing(25);
    mentionStrength.setMinorTickSpacing(5);
    mentionStrength.setPaintTicks(false);
    mentionStrength.setPaintLabels(false);
    mentionStrength.setToolTipText(
        "How strong the mention highlight is when using the preset highlight (0-100). Defaults to 35.");

    return new ChatThemeControls(
        preset,
        timestamp,
        system,
        mention,
        message,
        notice,
        action,
        error,
        presence,
        mentionStrength);
  }

  private ColorField buildOptionalColorField(String initialHex, String pickerTitle) {
    JTextField hex = new JTextField();
    hex.setColumns(10);
    hex.setToolTipText("Leave blank to use the preset/theme default.");

    String raw = initialHex != null ? initialHex.trim() : "";
    hex.setText(raw);

    JButton pick = new JButton("Pick");
    JButton clear = new JButton("Clear");

    Runnable updateIcon =
        () -> {
          Color c = parseHexColorLenient(hex.getText());
          if (c == null) {
            pick.setIcon(null);
            pick.setText("Pick");
          } else {
            pick.setText("");
            pick.setIcon(createColorSwatchIcon(c, 14, 14));
          }
        };

    pick.addActionListener(
        e -> {
          Color initial = parseHexColorLenient(hex.getText());
          if (initial == null) {
            initial = UIManager.getColor("Label.foreground");
          }
          Color chosen =
              showColorPickerDialog(
                  SwingUtilities.getWindowAncestor(pick),
                  pickerTitle,
                  initial,
                  preferredPreviewBackground());
          if (chosen != null) {
            hex.setText(toHex(chosen));
            updateIcon.run();
          }
        });

    clear.addActionListener(
        e -> {
          hex.setText("");
          updateIcon.run();
        });

    hex.getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                updateIcon.run();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                updateIcon.run();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                updateIcon.run();
              }
            });

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx", "[grow]6[]6[]"));
    panel.add(hex, "growx");
    panel.add(pick);
    panel.add(clear);

    updateIcon.run();
    return new ColorField(hex, pick, clear, panel, updateIcon);
  }

  private TweakControls buildTweakControls(
      ThemeTweakSettings current, List<AutoCloseable> closeables) {
    ThemeTweakSettings cur =
        current != null
            ? current
            : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);

    DensityOption[] opts =
        new DensityOption[] {
          new DensityOption("auto", "Auto (theme default)"),
          new DensityOption("compact", "Compact"),
          new DensityOption("cozy", "Cozy"),
          new DensityOption("spacious", "Spacious")
        };

    JComboBox<DensityOption> density = new JComboBox<>(opts);
    density.setToolTipText(DENSITY_TOOLTIP);
    String curId = cur.densityId();
    for (DensityOption o : opts) {
      if (o != null && o.id().equalsIgnoreCase(curId)) {
        density.setSelectedItem(o);
        break;
      }
    }

    JSlider cornerRadius = new JSlider(0, 20, cur.cornerRadius());
    cornerRadius.setPaintTicks(true);
    cornerRadius.setMajorTickSpacing(5);
    cornerRadius.setMinorTickSpacing(1);
    cornerRadius.setToolTipText(CORNER_RADIUS_TOOLTIP);

    JComboBox<String> uiFontFamily = new JComboBox<>(availableFontFamiliesSorted());
    uiFontFamily.setEditable(true);
    uiFontFamily.setSelectedItem(cur.uiFontFamily());
    uiFontFamily.setToolTipText(UI_FONT_OVERRIDE_TOOLTIP);
    applyEditableComboEditorPalette(uiFontFamily);
    uiFontFamily.addPropertyChangeListener(
        "UI", e -> applyEditableComboEditorPalette(uiFontFamily));
    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateComboBoxSelection(uiFontFamily));
      } catch (Exception ignored) {
      }
    }

    JSpinner uiFontSize = numberSpinner(cur.uiFontSize(), 8, 48, 1, closeables);
    uiFontSize.setToolTipText(UI_FONT_OVERRIDE_TOOLTIP);

    JCheckBox uiFontOverrideEnabled = new JCheckBox("Override system UI font");
    uiFontOverrideEnabled.setSelected(cur.uiFontOverrideEnabled());
    uiFontOverrideEnabled.setToolTipText(UI_FONT_OVERRIDE_TOOLTIP);

    Runnable applyUiFontEnabledState =
        () -> {
          boolean enabled = uiFontOverrideEnabled.isSelected();
          uiFontFamily.setEnabled(enabled);
          uiFontSize.setEnabled(enabled);
        };
    applyUiFontEnabledState.run();

    return new TweakControls(
        density,
        cornerRadius,
        uiFontOverrideEnabled,
        uiFontFamily,
        uiFontSize,
        applyUiFontEnabledState);
  }

  private FontControls buildFontControls(UiSettings current, List<AutoCloseable> closeables) {
    JComboBox<String> fontFamily = new JComboBox<>(availableFontFamiliesSorted());
    fontFamily.setEditable(true);
    fontFamily.setSelectedItem(current.chatFontFamily());
    applyEditableComboEditorPalette(fontFamily);
    fontFamily.addPropertyChangeListener("UI", e -> applyEditableComboEditorPalette(fontFamily));

    // Allow scrolling through the font list while hovering with the mousewheel.
    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateComboBoxSelection(fontFamily));
      } catch (Exception ignored) {
        // best-effort
      }
    } else {
      try {
        MouseWheelDecorator.decorateComboBoxSelection(fontFamily);
      } catch (Exception ignored) {
        // best-effort
      }
    }

    // Two-column font preview:
    //   Left  = family name in the UI font (always readable)
    //   Right = short sample rendered in that family (when possible)
    Font baseFont = fontFamily.getFont();
    final String sampleText = "AaBbYyZz 0123";
    fontFamily.setRenderer(
        new ListCellRenderer<>() {
          private final JPanel panel = new JPanel(new BorderLayout(8, 0));
          private final JLabel left = new JLabel();
          private final JLabel right = new JLabel();

          {
            panel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            left.setOpaque(false);
            right.setOpaque(false);
            right.setHorizontalAlignment(SwingConstants.RIGHT);
            panel.add(left, BorderLayout.WEST);
            panel.add(right, BorderLayout.EAST);
            panel.setOpaque(true);
          }

          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<? extends String> list,
              String value,
              int index,
              boolean isSelected,
              boolean cellHasFocus) {
            String family = value != null ? value : "";

            // Colors
            Color bg;
            Color fg;
            if (list != null) {
              bg = isSelected ? list.getSelectionBackground() : list.getBackground();
              fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            } else {
              bg = UIManager.getColor("ComboBox.background");
              fg = UIManager.getColor("ComboBox.foreground");
            }
            panel.setBackground(bg);
            left.setForeground(fg);
            right.setForeground(fg);

            // Left column: always readable
            left.setText(family);
            left.setFont(baseFont);

            // Right column: sample preview (only if the family can actually render it)
            String preview = "";
            Font previewFont = baseFont;
            if (!family.isBlank()) {
              Font candidate = new Font(family, baseFont.getStyle(), baseFont.getSize());
              // If the requested font isn't available (editable entry), keep the UI default.
              if (candidate != null
                  && (candidate.getFamily().equalsIgnoreCase(family)
                      || candidate.getName().equalsIgnoreCase(family))) {
                if (candidate.canDisplayUpTo(sampleText) == -1) {
                  preview = sampleText;
                  previewFont = candidate;
                } else {
                  // If it can't render even the basic sample, don't show tofu squares.
                  preview = "";
                  previewFont = baseFont;
                }
              }
            }
            right.setText(preview);
            right.setFont(previewFont);

            return panel;
          }
        });

    JSpinner fontSize = numberSpinner(current.chatFontSize(), 8, 48, 1, closeables);

    return new FontControls(fontFamily, fontSize);
  }

  private static String[] availableFontFamiliesSorted() {
    String[] families =
        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Arrays.sort(families, String.CASE_INSENSITIVE_ORDER);
    return families;
  }

  private static void applyEditableComboEditorPalette(JComboBox<?> combo) {
    if (combo == null || !combo.isEditable()) return;
    javax.swing.ComboBoxEditor editor = combo.getEditor();
    if (editor == null) return;

    java.awt.Component editorComponent = editor.getEditorComponent();
    if (!(editorComponent instanceof JTextField field)) return;

    Color bg =
        firstUiColor("ComboBox.background", "TextField.background", "TextComponent.background");
    Color fg = firstUiColor("ComboBox.foreground", "TextField.foreground", "Label.foreground");
    Color selBg =
        firstUiColor(
            "ComboBox.selectionBackground",
            "TextComponent.selectionBackground",
            "List.selectionBackground");
    Color selFg =
        firstUiColor(
            "ComboBox.selectionForeground",
            "TextComponent.selectionForeground",
            "List.selectionForeground");

    if (bg != null) field.setBackground(asUiResource(bg));
    if (fg != null) {
      Color uiFg = asUiResource(fg);
      field.setForeground(uiFg);
      field.setCaretColor(uiFg);
    }
    if (selBg != null) field.setSelectionColor(asUiResource(selBg));
    if (selFg != null) field.setSelectedTextColor(asUiResource(selFg));
  }

  private static Color firstUiColor(String... keys) {
    if (keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      Color c = UIManager.getColor(key);
      if (c != null) return c;
    }
    return null;
  }

  private static Color asUiResource(Color c) {
    if (c == null || c instanceof javax.swing.plaf.ColorUIResource) return c;
    return new javax.swing.plaf.ColorUIResource(c);
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
      UiSettings current,
      NotificationSoundSettings soundSettings,
      PushyProperties pushySettings,
      List<AutoCloseable> closeables) {
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

    ExecutorService pushyTestExec = VirtualThreads.newSingleThreadExecutor("ircafe-pushy-test");
    closeables.add(() -> pushyTestExec.shutdownNow());
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

          pushyTestExec.submit(
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

    JPanel trayTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    trayTab.setOpaque(false);
    JPanel trayBehavior =
        captionPanel("Tray behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    trayBehavior.add(enabled, "growx");
    trayBehavior.add(closeToTray, "growx");
    trayBehavior.add(minimizeToTray, "growx");
    trayBehavior.add(startMinimized, "growx, wrap");
    trayTab.add(trayBehavior, "growx, wmin 0, wrap");
    trayTab.add(
        helpText(
            "Tray availability depends on your desktop environment. If tray support is unavailable, these options will have no effect."),
        "growx");

    JPanel notificationsTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    notificationsTab.setOpaque(false);
    JPanel notificationEvents =
        captionPanel("Notification events", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    notificationEvents.add(notifyHighlights, "growx");
    notificationEvents.add(notifyPrivateMessages, "growx");
    notificationEvents.add(notifyConnectionState, "growx");
    notificationsTab.add(notificationEvents, "growx, wmin 0, wrap");
    JPanel notificationBackendGroup =
        captionPanel("Delivery backend", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "[]");
    notificationBackendGroup.add(new JLabel("Mode:"));
    notificationBackendGroup.add(notificationBackend, "w 260!, wrap");
    notificationBackendGroup.add(
        helpText(
            "Auto tries native OS notifications first and falls back to two-slices.\n"
                + "Native only disables fallback. Two-slices only bypasses OS-native backends."),
        "span 2, growx");
    notificationsTab.add(notificationBackendGroup, "growx, wmin 0, wrap");
    JPanel notificationVisibility =
        captionPanel("Suppression and focus rules", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    notificationVisibility.add(notifyOnlyWhenUnfocused, "growx");
    notificationVisibility.add(notifyOnlyWhenMinimizedOrHidden, "growx");
    notificationVisibility.add(notifySuppressWhenTargetActive, "growx, wrap");
    notificationVisibility.add(new JSeparator(), "growx, gaptop 4");
    notificationVisibility.add(testNotification, "w 180!");
    notificationsTab.add(notificationVisibility, "growx, wmin 0, wrap");
    notificationsTab.add(
        helpText(
            "Desktop notifications are shown when your notification rules trigger (or for connection events, if enabled)."),
        "growx");

    JPanel soundsTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    soundsTab.setOpaque(false);
    JPanel soundsBehavior =
        captionPanel("Sound behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    soundsBehavior.add(notificationSoundsEnabled, "growx");
    soundsBehavior.add(notificationSoundUseCustom, "growx, wrap");
    soundsTab.add(soundsBehavior, "growx, wmin 0, wrap");
    JPanel customSound =
        captionPanel(
            "Custom sound file", "insets 0, fillx, wrap 4", "[right]8[grow,fill]8[]8[]", "[]");
    customSound.add(new JLabel("File:"));
    customSound.add(notificationSoundCustomPath, "growx, pushx, wmin 0");
    customSound.add(browseCustomSound, "w 110!");
    customSound.add(clearCustomSound, "w 80!, wrap");
    soundsTab.add(customSound, "growx, wmin 0, wrap");
    JPanel builtInSound =
        captionPanel("Built-in sound", "insets 0, fillx, wrap 3", "[right]8[grow,fill]8[]", "[]");
    builtInSound.add(new JLabel("Preset:"));
    builtInSound.add(notificationSound, "w 240!");
    builtInSound.add(testSound, "w 120!, wrap");
    soundsTab.add(builtInSound, "growx, wmin 0, wrap");

    Path cfg = runtimeConfig != null ? runtimeConfig.runtimeConfigPath() : null;
    Path base = cfg != null ? cfg.getParent() : null;
    if (base != null) {
      soundsTab.add(
          helpText(
              "Custom sounds are copied to: "
                  + base.resolve("sounds")
                  + "\nTip: Use small files (short MP3/WAV) for snappy notifications."),
          "growx");
    }

    JPanel pushyTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    pushyTab.setOpaque(false);

    JPanel pushyBasics =
        captionPanel("Pushy integration", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "[]");
    pushyBasics.add(pushyEnabled, "span 2, growx, wrap");
    pushyBasics.add(new JLabel("Endpoint:"));
    pushyBasics.add(pushyEndpoint, "growx, pushx, wmin 0, wrap");
    pushyBasics.add(new JLabel("API key:"));
    pushyBasics.add(pushyApiKey, "growx, pushx, wmin 0, wrap");
    pushyBasics.add(new JLabel("Title prefix:"));
    pushyBasics.add(pushyTitlePrefix, "growx, pushx, wmin 0, wrap");
    pushyTab.add(pushyBasics, "growx, wmin 0, wrap");

    JPanel pushyDestination =
        captionPanel("Destination", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "[]");
    pushyDestination.add(new JLabel("Target mode:"));
    pushyDestination.add(pushyTargetMode, "w 180!, wrap");
    pushyDestination.add(new JLabel("Target value:"));
    pushyDestination.add(pushyTargetValue, "growx, pushx, wmin 0, wrap");
    pushyDestination.add(
        helpText("Choose a destination type and enter the corresponding value."), "span 2, growx");
    pushyTab.add(pushyDestination, "growx, wmin 0, wrap");

    JPanel pushyTimeouts =
        captionPanel("Network timeouts", "insets 0, fillx, wrap 4", "[right]8[]20[right]8[]", "[]");
    pushyTimeouts.add(new JLabel("Connect (s):"));
    pushyTimeouts.add(pushyConnectTimeoutSeconds, "w 90!");
    pushyTimeouts.add(new JLabel("Read (s):"));
    pushyTimeouts.add(pushyReadTimeoutSeconds, "w 90!, wrap");
    pushyTab.add(pushyTimeouts, "growx, wmin 0, wrap");
    JPanel pushyActions =
        captionPanel("Validation & testing", "insets 0, fillx, wrap 2", "[]12[grow,fill]", "[]");
    pushyActions.add(pushyTest, "w 150!");
    pushyActions.add(pushyTestStatus, "growx, wmin 0, wrap");
    pushyActions.add(new JLabel(""));
    pushyActions.add(pushyValidationLabel, "growx, wmin 0");
    pushyTab.add(pushyActions, "growx, wmin 0, wrap");
    pushyTab.add(
        helpText(
            "Pushy notifications are triggered by matching IRC event rules in Notifications -> IRC Event Rules."),
        "growx");

    JPanel linuxTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    linuxTab.setOpaque(false);
    JPanel linuxGroup =
        captionPanel("Linux integration", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    linuxGroup.add(linuxDbusActions, "growx, wrap");
    if (!linux) {
      linuxGroup.add(helpText("Linux only."), "growx");
    } else if (!linuxActionsSupported) {
      linuxGroup.add(
          helpText(
              "Linux notification actions were not detected for this session.\n"
                  + "IRCafe will fall back to notify-send."),
          "growx");
    } else {
      linuxGroup.add(
          helpText(
              "Uses org.freedesktop.Notifications over D-Bus so clicking a notification can open IRCafe."),
          "growx");
    }
    linuxTab.add(linuxGroup, "growx, wmin 0");

    JTabbedPane subTabs = new JTabbedPane();
    subTabs.addTab("Tray", padSubTab(trayTab));
    subTabs.addTab("Desktop notifications", padSubTab(notificationsTab));
    subTabs.addTab("Sounds", padSubTab(soundsTab));
    subTabs.addTab("Pushy", padSubTab(pushyTab));
    subTabs.addTab("Linux / Advanced", padSubTab(linuxTab));

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    panel.setOpaque(false);
    panel.add(subTabs, "growx, wmin 0");
    return new TrayControls(
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
        pushyTestStatus,
        panel);
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

  private static void configureBuiltInSoundCombo(JComboBox<BuiltInSound> combo) {
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

  private Ircv3CapabilitiesControls buildIrcv3CapabilitiesControls() {
    Map<String, Boolean> persisted = runtimeConfig.readIrcv3Capabilities();

    LinkedHashMap<String, JCheckBox> checkboxes = new LinkedHashMap<>();
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1, hidemode 3", "[grow,fill]", "[]6[]"));
    panel.setOpaque(false);

    LinkedHashMap<String, List<String>> grouped = new LinkedHashMap<>();
    for (String cap : PircbotxBotFactory.requestableCapabilities()) {
      String key = normalizeIrcv3CapabilityKey(cap);
      if (key.isEmpty()) continue;
      grouped.computeIfAbsent(capabilityGroupKey(key), __ -> new ArrayList<>()).add(key);
    }

    for (Map.Entry<String, List<String>> group : grouped.entrySet()) {
      List<String> caps = group.getValue();
      if (caps == null || caps.isEmpty()) continue;
      List<String> orderedCaps = new ArrayList<>(caps);
      orderedCaps.sort(
          (a, b) -> {
            int oa = capabilitySortOrder(a);
            int ob = capabilitySortOrder(b);
            if (oa != ob) return Integer.compare(oa, ob);
            return capabilityDisplayLabel(a).compareToIgnoreCase(capabilityDisplayLabel(b));
          });

      JPanel groupPanel =
          new JPanel(
              new MigLayout(
                  "insets 6 8 8 8, fillx, wrap 2, hidemode 3",
                  "[grow,fill]12[grow,fill]",
                  "[]2[]"));
      groupPanel.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createTitledBorder(capabilityGroupTitle(group.getKey())),
              BorderFactory.createEmptyBorder(4, 6, 4, 6)));
      groupPanel.setOpaque(false);

      for (String key : orderedCaps) {
        JCheckBox cb = new JCheckBox(capabilityDisplayLabel(key));
        cb.setSelected(persisted.getOrDefault(key, Boolean.TRUE));
        cb.setToolTipText(capabilityTooltip(key));
        checkboxes.put(key, cb);

        JButton help = whyHelpButton(capabilityHelpTitle(key), capabilityHelpMessage(key));
        help.setToolTipText("What does this capability do in IRCafe?");

        JPanel row = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]4[]", "[]"));
        row.setOpaque(false);
        row.add(cb, "growx, wmin 0");
        row.add(help, "aligny center");

        groupPanel.add(row, "growx, wmin 0");
      }

      panel.add(groupPanel, "growx, wmin 0, wrap");
    }

    return new Ircv3CapabilitiesControls(checkboxes, panel);
  }

  private NickColorControls buildNickColorControls(Window owner, List<AutoCloseable> closeables) {
    NickColorSettings cur = (nickColorSettingsBus != null) ? nickColorSettingsBus.get() : null;
    boolean enabledSeed = cur == null || cur.enabled();
    double minContrastSeed = cur != null ? cur.minContrast() : 3.0;
    if (minContrastSeed <= 0) minContrastSeed = 3.0;

    JCheckBox enabled = new JCheckBox("Color nicknames (channels and PMs)");
    enabled.setSelected(enabledSeed);
    enabled.setToolTipText(
        "When enabled, IRCafe renders nicknames in deterministic colors (per nick),\n"
            + "adjusted to meet a minimum contrast ratio against the chat background.");

    JSpinner minContrast = doubleSpinner(minContrastSeed, 1.0, 21.0, 0.5, closeables);
    minContrast.setToolTipText(
        "Minimum contrast ratio against the chat background (WCAG-style).\n"
            + "Higher values are safer for readability but may push colors toward lighter/darker extremes.");

    JButton overrides = new JButton("Edit overrides...");
    overrides.setToolTipText(
        "Open the per-nick override editor. Overrides take precedence over the palette.");

    NickColorPreviewPanel preview = new NickColorPreviewPanel(nickColorService);

    Runnable updatePreview =
        () -> {
          boolean en = enabled.isSelected();
          double mc = ((Number) minContrast.getValue()).doubleValue();
          if (mc <= 0) mc = 3.0;
          minContrast.setEnabled(en);
          preview.updatePreview(en, mc);
        };

    enabled.addActionListener(e -> updatePreview.run());
    minContrast.addChangeListener(e -> updatePreview.run());

    overrides.addActionListener(
        e -> {
          if (nickColorOverridesDialog != null) {
            nickColorOverridesDialog.open(owner);
          }
          updatePreview.run();
        });

    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]8[nogrid]", "[]6[]6[]6[]"));
    panel.setOpaque(false);
    panel.add(enabled, "span 2, wrap");
    panel.add(new JLabel("Minimum contrast ratio:"));
    panel.add(minContrast, "w 110!, wrap");
    panel.add(overrides, "span 2, alignx left, wrap");
    panel.add(
        helpText(
            "Tip: If nick colors look too similar to the background, increase the contrast ratio.\n"
                + "Overrides always win over the palette."),
        "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Preview:"), "span 2, wrap");
    panel.add(preview, "span 2, growx");
    updatePreview.run();

    return new NickColorControls(enabled, minContrast, overrides, preview, panel);
  }

  private ImageEmbedControls buildImageEmbedControls(
      UiSettings current, List<AutoCloseable> closeables) {
    JCheckBox imageEmbeds = new JCheckBox("Enable inline image embeds (direct links)");
    imageEmbeds.setSelected(current.imageEmbedsEnabled());
    imageEmbeds.setToolTipText(
        "If enabled, IRCafe will download and render images from direct image URLs in chat.");

    JCheckBox imageEmbedsCollapsed = new JCheckBox("Collapse inline images by default");
    imageEmbedsCollapsed.setSelected(current.imageEmbedsCollapsedByDefault());
    imageEmbedsCollapsed.setToolTipText(
        "If enabled, newly inserted inline images start collapsed (header shown; click to expand).");
    imageEmbedsCollapsed.setEnabled(imageEmbeds.isSelected());
    JSpinner imageMaxWidth =
        numberSpinner(current.imageEmbedsMaxWidthPx(), 0, 4096, 10, closeables);
    imageMaxWidth.setToolTipText(
        "Maximum width for inline images (pixels).\n"
            + "If 0, IRCafe will only scale images down to fit the chat viewport.");
    imageMaxWidth.setEnabled(imageEmbeds.isSelected());
    JSpinner imageMaxHeight =
        numberSpinner(current.imageEmbedsMaxHeightPx(), 0, 4096, 10, closeables);
    imageMaxHeight.setToolTipText(
        "Maximum height for inline images (pixels).\n"
            + "If 0, IRCafe will only scale images down based on viewport width (and max width cap, if set).");
    imageMaxHeight.setEnabled(imageEmbeds.isSelected());

    JCheckBox animateGifs = new JCheckBox("Animate GIFs");
    animateGifs.setSelected(current.imageEmbedsAnimateGifs());
    animateGifs.setToolTipText("If disabled, animated GIFs render as a still image (first frame).");
    animateGifs.setEnabled(imageEmbeds.isSelected());

    imageEmbeds.addActionListener(
        e -> {
          boolean enabled = imageEmbeds.isSelected();
          imageEmbedsCollapsed.setEnabled(enabled);
          imageMaxWidth.setEnabled(enabled);
          imageMaxHeight.setEnabled(enabled);
          animateGifs.setEnabled(enabled);
        });

    JPanel imagePanel =
        new JPanel(
            new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]8[nogrid]", "[]4[]4[]4[]4[]"));
    imagePanel.setOpaque(false);
    imagePanel.add(imageEmbeds, "span 2, wrap");
    imagePanel.add(imageEmbedsCollapsed, "span 2, wrap");
    imagePanel.add(new JLabel("Max image width (px, 0 = no limit):"));
    imagePanel.add(imageMaxWidth, "w 110!");
    imagePanel.add(new JLabel("Max image height (px, 0 = no limit):"));
    imagePanel.add(imageMaxHeight, "w 110!");
    imagePanel.add(animateGifs, "span 2, wrap");

    return new ImageEmbedControls(
        imageEmbeds, imageEmbedsCollapsed, imageMaxWidth, imageMaxHeight, animateGifs, imagePanel);
  }

  private LinkPreviewControls buildLinkPreviewControls(UiSettings current) {
    JCheckBox linkPreviews = new JCheckBox("Enable link previews (OpenGraph cards)");
    linkPreviews.setSelected(current.linkPreviewsEnabled());
    linkPreviews.setToolTipText(
        "If enabled, IRCafe will fetch page metadata (title/description/image) and show a preview card under messages.\n"
            + "Note: this makes network requests to the linked sites.");

    JCheckBox linkPreviewsCollapsed = new JCheckBox("Collapse link previews by default");
    linkPreviewsCollapsed.setSelected(current.linkPreviewsCollapsedByDefault());
    linkPreviewsCollapsed.setToolTipText(
        "If enabled, newly inserted link previews start collapsed (header shown; click to expand).");
    linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected());
    linkPreviews.addActionListener(
        e -> linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected()));

    JPanel linkPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]4[]"));
    linkPanel.setOpaque(false);
    linkPanel.add(linkPreviews);
    linkPanel.add(linkPreviewsCollapsed);

    return new LinkPreviewControls(linkPreviews, linkPreviewsCollapsed, linkPanel);
  }

  private TimestampControls buildTimestampControls(UiSettings current) {
    JCheckBox enabled = new JCheckBox("Show timestamps");
    enabled.setSelected(current.timestampsEnabled());
    enabled.setToolTipText("Prefix transcript lines with a time like [12:34:56].");

    JTextField format = new JTextField(current.timestampFormat(), 16);
    format.setToolTipText("java.time DateTimeFormatter pattern (e.g., HH:mm:ss or h:mm a).");

    JCheckBox includeChatMessages = new JCheckBox("Include regular chat messages");
    includeChatMessages.setSelected(current.timestampsIncludeChatMessages());
    includeChatMessages.setToolTipText(
        "When enabled, timestamps are also shown on normal chat messages (not just status lines).");

    JCheckBox includePresenceMessages = new JCheckBox("Include presence / folded messages");
    includePresenceMessages.setSelected(current.timestampsIncludePresenceMessages());
    includePresenceMessages.setToolTipText(
        "When enabled, timestamps are shown for join/part/quit/nick presence lines and expanded fold details.");

    Runnable syncEnabled =
        () -> {
          boolean on = enabled.isSelected();
          format.setEnabled(on);
          includeChatMessages.setEnabled(on);
          includePresenceMessages.setEnabled(on);
        };
    enabled.addItemListener(e -> syncEnabled.run());
    syncEnabled.run();

    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]6[]6[]"));
    panel.setOpaque(false);
    panel.add(enabled);
    JPanel fmtRow = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[][grow,fill]", "[]"));
    fmtRow.setOpaque(false);
    fmtRow.add(new JLabel("Format"));
    fmtRow.add(format, "w 200!");
    panel.add(fmtRow);
    panel.add(includeChatMessages);
    panel.add(includePresenceMessages);

    return new TimestampControls(
        enabled, format, includeChatMessages, includePresenceMessages, panel);
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
    JSpinner historyInitialLoadLines =
        numberSpinner(current.chatHistoryInitialLoadLines(), 0, 10_000, 50, closeables);
    historyInitialLoadLines.setToolTipText(
        "How many logged lines to prefill into a transcript when you select a channel/query.\n"
            + "Set to 0 to disable history prefill.");

    JSpinner historyPageSize =
        numberSpinner(current.chatHistoryPageSize(), 50, 10_000, 50, closeables);
    historyPageSize.setToolTipText(
        "How many lines to fetch per click when you use 'Load older messages…' inside the transcript.");

    JSpinner commandHistoryMaxSize =
        numberSpinner(current.commandHistoryMaxSize(), 1, 500, 25, closeables);
    commandHistoryMaxSize.setToolTipText(
        "Max entries kept for Up/Down command history in the input bar.\n"
            + "This history is in-memory only; it does not persist across restarts.");

    JTextArea historyInfo =
        new JTextArea(
            "Chat history settings (requires chat logging to be enabled).\n"
                + "These affect how many messages are pulled from the database when opening a transcript or paging older history.");
    historyInfo.setEditable(false);
    historyInfo.setLineWrap(true);
    historyInfo.setWrapStyleWord(true);
    historyInfo.setOpaque(false);
    historyInfo.setFocusable(false);
    historyInfo.setBorder(null);
    historyInfo.setFont(UIManager.getFont("Label.font"));
    historyInfo.setForeground(UIManager.getColor("Label.foreground"));
    historyInfo.setColumns(48);

    JPanel historyPanel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    historyPanel.setOpaque(false);
    historyPanel.add(historyInfo, "span 2, growx, wmin 0, wrap");
    historyPanel.add(new JLabel("Initial load (lines):"));
    historyPanel.add(historyInitialLoadLines, "w 110!");
    historyPanel.add(new JLabel("Page size (Load older):"));
    historyPanel.add(historyPageSize, "w 110!");
    historyPanel.add(new JLabel("Input command history (max):"));
    historyPanel.add(commandHistoryMaxSize, "w 110!");

    return new HistoryControls(
        historyInitialLoadLines, historyPageSize, commandHistoryMaxSize, historyPanel);
  }

  private LoggingControls buildLoggingControls(
      LogProperties logProps, List<AutoCloseable> closeables) {
    boolean loggingEnabledCurrent = logProps != null && Boolean.TRUE.equals(logProps.enabled());
    boolean logSoftIgnoredCurrent =
        logProps == null || Boolean.TRUE.equals(logProps.logSoftIgnoredLines());
    boolean logPrivateMessagesCurrent =
        logProps == null || Boolean.TRUE.equals(logProps.logPrivateMessages());
    boolean savePrivateMessageListCurrent =
        logProps == null || Boolean.TRUE.equals(logProps.savePrivateMessageList());

    JCheckBox loggingEnabled = new JCheckBox("Enable chat logging (store messages to local DB)");
    loggingEnabled.setSelected(loggingEnabledCurrent);
    loggingEnabled.setToolTipText(
        "When enabled, IRCafe will persist chat messages to an embedded local database for history loading.\n"
            + "Privacy-first: this is OFF by default.\n\n"
            + "Note: enabling/disabling requires restarting IRCafe to take effect.");

    JCheckBox loggingSoftIgnore = new JCheckBox("Log soft-ignored (spoiler) lines");
    loggingSoftIgnore.setSelected(logSoftIgnoredCurrent);
    loggingSoftIgnore.setToolTipText(
        "If enabled, messages that are soft-ignored (spoiler-covered) are still stored,\n"
            + "and will re-load as spoiler-covered lines in history.");
    loggingSoftIgnore.setEnabled(loggingEnabled.isSelected());

    JCheckBox loggingPrivateMessages = new JCheckBox("Save private-message history");
    loggingPrivateMessages.setSelected(logPrivateMessagesCurrent);
    loggingPrivateMessages.setToolTipText(
        "If enabled, PM/query messages are stored in the local history database.\n"
            + "If disabled, only non-PM targets are persisted.");
    loggingPrivateMessages.setEnabled(loggingEnabled.isSelected());

    JCheckBox savePrivateMessageList = new JCheckBox("Save private-message chat list");
    savePrivateMessageList.setSelected(savePrivateMessageListCurrent);
    savePrivateMessageList.setToolTipText(
        "If enabled, PM/query targets are remembered and re-opened after reconnect/restart.\n"
            + "The per-server PM list is managed in Servers -> Edit -> Auto-Join.");

    boolean keepForeverCurrent = logProps == null || Boolean.TRUE.equals(logProps.keepForever());
    int retentionDaysCurrent =
        (logProps != null && logProps.retentionDays() != null)
            ? Math.max(0, logProps.retentionDays())
            : 0;

    JCheckBox keepForever = new JCheckBox("Keep chat history forever (no retention pruning)");
    keepForever.setSelected(keepForeverCurrent);
    keepForever.setToolTipText(
        "If enabled, IRCafe will never automatically delete old chat history.\n"
            + "If disabled, you can set a retention window in days to prune older rows.\n\n"
            + "Note: retention pruning runs only when logging is enabled and takes effect after restart.");

    JSpinner retentionDays = numberSpinner(retentionDaysCurrent, 0, 10_000, 1, closeables);
    retentionDays.setToolTipText(
        "Retention window in days (0 disables retention).\n"
            + "Only used when Keep forever is unchecked.\n\n"
            + "Note: applied on next restart.");

    String dbBaseNameCurrent =
        (logProps != null && logProps.hsqldb() != null)
            ? logProps.hsqldb().fileBaseName()
            : "ircafe-chatlog";
    boolean dbNextToConfigCurrent =
        logProps == null
            || (logProps.hsqldb() != null
                && Boolean.TRUE.equals(logProps.hsqldb().nextToRuntimeConfig()));

    JTextField dbBaseName = new JTextField(dbBaseNameCurrent, 18);
    dbBaseName.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "ircafe-chatlog");
    dbBaseName.setToolTipText(
        "Base filename for HSQLDB (no extension).\n"
            + "HSQLDB will create multiple files like .data/.script/.properties.");

    JCheckBox dbNextToConfig = new JCheckBox("Store DB next to runtime config file");
    dbNextToConfig.setSelected(dbNextToConfigCurrent);
    dbNextToConfig.setToolTipText(
        "If enabled, the DB files are stored alongside your runtime YAML config (recommended).\n"
            + "If disabled, IRCafe uses the default ~/.config/ircafe directory.");

    JTextArea loggingInfo =
        new JTextArea(
            "Logging settings are applied on the next restart.\n"
                + "Tip: You can enable logging first, restart, then history controls (Load older messages…) will appear when data exists.");
    loggingInfo.setEditable(false);
    loggingInfo.setLineWrap(true);
    loggingInfo.setWrapStyleWord(true);
    loggingInfo.setOpaque(false);
    loggingInfo.setFocusable(false);
    loggingInfo.setBorder(null);
    loggingInfo.setFont(UIManager.getFont("Label.font"));
    loggingInfo.setForeground(UIManager.getColor("Label.foreground"));
    loggingInfo.setColumns(48);
    Runnable updateRetentionUi =
        () -> {
          retentionDays.setEnabled(!keepForever.isSelected());
        };
    keepForever.addActionListener(e -> updateRetentionUi.run());

    Runnable updateLoggingEnabledState =
        () -> {
          boolean en = loggingEnabled.isSelected();
          loggingSoftIgnore.setEnabled(en);
          loggingPrivateMessages.setEnabled(en);
          dbBaseName.setEnabled(true);
          dbNextToConfig.setEnabled(true);
          updateRetentionUi.run();
        };
    loggingEnabled.addActionListener(e -> updateLoggingEnabledState.run());
    updateLoggingEnabledState.run();

    JButton managePmList = new JButton("Open Server Auto-Join Settings…");
    managePmList.setIcon(SvgIcons.action("settings", 16));
    managePmList.setDisabledIcon(SvgIcons.actionDisabled("settings", 16));
    managePmList.setEnabled(serverDialogs != null);
    managePmList.addActionListener(
        e -> {
          if (serverDialogs == null) return;
          Window owner = dialog != null ? dialog : SwingUtilities.getWindowAncestor(managePmList);
          serverDialogs.openManageServers(owner);
        });

    return new LoggingControls(
        loggingEnabled,
        loggingSoftIgnore,
        loggingPrivateMessages,
        savePrivateMessageList,
        managePmList,
        keepForever,
        retentionDays,
        dbBaseName,
        dbNextToConfig,
        loggingInfo);
  }

  private OutgoingColorControls buildOutgoingColorControls(UiSettings current) {
    JCheckBox outgoingColorEnabled = new JCheckBox("Use custom color for my outgoing messages");
    outgoingColorEnabled.setSelected(current.clientLineColorEnabled());
    outgoingColorEnabled.setToolTipText(
        "If enabled, IRCafe will render lines you send (locally echoed into chat) using a custom color.");

    JTextField outgoingColorHex =
        new JTextField(UiSettings.normalizeHexOrDefault(current.clientLineColor(), "#6AA2FF"), 10);
    outgoingColorHex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#RRGGBB");

    JLabel outgoingPreview = new JLabel();
    outgoingPreview.setOpaque(true);
    outgoingPreview.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
    outgoingPreview.setPreferredSize(new Dimension(120, 24));

    JButton outgoingPick = new JButton("Pick...");
    outgoingPick.addActionListener(
        e -> {
          Color currentColor = parseHexColor(outgoingColorHex.getText());
          if (currentColor == null) currentColor = parseHexColor(current.clientLineColor());
          if (currentColor == null) currentColor = UIManager.getColor("Label.foreground");
          if (currentColor == null) currentColor = Color.WHITE;

          Color chosen =
              showColorPickerDialog(
                  dialog,
                  "Choose Outgoing Message Color",
                  currentColor,
                  preferredPreviewBackground());
          if (chosen != null) {
            outgoingColorHex.setText(toHex(chosen));
          }
        });

    JPanel outgoingColorPanel =
        new JPanel(
            new MigLayout("insets 0, fillx, wrap 3", "[grow,fill]8[nogrid]8[nogrid]", "[]4[]"));
    outgoingColorPanel.setOpaque(false);
    outgoingColorPanel.add(outgoingColorEnabled, "span 3, wrap");
    outgoingColorPanel.add(outgoingColorHex, "w 110!");
    outgoingColorPanel.add(outgoingPick);
    outgoingColorPanel.add(outgoingPreview);

    Runnable updateOutgoingColorUi =
        () -> {
          boolean enabled = outgoingColorEnabled.isSelected();
          outgoingColorHex.setEnabled(enabled);
          outgoingPick.setEnabled(enabled);

          if (!enabled) {
            outgoingPreview.setOpaque(false);
            outgoingPreview.setText("");
            outgoingPreview.repaint();
            return;
          }

          Color c = parseHexColor(outgoingColorHex.getText());
          if (c != null) {
            outgoingPreview.setOpaque(true);
            outgoingPreview.setBackground(c);
            outgoingPreview.setText(toHex(c));
          } else {
            outgoingPreview.setOpaque(false);
            outgoingPreview.setText("Invalid");
          }
          outgoingPreview.repaint();
        };

    outgoingColorEnabled.addActionListener(e -> updateOutgoingColorUi.run());
    outgoingColorHex
        .getDocument()
        .addDocumentListener(new SimpleDocListener(updateOutgoingColorUi));
    updateOutgoingColorUi.run();

    return new OutgoingColorControls(
        outgoingColorEnabled, outgoingColorHex, outgoingPreview, outgoingColorPanel);
  }

  private NetworkAdvancedControls buildNetworkAdvancedControls(
      UiSettings current, List<AutoCloseable> closeables) {
    IrcProperties.Proxy p = NetProxyContext.settings();
    if (p == null) p = new IrcProperties.Proxy(false, "", 1080, "", "", true, 10_000, 30_000);
    // Network grew vertically as we added options. Keep it compact by splitting into sub-tabs.
    // Let the inner sub-tabs consume vertical space so this tab doesn't feel "short".
    JPanel networkPanel =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[grow,fill]"));
    JPanel userLookupsPanel =
        new JPanel(new MigLayout("insets 12, fillx, wrap 1, hidemode 3", "[grow,fill]", ""));

    // ---- Proxy tab ----
    JPanel proxyTab =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]"));
    proxyTab.setOpaque(false);

    JPanel proxyHeader = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    proxyHeader.setOpaque(false);
    proxyHeader.add(sectionTitle("SOCKS5 proxy"), "growx, wmin 0");
    proxyHeader.add(
        whyHelpButton(
            "SOCKS5 proxy",
            "When enabled, IRCafe routes IRC connections, link previews, embedded images, and file downloads through a SOCKS5 proxy.\n\n"
                + "Heads up: proxy credentials are stored in your runtime config file in plain text."),
        "align right");
    proxyTab.add(proxyHeader, "span 2, growx, wmin 0, wrap");

    JTextArea proxyBlurb = subtleInfoText();
    proxyBlurb.setText(
        "Routes IRC + embeds through SOCKS5. Use remote DNS if local DNS is blocked.");
    proxyTab.add(proxyBlurb, "span 2, growx, wmin 0, wrap");

    JCheckBox proxyEnabled = new JCheckBox("Use SOCKS5 proxy");
    proxyEnabled.setSelected(p.enabled());

    JTextField proxyHost = new JTextField(Objects.toString(p.host(), ""));
    proxyHost.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "127.0.0.1");

    int portDefault = (p.port() > 0 && p.port() <= 65535) ? p.port() : 1080;
    JSpinner proxyPort = numberSpinner(portDefault, 1, 65535, 1, closeables);

    JCheckBox proxyRemoteDns = new JCheckBox();
    proxyRemoteDns.setSelected(p.remoteDns());
    proxyRemoteDns.setToolTipText(
        "When enabled, IRCafe asks the proxy to resolve hostnames. Useful if local DNS is blocked.");
    JComponent proxyRemoteDnsRow = wrapCheckBox(proxyRemoteDns, "Proxy resolves DNS (remote DNS)");

    JTextField proxyUsername = new JTextField(Objects.toString(p.username(), ""));
    proxyUsername.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "(optional)");

    JPasswordField proxyPassword = new JPasswordField(Objects.toString(p.password(), ""));
    proxyPassword.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "(optional)");
    // FlatLaf: show the standard "reveal" (eye) button inside password fields.
    // Some FlatLaf versions prefer the STYLE flag; keep both for compatibility.
    proxyPassword.putClientProperty("JPasswordField.showRevealButton", true);
    proxyPassword.putClientProperty(FlatClientProperties.STYLE, "showRevealButton:true;");
    JButton clearPassword = new JButton("Clear");
    clearPassword.addActionListener(e -> proxyPassword.setText(""));

    int connectTimeoutSec = (int) Math.max(1, p.connectTimeoutMs() / 1000L);
    int readTimeoutSec = (int) Math.max(1, p.readTimeoutMs() / 1000L);
    JSpinner connectTimeoutSeconds = numberSpinner(connectTimeoutSec, 1, 300, 1, closeables);
    JSpinner readTimeoutSeconds = numberSpinner(readTimeoutSec, 1, 600, 1, closeables);

    JPanel passwordRow = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    passwordRow.setOpaque(false);
    passwordRow.add(proxyPassword, "growx, pushx, wmin 0");
    passwordRow.add(clearPassword);

    Runnable updateProxyEnabledState =
        () -> {
          boolean enabled = proxyEnabled.isSelected();
          proxyHost.setEnabled(enabled);
          proxyPort.setEnabled(enabled);
          proxyRemoteDns.setEnabled(enabled);
          proxyUsername.setEnabled(enabled);
          proxyPassword.setEnabled(enabled);
          clearPassword.setEnabled(enabled);
          connectTimeoutSeconds.setEnabled(enabled);
          readTimeoutSeconds.setEnabled(enabled);
        };

    Runnable validateProxyInputs =
        () -> {
          // FlatLaf outlines: highlight invalid fields without adding noisy labels.
          if (!proxyEnabled.isSelected()) {
            proxyHost.putClientProperty("JComponent.outline", null);
            proxyUsername.putClientProperty("JComponent.outline", null);
            proxyPassword.putClientProperty("JComponent.outline", null);
            return;
          }

          String host = Objects.toString(proxyHost.getText(), "").trim();
          proxyHost.putClientProperty("JComponent.outline", host.isBlank() ? "error" : null);

          String user = Objects.toString(proxyUsername.getText(), "").trim();
          String pass = new String(proxyPassword.getPassword()).trim();

          // SOCKS5 auth is username+password. If only one is provided, warn.
          boolean hasUser = !user.isBlank();
          boolean hasPass = !pass.isBlank();
          boolean mismatch = hasUser ^ hasPass;

          Object outline = mismatch ? "warning" : null;
          proxyUsername.putClientProperty("JComponent.outline", outline);
          proxyPassword.putClientProperty("JComponent.outline", outline);
        };

    proxyEnabled.addActionListener(
        e -> {
          updateProxyEnabledState.run();
          validateProxyInputs.run();
        });
    updateProxyEnabledState.run();

    proxyHost.getDocument().addDocumentListener(new SimpleDocListener(validateProxyInputs));
    proxyUsername.getDocument().addDocumentListener(new SimpleDocListener(validateProxyInputs));
    proxyPassword.getDocument().addDocumentListener(new SimpleDocListener(validateProxyInputs));
    validateProxyInputs.run();

    proxyTab.add(proxyEnabled, "span 2, wrap");
    proxyTab.add(new JLabel("Host:"));
    proxyTab.add(proxyHost, "growx, wmin 0");
    proxyTab.add(new JLabel("Port:"));
    proxyTab.add(proxyPort, "w 110!");
    proxyTab.add(new JLabel(""));
    proxyTab.add(proxyRemoteDnsRow, "growx, wmin 0");
    proxyTab.add(new JLabel("Username:"));
    proxyTab.add(proxyUsername, "growx, wmin 0");
    proxyTab.add(new JLabel("Password:"));
    proxyTab.add(passwordRow, "growx, wmin 0");
    proxyTab.add(new JLabel("Connect timeout (sec):"));
    proxyTab.add(connectTimeoutSeconds, "w 110!");
    proxyTab.add(new JLabel("Read timeout (sec):"));
    proxyTab.add(readTimeoutSeconds, "w 110!");

    // ---- TLS tab ----
    JPanel tlsTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]"));
    tlsTab.setOpaque(false);
    JPanel tlsHeader = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    tlsHeader.setOpaque(false);
    tlsHeader.add(sectionTitle("TLS / SSL"), "growx, wmin 0");
    tlsHeader.add(
        whyHelpButton(
            "TLS / SSL (Trust all certificates)",
            "This setting is intentionally dangerous. If enabled, IRCafe will accept any TLS certificate (expired, mismatched, self-signed, etc)\n"
                + "for IRC-over-TLS connections and for HTTPS fetching (link previews, embedded images, etc).\n\n"
                + "Only enable this if you understand the risk (MITM becomes trivial)."),
        "align right");
    tlsTab.add(tlsHeader, "growx, wmin 0, wrap");

    JTextArea tlsBlurb = subtleInfoText();
    tlsBlurb.setText(
        "If enabled, certificate validation is skipped (insecure). Only use for debugging.");
    tlsTab.add(tlsBlurb, "growx, wmin 0, wrap");

    JCheckBox trustAllTlsCertificates = new JCheckBox();
    trustAllTlsCertificates.setSelected(NetTlsContext.trustAllCertificates());
    JComponent trustAllTlsRow =
        wrapCheckBox(trustAllTlsCertificates, "Trust all TLS/SSL certificates (insecure)");
    tlsTab.add(trustAllTlsRow, "growx, wmin 0, wrap");

    // ---- Heartbeat tab ----
    JPanel heartbeatTab =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]"));
    heartbeatTab.setOpaque(false);
    JPanel heartbeatHeader = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    heartbeatHeader.setOpaque(false);
    heartbeatHeader.add(sectionTitle("Connection heartbeat"), "growx, wmin 0");
    heartbeatHeader.add(
        whyHelpButton(
            "Connection heartbeat",
            "IRCafe can detect 'silent' disconnects by monitoring inbound traffic.\n"
                + "If no IRC messages are received for the configured timeout, IRCafe will close the socket\n"
                + "and let the reconnect logic take over (if enabled).\n\n"
                + "Tip: If your network is very quiet, increase the timeout."),
        "align right");
    heartbeatTab.add(heartbeatHeader, "span 2, growx, wmin 0, wrap");

    JTextArea heartbeatBlurb = subtleInfoText();
    heartbeatBlurb.setText(
        "Detects silent disconnects by closing idle sockets so reconnect can kick in.");
    heartbeatTab.add(heartbeatBlurb, "span 2, growx, wmin 0, wrap");

    IrcProperties.Heartbeat hb = NetHeartbeatContext.settings();
    if (hb == null) hb = new IrcProperties.Heartbeat(true, 15_000, 360_000);

    JCheckBox heartbeatEnabled = new JCheckBox();
    heartbeatEnabled.setSelected(hb.enabled());
    JComponent heartbeatEnabledRow =
        wrapCheckBox(heartbeatEnabled, "Enable heartbeat / idle timeout detection");

    int hbCheckSec = (int) Math.max(1, hb.checkPeriodMs() / 1000L);
    int hbTimeoutSec = (int) Math.max(1, hb.timeoutMs() / 1000L);
    JSpinner heartbeatCheckPeriodSeconds = numberSpinner(hbCheckSec, 1, 600, 1, closeables);
    JSpinner heartbeatTimeoutSeconds = numberSpinner(hbTimeoutSec, 5, 7200, 5, closeables);

    Runnable updateHeartbeatEnabledState =
        () -> {
          boolean enabled = heartbeatEnabled.isSelected();
          heartbeatCheckPeriodSeconds.setEnabled(enabled);
          heartbeatTimeoutSeconds.setEnabled(enabled);
        };
    heartbeatEnabled.addActionListener(e -> updateHeartbeatEnabledState.run());
    updateHeartbeatEnabledState.run();

    heartbeatTab.add(heartbeatEnabledRow, "span 2, growx, wmin 0, wrap");
    heartbeatTab.add(new JLabel("Check period (sec):"));
    heartbeatTab.add(heartbeatCheckPeriodSeconds, "w 110!");
    heartbeatTab.add(new JLabel("Timeout (sec):"));
    heartbeatTab.add(heartbeatTimeoutSeconds, "w 110!");

    JTabbedPane networkTabs = new JTabbedPane();
    networkTabs.addTab("Proxy", padSubTab(proxyTab));
    networkTabs.addTab("TLS", padSubTab(tlsTab));
    networkTabs.addTab("Heartbeat", padSubTab(heartbeatTab));

    JPanel networkIntro =
        new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[grow,fill]6[]", "[]"));
    networkIntro.setOpaque(false);
    networkIntro.add(tabTitle("Network"), "growx, wmin 0");
    networkIntro.add(
        whyHelpButton(
            "Network settings",
            "These settings affect how IRCafe connects to networks and fetches external content (link previews, embedded images, etc).\n\n"
                + "Tip: Most users only touch Proxy. Leave TLS trust-all off unless you're debugging."),
        "align right");

    networkPanel.add(networkIntro, "growx, wmin 0, wrap");
    networkPanel.add(networkTabs, "grow, push, wmin 0");

    ProxyControls proxyControls =
        new ProxyControls(
            proxyEnabled,
            proxyHost,
            proxyPort,
            proxyRemoteDns,
            proxyUsername,
            proxyPassword,
            clearPassword,
            connectTimeoutSeconds,
            readTimeoutSeconds);

    HeartbeatControls heartbeatControls =
        new HeartbeatControls(
            heartbeatEnabled, heartbeatCheckPeriodSeconds, heartbeatTimeoutSeconds);
    userLookupsPanel.add(tabTitle("User lookups"), "growx, wrap");

    JPanel userLookupsIntro = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    userLookupsIntro.setOpaque(false);
    JTextArea userLookupsBlurb =
        helpText(
            "Optional fallbacks for account/away/host info (USERHOST / WHOIS), with conservative rate limits.");
    JButton userLookupsHelp =
        whyHelpButton(
            "Why do I need user lookups?",
            "Most modern IRC networks provide account and presence information via IRCv3 (e.g., account-tag, account-notify, away-notify, extended-join).\n\n"
                + "However, some networks (or some pieces of data) still require fallback lookups. IRCafe can optionally use USERHOST and (as a last resort) WHOIS to fill missing metadata.\n\n"
                + "If you're on an IRCv3-capable network and don't use hostmask-based ignore rules, you can usually leave these disabled.");
    userLookupsIntro.add(userLookupsBlurb, "growx, wmin 0");
    userLookupsIntro.add(userLookupsHelp, "align right");
    JPanel lookupPresetPanel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]"));
    lookupPresetPanel.setOpaque(false);

    JComboBox<LookupRatePreset> lookupPreset = new JComboBox<>(LookupRatePreset.values());
    lookupPreset.setSelectedItem(detectLookupRatePreset(current));

    JTextArea lookupPresetHint = subtleInfoText();

    Runnable updateLookupPresetHint =
        () -> {
          LookupRatePreset psel = (LookupRatePreset) lookupPreset.getSelectedItem();
          if (psel == null) psel = LookupRatePreset.CUSTOM;

          String msg;
          if (psel == LookupRatePreset.CONSERVATIVE) {
            msg = "Lowest traffic. Best for huge channels or strict networks.";
          } else if (psel == LookupRatePreset.BALANCED) {
            msg = "Recommended default. Good fill-in speed with low risk.";
          } else if (psel == LookupRatePreset.RAPID) {
            msg = "Faster fill-in. More commands on the wire (use with caution).";
          } else {
            msg = "Custom shows the tuning controls below.";
          }
          lookupPresetHint.setText(msg);
        };
    updateLookupPresetHint.run();

    lookupPresetPanel.add(new JLabel("Rate limit preset:"));
    lookupPresetPanel.add(lookupPreset, "w 220!");
    lookupPresetPanel.add(lookupPresetHint, "span 2, growx, wmin 0, wrap");

    JSpinner monitorIsonPollIntervalSeconds =
        numberSpinner(current.monitorIsonFallbackPollIntervalSeconds(), 5, 600, 5, closeables);
    monitorIsonPollIntervalSeconds.setToolTipText(
        "Polling interval for ISON monitor fallback when IRC MONITOR is unavailable.");
    lookupPresetPanel.add(new JLabel("MONITOR fallback poll (sec):"));
    lookupPresetPanel.add(monitorIsonPollIntervalSeconds, "w 110!, wrap");

    JPanel hostmaskPanel =
        new JPanel(
            new MigLayout("insets 8, fillx, wrap 2, hidemode 3", "[right]12[grow,fill]", ""));
    hostmaskPanel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Hostmask discovery"),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
    hostmaskPanel.setOpaque(false);

    JCheckBox userhostEnabled =
        new JCheckBox("Fill missing hostmasks using USERHOST (rate-limited)");
    userhostEnabled.setSelected(current.userhostDiscoveryEnabled());
    userhostEnabled.setToolTipText(
        "When enabled, IRCafe may send USERHOST only when hostmask-based ignore rules exist and some nicks are missing hostmasks.");

    JButton hostmaskHelp =
        whyHelpButton(
            "Why do I need hostmask discovery?",
            "Some ignore rules rely on hostmasks (nick!user@host).\n\n"
                + "On many networks, the full hostmask isn't included in NAMES and might not be available until additional lookups happen.\n\n"
                + "If you use hostmask-based ignore rules and some users show up without hostmasks, IRCafe can send rate-limited USERHOST commands to fill them in.\n\n"
                + "If you don't use hostmask-based ignores, you can usually leave this off.");

    JTextArea hostmaskSummary = subtleInfoText();
    hostmaskSummary.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

    JSpinner userhostMinIntervalSeconds =
        numberSpinner(current.userhostMinIntervalSeconds(), 1, 60, 1, closeables);
    userhostMinIntervalSeconds.setToolTipText(
        "Minimum seconds between USERHOST commands per server.");

    JSpinner userhostMaxPerMinute =
        numberSpinner(current.userhostMaxCommandsPerMinute(), 1, 60, 1, closeables);
    userhostMaxPerMinute.setToolTipText("Maximum USERHOST commands per minute per server.");

    JSpinner userhostNickCooldownMinutes =
        numberSpinner(current.userhostNickCooldownMinutes(), 1, 240, 1, closeables);
    userhostNickCooldownMinutes.setToolTipText(
        "Cooldown in minutes before re-querying the same nick.");

    JSpinner userhostMaxNicksPerCommand =
        numberSpinner(current.userhostMaxNicksPerCommand(), 1, 5, 1, closeables);
    userhostMaxNicksPerCommand.setToolTipText(
        "How many nicks to include per USERHOST command (servers typically allow up to 5).");

    JPanel hostmaskAdvanced =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    hostmaskAdvanced.setOpaque(false);
    hostmaskAdvanced.add(new JLabel("Min interval (sec):"));
    hostmaskAdvanced.add(userhostMinIntervalSeconds, "w 110!");
    hostmaskAdvanced.add(new JLabel("Max commands/min:"));
    hostmaskAdvanced.add(userhostMaxPerMinute, "w 110!");
    hostmaskAdvanced.add(new JLabel("Nick cooldown (min):"));
    hostmaskAdvanced.add(userhostNickCooldownMinutes, "w 110!");
    hostmaskAdvanced.add(new JLabel("Max nicks/command:"));
    hostmaskAdvanced.add(userhostMaxNicksPerCommand, "w 110!");
    JPanel enrichmentPanel =
        new JPanel(
            new MigLayout("insets 8, fillx, wrap 2, hidemode 3", "[right]12[grow,fill]", ""));
    enrichmentPanel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Roster enrichment (fallback)"),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
    enrichmentPanel.setOpaque(false);

    JCheckBox enrichmentEnabled =
        new JCheckBox("Best-effort roster enrichment using USERHOST (rate-limited)");
    enrichmentEnabled.setSelected(current.userInfoEnrichmentEnabled());
    enrichmentEnabled.setToolTipText(
        "When enabled, IRCafe may send USERHOST occasionally to enrich user info even when you don't have hostmask-based ignore rules.\n"
            + "This is a best-effort fallback for older networks.");

    JCheckBox enrichmentWhoisFallbackEnabled =
        new JCheckBox("Also use WHOIS fallback for account info (very slow)");
    enrichmentWhoisFallbackEnabled.setSelected(current.userInfoEnrichmentWhoisFallbackEnabled());
    enrichmentWhoisFallbackEnabled.setToolTipText(
        "When enabled, IRCafe may occasionally send WHOIS to learn account login state/name and away message.\n"
            + "This is slower and more likely to hit server rate limits. Recommended OFF by default.");

    JCheckBox enrichmentPeriodicRefreshEnabled =
        new JCheckBox("Periodic background refresh (slow scan)");
    enrichmentPeriodicRefreshEnabled.setSelected(
        current.userInfoEnrichmentPeriodicRefreshEnabled());
    enrichmentPeriodicRefreshEnabled.setToolTipText(
        "When enabled, IRCafe will periodically re-check a small number of nicks to detect changes.\n"
            + "Use conservative intervals to avoid extra network load.");

    JButton enrichmentHelp =
        whyHelpButton(
            "Why do I need roster enrichment?",
            "This is a best-effort fallback for older networks or edge cases where IRCv3 metadata isn't available.\n\n"
                + "IRCafe can use rate-limited USERHOST to fill missing user info. Optionally it can also use WHOIS (much slower) to learn account/away details.\n\n"
                + "On modern IRCv3 networks, you typically don't need this. Leave it OFF unless you have a specific reason.");

    JButton whoisHelp =
        whyHelpButton(
            "WHOIS fallback",
            "WHOIS is the slowest and noisiest fallback. It can provide account and away information when IRCv3 isn't available, but it is easy to hit server throttles.\n\n"
                + "Keep this OFF unless you're on a network that doesn't provide account info via IRCv3.");

    JButton refreshHelp =
        whyHelpButton(
            "Periodic background refresh",
            "This periodically re-probes a small number of users to detect changes (e.g., account/away state) on networks that don't push updates.\n\n"
                + "It's a slow scan by design: use high intervals and small batch sizes to avoid extra network load.");

    JTextArea enrichmentSummary = subtleInfoText();
    enrichmentSummary.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

    JSpinner enrichmentUserhostMinIntervalSeconds =
        numberSpinner(
            current.userInfoEnrichmentUserhostMinIntervalSeconds(), 1, 300, 1, closeables);
    enrichmentUserhostMinIntervalSeconds.setToolTipText(
        "Minimum seconds between USERHOST commands per server for enrichment.");

    JSpinner enrichmentUserhostMaxPerMinute =
        numberSpinner(
            current.userInfoEnrichmentUserhostMaxCommandsPerMinute(), 1, 60, 1, closeables);
    enrichmentUserhostMaxPerMinute.setToolTipText(
        "Maximum USERHOST commands per minute per server for enrichment.");

    JSpinner enrichmentUserhostNickCooldownMinutes =
        numberSpinner(
            current.userInfoEnrichmentUserhostNickCooldownMinutes(), 1, 1440, 1, closeables);
    enrichmentUserhostNickCooldownMinutes.setToolTipText(
        "Cooldown in minutes before re-querying the same nick via USERHOST (enrichment).\n"
            + "Higher values reduce network load.");

    JSpinner enrichmentUserhostMaxNicksPerCommand =
        numberSpinner(current.userInfoEnrichmentUserhostMaxNicksPerCommand(), 1, 5, 1, closeables);
    enrichmentUserhostMaxNicksPerCommand.setToolTipText(
        "How many nicks to include per USERHOST command (servers typically allow up to 5).\n"
            + "This applies to enrichment mode, separate from hostmask discovery.");

    JSpinner enrichmentWhoisMinIntervalSeconds =
        numberSpinner(current.userInfoEnrichmentWhoisMinIntervalSeconds(), 5, 600, 5, closeables);
    enrichmentWhoisMinIntervalSeconds.setToolTipText(
        "Minimum seconds between WHOIS commands per server (enrichment).\n"
            + "Keep this high to avoid throttling.");

    JSpinner enrichmentWhoisNickCooldownMinutes =
        numberSpinner(current.userInfoEnrichmentWhoisNickCooldownMinutes(), 1, 1440, 1, closeables);
    enrichmentWhoisNickCooldownMinutes.setToolTipText(
        "Cooldown in minutes before re-WHOIS'ing the same nick.");

    JSpinner enrichmentPeriodicRefreshIntervalSeconds =
        numberSpinner(
            current.userInfoEnrichmentPeriodicRefreshIntervalSeconds(), 30, 3600, 30, closeables);
    enrichmentPeriodicRefreshIntervalSeconds.setToolTipText(
        "How often to run a slow scan tick (seconds).\n"
            + "Higher values are safer. Example: 300 seconds (5 minutes).");

    JSpinner enrichmentPeriodicRefreshNicksPerTick =
        numberSpinner(
            current.userInfoEnrichmentPeriodicRefreshNicksPerTick(), 1, 20, 1, closeables);
    enrichmentPeriodicRefreshNicksPerTick.setToolTipText(
        "How many nicks to probe per periodic tick.\n" + "Keep this small (e.g., 1-3).");

    JPanel enrichmentAdvanced =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2",
                "[right]12[grow,fill]",
                "[]6[]6[]6[]10[]6[]6[]10[]6[]6[]"));
    enrichmentAdvanced.setOpaque(false);
    JLabel userhostHdr = new JLabel("USERHOST tuning");
    userhostHdr.setFont(userhostHdr.getFont().deriveFont(Font.BOLD));
    enrichmentAdvanced.add(userhostHdr, "span 2, growx, wmin 0, wrap");
    enrichmentAdvanced.add(new JLabel("Min interval (sec):"));
    enrichmentAdvanced.add(enrichmentUserhostMinIntervalSeconds, "w 110!");
    enrichmentAdvanced.add(new JLabel("Max cmd/min:"));
    enrichmentAdvanced.add(enrichmentUserhostMaxPerMinute, "w 110!");
    enrichmentAdvanced.add(new JLabel("Nick cooldown (min):"));
    enrichmentAdvanced.add(enrichmentUserhostNickCooldownMinutes, "w 110!");
    enrichmentAdvanced.add(new JLabel("Max nicks/cmd:"));
    enrichmentAdvanced.add(enrichmentUserhostMaxNicksPerCommand, "w 110!");
    JLabel whoisHdr = new JLabel("WHOIS tuning");
    whoisHdr.setFont(whoisHdr.getFont().deriveFont(Font.BOLD));
    enrichmentAdvanced.add(whoisHdr, "span 2, growx, wmin 0, wrap");
    enrichmentAdvanced.add(new JLabel("Min interval (sec):"));
    enrichmentAdvanced.add(enrichmentWhoisMinIntervalSeconds, "w 110!");
    enrichmentAdvanced.add(new JLabel("Nick cooldown (min):"));
    enrichmentAdvanced.add(enrichmentWhoisNickCooldownMinutes, "w 110!");
    JLabel refreshHdr = new JLabel("Periodic refresh tuning");
    refreshHdr.setFont(refreshHdr.getFont().deriveFont(Font.BOLD));
    enrichmentAdvanced.add(refreshHdr, "span 2, growx, wmin 0, wrap");
    enrichmentAdvanced.add(new JLabel("Interval (sec):"));
    enrichmentAdvanced.add(enrichmentPeriodicRefreshIntervalSeconds, "w 110!");
    enrichmentAdvanced.add(new JLabel("Nicks per tick:"));
    enrichmentAdvanced.add(enrichmentPeriodicRefreshNicksPerTick, "w 110!");
    Consumer<LookupRatePreset> applyLookupPreset =
        preset -> {
          if (preset == null || preset == LookupRatePreset.CUSTOM) return;

          switch (preset) {
            case CONSERVATIVE -> {
              userhostMinIntervalSeconds.setValue(10);
              userhostMaxPerMinute.setValue(2);
              userhostNickCooldownMinutes.setValue(60);
              userhostMaxNicksPerCommand.setValue(5);
              enrichmentUserhostMinIntervalSeconds.setValue(30);
              enrichmentUserhostMaxPerMinute.setValue(2);
              enrichmentUserhostNickCooldownMinutes.setValue(180);
              enrichmentUserhostMaxNicksPerCommand.setValue(5);
              enrichmentWhoisMinIntervalSeconds.setValue(120);
              enrichmentWhoisNickCooldownMinutes.setValue(240);
              enrichmentPeriodicRefreshIntervalSeconds.setValue(600);
              enrichmentPeriodicRefreshNicksPerTick.setValue(1);
            }
            case BALANCED -> {
              userhostMinIntervalSeconds.setValue(5);
              userhostMaxPerMinute.setValue(6);
              userhostNickCooldownMinutes.setValue(30);
              userhostMaxNicksPerCommand.setValue(5);
              enrichmentUserhostMinIntervalSeconds.setValue(15);
              enrichmentUserhostMaxPerMinute.setValue(4);
              enrichmentUserhostNickCooldownMinutes.setValue(60);
              enrichmentUserhostMaxNicksPerCommand.setValue(5);
              enrichmentWhoisMinIntervalSeconds.setValue(60);
              enrichmentWhoisNickCooldownMinutes.setValue(120);
              enrichmentPeriodicRefreshIntervalSeconds.setValue(300);
              enrichmentPeriodicRefreshNicksPerTick.setValue(2);
            }
            case RAPID -> {
              userhostMinIntervalSeconds.setValue(2);
              userhostMaxPerMinute.setValue(15);
              userhostNickCooldownMinutes.setValue(10);
              userhostMaxNicksPerCommand.setValue(5);
              enrichmentUserhostMinIntervalSeconds.setValue(5);
              enrichmentUserhostMaxPerMinute.setValue(10);
              enrichmentUserhostNickCooldownMinutes.setValue(15);
              enrichmentUserhostMaxNicksPerCommand.setValue(5);
              enrichmentWhoisMinIntervalSeconds.setValue(15);
              enrichmentWhoisNickCooldownMinutes.setValue(30);
              enrichmentPeriodicRefreshIntervalSeconds.setValue(60);
              enrichmentPeriodicRefreshNicksPerTick.setValue(3);
            }
            default -> {
              /* no-op */
            }
          }
        };
    Runnable updateHostmaskSummary =
        () -> {
          if (!userhostEnabled.isSelected()) {
            hostmaskSummary.setText("Disabled");
            return;
          }
          int minI = ((Number) userhostMinIntervalSeconds.getValue()).intValue();
          int maxM = ((Number) userhostMaxPerMinute.getValue()).intValue();
          int cdM = ((Number) userhostNickCooldownMinutes.getValue()).intValue();
          int maxN = ((Number) userhostMaxNicksPerCommand.getValue()).intValue();
          hostmaskSummary.setText(
              String.format(
                  "USERHOST ≤%d/min • min %ds • cooldown %dm • up to %d nicks/cmd",
                  maxM, minI, cdM, maxN));
        };

    Runnable updateEnrichmentSummary =
        () -> {
          if (!enrichmentEnabled.isSelected()) {
            enrichmentSummary.setText("Disabled");
            return;
          }

          int minI = ((Number) enrichmentUserhostMinIntervalSeconds.getValue()).intValue();
          int maxM = ((Number) enrichmentUserhostMaxPerMinute.getValue()).intValue();
          int cdM = ((Number) enrichmentUserhostNickCooldownMinutes.getValue()).intValue();
          int maxN = ((Number) enrichmentUserhostMaxNicksPerCommand.getValue()).intValue();

          String whois;
          if (enrichmentWhoisFallbackEnabled.isSelected()) {
            int whoisMin = ((Number) enrichmentWhoisMinIntervalSeconds.getValue()).intValue();
            int whoisCd = ((Number) enrichmentWhoisNickCooldownMinutes.getValue()).intValue();
            whois = String.format("WHOIS min %ds, cooldown %dm", whoisMin, whoisCd);
          } else {
            whois = "WHOIS off";
          }

          String refresh;
          if (enrichmentPeriodicRefreshEnabled.isSelected()) {
            int interval =
                ((Number) enrichmentPeriodicRefreshIntervalSeconds.getValue()).intValue();
            int nicks = ((Number) enrichmentPeriodicRefreshNicksPerTick.getValue()).intValue();
            refresh = String.format("Refresh %ds ×%d", interval, nicks);
          } else {
            refresh = "Refresh off";
          }

          enrichmentSummary.setText(
              String.format(
                  "USERHOST ≤%d/min • min %ds • cooldown %dm • up to %d nicks/cmd\n%s • %s",
                  maxM, minI, cdM, maxN, whois, refresh));
        };

    Runnable updateAllSummaries =
        () -> {
          updateHostmaskSummary.run();
          updateEnrichmentSummary.run();
        };

    Runnable updateHostmaskState =
        () -> {
          boolean enabled = userhostEnabled.isSelected();
          LookupRatePreset preset = (LookupRatePreset) lookupPreset.getSelectedItem();
          boolean custom = preset == LookupRatePreset.CUSTOM;

          boolean show = enabled && custom;
          hostmaskAdvanced.setVisible(show);

          userhostMinIntervalSeconds.setEnabled(show);
          userhostMaxPerMinute.setEnabled(show);
          userhostNickCooldownMinutes.setEnabled(show);
          userhostMaxNicksPerCommand.setEnabled(show);

          updateHostmaskSummary.run();
        };

    Runnable updateEnrichmentState =
        () -> {
          boolean enabled = enrichmentEnabled.isSelected();
          LookupRatePreset preset = (LookupRatePreset) lookupPreset.getSelectedItem();
          boolean custom = preset == LookupRatePreset.CUSTOM;

          enrichmentWhoisFallbackEnabled.setEnabled(enabled);
          enrichmentPeriodicRefreshEnabled.setEnabled(enabled);

          boolean showAdv = enabled && custom;
          enrichmentAdvanced.setVisible(showAdv);

          enrichmentUserhostMinIntervalSeconds.setEnabled(showAdv);
          enrichmentUserhostMaxPerMinute.setEnabled(showAdv);
          enrichmentUserhostNickCooldownMinutes.setEnabled(showAdv);
          enrichmentUserhostMaxNicksPerCommand.setEnabled(showAdv);

          boolean whoisEnabled = showAdv && enrichmentWhoisFallbackEnabled.isSelected();
          enrichmentWhoisMinIntervalSeconds.setEnabled(whoisEnabled);
          enrichmentWhoisNickCooldownMinutes.setEnabled(whoisEnabled);

          boolean periodicEnabled = showAdv && enrichmentPeriodicRefreshEnabled.isSelected();
          enrichmentPeriodicRefreshIntervalSeconds.setEnabled(periodicEnabled);
          enrichmentPeriodicRefreshNicksPerTick.setEnabled(periodicEnabled);

          updateEnrichmentSummary.run();
        };
    userhostEnabled.addActionListener(
        e -> {
          updateHostmaskState.run();
          updateAllSummaries.run();
          hostmaskPanel.revalidate();
          hostmaskPanel.repaint();
          userLookupsPanel.revalidate();
          userLookupsPanel.repaint();
        });

    enrichmentEnabled.addActionListener(
        e -> {
          updateEnrichmentState.run();
          updateAllSummaries.run();
          enrichmentPanel.revalidate();
          enrichmentPanel.repaint();
          userLookupsPanel.revalidate();
          userLookupsPanel.repaint();
        });

    enrichmentWhoisFallbackEnabled.addActionListener(
        e -> {
          updateEnrichmentState.run();
          updateAllSummaries.run();
        });
    enrichmentPeriodicRefreshEnabled.addActionListener(
        e -> {
          updateEnrichmentState.run();
          updateAllSummaries.run();
        });

    lookupPreset.addActionListener(
        e -> {
          LookupRatePreset psel = (LookupRatePreset) lookupPreset.getSelectedItem();
          if (psel != null && psel != LookupRatePreset.CUSTOM) {
            applyLookupPreset.accept(psel);
          }
          updateLookupPresetHint.run();
          updateHostmaskState.run();
          updateEnrichmentState.run();
          updateAllSummaries.run();
          hostmaskPanel.revalidate();
          hostmaskPanel.repaint();
          enrichmentPanel.revalidate();
          enrichmentPanel.repaint();
          userLookupsPanel.revalidate();
          userLookupsPanel.repaint();
        });

    javax.swing.event.ChangeListener summaryChange = e -> updateAllSummaries.run();
    userhostMinIntervalSeconds.addChangeListener(summaryChange);
    userhostMaxPerMinute.addChangeListener(summaryChange);
    userhostNickCooldownMinutes.addChangeListener(summaryChange);
    userhostMaxNicksPerCommand.addChangeListener(summaryChange);
    enrichmentUserhostMinIntervalSeconds.addChangeListener(summaryChange);
    enrichmentUserhostMaxPerMinute.addChangeListener(summaryChange);
    enrichmentUserhostNickCooldownMinutes.addChangeListener(summaryChange);
    enrichmentUserhostMaxNicksPerCommand.addChangeListener(summaryChange);
    enrichmentWhoisMinIntervalSeconds.addChangeListener(summaryChange);
    enrichmentWhoisNickCooldownMinutes.addChangeListener(summaryChange);
    enrichmentPeriodicRefreshIntervalSeconds.addChangeListener(summaryChange);
    enrichmentPeriodicRefreshNicksPerTick.addChangeListener(summaryChange);
    JPanel enrichmentWhoisRow =
        new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    enrichmentWhoisRow.setOpaque(false);
    enrichmentWhoisRow.add(enrichmentWhoisFallbackEnabled, "growx");
    enrichmentWhoisRow.add(whoisHelp, "align right");

    JPanel enrichmentRefreshRow =
        new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    enrichmentRefreshRow.setOpaque(false);
    enrichmentRefreshRow.add(enrichmentPeriodicRefreshEnabled, "growx");
    enrichmentRefreshRow.add(refreshHelp, "align right");
    hostmaskPanel.add(userhostEnabled, "growx");
    hostmaskPanel.add(hostmaskHelp, "align right, wrap");
    hostmaskPanel.add(hostmaskSummary, "span 2, growx, wmin 0, wrap");
    hostmaskPanel.add(hostmaskAdvanced, "span 2, growx, wrap, hidemode 3");

    enrichmentPanel.add(enrichmentEnabled, "growx");
    enrichmentPanel.add(enrichmentHelp, "align right, wrap");
    enrichmentPanel.add(enrichmentSummary, "span 2, growx, wmin 0, wrap");
    enrichmentPanel.add(enrichmentWhoisRow, "span 2, gapleft 18, growx, wrap");
    enrichmentPanel.add(enrichmentRefreshRow, "span 2, gapleft 18, growx, wrap");
    enrichmentPanel.add(enrichmentAdvanced, "span 2, growx, wrap, hidemode 3");
    hostmaskAdvanced.setVisible(false);
    enrichmentAdvanced.setVisible(false);
    updateHostmaskState.run();
    updateEnrichmentState.run();

    JTabbedPane lookupsTabs = new JTabbedPane();
    JPanel lookupsOverview =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]10[]"));
    lookupsOverview.setOpaque(false);
    lookupsOverview.add(userLookupsIntro, "growx, wmin 0, wrap");
    lookupsOverview.add(lookupPresetPanel, "growx, wmin 0, wrap");

    lookupsTabs.addTab("Overview", padSubTab(lookupsOverview));
    lookupsTabs.addTab("Hostmask discovery", padSubTab(hostmaskPanel));
    lookupsTabs.addTab("Roster enrichment", padSubTab(enrichmentPanel));

    userLookupsPanel.add(lookupsTabs, "growx, wmin 0, wrap");

    UserhostControls userhostControls =
        new UserhostControls(
            userhostEnabled,
            userhostMinIntervalSeconds,
            userhostMaxPerMinute,
            userhostNickCooldownMinutes,
            userhostMaxNicksPerCommand);

    UserInfoEnrichmentControls enrichmentControls =
        new UserInfoEnrichmentControls(
            enrichmentEnabled,
            enrichmentUserhostMinIntervalSeconds,
            enrichmentUserhostMaxPerMinute,
            enrichmentUserhostNickCooldownMinutes,
            enrichmentUserhostMaxNicksPerCommand,
            enrichmentWhoisFallbackEnabled,
            enrichmentWhoisMinIntervalSeconds,
            enrichmentWhoisNickCooldownMinutes,
            enrichmentPeriodicRefreshEnabled,
            enrichmentPeriodicRefreshIntervalSeconds,
            enrichmentPeriodicRefreshNicksPerTick);

    return new NetworkAdvancedControls(
        proxyControls,
        userhostControls,
        enrichmentControls,
        heartbeatControls,
        monitorIsonPollIntervalSeconds,
        trustAllTlsCertificates,
        networkPanel,
        userLookupsPanel);
  }

  private JPanel buildAppearancePanel(
      ThemeControls theme,
      AccentControls accent,
      ChatThemeControls chatTheme,
      FontControls fonts,
      TweakControls tweaks) {
    JPanel form =
        new JPanel(
            new MigLayout(
                "insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]6[]10[]6[]10[]6[]"));

    form.add(tabTitle("Appearance"), "span 2, growx, wmin 0, wrap");
    form.add(sectionTitle("Look & feel"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Theme"));
    form.add(theme.combo, "growx");

    JPanel accentLabel = new JPanel(new MigLayout("insets 0", "[]6[]", "[]"));
    accentLabel.setOpaque(false);
    accentLabel.add(new JLabel("Accent"));
    accentLabel.add(accent.chip);
    form.add(accentLabel);
    form.add(accent.panel, "growx");

    form.add(new JLabel("Accent strength"));
    form.add(accent.strength, "growx");

    form.add(new JLabel("Density"));
    form.add(tweaks.density, "growx");

    form.add(new JLabel("Corner radius"));
    form.add(tweaks.cornerRadius, "growx");

    JTextArea tweakHint = subtleInfoText();
    tweakHint.setText("Density and corner radius are available for FlatLaf-based themes.");
    form.add(new JLabel(""));
    form.add(tweakHint, "growx, wmin 0");

    form.add(sectionTitle("UI text"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Font override"));
    form.add(tweaks.uiFontOverrideEnabled, "growx");
    form.add(new JLabel("Font family"));
    form.add(tweaks.uiFontFamily, "growx");
    form.add(new JLabel("Font size"));
    form.add(tweaks.uiFontSize, "w 110!");

    JTextArea uiFontHint = subtleInfoText();
    uiFontHint.setText(
        "Applies globally to menus, dialogs, tabs, forms, and controls for all themes.");
    form.add(new JLabel(""));
    form.add(uiFontHint, "growx, wmin 0");

    form.add(sectionTitle("Chat transcript"), "span 2, growx, wmin 0, wrap");
    JTabbedPane chatTabs = new JTabbedPane();
    chatTabs.addTab("Palette", padSubTab(buildChatThemePaletteSubTab(chatTheme)));
    chatTabs.addTab("Message colors", padSubTab(buildChatMessageColorsSubTab(chatTheme)));
    form.add(chatTabs, "span 2, growx, wmin 0");

    JButton reset = new JButton("Reset to defaults");
    reset.setToolTipText(
        "Revert the appearance controls to default values. Changes preview live; Apply/OK saves.");
    reset.addActionListener(
        e -> {
          theme.combo.setSelectedItem("darcula");
          fonts.fontFamily.setSelectedItem("Monospaced");
          fonts.fontSize.setValue(12);
          accent.preset.setSelectedItem(AccentPreset.IRCAFE_COBALT);
          accent.enabled.setSelected(true);
          accent.hex.setText(UiProperties.DEFAULT_ACCENT_COLOR);
          accent.strength.setValue(UiProperties.DEFAULT_ACCENT_STRENGTH);
          // LAF tweak defaults
          for (int i = 0; i < tweaks.density.getItemCount(); i++) {
            DensityOption o = tweaks.density.getItemAt(i);
            if (o != null && "auto".equalsIgnoreCase(o.id())) {
              tweaks.density.setSelectedIndex(i);
              break;
            }
          }
          tweaks.cornerRadius.setValue(10);
          tweaks.uiFontOverrideEnabled.setSelected(false);
          tweaks.uiFontFamily.setSelectedItem(ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY);
          tweaks.uiFontSize.setValue(ThemeTweakSettings.DEFAULT_UI_FONT_SIZE);

          // Chat theme
          chatTheme.preset.setSelectedItem(ChatThemeSettings.Preset.DEFAULT);
          chatTheme.timestamp.hex.setText("");
          chatTheme.system.hex.setText("");
          chatTheme.mention.hex.setText("");
          chatTheme.message.hex.setText("");
          chatTheme.notice.hex.setText("");
          chatTheme.action.hex.setText("");
          chatTheme.error.hex.setText("");
          chatTheme.presence.hex.setText("");
          chatTheme.mentionStrength.setValue(35);
          chatTheme.timestamp.updateIcon.run();
          chatTheme.system.updateIcon.run();
          chatTheme.mention.updateIcon.run();
          chatTheme.message.updateIcon.run();
          chatTheme.notice.updateIcon.run();
          chatTheme.action.updateIcon.run();
          chatTheme.error.updateIcon.run();
          chatTheme.presence.updateIcon.run();

          accent.applyEnabledState.run();
          accent.syncPresetFromHex.run();
          tweaks.applyUiFontEnabledState.run();
        });
    form.add(new JLabel(""));
    form.add(reset, "alignx left");

    form.add(sectionTitle("Chat text"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Font family"));
    form.add(fonts.fontFamily, "growx");
    form.add(new JLabel("Font size"));
    form.add(fonts.fontSize, "w 110!");

    return form;
  }

  private JPanel buildChatThemePaletteSubTab(ChatThemeControls chatTheme) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    panel.setOpaque(false);

    panel.add(new JLabel("Chat theme preset"));
    panel.add(chatTheme.preset, "growx");

    panel.add(new JLabel("Timestamp color"));
    panel.add(chatTheme.timestamp.panel, "growx");

    panel.add(new JLabel("Mention highlight"));
    panel.add(chatTheme.mention.panel, "growx");

    panel.add(new JLabel("Mention strength"));
    panel.add(chatTheme.mentionStrength, "growx");

    panel.add(new JLabel(""));
    panel.add(
        helpText("Use Message colors when you want to override specific line types."),
        "growx, wmin 0");
    return panel;
  }

  private JPanel buildChatMessageColorsSubTab(ChatThemeControls chatTheme) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]6[]6[]6[]"));
    panel.setOpaque(false);

    panel.add(new JLabel("Server/system"));
    panel.add(chatTheme.system.panel, "growx");

    panel.add(new JLabel("User messages"));
    panel.add(chatTheme.message.panel, "growx");

    panel.add(new JLabel("Notice messages"));
    panel.add(chatTheme.notice.panel, "growx");

    panel.add(new JLabel("Action messages"));
    panel.add(chatTheme.action.panel, "growx");

    panel.add(new JLabel("Presence messages"));
    panel.add(chatTheme.presence.panel, "growx");

    panel.add(new JLabel("Error messages"));
    panel.add(chatTheme.error.panel, "growx");

    panel.add(new JLabel(""));
    panel.add(helpText("Leave any field blank to use the theme default."), "growx, wmin 0");

    return panel;
  }

  private JPanel buildMemoryPanel(
      JComboBox<MemoryUsageDisplayMode> memoryUsageDisplayMode,
      MemoryWarningControls memoryWarnings) {
    JPanel form =
        new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]"));
    form.add(tabTitle("Memory"), "span 2, growx, wmin 0, wrap");

    form.add(sectionTitle("Widget"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Memory usage widget"));
    form.add(memoryUsageDisplayMode, "growx");

    form.add(sectionTitle("Warnings"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Warn near max (%)"));
    form.add(memoryWarnings.nearMaxPercent, "w 110!");

    form.add(new JLabel("Warning actions"), "aligny top");
    JPanel warningActions =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]2[]2[]2[]"));
    warningActions.setOpaque(false);
    warningActions.add(memoryWarnings.tooltipEnabled, "growx");
    warningActions.add(memoryWarnings.toastEnabled, "growx");
    warningActions.add(memoryWarnings.pushyEnabled, "growx");
    warningActions.add(memoryWarnings.soundEnabled, "growx");
    form.add(warningActions, "growx");

    JTextArea hint = subtleInfoText();
    hint.setText(
        "Controls the memory widget in the top menu bar and threshold-triggered warning behavior.");
    form.add(new JLabel(""));
    form.add(hint, "growx, wmin 0");

    JButton reset = new JButton("Reset memory defaults");
    reset.setToolTipText("Reset memory mode and warning actions to defaults.");
    reset.addActionListener(
        e -> {
          memoryUsageDisplayMode.setSelectedItem(MemoryUsageDisplayMode.LONG);
          memoryWarnings.nearMaxPercent.setValue(5);
          memoryWarnings.tooltipEnabled.setSelected(true);
          memoryWarnings.toastEnabled.setSelected(false);
          memoryWarnings.pushyEnabled.setSelected(false);
          memoryWarnings.soundEnabled.setSelected(false);
        });
    form.add(new JLabel(""));
    form.add(reset, "alignx left");

    return form;
  }

  private JPanel buildStartupPanel(JCheckBox autoConnectOnStart) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]10[]6[]"));
    form.add(tabTitle("Startup"), "growx, wrap");

    form.add(sectionTitle("On launch"), "growx, wrap");
    form.add(autoConnectOnStart, "growx, wrap");
    form.add(
        helpText(
            "If enabled, IRCafe will connect to all configured servers automatically after the UI loads."),
        "growx, wrap");

    return form;
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
      NickColorControls nickColors,
      TimestampControls timestamps,
      OutgoingColorControls outgoing) {
    JPanel form =
        new JPanel(
            new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]"));

    form.add(tabTitle("Chat"), "span 2, growx, wmin 0, wrap");
    form.add(sectionTitle("Display"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Presence events"), "aligny top");
    form.add(presenceFolds, "alignx left");

    form.add(new JLabel("CTCP requests"), "aligny top");
    form.add(ctcpRequestsInActiveTarget, "alignx left");

    form.add(new JLabel("Nick colors"), "aligny top");
    form.add(nickColors.panel, "growx");

    form.add(new JLabel("Timestamps"), "aligny top");
    form.add(timestamps.panel, "growx");

    form.add(sectionTitle("Your messages"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Outgoing messages"), "aligny top");
    form.add(outgoing.panel, "growx");

    return form;
  }

  private CtcpAutoReplyControls buildCtcpAutoReplyControls() {
    JCheckBox enabled = new JCheckBox("Enable automatic CTCP replies");
    enabled.setSelected(runtimeConfig.readCtcpAutoRepliesEnabled(true));
    enabled.setToolTipText(
        "When enabled, IRCafe can auto-reply to private CTCP requests (VERSION, PING, TIME).");

    JCheckBox version = new JCheckBox("Reply to CTCP VERSION");
    version.setSelected(runtimeConfig.readCtcpAutoReplyVersionEnabled(true));
    version.setToolTipText("Respond with your client version.");

    JCheckBox ping = new JCheckBox("Reply to CTCP PING");
    ping.setSelected(runtimeConfig.readCtcpAutoReplyPingEnabled(true));
    ping.setToolTipText("Echo back the request payload so the sender can measure latency.");

    JCheckBox time = new JCheckBox("Reply to CTCP TIME");
    time.setSelected(runtimeConfig.readCtcpAutoReplyTimeEnabled(true));
    time.setToolTipText("Respond with your current local timestamp.");

    Runnable syncEnabled =
        () -> {
          boolean on = enabled.isSelected();
          version.setEnabled(on);
          ping.setEnabled(on);
          time.setEnabled(on);
        };
    enabled.addActionListener(e -> syncEnabled.run());
    syncEnabled.run();

    return new CtcpAutoReplyControls(enabled, version, ping, time);
  }

  private JPanel buildCtcpRepliesPanel(CtcpAutoReplyControls controls) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]8[]8[]"));

    form.add(tabTitle("CTCP Replies"), "growx, wmin 0, wrap");
    form.add(
        subtleInfoTextWith(
            "Control automatic replies to inbound private CTCP requests. "
                + "Outbound /ctcp commands are not affected."),
        "growx, wmin 0, wrap");
    form.add(controls.enabled, "growx, wrap");

    JPanel perCommand =
        new JPanel(new MigLayout("insets 8, fillx, wrap 1, hidemode 3", "[grow,fill]", "[]2[]2[]"));
    perCommand.setOpaque(false);
    perCommand.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Per-command replies"),
            BorderFactory.createEmptyBorder(4, 8, 6, 8)));
    perCommand.add(controls.version, "growx, wmin 0, gapleft 8, wrap");
    perCommand.add(controls.ping, "growx, wmin 0, gapleft 8, wrap");
    perCommand.add(controls.time, "growx, wmin 0, gapleft 8, wrap");
    form.add(perCommand, "growx, wmin 0, wrap");

    JButton enableDefaults = new JButton("Enable defaults");
    enableDefaults.setToolTipText("Enable automatic replies and turn on VERSION, PING, and TIME.");
    enableDefaults.addActionListener(
        e -> {
          controls.enabled.setSelected(true);
          controls.version.setSelected(true);
          controls.ping.setSelected(true);
          controls.time.setSelected(true);
        });

    JButton disableAll = new JButton("Disable all");
    disableAll.setToolTipText("Disable all automatic CTCP replies.");
    disableAll.addActionListener(
        e -> {
          controls.enabled.setSelected(false);
          controls.version.setSelected(false);
          controls.ping.setSelected(false);
          controls.time.setSelected(false);
        });

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actions.setOpaque(false);
    actions.add(enableDefaults);
    actions.add(disableAll);
    form.add(actions, "growx, wmin 0, wrap");

    form.add(
        helpText("If the top toggle is off, IRCafe will not send any automatic CTCP replies."),
        "growx, wmin 0, wrap");
    return form;
  }

  private JPanel buildIrcv3CapabilitiesPanel(
      JCheckBox typingIndicatorsSendEnabled,
      JCheckBox typingIndicatorsReceiveEnabled,
      JComboBox<TypingTreeIndicatorStyleOption> typingTreeIndicatorStyle,
      Ircv3CapabilitiesControls ircv3Capabilities) {
    JPanel form =
        new JPanel(
            new MigLayout(
                "insets 12, fill, wrap 1, hidemode 3", "[grow,fill]", "[]8[]8[grow,fill]"));

    form.add(tabTitle("IRCv3"), "growx, wmin 0, wrap");
    form.add(
        subtleInfoTextWith(
            "Typing and capability settings for modern IRCv3 features. Capability changes apply on reconnect."),
        "growx, wmin 0, wrap");

    JButton typingHelp =
        whyHelpButton(
            "Typing indicators",
            "What it is:\n"
                + "Typing indicators show when someone is actively typing or has paused.\n\n"
                + "Impact in IRCafe:\n"
                + "- Send: broadcasts your typing state to peers when supported.\n"
                + "- Display: shows incoming typing state in the active UI.\n\n"
                + "If disabled:\n"
                + "- Send disabled: IRCafe won't broadcast your typing state.\n"
                + "- Display disabled: IRCafe won't render incoming typing indicators.");
    typingHelp.setToolTipText("How typing indicators affect IRCafe");
    JPanel typingRow =
        new JPanel(
            new MigLayout("insets 8, fillx, wrap 1, hidemode 3", "[grow,fill]6[]", "[]2[]2[]"));
    typingRow.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Typing indicators"),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
    typingRow.setOpaque(false);
    typingRow.add(typingIndicatorsSendEnabled, "growx, wmin 0, split 2");
    typingRow.add(typingHelp, "aligny center");
    typingRow.add(typingIndicatorsReceiveEnabled, "growx, wmin 0");
    JPanel treeStyleRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[grow,fill]", "[]"));
    treeStyleRow.setOpaque(false);
    treeStyleRow.add(new JLabel("Server tree marker style"));
    treeStyleRow.add(typingTreeIndicatorStyle, "growx, wmin 180");
    typingRow.add(treeStyleRow, "growx, wmin 0");
    JTextArea typingImpact = subtleInfoText();
    typingImpact.setText(
        "Send controls your outbound typing state; Display controls incoming typing state from others.\n"
            + "Server tree marker style controls the channel typing activity indicator.");
    typingImpact.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
    JPanel typingTab =
        new JPanel(new MigLayout("insets 6, fillx, wrap 1, hidemode 3", "[grow,fill]", "[]6[]"));
    typingTab.setOpaque(false);
    typingTab.add(typingRow, "growx, wmin 0, wrap");
    typingTab.add(typingImpact, "growx, wmin 0, wrap");

    JPanel capabilityBlock =
        new JPanel(
            new MigLayout("insets 8, fill, wrap 1, hidemode 3", "[grow,fill]", "[]6[grow,fill]"));
    capabilityBlock.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Requested capabilities"),
            BorderFactory.createEmptyBorder(4, 6, 6, 6)));
    capabilityBlock.setOpaque(false);
    capabilityBlock.add(
        subtleInfoTextWith(
            "These capabilities are requested during CAP negotiation.\n"
                + "Changes apply on new connections or reconnect."),
        "growx, wmin 0, wrap");
    JScrollPane capabilityScroll =
        new JScrollPane(
            ircv3Capabilities.panel(),
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    capabilityScroll.setBorder(BorderFactory.createEmptyBorder());
    capabilityScroll.setViewportBorder(null);
    capabilityScroll.getVerticalScrollBar().setUnitIncrement(16);
    capabilityScroll.setPreferredSize(new Dimension(1, 320));
    capabilityBlock.add(capabilityScroll, "grow, push, wmin 0, hmin 180");

    JPanel capabilitiesTab =
        new JPanel(
            new MigLayout("insets 6, fill, wrap 1, hidemode 3", "[grow,fill]", "[grow,fill]"));
    capabilitiesTab.setOpaque(false);
    capabilitiesTab.add(capabilityBlock, "grow, push, wmin 0");

    JButton typingHeader = new JButton();
    typingHeader.setHorizontalAlignment(SwingConstants.LEFT);
    typingHeader.setFocusable(false);
    typingHeader.setMargin(new Insets(6, 10, 6, 10));
    typingHeader.setToolTipText("Show typing-indicator settings");

    JButton capabilitiesHeader = new JButton();
    capabilitiesHeader.setHorizontalAlignment(SwingConstants.LEFT);
    capabilitiesHeader.setFocusable(false);
    capabilitiesHeader.setMargin(new Insets(6, 10, 6, 10));
    capabilitiesHeader.setToolTipText("Show requested IRCv3 capabilities");

    final boolean[] typingExpanded = new boolean[] {true};
    final boolean[] capabilitiesExpanded = new boolean[] {false};

    Runnable refreshAccordion =
        () -> {
          typingHeader.setText((typingExpanded[0] ? "▾ " : "▸ ") + "Typing indicators");
          capabilitiesHeader.setText(
              (capabilitiesExpanded[0] ? "▾ " : "▸ ") + "Requested capabilities");
          typingTab.setVisible(typingExpanded[0]);
          capabilitiesTab.setVisible(capabilitiesExpanded[0]);
          form.revalidate();
          form.repaint();
        };

    typingHeader.addActionListener(
        e -> {
          if (typingExpanded[0]) return;
          typingExpanded[0] = true;
          capabilitiesExpanded[0] = false;
          refreshAccordion.run();
        });

    capabilitiesHeader.addActionListener(
        e -> {
          if (capabilitiesExpanded[0]) return;
          capabilitiesExpanded[0] = true;
          typingExpanded[0] = false;
          refreshAccordion.run();
        });

    form.add(typingHeader, "growx, wmin 0, wrap");
    form.add(typingTab, "growx, wmin 0, wrap, hidemode 3");
    form.add(capabilitiesHeader, "growx, wmin 0, wrap");
    form.add(capabilitiesTab, "grow, push, wmin 0, hmin 180, hidemode 3");
    refreshAccordion.run();

    return form;
  }

  private static JTextArea subtleInfoTextWith(String text) {
    JTextArea t = subtleInfoText();
    t.setText(text);
    return t;
  }

  private void persistIrcv3Capabilities(Map<String, Boolean> capabilities) {
    if (capabilities == null || capabilities.isEmpty()) return;
    for (Map.Entry<String, Boolean> e : capabilities.entrySet()) {
      String key = normalizeIrcv3CapabilityKey(e.getKey());
      if (key.isEmpty()) continue;
      boolean enabled = Boolean.TRUE.equals(e.getValue());
      runtimeConfig.rememberIrcv3CapabilityEnabled(key, enabled);
    }
  }

  private static String capabilityDisplayLabel(String capability) {
    return switch (capability) {
      case "message-tags" -> "Message tags";
      case "sts" -> "Strict transport security";
      case "server-time" -> "Server timestamps";
      case "echo-message" -> "Echo own messages";
      case "account-tag" -> "Account tags";
      case "userhost-in-names" -> "USERHOST in NAMES";
      case "multiline" -> "Multiline messages";
      case "draft/multiline" -> "Multiline messages (draft)";
      case "draft/typing" -> "Typing transport (draft)";
      case "typing" -> "Typing transport";
      case "read-marker" -> "Read markers";
      case "draft/reply" -> "Reply metadata";
      case "draft/react" -> "Reaction metadata";
      case "draft/message-edit" -> "Message edits (draft)";
      case "message-edit" -> "Message edits (final)";
      case "draft/message-redaction" -> "Message redaction (draft)";
      case "message-redaction" -> "Message redaction (final)";
      case "chathistory" -> "Chat history (final)";
      case "draft/chathistory" -> "Chat history (draft)";
      case "znc.in/playback" -> "ZNC playback";
      case "labeled-response" -> "Labeled responses";
      case "standard-replies" -> "Standard replies";
      case "multi-prefix" -> "Multi-prefix names";
      case "cap-notify" -> "CAP updates";
      case "away-notify" -> "Away status updates";
      case "account-notify" -> "Account status updates";
      case "extended-join" -> "Extended join data";
      case "setname" -> "Setname updates";
      case "chghost" -> "Hostmask changes";
      case "batch" -> "Batch event grouping";
      default -> capability;
    };
  }

  private static String capabilityTooltip(String capability) {
    return capabilityImpactSummary(capability);
  }

  private static String capabilityGroupKey(String capability) {
    return switch (capability) {
      case "multi-prefix",
          "cap-notify",
          "away-notify",
          "account-notify",
          "extended-join",
          "setname",
          "chghost",
          "message-tags",
          "sts",
          "server-time",
          "standard-replies",
          "echo-message",
          "labeled-response",
          "account-tag",
          "userhost-in-names" ->
          "core";
      case "draft/reply",
          "draft/react",
          "draft/message-edit",
          "message-edit",
          "draft/message-redaction",
          "message-redaction",
          "draft/typing",
          "typing",
          "read-marker",
          "multiline",
          "draft/multiline" ->
          "conversation";
      case "batch", "chathistory", "draft/chathistory", "znc.in/playback" -> "history";
      default -> "other";
    };
  }

  private static String capabilityGroupTitle(String groupKey) {
    return switch (groupKey) {
      case "core" -> "Core metadata and sync";
      case "conversation" -> "Conversation features";
      case "history" -> "History and playback";
      default -> "Other capabilities";
    };
  }

  private static int capabilitySortOrder(String capability) {
    return switch (capability) {
      // Core metadata and sync
      case "message-tags" -> 10;
      case "sts" -> 20;
      case "server-time" -> 30;
      case "echo-message" -> 40;
      case "labeled-response" -> 50;
      case "standard-replies" -> 60;
      case "account-tag" -> 70;
      case "account-notify" -> 80;
      case "away-notify" -> 90;
      case "extended-join" -> 100;
      case "chghost" -> 110;
      case "setname" -> 120;
      case "multi-prefix" -> 130;
      case "cap-notify" -> 140;
      case "userhost-in-names" -> 150;

      // Conversation features
      case "multiline" -> 210;
      case "draft/multiline" -> 220;
      case "draft/typing" -> 225;
      case "typing" -> 230;
      case "read-marker" -> 240;
      case "draft/reply" -> 250;
      case "draft/react" -> 260;
      case "message-edit" -> 270;
      case "draft/message-edit" -> 280;
      case "message-redaction" -> 290;
      case "draft/message-redaction" -> 300;

      // History and playback
      case "batch" -> 410;
      case "chathistory" -> 420;
      case "draft/chathistory" -> 430;
      case "znc.in/playback" -> 440;

      default -> 10_000;
    };
  }

  private static String capabilityImpactSummary(String capability) {
    return switch (capability) {
      case "message-tags" ->
          "Foundation for many IRCv3 features: carries structured metadata on messages.";
      case "sts" ->
          "Learns strict transport policy and upgrades future connects for this host to TLS.";
      case "server-time" ->
          "Uses server-provided timestamps to improve ordering and replay accuracy.";
      case "echo-message" ->
          "Server echoes your outbound messages, improving multi-client/bouncer consistency.";
      case "account-tag" -> "Attaches account metadata to messages for richer identity info.";
      case "userhost-in-names" ->
          "May provide richer host/user identity details during names lists.";
      case "multiline", "draft/multiline" ->
          "Allows sending and receiving multiline messages as a single logical message.";
      case "typing", "draft/typing" ->
          "Transport for typing indicators; required to send/receive typing events.";
      case "read-marker" -> "Enables read-position markers on servers that support them.";
      case "draft/reply" -> "Carries reply context so quoted/reply relationships can be preserved.";
      case "draft/react" -> "Carries reaction metadata where servers/clients support it.";
      case "draft/message-edit", "message-edit" ->
          "Allows edit updates for previously sent messages.";
      case "draft/message-redaction", "message-redaction" ->
          "Allows delete/redaction updates for messages.";
      case "chathistory", "draft/chathistory" ->
          "Enables server-side history retrieval and backfill features.";
      case "znc.in/playback" -> "Requests playback support from ZNC bouncers when available.";
      case "labeled-response" -> "Correlates command responses with requests more reliably.";
      case "standard-replies" -> "Provides structured success/error replies from the server.";
      case "multi-prefix" ->
          "Preserves all nick privilege prefixes (not just the highest) in user data.";
      case "cap-notify" -> "Allows capability change notifications after initial connection.";
      case "away-notify" -> "Tracks away/back state transitions for users.";
      case "account-notify" -> "Tracks account login/logout changes for users.";
      case "extended-join" -> "Adds account/realname metadata to join events when available.";
      case "setname" -> "Receives user real-name changes without extra lookups.";
      case "chghost" -> "Keeps hostmask/userhost identity changes in sync.";
      case "batch" -> "Groups related events into coherent batches (useful for playback/history).";
      default -> "Requests \"" + capability + "\" during CAP negotiation on connect/reconnect.";
    };
  }

  private static String capabilityHelpTitle(String capability) {
    return capabilityDisplayLabel(capability) + " (" + capability + ")";
  }

  private static String capabilityHelpMessage(String capability) {
    return "What it is:\n"
        + "Requests IRCv3 capability \""
        + capability
        + "\" during CAP negotiation.\n\n"
        + "Impact in IRCafe:\n"
        + capabilityImpactSummary(capability)
        + "\n\n"
        + "If disabled:\n"
        + "IRCafe will not request this capability on new connections; related features may be unavailable.";
  }

  private static String normalizeIrcv3CapabilityKey(String capability) {
    if (capability == null) return "";
    String k = capability.trim().toLowerCase(Locale.ROOT);
    return k;
  }

  private JPanel buildEmbedsAndPreviewsPanel(ImageEmbedControls image, LinkPreviewControls links) {
    JPanel form =
        new JPanel(
            new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]"));

    form.add(tabTitle("Embeds & Previews"), "span 2, growx, wmin 0, wrap");
    form.add(sectionTitle("Inline images"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Direct image links"), "aligny top");
    form.add(image.panel, "growx");

    form.add(sectionTitle("Link previews"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("OpenGraph cards"), "aligny top");
    form.add(links.panel, "growx");

    return form;
  }

  private JPanel buildHistoryAndStoragePanel(LoggingControls logging, HistoryControls history) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]6[]6[]6[]10[]6[]"));

    panel.add(tabTitle("History & Storage"), "span 2, growx, wmin 0, wrap");

    panel.add(sectionTitle("Logging"), "span 2, growx, wmin 0, wrap");
    panel.add(logging.info, "span 2, growx, wmin 0, wrap");
    panel.add(logging.enabled, "span 2, alignx left, wrap");
    panel.add(logging.logSoftIgnored, "span 2, alignx left, wrap");
    panel.add(logging.logPrivateMessages, "span 2, alignx left, wrap");
    panel.add(logging.savePrivateMessageList, "span 2, alignx left, wrap");
    panel.add(new JLabel("PM list settings"));
    panel.add(logging.managePrivateMessageList, "alignx left, wrap");
    panel.add(logging.keepForever, "span 2, alignx left, wrap");
    panel.add(new JLabel("Retention (days)"));
    panel.add(logging.retentionDays, "w 110!, wrap");
    panel.add(new JLabel("DB file base name"));
    panel.add(logging.dbBaseName, "w 260!");
    panel.add(new JLabel("DB location"));
    panel.add(logging.dbNextToConfig, "alignx left, wrap");

    panel.add(sectionTitle("History paging"), "span 2, growx, wmin 0, wrap");
    panel.add(history.panel, "span 2, growx");

    return panel;
  }

  private NotificationRulesControls buildNotificationRulesControls(
      UiSettings current, List<AutoCloseable> closeables) {
    int cooldown = current != null ? current.notificationRuleCooldownSeconds() : 15;
    JSpinner cooldownSeconds = numberSpinner(cooldown, 0, 3600, 1, closeables);

    NotificationRulesTableModel model =
        new NotificationRulesTableModel(current != null ? current.notificationRules() : List.of());
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

    // Column sizing
    TableColumn enabledCol =
        table.getColumnModel().getColumn(NotificationRulesTableModel.COL_ENABLED);
    enabledCol.setMaxWidth(80);
    enabledCol.setPreferredWidth(70);

    TableColumn labelCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_LABEL);
    labelCol.setPreferredWidth(190);

    TableColumn matchCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_MATCH);
    matchCol.setPreferredWidth(380);

    TableColumn optionsCol =
        table.getColumnModel().getColumn(NotificationRulesTableModel.COL_OPTIONS);
    optionsCol.setMaxWidth(220);
    optionsCol.setPreferredWidth(190);

    TableColumn colorCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_COLOR);
    colorCol.setMaxWidth(130);
    colorCol.setPreferredWidth(110);
    colorCol.setCellRenderer(new RuleColorCellRenderer());

    JLabel validationLabel = new JLabel();
    validationLabel.setVisible(false);
    Color err = errorForeground();
    if (err != null) validationLabel.setForeground(err);

    JTextArea testInput = new JTextArea(4, 40);
    testInput.setLineWrap(true);
    testInput.setWrapStyleWord(true);

    JTextArea testOutput = new JTextArea(6, 40);
    testOutput.setEditable(false);
    testOutput.setLineWrap(true);
    testOutput.setWrapStyleWord(true);

    JLabel testStatus = new JLabel(" ");
    RuleTestRunner testRunner = new RuleTestRunner();
    closeables.add(testRunner);

    return new NotificationRulesControls(
        cooldownSeconds,
        table,
        model,
        validationLabel,
        testInput,
        testOutput,
        testStatus,
        testRunner);
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
    JPanel tab =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]"));
    tab.setOpaque(false);

    JComboBox<IrcEventNotificationPreset> defaultsPreset =
        new JComboBox<>(IrcEventNotificationPreset.values());
    JButton applyDefaults = new JButton("Apply defaults");
    JButton resetToIrcafeDefaults = new JButton("Reset to IRCafe defaults");
    configureIconOnlyButton(
        applyDefaults, "check", "Apply preset defaults to matching IRC event types");
    configureIconOnlyButton(
        resetToIrcafeDefaults, "refresh", "Replace all IRC event rules with IRCafe defaults");

    JPanel defaultsRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[grow,fill]8[]8[]", "[]"));
    defaultsRow.setOpaque(false);
    defaultsRow.add(new JLabel("Defaults"));
    defaultsRow.add(defaultsPreset, "w 240!");
    defaultsRow.add(applyDefaults, "w 36!, h 28!");
    defaultsRow.add(resetToIrcafeDefaults, "w 36!, h 28!");

    JButton add = new JButton("Add");
    JButton edit = new JButton("Edit");
    JButton enableRule = new JButton("Enable");
    JButton disableRule = new JButton("Disable");
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");
    configureIconOnlyButton(add, "plus", "Add IRC event rule");
    configureIconOnlyButton(edit, "edit", "Edit selected IRC event rule");
    configureIconOnlyButton(enableRule, "check", "Enable selected IRC event rule");
    configureIconOnlyButton(disableRule, "pause", "Disable selected IRC event rule");
    configureIconOnlyButton(duplicate, "copy", "Duplicate selected IRC event rule");
    configureIconOnlyButton(remove, "trash", "Remove selected IRC event rule");
    configureIconOnlyButton(up, "arrow-up", "Move selected IRC event rule up");
    configureIconOnlyButton(down, "arrow-down", "Move selected IRC event rule down");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(add);
    buttons.add(edit);
    buttons.add(enableRule);
    buttons.add(disableRule);
    buttons.add(duplicate);
    buttons.add(remove);
    buttons.add(up);
    buttons.add(down);

    Runnable refreshRuleButtons =
        () -> {
          int viewRow = controls.table.getSelectedRow();
          boolean hasSelection = viewRow >= 0;
          int modelRow = hasSelection ? controls.table.convertRowIndexToModel(viewRow) : -1;
          IrcEventNotificationRule selectedRule =
              modelRow >= 0 ? controls.model.ruleAt(modelRow) : null;
          boolean selectedEnabled = selectedRule != null && selectedRule.enabled();
          edit.setEnabled(hasSelection);
          enableRule.setEnabled(hasSelection && !selectedEnabled);
          disableRule.setEnabled(hasSelection && selectedEnabled);
          duplicate.setEnabled(hasSelection);
          remove.setEnabled(hasSelection);
          up.setEnabled(hasSelection && modelRow > 0);
          down.setEnabled(
              hasSelection && modelRow >= 0 && modelRow < (controls.model.getRowCount() - 1));
        };

    Runnable openEditRuleDialog =
        () -> {
          int viewRow = controls.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = controls.table.convertRowIndexToModel(viewRow);
          IrcEventNotificationRule seed = controls.model.ruleAt(modelRow);
          if (seed == null) return;
          IrcEventNotificationRule edited =
              promptIrcEventNotificationRuleDialog("Edit IRC Event Rule", seed);
          if (edited == null) return;
          controls.model.setRule(modelRow, edited);
          int nextView = controls.table.convertRowIndexToView(modelRow);
          if (nextView >= 0) {
            controls.table.getSelectionModel().setSelectionInterval(nextView, nextView);
            controls.table.scrollRectToVisible(controls.table.getCellRect(nextView, 0, true));
          }
          refreshRuleButtons.run();
        };

    add.addActionListener(
        e -> {
          IrcEventNotificationRule created =
              promptIrcEventNotificationRuleDialog("Add IRC Event Rule", null);
          if (created == null) return;
          int row = controls.model.addRule(created);
          if (row >= 0) {
            int viewRow = controls.table.convertRowIndexToView(row);
            if (viewRow >= 0) {
              controls.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
              controls.table.scrollRectToVisible(controls.table.getCellRect(viewRow, 0, true));
            }
          }
          refreshRuleButtons.run();
        });

    edit.addActionListener(e -> openEditRuleDialog.run());

    enableRule.addActionListener(
        e -> {
          int viewRow = controls.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = controls.table.convertRowIndexToModel(viewRow);
          controls.model.setEnabledAt(modelRow, true);
          refreshRuleButtons.run();
        });

    disableRule.addActionListener(
        e -> {
          int viewRow = controls.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = controls.table.convertRowIndexToModel(viewRow);
          controls.model.setEnabledAt(modelRow, false);
          refreshRuleButtons.run();
        });

    duplicate.addActionListener(
        e -> {
          int viewRow = controls.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = controls.table.convertRowIndexToModel(viewRow);
          int dup = controls.model.duplicateRow(modelRow);
          if (dup >= 0) {
            int dupView = controls.table.convertRowIndexToView(dup);
            if (dupView >= 0) {
              controls.table.getSelectionModel().setSelectionInterval(dupView, dupView);
              controls.table.scrollRectToVisible(controls.table.getCellRect(dupView, 0, true));
            }
          }
          refreshRuleButtons.run();
        });

    remove.addActionListener(
        e -> {
          int viewRow = controls.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = controls.table.convertRowIndexToModel(viewRow);
          IrcEventNotificationRule rule = controls.model.ruleAt(modelRow);
          String label = IrcEventNotificationTableModel.effectiveRuleLabel(rule);
          int res =
              JOptionPane.showConfirmDialog(
                  dialog,
                  "Remove IRC event rule \"" + label + "\"?",
                  "Remove IRC Event Rule",
                  JOptionPane.OK_CANCEL_OPTION);
          if (res != JOptionPane.OK_OPTION) return;
          controls.model.removeRow(modelRow);
          int nextModelRow = Math.min(modelRow, controls.model.getRowCount() - 1);
          if (nextModelRow >= 0) {
            int nextView = controls.table.convertRowIndexToView(nextModelRow);
            if (nextView >= 0) {
              controls.table.getSelectionModel().setSelectionInterval(nextView, nextView);
              controls.table.scrollRectToVisible(controls.table.getCellRect(nextView, 0, true));
            }
          } else {
            controls.table.clearSelection();
          }
          refreshRuleButtons.run();
        });

    up.addActionListener(
        e -> {
          int viewRow = controls.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = controls.table.convertRowIndexToModel(viewRow);
          int next = controls.model.moveRow(modelRow, modelRow - 1);
          if (next >= 0) {
            int nextView = controls.table.convertRowIndexToView(next);
            if (nextView >= 0) {
              controls.table.getSelectionModel().setSelectionInterval(nextView, nextView);
              controls.table.scrollRectToVisible(controls.table.getCellRect(nextView, 0, true));
            }
          }
          refreshRuleButtons.run();
        });

    down.addActionListener(
        e -> {
          int viewRow = controls.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = controls.table.convertRowIndexToModel(viewRow);
          int next = controls.model.moveRow(modelRow, modelRow + 1);
          if (next >= 0) {
            int nextView = controls.table.convertRowIndexToView(next);
            if (nextView >= 0) {
              controls.table.getSelectionModel().setSelectionInterval(nextView, nextView);
              controls.table.scrollRectToVisible(controls.table.getCellRect(nextView, 0, true));
            }
          }
          refreshRuleButtons.run();
        });

    applyDefaults.addActionListener(
        e -> {
          IrcEventNotificationPreset preset =
              (IrcEventNotificationPreset) defaultsPreset.getSelectedItem();
          if (preset == null) return;
          List<IrcEventNotificationRule> rules = buildIrcEventDefaultPreset(preset);
          if (rules.isEmpty()) return;
          controls.model.applyPreset(rules);
          int row = controls.model.firstRowForEvent(rules.get(0).eventType());
          if (row < 0) row = 0;
          int viewRow = controls.table.convertRowIndexToView(row);
          if (viewRow >= 0) {
            controls.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            controls.table.scrollRectToVisible(controls.table.getCellRect(viewRow, 0, true));
          }
          refreshRuleButtons.run();
        });

    resetToIrcafeDefaults.addActionListener(
        e -> {
          int confirm =
              JOptionPane.showConfirmDialog(
                  dialog,
                  "Replace all IRC event rules with IRCafe defaults?",
                  "Reset IRC event rules",
                  JOptionPane.OK_CANCEL_OPTION);
          if (confirm != JOptionPane.OK_OPTION) return;

          List<IrcEventNotificationRule> defaults = IrcEventNotificationRule.defaults();
          if (defaults.isEmpty()) return;
          controls.model.replaceAll(defaults);
          if (controls.table.getRowCount() > 0) {
            controls.table.getSelectionModel().setSelectionInterval(0, 0);
            controls.table.scrollRectToVisible(controls.table.getCellRect(0, 0, true));
          } else {
            controls.table.clearSelection();
          }
          refreshRuleButtons.run();
        });

    controls
        .table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e != null && e.getValueIsAdjusting()) return;
              refreshRuleButtons.run();
            });

    controls.table.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e == null) return;
            if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return;
            if (e.getClickCount() != 2) return;
            int viewRow = controls.table.rowAtPoint(e.getPoint());
            if (viewRow < 0) return;
            controls.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            openEditRuleDialog.run();
          }
        });
    refreshRuleButtons.run();

    JScrollPane scroll = new JScrollPane(controls.table);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JPanel presetsPanel =
        captionPanelWithPadding(
            "Presets", "insets 0, fillx, wrap 1", "[grow,fill]", "[]4[]", 10, 10, 10, 10);
    presetsPanel.add(defaultsRow, "growx, wmin 0, wrap");
    presetsPanel.add(
        helpText(
            "Configure event actions for kicks, bans, invites, joins, and mode changes.\n"
                + "Source supports self/others/specific nicks/glob/regex. Channel scope supports Active channel only.\n"
                + "Apply defaults merges by event type. Reset to IRCafe defaults replaces the full rule list."),
        "growx, wmin 0, wrap");
    tab.add(presetsPanel, "growx, wmin 0, wrap");

    JPanel rulesPanel =
        captionPanelWithPadding(
            "Rules", "insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]4[]", 10, 10, 10, 10);
    rulesPanel.add(buttons, "growx, wmin 0, wrap");
    scroll.setPreferredSize(new Dimension(400, 260));
    rulesPanel.add(scroll, "grow, push, wmin 0, wrap");
    rulesPanel.add(helpText("Tip: Double-click a rule to edit it."), "growx, wmin 0, wrap");
    tab.add(rulesPanel, "grow, push, wmin 0, wrap");

    return tab;
  }

  private IrcEventNotificationRule promptIrcEventNotificationRuleDialog(
      String title, IrcEventNotificationRule seed) {
    IrcEventNotificationRule base =
        seed != null
            ? seed
            : new IrcEventNotificationRule(
                false,
                IrcEventNotificationRule.EventType.INVITE_RECEIVED,
                IrcEventNotificationRule.SourceMode.ANY,
                null,
                IrcEventNotificationRule.ChannelScope.ALL,
                null,
                true,
                IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
                true,
                true,
                false,
                defaultBuiltInSoundForIrcEventRule(
                        IrcEventNotificationRule.EventType.INVITE_RECEIVED)
                    .name(),
                false,
                null,
                false,
                null,
                null,
                null);

    Window owner = dialog != null ? dialog : null;

    JCheckBox enabled = new JCheckBox("Enabled", base.enabled());

    JComboBox<IrcEventNotificationRule.EventType> eventType =
        new JComboBox<>(IrcEventNotificationRule.EventType.values());
    eventType.setSelectedItem(
        base.eventType() != null
            ? base.eventType()
            : IrcEventNotificationRule.EventType.INVITE_RECEIVED);

    JComboBox<IrcEventNotificationRule.SourceMode> sourceMode =
        new JComboBox<>(IrcEventNotificationRule.SourceMode.values());
    sourceMode.setSelectedItem(
        base.sourceMode() != null ? base.sourceMode() : IrcEventNotificationRule.SourceMode.ANY);

    JTextField sourcePattern = new JTextField(Objects.toString(base.sourcePattern(), ""));
    sourcePattern.setToolTipText(
        "For Specific nicks: comma-separated list.\n"
            + "For Nick glob: wildcard patterns (* and ?).\n"
            + "For Nick regex: Java regular expression.");

    JComboBox<IrcEventNotificationRule.ChannelScope> channelScope =
        new JComboBox<>(IrcEventNotificationRule.ChannelScope.values());
    channelScope.setSelectedItem(
        base.channelScope() != null
            ? base.channelScope()
            : IrcEventNotificationRule.ChannelScope.ALL);

    JTextField channelPatterns = new JTextField(Objects.toString(base.channelPatterns(), ""));
    channelPatterns.setToolTipText("Comma-separated channel masks (for example: #staff*, #ops).");

    JCheckBox toastEnabled = new JCheckBox("Desktop toast", base.toastEnabled());

    JComboBox<IrcEventNotificationRule.FocusScope> focusScope =
        new JComboBox<>(IrcEventNotificationRule.FocusScope.values());
    focusScope.setSelectedItem(
        base.focusScope() != null
            ? base.focusScope()
            : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY);

    JCheckBox statusBarEnabled = new JCheckBox("Status bar message", base.statusBarEnabled());
    JCheckBox notificationsNodeEnabled =
        new JCheckBox("Notifications node entry", base.notificationsNodeEnabled());

    JCheckBox soundEnabled = new JCheckBox("Play sound", base.soundEnabled());
    JComboBox<BuiltInSound> builtInSound = new JComboBox<>(BuiltInSound.valuesForUi());
    configureBuiltInSoundCombo(builtInSound);
    builtInSound.setSelectedItem(BuiltInSound.fromId(base.soundId()));

    JCheckBox soundUseCustom = new JCheckBox("Use custom file", base.soundUseCustom());
    JTextField soundCustomPath = new JTextField(Objects.toString(base.soundCustomPath(), ""));
    JButton browseCustomSound = new JButton("Browse...");
    JButton clearCustomSound = new JButton("Clear");
    JButton testSound = new JButton("Test");
    configureIconOnlyButton(browseCustomSound, "folder-open", "Browse/import custom sound file");
    configureIconOnlyButton(clearCustomSound, "close", "Clear custom sound path");
    configureIconOnlyButton(testSound, "play", "Test selected sound");

    JCheckBox scriptEnabled = new JCheckBox("Run script/program", base.scriptEnabled());
    JTextField scriptPath = new JTextField(Objects.toString(base.scriptPath(), ""));
    JButton browseScript = new JButton("Browse...");
    JButton clearScript = new JButton("Clear");
    configureIconOnlyButton(browseScript, "terminal", "Browse for script/program");
    configureIconOnlyButton(clearScript, "close", "Clear script path");

    JTextField scriptArgs = new JTextField(Objects.toString(base.scriptArgs(), ""));
    JTextField scriptWorkingDirectory =
        new JTextField(Objects.toString(base.scriptWorkingDirectory(), ""));
    JButton browseScriptWorkingDirectory = new JButton("Browse...");
    JButton clearScriptWorkingDirectory = new JButton("Clear");
    configureIconOnlyButton(
        browseScriptWorkingDirectory, "settings", "Browse for script working directory");
    configureIconOnlyButton(clearScriptWorkingDirectory, "close", "Clear script working directory");

    Runnable refreshSourceFieldState =
        () -> {
          IrcEventNotificationRule.SourceMode mode =
              sourceMode.getSelectedItem() instanceof IrcEventNotificationRule.SourceMode s
                  ? s
                  : IrcEventNotificationRule.SourceMode.ANY;
          boolean needsPattern =
              mode == IrcEventNotificationRule.SourceMode.NICK_LIST
                  || mode == IrcEventNotificationRule.SourceMode.GLOB
                  || mode == IrcEventNotificationRule.SourceMode.REGEX;
          sourcePattern.setEnabled(needsPattern);
          sourcePattern.setEditable(needsPattern);
          String placeholder =
              switch (mode) {
                case NICK_LIST -> "alice, bob";
                case GLOB -> "op*, admin?";
                case REGEX -> "^op[0-9]+$";
                default -> "";
              };
          sourcePattern.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        };

    Runnable refreshChannelFieldState =
        () -> {
          IrcEventNotificationRule.ChannelScope scope =
              channelScope.getSelectedItem() instanceof IrcEventNotificationRule.ChannelScope s
                  ? s
                  : IrcEventNotificationRule.ChannelScope.ALL;
          boolean needsPattern =
              scope == IrcEventNotificationRule.ChannelScope.ONLY
                  || scope == IrcEventNotificationRule.ChannelScope.ALL_EXCEPT;
          channelPatterns.setEnabled(needsPattern);
          channelPatterns.setEditable(needsPattern);
          channelPatterns.putClientProperty(
              FlatClientProperties.PLACEHOLDER_TEXT, needsPattern ? "#staff*, #ops" : "");
        };

    Runnable refreshSoundState =
        () -> {
          boolean soundOn = soundEnabled.isSelected();
          soundUseCustom.setEnabled(soundOn);
          boolean useCustom = soundOn && soundUseCustom.isSelected();
          builtInSound.setEnabled(soundOn && !useCustom);
          soundCustomPath.setEnabled(soundOn && useCustom);
          soundCustomPath.setEditable(soundOn && useCustom);
          browseCustomSound.setEnabled(soundOn && useCustom);
          String custom = Objects.toString(soundCustomPath.getText(), "").trim();
          clearCustomSound.setEnabled(soundOn && useCustom && !custom.isBlank());
          testSound.setEnabled(soundOn);
        };

    Runnable refreshScriptState =
        () -> {
          boolean run = scriptEnabled.isSelected();
          scriptPath.setEnabled(run);
          scriptPath.setEditable(run);
          browseScript.setEnabled(run);
          clearScript.setEnabled(
              run && !Objects.toString(scriptPath.getText(), "").trim().isBlank());
          scriptArgs.setEnabled(run);
          scriptArgs.setEditable(run);
          scriptWorkingDirectory.setEnabled(run);
          scriptWorkingDirectory.setEditable(run);
          browseScriptWorkingDirectory.setEnabled(run);
          clearScriptWorkingDirectory.setEnabled(
              run && !Objects.toString(scriptWorkingDirectory.getText(), "").trim().isBlank());
        };

    final IrcEventNotificationRule.EventType[] priorEvent =
        new IrcEventNotificationRule.EventType[] {
          eventType.getSelectedItem() instanceof IrcEventNotificationRule.EventType e
              ? e
              : IrcEventNotificationRule.EventType.INVITE_RECEIVED
        };

    eventType.addActionListener(
        e -> {
          IrcEventNotificationRule.EventType selectedEvent =
              eventType.getSelectedItem() instanceof IrcEventNotificationRule.EventType et
                  ? et
                  : IrcEventNotificationRule.EventType.INVITE_RECEIVED;
          IrcEventNotificationRule.EventType previous = priorEvent[0];
          if (previous == null) previous = IrcEventNotificationRule.EventType.INVITE_RECEIVED;
          if (!soundUseCustom.isSelected()) {
            Object selectedSound = builtInSound.getSelectedItem();
            if (selectedSound instanceof BuiltInSound currentSound) {
              BuiltInSound previousDefault = defaultBuiltInSoundForIrcEventRule(previous);
              if (currentSound == previousDefault) {
                builtInSound.setSelectedItem(defaultBuiltInSoundForIrcEventRule(selectedEvent));
              }
            }
          }
          priorEvent[0] = selectedEvent;
        });

    sourceMode.addActionListener(e -> refreshSourceFieldState.run());
    channelScope.addActionListener(e -> refreshChannelFieldState.run());
    soundEnabled.addActionListener(e -> refreshSoundState.run());
    soundUseCustom.addActionListener(e -> refreshSoundState.run());
    soundCustomPath.getDocument().addDocumentListener(new SimpleDocListener(refreshSoundState));
    scriptEnabled.addActionListener(e -> refreshScriptState.run());
    scriptPath.getDocument().addDocumentListener(new SimpleDocListener(refreshScriptState));
    scriptWorkingDirectory
        .getDocument()
        .addDocumentListener(new SimpleDocListener(refreshScriptState));

    browseCustomSound.addActionListener(
        e -> {
          try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose notification sound (MP3 or WAV)");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setAcceptAllFileFilterUsed(true);
            chooser.addChoosableFileFilter(
                new FileNameExtensionFilter("Audio files (MP3, WAV)", "mp3", "wav"));
            int result = chooser.showOpenDialog(owner);
            if (result != JFileChooser.APPROVE_OPTION) return;
            File f = chooser.getSelectedFile();
            if (f == null) return;
            String rel = importNotificationSoundFileToRuntimeDir(f);
            if (rel != null && !rel.isBlank()) {
              soundCustomPath.setText(rel);
              soundUseCustom.setSelected(true);
              refreshSoundState.run();
            }
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                owner,
                "Could not import sound file.\n\n" + ex.getMessage(),
                "Import failed",
                JOptionPane.ERROR_MESSAGE);
          }
        });

    clearCustomSound.addActionListener(
        e -> {
          soundUseCustom.setSelected(false);
          soundCustomPath.setText("");
          refreshSoundState.run();
        });

    testSound.addActionListener(
        e -> {
          try {
            if (notificationSoundService == null) return;
            if (soundUseCustom.isSelected()) {
              String rel = Objects.toString(soundCustomPath.getText(), "").trim();
              if (!rel.isBlank()) notificationSoundService.previewCustom(rel);
            } else {
              BuiltInSound sound =
                  builtInSound.getSelectedItem() instanceof BuiltInSound s ? s : null;
              notificationSoundService.preview(sound);
            }
          } catch (Throwable ignored) {
          }
        });

    browseScript.addActionListener(
        e -> {
          JFileChooser chooser = new JFileChooser();
          chooser.setDialogTitle("Select script/program");
          chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
          chooser.setAcceptAllFileFilterUsed(true);
          int result = chooser.showOpenDialog(owner);
          if (result != JFileChooser.APPROVE_OPTION) return;
          File selected = chooser.getSelectedFile();
          if (selected == null) return;
          scriptPath.setText(selected.getAbsolutePath());
          refreshScriptState.run();
        });

    clearScript.addActionListener(
        e -> {
          scriptPath.setText("");
          refreshScriptState.run();
        });

    browseScriptWorkingDirectory.addActionListener(
        e -> {
          JFileChooser chooser = new JFileChooser();
          chooser.setDialogTitle("Select script working directory");
          chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
          chooser.setAcceptAllFileFilterUsed(true);
          int result = chooser.showOpenDialog(owner);
          if (result != JFileChooser.APPROVE_OPTION) return;
          File selected = chooser.getSelectedFile();
          if (selected == null) return;
          scriptWorkingDirectory.setText(selected.getAbsolutePath());
          refreshScriptState.run();
        });

    clearScriptWorkingDirectory.addActionListener(
        e -> {
          scriptWorkingDirectory.setText("");
          refreshScriptState.run();
        });

    refreshSourceFieldState.run();
    refreshChannelFieldState.run();
    refreshSoundState.run();
    refreshScriptState.run();

    JPanel filtersPanel =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 2,hidemode 3", "[right]8[grow,fill]", "[]6[]6[]6[]6[]6[]"));
    filtersPanel.add(enabled, "span 2,wrap");
    filtersPanel.add(new JLabel("Event"));
    filtersPanel.add(eventType, "growx, wmin 220, wrap");
    filtersPanel.add(new JLabel("Source"));
    filtersPanel.add(sourceMode, "growx, wrap");
    filtersPanel.add(new JLabel("Source match"));
    filtersPanel.add(sourcePattern, "growx, wrap");
    filtersPanel.add(new JLabel("Channel scope"));
    filtersPanel.add(channelScope, "growx, wrap");
    filtersPanel.add(new JLabel("Channels"));
    filtersPanel.add(channelPatterns, "growx, wrap");
    filtersPanel.add(new JLabel(""));
    filtersPanel.add(
        helpText(
            "Active channel only means the event target must match the currently selected channel on the same server."),
        "growx, wmin 0, wrap");

    JPanel actionsPanel =
        new JPanel(
            new MigLayout("insets 10,fillx,wrap 2,hidemode 3", "[right]8[grow,fill]", "[]6[]6[]"));
    actionsPanel.add(toastEnabled, "span 2, growx, wrap");
    actionsPanel.add(new JLabel("Toast focus"));
    actionsPanel.add(focusScope, "growx, wrap");
    actionsPanel.add(statusBarEnabled, "span 2, growx, wrap");
    actionsPanel.add(notificationsNodeEnabled, "span 2, growx, wrap");
    actionsPanel.add(new JLabel(""));
    actionsPanel.add(
        helpText(
            "Tip: combine multiple rules for the same event to split foreground/background behavior."),
        "growx, wmin 0, wrap");

    JPanel soundPanel =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 4,hidemode 3", "[right]8[grow,fill]8[]8[]", "[]6[]4[]"));
    soundPanel.add(soundEnabled, "span 4, growx, wrap");
    soundPanel.add(new JLabel("Built-in"));
    soundPanel.add(builtInSound, "growx, wmin 180");
    soundPanel.add(testSound, "w 36!, h 28!");
    soundPanel.add(soundUseCustom, "wrap");
    soundPanel.add(new JLabel("Custom file"));
    soundPanel.add(soundCustomPath, "growx, pushx, wmin 0");
    soundPanel.add(browseCustomSound, "w 36!, h 28!");
    soundPanel.add(clearCustomSound, "w 36!, h 28!, wrap");
    soundPanel.add(new JLabel(""));
    soundPanel.add(
        helpText("When Sound is disabled on a rule, no sound is played for that event."),
        "span 3, growx, wmin 0, wrap");

    JPanel scriptPanel =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 4,hidemode 3", "[right]8[grow,fill]8[]8[]", "[]6[]4[]"));
    scriptPanel.add(scriptEnabled, "span 4, growx, wrap");
    scriptPanel.add(new JLabel("Script path"));
    scriptPanel.add(scriptPath, "growx, pushx, wmin 0");
    scriptPanel.add(browseScript, "w 36!, h 28!");
    scriptPanel.add(clearScript, "w 36!, h 28!, wrap");
    scriptPanel.add(new JLabel("Arguments"));
    scriptPanel.add(scriptArgs, "span 3, growx, wmin 0, wrap");
    scriptPanel.add(new JLabel("Working dir"));
    scriptPanel.add(scriptWorkingDirectory, "growx, pushx, wmin 0");
    scriptPanel.add(browseScriptWorkingDirectory, "w 36!, h 28!");
    scriptPanel.add(clearScriptWorkingDirectory, "w 36!, h 28!, wrap");
    scriptPanel.add(new JLabel(""));
    scriptPanel.add(
        helpText(
            "If enabled, IRCafe executes the script and sets env vars:\n"
                + "IRCAFE_EVENT_TYPE, IRCAFE_SERVER_ID, IRCAFE_CHANNEL, IRCAFE_SOURCE_NICK,\n"
                + "IRCAFE_SOURCE_IS_SELF, IRCAFE_TITLE, IRCAFE_BODY, IRCAFE_TIMESTAMP_MS.\n"
                + "Arguments support quotes/escapes and are passed directly (no shell expansion)."),
        "span 3, growx, wmin 0, wrap");

    JTabbedPane tabs = new JTabbedPane();
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    tabs.addTab("Filters", filtersPanel);
    tabs.addTab("Actions", actionsPanel);
    tabs.addTab("Sound", soundPanel);
    tabs.addTab("Script", scriptPanel);
    tabs.setPreferredSize(new Dimension(640, 420));

    JPanel form = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[grow,fill]"));
    form.add(tabs, "grow, push, wmin 0");

    String dialogTitle = Objects.toString(title, "IRC Event Rule");
    while (true) {
      int choice =
          JOptionPane.showConfirmDialog(
              owner, form, dialogTitle, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (choice != JOptionPane.OK_OPTION) return null;

      IrcEventNotificationRule.EventType selectedEvent =
          eventType.getSelectedItem() instanceof IrcEventNotificationRule.EventType ev
              ? ev
              : IrcEventNotificationRule.EventType.INVITE_RECEIVED;
      IrcEventNotificationRule.SourceMode selectedSourceMode =
          sourceMode.getSelectedItem() instanceof IrcEventNotificationRule.SourceMode mode
              ? mode
              : IrcEventNotificationRule.SourceMode.ANY;
      IrcEventNotificationRule.ChannelScope selectedChannelScope =
          channelScope.getSelectedItem() instanceof IrcEventNotificationRule.ChannelScope scope
              ? scope
              : IrcEventNotificationRule.ChannelScope.ALL;
      IrcEventNotificationRule.FocusScope selectedFocusScope =
          focusScope.getSelectedItem() instanceof IrcEventNotificationRule.FocusScope focus
              ? focus
              : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;

      String sourcePatternValue = Objects.toString(sourcePattern.getText(), "").trim();
      if (sourcePatternValue.isEmpty()) sourcePatternValue = null;
      boolean sourceNeedsPattern =
          selectedSourceMode == IrcEventNotificationRule.SourceMode.NICK_LIST
              || selectedSourceMode == IrcEventNotificationRule.SourceMode.GLOB
              || selectedSourceMode == IrcEventNotificationRule.SourceMode.REGEX;
      if (sourceNeedsPattern && sourcePatternValue == null) {
        JOptionPane.showMessageDialog(
            owner,
            "Source mode \"" + selectedSourceMode + "\" requires a source pattern.",
            "Invalid IRC Event Rule",
            JOptionPane.ERROR_MESSAGE);
        tabs.setSelectedIndex(0);
        continue;
      }
      if (selectedSourceMode == IrcEventNotificationRule.SourceMode.REGEX
          && sourcePatternValue != null) {
        try {
          Pattern.compile(sourcePatternValue);
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(
              owner,
              "Invalid source regex pattern:\n"
                  + Objects.toString(ex.getMessage(), "Invalid regex"),
              "Invalid IRC Event Rule",
              JOptionPane.ERROR_MESSAGE);
          tabs.setSelectedIndex(0);
          continue;
        }
      }
      if (!sourceNeedsPattern) sourcePatternValue = null;

      String channelPatternsValue = Objects.toString(channelPatterns.getText(), "").trim();
      if (channelPatternsValue.isEmpty()) channelPatternsValue = null;
      boolean channelNeedsPattern =
          selectedChannelScope == IrcEventNotificationRule.ChannelScope.ONLY
              || selectedChannelScope == IrcEventNotificationRule.ChannelScope.ALL_EXCEPT;
      if (channelNeedsPattern && channelPatternsValue == null) {
        JOptionPane.showMessageDialog(
            owner,
            "Channel scope \"" + selectedChannelScope + "\" requires channel patterns.",
            "Invalid IRC Event Rule",
            JOptionPane.ERROR_MESSAGE);
        tabs.setSelectedIndex(0);
        continue;
      }
      if (!channelNeedsPattern) channelPatternsValue = null;

      BuiltInSound selectedSound =
          builtInSound.getSelectedItem() instanceof BuiltInSound sound
              ? sound
              : defaultBuiltInSoundForIrcEventRule(selectedEvent);
      String soundCustomPathValue = Objects.toString(soundCustomPath.getText(), "").trim();
      if (soundCustomPathValue.isEmpty()) soundCustomPathValue = null;
      boolean useCustomSound = soundUseCustom.isSelected() && soundCustomPathValue != null;

      String scriptPathValue = Objects.toString(scriptPath.getText(), "").trim();
      if (scriptPathValue.isEmpty()) scriptPathValue = null;
      String scriptArgsValue = Objects.toString(scriptArgs.getText(), "").trim();
      if (scriptArgsValue.isEmpty()) scriptArgsValue = null;
      String scriptWorkingDirectoryValue =
          Objects.toString(scriptWorkingDirectory.getText(), "").trim();
      if (scriptWorkingDirectoryValue.isEmpty()) scriptWorkingDirectoryValue = null;
      boolean runScript = scriptEnabled.isSelected();
      if (runScript && scriptPathValue == null) {
        JOptionPane.showMessageDialog(
            owner,
            "Script path is required when Run script/program is enabled.",
            "Invalid IRC Event Rule",
            JOptionPane.ERROR_MESSAGE);
        tabs.setSelectedIndex(3);
        continue;
      }

      return new IrcEventNotificationRule(
          enabled.isSelected(),
          selectedEvent,
          selectedSourceMode,
          sourcePatternValue,
          selectedChannelScope,
          channelPatternsValue,
          toastEnabled.isSelected(),
          selectedFocusScope,
          statusBarEnabled.isSelected(),
          notificationsNodeEnabled.isSelected(),
          soundEnabled.isSelected(),
          selectedSound.name(),
          useCustomSound,
          soundCustomPathValue,
          runScript,
          scriptPathValue,
          scriptArgsValue,
          scriptWorkingDirectoryValue);
    }
  }

  private List<IrcEventNotificationRule> buildIrcEventDefaultPreset(
      IrcEventNotificationPreset preset) {
    if (preset == null) return List.of();
    return switch (preset) {
      case ESSENTIAL ->
          List.of(
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.INVITE_RECEIVED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.YOU_KICKED,
                  IrcEventNotificationRule.SourceMode.ANY,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.YOU_BANNED,
                  IrcEventNotificationRule.SourceMode.ANY,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.YOU_KLINED,
                  IrcEventNotificationRule.SourceMode.ANY,
                  false));
      case MODERATION ->
          List.of(
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.KICKED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.BANNED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.OPPED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.DEOPPED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.VOICED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.DEVOICED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.HALF_OPPED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.DEHALF_OPPED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.INVITE_RECEIVED,
                  IrcEventNotificationRule.SourceMode.ANY,
                  false));
      case ALL_EVENTS ->
          Arrays.stream(IrcEventNotificationRule.EventType.values())
              .map(t -> eventDefaultRule(t, IrcEventNotificationRule.SourceMode.ANY, false))
              .toList();
    };
  }

  private static IrcEventNotificationRule eventDefaultRule(
      IrcEventNotificationRule.EventType eventType,
      IrcEventNotificationRule.SourceMode sourceMode,
      boolean soundEnabled) {
    return new IrcEventNotificationRule(
        true,
        eventType,
        sourceMode,
        null,
        IrcEventNotificationRule.ChannelScope.ALL,
        null,
        true,
        IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
        true,
        true,
        soundEnabled,
        defaultBuiltInSoundForIrcEventRule(eventType).name(),
        false,
        null,
        false,
        null,
        null,
        null);
  }

  private static BuiltInSound defaultBuiltInSoundForIrcEventRule(
      IrcEventNotificationRule.EventType eventType) {
    return IrcEventNotificationRule.defaultBuiltInSoundForEvent(eventType);
  }

  private JPanel buildNotificationsPanel(
      NotificationRulesControls notifications, IrcEventNotificationControls ircEventNotifications) {
    JPanel panel =
        new JPanel(new MigLayout("insets 10, fill, wrap 1", "[grow,fill]", "[]8[]4[grow,fill]"));

    panel.add(tabTitle("Notifications"), "growx, wmin 0, wrap");
    panel.add(sectionTitle("Rule matches"), "growx, wmin 0, wrap");
    panel.add(
        helpText(
            "Add custom word/regex rules to create notifications when messages match.\n"
                + "Rules only trigger for channels (not PMs) and only when the channel isn't the active target."),
        "growx, wmin 0, wrap");

    JButton add = new JButton("Add");
    JButton edit = new JButton("Edit");
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");
    configureIconOnlyButton(add, "plus", "Add notification rule");
    configureIconOnlyButton(edit, "edit", "Edit selected notification rule");
    configureIconOnlyButton(duplicate, "copy", "Duplicate selected notification rule");
    configureIconOnlyButton(remove, "trash", "Remove selected notification rule");
    configureIconOnlyButton(up, "arrow-up", "Move selected notification rule up");
    configureIconOnlyButton(down, "arrow-down", "Move selected notification rule down");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(add);
    buttons.add(edit);
    buttons.add(duplicate);
    buttons.add(remove);
    buttons.add(up);
    buttons.add(down);
    Runnable refreshRuleButtons =
        () -> {
          int viewRow = notifications.table.getSelectedRow();
          boolean hasSelection = viewRow >= 0;
          int modelRow = hasSelection ? notifications.table.convertRowIndexToModel(viewRow) : -1;
          edit.setEnabled(hasSelection);
          duplicate.setEnabled(hasSelection);
          remove.setEnabled(hasSelection);
          up.setEnabled(hasSelection && modelRow > 0);
          down.setEnabled(
              hasSelection && modelRow >= 0 && modelRow < (notifications.model.getRowCount() - 1));
        };

    Runnable openEditRuleDialog =
        () -> {
          int viewRow = notifications.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = notifications.table.convertRowIndexToModel(viewRow);
          NotificationRule seed = notifications.model.ruleAt(modelRow);
          if (seed == null) return;
          NotificationRule edited = promptNotificationRuleDialog("Edit Notification Rule", seed);
          if (edited == null) return;
          notifications.model.setRule(modelRow, edited);
          int nextView = notifications.table.convertRowIndexToView(modelRow);
          if (nextView >= 0) {
            notifications.table.getSelectionModel().setSelectionInterval(nextView, nextView);
            notifications.table.scrollRectToVisible(
                notifications.table.getCellRect(nextView, 0, true));
          }
          refreshRuleButtons.run();
        };

    add.addActionListener(
        e -> {
          NotificationRule created = promptNotificationRuleDialog("Add Notification Rule", null);
          if (created == null) return;
          int row = notifications.model.addRule(created);
          if (row >= 0) {
            int viewRow = notifications.table.convertRowIndexToView(row);
            if (viewRow >= 0) {
              notifications.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
              notifications.table.scrollRectToVisible(
                  notifications.table.getCellRect(viewRow, 0, true));
            }
          }
          refreshRuleButtons.run();
        });

    edit.addActionListener(e -> openEditRuleDialog.run());

    duplicate.addActionListener(
        e -> {
          int viewRow = notifications.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = notifications.table.convertRowIndexToModel(viewRow);
          int dup = notifications.model.duplicateRow(modelRow);
          if (dup >= 0) {
            int dupView = notifications.table.convertRowIndexToView(dup);
            if (dupView >= 0) {
              notifications.table.getSelectionModel().setSelectionInterval(dupView, dupView);
              notifications.table.scrollRectToVisible(
                  notifications.table.getCellRect(dupView, 0, true));
            }
          }
          refreshRuleButtons.run();
        });

    remove.addActionListener(
        e -> {
          int viewRow = notifications.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = notifications.table.convertRowIndexToModel(viewRow);
          NotificationRule rule = notifications.model.ruleAt(modelRow);
          String label = NotificationRulesTableModel.effectiveRuleLabel(rule);
          int res =
              JOptionPane.showConfirmDialog(
                  dialog,
                  "Remove notification rule \"" + label + "\"?",
                  "Remove Notification Rule",
                  JOptionPane.OK_CANCEL_OPTION);
          if (res != JOptionPane.OK_OPTION) return;
          notifications.model.removeRow(modelRow);
          int nextModelRow = Math.min(modelRow, notifications.model.getRowCount() - 1);
          if (nextModelRow >= 0) {
            int nextView = notifications.table.convertRowIndexToView(nextModelRow);
            if (nextView >= 0) {
              notifications.table.getSelectionModel().setSelectionInterval(nextView, nextView);
              notifications.table.scrollRectToVisible(
                  notifications.table.getCellRect(nextView, 0, true));
            }
          } else {
            notifications.table.clearSelection();
          }
          refreshRuleButtons.run();
        });

    up.addActionListener(
        e -> {
          int viewRow = notifications.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = notifications.table.convertRowIndexToModel(viewRow);
          int next = notifications.model.moveRow(modelRow, modelRow - 1);
          if (next >= 0) {
            int nextView = notifications.table.convertRowIndexToView(next);
            if (nextView >= 0) {
              notifications.table.getSelectionModel().setSelectionInterval(nextView, nextView);
              notifications.table.scrollRectToVisible(
                  notifications.table.getCellRect(nextView, 0, true));
            }
          }
          refreshRuleButtons.run();
        });

    down.addActionListener(
        e -> {
          int viewRow = notifications.table.getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = notifications.table.convertRowIndexToModel(viewRow);
          int next = notifications.model.moveRow(modelRow, modelRow + 1);
          if (next >= 0) {
            int nextView = notifications.table.convertRowIndexToView(next);
            if (nextView >= 0) {
              notifications.table.getSelectionModel().setSelectionInterval(nextView, nextView);
              notifications.table.scrollRectToVisible(
                  notifications.table.getCellRect(nextView, 0, true));
            }
          }
          refreshRuleButtons.run();
        });

    notifications
        .table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e != null && e.getValueIsAdjusting()) return;
              refreshRuleButtons.run();
            });

    notifications.table.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e == null) return;
            if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return;
            if (e.getClickCount() != 2) return;
            int viewRow = notifications.table.rowAtPoint(e.getPoint());
            if (viewRow < 0) return;
            notifications.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            openEditRuleDialog.run();
          }
        });
    refreshRuleButtons.run();

    JScrollPane scroll = new JScrollPane(notifications.table);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JScrollPane testInScroll = new JScrollPane(notifications.testInput);
    testInScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    testInScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JScrollPane testOutScroll = new JScrollPane(notifications.testOutput);
    testOutScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    testOutScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JButton runTest = new JButton("Test");
    JButton clearTest = new JButton("Clear");
    configureIconOnlyButton(runTest, "check", "Test sample message against notification rules");
    configureIconOnlyButton(clearTest, "close", "Clear rule test input/output");

    JPanel testButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    testButtons.add(runTest);
    testButtons.add(clearTest);
    testButtons.add(notifications.testStatus);

    runTest.addActionListener(
        e -> {
          if (notifications.table.isEditing()) {
            try {
              notifications.table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          refreshNotificationRuleValidation(notifications);
          notifications.testRunner.runTest(notifications);
        });

    clearTest.addActionListener(
        e -> {
          notifications.testInput.setText("");
          notifications.testOutput.setText("");
          notifications.testStatus.setText(" ");
        });

    JPanel rulesTab = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]8[]"));
    rulesTab.setOpaque(false);
    JPanel rulesBehaviorPanel =
        captionPanel("Rule behavior", "insets 0, fillx, wrap 2", "[right]10[grow,fill]", "[]");
    rulesBehaviorPanel.add(new JLabel("Cooldown (sec)"));
    rulesBehaviorPanel.add(notifications.cooldownSeconds, "w 110!, wrap");
    rulesTab.add(rulesBehaviorPanel, "growx, wmin 0, wrap");
    JPanel rulesTablePanel =
        captionPanel("Rule list", "insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]4[]4[]");
    rulesTablePanel.add(buttons, "growx, wmin 0, wrap");
    rulesTablePanel.add(scroll, "grow, push, h 260!, wmin 0, wrap");
    rulesTablePanel.add(notifications.validationLabel, "growx, wmin 0, wrap");
    rulesTablePanel.add(helpText("Tip: Double-click a rule to edit it."), "growx, wmin 0, wrap");
    rulesTab.add(rulesTablePanel, "grow, push, wmin 0");

    JPanel testTab = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]"));
    testTab.setOpaque(false);
    JPanel testRunnerPanel =
        captionPanel(
            "Message test", "insets 0, fill, wrap 2", "[right]10[grow,fill]", "[]6[]4[]4[]");
    testRunnerPanel.add(
        helpText(
            "Paste a sample message to see which rules match. This is just a preview; it won't create real notifications."),
        "span 2, growx, wmin 0, wrap");
    testRunnerPanel.add(new JLabel("Sample"), "aligny top");
    testRunnerPanel.add(testInScroll, "growx, h 100!, wrap");
    testRunnerPanel.add(new JLabel("Matches"), "aligny top");
    testRunnerPanel.add(testOutScroll, "growx, h 160!, wrap");
    testRunnerPanel.add(new JLabel(""));
    testRunnerPanel.add(testButtons, "growx, wrap");
    testTab.add(testRunnerPanel, "grow, push, wmin 0");

    JTabbedPane subTabs = new JTabbedPane();
    Icon rulesTabIcon = SvgIcons.action("edit", 14);
    Icon testTabIcon = SvgIcons.action("check", 14);
    subTabs.addTab(
        "Rules", rulesTabIcon, padSubTab(rulesTab), "Manage notification matching rules");
    subTabs.addTab(
        "Test", testTabIcon, padSubTab(testTab), "Try a sample message against your rules");
    subTabs.addTab(
        "IRC Events",
        null,
        padSubTab(buildIrcEventNotificationsTab(ircEventNotifications)),
        "Configure notifications for IRC events like kick/ban/invite/mode updates");

    panel.add(subTabs, "grow, push, wmin 0");

    refreshNotificationRuleValidation(notifications);

    return panel;
  }

  private UserCommandAliasesControls buildUserCommandAliasesControls(
      List<UserCommandAlias> initial, boolean unknownCommandAsRawEnabled) {
    UserCommandAliasesTableModel model = new UserCommandAliasesTableModel(initial);
    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(Math.max(22, table.getRowHeight()));

    TableColumn enabledCol =
        table.getColumnModel().getColumn(UserCommandAliasesTableModel.COL_ENABLED);
    enabledCol.setMaxWidth(80);
    enabledCol.setPreferredWidth(70);

    TableColumn commandCol =
        table.getColumnModel().getColumn(UserCommandAliasesTableModel.COL_COMMAND);
    commandCol.setPreferredWidth(220);

    JTextArea template = new JTextArea(7, 40);
    template.setLineWrap(true);
    template.setWrapStyleWord(true);
    template.setToolTipText(
        "Use %1..%9, %1-, %*, %c, %t, %s, %e, %n, &1..&9. "
            + "Separate commands with ';' or new lines.");
    template.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "/msg %1 Hello %2-");

    JButton add = new JButton("Add");
    JButton importHexChat = new JButton("Import HexChat...");
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");
    configureIconOnlyButton(add, "plus", "Add command alias");
    configureIconOnlyButton(importHexChat, "copy", "Import aliases from HexChat commands.conf");
    configureIconOnlyButton(duplicate, "copy", "Duplicate selected alias");
    configureIconOnlyButton(remove, "trash", "Remove selected alias");
    configureIconOnlyButton(up, "arrow-up", "Move selected alias up");
    configureIconOnlyButton(down, "arrow-down", "Move selected alias down");
    JCheckBox unknownCommandAsRaw =
        new JCheckBox("Fallback unknown /commands to raw IRC (HexChat-compatible)");
    unknownCommandAsRaw.setSelected(unknownCommandAsRawEnabled);
    unknownCommandAsRaw.setToolTipText(
        "When enabled, typing an unknown slash command sends it to the server "
            + "as raw IRC (same as /quote), instead of showing a local Unknown command message.");

    JLabel hint = new JLabel("Select an alias row to edit its expansion.");
    hint.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");

    final boolean[] syncing = new boolean[] {false};

    Runnable loadSelectedTemplate =
        () -> {
          int row = table.getSelectedRow();
          syncing[0] = true;
          if (row < 0) {
            template.setText("");
          } else {
            int modelRow = table.convertRowIndexToModel(row);
            template.setText(model.templateAt(modelRow));
          }
          syncing[0] = false;

          boolean selected = row >= 0;
          duplicate.setEnabled(selected);
          remove.setEnabled(selected);
          up.setEnabled(selected && row > 0);
          down.setEnabled(selected && row < table.getRowCount() - 1);
          template.setEnabled(selected);
          hint.setText(
              selected
                  ? "Expansion supports multi-command ';' / newline and placeholders (%1, %2-, %*)."
                  : "Select an alias row to edit its expansion.");
        };

    Runnable persistSelectedTemplate =
        () -> {
          if (syncing[0]) return;
          int row = table.getSelectedRow();
          if (row < 0) return;
          int modelRow = table.convertRowIndexToModel(row);
          model.setTemplateAt(modelRow, template.getText());
        };

    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e != null && e.getValueIsAdjusting()) return;
              loadSelectedTemplate.run();
            });
    template.getDocument().addDocumentListener(new SimpleDocListener(persistSelectedTemplate));

    add.addActionListener(
        e -> {
          if (table.isEditing()) {
            try {
              table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          int idx = model.addAlias(new UserCommandAlias(true, "", ""));
          if (idx >= 0) {
            int view = table.convertRowIndexToView(idx);
            table.getSelectionModel().setSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
            table.editCellAt(view, UserCommandAliasesTableModel.COL_COMMAND);
            table.requestFocusInWindow();
          }
        });

    importHexChat.addActionListener(
        e -> {
          if (table.isEditing()) {
            try {
              table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }

          JFileChooser chooser = new JFileChooser();
          chooser.setDialogTitle("Import HexChat commands.conf");
          chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
          chooser.setAcceptAllFileFilterUsed(true);
          File suggested = suggestedHexChatCommandsConfFile();
          if (suggested != null) {
            File parent = suggested.getParentFile();
            if (parent != null && parent.isDirectory()) {
              chooser.setCurrentDirectory(parent);
            }
            chooser.setSelectedFile(suggested);
          } else {
            chooser.setSelectedFile(new File("commands.conf"));
          }

          int result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(importHexChat));
          if (result != JFileChooser.APPROVE_OPTION) return;

          File selected = chooser.getSelectedFile();
          if (selected == null) return;

          HexChatCommandAliasImporter.ImportResult imported;
          try {
            imported = HexChatCommandAliasImporter.importFile(selected.toPath());
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(importHexChat),
                "Could not import HexChat aliases from:\n" + selected + "\n\n" + ex.getMessage(),
                "Import failed",
                JOptionPane.ERROR_MESSAGE);
            return;
          }

          if (imported.aliases().isEmpty()) {
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(importHexChat),
                "No aliases were found in the selected file.",
                "HexChat import",
                JOptionPane.INFORMATION_MESSAGE);
            return;
          }

          Set<String> existing = new HashSet<>();
          for (UserCommandAlias alias : model.snapshot()) {
            String key = normalizeAliasCommandKey(alias != null ? alias.name() : null);
            if (!key.isEmpty()) existing.add(key);
          }

          int added = 0;
          int skippedExisting = 0;
          int firstAdded = -1;
          for (UserCommandAlias alias : imported.aliases()) {
            String key = normalizeAliasCommandKey(alias != null ? alias.name() : null);
            if (key.isEmpty()) continue;
            if (existing.contains(key)) {
              skippedExisting++;
              continue;
            }
            int idx = model.addAlias(alias);
            if (firstAdded < 0) firstAdded = idx;
            existing.add(key);
            added++;
          }

          if (firstAdded >= 0) {
            int view = table.convertRowIndexToView(firstAdded);
            if (view >= 0) {
              table.getSelectionModel().setSelectionInterval(view, view);
              table.scrollRectToVisible(table.getCellRect(view, 0, true));
            }
          }

          StringBuilder summary = new StringBuilder();
          if (added > 0) {
            summary.append("Imported ").append(added).append(" alias");
            if (added != 1) summary.append('e').append('s');
            summary.append('.');
          } else {
            summary.append("No new aliases were imported.");
          }

          if (skippedExisting > 0) {
            summary.append("\nSkipped ").append(skippedExisting).append(" alias");
            if (skippedExisting != 1) summary.append('e').append('s');
            summary.append(" because the command name already exists.");
          }

          if (imported.mergedDuplicateCommands() > 0) {
            summary
                .append("\nMerged ")
                .append(imported.mergedDuplicateCommands())
                .append(" duplicate command");
            if (imported.mergedDuplicateCommands() != 1) summary.append('s');
            summary.append(" from HexChat.");
          }

          if (imported.translatedPlaceholders() > 0) {
            summary
                .append("\nTranslated ")
                .append(imported.translatedPlaceholders())
                .append(" HexChat placeholder");
            if (imported.translatedPlaceholders() != 1) summary.append('s');
            summary.append(" (%t/%m/%v).");
          }

          if (imported.skippedInvalidEntries() > 0) {
            summary
                .append("\nSkipped ")
                .append(imported.skippedInvalidEntries())
                .append(" invalid command name");
            if (imported.skippedInvalidEntries() != 1) summary.append('s');
            summary.append('.');
          }

          JOptionPane.showMessageDialog(
              SwingUtilities.getWindowAncestor(importHexChat),
              summary.toString(),
              "HexChat import complete",
              JOptionPane.INFORMATION_MESSAGE);
        });

    duplicate.addActionListener(
        e -> {
          if (table.isEditing()) {
            try {
              table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          int row = table.getSelectedRow();
          if (row < 0) return;
          int modelRow = table.convertRowIndexToModel(row);
          int dup = model.duplicateRow(modelRow);
          if (dup >= 0) {
            int view = table.convertRowIndexToView(dup);
            table.getSelectionModel().setSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
          }
        });

    remove.addActionListener(
        e -> {
          if (table.isEditing()) {
            try {
              table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          int row = table.getSelectedRow();
          if (row < 0) return;
          int res =
              JOptionPane.showConfirmDialog(
                  dialog, "Remove selected alias?", "Remove alias", JOptionPane.OK_CANCEL_OPTION);
          if (res != JOptionPane.OK_OPTION) return;
          int modelRow = table.convertRowIndexToModel(row);
          model.removeRow(modelRow);
        });

    up.addActionListener(
        e -> {
          if (table.isEditing()) {
            try {
              table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          int row = table.getSelectedRow();
          if (row <= 0) return;
          int modelRow = table.convertRowIndexToModel(row);
          int modelPrevRow = table.convertRowIndexToModel(row - 1);
          int next = model.moveRow(modelRow, modelPrevRow);
          if (next >= 0) {
            int view = table.convertRowIndexToView(next);
            table.getSelectionModel().setSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
          }
        });

    down.addActionListener(
        e -> {
          if (table.isEditing()) {
            try {
              table.getCellEditor().stopCellEditing();
            } catch (Exception ignored) {
            }
          }
          int row = table.getSelectedRow();
          if (row < 0 || row >= table.getRowCount() - 1) return;
          int modelRow = table.convertRowIndexToModel(row);
          int modelNextRow = table.convertRowIndexToModel(row + 1);
          int next = model.moveRow(modelRow, modelNextRow);
          if (next >= 0) {
            int view = table.convertRowIndexToView(next);
            table.getSelectionModel().setSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
          }
        });

    loadSelectedTemplate.run();
    return new UserCommandAliasesControls(
        table,
        model,
        template,
        unknownCommandAsRaw,
        add,
        importHexChat,
        duplicate,
        remove,
        up,
        down,
        hint);
  }

  private JPanel buildUserCommandsPanel(UserCommandAliasesControls controls) {
    JPanel panel =
        new JPanel(
            new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]8[]6[]8[grow,fill]8[]"));

    panel.add(tabTitle("Commands"), "growx, wmin 0, wrap");
    panel.add(sectionTitle("User command aliases"), "growx, wmin 0, wrap");
    panel.add(
        helpText(
            "Define custom /commands that expand before built-in parsing.\n"
                + "Placeholders: %1..%9 (positional), %1- (rest from arg), %* (all args), &1..&9 (from end), %c (channel), %t (target), %s/%e (server), %n (nick).\n"
                + "HexChat import maps %t (time), %m and %v into IRCafe-compatible placeholders.\n"
                + "Multi-command expansion: separate commands with ';' or new lines."),
        "growx, wmin 0, wrap");

    JPanel behavior = captionPanel("Behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    behavior.add(controls.unknownCommandAsRaw, "growx, wmin 0, wrap");
    panel.add(behavior, "growx, wmin 0, wrap");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(controls.add);
    buttons.add(controls.importHexChat);
    buttons.add(controls.duplicate);
    buttons.add(controls.remove);
    buttons.add(controls.up);
    buttons.add(controls.down);

    JScrollPane tableScroll = new JScrollPane(controls.table);
    tableScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    tableScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JScrollPane templateScroll = new JScrollPane(controls.template);
    templateScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    templateScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    JPanel aliasList =
        captionPanel("Alias list", "insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]");
    aliasList.add(buttons, "growx, wmin 0, wrap");
    aliasList.add(tableScroll, "grow, push, h 220!, wmin 0");
    panel.add(aliasList, "grow, push, wmin 0, wrap");

    JPanel editor =
        captionPanel("Expansion editor", "insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]");
    editor.add(controls.hint, "growx, wmin 0, wrap");
    editor.add(templateScroll, "growx, h 140!, wmin 0, wrap");
    panel.add(editor, "growx, wmin 0, wrap");

    return panel;
  }

  private DiagnosticsControls buildDiagnosticsControls() {
    JCheckBox assertjSwingEnabled = new JCheckBox("Enable AssertJ Swing diagnostics");
    assertjSwingEnabled.setSelected(runtimeConfig.readAppDiagnosticsAssertjSwingEnabled(true));
    assertjSwingEnabled.setToolTipText(
        "Installs AssertJ Swing (or a fallback detector) for EDT thread violation checks.");

    JCheckBox assertjSwingFreezeWatchdogEnabled = new JCheckBox("Enable EDT freeze watchdog");
    assertjSwingFreezeWatchdogEnabled.setSelected(
        runtimeConfig.readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(true));
    assertjSwingFreezeWatchdogEnabled.setToolTipText(
        "Reports prolonged Event Dispatch Thread stalls into Application -> AssertJ Swing.");

    int freezeThresholdMs = runtimeConfig.readAppDiagnosticsAssertjSwingFreezeThresholdMs(2500);
    JSpinner assertjSwingFreezeThresholdMs =
        new JSpinner(new SpinnerNumberModel(freezeThresholdMs, 500, 120_000, 100));

    int watchdogPollMs = runtimeConfig.readAppDiagnosticsAssertjSwingWatchdogPollMs(500);
    JSpinner assertjSwingWatchdogPollMs =
        new JSpinner(new SpinnerNumberModel(watchdogPollMs, 100, 10_000, 100));

    int fallbackViolationReportMs =
        runtimeConfig.readAppDiagnosticsAssertjSwingFallbackViolationReportMs(5000);
    JSpinner assertjSwingFallbackViolationReportMs =
        new JSpinner(new SpinnerNumberModel(fallbackViolationReportMs, 250, 120_000, 250));

    JCheckBox assertjSwingOnIssuePlaySound = new JCheckBox("Play sound when an issue is detected");
    assertjSwingOnIssuePlaySound.setSelected(
        runtimeConfig.readAppDiagnosticsAssertjSwingIssuePlaySound(false));
    assertjSwingOnIssuePlaySound.setToolTipText(
        "Uses the configured tray notification sound when EDT freeze/violation issues are detected.");

    JCheckBox assertjSwingOnIssueShowNotification =
        new JCheckBox("Show desktop notification when an issue is detected");
    assertjSwingOnIssueShowNotification.setSelected(
        runtimeConfig.readAppDiagnosticsAssertjSwingIssueShowNotification(false));
    assertjSwingOnIssueShowNotification.setToolTipText(
        "Uses the tray notification pipeline; desktop-notification delivery still follows tray settings.");

    JCheckBox jhiccupEnabled = new JCheckBox("Enable jHiccup process integration");
    jhiccupEnabled.setSelected(runtimeConfig.readAppDiagnosticsJhiccupEnabled(false));
    jhiccupEnabled.setToolTipText(
        "Runs an external jHiccup process and mirrors output into Application -> jHiccup.");

    JTextField jhiccupJarPath = new JTextField(runtimeConfig.readAppDiagnosticsJhiccupJarPath(""));
    jhiccupJarPath.setToolTipText(
        "Path to jHiccup jar file. Relative paths are resolved from the runtime-config directory.");

    JTextField jhiccupJavaCommand =
        new JTextField(runtimeConfig.readAppDiagnosticsJhiccupJavaCommand("java"));
    jhiccupJavaCommand.setToolTipText("Java launcher command used to start jHiccup.");

    JTextArea jhiccupArgs = new JTextArea(5, 40);
    jhiccupArgs.setLineWrap(false);
    jhiccupArgs.setWrapStyleWord(false);
    jhiccupArgs.setText(String.join("\n", runtimeConfig.readAppDiagnosticsJhiccupArgs(List.of())));
    jhiccupArgs.setToolTipText("One argument per line.");

    Runnable syncEnabledState =
        () -> {
          boolean assertjEnabled = assertjSwingEnabled.isSelected();
          assertjSwingFreezeWatchdogEnabled.setEnabled(assertjEnabled);
          boolean watchdogEnabled =
              assertjEnabled && assertjSwingFreezeWatchdogEnabled.isSelected();
          assertjSwingFreezeThresholdMs.setEnabled(watchdogEnabled);
          assertjSwingWatchdogPollMs.setEnabled(watchdogEnabled);
          assertjSwingFallbackViolationReportMs.setEnabled(assertjEnabled);
          assertjSwingOnIssuePlaySound.setEnabled(assertjEnabled);
          assertjSwingOnIssueShowNotification.setEnabled(assertjEnabled);
        };
    assertjSwingEnabled.addActionListener(e -> syncEnabledState.run());
    assertjSwingFreezeWatchdogEnabled.addActionListener(e -> syncEnabledState.run());
    syncEnabledState.run();

    return new DiagnosticsControls(
        assertjSwingEnabled,
        assertjSwingFreezeWatchdogEnabled,
        assertjSwingFreezeThresholdMs,
        assertjSwingWatchdogPollMs,
        assertjSwingFallbackViolationReportMs,
        assertjSwingOnIssuePlaySound,
        assertjSwingOnIssueShowNotification,
        jhiccupEnabled,
        jhiccupJarPath,
        jhiccupJavaCommand,
        jhiccupArgs);
  }

  private JPanel buildDiagnosticsPanel(DiagnosticsControls controls) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]8[]8[]"));

    panel.add(tabTitle("Diagnostics"), "growx, wmin 0, wrap");
    panel.add(
        helpText(
            "Configure optional application diagnostics integrations exposed under the Application tree node.\n"
                + "Startup-related changes apply after restarting IRCafe."),
        "growx, wmin 0, wrap");

    JPanel assertjPanel =
        captionPanel(
            "AssertJ Swing / EDT watchdog",
            "insets 0, fillx, wrap 2",
            "[right]10[grow,fill]",
            "[]4[]4[]4[]4[]4[]");
    assertjPanel.add(controls.assertjSwingEnabled, "span 2, growx, wmin 0, wrap");
    assertjPanel.add(
        controls.assertjSwingFreezeWatchdogEnabled, "span 2, growx, wmin 0, gapleft 14, wrap");
    assertjPanel.add(new JLabel("Freeze threshold (ms)"), "gapleft 24");
    assertjPanel.add(controls.assertjSwingFreezeThresholdMs, "w 140!");
    assertjPanel.add(new JLabel("Watchdog poll (ms)"), "gapleft 24");
    assertjPanel.add(controls.assertjSwingWatchdogPollMs, "w 140!");
    assertjPanel.add(new JLabel("Fallback violation report interval (ms)"), "gapleft 24");
    assertjPanel.add(controls.assertjSwingFallbackViolationReportMs, "w 140!");
    assertjPanel.add(
        controls.assertjSwingOnIssuePlaySound, "span 2, growx, wmin 0, gapleft 24, wrap");
    assertjPanel.add(
        controls.assertjSwingOnIssueShowNotification, "span 2, growx, wmin 0, gapleft 24, wrap");
    assertjPanel.add(
        helpText(
            "Watchdog logs stalls when EDT lag exceeds the threshold. Fallback interval controls how often "
                + "off-EDT Swing violations are re-reported."),
        "span 2, gapleft 24, growx, wrap");
    panel.add(assertjPanel, "growx, wmin 0, wrap");

    JScrollPane argsScroll = new JScrollPane(controls.jhiccupArgs);
    argsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    argsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    JPanel jhiccupPanel =
        captionPanel(
            "jHiccup integration",
            "insets 0, fillx, wrap 2",
            "[right]10[grow,fill]",
            "[]4[]4[]4[]");
    jhiccupPanel.add(controls.jhiccupEnabled, "span 2, growx, wmin 0, wrap");
    jhiccupPanel.add(new JLabel("jHiccup jar"), "aligny top");
    jhiccupPanel.add(controls.jhiccupJarPath, "growx, wmin 0, wrap");
    jhiccupPanel.add(new JLabel("Java command"), "aligny top");
    jhiccupPanel.add(controls.jhiccupJavaCommand, "growx, wmin 0, wrap");
    jhiccupPanel.add(new JLabel("Arguments"), "aligny top");
    jhiccupPanel.add(argsScroll, "growx, wmin 0, h 110!, wrap");
    jhiccupPanel.add(
        helpText(
            "One argument per line. Example flags: -i 1000, -l 2000000.\n"
                + "Relative jar paths are resolved from the runtime-config directory."),
        "span 2, growx, wmin 0, wrap");
    panel.add(jhiccupPanel, "growx, wmin 0, wrap");

    return panel;
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

  private static String normalizeAliasCommandKey(String raw) {
    String command = Objects.toString(raw, "").trim();
    if (command.startsWith("/")) command = command.substring(1).trim();
    int split = command.indexOf(' ');
    if (split >= 0) command = command.substring(0, split).trim();
    return command.toLowerCase(Locale.ROOT);
  }

  private static File suggestedHexChatCommandsConfFile() {
    String home = Objects.toString(System.getProperty("user.home"), "").trim();
    if (home.isEmpty()) return null;

    Path userHome = Path.of(home);
    List<Path> candidates =
        List.of(
            userHome.resolve(".config").resolve("hexchat").resolve("commands.conf"),
            userHome.resolve(".xchat2").resolve("commands.conf"),
            userHome
                .resolve("AppData")
                .resolve("Roaming")
                .resolve("HexChat")
                .resolve("commands.conf"));

    for (Path p : candidates) {
      if (p != null && Files.isRegularFile(p)) return p.toFile();
    }
    return candidates.get(0).toFile();
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

  private record ValidationError(int rowIndex, String label, String pattern, String message) {

    String effectiveLabel() {
      String l = label != null ? label.trim() : "";
      if (!l.isEmpty()) return l;
      String p = pattern != null ? pattern.trim() : "";
      return p.isEmpty() ? "(unnamed)" : p;
    }

    String formatForInline() {
      String msg = message != null ? message.trim() : "Invalid regex";
      if (msg.length() > 180) msg = msg.substring(0, 180) + "…";
      return "Invalid REGEX (row " + (rowIndex + 1) + ", " + effectiveLabel() + "): " + msg;
    }

    String formatForDialog() {
      String msg = message != null ? message.trim() : "Invalid regex";
      return "Row "
          + (rowIndex + 1)
          + " ("
          + effectiveLabel()
          + "):\n"
          + msg
          + "\n\nPattern:\n"
          + (pattern != null ? pattern : "");
    }
  }

  private record UserCommandAliasValidationError(int rowIndex, String command, String message) {

    String formatForDialog() {
      String cmd = Objects.toString(command, "").trim();
      if (cmd.isEmpty()) cmd = "(blank)";
      String msg = Objects.toString(message, "Invalid alias").trim();
      return "Row " + (rowIndex + 1) + " (/" + cmd + "):\n" + msg;
    }
  }

  private static final class RuleTestRunner implements AutoCloseable {

    private static final int MAX_TEST_CHARS = 800;

    private final ExecutorService exec;
    private final AtomicLong seq = new AtomicLong();

    RuleTestRunner() {
      this.exec = VirtualThreads.newSingleThreadExecutor("ircafe-notification-rule-test");
    }

    void runTest(NotificationRulesControls controls) {
      if (controls == null) return;

      String sample = controls.testInput.getText();
      if (sample == null) sample = "";
      if (sample.length() > MAX_TEST_CHARS) {
        sample = sample.substring(0, MAX_TEST_CHARS);
      }

      List<NotificationRule> rules = controls.model.snapshot();
      List<ValidationError> errors = controls.model.validationErrors();

      long token = seq.incrementAndGet();
      controls.testStatus.setText("Testing…");

      final String sampleFinal = sample;
      exec.submit(
          () -> {
            String report = buildRuleTestReport(rules, errors, sampleFinal);
            SwingUtilities.invokeLater(
                () -> {
                  if (seq.get() != token) return;
                  controls.testOutput.setText(report);
                  controls.testOutput.setCaretPosition(0);
                  controls.testStatus.setText(" ");
                });
          });
    }

    @Override
    public void close() {
      exec.shutdownNow();
    }
  }

  private static String buildRuleTestReport(
      List<NotificationRule> rules, List<ValidationError> errors, String sample) {
    String msg = sample != null ? sample : "";
    msg = msg.trim();
    if (msg.isEmpty()) {
      return "Type a sample message above, then click Test.";
    }

    StringBuilder out = new StringBuilder();

    if (errors != null && !errors.isEmpty()) {
      out.append("Invalid REGEX rules (ignored):\n");
      int shown = 0;
      for (ValidationError e : errors) {
        if (e == null) continue;
        out.append("  - row ")
            .append(e.rowIndex + 1)
            .append(": ")
            .append(e.effectiveLabel())
            .append("\n");
        shown++;
        if (shown >= 5) {
          int remain = errors.size() - shown;
          if (remain > 0) out.append("  (").append(remain).append(" more)\n");
          break;
        }
      }
      out.append("\n");
    }

    List<String> matches = new ArrayList<>();
    List<String> invalidRegex = new ArrayList<>();

    for (NotificationRule r : (rules != null ? rules : List.<NotificationRule>of())) {
      if (r == null) continue;
      if (!r.enabled()) continue;
      String pat = r.pattern() != null ? r.pattern().trim() : "";
      if (pat.isEmpty()) continue;

      if (r.type() == NotificationRule.Type.REGEX) {
        Pattern p;
        try {
          int flags = Pattern.UNICODE_CASE;
          if (!r.caseSensitive()) flags |= Pattern.CASE_INSENSITIVE;
          p = Pattern.compile(pat, flags);
        } catch (Exception ex) {
          invalidRegex.add(r.label());
          continue;
        }

        Matcher m = p.matcher(msg);
        if (m.find()) {
          String snip = snippetAround(msg, m.start(), m.end());
          matches.add(lineFor(r, snip));
        }
      } else {
        RuleMatch m = findWordMatch(msg, pat, r.caseSensitive(), r.wholeWord());
        if (m != null) {
          String snip = snippetAround(msg, m.start, m.end);
          matches.add(lineFor(r, snip));
        }
      }
    }

    if (!invalidRegex.isEmpty() && (errors == null || errors.isEmpty())) {
      // This can happen if the errors list was stale; include a brief warning anyway.
      out.append("Some REGEX rules are invalid and were ignored.\n\n");
    }

    if (matches.isEmpty()) {
      out.append("No matches.");
    } else {
      out.append("Matches (").append(matches.size()).append("):\n");
      for (String l : matches) {
        out.append("  ").append(l).append("\n");
      }
    }

    return out.toString().trim();
  }

  private static String lineFor(NotificationRule rule, String snippet) {
    String label =
        (rule.label() != null && !rule.label().trim().isEmpty())
            ? rule.label().trim()
            : (rule.pattern() != null ? rule.pattern().trim() : "(unnamed)");
    return "- " + label + " [" + rule.type() + "]: " + snippet;
  }

  private static String snippetAround(String msg, int start, int end) {
    if (msg == null) return "";
    int len = msg.length();
    if (start < 0) start = 0;
    if (end < start) end = start;
    if (end > len) end = len;

    int ctx = 30;
    int s = Math.max(0, start - ctx);
    int e = Math.min(len, end + ctx);

    String prefix = s > 0 ? "…" : "";
    String suffix = e < len ? "…" : "";

    String before = msg.substring(s, start);
    String mid = msg.substring(start, end);
    String after = msg.substring(end, e);

    return prefix + collapseWs(before) + "[" + collapseWs(mid) + "]" + collapseWs(after) + suffix;
  }

  private static String collapseWs(String s) {
    if (s == null || s.isEmpty()) return "";
    return s.replaceAll("\\s+", " ");
  }

  private static RuleMatch findWordMatch(
      String msg, String pat, boolean caseSensitive, boolean wholeWord) {
    if (msg == null || pat == null) return null;
    if (pat.isEmpty()) return null;

    if (wholeWord) {
      int plen = pat.length();
      for (Token tok : tokenize(msg)) {
        int tlen = tok.end - tok.start;
        if (tlen != plen) continue;

        boolean ok =
            caseSensitive
                ? msg.regionMatches(false, tok.start, pat, 0, plen)
                : msg.regionMatches(true, tok.start, pat, 0, plen);

        if (ok) return new RuleMatch(tok.start, tok.end);
      }
      return null;
    }

    int idx;
    if (caseSensitive) {
      idx = msg.indexOf(pat);
    } else {
      idx = msg.toLowerCase(Locale.ROOT).indexOf(pat.toLowerCase(Locale.ROOT));
    }
    if (idx < 0) return null;
    return new RuleMatch(idx, idx + pat.length());
  }

  private static List<Token> tokenize(String message) {
    int len = message.length();
    if (len == 0) return List.of();

    List<Token> toks = new ArrayList<>();
    int i = 0;

    while (i < len) {
      while (i < len && !isWordChar(message.charAt(i))) i++;
      if (i >= len) break;
      int start = i;
      while (i < len && isWordChar(message.charAt(i))) i++;
      int end = i;
      toks.add(new Token(start, end));
    }

    return toks;
  }

  private static boolean isWordChar(char ch) {
    if (ch >= '0' && ch <= '9') return true;
    if (ch >= 'A' && ch <= 'Z') return true;
    if (ch >= 'a' && ch <= 'z') return true;
    return ch == '_' || ch == '-';
  }

  private record Token(int start, int end) {}

  private record RuleMatch(int start, int end) {}

  private static String normalizeThemeIdInternal(String id) {
    return ThemeIdUtils.normalizeThemeId(id);
  }

  private static boolean sameThemeInternal(String a, String b) {
    return ThemeIdUtils.sameTheme(a, b);
  }

  private static boolean supportsFlatLafTweaksInternal(String themeId) {
    return ThemeIdUtils.isLikelyFlatTarget(themeId);
  }

  private static void configureIconOnlyButton(JButton button, String iconName, String tooltip) {
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

  private static JSpinner numberSpinner(
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
    if (c == null) return "";
    return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
  }

  private static Color parseHexColor(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;
    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
    if (s.length() != 6) return null;
    try {
      int rgb = Integer.parseInt(s, 16);
      return new Color(rgb);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static final int MAX_RECENT_COLORS = 12;
  private static final Deque<String> RECENT_COLOR_HEX = new ArrayDeque<>();

  private static Color parseHexColorLenient(String raw) {
    Color c = parseHexColor(raw);
    if (c != null) return c;
    if (raw == null) return null;

    String s = raw.trim();
    if (s.startsWith("#")) s = s.substring(1).trim();
    if (s.length() != 3) return null;

    char r = s.charAt(0);
    char g = s.charAt(1);
    char b = s.charAt(2);
    return parseHexColor("#" + r + r + g + g + b + b);
  }

  static String normalizeOptionalHexForApply(String raw, String fieldLabel) {
    String hex = raw != null ? raw.trim() : "";
    if (hex.isBlank()) return null;
    Color c = parseHexColorLenient(hex);
    if (c == null) {
      String label = Objects.toString(fieldLabel, "Color");
      throw new IllegalArgumentException(
          label + " must be a hex value like #RRGGBB (or blank for default).");
    }
    return toHex(c);
  }

  private static Color contrastTextColor(Color bg) {
    if (bg == null) return UIManager.getColor("Label.foreground");
    // Relative luminance (sRGB-ish) for a quick black/white choice.
    double r = bg.getRed() / 255.0;
    double g = bg.getGreen() / 255.0;
    double b = bg.getBlue() / 255.0;
    double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    return y < 0.55 ? Color.WHITE : Color.BLACK;
  }

  private static Color preferredPreviewBackground() {
    Color bg = UIManager.getColor("TextPane.background");
    if (bg == null) bg = UIManager.getColor("TextArea.background");
    if (bg == null) bg = UIManager.getColor("Table.background");
    if (bg == null) bg = UIManager.getColor("Panel.background");
    return bg != null ? bg : new Color(30, 30, 30);
  }

  private static Icon createColorSwatchIcon(Color color, int w, int h) {
    // Simple swatch icon used in compact pickers/buttons.
    return new ColorSwatch(color, w, h);
  }

  private NotificationRule promptNotificationRuleDialog(String title, NotificationRule seed) {
    NotificationRule base =
        seed != null
            ? seed
            : new NotificationRule("", NotificationRule.Type.WORD, "", true, false, true, null);

    JCheckBox enabled = new JCheckBox("Enabled", base.enabled());
    JTextField label = new JTextField(Objects.toString(base.label(), ""));
    JComboBox<NotificationRule.Type> type = new JComboBox<>(NotificationRule.Type.values());
    type.setSelectedItem(base.type() != null ? base.type() : NotificationRule.Type.WORD);

    JTextField pattern = new JTextField(Objects.toString(base.pattern(), ""));
    pattern.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, "Keyword or regular expression");
    JCheckBox caseSensitive = new JCheckBox("Case sensitive", base.caseSensitive());
    JCheckBox wholeWord = new JCheckBox("Whole word", base.wholeWord());
    wholeWord.setToolTipText("Only applies to WORD rules.");

    Color seedColor = parseHexColorLenient(base.highlightFg());
    final String[] colorHex = new String[] {seedColor != null ? toHex(seedColor) : null};
    JLabel colorPreview = new JLabel();
    JButton pickColor = new JButton("Choose…");
    JButton clearColor = new JButton("Clear");
    pickColor.setIcon(SvgIcons.action("palette", 14));
    pickColor.setDisabledIcon(SvgIcons.actionDisabled("palette", 14));
    clearColor.setIcon(SvgIcons.action("close", 14));
    clearColor.setDisabledIcon(SvgIcons.actionDisabled("close", 14));

    Runnable refreshWholeWordState =
        () -> {
          boolean wordRule = NotificationRule.Type.WORD.equals(type.getSelectedItem());
          wholeWord.setEnabled(wordRule);
          if (!wordRule) {
            wholeWord.setSelected(false);
          }
        };
    type.addActionListener(e -> refreshWholeWordState.run());
    refreshWholeWordState.run();

    Runnable refreshColorPreview =
        () -> {
          Color c = parseHexColorLenient(colorHex[0]);
          if (c == null) {
            colorPreview.setIcon(null);
            colorPreview.setText("Default");
            Color fg = UIManager.getColor("Label.foreground");
            if (fg != null) colorPreview.setForeground(fg);
            return;
          }
          colorPreview.setIcon(new ColorSwatch(c, 14, 14));
          colorPreview.setText(toHex(c));
          Color fg = UIManager.getColor("Label.foreground");
          if (fg != null) colorPreview.setForeground(fg);
        };
    refreshColorPreview.run();

    pickColor.addActionListener(
        e -> {
          Color current = parseHexColorLenient(colorHex[0]);
          if (current == null) {
            Color fallback = UIManager.getColor("TextPane.foreground");
            if (fallback == null) fallback = UIManager.getColor("Label.foreground");
            current = fallback != null ? fallback : Color.WHITE;
          }
          Color chosen =
              showColorPickerDialog(
                  dialog, "Choose Rule Highlight Color", current, preferredPreviewBackground());
          if (chosen == null) return;
          colorHex[0] = toHex(chosen);
          refreshColorPreview.run();
        });

    clearColor.addActionListener(
        e -> {
          colorHex[0] = null;
          refreshColorPreview.run();
        });

    JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    colorRow.setOpaque(false);
    colorRow.add(colorPreview);
    colorRow.add(pickColor);
    colorRow.add(clearColor);

    JTextArea hint =
        helpText("WORD supports whole-word matching; REGEX supports Java regular expressions.");

    JPanel form =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 2,hidemode 3", "[right][grow,fill]", "[]6[]6[]6[]6[]6[]6[]"));
    form.add(enabled, "span 2,wrap");
    form.add(new JLabel("Label:"));
    form.add(label, "growx,pushx,wmin 0,wrap");
    form.add(new JLabel("Type:"));
    form.add(type, "w 140!,wrap");
    form.add(new JLabel("Pattern:"));
    form.add(pattern, "growx,pushx,wmin 0,wrap");
    form.add(new JLabel("Options:"));
    JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    options.setOpaque(false);
    options.add(caseSensitive);
    options.add(wholeWord);
    form.add(options, "growx,wrap");
    form.add(new JLabel("Color:"));
    form.add(colorRow, "growx,wrap");
    form.add(new JLabel(""));
    form.add(hint, "growx,wmin 0,wrap");

    Window owner = dialog != null ? dialog : null;
    String dialogTitle = Objects.toString(title, "Notification Rule");

    while (true) {
      int choice =
          JOptionPane.showConfirmDialog(
              owner, form, dialogTitle, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (choice != JOptionPane.OK_OPTION) return null;

      NotificationRule.Type selectedType =
          type.getSelectedItem() instanceof NotificationRule.Type t
              ? t
              : NotificationRule.Type.WORD;

      String patternText = Objects.toString(pattern.getText(), "").trim();
      if (selectedType == NotificationRule.Type.REGEX && !patternText.isEmpty()) {
        try {
          int flags = Pattern.UNICODE_CASE;
          if (!caseSensitive.isSelected()) flags |= Pattern.CASE_INSENSITIVE;
          Pattern.compile(patternText, flags);
        } catch (Exception ex) {
          String msg = Objects.toString(ex.getMessage(), "Invalid regular expression");
          JOptionPane.showMessageDialog(
              owner,
              "Invalid REGEX pattern:\n" + msg,
              "Invalid Notification Rule",
              JOptionPane.ERROR_MESSAGE);
          continue;
        }
      }

      return new NotificationRule(
          label.getText(),
          selectedType,
          patternText,
          enabled.isSelected(),
          caseSensitive.isSelected(),
          selectedType == NotificationRule.Type.WORD && wholeWord.isSelected(),
          colorHex[0]);
    }
  }

  private static void rememberRecentColorHex(String hex) {
    if (hex == null) return;
    String s = hex.trim().toUpperCase(Locale.ROOT);
    if (s.isEmpty()) return;
    if (!s.startsWith("#")) s = "#" + s;
    if (s.length() == 4) {
      // Expand #RGB -> #RRGGBB
      char r = s.charAt(1);
      char g = s.charAt(2);
      char b = s.charAt(3);
      s = "#" + r + r + g + g + b + b;
    }
    if (s.length() != 7) return;

    final String needle = s;

    synchronized (RECENT_COLOR_HEX) {
      RECENT_COLOR_HEX.removeIf(v -> v != null && v.equalsIgnoreCase(needle));
      RECENT_COLOR_HEX.addFirst(needle);
      while (RECENT_COLOR_HEX.size() > MAX_RECENT_COLORS) {
        RECENT_COLOR_HEX.removeLast();
      }
    }
  }

  private static List<String> snapshotRecentColorHex() {
    synchronized (RECENT_COLOR_HEX) {
      return new ArrayList<>(RECENT_COLOR_HEX);
    }
  }

  private static JButton colorSwatchButton(Color c, Consumer<Color> onPick) {
    JButton b = new JButton();
    b.setFocusable(false);
    b.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    b.setContentAreaFilled(false);
    b.setIcon(new ColorSwatch(c, 18, 18));
    b.setToolTipText(toHex(c));
    b.addActionListener(
        e -> {
          if (onPick != null) onPick.accept(c);
        });
    return b;
  }

  private static double contrastRatio(Color fg, Color bg) {
    if (fg == null || bg == null) return 0.0;

    double l1 = relativeLuminance(fg);
    double l2 = relativeLuminance(bg);
    if (l1 < l2) {
      double t = l1;
      l1 = l2;
      l2 = t;
    }
    return (l1 + 0.05) / (l2 + 0.05);
  }

  private static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed());
    double g = srgbToLinear(c.getGreen());
    double b = srgbToLinear(c.getBlue());
    return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
  }

  private static double srgbToLinear(int channel) {
    double v = channel / 255.0;
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }

  private static Color showColorPickerDialog(
      Window owner, String title, Color initial, Color previewBackground) {
    Color bg = previewBackground != null ? previewBackground : preferredPreviewBackground();
    Color init = initial != null ? initial : Color.WHITE;

    // A compact "hex + palette + preview" picker; way less chaotic than the stock Swing chooser.
    final JDialog d = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
    d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

    final Color[] current = new Color[] {init};
    final Color[] result = new Color[1];

    JLabel preview = new JLabel(" IRCafe preview ");
    preview.setOpaque(true);
    preview.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    preview.setBackground(bg);

    JLabel contrast = new JLabel();
    contrast.setFont(UIManager.getFont("Label.smallFont"));

    JTextField hex = new JTextField(toHex(init), 10);
    hex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#RRGGBB");

    JLabel hexStatus = new JLabel(" ");
    hexStatus.setFont(UIManager.getFont("Label.smallFont"));

    JButton more = new JButton("More…");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");

    final boolean[] internalUpdate = new boolean[] {false};

    Runnable updatePreview =
        () -> {
          Color fg = current[0];
          preview.setForeground(fg);
          preview.setText(" IRCafe preview  " + toHex(fg));
          double cr = contrastRatio(fg, bg);
          String verdict = cr >= 4.5 ? "OK" : (cr >= 3.0 ? "Low" : "Bad");
          contrast.setText(String.format(Locale.ROOT, "Contrast: %.1f (%s)", cr, verdict));
          ok.setEnabled(fg != null);
        };

    Consumer<Color> setColor =
        c -> {
          if (c == null) return;
          current[0] = c;
          internalUpdate[0] = true;
          hex.setText(toHex(c));
          internalUpdate[0] = false;
          hexStatus.setText(" ");
          updatePreview.run();
        };

    hex.getDocument()
        .addDocumentListener(
            new SimpleDocListener(
                () -> {
                  if (internalUpdate[0]) return;

                  Color parsed = parseHexColorLenient(hex.getText());
                  if (parsed == null) {
                    hexStatus.setText("Invalid hex (use #RRGGBB or #RGB)");
                    ok.setEnabled(false);
                    return;
                  }
                  current[0] = parsed;
                  hexStatus.setText(" ");
                  updatePreview.run();
                }));

    JPanel palette = new JPanel(new MigLayout("insets 0, wrap 8, gap 6", "[]", "[]"));
    Color[] colors =
        new Color[] {
          new Color(0xFFFFFF), new Color(0xD9D9D9), new Color(0xA6A6A6), new Color(0x4D4D4D),
              new Color(0x000000), new Color(0xFF6B6B), new Color(0xFFA94D), new Color(0xFFD43B),
          new Color(0x69DB7C), new Color(0x38D9A9), new Color(0x22B8CF), new Color(0x4DABF7),
              new Color(0x748FFC), new Color(0x9775FA), new Color(0xDA77F2), new Color(0xF783AC),
          new Color(0xC92A2A), new Color(0xE8590C), new Color(0xF08C00), new Color(0x2F9E44),
              new Color(0x0CA678), new Color(0x1098AD), new Color(0x1971C2), new Color(0x5F3DC4)
        };
    for (Color c : colors) {
      palette.add(colorSwatchButton(c, setColor));
    }

    JPanel recent = new JPanel(new MigLayout("insets 0, wrap 8, gap 6", "[]", "[]"));
    Runnable refreshRecent =
        () -> {
          recent.removeAll();
          List<String> rec = snapshotRecentColorHex();
          if (rec.isEmpty()) {
            recent.add(helpText("No recent colors yet."), "span 8");
          } else {
            for (String hx : rec) {
              Color c = parseHexColorLenient(hx);
              if (c == null) continue;
              recent.add(colorSwatchButton(c, setColor));
            }
          }
          recent.revalidate();
          recent.repaint();
        };
    refreshRecent.run();

    more.addActionListener(
        e -> {
          Color picked =
              JColorChooser.showDialog(d, "More Colors", current[0] != null ? current[0] : init);
          if (picked != null) setColor.accept(picked);
        });

    ok.addActionListener(
        e -> {
          if (current[0] == null) return;
          result[0] = current[0];
          rememberRecentColorHex(toHex(current[0]));
          d.dispose();
        });

    cancel.addActionListener(
        e -> {
          result[0] = null;
          d.dispose();
        });

    JPanel content =
        new JPanel(
            new MigLayout(
                "insets 12, fillx, wrap 2", "[grow,fill]12[grow,fill]", "[]10[]6[]10[]6[]10[]"));
    content.add(preview, "span 2, growx, wrap");
    content.add(contrast, "span 2, growx, wrap");

    content.add(new JLabel("Hex"));
    JPanel hexRow =
        new JPanel(
            new MigLayout("insets 0, fillx, wrap 3", "[grow,fill]6[nogrid]6[nogrid]", "[]2[]"));
    hexRow.setOpaque(false);
    hexRow.add(hex, "w 110!");
    hexRow.add(more);
    hexRow.add(new JLabel(), "push");
    hexRow.add(hexStatus, "span 3, growx");
    content.add(hexRow, "growx, wrap");

    content.add(new JLabel("Palette"), "aligny top");
    content.add(palette, "growx, wrap");

    content.add(new JLabel("Recent"), "aligny top");
    content.add(recent, "growx, wrap");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.add(cancel);
    buttons.add(ok);

    JPanel outer = new JPanel(new BorderLayout());
    outer.add(content, BorderLayout.CENTER);
    outer.add(buttons, BorderLayout.SOUTH);

    d.setContentPane(outer);
    d.getRootPane().setDefaultButton(ok);
    d.getRootPane()
        .registerKeyboardAction(
            ev -> cancel.doClick(),
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

    updatePreview.run();
    d.pack();
    d.setLocationRelativeTo(owner);
    d.setVisible(true);

    return result[0];
  }

  private enum LookupRatePreset {
    CONSERVATIVE("Conservative"),
    BALANCED("Balanced"),
    RAPID("Rapid"),
    CUSTOM("Custom");

    private final String label;

    LookupRatePreset(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private enum IrcEventNotificationPreset {
    ESSENTIAL("Essential alerts (Recommended)"),
    MODERATION("Moderation focused"),
    ALL_EVENTS("All events");

    private final String label;

    IrcEventNotificationPreset(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static LookupRatePreset detectLookupRatePreset(UiSettings s) {
    if (matchesLookupRatePreset(s, LookupRatePreset.BALANCED)) return LookupRatePreset.BALANCED;
    if (matchesLookupRatePreset(s, LookupRatePreset.CONSERVATIVE))
      return LookupRatePreset.CONSERVATIVE;
    if (matchesLookupRatePreset(s, LookupRatePreset.RAPID)) return LookupRatePreset.RAPID;
    return LookupRatePreset.CUSTOM;
  }

  private static boolean matchesLookupRatePreset(UiSettings s, LookupRatePreset preset) {
    return switch (preset) {
      case CONSERVATIVE ->
          (s.userhostMinIntervalSeconds() == 10
              && s.userhostMaxCommandsPerMinute() == 2
              && s.userhostNickCooldownMinutes() == 60
              && s.userhostMaxNicksPerCommand() == 5
              && s.userInfoEnrichmentUserhostMinIntervalSeconds() == 30
              && s.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 2
              && s.userInfoEnrichmentUserhostNickCooldownMinutes() == 180
              && s.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5
              && s.userInfoEnrichmentWhoisMinIntervalSeconds() == 120
              && s.userInfoEnrichmentWhoisNickCooldownMinutes() == 240
              && s.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 600
              && s.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 1);
      case BALANCED ->
          (s.userhostMinIntervalSeconds() == 5
              && s.userhostMaxCommandsPerMinute() == 6
              && s.userhostNickCooldownMinutes() == 30
              && s.userhostMaxNicksPerCommand() == 5
              && s.userInfoEnrichmentUserhostMinIntervalSeconds() == 15
              && s.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 4
              && s.userInfoEnrichmentUserhostNickCooldownMinutes() == 60
              && s.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5
              && s.userInfoEnrichmentWhoisMinIntervalSeconds() == 60
              && s.userInfoEnrichmentWhoisNickCooldownMinutes() == 120
              && s.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 300
              && s.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 2);
      case RAPID ->
          (s.userhostMinIntervalSeconds() == 2
              && s.userhostMaxCommandsPerMinute() == 15
              && s.userhostNickCooldownMinutes() == 10
              && s.userhostMaxNicksPerCommand() == 5
              && s.userInfoEnrichmentUserhostMinIntervalSeconds() == 5
              && s.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 10
              && s.userInfoEnrichmentUserhostNickCooldownMinutes() == 15
              && s.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5
              && s.userInfoEnrichmentWhoisMinIntervalSeconds() == 15
              && s.userInfoEnrichmentWhoisNickCooldownMinutes() == 30
              && s.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 60
              && s.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 3);
      default -> false;
    };
  }

  private record NotificationRulesControls(
      JSpinner cooldownSeconds,
      JTable table,
      NotificationRulesTableModel model,
      JLabel validationLabel,
      JTextArea testInput,
      JTextArea testOutput,
      JLabel testStatus,
      RuleTestRunner testRunner) {}

  private record IrcEventNotificationControls(JTable table, IrcEventNotificationTableModel model) {}

  private record UserCommandAliasesControls(
      JTable table,
      UserCommandAliasesTableModel model,
      JTextArea template,
      JCheckBox unknownCommandAsRaw,
      JButton add,
      JButton importHexChat,
      JButton duplicate,
      JButton remove,
      JButton up,
      JButton down,
      JLabel hint) {}

  private record DiagnosticsControls(
      JCheckBox assertjSwingEnabled,
      JCheckBox assertjSwingFreezeWatchdogEnabled,
      JSpinner assertjSwingFreezeThresholdMs,
      JSpinner assertjSwingWatchdogPollMs,
      JSpinner assertjSwingFallbackViolationReportMs,
      JCheckBox assertjSwingOnIssuePlaySound,
      JCheckBox assertjSwingOnIssueShowNotification,
      JCheckBox jhiccupEnabled,
      JTextField jhiccupJarPath,
      JTextField jhiccupJavaCommand,
      JTextArea jhiccupArgs) {}

  private record TypingTreeIndicatorStyleOption(String id, String label) {}

  private static final class IrcEventNotificationTableModel extends AbstractTableModel {
    static final int COL_ENABLED = 0;
    static final int COL_EVENT = 1;
    static final int COL_SOURCE_SUMMARY = 2;
    static final int COL_CHANNEL_SUMMARY = 3;
    static final int COL_ACTIONS_SUMMARY = 4;

    private static final String[] COLS =
        new String[] {"Enabled", "Event", "Source", "Channel", "Actions"};

    private final List<MutableRule> rows = new ArrayList<>();

    IrcEventNotificationTableModel(List<IrcEventNotificationRule> initial) {
      if (initial != null) {
        for (IrcEventNotificationRule r : initial) {
          if (r == null) continue;
          rows.add(MutableRule.from(r));
        }
      }
    }

    List<IrcEventNotificationRule> snapshot() {
      return rows.stream().map(MutableRule::toRule).toList();
    }

    IrcEventNotificationRule ruleAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      MutableRule m = rows.get(row);
      return m != null ? m.toRule() : null;
    }

    void setRule(int row, IrcEventNotificationRule rule) {
      if (row < 0 || row >= rows.size()) return;
      rows.set(row, MutableRule.from(rule));
      fireTableRowsUpdated(row, row);
    }

    void setEnabledAt(int row, boolean enabled) {
      if (row < 0 || row >= rows.size()) return;
      MutableRule current = rows.get(row);
      if (current == null || current.enabled == enabled) return;
      current.enabled = enabled;
      fireTableRowsUpdated(row, row);
    }

    static String effectiveRuleLabel(IrcEventNotificationRule rule) {
      if (rule == null) return "(rule)";
      String event = rule.eventType() != null ? Objects.toString(rule.eventType(), "").trim() : "";
      String source =
          rule.sourceMode() != null ? Objects.toString(rule.sourceMode(), "").trim() : "";
      if (event.isEmpty()) event = "Event";
      if (source.isEmpty()) return event;
      return event + " (" + source + ")";
    }

    int addRule(IrcEventNotificationRule rule) {
      rows.add(MutableRule.from(rule));
      int idx = rows.size() - 1;
      fireTableRowsInserted(idx, idx);
      return idx;
    }

    int duplicateRow(int row) {
      if (row < 0 || row >= rows.size()) return -1;
      MutableRule src = rows.get(row);
      MutableRule copy = src.copy();
      int idx = Math.min(rows.size(), row + 1);
      rows.add(idx, copy);
      fireTableRowsInserted(idx, idx);
      return idx;
    }

    void removeRow(int row) {
      if (row < 0 || row >= rows.size()) return;
      rows.remove(row);
      fireTableRowsDeleted(row, row);
    }

    int moveRow(int from, int to) {
      if (from < 0 || from >= rows.size()) return -1;
      if (to < 0 || to >= rows.size()) return -1;
      if (from == to) return from;
      MutableRule r = rows.remove(from);
      rows.add(to, r);
      fireTableDataChanged();
      return to;
    }

    int firstRowForEvent(IrcEventNotificationRule.EventType eventType) {
      if (eventType == null) return -1;
      for (int i = 0; i < rows.size(); i++) {
        MutableRule r = rows.get(i);
        if (r == null) continue;
        if (r.eventType == eventType) return i;
      }
      return -1;
    }

    void applyPreset(List<IrcEventNotificationRule> presetRules) {
      if (presetRules == null || presetRules.isEmpty()) return;
      for (IrcEventNotificationRule rule : presetRules) {
        if (rule == null) continue;
        int idx = firstRowForEvent(rule.eventType());
        if (idx >= 0) {
          rows.set(idx, MutableRule.from(rule));
        } else {
          rows.add(MutableRule.from(rule));
        }
      }
      fireTableDataChanged();
    }

    void replaceAll(List<IrcEventNotificationRule> replacement) {
      rows.clear();
      if (replacement != null) {
        for (IrcEventNotificationRule rule : replacement) {
          if (rule == null) continue;
          rows.add(MutableRule.from(rule));
        }
      }
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLS.length;
    }

    @Override
    public String getColumnName(int column) {
      if (column < 0 || column >= COLS.length) return "";
      return COLS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == COL_ENABLED ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return null;
      MutableRule r = rows.get(rowIndex);
      return switch (columnIndex) {
        case COL_ENABLED -> r.enabled;
        case COL_EVENT -> Objects.toString(r.eventType, "");
        case COL_SOURCE_SUMMARY -> summarizeSource(r);
        case COL_CHANNEL_SUMMARY -> summarizeChannel(r);
        case COL_ACTIONS_SUMMARY -> summarizeActions(r);
        default -> null;
      };
    }

    private static String summarizeSource(MutableRule r) {
      if (r == null) return "";
      IrcEventNotificationRule.SourceMode mode =
          r.sourceMode != null ? r.sourceMode : IrcEventNotificationRule.SourceMode.ANY;
      String label = Objects.toString(mode, "");
      if (!sourcePatternAllowed(mode)) return label;
      String pattern = trimToNull(r.sourcePattern);
      return pattern == null ? label + ": (empty)" : label + ": " + truncate(pattern, 56);
    }

    private static boolean sourcePatternAllowed(IrcEventNotificationRule.SourceMode mode) {
      return mode == IrcEventNotificationRule.SourceMode.NICK_LIST
          || mode == IrcEventNotificationRule.SourceMode.GLOB
          || mode == IrcEventNotificationRule.SourceMode.REGEX;
    }

    private static boolean channelPatternAllowed(IrcEventNotificationRule.ChannelScope scope) {
      return scope == IrcEventNotificationRule.ChannelScope.ONLY
          || scope == IrcEventNotificationRule.ChannelScope.ALL_EXCEPT;
    }

    private static String summarizeChannel(MutableRule r) {
      if (r == null) return "";
      IrcEventNotificationRule.ChannelScope scope =
          r.channelScope != null ? r.channelScope : IrcEventNotificationRule.ChannelScope.ALL;
      String label = Objects.toString(scope, "");
      if (!channelPatternAllowed(scope)) return label;
      String patterns = trimToNull(r.channelPatterns);
      return patterns == null ? label + ": (empty)" : label + ": " + truncate(patterns, 56);
    }

    private static String summarizeActions(MutableRule r) {
      if (r == null) return "";
      List<String> parts = new ArrayList<>();
      if (r.toastEnabled) {
        IrcEventNotificationRule.FocusScope focus =
            r.focusScope != null
                ? r.focusScope
                : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;
        parts.add("Toast(" + focus + ")");
      }
      if (r.statusBarEnabled) parts.add("Status bar");
      if (r.notificationsNodeEnabled) parts.add("Node");
      if (r.soundEnabled) {
        if (r.soundUseCustom && trimToNull(r.soundCustomPath) != null) {
          parts.add("Sound(custom)");
        } else {
          BuiltInSound sound = BuiltInSound.fromId(r.soundId);
          parts.add("Sound(" + sound.displayNameForUi() + ")");
        }
      }
      if (r.scriptEnabled) {
        String script = trimToNull(r.scriptPath);
        if (script == null) {
          parts.add("Script");
        } else {
          int slash = Math.max(script.lastIndexOf('/'), script.lastIndexOf('\\'));
          String leaf =
              (slash >= 0 && slash < (script.length() - 1)) ? script.substring(slash + 1) : script;
          parts.add("Script(" + truncate(leaf, 26) + ")");
        }
      }
      if (parts.isEmpty()) return "(none)";
      return String.join(", ", parts);
    }

    private static String trimToNull(String raw) {
      String value = Objects.toString(raw, "").trim();
      return value.isEmpty() ? null : value;
    }

    private static String truncate(String value, int maxLen) {
      if (value == null) return "";
      String v = value.trim();
      if (v.length() <= maxLen) return v;
      return v.substring(0, Math.max(0, maxLen - 1)) + "…";
    }

    private static final class MutableRule {
      boolean enabled;
      IrcEventNotificationRule.EventType eventType;
      IrcEventNotificationRule.SourceMode sourceMode;
      String sourcePattern;
      IrcEventNotificationRule.ChannelScope channelScope;
      String channelPatterns;
      boolean toastEnabled;
      boolean statusBarEnabled;
      IrcEventNotificationRule.FocusScope focusScope;
      boolean notificationsNodeEnabled;
      boolean soundEnabled;
      String soundId;
      boolean soundUseCustom;
      String soundCustomPath;
      boolean scriptEnabled;
      String scriptPath;
      String scriptArgs;
      String scriptWorkingDirectory;

      IrcEventNotificationRule toRule() {
        return new IrcEventNotificationRule(
            enabled,
            eventType,
            sourceMode,
            sourcePattern,
            channelScope,
            channelPatterns,
            toastEnabled,
            focusScope,
            statusBarEnabled,
            notificationsNodeEnabled,
            soundEnabled,
            soundId,
            soundUseCustom,
            soundCustomPath,
            scriptEnabled,
            scriptPath,
            scriptArgs,
            scriptWorkingDirectory);
      }

      MutableRule copy() {
        MutableRule m = new MutableRule();
        m.enabled = enabled;
        m.eventType = eventType;
        m.sourceMode = sourceMode;
        m.sourcePattern = sourcePattern;
        m.channelScope = channelScope;
        m.channelPatterns = channelPatterns;
        m.toastEnabled = toastEnabled;
        m.statusBarEnabled = statusBarEnabled;
        m.focusScope = focusScope;
        m.notificationsNodeEnabled = notificationsNodeEnabled;
        m.soundEnabled = soundEnabled;
        m.soundId = soundId;
        m.soundUseCustom = soundUseCustom;
        m.soundCustomPath = soundCustomPath;
        m.scriptEnabled = scriptEnabled;
        m.scriptPath = scriptPath;
        m.scriptArgs = scriptArgs;
        m.scriptWorkingDirectory = scriptWorkingDirectory;
        return m;
      }

      static MutableRule from(IrcEventNotificationRule r) {
        MutableRule m = new MutableRule();
        if (r == null) {
          m.enabled = false;
          m.eventType = IrcEventNotificationRule.EventType.INVITE_RECEIVED;
          m.sourceMode = IrcEventNotificationRule.SourceMode.ANY;
          m.sourcePattern = null;
          m.channelScope = IrcEventNotificationRule.ChannelScope.ALL;
          m.channelPatterns = null;
          m.toastEnabled = true;
          m.statusBarEnabled = true;
          m.focusScope = IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;
          m.notificationsNodeEnabled = true;
          m.soundEnabled = false;
          m.soundId = defaultBuiltInSoundForIrcEventRule(m.eventType).name();
          m.soundUseCustom = false;
          m.soundCustomPath = null;
          m.scriptEnabled = false;
          m.scriptPath = null;
          m.scriptArgs = null;
          m.scriptWorkingDirectory = null;
          return m;
        }

        m.enabled = r.enabled();
        m.eventType = r.eventType();
        m.sourceMode = r.sourceMode();
        m.sourcePattern = r.sourcePattern();
        m.channelScope = r.channelScope();
        m.channelPatterns = r.channelPatterns();
        m.toastEnabled = r.toastEnabled();
        m.statusBarEnabled = r.statusBarEnabled();
        m.focusScope = r.focusScope();
        m.notificationsNodeEnabled = r.notificationsNodeEnabled();
        m.soundEnabled = r.soundEnabled();
        m.soundId = BuiltInSound.fromId(r.soundId()).name();
        m.soundUseCustom = r.soundUseCustom();
        m.soundCustomPath = r.soundCustomPath();
        m.scriptEnabled = r.scriptEnabled();
        m.scriptPath = r.scriptPath();
        m.scriptArgs = r.scriptArgs();
        m.scriptWorkingDirectory = r.scriptWorkingDirectory();
        return m;
      }
    }
  }

  private static final class UserCommandAliasesTableModel extends AbstractTableModel {
    static final int COL_ENABLED = 0;
    static final int COL_COMMAND = 1;

    private static final String[] COLS = new String[] {"Enabled", "Command"};
    private static final Pattern COMMAND_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");

    private final List<MutableAlias> rows = new ArrayList<>();

    UserCommandAliasesTableModel(List<UserCommandAlias> initial) {
      if (initial != null) {
        for (UserCommandAlias alias : initial) {
          if (alias == null) continue;
          rows.add(MutableAlias.from(alias));
        }
      }
    }

    List<UserCommandAlias> snapshot() {
      return rows.stream().map(MutableAlias::toAlias).toList();
    }

    int addAlias(UserCommandAlias alias) {
      rows.add(MutableAlias.from(alias));
      int idx = rows.size() - 1;
      fireTableRowsInserted(idx, idx);
      return idx;
    }

    int duplicateRow(int row) {
      if (row < 0 || row >= rows.size()) return -1;
      MutableAlias src = rows.get(row);
      MutableAlias copy = src.copy();
      int idx = Math.min(rows.size(), row + 1);
      rows.add(idx, copy);
      fireTableRowsInserted(idx, idx);
      return idx;
    }

    void removeRow(int row) {
      if (row < 0 || row >= rows.size()) return;
      rows.remove(row);
      fireTableRowsDeleted(row, row);
    }

    int moveRow(int from, int to) {
      if (from < 0 || from >= rows.size()) return -1;
      if (to < 0 || to >= rows.size()) return -1;
      if (from == to) return from;
      MutableAlias alias = rows.remove(from);
      rows.add(to, alias);
      fireTableDataChanged();
      return to;
    }

    String templateAt(int row) {
      if (row < 0 || row >= rows.size()) return "";
      return Objects.toString(rows.get(row).template, "");
    }

    void setTemplateAt(int row, String template) {
      if (row < 0 || row >= rows.size()) return;
      rows.get(row).template = Objects.toString(template, "");
      fireTableRowsUpdated(row, row);
    }

    UserCommandAliasValidationError firstValidationError() {
      Map<String, Integer> seenEnabled = new LinkedHashMap<>();

      for (int i = 0; i < rows.size(); i++) {
        MutableAlias a = rows.get(i);
        if (a == null || !a.enabled) continue;

        String cmd = normalizeCommand(a.name);
        if (cmd.isEmpty()) {
          return new UserCommandAliasValidationError(
              i, a.name, "Enabled aliases require a command name.");
        }
        if (!COMMAND_NAME_PATTERN.matcher(cmd).matches()) {
          return new UserCommandAliasValidationError(
              i,
              a.name,
              "Command names must start with a letter and contain only letters, numbers, '_' or '-'.");
        }
        if (Objects.toString(a.template, "").isBlank()) {
          return new UserCommandAliasValidationError(
              i, cmd, "Enabled aliases require an expansion.");
        }

        String key = cmd.toLowerCase(Locale.ROOT);
        Integer prev = seenEnabled.putIfAbsent(key, i);
        if (prev != null) {
          return new UserCommandAliasValidationError(
              i,
              cmd,
              "Duplicate enabled alias: /" + cmd + " (also used on row " + (prev + 1) + ").");
        }
      }

      return null;
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLS.length;
    }

    @Override
    public String getColumnName(int column) {
      if (column < 0 || column >= COLS.length) return "";
      return COLS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == COL_ENABLED) return Boolean.class;
      return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return rowIndex >= 0
          && rowIndex < rows.size()
          && columnIndex >= 0
          && columnIndex < COLS.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return null;
      MutableAlias a = rows.get(rowIndex);
      return switch (columnIndex) {
        case COL_ENABLED -> a.enabled;
        case COL_COMMAND -> a.name;
        default -> null;
      };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return;
      MutableAlias a = rows.get(rowIndex);

      switch (columnIndex) {
        case COL_ENABLED -> a.enabled = aValue instanceof Boolean b && b;
        case COL_COMMAND -> a.name = normalizeCommand(Objects.toString(aValue, ""));
        default -> {}
      }

      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    private static String normalizeCommand(String raw) {
      String cmd = Objects.toString(raw, "").trim();
      if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
      return cmd;
    }

    private static final class MutableAlias {
      boolean enabled;
      String name;
      String template;

      UserCommandAlias toAlias() {
        return new UserCommandAlias(
            enabled, normalizeCommand(name), Objects.toString(template, ""));
      }

      MutableAlias copy() {
        MutableAlias c = new MutableAlias();
        c.enabled = enabled;
        c.name = name;
        c.template = template;
        return c;
      }

      static MutableAlias from(UserCommandAlias alias) {
        MutableAlias m = new MutableAlias();
        if (alias == null) {
          m.enabled = true;
          m.name = "";
          m.template = "";
          return m;
        }
        m.enabled = alias.enabled();
        m.name = normalizeCommand(alias.name());
        m.template = Objects.toString(alias.template(), "");
        return m;
      }
    }
  }

  private static final class NotificationRulesTableModel extends AbstractTableModel {
    static final int COL_ENABLED = 0;
    static final int COL_LABEL = 1;
    static final int COL_MATCH = 2;
    static final int COL_OPTIONS = 3;
    static final int COL_COLOR = 4;

    private static final String[] COLS =
        new String[] {
          "Enabled", "Label", "Match", "Options", "Color",
        };

    private final List<MutableRule> rows = new ArrayList<>();

    NotificationRulesTableModel(List<NotificationRule> initial) {
      if (initial != null) {
        for (NotificationRule r : initial) {
          if (r == null) continue;
          rows.add(MutableRule.from(r));
        }
      }
    }

    List<NotificationRule> snapshot() {
      return rows.stream().map(MutableRule::toRule).toList();
    }

    NotificationRule ruleAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      MutableRule m = rows.get(row);
      return m != null ? m.toRule() : null;
    }

    void setRule(int row, NotificationRule rule) {
      if (row < 0 || row >= rows.size()) return;
      rows.set(row, MutableRule.from(rule));
      fireTableRowsUpdated(row, row);
    }

    static String effectiveRuleLabel(NotificationRule rule) {
      if (rule == null) return "(unnamed)";
      String label = Objects.toString(rule.label(), "").trim();
      if (!label.isEmpty()) return label;
      String pattern = Objects.toString(rule.pattern(), "").trim();
      return pattern.isEmpty() ? "(unnamed)" : pattern;
    }

    String highlightFgAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      MutableRule r = rows.get(row);
      return r != null ? r.highlightFg : null;
    }

    void setHighlightFg(int row, String hex) {
      if (row < 0 || row >= rows.size()) return;
      MutableRule r = rows.get(row);
      if (r == null) return;
      r.highlightFg = normalizeHexColor(Objects.toString(hex, "").trim());
      fireTableRowsUpdated(row, row);
    }

    List<ValidationError> validationErrors() {
      List<ValidationError> out = new ArrayList<>();
      for (int i = 0; i < rows.size(); i++) {
        MutableRule r = rows.get(i);
        if (r == null) continue;
        if (!r.enabled) continue;
        if (r.type != NotificationRule.Type.REGEX) continue;

        String pat = r.pattern != null ? r.pattern.trim() : "";
        if (pat.isEmpty()) continue;

        try {
          int flags = Pattern.UNICODE_CASE;
          if (!r.caseSensitive) flags |= Pattern.CASE_INSENSITIVE;
          Pattern.compile(pat, flags);
        } catch (Exception ex) {
          out.add(new ValidationError(i, r.label, pat, ex.getMessage()));
        }
      }
      return out;
    }

    ValidationError firstValidationError() {
      List<ValidationError> errs = validationErrors();
      return errs.isEmpty() ? null : errs.get(0);
    }

    int addRule(NotificationRule rule) {
      rows.add(MutableRule.from(rule));
      int idx = rows.size() - 1;
      fireTableRowsInserted(idx, idx);
      return idx;
    }

    int duplicateRow(int row) {
      if (row < 0 || row >= rows.size()) return -1;
      MutableRule src = rows.get(row);
      MutableRule copy = src.copy();
      int idx = Math.min(rows.size(), row + 1);
      rows.add(idx, copy);
      fireTableRowsInserted(idx, idx);
      return idx;
    }

    void removeRow(int row) {
      if (row < 0 || row >= rows.size()) return;
      rows.remove(row);
      fireTableRowsDeleted(row, row);
    }

    int moveRow(int from, int to) {
      if (from < 0 || from >= rows.size()) return -1;
      if (to < 0 || to >= rows.size()) return -1;
      if (from == to) return from;
      MutableRule r = rows.remove(from);
      rows.add(to, r);
      fireTableDataChanged();
      return to;
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLS.length;
    }

    @Override
    public String getColumnName(int column) {
      if (column < 0 || column >= COLS.length) return "";
      return COLS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == COL_ENABLED) return Boolean.class;
      return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return null;
      MutableRule r = rows.get(rowIndex);
      return switch (columnIndex) {
        case COL_ENABLED -> r.enabled;
        case COL_LABEL -> effectiveRuleLabel(r.toRule());
        case COL_MATCH -> summarizeMatch(r);
        case COL_OPTIONS -> summarizeOptions(r);
        case COL_COLOR -> Objects.toString(r.highlightFg, "");
        default -> null;
      };
    }

    private static String summarizeMatch(MutableRule r) {
      if (r == null) return "";
      String pattern = Objects.toString(r.pattern, "").trim();
      if (pattern.isEmpty()) pattern = "(empty)";
      String type = r.type == NotificationRule.Type.REGEX ? "REGEX" : "WORD";
      return type + ": " + pattern;
    }

    private static String summarizeOptions(MutableRule r) {
      if (r == null) return "";
      String caseLabel = r.caseSensitive ? "Case" : "No case";
      if (r.type == NotificationRule.Type.WORD) {
        return caseLabel + ", " + (r.wholeWord ? "Whole word" : "Substring");
      }
      return caseLabel;
    }

    private static String normalizeHexColor(String raw) {
      if (raw == null) return null;
      String s = raw.trim();
      if (s.isEmpty()) return null;

      if (s.startsWith("#")) s = s.substring(1).trim();
      if (s.length() == 3) {
        char r = s.charAt(0);
        char g = s.charAt(1);
        char b = s.charAt(2);
        s = "" + r + r + g + g + b + b;
      } else if (s.length() != 6) {
        return null;
      }

      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        if (!ok) return null;
      }

      return "#" + s.toUpperCase(Locale.ROOT);
    }

    private static final class MutableRule {
      boolean enabled;
      NotificationRule.Type type;
      String label;
      String pattern;
      boolean caseSensitive;
      boolean wholeWord;
      String highlightFg;

      NotificationRule toRule() {
        boolean ww = (type == NotificationRule.Type.WORD) && wholeWord;
        return new NotificationRule(label, type, pattern, enabled, caseSensitive, ww, highlightFg);
      }

      MutableRule copy() {
        MutableRule m = new MutableRule();
        m.enabled = enabled;
        m.type = type;
        m.label = label;
        m.pattern = pattern;
        m.caseSensitive = caseSensitive;
        m.wholeWord = wholeWord;
        m.highlightFg = highlightFg;
        return m;
      }

      static MutableRule from(NotificationRule r) {
        MutableRule m = new MutableRule();
        if (r == null) {
          m.enabled = false;
          m.type = NotificationRule.Type.WORD;
          m.label = "";
          m.pattern = "";
          m.caseSensitive = false;
          m.wholeWord = true;
          m.highlightFg = null;
          return m;
        }

        m.enabled = r.enabled();
        m.type = r.type();
        m.label = Objects.toString(r.label(), "");
        m.pattern = Objects.toString(r.pattern(), "");
        m.caseSensitive = r.caseSensitive();
        m.wholeWord = r.wholeWord();
        m.highlightFg = r.highlightFg();
        return m;
      }
    }
  }

  private static final class RuleColorCellRenderer extends DefaultTableCellRenderer {
    @Override
    public java.awt.Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel c =
          (JLabel)
              super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      String raw = value != null ? value.toString().trim() : "";
      Color col = parseHexColor(raw);
      if (col == null && raw != null && raw.startsWith("#") && raw.length() == 4) {
        // Try #RGB
        String s = raw.substring(1);
        char r = s.charAt(0), g = s.charAt(1), b = s.charAt(2);
        col = parseHexColor("#" + r + r + g + g + b + b);
      }

      if (col != null) {
        c.setIcon(new ColorSwatch(col, 12, 12));
        c.setText(toHex(col));
      } else {
        c.setIcon(null);
        c.setText(raw.isEmpty() ? "" : raw);
      }
      return c;
    }
  }

  private static final class ColorSwatch implements Icon {
    private final Color color;
    private final int w;
    private final int h;

    ColorSwatch(Color color, int w, int h) {
      this.color = color != null ? color : Color.GRAY;
      this.w = Math.max(6, w);
      this.h = Math.max(6, h);
    }

    @Override
    public int getIconWidth() {
      return w;
    }

    @Override
    public int getIconHeight() {
      return h;
    }

    @Override
    public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
      Color old = g.getColor();
      try {
        g.setColor(color);
        g.fillRect(x, y, w, h);
        Color border = c != null ? c.getForeground() : null;
        if (border == null) border = UIManager.getColor("Component.borderColor");
        if (border == null) border = UIManager.getColor("Separator.foreground");
        if (border == null) border = Color.BLACK;
        border = new Color(border.getRed(), border.getGreen(), border.getBlue(), 120);
        g.setColor(border);
        g.drawRect(x, y, w - 1, h - 1);
      } finally {
        g.setColor(old);
      }
    }
  }

  private enum AccentPreset {
    THEME_DEFAULT("Theme default", null),
    IRCAFE_COBALT("IRCafe cobalt", UiProperties.DEFAULT_ACCENT_COLOR),
    INDIGO("Indigo", "#4F46E5"),
    VIOLET("Violet", "#7C3AED"),
    CUSTOM("Custom…", null);

    final String label;
    final String hex;

    AccentPreset(String label, String hex) {
      this.label = label;
      this.hex = hex;
    }

    Color colorOrNull() {
      if (hex == null) return null;
      return parseHexColorLenient(hex);
    }

    static AccentPreset fromHexOrCustom(String normalizedHex) {
      if (normalizedHex == null || normalizedHex.isBlank()) return CUSTOM;
      for (AccentPreset p : values()) {
        if (p.hex != null && p.hex.equalsIgnoreCase(normalizedHex)) return p;
      }
      return CUSTOM;
    }
  }

  private record AccentControls(
      JCheckBox enabled,
      JComboBox<AccentPreset> preset,
      JTextField hex,
      JButton pick,
      JButton clear,
      JSlider strength,
      JComponent chip,
      JPanel panel,
      Runnable applyEnabledState,
      Runnable syncPresetFromHex,
      Runnable updateChip) {}

  private record ColorField(
      JTextField hex, JButton pick, JButton clear, JPanel panel, Runnable updateIcon) {}

  private record ChatThemeControls(
      JComboBox<ChatThemeSettings.Preset> preset,
      ColorField timestamp,
      ColorField system,
      ColorField mention,
      ColorField message,
      ColorField notice,
      ColorField action,
      ColorField error,
      ColorField presence,
      JSlider mentionStrength) {}

  private record ThemeControls(JComboBox<String> combo) {}

  private record FontControls(JComboBox<String> fontFamily, JSpinner fontSize) {}

  private record DensityOption(String id, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  private record TweakControls(
      JComboBox<DensityOption> density,
      JSlider cornerRadius,
      JCheckBox uiFontOverrideEnabled,
      JComboBox<String> uiFontFamily,
      JSpinner uiFontSize,
      Runnable applyUiFontEnabledState) {}

  private enum PushyTargetMode {
    DEVICE_TOKEN("Device token"),
    TOPIC("Topic");

    private final String label;

    PushyTargetMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private record TrayControls(
      JCheckBox enabled,
      JCheckBox closeToTray,
      JCheckBox minimizeToTray,
      JCheckBox startMinimized,
      JCheckBox notifyHighlights,
      JCheckBox notifyPrivateMessages,
      JCheckBox notifyConnectionState,
      JCheckBox notifyOnlyWhenUnfocused,
      JCheckBox notifyOnlyWhenMinimizedOrHidden,
      JCheckBox notifySuppressWhenTargetActive,
      JCheckBox linuxDbusActions,
      JComboBox<NotificationBackendMode> notificationBackend,
      JButton testNotification,
      JCheckBox notificationSoundsEnabled,
      JCheckBox notificationSoundUseCustom,
      JTextField notificationSoundCustomPath,
      JButton browseCustomSound,
      JButton clearCustomSound,
      JComboBox<BuiltInSound> notificationSound,
      JButton testSound,
      JCheckBox pushyEnabled,
      JTextField pushyEndpoint,
      JPasswordField pushyApiKey,
      JComboBox<PushyTargetMode> pushyTargetMode,
      JTextField pushyTargetValue,
      JTextField pushyTitlePrefix,
      JSpinner pushyConnectTimeoutSeconds,
      JSpinner pushyReadTimeoutSeconds,
      JLabel pushyValidationLabel,
      JButton pushyTest,
      JLabel pushyTestStatus,
      JPanel panel) {}

  private record ImageEmbedControls(
      JCheckBox enabled,
      JCheckBox collapsed,
      JSpinner maxWidth,
      JSpinner maxHeight,
      JCheckBox animateGifs,
      JPanel panel) {}

  private record LinkPreviewControls(JCheckBox enabled, JCheckBox collapsed, JPanel panel) {}

  private record MemoryWarningControls(
      JSpinner nearMaxPercent,
      JCheckBox tooltipEnabled,
      JCheckBox toastEnabled,
      JCheckBox pushyEnabled,
      JCheckBox soundEnabled) {}

  private record CtcpAutoReplyControls(
      JCheckBox enabled, JCheckBox version, JCheckBox ping, JCheckBox time) {}

  private record Ircv3CapabilitiesControls(Map<String, JCheckBox> checkboxes, JPanel panel) {
    Map<String, Boolean> snapshot() {
      Map<String, Boolean> out = new LinkedHashMap<>();
      for (Map.Entry<String, JCheckBox> e : checkboxes.entrySet()) {
        JCheckBox cb = e.getValue();
        out.put(e.getKey(), cb != null && cb.isSelected());
      }
      return out;
    }
  }

  private record NickColorControls(
      JCheckBox enabled,
      JSpinner minContrast,
      JButton overrides,
      NickColorPreviewPanel preview,
      JPanel panel) {}

  private record TimestampControls(
      JCheckBox enabled,
      JTextField format,
      JCheckBox includeChatMessages,
      JCheckBox includePresenceMessages,
      JPanel panel) {}

  private record HistoryControls(
      JSpinner initialLoadLines, JSpinner pageSize, JSpinner commandHistoryMaxSize, JPanel panel) {}

  private record LoggingControls(
      JCheckBox enabled,
      JCheckBox logSoftIgnored,
      JCheckBox logPrivateMessages,
      JCheckBox savePrivateMessageList,
      JButton managePrivateMessageList,
      JCheckBox keepForever,
      JSpinner retentionDays,
      JTextField dbBaseName,
      JCheckBox dbNextToConfig,
      JTextArea info) {}

  private record OutgoingColorControls(
      JCheckBox enabled, JTextField hex, JLabel preview, JPanel panel) {}

  private record UserhostControls(
      JCheckBox enabled,
      JSpinner minIntervalSeconds,
      JSpinner maxPerMinute,
      JSpinner nickCooldownMinutes,
      JSpinner maxNicksPerCommand) {}

  private record UserInfoEnrichmentControls(
      JCheckBox enabled,
      JSpinner userhostMinIntervalSeconds,
      JSpinner userhostMaxPerMinute,
      JSpinner userhostNickCooldownMinutes,
      JSpinner userhostMaxNicksPerCommand,
      JCheckBox whoisFallbackEnabled,
      JSpinner whoisMinIntervalSeconds,
      JSpinner whoisNickCooldownMinutes,
      JCheckBox periodicRefreshEnabled,
      JSpinner periodicRefreshIntervalSeconds,
      JSpinner periodicRefreshNicksPerTick) {}

  private record ProxyControls(
      JCheckBox enabled,
      JTextField host,
      JSpinner port,
      JCheckBox remoteDns,
      JTextField username,
      JPasswordField password,
      JButton clearPassword,
      JSpinner connectTimeoutSeconds,
      JSpinner readTimeoutSeconds) {}

  private record HeartbeatControls(
      JCheckBox enabled, JSpinner checkPeriodSeconds, JSpinner timeoutSeconds) {}

  private record NetworkAdvancedControls(
      ProxyControls proxy,
      UserhostControls userhost,
      UserInfoEnrichmentControls enrichment,
      HeartbeatControls heartbeat,
      JSpinner monitorIsonPollIntervalSeconds,
      JCheckBox trustAllTlsCertificates,
      JPanel networkPanel,
      JPanel userLookupsPanel) {}

  private static final class NickColorPreviewPanel extends JPanel {
    private static final String[] SAMPLE_NICKS =
        new String[] {"Alice", "Bob", "Carol", "Dave", "Eve", "Mallory"};

    private final NickColorService nickColorService;
    private final java.util.List<JLabel> labels = new java.util.ArrayList<>();

    private NickColorPreviewPanel(NickColorService nickColorService) {
      super(new FlowLayout(FlowLayout.LEFT, 10, 6));
      this.nickColorService = nickColorService;

      setOpaque(true);
      Color border = UIManager.getColor("Component.borderColor");
      if (border == null) border = UIManager.getColor("Separator.foreground");
      if (border == null) border = UIManager.getColor("Label.foreground");
      if (border == null) border = Color.GRAY;

      setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(border, 1),
              BorderFactory.createEmptyBorder(6, 8, 6, 8)));

      for (String n : SAMPLE_NICKS) {
        JLabel l = new JLabel(n);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        labels.add(l);
        add(l);
      }
    }

    private void updatePreview(boolean enabled, double minContrast) {
      Color bg = UIManager.getColor("TextPane.background");
      if (bg == null) bg = UIManager.getColor("Panel.background");
      if (bg == null) bg = Color.WHITE;

      Color fg = UIManager.getColor("TextPane.foreground");
      if (fg == null) fg = UIManager.getColor("Label.foreground");
      if (fg == null) fg = Color.BLACK;

      setBackground(bg);

      for (int i = 0; i < labels.size(); i++) {
        JLabel l = labels.get(i);
        String nick = SAMPLE_NICKS[i];

        Color c =
            (nickColorService != null)
                ? nickColorService.previewColorForNick(nick, bg, fg, enabled, minContrast)
                : fg;

        l.setForeground(c);
      }

      repaint();
    }
  }

  private static final class SimpleDocListener implements DocumentListener {
    private final Runnable onChange;

    private SimpleDocListener(Runnable onChange) {
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

  private static final class FilterControls {
    final JCheckBox filtersEnabledByDefault;
    final JCheckBox placeholdersEnabledByDefault;
    final JCheckBox placeholdersCollapsedByDefault;
    final JSpinner placeholderPreviewLines;
    final JSpinner placeholderMaxLinesPerRun;
    final JSpinner placeholderTooltipMaxTags;
    final JCheckBox historyPlaceholdersEnabledByDefault;
    final JSpinner historyPlaceholderMaxRunsPerBatch;

    final FilterOverridesTableModel overridesModel;
    final JTable overridesTable;
    final JButton addOverride;
    final JButton removeOverride;

    final JTable rulesTable;

    final JButton addRule;
    final JButton editRule;
    final JButton deleteRule;

    final JButton moveRuleUp;
    final JButton moveRuleDown;

    private FilterControls(
        JCheckBox filtersEnabledByDefault,
        JCheckBox placeholdersEnabledByDefault,
        JCheckBox placeholdersCollapsedByDefault,
        JSpinner placeholderPreviewLines,
        JSpinner placeholderMaxLinesPerRun,
        JSpinner placeholderTooltipMaxTags,
        JCheckBox historyPlaceholdersEnabledByDefault,
        JSpinner historyPlaceholderMaxRunsPerBatch,
        FilterOverridesTableModel overridesModel,
        JTable overridesTable,
        JButton addOverride,
        JButton removeOverride,
        JTable rulesTable,
        JButton addRule,
        JButton editRule,
        JButton deleteRule,
        JButton moveRuleUp,
        JButton moveRuleDown) {
      this.filtersEnabledByDefault = filtersEnabledByDefault;
      this.placeholdersEnabledByDefault = placeholdersEnabledByDefault;
      this.placeholdersCollapsedByDefault = placeholdersCollapsedByDefault;
      this.placeholderPreviewLines = placeholderPreviewLines;
      this.placeholderMaxLinesPerRun = placeholderMaxLinesPerRun;
      this.placeholderTooltipMaxTags = placeholderTooltipMaxTags;
      this.historyPlaceholdersEnabledByDefault = historyPlaceholdersEnabledByDefault;
      this.historyPlaceholderMaxRunsPerBatch = historyPlaceholderMaxRunsPerBatch;
      this.overridesModel = overridesModel;
      this.overridesTable = overridesTable;
      this.addOverride = addOverride;
      this.removeOverride = removeOverride;

      this.rulesTable = rulesTable;
      this.addRule = addRule;
      this.editRule = editRule;
      this.deleteRule = deleteRule;

      this.moveRuleUp = moveRuleUp;
      this.moveRuleDown = moveRuleDown;
    }
  }

  private enum Tri {
    DEFAULT("Default"),
    ON("On"),
    OFF("Off");

    final String label;

    Tri(String label) {
      this.label = label;
    }

    static Tri fromNullable(Boolean b) {
      if (b == null) return DEFAULT;
      return b ? ON : OFF;
    }

    Boolean toNullable() {
      return switch (this) {
        case DEFAULT -> null;
        case ON -> Boolean.TRUE;
        case OFF -> Boolean.FALSE;
      };
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static final class FilterOverridesRow {
    String scope;
    Tri filters;
    Tri placeholders;
    Tri collapsed;

    FilterOverridesRow(String scope, Tri filters, Tri placeholders, Tri collapsed) {
      this.scope = scope;
      this.filters = filters;
      this.placeholders = placeholders;
      this.collapsed = collapsed;
    }
  }

  private static final class FilterOverridesTableModel extends AbstractTableModel {
    private final List<FilterOverridesRow> rows = new ArrayList<>();

    void setOverrides(List<FilterScopeOverride> overrides) {
      rows.clear();
      if (overrides != null) {
        for (FilterScopeOverride o : overrides) {
          rows.add(
              new FilterOverridesRow(
                  o.scopePattern(),
                  Tri.fromNullable(o.filtersEnabled()),
                  Tri.fromNullable(o.placeholdersEnabled()),
                  Tri.fromNullable(o.placeholdersCollapsed())));
        }
      }
      fireTableDataChanged();
    }

    List<FilterScopeOverride> toOverrides() {
      List<FilterScopeOverride> out = new ArrayList<>();
      for (FilterOverridesRow r : rows) {
        String s = r.scope != null ? r.scope.trim() : "";
        if (s.isEmpty()) continue;
        out.add(
            new FilterScopeOverride(
                s, r.filters.toNullable(), r.placeholders.toNullable(), r.collapsed.toNullable()));
      }
      return out;
    }

    void addEmpty(String scope) {
      rows.add(new FilterOverridesRow(scope, Tri.DEFAULT, Tri.DEFAULT, Tri.DEFAULT));
      fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    void removeAt(int idx) {
      if (idx < 0 || idx >= rows.size()) return;
      rows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return 4;
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case 0 -> "Scope";
        case 1 -> "Filters";
        case 2 -> "Placeholders";
        case 3 -> "Collapsed";
        default -> "";
      };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return switch (columnIndex) {
        case 0 -> String.class;
        default -> Tri.class;
      };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      FilterOverridesRow r = rows.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> r.scope;
        case 1 -> r.filters;
        case 2 -> r.placeholders;
        case 3 -> r.collapsed;
        default -> null;
      };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      FilterOverridesRow r = rows.get(rowIndex);
      switch (columnIndex) {
        case 0 -> r.scope = aValue != null ? String.valueOf(aValue) : "";
        case 1 -> r.filters = (aValue instanceof Tri t) ? t : r.filters;
        case 2 -> r.placeholders = (aValue instanceof Tri t) ? t : r.placeholders;
        case 3 -> r.collapsed = (aValue instanceof Tri t) ? t : r.collapsed;
        default -> {}
      }
      fireTableRowsUpdated(rowIndex, rowIndex);
    }
  }

  private static final class CenteredBooleanRenderer extends JCheckBox
      implements TableCellRenderer {
    CenteredBooleanRenderer() {
      setHorizontalAlignment(SwingConstants.CENTER);
      setBorderPainted(false);
      setOpaque(true);
      setEnabled(true);
    }

    @Override
    public java.awt.Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setSelected(Boolean.TRUE.equals(value));
      if (isSelected) {
        setBackground(table.getSelectionBackground());
        setForeground(table.getSelectionForeground());
      } else {
        setBackground(table.getBackground());
        setForeground(table.getForeground());
      }
      return this;
    }
  }

  private static final class FilterRulesTableModel extends AbstractTableModel {
    private final List<FilterRule> rules = new ArrayList<>();

    void setRules(List<FilterRule> next) {
      rules.clear();
      if (next != null) rules.addAll(next);
      fireTableDataChanged();
    }

    FilterRule ruleAt(int row) {
      if (row < 0 || row >= rules.size()) return null;
      return rules.get(row);
    }

    @Override
    public int getRowCount() {
      return rules.size();
    }

    @Override
    public int getColumnCount() {
      return 5;
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case 0 -> "On";
        case 1 -> "Name";
        case 2 -> "Scope";
        case 3 -> "Action";
        case 4 -> "Summary";
        default -> "";
      };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return switch (columnIndex) {
        case 0 -> Boolean.class;
        default -> String.class;
      };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      FilterRule r = rules.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> r.enabled();
        case 1 -> r.name();
        case 2 -> r.scopePattern();
        case 3 -> prettyAction(r);
        case 4 -> summaryFor(r);
        default -> null;
      };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex != 0) return;
      if (rowIndex < 0 || rowIndex >= rules.size()) return;
      FilterRule cur = rules.get(rowIndex);
      if (cur == null) return;

      boolean enabled = Boolean.TRUE.equals(aValue);
      if (cur.enabled() == enabled) return;

      FilterRule next =
          new FilterRule(
              cur.id(),
              cur.name(),
              enabled,
              cur.scopePattern(),
              cur.action(),
              cur.direction(),
              cur.kinds(),
              cur.fromNickGlobs(),
              cur.textRegex(),
              cur.tags());
      rules.set(rowIndex, next);
      fireTableCellUpdated(rowIndex, columnIndex);
    }

    private static String prettyAction(FilterRule r) {
      if (r == null || r.action() == null) return "";
      String s = r.action().name().toLowerCase(Locale.ROOT);
      return s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String summaryFor(FilterRule r) {
      if (r == null) return "";
      List<String> parts = new ArrayList<>();

      if (r.hasKinds()) {
        String ks =
            r.kinds().stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("");
        if (!ks.isBlank()) parts.add("kinds=" + ks);
      }

      if (r.direction() != null
          && r.direction() != cafe.woden.ircclient.model.FilterDirection.ANY) {
        parts.add("dir=" + r.direction().name());
      }

      if (r.hasFromNickGlobs()) {
        String from = String.join(",", r.fromNickGlobs());
        parts.add("from=" + truncate(from, 48));
      }

      if (r.hasTextRegex()) {
        String pat = r.textRegex().pattern();
        String flags = "";
        if (r.textRegex().flags() != null && !r.textRegex().flags().isEmpty()) {
          StringBuilder sb = new StringBuilder();
          if (r.textRegex().flags().contains(cafe.woden.ircclient.model.RegexFlag.I))
            sb.append('i');
          if (r.textRegex().flags().contains(cafe.woden.ircclient.model.RegexFlag.M))
            sb.append('m');
          if (r.textRegex().flags().contains(cafe.woden.ircclient.model.RegexFlag.S))
            sb.append('s');
          flags = sb.toString();
        }
        String re = "/" + truncate(pat, 48) + "/" + flags;
        parts.add("text=" + re);
      }

      if (r != null && r.hasTags()) {
        parts.add("tags=" + truncate(r.tags().expr(), 48));
      }

      if (parts.isEmpty()) return "(matches any)";
      return String.join(" ", parts);
    }

    private static String truncate(String s, int max) {
      if (s == null) return "";
      String v = s.trim();
      if (v.length() <= max) return v;
      return v.substring(0, Math.max(0, max - 1)) + "…";
    }
  }

  private FilterControls buildFilterControls(
      FilterSettings current, List<AutoCloseable> closeables) {
    Objects.requireNonNull(current);

    JCheckBox enabledByDefault = new JCheckBox("Enable filters by default");
    enabledByDefault.setSelected(current.filtersEnabledByDefault());

    JCheckBox placeholdersEnabledByDefault =
        new JCheckBox("Enable \"Filtered (N)\" placeholders by default");
    placeholdersEnabledByDefault.setSelected(current.placeholdersEnabledByDefault());

    JCheckBox placeholdersCollapsedByDefault = new JCheckBox("Collapse placeholders by default");
    placeholdersCollapsedByDefault.setSelected(current.placeholdersCollapsedByDefault());

    JSpinner previewLines =
        new JSpinner(
            new SpinnerNumberModel(
                Math.max(0, Math.min(25, current.placeholderMaxPreviewLines())), 0, 25, 1));
    // Keep consistent with other numeric spinners in the dialog.
    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateNumberSpinner(previewLines));
      } catch (Exception ignored) {
        // best-effort; spinner still works without wheel support
      }
    } else {
      try {
        MouseWheelDecorator.decorateNumberSpinner(previewLines);
      } catch (Exception ignored) {
        // best-effort
      }
    }

    JSpinner maxLinesPerRun =
        new JSpinner(
            new SpinnerNumberModel(
                Math.max(0, Math.min(50_000, current.placeholderMaxLinesPerRun())), 0, 50_000, 50));
    maxLinesPerRun.setToolTipText(
        "Max hidden lines represented in a single placeholder run. 0 = unlimited.");
    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateNumberSpinner(maxLinesPerRun));
      } catch (Exception ignored) {
      }
    } else {
      try {
        MouseWheelDecorator.decorateNumberSpinner(maxLinesPerRun);
      } catch (Exception ignored) {
      }
    }

    JSpinner tooltipMaxTags =
        new JSpinner(
            new SpinnerNumberModel(
                Math.max(0, Math.min(500, current.placeholderTooltipMaxTags())), 0, 500, 1));
    tooltipMaxTags.setToolTipText("Max tags shown in placeholder/hint tooltips. 0 = hide tags.");
    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateNumberSpinner(tooltipMaxTags));
      } catch (Exception ignored) {
      }
    } else {
      try {
        MouseWheelDecorator.decorateNumberSpinner(tooltipMaxTags);
      } catch (Exception ignored) {
      }
    }

    JCheckBox historyPlaceholdersEnabledByDefault =
        new JCheckBox("Show placeholders for filtered history loads");
    historyPlaceholdersEnabledByDefault.setSelected(current.historyPlaceholdersEnabledByDefault());
    historyPlaceholdersEnabledByDefault.setToolTipText(
        "If off, filtered lines loaded from history are silently hidden (no placeholder/hint rows).");

    JSpinner historyMaxRuns =
        new JSpinner(
            new SpinnerNumberModel(
                Math.max(0, Math.min(5_000, current.historyPlaceholderMaxRunsPerBatch())),
                0,
                5_000,
                1));
    historyMaxRuns.setToolTipText("Max placeholder runs per history load batch. 0 = unlimited.");
    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateNumberSpinner(historyMaxRuns));
      } catch (Exception ignored) {
      }
    } else {
      try {
        MouseWheelDecorator.decorateNumberSpinner(historyMaxRuns);
      } catch (Exception ignored) {
      }
    }

    // If history placeholders are disabled, the batch cap is irrelevant (keep the value but disable
    // the control).
    try {
      historyMaxRuns.setEnabled(historyPlaceholdersEnabledByDefault.isSelected());
      historyPlaceholdersEnabledByDefault.addActionListener(
          e -> historyMaxRuns.setEnabled(historyPlaceholdersEnabledByDefault.isSelected()));
    } catch (Exception ignored) {
      // best-effort
    }

    FilterOverridesTableModel model = new FilterOverridesTableModel();
    model.setOverrides(current.overrides());

    JTable table = new JTable(model);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    // Tri-state editor
    JComboBox<Tri> triCombo = new JComboBox<>(Tri.values());
    TableColumn c1 = table.getColumnModel().getColumn(1);
    TableColumn c2 = table.getColumnModel().getColumn(2);
    TableColumn c3 = table.getColumnModel().getColumn(3);
    c1.setCellEditor(new DefaultCellEditor(triCombo));
    c2.setCellEditor(new DefaultCellEditor(new JComboBox<>(Tri.values())));
    c3.setCellEditor(new DefaultCellEditor(new JComboBox<>(Tri.values())));

    JButton add = new JButton("Add override...");
    JButton remove = new JButton("Remove");
    configureIconOnlyButton(add, "plus", "Add scope override");
    configureIconOnlyButton(remove, "trash", "Remove selected scope override");
    remove.setEnabled(false);

    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              remove.setEnabled(table.getSelectedRow() >= 0);
            });

    add.addActionListener(
        e -> {
          String scope =
              JOptionPane.showInputDialog(
                  dialog,
                  "Scope pattern (e.g. libera/#llamas, libera/*, */status)",
                  "Add Override",
                  JOptionPane.PLAIN_MESSAGE);
          if (scope == null) return;
          scope = scope.trim();
          if (scope.isEmpty()) return;
          model.addEmpty(scope);
          int idx = model.getRowCount() - 1;
          if (idx >= 0) {
            table.getSelectionModel().setSelectionInterval(idx, idx);
            table.scrollRectToVisible(table.getCellRect(idx, 0, true));
          }
        });

    remove.addActionListener(
        e -> {
          int row = table.getSelectedRow();
          if (row < 0) return;
          int confirm =
              JOptionPane.showConfirmDialog(
                  dialog,
                  "Remove selected override?",
                  "Remove Override",
                  JOptionPane.OK_CANCEL_OPTION);
          if (confirm != JOptionPane.OK_OPTION) return;
          model.removeAt(row);
        });

    // Rules
    FilterRulesTableModel rulesModel = new FilterRulesTableModel();
    rulesModel.setRules(current.rules());

    JTable rulesTable = new JTable(rulesModel);
    rulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    // Allow drag-and-drop reordering of filter rules (WeeChat-style).
    // This persists immediately (same behavior as the Move up/down buttons).
    try {
      rulesTable.setDragEnabled(true);
      rulesTable.setDropMode(DropMode.INSERT_ROWS);
    } catch (Exception ignored) {
      // best-effort; table still works without DnD
    }
    // Enabled checkbox column (toggleable)
    try {
      TableColumn onCol = rulesTable.getColumnModel().getColumn(0);
      onCol.setMinWidth(42);
      onCol.setMaxWidth(50);
      onCol.setPreferredWidth(45);
      onCol.setCellRenderer(new CenteredBooleanRenderer());
      JCheckBox cb = new JCheckBox();
      cb.setHorizontalAlignment(SwingConstants.CENTER);
      cb.setBorderPainted(false);
      onCol.setCellEditor(new DefaultCellEditor(cb));
    } catch (Exception ignored) {
      // best-effort
    }

    // Keep in sync if filter settings change while the dialog is open (e.g. /filter add ...).
    PropertyChangeListener rulesListener =
        evt -> {
          if (!FilterSettingsBus.PROP_FILTER_SETTINGS.equals(evt.getPropertyName())) return;
          Object nv = evt.getNewValue();
          if (!(nv instanceof FilterSettings fs)) return;

          SwingUtilities.invokeLater(
              () -> {
                try {
                  java.util.UUID selectedId = null;
                  int selectedRow = rulesTable.getSelectedRow();
                  if (selectedRow >= 0) {
                    FilterRule selected = rulesModel.ruleAt(selectedRow);
                    if (selected != null) selectedId = selected.id();
                  }

                  rulesModel.setRules(fs.rules());

                  if (selectedId != null) {
                    for (int i = 0; i < rulesModel.getRowCount(); i++) {
                      FilterRule r = rulesModel.ruleAt(i);
                      if (r != null && selectedId.equals(r.id())) {
                        rulesTable.getSelectionModel().setSelectionInterval(i, i);
                        rulesTable.scrollRectToVisible(rulesTable.getCellRect(i, 0, true));
                        break;
                      }
                    }
                  }
                } catch (Exception ignored) {
                }
              });
        };
    filterSettingsBus.addListener(rulesListener);
    if (closeables != null) {
      closeables.add(() -> filterSettingsBus.removeListener(rulesListener));
    }

    // Persist enabled/disabled toggles from the table immediately.
    rulesModel.addTableModelListener(
        ev -> {
          try {
            if (ev.getColumn() != 0) return;
            int row = ev.getFirstRow();
            if (row < 0) return;
            FilterRule edited = rulesModel.ruleAt(row);
            if (edited == null) return;

            FilterSettings snap = filterSettingsBus.get();
            if (snap == null) return;

            java.util.List<FilterRule> nextRules = new java.util.ArrayList<>();
            boolean replaced = false;
            if (snap.rules() != null) {
              for (FilterRule r : snap.rules()) {
                if (!replaced && r != null && edited.id() != null && edited.id().equals(r.id())) {
                  nextRules.add(edited);
                  replaced = true;
                } else {
                  nextRules.add(r);
                }
              }
            }

            if (!replaced) {
              // Fallback: replace by index if possible.
              if (row >= 0 && row < nextRules.size()) {
                nextRules.set(row, edited);
                replaced = true;
              }
            }

            if (!replaced) {
              // Better than losing the toggle.
              nextRules.add(edited);
            }

            FilterSettings next =
                new FilterSettings(
                    snap.filtersEnabledByDefault(),
                    snap.placeholdersEnabledByDefault(),
                    snap.placeholdersCollapsedByDefault(),
                    snap.placeholderMaxPreviewLines(),
                    snap.placeholderMaxLinesPerRun(),
                    snap.placeholderTooltipMaxTags(),
                    snap.historyPlaceholderMaxRunsPerBatch(),
                    snap.historyPlaceholdersEnabledByDefault(),
                    nextRules,
                    snap.overrides());

            filterSettingsBus.set(next);
            runtimeConfig.rememberFilterRules(next.rules());

            // Best-effort: rebuild active target so changes take effect immediately.
            try {
              TargetRef active = targetCoordinator.getActiveTarget();
              if (active != null) transcriptRebuildService.rebuild(active);
            } catch (Exception ignored) {
            }
          } catch (Exception ignored) {
          }
        });

    JButton addRule = new JButton("Add rule...");
    JButton editRule = new JButton("Edit...");
    JButton deleteRule = new JButton("Delete");
    JButton moveRuleUp = new JButton("Move up");
    JButton moveRuleDown = new JButton("Move down");
    configureIconOnlyButton(addRule, "plus", "Add filter rule");
    configureIconOnlyButton(editRule, "edit", "Edit selected filter rule");
    configureIconOnlyButton(deleteRule, "trash", "Delete selected filter rule");
    configureIconOnlyButton(moveRuleUp, "arrow-up", "Move selected filter rule up");
    configureIconOnlyButton(moveRuleDown, "arrow-down", "Move selected filter rule down");
    editRule.setEnabled(false);
    deleteRule.setEnabled(false);
    moveRuleUp.setEnabled(false);
    moveRuleDown.setEnabled(false);

    Runnable refreshRuleButtons =
        () -> {
          int row = rulesTable.getSelectedRow();
          boolean has = row >= 0 && rulesModel.ruleAt(row) != null;
          editRule.setEnabled(has);
          deleteRule.setEnabled(has);

          if (!has) {
            moveRuleUp.setEnabled(false);
            moveRuleDown.setEnabled(false);
            return;
          }
          moveRuleUp.setEnabled(row > 0);
          moveRuleDown.setEnabled(row < (rulesModel.getRowCount() - 1));
        };

    rulesTable.getSelectionModel().addListSelectionListener(e -> refreshRuleButtons.run());

    // Drag-and-drop row reordering
    try {
      class RuleRowTransferHandler extends TransferHandler {
        private final DataFlavor rowFlavor;

        RuleRowTransferHandler() {
          try {
            rowFlavor =
                new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.Integer");
          } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
          if (!(c instanceof JTable t)) return null;
          int row = t.getSelectedRow();
          if (row < 0) return null;

          final Integer payload = row;
          return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
              return new DataFlavor[] {rowFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
              return rowFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
              if (!isDataFlavorSupported(flavor)) return null;
              return payload;
            }
          };
        }

        @Override
        public int getSourceActions(JComponent c) {
          return MOVE;
        }

        @Override
        public boolean canImport(TransferSupport support) {
          if (!support.isDrop()) return false;
          if (!(support.getComponent() instanceof JTable)) return false;
          support.setShowDropLocation(true);
          return support.isDataFlavorSupported(rowFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
          if (!canImport(support)) return false;
          if (!(support.getComponent() instanceof JTable target)) return false;
          if (!(support.getDropLocation() instanceof JTable.DropLocation dl)) return false;

          int dropViewRow = dl.getRow();
          if (dropViewRow < 0) dropViewRow = target.getRowCount();

          Integer fromViewRow;
          try {
            Object o = support.getTransferable().getTransferData(rowFlavor);
            if (!(o instanceof Integer i)) return false;
            fromViewRow = i;
          } catch (Exception ex) {
            return false;
          }

          // No-op drops (dragging onto itself)
          if (fromViewRow == dropViewRow || fromViewRow + 1 == dropViewRow) return false;

          // Convert to model rows (in case a row sorter is enabled later)
          int fromModelRow = target.convertRowIndexToModel(fromViewRow);

          // dropViewRow is an insertion point; when dropping at end, it equals rowCount.
          int dropModelRow;
          if (dropViewRow >= target.getRowCount()) {
            dropModelRow = target.getRowCount();
          } else {
            dropModelRow = target.convertRowIndexToModel(dropViewRow);
          }

          FilterSettings snap = filterSettingsBus.get();
          if (snap == null || snap.rules() == null) return false;

          List<FilterRule> nextRules = new ArrayList<>(snap.rules());
          if (fromModelRow < 0 || fromModelRow >= nextRules.size()) return false;

          FilterRule moving = nextRules.remove(fromModelRow);

          // If moving down, the removal shifts the insertion index.
          if (dropModelRow > fromModelRow) dropModelRow--;

          dropModelRow = Math.max(0, Math.min(dropModelRow, nextRules.size()));
          nextRules.add(dropModelRow, moving);

          FilterSettings next =
              new FilterSettings(
                  snap.filtersEnabledByDefault(),
                  snap.placeholdersEnabledByDefault(),
                  snap.placeholdersCollapsedByDefault(),
                  snap.placeholderMaxPreviewLines(),
                  snap.placeholderMaxLinesPerRun(),
                  snap.placeholderTooltipMaxTags(),
                  snap.historyPlaceholderMaxRunsPerBatch(),
                  snap.historyPlaceholdersEnabledByDefault(),
                  nextRules,
                  snap.overrides());

          filterSettingsBus.set(next);
          runtimeConfig.rememberFilterRules(next.rules());

          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null) transcriptRebuildService.rebuild(active);
          } catch (Exception ignored) {
          }

          final int newRow = dropModelRow;
          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  if (newRow >= 0 && newRow < rulesModel.getRowCount()) {
                    rulesTable.getSelectionModel().setSelectionInterval(newRow, newRow);
                    rulesTable.scrollRectToVisible(rulesTable.getCellRect(newRow, 0, true));
                  }
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });

          return true;
        }
      }

      rulesTable.setTransferHandler(new RuleRowTransferHandler());
    } catch (Exception ignored) {
      // best-effort; keep Move up/down buttons as fallback
    }

    Runnable moveSelectedRuleUp =
        () -> {
          int row = rulesTable.getSelectedRow();
          if (row <= 0) return;

          int newRow = row - 1;
          FilterSettings snap = filterSettingsBus.get();
          if (snap == null || snap.rules() == null) return;

          List<FilterRule> nextRules = new ArrayList<>(snap.rules());
          if (row >= nextRules.size() || newRow < 0) return;
          java.util.Collections.swap(nextRules, row, newRow);

          FilterSettings next =
              new FilterSettings(
                  snap.filtersEnabledByDefault(),
                  snap.placeholdersEnabledByDefault(),
                  snap.placeholdersCollapsedByDefault(),
                  snap.placeholderMaxPreviewLines(),
                  snap.placeholderMaxLinesPerRun(),
                  snap.placeholderTooltipMaxTags(),
                  snap.historyPlaceholderMaxRunsPerBatch(),
                  snap.historyPlaceholdersEnabledByDefault(),
                  nextRules,
                  snap.overrides());

          filterSettingsBus.set(next);
          runtimeConfig.rememberFilterRules(next.rules());

          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null) transcriptRebuildService.rebuild(active);
          } catch (Exception ignored) {
          }

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  rulesTable.getSelectionModel().setSelectionInterval(newRow, newRow);
                  rulesTable.scrollRectToVisible(rulesTable.getCellRect(newRow, 0, true));
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });
        };

    Runnable moveSelectedRuleDown =
        () -> {
          int row = rulesTable.getSelectedRow();
          if (row < 0) return;
          if (row >= rulesModel.getRowCount() - 1) return;

          int newRow = row + 1;
          FilterSettings snap = filterSettingsBus.get();
          if (snap == null || snap.rules() == null) return;

          List<FilterRule> nextRules = new ArrayList<>(snap.rules());
          if (newRow >= nextRules.size()) return;
          java.util.Collections.swap(nextRules, row, newRow);

          FilterSettings next =
              new FilterSettings(
                  snap.filtersEnabledByDefault(),
                  snap.placeholdersEnabledByDefault(),
                  snap.placeholdersCollapsedByDefault(),
                  snap.placeholderMaxPreviewLines(),
                  snap.placeholderMaxLinesPerRun(),
                  snap.placeholderTooltipMaxTags(),
                  snap.historyPlaceholderMaxRunsPerBatch(),
                  snap.historyPlaceholdersEnabledByDefault(),
                  nextRules,
                  snap.overrides());

          filterSettingsBus.set(next);
          runtimeConfig.rememberFilterRules(next.rules());

          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null) transcriptRebuildService.rebuild(active);
          } catch (Exception ignored) {
          }

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  rulesTable.getSelectionModel().setSelectionInterval(newRow, newRow);
                  rulesTable.scrollRectToVisible(rulesTable.getCellRect(newRow, 0, true));
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });
        };

    moveRuleUp.addActionListener(e -> moveSelectedRuleUp.run());
    moveRuleDown.addActionListener(e -> moveSelectedRuleDown.run());

    Runnable openEditRule =
        () -> {
          int row = rulesTable.getSelectedRow();
          if (row < 0) return;
          FilterRule seed = rulesModel.ruleAt(row);
          if (seed == null) return;

          FilterSettings snap = filterSettingsBus.get();

          Set<String> reserved = new HashSet<>();
          if (snap != null && snap.rules() != null) {
            for (FilterRule r : snap.rules()) {
              if (r == null) continue;
              if (seed.id() != null && seed.id().equals(r.id())) continue;
              reserved.add(r.nameKey());
            }
          }

          var edited =
              FilterRuleEntryDialog.open(
                  dialog, "Edit Filter Rule", seed, reserved, seed.scopePattern());
          if (edited.isEmpty()) return;

          List<FilterRule> nextRules = new ArrayList<>();
          boolean replaced = false;
          if (snap != null && snap.rules() != null) {
            for (FilterRule r : snap.rules()) {
              if (!replaced && r != null && seed.id() != null && seed.id().equals(r.id())) {
                nextRules.add(edited.get());
                replaced = true;
              } else {
                nextRules.add(r);
              }
            }
          }

          if (!replaced) {
            // Fallback (should be rare): replace by index if possible.
            if (row >= 0 && row < nextRules.size()) {
              nextRules.set(row, edited.get());
              replaced = true;
            }
          }

          // If we still didn't replace, append (better than losing the edit).
          if (!replaced) nextRules.add(edited.get());

          FilterSettings next =
              new FilterSettings(
                  snap != null ? snap.filtersEnabledByDefault() : true,
                  snap != null ? snap.placeholdersEnabledByDefault() : true,
                  snap != null ? snap.placeholdersCollapsedByDefault() : true,
                  snap != null ? snap.placeholderMaxPreviewLines() : 3,
                  snap != null ? snap.placeholderMaxLinesPerRun() : 250,
                  snap != null ? snap.placeholderTooltipMaxTags() : 12,
                  snap != null ? snap.historyPlaceholderMaxRunsPerBatch() : 10,
                  snap != null ? snap.historyPlaceholdersEnabledByDefault() : true,
                  nextRules,
                  snap != null ? snap.overrides() : List.of());

          filterSettingsBus.set(next);
          runtimeConfig.rememberFilterRules(next.rules());

          // Rebuild active target so changes take effect immediately.
          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null) transcriptRebuildService.rebuild(active);
          } catch (Exception ignored) {
          }

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  // Re-select the edited rule.
                  int idx = -1;
                  for (int i = 0; i < next.rules().size(); i++) {
                    FilterRule r = next.rules().get(i);
                    if (r != null
                        && edited.get().id() != null
                        && edited.get().id().equals(r.id())) {
                      idx = i;
                      break;
                    }
                  }
                  if (idx < 0) idx = Math.max(0, Math.min(row, rulesModel.getRowCount() - 1));
                  if (idx >= 0 && idx < rulesModel.getRowCount()) {
                    rulesTable.getSelectionModel().setSelectionInterval(idx, idx);
                    rulesTable.scrollRectToVisible(rulesTable.getCellRect(idx, 0, true));
                  }
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });
        };

    editRule.addActionListener(e -> openEditRule.run());

    deleteRule.addActionListener(
        e -> {
          int row = rulesTable.getSelectedRow();
          if (row < 0) return;
          FilterRule seed = rulesModel.ruleAt(row);
          if (seed == null) return;

          int confirm =
              JOptionPane.showConfirmDialog(
                  dialog,
                  "Delete filter rule '" + seed.name() + "'?",
                  "Delete Filter Rule",
                  JOptionPane.OK_CANCEL_OPTION);
          if (confirm != JOptionPane.OK_OPTION) return;

          FilterSettings snap = filterSettingsBus.get();
          List<FilterRule> nextRules = new ArrayList<>();
          boolean removed = false;
          if (snap != null && snap.rules() != null) {
            for (FilterRule r : snap.rules()) {
              if (!removed && r != null) {
                if (seed.id() != null && seed.id().equals(r.id())) {
                  removed = true;
                  continue;
                }
              }
              nextRules.add(r);
            }
          }

          if (!removed) {
            // Fallback: remove by index if possible.
            if (row >= 0 && row < nextRules.size()) {
              nextRules.remove(row);
              removed = true;
            }
          }

          if (!removed) return;

          FilterSettings next =
              new FilterSettings(
                  snap != null ? snap.filtersEnabledByDefault() : true,
                  snap != null ? snap.placeholdersEnabledByDefault() : true,
                  snap != null ? snap.placeholdersCollapsedByDefault() : true,
                  snap != null ? snap.placeholderMaxPreviewLines() : 3,
                  snap != null ? snap.placeholderMaxLinesPerRun() : 250,
                  snap != null ? snap.placeholderTooltipMaxTags() : 12,
                  snap != null ? snap.historyPlaceholderMaxRunsPerBatch() : 10,
                  snap != null ? snap.historyPlaceholdersEnabledByDefault() : true,
                  nextRules,
                  snap != null ? snap.overrides() : List.of());

          filterSettingsBus.set(next);
          runtimeConfig.rememberFilterRules(next.rules());

          // Rebuild active target so changes take effect immediately.
          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null) transcriptRebuildService.rebuild(active);
          } catch (Exception ignored) {
          }

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  int nextRow = Math.min(row, Math.max(0, rulesModel.getRowCount() - 1));
                  if (rulesModel.getRowCount() > 0) {
                    rulesTable.getSelectionModel().setSelectionInterval(nextRow, nextRow);
                    rulesTable.scrollRectToVisible(rulesTable.getCellRect(nextRow, 0, true));
                  }
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });
        });
    rulesTable.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
              openEditRule.run();
            }
          }
        });

    addRule.addActionListener(
        e -> {
          FilterSettings snap = filterSettingsBus.get();

          // Suggest a scope based on the currently active target.
          String suggestedScope = "*";
          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null && !active.isUiOnly()) {
              if (active.isStatus()) {
                suggestedScope = "*/status";
              } else {
                suggestedScope = active.serverId() + "/" + active.target();
              }
            }
          } catch (Exception ignored) {
          }

          Set<String> reserved = new HashSet<>();
          if (snap != null && snap.rules() != null) {
            for (FilterRule r : snap.rules()) {
              if (r == null) continue;
              reserved.add(r.nameKey());
            }
          }

          var created =
              FilterRuleEntryDialog.open(dialog, "Add Filter Rule", null, reserved, suggestedScope);
          if (created.isEmpty()) return;

          List<FilterRule> nextRules = new ArrayList<>();
          if (snap != null && snap.rules() != null) {
            nextRules.addAll(snap.rules());
          }
          nextRules.add(created.get());

          FilterSettings next =
              new FilterSettings(
                  snap != null ? snap.filtersEnabledByDefault() : true,
                  snap != null ? snap.placeholdersEnabledByDefault() : true,
                  snap != null ? snap.placeholdersCollapsedByDefault() : true,
                  snap != null ? snap.placeholderMaxPreviewLines() : 3,
                  snap != null ? snap.placeholderMaxLinesPerRun() : 250,
                  snap != null ? snap.placeholderTooltipMaxTags() : 12,
                  snap != null ? snap.historyPlaceholderMaxRunsPerBatch() : 10,
                  snap != null ? snap.historyPlaceholdersEnabledByDefault() : true,
                  nextRules,
                  snap != null ? snap.overrides() : List.of());

          filterSettingsBus.set(next);
          runtimeConfig.rememberFilterRules(next.rules());

          // Rebuild active target so changes take effect immediately.
          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null) transcriptRebuildService.rebuild(active);
          } catch (Exception ignored) {
          }

          // Select the newly-added rule.
          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  int row = rulesModel.getRowCount() - 1;
                  if (row >= 0) {
                    rulesTable.getSelectionModel().setSelectionInterval(row, row);
                    rulesTable.scrollRectToVisible(rulesTable.getCellRect(row, 0, true));
                  }
                } catch (Exception ignored) {
                }
              });
        });

    return new FilterControls(
        enabledByDefault,
        placeholdersEnabledByDefault,
        placeholdersCollapsedByDefault,
        previewLines,
        maxLinesPerRun,
        tooltipMaxTags,
        historyPlaceholdersEnabledByDefault,
        historyMaxRuns,
        model,
        table,
        add,
        remove,
        rulesTable,
        addRule,
        editRule,
        deleteRule,
        moveRuleUp,
        moveRuleDown);
  }

  private JPanel buildFiltersPanel(FilterControls c) {
    JPanel panel =
        new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]8[]6[grow,fill]"));

    panel.add(tabTitle("Filters"), "growx, wrap");
    panel.add(sectionTitle("Configuration"), "growx, wmin 0, wrap");
    panel.add(
        helpText("Filters only affect transcript rendering; messages are still logged."),
        "growx, wmin 0, wrap");

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("General", buildFiltersGeneralTab(c));
    tabs.addTab("Placeholders", buildFiltersPlaceholdersTab(c));
    tabs.addTab("History", buildFiltersHistoryTab(c));
    tabs.addTab("Overrides", buildFiltersOverridesTab(c));
    tabs.addTab("Rules", buildFiltersRulesTab(c));

    panel.add(tabs, "grow, push, wmin 0");
    return panel;
  }

  private JPanel buildFiltersGeneralTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", ""));
    panel.setOpaque(false);

    JPanel defaults = captionPanel("Defaults", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    defaults.add(c.filtersEnabledByDefault, "growx, wrap");
    defaults.add(
        helpText(
            "When disabled, rules and placeholders are ignored unless a scope override enables them."),
        "growx, wmin 0, wrap");
    panel.add(defaults, "growx, wmin 0");

    return panel;
  }

  private JPanel buildFiltersPlaceholdersTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]8[]8[]"));
    panel.setOpaque(false);

    JPanel behavior =
        captionPanel("Placeholder behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    behavior.add(c.placeholdersEnabledByDefault, "growx, wrap");
    behavior.add(c.placeholdersCollapsedByDefault, "growx, wrap");

    JPanel previewRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    previewRow.add(new JLabel("Placeholder preview lines:"), "split 2");
    previewRow.add(c.placeholderPreviewLines, "w 80!");
    behavior.add(previewRow, "growx, wrap");
    panel.add(behavior, "growx, wmin 0, wrap");

    JPanel limits =
        captionPanel("Preview and run limits", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    JPanel runCapRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    runCapRow.add(new JLabel("Max hidden lines per run:"), "split 2");
    runCapRow.add(c.placeholderMaxLinesPerRun, "w 80!");
    limits.add(runCapRow, "growx, wrap");
    limits.add(
        helpText(
            "0 = unlimited. Prevents a single placeholder from representing an enormous filtered run."),
        "growx, wmin 0, wrap");
    panel.add(limits, "growx, wmin 0, wrap");

    JPanel tooltip = captionPanel("Tooltip details", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    JPanel tooltipTagsRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    tooltipTagsRow.add(new JLabel("Tooltip tag limit:"), "split 2");
    tooltipTagsRow.add(c.placeholderTooltipMaxTags, "w 80!");
    tooltip.add(tooltipTagsRow, "growx, wrap");
    tooltip.add(
        helpText("0 = hide tags in the tooltip (rule + count still shown)."),
        "growx, wmin 0, wrap");
    panel.add(tooltip, "growx, wmin 0, wrap");

    return panel;
  }

  private JPanel buildFiltersHistoryTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", ""));
    panel.setOpaque(false);

    JPanel history = captionPanel("History loading", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    history.add(c.historyPlaceholdersEnabledByDefault, "growx, wrap");

    JPanel historyCapRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    historyCapRow.add(new JLabel("History placeholder run cap per batch:"), "split 2");
    historyCapRow.add(c.historyPlaceholderMaxRunsPerBatch, "w 80!");
    history.add(historyCapRow, "growx, wrap");
    history.add(
        helpText(
            "0 = unlimited. Limits how many filtered placeholder/hint runs appear per history load."),
        "growx, wmin 0, wrap");
    panel.add(history, "growx, wmin 0");

    return panel;
  }

  private JPanel buildFiltersOverridesTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", ""));
    panel.setOpaque(false);

    JScrollPane tableScroll = new JScrollPane(c.overridesTable);
    tableScroll.setPreferredSize(new Dimension(520, 220));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    buttons.add(c.addOverride);
    buttons.add(c.removeOverride);

    JPanel overrides =
        captionPanel("Scope overrides", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    overrides.add(
        helpText("Overrides apply by scope pattern. Most specific match wins."),
        "growx, wmin 0, wrap");
    overrides.add(tableScroll, "growx, wrap 8");
    overrides.add(buttons, "growx, wrap 8");
    overrides.add(
        helpText(
            "Tip: You can also manage overrides via /filter override ... and export with /filter export."),
        "growx, wmin 0, wrap");
    panel.add(overrides, "growx, wmin 0");

    return panel;
  }

  private JPanel buildFiltersRulesTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", ""));
    panel.setOpaque(false);

    JScrollPane rulesScroll = new JScrollPane(c.rulesTable);
    rulesScroll.setPreferredSize(new Dimension(760, 260));

    JPanel ruleButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    ruleButtons.add(c.addRule);
    ruleButtons.add(c.editRule);
    ruleButtons.add(c.deleteRule);
    ruleButtons.add(c.moveRuleUp);
    ruleButtons.add(c.moveRuleDown);

    JPanel rules = captionPanel("Filter rules", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    rules.add(
        helpText("Rules affect transcript rendering only (they do not prevent logging)."),
        "growx, wmin 0, wrap");
    rules.add(rulesScroll, "growx, wrap 8");
    rules.add(ruleButtons, "growx, wrap 8");
    rules.add(
        helpText(
            "Tip: You can also manage rules via /filter add|del|set and export with /filter export."),
        "growx, wmin 0, wrap");
    panel.add(rules, "growx, wmin 0");

    return panel;
  }

  private void applyFilterSettingsFromUi(FilterControls c) {
    if (c == null) return;

    FilterSettings prev = filterSettingsBus.get();
    boolean enabledByDefault = c.filtersEnabledByDefault.isSelected();
    boolean placeholdersEnabledByDefault = c.placeholdersEnabledByDefault.isSelected();
    boolean placeholdersCollapsedByDefault = c.placeholdersCollapsedByDefault.isSelected();
    int previewLines = ((Number) c.placeholderPreviewLines.getValue()).intValue();
    if (previewLines < 0) previewLines = 0;
    if (previewLines > 25) previewLines = 25;

    int maxLinesPerRun = ((Number) c.placeholderMaxLinesPerRun.getValue()).intValue();
    if (maxLinesPerRun < 0) maxLinesPerRun = 0;
    if (maxLinesPerRun > 50_000) maxLinesPerRun = 50_000;

    int tooltipMaxTags = ((Number) c.placeholderTooltipMaxTags.getValue()).intValue();
    if (tooltipMaxTags < 0) tooltipMaxTags = 0;
    if (tooltipMaxTags > 500) tooltipMaxTags = 500;

    boolean historyPlaceholdersEnabledByDefault =
        c.historyPlaceholdersEnabledByDefault.isSelected();

    int maxRunsPerBatch = ((Number) c.historyPlaceholderMaxRunsPerBatch.getValue()).intValue();
    if (maxRunsPerBatch < 0) maxRunsPerBatch = 0;
    if (maxRunsPerBatch > 5_000) maxRunsPerBatch = 5_000;

    List<FilterScopeOverride> overrides = c.overridesModel.toOverrides();

    FilterSettings next =
        new FilterSettings(
            enabledByDefault,
            placeholdersEnabledByDefault,
            placeholdersCollapsedByDefault,
            previewLines,
            maxLinesPerRun,
            tooltipMaxTags,
            maxRunsPerBatch,
            historyPlaceholdersEnabledByDefault,
            prev != null ? prev.rules() : List.of(),
            overrides);

    // If nothing changed, don't trigger a transcript rebuild. Pressing OK/Apply on the preferences
    // dialog should be a no-op for the active transcript unless a setting meaningfully changed.
    if (java.util.Objects.equals(prev, next)) {
      return;
    }

    filterSettingsBus.set(next);
    runtimeConfig.rememberFiltersEnabledByDefault(enabledByDefault);
    runtimeConfig.rememberFilterPlaceholdersEnabledByDefault(placeholdersEnabledByDefault);
    runtimeConfig.rememberFilterPlaceholdersCollapsedByDefault(placeholdersCollapsedByDefault);
    runtimeConfig.rememberFilterPlaceholderMaxPreviewLines(previewLines);
    runtimeConfig.rememberFilterPlaceholderMaxLinesPerRun(maxLinesPerRun);
    runtimeConfig.rememberFilterPlaceholderTooltipMaxTags(tooltipMaxTags);
    runtimeConfig.rememberFilterHistoryPlaceholdersEnabledByDefault(
        historyPlaceholdersEnabledByDefault);
    runtimeConfig.rememberFilterHistoryPlaceholderMaxRunsPerBatch(maxRunsPerBatch);
    runtimeConfig.rememberFilterOverrides(overrides);

    // Best-effort: rebuild active target so changes take effect immediately.
    try {
      TargetRef active = targetCoordinator.getActiveTarget();
      if (active != null) {
        transcriptRebuildService.rebuild(active);
      }
    } catch (Exception ignored) {
      // rebuild is best-effort; never block saving preferences
    }
  }
}
