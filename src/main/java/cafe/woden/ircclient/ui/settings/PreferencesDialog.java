package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.app.notifications.IrcEventNotificationRule;
import cafe.woden.ircclient.app.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.irc.PircbotxBotFactory;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.NetHeartbeatContext;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettings;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import cafe.woden.ircclient.ui.filter.FilterScopeOverride;
import cafe.woden.ircclient.ui.filter.FilterRule;
import cafe.woden.ircclient.ui.filter.FilterRuleEntryDialog;
import cafe.woden.ircclient.ui.filter.FilterSettings;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.DialogCloseableScopeDecorator;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.notify.sound.BuiltInSound;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettings;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettingsBus;
import cafe.woden.ircclient.util.VirtualThreads;
import com.formdev.flatlaf.FlatClientProperties;
import java.beans.PropertyChangeListener;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultCellEditor;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.ListCellRenderer;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.ListSelectionModel;
import javax.swing.JOptionPane;
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
  private final TargetCoordinator targetCoordinator;
  private final TrayService trayService;
  private final TrayNotificationService trayNotificationService;
  private final GnomeDbusNotificationBackend gnomeDbusBackend;
  private final NotificationSoundSettingsBus notificationSoundSettingsBus;
  private final IrcEventNotificationRulesBus ircEventNotificationRulesBus;
  private final NotificationSoundService notificationSoundService;
  private final ServerDialogs serverDialogs;

  private JDialog dialog;

  public PreferencesDialog(UiSettingsBus settingsBus,
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
                           TargetCoordinator targetCoordinator,
                           TrayService trayService,
                           TrayNotificationService trayNotificationService,
                           GnomeDbusNotificationBackend gnomeDbusBackend,
                           NotificationSoundSettingsBus notificationSoundSettingsBus,
                           IrcEventNotificationRulesBus ircEventNotificationRulesBus,
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
    this.ircEventNotificationRulesBus = ircEventNotificationRulesBus;
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
    ThemeAccentSettings initialAccent = accentSettingsBus != null
        ? accentSettingsBus.get()
        : new ThemeAccentSettings(UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);
    AccentControls accent = buildAccentControls(initialAccent);
    ThemeTweakSettings initialTweaks = tweakSettingsBus != null ? tweakSettingsBus.get() : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.COZY, 10);
    TweakControls tweaks = buildTweakControls(initialTweaks);

    ChatThemeSettings initialChatTheme = chatThemeSettingsBus != null
        ? chatThemeSettingsBus.get()
        : new ChatThemeSettings(ChatThemeSettings.Preset.DEFAULT, null, null, null, 35);
    ChatThemeControls chatTheme = buildChatThemeControls(initialChatTheme);

    // Allow mousewheel selection cycling for Appearance-tab combos.
    try { closeables.add(MouseWheelDecorator.decorateComboBoxSelection(theme.combo)); } catch (Exception ignored) {}
    try { closeables.add(MouseWheelDecorator.decorateComboBoxSelection(chatTheme.preset)); } catch (Exception ignored) {}
    try { closeables.add(MouseWheelDecorator.decorateComboBoxSelection(tweaks.density)); } catch (Exception ignored) {}

    // Live preview snapshot: used to rollback any preview-only changes on Cancel / window close.
    final java.util.concurrent.atomic.AtomicReference<String> committedThemeId = new java.util.concurrent.atomic.AtomicReference<>(
        normalizeThemeIdInternal(current != null ? current.theme() : null));
    final java.util.concurrent.atomic.AtomicReference<String> lastPreviewThemeId = new java.util.concurrent.atomic.AtomicReference<>(committedThemeId.get());
    final java.util.concurrent.atomic.AtomicReference<UiSettings> committedUiSettings = new java.util.concurrent.atomic.AtomicReference<>(current);
    final java.util.concurrent.atomic.AtomicReference<ThemeAccentSettings> committedAccentSettings = new java.util.concurrent.atomic.AtomicReference<>(
        initialAccent != null
            ? initialAccent
            : new ThemeAccentSettings(UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH));
    final java.util.concurrent.atomic.AtomicReference<ThemeTweakSettings> committedTweakSettings = new java.util.concurrent.atomic.AtomicReference<>(
        initialTweaks != null ? initialTweaks : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.COZY, 10));
    final java.util.concurrent.atomic.AtomicReference<ChatThemeSettings> committedChatThemeSettings = new java.util.concurrent.atomic.AtomicReference<>(
        initialChatTheme != null
            ? initialChatTheme
            : new ChatThemeSettings(ChatThemeSettings.Preset.DEFAULT, null, null, null, 35));

    final java.util.concurrent.atomic.AtomicBoolean suppressLivePreview = new java.util.concurrent.atomic.AtomicBoolean(false);

    final java.util.concurrent.atomic.AtomicReference<String> lastValidAccentHex = new java.util.concurrent.atomic.AtomicReference<>(
        committedAccentSettings.get() != null ? committedAccentSettings.get().accentColor() : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatTimestampHex = new java.util.concurrent.atomic.AtomicReference<>(
        committedChatThemeSettings.get() != null ? committedChatThemeSettings.get().timestampColor() : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatSystemHex = new java.util.concurrent.atomic.AtomicReference<>(
        committedChatThemeSettings.get() != null ? committedChatThemeSettings.get().systemColor() : null);
    final java.util.concurrent.atomic.AtomicReference<String> lastValidChatMentionHex = new java.util.concurrent.atomic.AtomicReference<>(
        committedChatThemeSettings.get() != null ? committedChatThemeSettings.get().mentionBgColor() : null);

    // Debounced live preview to avoid spamming full UI refreshes while sliders are dragged.
    final javax.swing.Timer lafPreviewTimer = new javax.swing.Timer(140, null);
    lafPreviewTimer.setRepeats(false);
    final Runnable applyLafPreview = () -> {
      if (suppressLivePreview.get()) return;
      if (themeManager == null) return;

      String sel = normalizeThemeIdInternal(String.valueOf(theme.combo.getSelectedItem()));
      if (sel.isBlank()) return;

      if (tweakSettingsBus != null) {
        DensityOption opt = (DensityOption) tweaks.density.getSelectedItem();
        String densityId = opt != null ? opt.id() : "cozy";
        ThemeTweakSettings nextTweaks = new ThemeTweakSettings(
            ThemeTweakSettings.ThemeDensity.from(densityId),
            tweaks.cornerRadius.getValue()
        );
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

        ThemeAccentSettings nextAccent = new ThemeAccentSettings(hex, accent.strength.getValue());
        accentSettingsBus.set(nextAccent);
      }

      if (!java.util.Objects.equals(sel, lastPreviewThemeId.get())) {
        themeManager.applyTheme(sel);
        lastPreviewThemeId.set(sel);
      } else {
        themeManager.applyAppearance(true);
      }

      // Theme switches can change the "Theme" accent color; refresh the preview pill.
      try { accent.updateChip.run(); } catch (Exception ignored) {}
    };
    lafPreviewTimer.addActionListener(e -> applyLafPreview.run());
    final Runnable scheduleLafPreview = () -> {
      if (suppressLivePreview.get()) return;
      lafPreviewTimer.restart();
    };

    final javax.swing.Timer chatPreviewTimer = new javax.swing.Timer(120, null);
    chatPreviewTimer.setRepeats(false);
    final java.util.function.BiFunction<JTextField, java.util.concurrent.atomic.AtomicReference<String>, String> parseOptionalHex = (field, lastRef) -> {
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
    final Runnable applyChatPreview = () -> {
      if (suppressLivePreview.get()) return;
      if (themeManager == null) return;
      if (chatThemeSettingsBus == null) return;

      ChatThemeSettings.Preset presetV = (chatTheme.preset.getSelectedItem() instanceof ChatThemeSettings.Preset p)
          ? p
          : ChatThemeSettings.Preset.DEFAULT;

      String tsHexV = parseOptionalHex.apply(chatTheme.timestamp.hex, lastValidChatTimestampHex);
      String sysHexV = parseOptionalHex.apply(chatTheme.system.hex, lastValidChatSystemHex);
      String menHexV = parseOptionalHex.apply(chatTheme.mention.hex, lastValidChatMentionHex);
      int mentionStrengthV = chatTheme.mentionStrength.getValue();

      ChatThemeSettings nextChatTheme = new ChatThemeSettings(presetV, tsHexV, sysHexV, menHexV, mentionStrengthV);
      chatThemeSettingsBus.set(nextChatTheme);
      themeManager.refreshChatStyles();
    };
    chatPreviewTimer.addActionListener(e -> applyChatPreview.run());
    final Runnable scheduleChatPreview = () -> {
      if (suppressLivePreview.get()) return;
      chatPreviewTimer.restart();
    };

    final javax.swing.Timer fontPreviewTimer = new javax.swing.Timer(120, null);
    fontPreviewTimer.setRepeats(false);
    final Runnable applyFontPreview = () -> {
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
    final Runnable scheduleFontPreview = () -> {
      if (suppressLivePreview.get()) return;
      fontPreviewTimer.restart();
    };

    closeables.add(() -> {
      lafPreviewTimer.stop();
      chatPreviewTimer.stop();
      fontPreviewTimer.stop();
    });

    final Runnable restoreCommittedAppearance = () -> {
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
          accentSettingsBus.set(a != null
              ? a
              : new ThemeAccentSettings(UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH));
        }
        if (tweakSettingsBus != null) {
          ThemeTweakSettings tw = committedTweakSettings.get();
          tweakSettingsBus.set(tw != null ? tw : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.COZY, 10));
        }
        if (chatThemeSettingsBus != null) {
          ChatThemeSettings ct = committedChatThemeSettings.get();
          chatThemeSettingsBus.set(ct != null ? ct : new ChatThemeSettings(ChatThemeSettings.Preset.DEFAULT, null, null, null, 35));
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

    final boolean[] ignoreThemeComboEvents = new boolean[] { true };
    theme.combo.addActionListener(e -> {
      if (ignoreThemeComboEvents[0]) return;
      scheduleLafPreview.run();
    });
    ignoreThemeComboEvents[0] = false;

    // LAF + accent/tweak preview
    accent.enabled.addActionListener(e -> scheduleLafPreview.run());
    accent.preset.addActionListener(e -> scheduleLafPreview.run());
    accent.strength.addChangeListener(e -> scheduleLafPreview.run());
    accent.hex.getDocument().addDocumentListener(new SimpleDocListener(() -> {
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

    // Chat theme preview (transcript-only)
    chatTheme.preset.addActionListener(e -> scheduleChatPreview.run());
    chatTheme.mentionStrength.addChangeListener(e -> scheduleChatPreview.run());
    chatTheme.timestamp.hex.getDocument().addDocumentListener(new SimpleDocListener(() -> {
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
    chatTheme.system.hex.getDocument().addDocumentListener(new SimpleDocListener(() -> {
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
    chatTheme.mention.hex.getDocument().addDocumentListener(new SimpleDocListener(() -> {
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

    // Chat font preview
    fonts.fontFamily.addActionListener(e -> scheduleFontPreview.run());
    fonts.fontFamily.addItemListener(e -> {
      if (e != null && e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
        scheduleFontPreview.run();
      }
    });
    java.awt.Component ffEditor = fonts.fontFamily.getEditor() != null
        ? fonts.fontFamily.getEditor().getEditorComponent()
        : null;
    if (ffEditor instanceof JTextField tf) {
      tf.getDocument().addDocumentListener(new SimpleDocListener(scheduleFontPreview));
    }
    fonts.fontSize.addChangeListener(e -> scheduleFontPreview.run());
    JCheckBox autoConnectOnStart = buildAutoConnectCheckbox(current);
    NotificationSoundSettings soundSettings = notificationSoundSettingsBus != null
        ? notificationSoundSettingsBus.get()
        : new NotificationSoundSettings(true, BuiltInSound.NOTIF_1.name(), false, null);
    TrayControls trayControls = buildTrayControls(current, soundSettings);

    ImageEmbedControls imageEmbeds = buildImageEmbedControls(current, closeables);
    LinkPreviewControls linkPreviews = buildLinkPreviewControls(current);
    TimestampControls timestamps = buildTimestampControls(current);

    JCheckBox presenceFolds = buildPresenceFoldsCheckbox(current);
    JCheckBox ctcpRequestsInActiveTarget = buildCtcpRequestsInActiveTargetCheckbox(current);
    JCheckBox typingIndicatorsSendEnabled = buildTypingIndicatorsSendCheckbox(current);
    JCheckBox typingIndicatorsReceiveEnabled = buildTypingIndicatorsReceiveCheckbox(current);
    Ircv3CapabilitiesControls ircv3Capabilities = buildIrcv3CapabilitiesControls();
    NickColorControls nickColors = buildNickColorControls(owner, closeables);

    HistoryControls history = buildHistoryControls(current, closeables);
    LoggingControls logging = buildLoggingControls(logProps, closeables);

    OutgoingColorControls outgoing = buildOutgoingColorControls(current);
    NetworkAdvancedControls network = buildNetworkAdvancedControls(current, closeables);
    ProxyControls proxy = network.proxy;
    UserhostControls userhost = network.userhost;
    UserInfoEnrichmentControls enrichment = network.enrichment;
    HeartbeatControls heartbeat = network.heartbeat;
    JCheckBox trustAllTlsCertificates = network.trustAllTlsCertificates;

    JPanel networkPanel = network.networkPanel;
    JPanel userLookupsPanel = network.userLookupsPanel;

    NotificationRulesControls notifications = buildNotificationRulesControls(current, closeables);
    IrcEventNotificationControls ircEventNotifications = buildIrcEventNotificationControls(
        ircEventNotificationRulesBus != null
            ? ircEventNotificationRulesBus.get()
            : IrcEventNotificationRule.defaults());

    FilterControls filters = buildFilterControls(filterSettingsBus.get(), closeables);

    JPanel appearancePanel = buildAppearancePanel(theme, accent, chatTheme, fonts, tweaks);
    JPanel startupPanel = buildStartupPanel(autoConnectOnStart);
    JPanel trayPanel = buildTrayNotificationsPanel(trayControls);
    JPanel chatPanel = buildChatPanel(presenceFolds, ctcpRequestsInActiveTarget, nickColors, timestamps, outgoing);
    JPanel ircv3Panel = buildIrcv3CapabilitiesPanel(
        typingIndicatorsSendEnabled,
        typingIndicatorsReceiveEnabled,
        ircv3Capabilities);
    JPanel embedsPanel = buildEmbedsAndPreviewsPanel(imageEmbeds, linkPreviews);
    JPanel historyStoragePanel = buildHistoryAndStoragePanel(logging, history);
    JPanel notificationsPanel = buildNotificationsPanel(notifications, ircEventNotifications);
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

    Runnable doApply = () -> {
      String t = String.valueOf(theme.combo.getSelectedItem());
      String fam = String.valueOf(fonts.fontFamily.getSelectedItem());
      int size = ((Number) fonts.fontSize.getValue()).intValue();

      ThemeTweakSettings prevTweaks = tweakSettingsBus != null
          ? tweakSettingsBus.get()
          : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.COZY, 10);
      DensityOption densityOpt = (DensityOption) tweaks.density.getSelectedItem();
      String densityIdV = densityOpt != null ? densityOpt.id() : "cozy";
      int cornerRadiusV = tweaks.cornerRadius.getValue();
      ThemeTweakSettings nextTweaks = new ThemeTweakSettings(
          ThemeTweakSettings.ThemeDensity.from(densityIdV),
          cornerRadiusV
      );
      boolean tweaksChanged = !java.util.Objects.equals(prevTweaks, nextTweaks);

      ThemeAccentSettings prevAccent = accentSettingsBus != null
          ? accentSettingsBus.get()
          : new ThemeAccentSettings(UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);
      boolean accentOverrideEnabledV = accent.enabled.isSelected();
      int accentStrengthV = accent.strength.getValue();
      String accentHexV = null;
      if (accentOverrideEnabledV) {
        Color parsed = parseHexColorLenient(accent.hex.getText());
        if (parsed == null) {
          JOptionPane.showMessageDialog(dialog,
              "Accent color must be a hex value like #RRGGBB.",
              "Invalid accent color",
              JOptionPane.ERROR_MESSAGE);
          return;
        }
        accentHexV = toHex(parsed);
      }
      ThemeAccentSettings nextAccent = new ThemeAccentSettings(accentHexV, accentStrengthV);
      boolean accentChanged = !java.util.Objects.equals(prevAccent, nextAccent);

      ChatThemeSettings prevChatTheme = chatThemeSettingsBus != null
          ? chatThemeSettingsBus.get()
          : new ChatThemeSettings(ChatThemeSettings.Preset.DEFAULT, null, null, null, 35);

      ChatThemeSettings.Preset presetV = (ChatThemeSettings.Preset) chatTheme.preset.getSelectedItem();
      if (presetV == null) presetV = ChatThemeSettings.Preset.DEFAULT;

      String tsHexV = chatTheme.timestamp.hex.getText();
      tsHexV = tsHexV != null ? tsHexV.trim() : "";
      if (tsHexV.isBlank()) tsHexV = null;
      if (tsHexV != null) {
        Color c = parseHexColorLenient(tsHexV);
        if (c == null) {
          JOptionPane.showMessageDialog(dialog,
              "Chat timestamp color must be a hex value like #RRGGBB (or blank for default).",
              "Invalid chat timestamp color",
              JOptionPane.ERROR_MESSAGE);
          return;
        }
        tsHexV = toHex(c);
      }

      String sysHexV = chatTheme.system.hex.getText();
      sysHexV = sysHexV != null ? sysHexV.trim() : "";
      if (sysHexV.isBlank()) sysHexV = null;
      if (sysHexV != null) {
        Color c = parseHexColorLenient(sysHexV);
        if (c == null) {
          JOptionPane.showMessageDialog(dialog,
              "Chat system color must be a hex value like #RRGGBB (or blank for default).",
              "Invalid chat system color",
              JOptionPane.ERROR_MESSAGE);
          return;
        }
        sysHexV = toHex(c);
      }

      String menHexV = chatTheme.mention.hex.getText();
      menHexV = menHexV != null ? menHexV.trim() : "";
      if (menHexV.isBlank()) menHexV = null;
      if (menHexV != null) {
        Color c = parseHexColorLenient(menHexV);
        if (c == null) {
          JOptionPane.showMessageDialog(dialog,
              "Mention highlight color must be a hex value like #RRGGBB (or blank for default).",
              "Invalid mention highlight color",
              JOptionPane.ERROR_MESSAGE);
          return;
        }
        menHexV = toHex(c);
      }

      int mentionStrengthV = chatTheme.mentionStrength.getValue();
      ChatThemeSettings nextChatTheme = new ChatThemeSettings(presetV, tsHexV, sysHexV, menHexV, mentionStrengthV);
      boolean chatThemeChanged = !java.util.Objects.equals(prevChatTheme, nextChatTheme);

      boolean autoConnectV = autoConnectOnStart.isSelected();

      boolean trayEnabledV = trayControls.enabled.isSelected();
      boolean trayCloseToTrayV = trayEnabledV && trayControls.closeToTray.isSelected();
      boolean trayMinimizeToTrayV = trayEnabledV && trayControls.minimizeToTray.isSelected();
      boolean trayStartMinimizedV = trayEnabledV && trayControls.startMinimized.isSelected();

      boolean trayNotifyHighlightsV = trayEnabledV && trayControls.notifyHighlights.isSelected();
      boolean trayNotifyPrivateMessagesV = trayEnabledV && trayControls.notifyPrivateMessages.isSelected();
      boolean trayNotifyConnectionStateV = trayEnabledV && trayControls.notifyConnectionState.isSelected();

      boolean trayNotifyOnlyWhenUnfocusedV = trayEnabledV && trayControls.notifyOnlyWhenUnfocused.isSelected();
      boolean trayNotifyOnlyWhenMinimizedOrHiddenV = trayEnabledV && trayControls.notifyOnlyWhenMinimizedOrHidden.isSelected();
      boolean trayNotifySuppressWhenTargetActiveV = trayEnabledV && trayControls.notifySuppressWhenTargetActive.isSelected();

      boolean trayLinuxDbusActionsEnabledV = trayEnabledV && trayControls.linuxDbusActions.isSelected();

      boolean trayNotificationSoundsEnabledV = trayEnabledV && trayControls.notificationSoundsEnabled.isSelected();
      BuiltInSound selectedSoundV = (BuiltInSound) trayControls.notificationSound.getSelectedItem();
      String trayNotificationSoundIdV = selectedSoundV != null ? selectedSoundV.name() : BuiltInSound.NOTIF_1.name();

      boolean trayNotificationSoundUseCustomV = trayControls.notificationSoundUseCustom.isSelected();
      String trayNotificationSoundCustomPathV = trayControls.notificationSoundCustomPath.getText();
      trayNotificationSoundCustomPathV = trayNotificationSoundCustomPathV != null ? trayNotificationSoundCustomPathV.trim() : "";
      if (trayNotificationSoundCustomPathV.isBlank()) trayNotificationSoundCustomPathV = null;
      if (trayNotificationSoundUseCustomV && trayNotificationSoundCustomPathV == null) {
        trayNotificationSoundUseCustomV = false;
      }

      boolean timestampsEnabledV = timestamps.enabled.isSelected();
      boolean timestampsIncludeChatMessagesV = timestamps.includeChatMessages.isSelected();
      boolean timestampsIncludePresenceMessagesV = timestamps.includePresenceMessages.isSelected();
      String timestampFormatV = timestamps.format.getText() != null ? timestamps.format.getText().trim() : "";
      if (timestampFormatV.isBlank()) timestampFormatV = "HH:mm:ss";
      try {
        DateTimeFormatter.ofPattern(timestampFormatV);
      } catch (Exception ex) {
        javax.swing.JOptionPane.showMessageDialog(dialog,
            "Invalid timestamp format: " + timestampFormatV + "\n\nUse a java.time DateTimeFormatter pattern (e.g. HH:mm:ss)",
            "Invalid timestamp format",
            javax.swing.JOptionPane.ERROR_MESSAGE);
        return;
      }
      timestamps.format.setText(timestampFormatV);

      boolean presenceFoldsV = presenceFolds.isSelected();
      boolean ctcpRequestsInActiveTargetV = ctcpRequestsInActiveTarget.isSelected();
      boolean typingIndicatorsSendEnabledV = typingIndicatorsSendEnabled.isSelected();
      boolean typingIndicatorsReceiveEnabledV = typingIndicatorsReceiveEnabled.isSelected();
      Map<String, Boolean> ircv3CapabilitiesV = ircv3Capabilities.snapshot();

      boolean nickColoringEnabledV = nickColors.enabled.isSelected();
      double nickColorMinContrastV = ((Number) nickColors.minContrast.getValue()).doubleValue();
      if (nickColorMinContrastV <= 0) nickColorMinContrastV = 3.0;

      int maxImageW = ((Number) imageEmbeds.maxWidth.getValue()).intValue();
      int maxImageH = ((Number) imageEmbeds.maxHeight.getValue()).intValue();

      int historyInitialLoadV = ((Number) history.initialLoadLines.getValue()).intValue();
      int historyPageSizeV = ((Number) history.pageSize.getValue()).intValue();
      int commandHistoryMaxSizeV = ((Number) history.commandHistoryMaxSize.getValue()).intValue();
      IrcProperties.Proxy proxyCfg;
      try {
        boolean proxyEnabledV = proxy.enabled.isSelected();
        String proxyHostV = proxy.host.getText() != null ? proxy.host.getText().trim() : "";
        int proxyPortV = ((Number) proxy.port.getValue()).intValue();
        String proxyUserV = proxy.username.getText() != null ? proxy.username.getText().trim() : "";
        String proxyPassV = new String(proxy.password.getPassword());
        boolean proxyRemoteDnsV = proxy.remoteDns.isSelected();

        int connectTimeoutSecondsV = ((Number) proxy.connectTimeoutSeconds.getValue()).intValue();
        int readTimeoutSecondsV = ((Number) proxy.readTimeoutSeconds.getValue()).intValue();

        if (proxyEnabledV) {
          if (proxyHostV.isBlank()) throw new IllegalArgumentException("Proxy host is required when proxy is enabled.");
          if (proxyPortV <= 0 || proxyPortV > 65535) throw new IllegalArgumentException("Proxy port must be 1..65535.");
        }

        proxyCfg = new IrcProperties.Proxy(
            proxyEnabledV,
            proxyHostV,
            proxyPortV,
            proxyUserV,
            proxyPassV,
            proxyRemoteDnsV,
            Math.max(1L, connectTimeoutSecondsV) * 1000L,
            Math.max(1L, readTimeoutSecondsV) * 1000L
        );
      } catch (Exception ex) {
        javax.swing.JOptionPane.showMessageDialog(dialog,
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

        heartbeatCfg = new IrcProperties.Heartbeat(
            hbEnabledV,
            hbCheckSecondsV * 1000L,
            hbTimeoutSecondsV * 1000L
        );
      } catch (Exception ex) {
        javax.swing.JOptionPane.showMessageDialog(dialog,
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
      int uieUserhostMinIntervalV = ((Number) enrichment.userhostMinIntervalSeconds.getValue()).intValue();
      int uieUserhostMaxPerMinuteV = ((Number) enrichment.userhostMaxPerMinute.getValue()).intValue();
      int uieUserhostNickCooldownV = ((Number) enrichment.userhostNickCooldownMinutes.getValue()).intValue();
      int uieUserhostMaxNicksV = ((Number) enrichment.userhostMaxNicksPerCommand.getValue()).intValue();

      boolean uieWhoisFallbackEnabledRawV = enrichment.whoisFallbackEnabled.isSelected();
      int uieWhoisMinIntervalV = ((Number) enrichment.whoisMinIntervalSeconds.getValue()).intValue();
      int uieWhoisNickCooldownV = ((Number) enrichment.whoisNickCooldownMinutes.getValue()).intValue();

      boolean uiePeriodicRefreshEnabledRawV = enrichment.periodicRefreshEnabled.isSelected();
      int uiePeriodicRefreshIntervalV = ((Number) enrichment.periodicRefreshIntervalSeconds.getValue()).intValue();
      int uiePeriodicRefreshNicksPerTickV = ((Number) enrichment.periodicRefreshNicksPerTick.getValue()).intValue();
      boolean uieWhoisFallbackEnabledV = userInfoEnrichmentEnabledV && uieWhoisFallbackEnabledRawV;
      boolean uiePeriodicRefreshEnabledV = userInfoEnrichmentEnabledV && uiePeriodicRefreshEnabledRawV;

      UiSettings prev = settingsBus.get();
      boolean outgoingColorEnabledV = outgoing.enabled.isSelected();
      String outgoingHexV = UiSettings.normalizeHexOrDefault(outgoing.hex.getText(), prev.clientLineColor());
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

      ValidationError notifErr = notifications.model.firstValidationError();
      if (notifErr != null) {
        refreshNotificationRuleValidation(notifications);
        JOptionPane.showMessageDialog(dialog,
            notifErr.formatForDialog(),
            "Invalid notification rule",
            JOptionPane.ERROR_MESSAGE);
        return;
      }

      int notificationRuleCooldownSecondsV = ((Number) notifications.cooldownSeconds.getValue()).intValue();
      if (notificationRuleCooldownSecondsV < 0) notificationRuleCooldownSecondsV = 15;
      if (notificationRuleCooldownSecondsV > 3600) notificationRuleCooldownSecondsV = 3600;
      List<NotificationRule> notificationRulesV = notifications.model.snapshot();
      List<IrcEventNotificationRule> ircEventNotificationRulesV = ircEventNotifications.model.snapshot();

      UiSettings next = new UiSettings(
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

          notificationRuleCooldownSecondsV,
          notificationRulesV
      );

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

      runtimeConfig.rememberUiSettings(next.theme(), next.chatFontFamily(), next.chatFontSize());
      // Chat theme (transcript-only palette)
      runtimeConfig.rememberChatThemePreset(nextChatTheme.preset().name());
      runtimeConfig.rememberChatTimestampColor(nextChatTheme.timestampColor());
      runtimeConfig.rememberChatSystemColor(nextChatTheme.systemColor());
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
      runtimeConfig.rememberTrayNotifyOnlyWhenMinimizedOrHidden(next.trayNotifyOnlyWhenMinimizedOrHidden());
      runtimeConfig.rememberTrayNotifySuppressWhenTargetActive(next.trayNotifySuppressWhenTargetActive());
      runtimeConfig.rememberTrayLinuxDbusActionsEnabled(next.trayLinuxDbusActionsEnabled());

      if (notificationSoundSettingsBus != null) {
        notificationSoundSettingsBus.set(new NotificationSoundSettings(
            trayNotificationSoundsEnabledV,
            trayNotificationSoundIdV,
            trayNotificationSoundUseCustomV,
            trayNotificationSoundCustomPathV
        ));
      }
      runtimeConfig.rememberTrayNotificationSoundsEnabled(trayNotificationSoundsEnabledV);
      runtimeConfig.rememberTrayNotificationSound(trayNotificationSoundIdV);
      runtimeConfig.rememberTrayNotificationSoundUseCustom(trayNotificationSoundUseCustomV);
      runtimeConfig.rememberTrayNotificationSoundCustomPath(trayNotificationSoundCustomPathV);

      if (trayService != null) {
        trayService.applySettings();
      }
      runtimeConfig.rememberImageEmbedsEnabled(next.imageEmbedsEnabled());
      runtimeConfig.rememberImageEmbedsCollapsedByDefault(next.imageEmbedsCollapsedByDefault());
      runtimeConfig.rememberImageEmbedsMaxWidthPx(next.imageEmbedsMaxWidthPx());
      runtimeConfig.rememberImageEmbedsMaxHeightPx(next.imageEmbedsMaxHeightPx());
      runtimeConfig.rememberImageEmbedsAnimateGifs(next.imageEmbedsAnimateGifs());
      runtimeConfig.rememberLinkPreviewsEnabled(next.linkPreviewsEnabled());
      runtimeConfig.rememberLinkPreviewsCollapsedByDefault(next.linkPreviewsCollapsedByDefault());
      runtimeConfig.rememberPresenceFoldsEnabled(next.presenceFoldsEnabled());
      runtimeConfig.rememberCtcpRequestsInActiveTargetEnabled(next.ctcpRequestsInActiveTargetEnabled());
      runtimeConfig.rememberTypingIndicatorsEnabled(next.typingIndicatorsEnabled());
      runtimeConfig.rememberTypingIndicatorsReceiveEnabled(next.typingIndicatorsReceiveEnabled());
      persistIrcv3Capabilities(ircv3CapabilitiesV);

      if (nickColorSettingsBus != null) {
        nickColorSettingsBus.set(new NickColorSettings(nickColoringEnabledV, nickColorMinContrastV));
      }
      runtimeConfig.rememberNickColoringEnabled(nickColoringEnabledV);
      runtimeConfig.rememberNickColorMinContrast(nickColorMinContrastV);
      runtimeConfig.rememberTimestampsEnabled(next.timestampsEnabled());
      runtimeConfig.rememberTimestampFormat(next.timestampFormat());
      runtimeConfig.rememberTimestampsIncludeChatMessages(next.timestampsIncludeChatMessages());
      runtimeConfig.rememberTimestampsIncludePresenceMessages(next.timestampsIncludePresenceMessages());

      runtimeConfig.rememberChatHistoryInitialLoadLines(next.chatHistoryInitialLoadLines());
      runtimeConfig.rememberChatHistoryPageSize(next.chatHistoryPageSize());
      runtimeConfig.rememberCommandHistoryMaxSize(next.commandHistoryMaxSize());

      applyFilterSettingsFromUi(filters);
      runtimeConfig.rememberChatLoggingEnabled(logging.enabled.isSelected());
      runtimeConfig.rememberChatLoggingLogSoftIgnoredLines(logging.logSoftIgnored.isSelected());
      runtimeConfig.rememberChatLoggingLogPrivateMessages(logging.logPrivateMessages.isSelected());
      runtimeConfig.rememberChatLoggingSavePrivateMessageList(logging.savePrivateMessageList.isSelected());
      runtimeConfig.rememberChatLoggingDbFileBaseName(logging.dbBaseName.getText());
      runtimeConfig.rememberChatLoggingDbNextToRuntimeConfig(logging.dbNextToConfig.isSelected());

      runtimeConfig.rememberChatLoggingKeepForever(logging.keepForever.isSelected());
      runtimeConfig.rememberChatLoggingRetentionDays(((Number) logging.retentionDays.getValue()).intValue());

      runtimeConfig.rememberClientLineColorEnabled(next.clientLineColorEnabled());
      runtimeConfig.rememberClientLineColor(next.clientLineColor());

      runtimeConfig.rememberNotificationRuleCooldownSeconds(next.notificationRuleCooldownSeconds());
      runtimeConfig.rememberNotificationRules(notificationRulesV);
      runtimeConfig.rememberIrcEventNotificationRules(ircEventNotificationRulesV);
      if (ircEventNotificationRulesBus != null) {
        ircEventNotificationRulesBus.set(ircEventNotificationRulesV);
      }

      runtimeConfig.rememberUserhostDiscoveryEnabled(next.userhostDiscoveryEnabled());
      runtimeConfig.rememberUserhostMinIntervalSeconds(next.userhostMinIntervalSeconds());
      runtimeConfig.rememberUserhostMaxCommandsPerMinute(next.userhostMaxCommandsPerMinute());
      runtimeConfig.rememberUserhostNickCooldownMinutes(next.userhostNickCooldownMinutes());
      runtimeConfig.rememberUserhostMaxNicksPerCommand(next.userhostMaxNicksPerCommand());
      runtimeConfig.rememberUserInfoEnrichmentEnabled(next.userInfoEnrichmentEnabled());
      runtimeConfig.rememberUserInfoEnrichmentWhoisFallbackEnabled(next.userInfoEnrichmentWhoisFallbackEnabled());

      runtimeConfig.rememberUserInfoEnrichmentUserhostMinIntervalSeconds(next.userInfoEnrichmentUserhostMinIntervalSeconds());
      runtimeConfig.rememberUserInfoEnrichmentUserhostMaxCommandsPerMinute(next.userInfoEnrichmentUserhostMaxCommandsPerMinute());
      runtimeConfig.rememberUserInfoEnrichmentUserhostNickCooldownMinutes(next.userInfoEnrichmentUserhostNickCooldownMinutes());
      runtimeConfig.rememberUserInfoEnrichmentUserhostMaxNicksPerCommand(next.userInfoEnrichmentUserhostMaxNicksPerCommand());

      runtimeConfig.rememberUserInfoEnrichmentWhoisMinIntervalSeconds(next.userInfoEnrichmentWhoisMinIntervalSeconds());
      runtimeConfig.rememberUserInfoEnrichmentWhoisNickCooldownMinutes(next.userInfoEnrichmentWhoisNickCooldownMinutes());

      runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshEnabled(next.userInfoEnrichmentPeriodicRefreshEnabled());
      runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshIntervalSeconds(next.userInfoEnrichmentPeriodicRefreshIntervalSeconds());
      runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshNicksPerTick(next.userInfoEnrichmentPeriodicRefreshNicksPerTick());
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
    };

    apply.addActionListener(e -> doApply.run());
    final JDialog d = createDialog(owner);
    this.dialog = d;
    d.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override public void windowClosing(java.awt.event.WindowEvent e) {
        restoreCommittedAppearance.run();
      }
    });

    final CloseableScope scope = DialogCloseableScopeDecorator.install(d);
    closeables.forEach(scope::add);
    scope.addCleanup(() -> {
      if (this.dialog == d) this.dialog = null;
    });

    ok.addActionListener(e -> {
      doApply.run();
      d.dispose();
    });
    cancel.addActionListener(e -> {
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
    tabs.addTab("Startup", wrapTab(startupPanel));
    tabs.addTab("Tray & Notifications", wrapTab(trayPanel));
    tabs.addTab("Chat", wrapTab(chatPanel));
    tabs.addTab("IRCv3", wrapTab(ircv3Panel));
    tabs.addTab("Embeds & Previews", wrapTab(embedsPanel));
    tabs.addTab("History & Storage", wrapTab(historyStoragePanel));
    tabs.addTab("Notifications", wrapTab(notificationsPanel));
    tabs.addTab("Filters", wrapTab(filtersPanel));
    tabs.addTab("Network", wrapTab(networkPanel));
    tabs.addTab("User lookups", wrapTab(userLookupsPanel));

    d.setLayout(new BorderLayout());
    d.add(tabs, BorderLayout.CENTER);
    d.add(buttons, BorderLayout.SOUTH);
    // A tiny minimum size makes "dynamic tab sizing" feel jarring (some tabs would shrink the whole dialog
    // to near-nothing). Keep a comfortable baseline so tabs like Network don't open comically short.
    d.setMinimumSize(new Dimension(680, 540));
    installDynamicTabSizing(d, tabs, owner);
    d.setLocationRelativeTo(owner);
    d.setVisible(true);
  }

  /**
   * JTabbedPane's preferred size is normally the max of all tabs.
   * That works for fixed-size tab UIs, but for settings screens it often
   * causes the dialog to open at the size of the "largest" tab (even if
   * the user never visits it).
   *
   * <p>This variant prefers the currently-selected tab, so the dialog can
   * pack smaller for simple pages and grow for larger ones.
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

      // base already includes tabs/header/insets; swap the "max tab" content for the selected tab content.
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
      var gc = owner != null ? owner.getGraphicsConfiguration() : fallback.getGraphicsConfiguration();
      if (gc == null) return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

      Rectangle b = gc.getBounds();
      Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
      return new Rectangle(
          b.x + in.left,
          b.y + in.top,
          b.width - in.left - in.right,
          b.height - in.top - in.bottom
      );
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
   * <p>Important: we use a Scrollable wrapper that tracks the viewport width.
   * Without this, when the dialog is resized larger and then smaller again,
   * Swing can keep the tab view at the larger width (no horizontal scrollbar),
   * making controls appear to "stick" expanded instead of shrinking.
   */
  private static JScrollPane wrapTab(JPanel panel) {
    ScrollableViewportWidthPanel wrapper = new ScrollableViewportWidthPanel(new BorderLayout());
    wrapper.add(panel, BorderLayout.NORTH);

    JScrollPane scroll = new JScrollPane(
        wrapper,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    );
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
   * A lightweight view wrapper for JScrollPane that always tracks viewport width.
   * This prevents "expanded" components from not shrinking back down when the
   * parent dialog is resized smaller.
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
  label.addMouseListener(new java.awt.event.MouseAdapter() {
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

    javax.swing.JOptionPane.showMessageDialog(parent, scroll, title, javax.swing.JOptionPane.INFORMATION_MESSAGE);
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

    JTextArea body = new JTextArea(message + "\n\n" +
        "This tab is a placeholder. Controls will move here later.");
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
    theme.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
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
    ThemeAccentSettings cur = current != null
        ? current
        : new ThemeAccentSettings(UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);

    JCheckBox enabled = new JCheckBox("Override theme accent");
    enabled.setToolTipText("If enabled, your chosen accent is blended into the current theme. Changes preview live; Apply/OK saves.");
    enabled.setSelected(cur.enabled());

    // Presets keep this easy for normal users, but we still expose a hex field for power users.
    JComboBox<AccentPreset> preset = new JComboBox<>(AccentPreset.values());
    preset.setRenderer(new DefaultListCellRenderer() {
      @Override
      public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
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
        "border: 2,8,2,8, $Component.borderColor, 1, 999; background: $Panel.background;"
    );

    Runnable updatePickIcon = () -> {
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

    Runnable updateChip = () -> {
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
        if (p == null) p = AccentPreset.fromHexOrCustom(ThemeAccentSettings.normalizeHexOrNull(raw));

        text = switch (p) {
          case IRCAFE_COBALT -> "Cobalt";
          case INDIGO -> "Indigo";
          case VIOLET -> "Violet";
          case CUSTOM -> "Custom";
          case THEME_DEFAULT -> "Theme";
        };
        tip = "Accent override: " + (chosen != null ? toHex(chosen) : "(invalid)") + " • " + strength.getValue() + "%";
      }

      chip.setText(text);
      chip.setBackground(bg);
      chip.setForeground(contrastTextColor(bg));
      chip.setToolTipText(tip);
    };

    java.util.concurrent.atomic.AtomicBoolean adjusting = new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicReference<AccentPreset> lastPreset = new java.util.concurrent.atomic.AtomicReference<>();

    Runnable syncPresetFromHex = () -> {
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

    Runnable applyEnabledState = () -> {
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

    pick.addActionListener(e -> {
      Color initial = parseHexColorLenient(hex.getText());
      Color chosen = showColorPickerDialog(SwingUtilities.getWindowAncestor(pick),
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

    clear.addActionListener(e -> {
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

    preset.addActionListener(e -> {
      if (adjusting.get()) return;
      AccentPreset p = (AccentPreset) preset.getSelectedItem();
      if (p == null) return;

      // Preserve prior preset to revert if user cancels "Custom…".
      AccentPreset prev = lastPreset.get() != null ? lastPreset.get() : AccentPreset.THEME_DEFAULT;
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
        Color chosen = showColorPickerDialog(SwingUtilities.getWindowAncestor(preset),
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
    hex.getDocument().addDocumentListener(new SimpleDocListener(() -> {
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

    return new AccentControls(enabled, preset, hex, pick, clear, strength, chip, row, applyEnabledState, syncPresetFromHex, updateChip);
  }

  private ChatThemeControls buildChatThemeControls(ChatThemeSettings current) {
    JComboBox<ChatThemeSettings.Preset> preset = new JComboBox<>(ChatThemeSettings.Preset.values());
    preset.setRenderer(new DefaultListCellRenderer() {
      @Override
      public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        ChatThemeSettings.Preset p = (value instanceof ChatThemeSettings.Preset pr) ? pr : ChatThemeSettings.Preset.DEFAULT;
        c.setText(switch (p) {
          case DEFAULT -> "Default (follow theme)";
          case SOFT -> "Soft";
          case ACCENTED -> "Accented";
          case HIGH_CONTRAST -> "High contrast";
        });
        return c;
      }
    });
    preset.setSelectedItem(current != null ? current.preset() : ChatThemeSettings.Preset.DEFAULT);

    ColorField timestamp = buildOptionalColorField(current != null ? current.timestampColor() : null, "Pick a timestamp color");
    ColorField system = buildOptionalColorField(current != null ? current.systemColor() : null, "Pick a system/status color");
    ColorField mention = buildOptionalColorField(current != null ? current.mentionBgColor() : null, "Pick a mention highlight color");

    int ms = current != null ? current.mentionStrength() : 35;
    JSlider mentionStrength = new JSlider(0, 100, Math.max(0, Math.min(100, ms)));
    // Keep this compact (no tick labels) to avoid forcing scrollbars in the Appearance tab.
    mentionStrength.setMajorTickSpacing(25);
    mentionStrength.setMinorTickSpacing(5);
    mentionStrength.setPaintTicks(false);
    mentionStrength.setPaintLabels(false);
    mentionStrength.setToolTipText("How strong the mention highlight is when using the preset highlight (0-100). Defaults to 35.");

    return new ChatThemeControls(preset, timestamp, system, mention, mentionStrength);
  }

  private ColorField buildOptionalColorField(String initialHex, String pickerTitle) {
    JTextField hex = new JTextField();
    hex.setColumns(10);
    hex.setToolTipText("Leave blank to use the preset/theme default.");

    String raw = initialHex != null ? initialHex.trim() : "";
    hex.setText(raw);

    JButton pick = new JButton("Pick");
    JButton clear = new JButton("Clear");

    Runnable updateIcon = () -> {
      Color c = parseHexColorLenient(hex.getText());
      if (c == null) {
        pick.setIcon(null);
        pick.setText("Pick");
      } else {
        pick.setText("");
        pick.setIcon(createColorSwatchIcon(c, 14, 14, preferredPreviewBackground()));
      }
    };

    pick.addActionListener(e -> {
      Color initial = parseHexColorLenient(hex.getText());
      if (initial == null) {
        initial = UIManager.getColor("Label.foreground");
      }
      Color chosen = showColorPickerDialog(SwingUtilities.getWindowAncestor(pick), pickerTitle, initial, preferredPreviewBackground());
      if (chosen != null) {
        hex.setText(toHex(chosen));
        updateIcon.run();
      }
    });

    clear.addActionListener(e -> {
      hex.setText("");
      updateIcon.run();
    });

    hex.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { updateIcon.run(); }
      @Override public void removeUpdate(DocumentEvent e) { updateIcon.run(); }
      @Override public void changedUpdate(DocumentEvent e) { updateIcon.run(); }
    });

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx", "[grow]6[]6[]"));
    panel.add(hex, "growx");
    panel.add(pick);
    panel.add(clear);

    updateIcon.run();
    return new ColorField(hex, pick, clear, panel, updateIcon);
  }




  private TweakControls buildTweakControls(ThemeTweakSettings current) {
    ThemeTweakSettings cur = current != null ? current : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.COZY, 10);

    DensityOption[] opts = new DensityOption[] {
        new DensityOption("compact", "Compact"),
        new DensityOption("cozy", "Cozy (default)"),
        new DensityOption("spacious", "Spacious")
    };

    JComboBox<DensityOption> density = new JComboBox<>(opts);
    density.setToolTipText("Changes the overall UI spacing / row height. Changes preview live; Apply/OK saves.");
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
    cornerRadius.setToolTipText("Controls rounded corner radius for buttons/fields/etc. Changes preview live; Apply/OK saves.");

    return new TweakControls(density, cornerRadius);
  }

  private FontControls buildFontControls(UiSettings current, List<AutoCloseable> closeables) {
    String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Arrays.sort(families, String.CASE_INSENSITIVE_ORDER);

    JComboBox<String> fontFamily = new JComboBox<>(families);
    fontFamily.setEditable(true);
    fontFamily.setSelectedItem(current.chatFontFamily());

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
    fontFamily.setRenderer(new ListCellRenderer<>() {
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
      public java.awt.Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
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
          if (candidate != null && (candidate.getFamily().equalsIgnoreCase(family) || candidate.getName().equalsIgnoreCase(family))) {
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

  private JCheckBox buildAutoConnectCheckbox(UiSettings current) {
    JCheckBox autoConnectOnStart = new JCheckBox("Auto-connect to servers on startup");
    autoConnectOnStart.setSelected(current.autoConnectOnStart());
    autoConnectOnStart.setToolTipText("If enabled, IRCafe will connect to all configured servers automatically after the UI loads.\n" +
        "If disabled, IRCafe starts disconnected and you can connect manually using the Connect button.");
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


  private TrayControls buildTrayControls(UiSettings current, NotificationSoundSettings soundSettings) {
    if (soundSettings == null) {
      soundSettings = new NotificationSoundSettings(true, BuiltInSound.NOTIF_1.name(), false, null);
    }
    JCheckBox enabled = new JCheckBox("Enable system tray icon", current.trayEnabled());
    JCheckBox closeToTray = new JCheckBox("Close button hides to tray instead of exiting", current.trayCloseToTray());
    JCheckBox minimizeToTray = new JCheckBox("Minimize button hides to tray", current.trayMinimizeToTray());
    JCheckBox startMinimized = new JCheckBox("Start minimized to tray", current.trayStartMinimized());

    JCheckBox notifyHighlights = new JCheckBox("Desktop notifications for highlights", current.trayNotifyHighlights());
    JCheckBox notifyPrivateMessages = new JCheckBox("Desktop notifications for private messages", current.trayNotifyPrivateMessages());
    JCheckBox notifyConnectionState = new JCheckBox("Desktop notifications for connection state", current.trayNotifyConnectionState());

    JCheckBox notifyOnlyWhenUnfocused = new JCheckBox(
        "Only notify when IRCafe is not focused",
        current.trayNotifyOnlyWhenUnfocused()
    );
    JCheckBox notifyOnlyWhenMinimizedOrHidden = new JCheckBox(
        "Only notify when minimized or hidden to tray",
        current.trayNotifyOnlyWhenMinimizedOrHidden()
    );
    JCheckBox notifySuppressWhenTargetActive = new JCheckBox(
        "Don't notify for the active buffer",
        current.trayNotifySuppressWhenTargetActive()
    );

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

    JCheckBox linuxDbusActions = new JCheckBox(
        "Use Linux D-Bus notifications (click-to-open)",
        linux && linuxActionsSupported && current.trayLinuxDbusActionsEnabled()
    );
    linuxDbusActions.setToolTipText(linux
        ? (linuxActionsSupported
            ? "Uses org.freedesktop.Notifications over D-Bus so clicking a notification can open IRCafe."
            : "Click actions aren't available in this session (no D-Bus notification actions support detected).")
        : "Linux only.");

    JButton testNotification = new JButton("Test notification");
    testNotification.setToolTipText("Send a test desktop notification (click to open IRCafe).\n" +
        "This does not require highlight/PM notifications to be enabled.");
    testNotification.addActionListener(e -> {
      try {
        if (trayNotificationService != null) {
          trayNotificationService.notifyTest();
        }
      } catch (Throwable ignored) {
      }
    });

    notifyHighlights.setToolTipText("Show a desktop notification when someone mentions your nick in a channel.");
    notifyPrivateMessages.setToolTipText("Show a desktop notification when you receive a private message.");
    notifyConnectionState.setToolTipText("Show a desktop notification when connecting/disconnecting.");

    notifyOnlyWhenUnfocused.setToolTipText("Common HexChat behavior: only notify when IRCafe isn't the active window.");
    notifyOnlyWhenMinimizedOrHidden.setToolTipText("Only notify when IRCafe is minimized or hidden to tray.");
    notifySuppressWhenTargetActive.setToolTipText("If the message is in the currently selected buffer, suppress the notification.");

    JCheckBox notificationSoundsEnabled = new JCheckBox(
        "Play sound with desktop notifications",
        soundSettings.enabled()
    );
    notificationSoundsEnabled.setToolTipText("Plays a short sound whenever IRCafe shows a desktop notification.");

    JCheckBox notificationSoundUseCustom = new JCheckBox(
        "Use custom sound file",
        soundSettings.useCustom()
    );
    notificationSoundUseCustom.setToolTipText("If enabled, IRCafe will play a custom file stored next to your runtime config.\n" +
        "Supported formats: MP3, WAV.");

    JTextField notificationSoundCustomPath = new JTextField(Objects.toString(soundSettings.customPath(), ""));
    notificationSoundCustomPath.setEditable(false);
    notificationSoundCustomPath.setToolTipText("Custom sound path (relative to the runtime config directory).\n" +
        "Click Browse... to import a file.");

    JComboBox<BuiltInSound> notificationSound = new JComboBox<>(BuiltInSound.values());
    notificationSound.setSelectedItem(BuiltInSound.fromId(soundSettings.soundId()));
    notificationSound.setToolTipText("Choose which bundled sound to use for notifications.");

    JButton browseCustomSound = new JButton("Browse...");
    browseCustomSound.setToolTipText("Choose an MP3 or WAV file and copy it into IRCafe's runtime config directory.");
    browseCustomSound.addActionListener(e -> {
      try {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose notification sound (MP3 or WAV)");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Audio files (MP3, WAV)", "mp3", "wav"));
        int result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(browseCustomSound));
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
            javax.swing.JOptionPane.ERROR_MESSAGE
        );
      }
    });

    JButton clearCustomSound = new JButton("Clear");
    clearCustomSound.setToolTipText("Stop using a custom file and revert to bundled sounds.");
    clearCustomSound.addActionListener(e -> {
      notificationSoundUseCustom.setSelected(false);
      notificationSoundCustomPath.setText("");
    });

    JButton testSound = new JButton("Test sound");
    testSound.setToolTipText("Play the selected sound.");
    testSound.addActionListener(e -> {
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

    Runnable refreshEnabled = () -> {
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
trayTab.add(enabled, "growx");
trayTab.add(closeToTray, "growx");
trayTab.add(minimizeToTray, "growx");
trayTab.add(startMinimized, "growx, wrap");
trayTab.add(helpText("Tray availability depends on your desktop environment. If tray support is unavailable, these options will have no effect."), "growx");

JPanel notificationsTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
notificationsTab.setOpaque(false);
notificationsTab.add(notifyHighlights, "growx");
notificationsTab.add(notifyPrivateMessages, "growx");
notificationsTab.add(notifyConnectionState, "growx");
notificationsTab.add(new JSeparator(), "growx, gaptop 8");
notificationsTab.add(notifyOnlyWhenUnfocused, "growx");
notificationsTab.add(notifyOnlyWhenMinimizedOrHidden, "growx");
notificationsTab.add(notifySuppressWhenTargetActive, "growx, wrap");
notificationsTab.add(testNotification, "w 180!");
notificationsTab.add(helpText("Desktop notifications are shown when your notification rules trigger (or for connection events, if enabled)."), "growx");

JPanel soundsTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
soundsTab.setOpaque(false);
soundsTab.add(notificationSoundsEnabled, "growx");
soundsTab.add(notificationSoundUseCustom, "growx, wrap");
soundsTab.add(new JLabel("Custom file:"), "split 4");
soundsTab.add(notificationSoundCustomPath, "growx, pushx, wmin 0");
soundsTab.add(browseCustomSound, "w 110!");
soundsTab.add(clearCustomSound, "w 80!, wrap");
soundsTab.add(new JLabel("Built-in:"), "split 3");
soundsTab.add(notificationSound, "w 240!");
soundsTab.add(testSound, "w 120!, wrap");

Path cfg = runtimeConfig != null ? runtimeConfig.runtimeConfigPath() : null;
Path base = cfg != null ? cfg.getParent() : null;
if (base != null) {
  soundsTab.add(helpText("Custom sounds are copied to: " + base.resolve("sounds") + "\n" +
      "Tip: Use small files (short MP3/WAV) for snappy notifications."), "growx");
}

JPanel linuxTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
linuxTab.setOpaque(false);
linuxTab.add(linuxDbusActions, "growx, wrap");
if (!linux) {
  linuxTab.add(helpText("Linux only."), "growx");
} else if (!linuxActionsSupported) {
  linuxTab.add(helpText("Linux notification actions were not detected for this session.\n" +
      "IRCafe will fall back to notify-send."), "growx");
} else {
  linuxTab.add(helpText("Uses org.freedesktop.Notifications over D-Bus so clicking a notification can open IRCafe."), "growx");
}

JTabbedPane subTabs = new JTabbedPane();
subTabs.addTab("Tray", padSubTab(trayTab));
subTabs.addTab("Desktop notifications", padSubTab(notificationsTab));
subTabs.addTab("Sounds", padSubTab(soundsTab));
subTabs.addTab("Linux / Advanced", padSubTab(linuxTab));

JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
panel.setOpaque(false);
panel.add(subTabs, "growx, wmin 0");
    return new TrayControls(enabled, closeToTray, minimizeToTray, startMinimized,
        notifyHighlights, notifyPrivateMessages, notifyConnectionState,
        notifyOnlyWhenUnfocused, notifyOnlyWhenMinimizedOrHidden, notifySuppressWhenTargetActive,
        linuxDbusActions, testNotification,
        notificationSoundsEnabled, notificationSoundUseCustom, notificationSoundCustomPath, browseCustomSound, clearCustomSound,
        notificationSound, testSound,
        panel);
  }


  private JCheckBox buildPresenceFoldsCheckbox(UiSettings current) {
    JCheckBox presenceFolds = new JCheckBox("Fold join/part/quit spam into a compact block");
    presenceFolds.setSelected(current.presenceFoldsEnabled());
    presenceFolds.setToolTipText("When enabled, runs of join/part/quit/nick-change events are folded into a single expandable block.\n" +
        "When disabled, each event is shown as its own status line.");
    return presenceFolds;
  }

  private JCheckBox buildCtcpRequestsInActiveTargetCheckbox(UiSettings current) {
    JCheckBox ctcp = new JCheckBox("Show inbound CTCP requests in the currently active chat tab");
    ctcp.setSelected(current.ctcpRequestsInActiveTargetEnabled());
    ctcp.setToolTipText("When enabled, inbound CTCP requests (e.g. VERSION, PING) are announced in the currently active chat tab.\n" +
        "When disabled, CTCP requests are routed to the target they came from (channel or PM).");
    return ctcp;
  }

  private JCheckBox buildTypingIndicatorsSendCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Send typing indicators (IRCv3)");
    cb.setSelected(current.typingIndicatorsEnabled());
    cb.setToolTipText("When enabled, IRCafe will send your IRCv3 typing state (active/paused/done) when the server supports it.");
    return cb;
  }

  private JCheckBox buildTypingIndicatorsReceiveCheckbox(UiSettings current) {
    JCheckBox cb = new JCheckBox("Display incoming typing indicators (IRCv3)");
    cb.setSelected(current.typingIndicatorsReceiveEnabled());
    cb.setToolTipText("When enabled, IRCafe will display incoming IRCv3 typing indicators from other users.");
    return cb;
  }

  private Ircv3CapabilitiesControls buildIrcv3CapabilitiesControls() {
    Map<String, Boolean> persisted = runtimeConfig.readIrcv3Capabilities();

    LinkedHashMap<String, JCheckBox> checkboxes = new LinkedHashMap<>();
    JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]8[]"));
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
      orderedCaps.sort((a, b) -> {
        int oa = capabilitySortOrder(a);
        int ob = capabilitySortOrder(b);
        if (oa != ob) return Integer.compare(oa, ob);
        return capabilityDisplayLabel(a).compareToIgnoreCase(capabilityDisplayLabel(b));
      });

      JPanel groupPanel = new JPanel(new MigLayout("insets 8, fillx, wrap 1, hidemode 3", "[grow,fill]", ""));
      groupPanel.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createTitledBorder(capabilityGroupTitle(group.getKey())),
          BorderFactory.createEmptyBorder(6, 6, 6, 6)
      ));
      groupPanel.setOpaque(false);

      for (String key : orderedCaps) {
        JCheckBox cb = new JCheckBox(capabilityDisplayLabel(key));
        cb.setSelected(persisted.getOrDefault(key, Boolean.TRUE));
        cb.setToolTipText(capabilityTooltip(key));
        checkboxes.put(key, cb);

        JButton help = whyHelpButton(capabilityHelpTitle(key), capabilityHelpMessage(key));
        help.setToolTipText("What does this capability do in IRCafe?");

        JTextArea impact = subtleInfoText();
        impact.setText(capabilityImpactSummary(key));
        impact.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

        JPanel row = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]6[]", "[]1[]"));
        row.setOpaque(false);
        row.add(cb, "growx, wmin 0");
        row.add(help, "aligny top");
        row.add(impact, "span 2, growx, wmin 0, wrap");

        groupPanel.add(row, "growx, wmin 0, wrap");
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
    enabled.setToolTipText("When enabled, IRCafe renders nicknames in deterministic colors (per nick),\n" +
        "adjusted to meet a minimum contrast ratio against the chat background.");

    JSpinner minContrast = doubleSpinner(minContrastSeed, 1.0, 21.0, 0.5, closeables);
    minContrast.setToolTipText("Minimum contrast ratio against the chat background (WCAG-style).\n" +
        "Higher values are safer for readability but may push colors toward lighter/darker extremes.");

    JButton overrides = new JButton("Edit overrides...");
    overrides.setToolTipText("Open the per-nick override editor. Overrides take precedence over the palette.");

    NickColorPreviewPanel preview = new NickColorPreviewPanel(nickColorService);

    Runnable updatePreview = () -> {
      boolean en = enabled.isSelected();
      double mc = ((Number) minContrast.getValue()).doubleValue();
      if (mc <= 0) mc = 3.0;
      minContrast.setEnabled(en);
      preview.updatePreview(en, mc);
    };

    enabled.addActionListener(e -> updatePreview.run());
    minContrast.addChangeListener(e -> updatePreview.run());

    overrides.addActionListener(e -> {
      if (nickColorOverridesDialog != null) {
        nickColorOverridesDialog.open(owner);
      }
      updatePreview.run();
    });

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]8[nogrid]", "[]6[]6[]6[]"));
    panel.setOpaque(false);
    panel.add(enabled, "span 2, wrap");
    panel.add(new JLabel("Minimum contrast ratio:"));
    panel.add(minContrast, "w 110!, wrap");
    panel.add(overrides, "span 2, alignx left, wrap");
    panel.add(helpText("Tip: If nick colors look too similar to the background, increase the contrast ratio.\n" +
        "Overrides always win over the palette."), "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Preview:"), "span 2, wrap");
    panel.add(preview, "span 2, growx");
    updatePreview.run();

    return new NickColorControls(enabled, minContrast, overrides, preview, panel);
  }

  private ImageEmbedControls buildImageEmbedControls(UiSettings current, List<AutoCloseable> closeables) {
    JCheckBox imageEmbeds = new JCheckBox("Enable inline image embeds (direct links)");
    imageEmbeds.setSelected(current.imageEmbedsEnabled());
    imageEmbeds.setToolTipText("If enabled, IRCafe will download and render images from direct image URLs in chat.");

    JCheckBox imageEmbedsCollapsed = new JCheckBox("Collapse inline images by default");
    imageEmbedsCollapsed.setSelected(current.imageEmbedsCollapsedByDefault());
    imageEmbedsCollapsed.setToolTipText("If enabled, newly inserted inline images start collapsed (header shown; click to expand).");
    imageEmbedsCollapsed.setEnabled(imageEmbeds.isSelected());
    JSpinner imageMaxWidth = numberSpinner(current.imageEmbedsMaxWidthPx(), 0, 4096, 10, closeables);
    imageMaxWidth.setToolTipText("Maximum width for inline images (pixels).\n" +
        "If 0, IRCafe will only scale images down to fit the chat viewport.");
    imageMaxWidth.setEnabled(imageEmbeds.isSelected());
    JSpinner imageMaxHeight = numberSpinner(current.imageEmbedsMaxHeightPx(), 0, 4096, 10, closeables);
    imageMaxHeight.setToolTipText("Maximum height for inline images (pixels).\n" +
        "If 0, IRCafe will only scale images down based on viewport width (and max width cap, if set).");
    imageMaxHeight.setEnabled(imageEmbeds.isSelected());

    JCheckBox animateGifs = new JCheckBox("Animate GIFs");
    animateGifs.setSelected(current.imageEmbedsAnimateGifs());
    animateGifs.setToolTipText("If disabled, animated GIFs render as a still image (first frame).");
    animateGifs.setEnabled(imageEmbeds.isSelected());

    imageEmbeds.addActionListener(e -> {
      boolean enabled = imageEmbeds.isSelected();
      imageEmbedsCollapsed.setEnabled(enabled);
      imageMaxWidth.setEnabled(enabled);
      imageMaxHeight.setEnabled(enabled);
      animateGifs.setEnabled(enabled);
    });

    JPanel imagePanel = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]8[nogrid]", "[]4[]4[]4[]4[]"));
    imagePanel.setOpaque(false);
    imagePanel.add(imageEmbeds, "span 2, wrap");
    imagePanel.add(imageEmbedsCollapsed, "span 2, wrap");
    imagePanel.add(new JLabel("Max image width (px, 0 = no limit):"));
    imagePanel.add(imageMaxWidth, "w 110!");
    imagePanel.add(new JLabel("Max image height (px, 0 = no limit):"));
    imagePanel.add(imageMaxHeight, "w 110!");
    imagePanel.add(animateGifs, "span 2, wrap");

    return new ImageEmbedControls(imageEmbeds, imageEmbedsCollapsed, imageMaxWidth, imageMaxHeight, animateGifs, imagePanel);
  }

  private LinkPreviewControls buildLinkPreviewControls(UiSettings current) {
    JCheckBox linkPreviews = new JCheckBox("Enable link previews (OpenGraph cards)");
    linkPreviews.setSelected(current.linkPreviewsEnabled());
    linkPreviews.setToolTipText("If enabled, IRCafe will fetch page metadata (title/description/image) and show a preview card under messages.\n" +
        "Note: this makes network requests to the linked sites.");

    JCheckBox linkPreviewsCollapsed = new JCheckBox("Collapse link previews by default");
    linkPreviewsCollapsed.setSelected(current.linkPreviewsCollapsedByDefault());
    linkPreviewsCollapsed.setToolTipText("If enabled, newly inserted link previews start collapsed (header shown; click to expand).");
    linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected());
    linkPreviews.addActionListener(e -> linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected()));

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
    includeChatMessages.setToolTipText("When enabled, timestamps are also shown on normal chat messages (not just status lines).");

    JCheckBox includePresenceMessages = new JCheckBox("Include presence / folded messages");
    includePresenceMessages.setSelected(current.timestampsIncludePresenceMessages());
    includePresenceMessages.setToolTipText("When enabled, timestamps are shown for join/part/quit/nick presence lines and expanded fold details.");

    Runnable syncEnabled = () -> {
      boolean on = enabled.isSelected();
      format.setEnabled(on);
      includeChatMessages.setEnabled(on);
      includePresenceMessages.setEnabled(on);
    };
    enabled.addActionListener(e -> syncEnabled.run());
    syncEnabled.run();

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]6[]6[]"));
    panel.setOpaque(false);
    panel.add(enabled);
    JPanel fmtRow = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[][grow,fill]", "[]"));
    fmtRow.setOpaque(false);
    fmtRow.add(new JLabel("Format"));
    fmtRow.add(format, "w 200!");
    panel.add(fmtRow);
    panel.add(includeChatMessages);
    panel.add(includePresenceMessages);

    return new TimestampControls(enabled, format, includeChatMessages, includePresenceMessages, panel);
  }

  private HistoryControls buildHistoryControls(UiSettings current, List<AutoCloseable> closeables) {
    JSpinner historyInitialLoadLines = numberSpinner(current.chatHistoryInitialLoadLines(), 0, 10_000, 50, closeables);
    historyInitialLoadLines.setToolTipText("How many logged lines to prefill into a transcript when you select a channel/query.\n" +
        "Set to 0 to disable history prefill.");

    JSpinner historyPageSize = numberSpinner(current.chatHistoryPageSize(), 50, 10_000, 50, closeables);
    historyPageSize.setToolTipText("How many lines to fetch per click when you use 'Load older messages…' inside the transcript.");

    JSpinner commandHistoryMaxSize = numberSpinner(current.commandHistoryMaxSize(), 1, 500, 25, closeables);
    commandHistoryMaxSize.setToolTipText("Max entries kept for Up/Down command history in the input bar.\n" +
        "This history is in-memory only; it does not persist across restarts.");

    JTextArea historyInfo = new JTextArea(
        "Chat history settings (requires chat logging to be enabled).\n" +
            "These affect how many messages are pulled from the database when opening a transcript or paging older history."
    );
    historyInfo.setEditable(false);
    historyInfo.setLineWrap(true);
    historyInfo.setWrapStyleWord(true);
    historyInfo.setOpaque(false);
    historyInfo.setFocusable(false);
    historyInfo.setBorder(null);
    historyInfo.setFont(UIManager.getFont("Label.font"));
    historyInfo.setForeground(UIManager.getColor("Label.foreground"));
    historyInfo.setColumns(48);

    JPanel historyPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    historyPanel.setOpaque(false);
    historyPanel.add(historyInfo, "span 2, growx, wmin 0, wrap");
    historyPanel.add(new JLabel("Initial load (lines):"));
    historyPanel.add(historyInitialLoadLines, "w 110!");
    historyPanel.add(new JLabel("Page size (Load older):"));
    historyPanel.add(historyPageSize, "w 110!");
    historyPanel.add(new JLabel("Input command history (max):"));
    historyPanel.add(commandHistoryMaxSize, "w 110!");

    return new HistoryControls(historyInitialLoadLines, historyPageSize, commandHistoryMaxSize, historyPanel);
  }

  private LoggingControls buildLoggingControls(LogProperties logProps, List<AutoCloseable> closeables) {
    boolean loggingEnabledCurrent = logProps != null && Boolean.TRUE.equals(logProps.enabled());
    boolean logSoftIgnoredCurrent = logProps == null || Boolean.TRUE.equals(logProps.logSoftIgnoredLines());
    boolean logPrivateMessagesCurrent = logProps == null || Boolean.TRUE.equals(logProps.logPrivateMessages());
    boolean savePrivateMessageListCurrent = logProps == null || Boolean.TRUE.equals(logProps.savePrivateMessageList());

    JCheckBox loggingEnabled = new JCheckBox("Enable chat logging (store messages to local DB)");
    loggingEnabled.setSelected(loggingEnabledCurrent);
    loggingEnabled.setToolTipText("When enabled, IRCafe will persist chat messages to an embedded local database for history loading.\n" +
        "Privacy-first: this is OFF by default.\n\n" +
        "Note: enabling/disabling requires restarting IRCafe to take effect.");

    JCheckBox loggingSoftIgnore = new JCheckBox("Log soft-ignored (spoiler) lines");
    loggingSoftIgnore.setSelected(logSoftIgnoredCurrent);
    loggingSoftIgnore.setToolTipText("If enabled, messages that are soft-ignored (spoiler-covered) are still stored,\n" +
        "and will re-load as spoiler-covered lines in history.");
    loggingSoftIgnore.setEnabled(loggingEnabled.isSelected());

    JCheckBox loggingPrivateMessages = new JCheckBox("Save private-message history");
    loggingPrivateMessages.setSelected(logPrivateMessagesCurrent);
    loggingPrivateMessages.setToolTipText("If enabled, PM/query messages are stored in the local history database.\n" +
        "If disabled, only non-PM targets are persisted.");
    loggingPrivateMessages.setEnabled(loggingEnabled.isSelected());

    JCheckBox savePrivateMessageList = new JCheckBox("Save private-message chat list");
    savePrivateMessageList.setSelected(savePrivateMessageListCurrent);
    savePrivateMessageList.setToolTipText("If enabled, PM/query targets are remembered and re-opened after reconnect/restart.\n" +
        "The per-server PM list is managed in Servers -> Edit -> Auto-Join.");

    boolean keepForeverCurrent = logProps == null || Boolean.TRUE.equals(logProps.keepForever());
    int retentionDaysCurrent = (logProps != null && logProps.retentionDays() != null) ? Math.max(0, logProps.retentionDays()) : 0;

    JCheckBox keepForever = new JCheckBox("Keep chat history forever (no retention pruning)");
    keepForever.setSelected(keepForeverCurrent);
    keepForever.setToolTipText("If enabled, IRCafe will never automatically delete old chat history.\n" +
        "If disabled, you can set a retention window in days to prune older rows.\n\n" +
        "Note: retention pruning runs only when logging is enabled and takes effect after restart.");

    JSpinner retentionDays = numberSpinner(retentionDaysCurrent, 0, 10_000, 1, closeables);
    retentionDays.setToolTipText("Retention window in days (0 disables retention).\n" +
        "Only used when Keep forever is unchecked.\n\n" +
        "Note: applied on next restart.");

    String dbBaseNameCurrent = (logProps != null && logProps.hsqldb() != null) ? logProps.hsqldb().fileBaseName() : "ircafe-chatlog";
    boolean dbNextToConfigCurrent = logProps == null || (logProps.hsqldb() != null && Boolean.TRUE.equals(logProps.hsqldb().nextToRuntimeConfig()));

    JTextField dbBaseName = new JTextField(dbBaseNameCurrent, 18);
    dbBaseName.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "ircafe-chatlog");
    dbBaseName.setToolTipText("Base filename for HSQLDB (no extension).\n" +
        "HSQLDB will create multiple files like .data/.script/.properties.");

    JCheckBox dbNextToConfig = new JCheckBox("Store DB next to runtime config file");
    dbNextToConfig.setSelected(dbNextToConfigCurrent);
    dbNextToConfig.setToolTipText("If enabled, the DB files are stored alongside your runtime YAML config (recommended).\n" +
        "If disabled, IRCafe uses the default ~/.config/ircafe directory.");

    JTextArea loggingInfo = new JTextArea(
        "Logging settings are applied on the next restart.\n" +
            "Tip: You can enable logging first, restart, then history controls (Load older messages…) will appear when data exists."
    );
    loggingInfo.setEditable(false);
    loggingInfo.setLineWrap(true);
    loggingInfo.setWrapStyleWord(true);
    loggingInfo.setOpaque(false);
    loggingInfo.setFocusable(false);
    loggingInfo.setBorder(null);
    loggingInfo.setFont(UIManager.getFont("Label.font"));
    loggingInfo.setForeground(UIManager.getColor("Label.foreground"));
    loggingInfo.setColumns(48);
    Runnable updateRetentionUi = () -> {
      retentionDays.setEnabled(!keepForever.isSelected());
    };
    keepForever.addActionListener(e -> updateRetentionUi.run());

    Runnable updateLoggingEnabledState = () -> {
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
    managePmList.addActionListener(e -> {
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
    outgoingColorEnabled.setToolTipText("If enabled, IRCafe will render lines you send (locally echoed into chat) using a custom color.");

    JTextField outgoingColorHex = new JTextField(UiSettings.normalizeHexOrDefault(current.clientLineColor(), "#6AA2FF"), 10);
    outgoingColorHex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#RRGGBB");

    JLabel outgoingPreview = new JLabel();
    outgoingPreview.setOpaque(true);
    outgoingPreview.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
    outgoingPreview.setPreferredSize(new Dimension(120, 24));

    JButton outgoingPick = new JButton("Pick...");
    outgoingPick.addActionListener(e -> {
      Color currentColor = parseHexColor(outgoingColorHex.getText());
      if (currentColor == null) currentColor = parseHexColor(current.clientLineColor());
      if (currentColor == null) currentColor = UIManager.getColor("Label.foreground");
      if (currentColor == null) currentColor = Color.WHITE;

      Color chosen = showColorPickerDialog(dialog, "Choose Outgoing Message Color", currentColor, preferredPreviewBackground());
      if (chosen != null) {
        outgoingColorHex.setText(toHex(chosen));
      }
    });

    JPanel outgoingColorPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 3", "[grow,fill]8[nogrid]8[nogrid]", "[]4[]"));
    outgoingColorPanel.setOpaque(false);
    outgoingColorPanel.add(outgoingColorEnabled, "span 3, wrap");
    outgoingColorPanel.add(outgoingColorHex, "w 110!");
    outgoingColorPanel.add(outgoingPick);
    outgoingColorPanel.add(outgoingPreview);

    Runnable updateOutgoingColorUi = () -> {
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
    outgoingColorHex.getDocument().addDocumentListener(new SimpleDocListener(updateOutgoingColorUi));
    updateOutgoingColorUi.run();

    return new OutgoingColorControls(outgoingColorEnabled, outgoingColorHex, outgoingPreview, outgoingColorPanel);
  }

  
  
  private NetworkAdvancedControls buildNetworkAdvancedControls(UiSettings current, List<AutoCloseable> closeables) {
    IrcProperties.Proxy p = NetProxyContext.settings();
    if (p == null) p = new IrcProperties.Proxy(false, "", 1080, "", "", true, 10_000, 30_000);
    // Network grew vertically as we added options. Keep it compact by splitting into sub-tabs.
    // Let the inner sub-tabs consume vertical space so this tab doesn't feel "short".
    JPanel networkPanel = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[grow,fill]"));
    JPanel userLookupsPanel = new JPanel(new MigLayout("insets 12, fillx, wrap 1, hidemode 3", "[grow,fill]", ""));

    // ---- Proxy tab ----
    JPanel proxyTab = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]"));
    proxyTab.setOpaque(false);

    JPanel proxyHeader = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    proxyHeader.setOpaque(false);
    proxyHeader.add(sectionTitle("SOCKS5 proxy"), "growx, wmin 0");
    proxyHeader.add(whyHelpButton(
        "SOCKS5 proxy",
        "When enabled, IRCafe routes IRC connections, link previews, embedded images, and file downloads through a SOCKS5 proxy.\n\n" +
            "Heads up: proxy credentials are stored in your runtime config file in plain text."
    ), "align right");
    proxyTab.add(proxyHeader, "span 2, growx, wmin 0, wrap");

    JTextArea proxyBlurb = subtleInfoText();
    proxyBlurb.setText("Routes IRC + embeds through SOCKS5. Use remote DNS if local DNS is blocked.");
    proxyTab.add(proxyBlurb, "span 2, growx, wmin 0, wrap");

    JCheckBox proxyEnabled = new JCheckBox("Use SOCKS5 proxy");
    proxyEnabled.setSelected(p.enabled());

    JTextField proxyHost = new JTextField(Objects.toString(p.host(), ""));
    proxyHost.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "127.0.0.1");

    int portDefault = (p.port() > 0 && p.port() <= 65535) ? p.port() : 1080;
    JSpinner proxyPort = numberSpinner(portDefault, 1, 65535, 1, closeables);

    JCheckBox proxyRemoteDns = new JCheckBox();
    proxyRemoteDns.setSelected(p.remoteDns());
    proxyRemoteDns.setToolTipText("When enabled, IRCafe asks the proxy to resolve hostnames. Useful if local DNS is blocked.");
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

    Runnable updateProxyEnabledState = () -> {
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

    Runnable validateProxyInputs = () -> {
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

    proxyEnabled.addActionListener(e -> {
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
    tlsHeader.add(whyHelpButton(
        "TLS / SSL (Trust all certificates)",
        "This setting is intentionally dangerous. If enabled, IRCafe will accept any TLS certificate (expired, mismatched, self-signed, etc)\n" +
            "for IRC-over-TLS connections and for HTTPS fetching (link previews, embedded images, etc).\n\n" +
            "Only enable this if you understand the risk (MITM becomes trivial)."
    ), "align right");
    tlsTab.add(tlsHeader, "growx, wmin 0, wrap");

    JTextArea tlsBlurb = subtleInfoText();
    tlsBlurb.setText("If enabled, certificate validation is skipped (insecure). Only use for debugging.");
    tlsTab.add(tlsBlurb, "growx, wmin 0, wrap");

    JCheckBox trustAllTlsCertificates = new JCheckBox();
    trustAllTlsCertificates.setSelected(NetTlsContext.trustAllCertificates());
    JComponent trustAllTlsRow = wrapCheckBox(trustAllTlsCertificates, "Trust all TLS/SSL certificates (insecure)");
    tlsTab.add(trustAllTlsRow, "growx, wmin 0, wrap");

    // ---- Heartbeat tab ----
    JPanel heartbeatTab = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]"));
    heartbeatTab.setOpaque(false);
    JPanel heartbeatHeader = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    heartbeatHeader.setOpaque(false);
    heartbeatHeader.add(sectionTitle("Connection heartbeat"), "growx, wmin 0");
    heartbeatHeader.add(whyHelpButton(
        "Connection heartbeat",
        "IRCafe can detect 'silent' disconnects by monitoring inbound traffic.\n" +
            "If no IRC messages are received for the configured timeout, IRCafe will close the socket\n" +
            "and let the reconnect logic take over (if enabled).\n\n" +
            "Tip: If your network is very quiet, increase the timeout."
    ), "align right");
    heartbeatTab.add(heartbeatHeader, "span 2, growx, wmin 0, wrap");

    JTextArea heartbeatBlurb = subtleInfoText();
    heartbeatBlurb.setText("Detects silent disconnects by closing idle sockets so reconnect can kick in.");
    heartbeatTab.add(heartbeatBlurb, "span 2, growx, wmin 0, wrap");

    IrcProperties.Heartbeat hb = NetHeartbeatContext.settings();
    if (hb == null) hb = new IrcProperties.Heartbeat(true, 15_000, 360_000);

    JCheckBox heartbeatEnabled = new JCheckBox();
    heartbeatEnabled.setSelected(hb.enabled());
    JComponent heartbeatEnabledRow = wrapCheckBox(heartbeatEnabled, "Enable heartbeat / idle timeout detection");

    int hbCheckSec = (int) Math.max(1, hb.checkPeriodMs() / 1000L);
    int hbTimeoutSec = (int) Math.max(1, hb.timeoutMs() / 1000L);
    JSpinner heartbeatCheckPeriodSeconds = numberSpinner(hbCheckSec, 1, 600, 1, closeables);
    JSpinner heartbeatTimeoutSeconds = numberSpinner(hbTimeoutSec, 5, 7200, 5, closeables);

    Runnable updateHeartbeatEnabledState = () -> {
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

    JPanel networkIntro = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[grow,fill]6[]", "[]"));
    networkIntro.setOpaque(false);
    networkIntro.add(tabTitle("Network"), "growx, wmin 0");
    networkIntro.add(whyHelpButton(
        "Network settings",
        "These settings affect how IRCafe connects to networks and fetches external content (link previews, embedded images, etc).\n\n" +
            "Tip: Most users only touch Proxy. Leave TLS trust-all off unless you're debugging."
    ), "align right");

    networkPanel.add(networkIntro, "growx, wmin 0, wrap");
    networkPanel.add(networkTabs, "grow, push, wmin 0");

    ProxyControls proxyControls = new ProxyControls(
        proxyEnabled,
        proxyHost,
        proxyPort,
        proxyRemoteDns,
        proxyUsername,
        proxyPassword,
        clearPassword,
        connectTimeoutSeconds,
        readTimeoutSeconds
    );

    HeartbeatControls heartbeatControls = new HeartbeatControls(
        heartbeatEnabled,
        heartbeatCheckPeriodSeconds,
        heartbeatTimeoutSeconds
    );
    userLookupsPanel.add(tabTitle("User lookups"), "growx, wrap");

    JPanel userLookupsIntro = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    userLookupsIntro.setOpaque(false);
    JTextArea userLookupsBlurb = helpText(
        "Optional fallbacks for account/away/host info (USERHOST / WHOIS), with conservative rate limits."
    );
    JButton userLookupsHelp = whyHelpButton(
        "Why do I need user lookups?",
        "Most modern IRC networks provide account and presence information via IRCv3 (e.g., account-tag, account-notify, away-notify, extended-join).\n\n" +
            "However, some networks (or some pieces of data) still require fallback lookups. IRCafe can optionally use USERHOST and (as a last resort) WHOIS to fill missing metadata.\n\n" +
            "If you're on an IRCv3-capable network and don't use hostmask-based ignore rules, you can usually leave these disabled."
    );
    userLookupsIntro.add(userLookupsBlurb, "growx, wmin 0");
    userLookupsIntro.add(userLookupsHelp, "align right");
    JPanel lookupPresetPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]"));
    lookupPresetPanel.setOpaque(false);

    JComboBox<LookupRatePreset> lookupPreset = new JComboBox<>(LookupRatePreset.values());
    lookupPreset.setSelectedItem(detectLookupRatePreset(current));

    JTextArea lookupPresetHint = subtleInfoText();

    Runnable updateLookupPresetHint = () -> {
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
    JPanel hostmaskPanel = new JPanel(new MigLayout("insets 8, fillx, wrap 2, hidemode 3", "[right]12[grow,fill]", ""));
    hostmaskPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createTitledBorder("Hostmask discovery"),
        BorderFactory.createEmptyBorder(6, 6, 6, 6)
    ));
    hostmaskPanel.setOpaque(false);

    JCheckBox userhostEnabled = new JCheckBox("Fill missing hostmasks using USERHOST (rate-limited)");
    userhostEnabled.setSelected(current.userhostDiscoveryEnabled());
    userhostEnabled.setToolTipText("When enabled, IRCafe may send USERHOST only when hostmask-based ignore rules exist and some nicks are missing hostmasks.");

    JButton hostmaskHelp = whyHelpButton(
        "Why do I need hostmask discovery?",
        "Some ignore rules rely on hostmasks (nick!user@host).\n\n" +
            "On many networks, the full hostmask isn't included in NAMES and might not be available until additional lookups happen.\n\n" +
            "If you use hostmask-based ignore rules and some users show up without hostmasks, IRCafe can send rate-limited USERHOST commands to fill them in.\n\n" +
            "If you don't use hostmask-based ignores, you can usually leave this off."
    );

    JTextArea hostmaskSummary = subtleInfoText();
    hostmaskSummary.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

    JSpinner userhostMinIntervalSeconds = numberSpinner(current.userhostMinIntervalSeconds(), 1, 60, 1, closeables);
    userhostMinIntervalSeconds.setToolTipText("Minimum seconds between USERHOST commands per server.");

    JSpinner userhostMaxPerMinute = numberSpinner(current.userhostMaxCommandsPerMinute(), 1, 60, 1, closeables);
    userhostMaxPerMinute.setToolTipText("Maximum USERHOST commands per minute per server.");

    JSpinner userhostNickCooldownMinutes = numberSpinner(current.userhostNickCooldownMinutes(), 1, 240, 1, closeables);
    userhostNickCooldownMinutes.setToolTipText("Cooldown in minutes before re-querying the same nick.");

    JSpinner userhostMaxNicksPerCommand = numberSpinner(current.userhostMaxNicksPerCommand(), 1, 5, 1, closeables);
    userhostMaxNicksPerCommand.setToolTipText("How many nicks to include per USERHOST command (servers typically allow up to 5).");

    JPanel hostmaskAdvanced = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    hostmaskAdvanced.setOpaque(false);
    hostmaskAdvanced.add(new JLabel("Min interval (sec):"));
    hostmaskAdvanced.add(userhostMinIntervalSeconds, "w 110!");
    hostmaskAdvanced.add(new JLabel("Max commands/min:"));
    hostmaskAdvanced.add(userhostMaxPerMinute, "w 110!");
    hostmaskAdvanced.add(new JLabel("Nick cooldown (min):"));
    hostmaskAdvanced.add(userhostNickCooldownMinutes, "w 110!");
    hostmaskAdvanced.add(new JLabel("Max nicks/command:"));
    hostmaskAdvanced.add(userhostMaxNicksPerCommand, "w 110!");
    JPanel enrichmentPanel = new JPanel(new MigLayout("insets 8, fillx, wrap 2, hidemode 3", "[right]12[grow,fill]", ""));
    enrichmentPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createTitledBorder("Roster enrichment (fallback)"),
        BorderFactory.createEmptyBorder(6, 6, 6, 6)
    ));
    enrichmentPanel.setOpaque(false);

    JCheckBox enrichmentEnabled = new JCheckBox("Best-effort roster enrichment using USERHOST (rate-limited)");
    enrichmentEnabled.setSelected(current.userInfoEnrichmentEnabled());
    enrichmentEnabled.setToolTipText("When enabled, IRCafe may send USERHOST occasionally to enrich user info even when you don't have hostmask-based ignore rules.\n" +
        "This is a best-effort fallback for older networks.");

    JCheckBox enrichmentWhoisFallbackEnabled = new JCheckBox("Also use WHOIS fallback for account info (very slow)");
    enrichmentWhoisFallbackEnabled.setSelected(current.userInfoEnrichmentWhoisFallbackEnabled());
    enrichmentWhoisFallbackEnabled.setToolTipText("When enabled, IRCafe may occasionally send WHOIS to learn account login state/name and away message.\n" +
        "This is slower and more likely to hit server rate limits. Recommended OFF by default.");

    JCheckBox enrichmentPeriodicRefreshEnabled = new JCheckBox("Periodic background refresh (slow scan)");
    enrichmentPeriodicRefreshEnabled.setSelected(current.userInfoEnrichmentPeriodicRefreshEnabled());
    enrichmentPeriodicRefreshEnabled.setToolTipText("When enabled, IRCafe will periodically re-check a small number of nicks to detect changes.\n" +
        "Use conservative intervals to avoid extra network load.");

    JButton enrichmentHelp = whyHelpButton(
        "Why do I need roster enrichment?",
        "This is a best-effort fallback for older networks or edge cases where IRCv3 metadata isn't available.\n\n" +
            "IRCafe can use rate-limited USERHOST to fill missing user info. Optionally it can also use WHOIS (much slower) to learn account/away details.\n\n" +
            "On modern IRCv3 networks, you typically don't need this. Leave it OFF unless you have a specific reason."
    );

    JButton whoisHelp = whyHelpButton(
        "WHOIS fallback",
        "WHOIS is the slowest and noisiest fallback. It can provide account and away information when IRCv3 isn't available, but it is easy to hit server throttles.\n\n" +
            "Keep this OFF unless you're on a network that doesn't provide account info via IRCv3."
    );

    JButton refreshHelp = whyHelpButton(
        "Periodic background refresh",
        "This periodically re-probes a small number of users to detect changes (e.g., account/away state) on networks that don't push updates.\n\n" +
            "It's a slow scan by design: use high intervals and small batch sizes to avoid extra network load."
    );

    JTextArea enrichmentSummary = subtleInfoText();
    enrichmentSummary.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

    JSpinner enrichmentUserhostMinIntervalSeconds = numberSpinner(current.userInfoEnrichmentUserhostMinIntervalSeconds(), 1, 300, 1, closeables);
    enrichmentUserhostMinIntervalSeconds.setToolTipText("Minimum seconds between USERHOST commands per server for enrichment.");

    JSpinner enrichmentUserhostMaxPerMinute = numberSpinner(current.userInfoEnrichmentUserhostMaxCommandsPerMinute(), 1, 60, 1, closeables);
    enrichmentUserhostMaxPerMinute.setToolTipText("Maximum USERHOST commands per minute per server for enrichment.");

    JSpinner enrichmentUserhostNickCooldownMinutes = numberSpinner(current.userInfoEnrichmentUserhostNickCooldownMinutes(), 1, 1440, 1, closeables);
    enrichmentUserhostNickCooldownMinutes.setToolTipText("Cooldown in minutes before re-querying the same nick via USERHOST (enrichment).\n" +
        "Higher values reduce network load.");

    JSpinner enrichmentUserhostMaxNicksPerCommand = numberSpinner(current.userInfoEnrichmentUserhostMaxNicksPerCommand(), 1, 5, 1, closeables);
    enrichmentUserhostMaxNicksPerCommand.setToolTipText("How many nicks to include per USERHOST command (servers typically allow up to 5).\n" +
        "This applies to enrichment mode, separate from hostmask discovery.");

    JSpinner enrichmentWhoisMinIntervalSeconds = numberSpinner(current.userInfoEnrichmentWhoisMinIntervalSeconds(), 5, 600, 5, closeables);
    enrichmentWhoisMinIntervalSeconds.setToolTipText("Minimum seconds between WHOIS commands per server (enrichment).\n" +
        "Keep this high to avoid throttling.");

    JSpinner enrichmentWhoisNickCooldownMinutes = numberSpinner(current.userInfoEnrichmentWhoisNickCooldownMinutes(), 1, 1440, 1, closeables);
    enrichmentWhoisNickCooldownMinutes.setToolTipText("Cooldown in minutes before re-WHOIS'ing the same nick.");

    JSpinner enrichmentPeriodicRefreshIntervalSeconds = numberSpinner(current.userInfoEnrichmentPeriodicRefreshIntervalSeconds(), 30, 3600, 30, closeables);
    enrichmentPeriodicRefreshIntervalSeconds.setToolTipText("How often to run a slow scan tick (seconds).\n" +
        "Higher values are safer. Example: 300 seconds (5 minutes).");

    JSpinner enrichmentPeriodicRefreshNicksPerTick = numberSpinner(current.userInfoEnrichmentPeriodicRefreshNicksPerTick(), 1, 20, 1, closeables);
    enrichmentPeriodicRefreshNicksPerTick.setToolTipText("How many nicks to probe per periodic tick.\n" +
        "Keep this small (e.g., 1-3).");

    JPanel enrichmentAdvanced = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]10[]6[]6[]10[]6[]6[]"));
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
    Consumer<LookupRatePreset> applyLookupPreset = preset -> {
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
        default -> { /* no-op */ }
      }
    };
    Runnable updateHostmaskSummary = () -> {
      if (!userhostEnabled.isSelected()) {
        hostmaskSummary.setText("Disabled");
        return;
      }
      int minI = ((Number) userhostMinIntervalSeconds.getValue()).intValue();
      int maxM = ((Number) userhostMaxPerMinute.getValue()).intValue();
      int cdM = ((Number) userhostNickCooldownMinutes.getValue()).intValue();
      int maxN = ((Number) userhostMaxNicksPerCommand.getValue()).intValue();
      hostmaskSummary.setText(String.format("USERHOST ≤%d/min • min %ds • cooldown %dm • up to %d nicks/cmd", maxM, minI, cdM, maxN));
    };

    Runnable updateEnrichmentSummary = () -> {
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
        int interval = ((Number) enrichmentPeriodicRefreshIntervalSeconds.getValue()).intValue();
        int nicks = ((Number) enrichmentPeriodicRefreshNicksPerTick.getValue()).intValue();
        refresh = String.format("Refresh %ds ×%d", interval, nicks);
      } else {
        refresh = "Refresh off";
      }

      enrichmentSummary.setText(
          String.format("USERHOST ≤%d/min • min %ds • cooldown %dm • up to %d nicks/cmd\n%s • %s",
              maxM, minI, cdM, maxN, whois, refresh)
      );
    };

    Runnable updateAllSummaries = () -> {
      updateHostmaskSummary.run();
      updateEnrichmentSummary.run();
    };

    Runnable updateHostmaskState = () -> {
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

    Runnable updateEnrichmentState = () -> {
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
    userhostEnabled.addActionListener(e -> {
      updateHostmaskState.run();
      updateAllSummaries.run();
      hostmaskPanel.revalidate();
      hostmaskPanel.repaint();
      userLookupsPanel.revalidate();
      userLookupsPanel.repaint();
    });

    enrichmentEnabled.addActionListener(e -> {
      updateEnrichmentState.run();
      updateAllSummaries.run();
      enrichmentPanel.revalidate();
      enrichmentPanel.repaint();
      userLookupsPanel.revalidate();
      userLookupsPanel.repaint();
    });

    enrichmentWhoisFallbackEnabled.addActionListener(e -> {
      updateEnrichmentState.run();
      updateAllSummaries.run();
    });
    enrichmentPeriodicRefreshEnabled.addActionListener(e -> {
      updateEnrichmentState.run();
      updateAllSummaries.run();
    });

    lookupPreset.addActionListener(e -> {
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
    JPanel enrichmentWhoisRow = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    enrichmentWhoisRow.setOpaque(false);
    enrichmentWhoisRow.add(enrichmentWhoisFallbackEnabled, "growx");
    enrichmentWhoisRow.add(whoisHelp, "align right");

    JPanel enrichmentRefreshRow = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
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
    JPanel lookupsOverview = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]10[]"));
    lookupsOverview.setOpaque(false);
    lookupsOverview.add(userLookupsIntro, "growx, wmin 0, wrap");
    lookupsOverview.add(lookupPresetPanel, "growx, wmin 0, wrap");

    lookupsTabs.addTab("Overview", padSubTab(lookupsOverview));
    lookupsTabs.addTab("Hostmask discovery", padSubTab(hostmaskPanel));
    lookupsTabs.addTab("Roster enrichment", padSubTab(enrichmentPanel));

    userLookupsPanel.add(lookupsTabs, "growx, wmin 0, wrap");

    UserhostControls userhostControls = new UserhostControls(
        userhostEnabled,
        userhostMinIntervalSeconds,
        userhostMaxPerMinute,
        userhostNickCooldownMinutes,
        userhostMaxNicksPerCommand
    );

    UserInfoEnrichmentControls enrichmentControls = new UserInfoEnrichmentControls(
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
        enrichmentPeriodicRefreshNicksPerTick
    );

    return new NetworkAdvancedControls(proxyControls, userhostControls, enrichmentControls, heartbeatControls, trustAllTlsCertificates, networkPanel, userLookupsPanel);
  }

  private JPanel buildAppearancePanel(ThemeControls theme, AccentControls accent, ChatThemeControls chatTheme, FontControls fonts, TweakControls tweaks) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]6[]10[]6[]6[]"));

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

    form.add(new JLabel("Chat theme preset"));
    form.add(chatTheme.preset, "growx");

    form.add(new JLabel("Chat timestamp color"));
    form.add(chatTheme.timestamp.panel, "growx");

    form.add(new JLabel("Chat system color"));
    form.add(chatTheme.system.panel, "growx");

    form.add(new JLabel("Mention highlight"));
    form.add(chatTheme.mention.panel, "growx");

    form.add(new JLabel("Mention strength"));
    form.add(chatTheme.mentionStrength, "growx");

    form.add(new JLabel("Density"));
    form.add(tweaks.density, "growx");

    form.add(new JLabel("Corner radius"));
    form.add(tweaks.cornerRadius, "growx");

    JButton reset = new JButton("Reset to defaults");
    reset.setToolTipText("Revert the appearance controls to default values. Changes preview live; Apply/OK saves.");
    reset.addActionListener(e -> {
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
        if (o != null && "cozy".equalsIgnoreCase(o.id())) {
          tweaks.density.setSelectedIndex(i);
          break;
        }
      }
      tweaks.cornerRadius.setValue(10);

      // Chat theme
      chatTheme.preset.setSelectedItem(ChatThemeSettings.Preset.DEFAULT);
      chatTheme.timestamp.hex.setText("");
      chatTheme.system.hex.setText("");
      chatTheme.mention.hex.setText("");
      chatTheme.mentionStrength.setValue(35);
      chatTheme.timestamp.updateIcon.run();
      chatTheme.system.updateIcon.run();
      chatTheme.mention.updateIcon.run();

      accent.applyEnabledState.run();
      accent.syncPresetFromHex.run();
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

  private JPanel buildStartupPanel(JCheckBox autoConnectOnStart) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]10[]6[]"));
    form.add(tabTitle("Startup"), "growx, wrap");

    form.add(sectionTitle("On launch"), "growx, wrap");
    form.add(autoConnectOnStart, "growx, wrap");
    form.add(helpText("If enabled, IRCafe will connect to all configured servers automatically after the UI loads."), "growx, wrap");

    return form;
  }

  private JPanel buildTrayNotificationsPanel(TrayControls trayControls) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]10[]"));
    form.add(tabTitle("Tray & Notifications"), "growx, wrap");
    form.add(trayControls.panel, "growx");
    return form;
  }


  private JPanel buildChatPanel(JCheckBox presenceFolds,
                               JCheckBox ctcpRequestsInActiveTarget,
                               NickColorControls nickColors,
                               TimestampControls timestamps,
                               OutgoingColorControls outgoing) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]"));

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

  private JPanel buildIrcv3CapabilitiesPanel(
      JCheckBox typingIndicatorsSendEnabled,
      JCheckBox typingIndicatorsReceiveEnabled,
      Ircv3CapabilitiesControls ircv3Capabilities) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]10[]6[]10[]6[]"));

    form.add(tabTitle("IRCv3"), "growx, wmin 0, wrap");

    form.add(sectionTitle("Feature toggles"), "growx, wmin 0, wrap");
    JButton typingHelp = whyHelpButton(
        "Typing indicators",
        "What it is:\n" +
            "Typing indicators show when someone is actively typing or has paused.\n\n" +
            "Impact in IRCafe:\n" +
            "- Send: broadcasts your typing state to peers when supported.\n" +
            "- Display: shows incoming typing state in the active UI.\n\n" +
            "If disabled:\n" +
            "- Send disabled: IRCafe won't broadcast your typing state.\n" +
            "- Display disabled: IRCafe won't render incoming typing indicators."
    );
    typingHelp.setToolTipText("How typing indicators affect IRCafe");
    JPanel typingRow = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]6[]", "[]2[]"));
    typingRow.setOpaque(false);
    typingRow.add(typingIndicatorsSendEnabled, "growx, wmin 0, split 2");
    typingRow.add(typingHelp, "aligny top");
    typingRow.add(typingIndicatorsReceiveEnabled, "growx, wmin 0");
    form.add(typingRow, "growx, wmin 0, wrap");
    JTextArea typingImpact = subtleInfoText();
    typingImpact.setText("Choose whether IRCafe sends your typing state, displays incoming typing state, or both.");
    typingImpact.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
    form.add(typingImpact, "growx, wmin 0, wrap");

    form.add(sectionTitle("Requested capabilities"), "growx, wmin 0, wrap");
    form.add(helpText("These options control which IRCv3 capabilities IRCafe requests during CAP negotiation.\nChanges apply on new connections or reconnect."), "growx, wmin 0, wrap");
    form.add(ircv3Capabilities.panel(), "growx, wmin 0");

    return form;
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
      case "server-time" -> "Server timestamps";
      case "echo-message" -> "Echo own messages";
      case "account-tag" -> "Account tags";
      case "userhost-in-names" -> "USERHOST in NAMES";
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
      case "multi-prefix", "cap-notify", "away-notify", "account-notify", "extended-join",
           "setname", "chghost", "message-tags", "server-time", "standard-replies",
           "echo-message", "labeled-response", "account-tag", "userhost-in-names"
          -> "core";
      case "draft/reply", "draft/react", "draft/message-edit", "message-edit",
           "draft/message-redaction", "message-redaction", "typing", "read-marker"
          -> "conversation";
      case "batch", "chathistory", "draft/chathistory", "znc.in/playback"
          -> "history";
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
      case "server-time" -> 20;
      case "echo-message" -> 30;
      case "labeled-response" -> 40;
      case "standard-replies" -> 50;
      case "account-tag" -> 60;
      case "account-notify" -> 70;
      case "away-notify" -> 80;
      case "extended-join" -> 90;
      case "chghost" -> 100;
      case "setname" -> 110;
      case "multi-prefix" -> 120;
      case "cap-notify" -> 130;
      case "userhost-in-names" -> 140;

      // Conversation features
      case "typing" -> 210;
      case "read-marker" -> 220;
      case "draft/reply" -> 230;
      case "draft/react" -> 240;
      case "message-edit" -> 250;
      case "draft/message-edit" -> 260;
      case "message-redaction" -> 270;
      case "draft/message-redaction" -> 280;

      // History and playback
      case "batch" -> 310;
      case "chathistory" -> 320;
      case "draft/chathistory" -> 330;
      case "znc.in/playback" -> 340;

      default -> 10_000;
    };
  }

  private static String capabilityImpactSummary(String capability) {
    return switch (capability) {
      case "message-tags" -> "Foundation for many IRCv3 features: carries structured metadata on messages.";
      case "server-time" -> "Uses server-provided timestamps to improve ordering and replay accuracy.";
      case "echo-message" -> "Server echoes your outbound messages, improving multi-client/bouncer consistency.";
      case "account-tag" -> "Attaches account metadata to messages for richer identity info.";
      case "userhost-in-names" -> "May provide richer host/user identity details during names lists.";
      case "typing" -> "Transport for typing indicators; required to send/receive typing events.";
      case "read-marker" -> "Enables read-position markers on servers that support them.";
      case "draft/reply" -> "Carries reply context so quoted/reply relationships can be preserved.";
      case "draft/react" -> "Carries reaction metadata where servers/clients support it.";
      case "draft/message-edit", "message-edit" -> "Allows edit updates for previously sent messages.";
      case "draft/message-redaction", "message-redaction" -> "Allows delete/redaction updates for messages.";
      case "chathistory", "draft/chathistory" -> "Enables server-side history retrieval and backfill features.";
      case "znc.in/playback" -> "Requests playback support from ZNC bouncers when available.";
      case "labeled-response" -> "Correlates command responses with requests more reliably.";
      case "standard-replies" -> "Provides structured success/error replies from the server.";
      case "multi-prefix" -> "Preserves all nick privilege prefixes (not just the highest) in user data.";
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
        + "Requests IRCv3 capability \"" + capability + "\" during CAP negotiation.\n\n"
        + "Impact in IRCafe:\n"
        + capabilityImpactSummary(capability) + "\n\n"
        + "If disabled:\n"
        + "IRCafe will not request this capability on new connections; related features may be unavailable.";
  }

  private static String normalizeIrcv3CapabilityKey(String capability) {
    if (capability == null) return "";
    String k = capability.trim().toLowerCase(Locale.ROOT);
    return k;
  }

  private JPanel buildEmbedsAndPreviewsPanel(ImageEmbedControls image,
                                            LinkPreviewControls links) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]"));

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
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]6[]6[]6[]10[]6[]"));

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

  private NotificationRulesControls buildNotificationRulesControls(UiSettings current, List<AutoCloseable> closeables) {
    int cooldown = current != null ? current.notificationRuleCooldownSeconds() : 15;
    JSpinner cooldownSeconds = numberSpinner(cooldown, 0, 3600, 1, closeables);

    NotificationRulesTableModel model = new NotificationRulesTableModel(current != null ? current.notificationRules() : List.of());
    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(Math.max(22, table.getRowHeight()));

    // Editors
    JComboBox<NotificationRule.Type> typeCombo = new JComboBox<>(NotificationRule.Type.values());
    table.getColumnModel().getColumn(NotificationRulesTableModel.COL_TYPE).setCellEditor(new DefaultCellEditor(typeCombo));

    // Column sizing
    TableColumn enabledCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_ENABLED);
    enabledCol.setMaxWidth(80);
    enabledCol.setPreferredWidth(70);

    TableColumn typeCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_TYPE);
    typeCol.setMaxWidth(110);
    typeCol.setPreferredWidth(90);

    TableColumn colorCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_COLOR);
    colorCol.setMaxWidth(130);
    colorCol.setPreferredWidth(110);
    colorCol.setCellRenderer(new RuleColorCellRenderer());

    TableColumn caseCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_CASE);
    caseCol.setMaxWidth(90);
    caseCol.setPreferredWidth(80);

    TableColumn wholeCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_WHOLE);
    wholeCol.setMaxWidth(110);
    wholeCol.setPreferredWidth(90);

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

    return new NotificationRulesControls(cooldownSeconds, table, model, validationLabel, testInput, testOutput, testStatus, testRunner);
  }

  private IrcEventNotificationControls buildIrcEventNotificationControls(List<IrcEventNotificationRule> initialRules) {
    IrcEventNotificationTableModel model = new IrcEventNotificationTableModel(initialRules);
    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(Math.max(22, table.getRowHeight()));

    JComboBox<IrcEventNotificationRule.EventType> eventCombo = new JComboBox<>(IrcEventNotificationRule.EventType.values());
    table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_EVENT).setCellEditor(new DefaultCellEditor(eventCombo));

    JComboBox<IrcEventNotificationRule.SourceFilter> sourceCombo = new JComboBox<>(IrcEventNotificationRule.SourceFilter.values());
    table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_SOURCE).setCellEditor(new DefaultCellEditor(sourceCombo));

    TableColumn enabledCol = table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_ENABLED);
    enabledCol.setMaxWidth(80);
    enabledCol.setPreferredWidth(70);

    TableColumn eventCol = table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_EVENT);
    eventCol.setPreferredWidth(140);

    TableColumn sourceCol = table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_SOURCE);
    sourceCol.setPreferredWidth(110);

    TableColumn toastCol = table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_TOAST);
    toastCol.setMaxWidth(80);
    toastCol.setPreferredWidth(70);

    TableColumn soundCol = table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_SOUND);
    soundCol.setMaxWidth(80);
    soundCol.setPreferredWidth(70);

    TableColumn includeCol = table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_CHANNEL_WHITELIST);
    includeCol.setPreferredWidth(150);
    TableColumn excludeCol = table.getColumnModel().getColumn(IrcEventNotificationTableModel.COL_CHANNEL_BLACKLIST);
    excludeCol.setPreferredWidth(150);

    JComboBox<BuiltInSound> sound = new JComboBox<>(BuiltInSound.values());
    JCheckBox useCustom = new JCheckBox("Use custom file");
    JTextField customPath = new JTextField();
    customPath.setEditable(false);
    JButton browseCustom = new JButton("Browse...");
    JButton clearCustom = new JButton("Clear");
    JButton testSound = new JButton("Test sound");
    JLabel selectionHint = new JLabel("Select a rule row to edit sound settings.");

    final boolean[] syncing = new boolean[] { false };

    Runnable loadSelectedSound = () -> {
      int viewRow = table.getSelectedRow();
      int row = viewRow >= 0 ? table.convertRowIndexToModel(viewRow) : -1;

      syncing[0] = true;
      if (row < 0) {
        sound.setSelectedItem(BuiltInSound.NOTIF_1);
        useCustom.setSelected(false);
        customPath.setText("");
      } else {
        sound.setSelectedItem(BuiltInSound.fromId(model.soundIdAt(row)));
        useCustom.setSelected(model.soundUseCustomAt(row));
        customPath.setText(Objects.toString(model.soundCustomPathAt(row), ""));
      }
      syncing[0] = false;

      boolean selected = row >= 0;
      browseCustom.setEnabled(selected);
      sound.setEnabled(selected);
      useCustom.setEnabled(selected);
      testSound.setEnabled(selected);

      String rel = customPath.getText() != null ? customPath.getText().trim() : "";
      clearCustom.setEnabled(selected && !rel.isBlank());

      if (!selected) {
        selectionHint.setText("Select a rule row to edit sound settings.");
      } else {
        selectionHint.setText("Sound settings apply to the selected rule.");
      }
    };

    Runnable persistSelectedSound = () -> {
      if (syncing[0]) return;
      int viewRow = table.getSelectedRow();
      int row = viewRow >= 0 ? table.convertRowIndexToModel(viewRow) : -1;
      if (row < 0) return;

      BuiltInSound selectedSound = (BuiltInSound) sound.getSelectedItem();
      String soundId = selectedSound != null ? selectedSound.name() : BuiltInSound.NOTIF_1.name();
      boolean custom = useCustom.isSelected();
      String rel = customPath.getText() != null ? customPath.getText().trim() : "";
      if (rel.isBlank()) rel = null;
      model.setSoundConfig(row, soundId, custom, rel);

      clearCustom.setEnabled(rel != null);
    };

    table.getSelectionModel().addListSelectionListener(e -> {
      if (e != null && e.getValueIsAdjusting()) return;
      loadSelectedSound.run();
    });

    model.addTableModelListener(e -> loadSelectedSound.run());

    sound.addActionListener(e -> persistSelectedSound.run());
    useCustom.addActionListener(e -> persistSelectedSound.run());

    browseCustom.addActionListener(e -> {
      int viewRow = table.getSelectedRow();
      int row = viewRow >= 0 ? table.convertRowIndexToModel(viewRow) : -1;
      if (row < 0) return;
      try {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose notification sound (MP3 or WAV)");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Audio files (MP3, WAV)", "mp3", "wav"));
        int result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(browseCustom));
        if (result != JFileChooser.APPROVE_OPTION) return;

        File f = chooser.getSelectedFile();
        if (f == null) return;
        String rel = importNotificationSoundFileToRuntimeDir(f);
        if (rel == null || rel.isBlank()) return;

        customPath.setText(rel);
        useCustom.setSelected(true);
        persistSelectedSound.run();
        loadSelectedSound.run();
      } catch (Exception ex) {
        JOptionPane.showMessageDialog(
            SwingUtilities.getWindowAncestor(browseCustom),
            "Could not import sound file.\n\n" + ex.getMessage(),
            "Import failed",
            JOptionPane.ERROR_MESSAGE
        );
      }
    });

    clearCustom.addActionListener(e -> {
      customPath.setText("");
      useCustom.setSelected(false);
      persistSelectedSound.run();
      loadSelectedSound.run();
    });

    testSound.addActionListener(e -> {
      int viewRow = table.getSelectedRow();
      int row = viewRow >= 0 ? table.convertRowIndexToModel(viewRow) : -1;
      if (row < 0 || notificationSoundService == null) return;
      try {
        String rel = model.soundCustomPathAt(row);
        if (model.soundUseCustomAt(row) && rel != null && !rel.isBlank()) {
          notificationSoundService.previewCustom(rel);
          return;
        }
        notificationSoundService.preview(BuiltInSound.fromId(model.soundIdAt(row)));
      } catch (Exception ignored) {
      }
    });

    loadSelectedSound.run();
    return new IrcEventNotificationControls(table, model, sound, useCustom, customPath, browseCustom, clearCustom, testSound, selectionHint);
  }

  private JPanel buildIrcEventNotificationsTab(IrcEventNotificationControls controls) {
    JPanel tab = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]"));
    tab.setOpaque(false);

    tab.add(helpText(
            "Configure desktop notifications for IRC events like kicks, bans, invites, and mode changes.\n"
                + "Source filter: Self, Someone else, or Any. Channel filters use globs (* and ?), separated by commas or spaces."),
        "growx, wmin 0, wrap");

    JComboBox<IrcEventNotificationPreset> defaultsPreset = new JComboBox<>(IrcEventNotificationPreset.values());
    JButton applyDefaults = new JButton("Apply defaults");
    applyDefaults.setToolTipText("Apply a starter set. Existing rows for the same event type are updated.");

    applyDefaults.addActionListener(e -> {
      if (controls.table.isEditing()) {
        try {
          controls.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      IrcEventNotificationPreset preset = (IrcEventNotificationPreset) defaultsPreset.getSelectedItem();
      if (preset == null) return;
      List<IrcEventNotificationRule> rules = buildIrcEventDefaultPreset(preset);
      if (rules.isEmpty()) return;

      controls.model.applyPreset(rules);
      if (controls.table.getRowCount() > 0) {
        int row = controls.model.firstRowForEvent(rules.get(0).eventType());
        if (row < 0) row = 0;
        controls.table.getSelectionModel().setSelectionInterval(row, row);
        controls.table.scrollRectToVisible(controls.table.getCellRect(row, 0, true));
      }
    });

    JPanel defaultsRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[grow,fill]8[]", "[]"));
    defaultsRow.setOpaque(false);
    defaultsRow.add(new JLabel("Defaults"));
    defaultsRow.add(defaultsPreset, "w 240!");
    defaultsRow.add(applyDefaults, "w 130!");

    JButton add = new JButton("Add");
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(add);
    buttons.add(duplicate);
    buttons.add(remove);
    buttons.add(up);
    buttons.add(down);

    add.addActionListener(e -> {
      if (controls.table.isEditing()) {
        try {
          controls.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = controls.model.addRule(new IrcEventNotificationRule(
          false,
          IrcEventNotificationRule.EventType.INVITE_RECEIVED,
          IrcEventNotificationRule.SourceFilter.ANY,
          true,
          false,
          BuiltInSound.NOTIF_1.name(),
          false,
          null,
          null,
          null));
      if (row < 0) return;
      controls.table.getSelectionModel().setSelectionInterval(row, row);
      controls.table.scrollRectToVisible(controls.table.getCellRect(row, 0, true));
      controls.table.requestFocusInWindow();
    });

    duplicate.addActionListener(e -> {
      if (controls.table.isEditing()) {
        try {
          controls.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = controls.table.getSelectedRow();
      if (row < 0) return;
      int dup = controls.model.duplicateRow(row);
      if (dup >= 0) {
        controls.table.getSelectionModel().setSelectionInterval(dup, dup);
        controls.table.scrollRectToVisible(controls.table.getCellRect(dup, 0, true));
      }
    });

    remove.addActionListener(e -> {
      if (controls.table.isEditing()) {
        try {
          controls.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = controls.table.getSelectedRow();
      if (row < 0) return;
      int res = JOptionPane.showConfirmDialog(dialog, "Remove selected IRC event rule?", "Remove rule", JOptionPane.OK_CANCEL_OPTION);
      if (res != JOptionPane.OK_OPTION) return;
      controls.model.removeRow(row);
    });

    up.addActionListener(e -> {
      if (controls.table.isEditing()) {
        try {
          controls.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }
      int row = controls.table.getSelectedRow();
      if (row <= 0) return;
      int next = controls.model.moveRow(row, row - 1);
      if (next >= 0) {
        controls.table.getSelectionModel().setSelectionInterval(next, next);
        controls.table.scrollRectToVisible(controls.table.getCellRect(next, 0, true));
      }
    });

    down.addActionListener(e -> {
      if (controls.table.isEditing()) {
        try {
          controls.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }
      int row = controls.table.getSelectedRow();
      if (row < 0) return;
      int next = controls.model.moveRow(row, row + 1);
      if (next >= 0) {
        controls.table.getSelectionModel().setSelectionInterval(next, next);
        controls.table.scrollRectToVisible(controls.table.getCellRect(next, 0, true));
      }
    });

    JScrollPane scroll = new JScrollPane(controls.table);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    JPanel soundPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 4", "[right]8[grow,fill]8[]8[]", "[]6[]6[]4[]"));
    soundPanel.setOpaque(false);
    soundPanel.add(sectionTitle("Selected rule sound"), "span 4, growx, wmin 0, wrap");
    soundPanel.add(new JLabel("Built-in"));
    soundPanel.add(controls.sound, "growx, wmin 160");
    soundPanel.add(controls.testSound, "w 110!");
    soundPanel.add(controls.useCustom, "wrap");
    soundPanel.add(new JLabel("Custom file"));
    soundPanel.add(controls.customPath, "growx, pushx, wmin 0");
    soundPanel.add(controls.browseCustom, "w 100!");
    soundPanel.add(controls.clearCustom, "w 80!");
    soundPanel.add(controls.selectionHint, "span 4, growx, wmin 0, wrap");
    soundPanel.add(helpText("When Sound is disabled on a rule, no sound is played for that event."),
        "span 4, growx, wmin 0, wrap");

    tab.add(defaultsRow, "growx, wmin 0, wrap");
    tab.add(helpText("Applies a starter profile by event type. Existing rows with matching event types are replaced."),
        "growx, wmin 0, wrap");
    tab.add(buttons, "growx, wrap");
    tab.add(scroll, "grow, push, h 260!, wrap");
    tab.add(soundPanel, "growx, wmin 0, wrap");

    return tab;
  }

  private List<IrcEventNotificationRule> buildIrcEventDefaultPreset(IrcEventNotificationPreset preset) {
    if (preset == null) return List.of();
    return switch (preset) {
      case ESSENTIAL -> List.of(
          eventDefaultRule(IrcEventNotificationRule.EventType.INVITE_RECEIVED, IrcEventNotificationRule.SourceFilter.ANY, true),
          eventDefaultRule(IrcEventNotificationRule.EventType.KICKED, IrcEventNotificationRule.SourceFilter.ANY, true),
          eventDefaultRule(IrcEventNotificationRule.EventType.BANNED, IrcEventNotificationRule.SourceFilter.OTHERS, true),
          eventDefaultRule(IrcEventNotificationRule.EventType.KLINED, IrcEventNotificationRule.SourceFilter.ANY, true));
      case MODERATION -> List.of(
          eventDefaultRule(IrcEventNotificationRule.EventType.KICKED, IrcEventNotificationRule.SourceFilter.OTHERS, true),
          eventDefaultRule(IrcEventNotificationRule.EventType.BANNED, IrcEventNotificationRule.SourceFilter.OTHERS, true),
          eventDefaultRule(IrcEventNotificationRule.EventType.OPPED, IrcEventNotificationRule.SourceFilter.OTHERS, false),
          eventDefaultRule(IrcEventNotificationRule.EventType.DEOPPED, IrcEventNotificationRule.SourceFilter.OTHERS, false),
          eventDefaultRule(IrcEventNotificationRule.EventType.VOICED, IrcEventNotificationRule.SourceFilter.OTHERS, false),
          eventDefaultRule(IrcEventNotificationRule.EventType.DEVOICED, IrcEventNotificationRule.SourceFilter.OTHERS, false),
          eventDefaultRule(IrcEventNotificationRule.EventType.HALF_OPPED, IrcEventNotificationRule.SourceFilter.OTHERS, false),
          eventDefaultRule(IrcEventNotificationRule.EventType.DEHALF_OPPED, IrcEventNotificationRule.SourceFilter.OTHERS, false),
          eventDefaultRule(IrcEventNotificationRule.EventType.INVITE_RECEIVED, IrcEventNotificationRule.SourceFilter.ANY, false));
      case ALL_EVENTS -> Arrays.stream(IrcEventNotificationRule.EventType.values())
          .map(t -> eventDefaultRule(
              t,
              IrcEventNotificationRule.SourceFilter.ANY,
              t == IrcEventNotificationRule.EventType.INVITE_RECEIVED
                  || t == IrcEventNotificationRule.EventType.KICKED
                  || t == IrcEventNotificationRule.EventType.BANNED
                  || t == IrcEventNotificationRule.EventType.KLINED))
          .toList();
    };
  }

  private static IrcEventNotificationRule eventDefaultRule(
      IrcEventNotificationRule.EventType eventType,
      IrcEventNotificationRule.SourceFilter sourceFilter,
      boolean soundEnabled
  ) {
    return new IrcEventNotificationRule(
        true,
        eventType,
        sourceFilter,
        true,
        soundEnabled,
        BuiltInSound.NOTIF_1.name(),
        false,
        null,
        null,
        null);
  }

  private JPanel buildNotificationsPanel(NotificationRulesControls notifications, IrcEventNotificationControls ircEventNotifications) {
    JPanel panel = new JPanel(new MigLayout("insets 10, fill, wrap 1", "[grow,fill]", "[]8[]4[grow,fill]"));

    panel.add(tabTitle("Notifications"), "growx, wmin 0, wrap");
    panel.add(sectionTitle("Rule matches"), "growx, wmin 0, wrap");
    panel.add(helpText(
        "Add custom word/regex rules to create notifications when messages match.\n"
            + "Rules only trigger for channels (not PMs) and only when the channel isn't the active target."),
        "growx, wmin 0, wrap");

    JButton add = new JButton("Add");
    JButton edit = new JButton("Edit");
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");
    JButton pickColor = new JButton("Color…");
    JButton clearColor = new JButton("Clear color");

    pickColor.setToolTipText("Choose a highlight color for this rule.");
    clearColor.setToolTipText("Clear the rule's highlight color.");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(add);
    buttons.add(edit);
    buttons.add(duplicate);
    buttons.add(remove);
    buttons.add(up);
    buttons.add(down);
    buttons.add(pickColor);
    buttons.add(clearColor);

    add.addActionListener(e -> {
      if (notifications.table.isEditing()) {
        try {
          notifications.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = notifications.model.addRule(new NotificationRule(
          "",
          NotificationRule.Type.WORD,
          "",
          true,
          false,
          true,
          null
      ));
      if (row >= 0) {
        notifications.table.getSelectionModel().setSelectionInterval(row, row);
        notifications.table.scrollRectToVisible(notifications.table.getCellRect(row, 0, true));
        notifications.table.editCellAt(row, NotificationRulesTableModel.COL_PATTERN);
        notifications.table.requestFocusInWindow();
      }
    });

    edit.addActionListener(e -> {
      int row = notifications.table.getSelectedRow();
      if (row < 0) return;
      notifications.table.editCellAt(row, NotificationRulesTableModel.COL_PATTERN);
      notifications.table.requestFocusInWindow();
    });

    duplicate.addActionListener(e -> {
      if (notifications.table.isEditing()) {
        try {
          notifications.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = notifications.table.getSelectedRow();
      if (row < 0) return;
      int dup = notifications.model.duplicateRow(row);
      if (dup >= 0) {
        notifications.table.getSelectionModel().setSelectionInterval(dup, dup);
        notifications.table.scrollRectToVisible(notifications.table.getCellRect(dup, 0, true));
      }
    });

    remove.addActionListener(e -> {
      if (notifications.table.isEditing()) {
        try {
          notifications.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = notifications.table.getSelectedRow();
      if (row < 0) return;
      int res = JOptionPane.showConfirmDialog(dialog, "Remove selected rule?", "Remove rule", JOptionPane.OK_CANCEL_OPTION);
      if (res != JOptionPane.OK_OPTION) return;
      notifications.model.removeRow(row);
    });

    up.addActionListener(e -> {
      if (notifications.table.isEditing()) {
        try {
          notifications.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = notifications.table.getSelectedRow();
      if (row <= 0) return;
      int next = notifications.model.moveRow(row, row - 1);
      if (next >= 0) {
        notifications.table.getSelectionModel().setSelectionInterval(next, next);
        notifications.table.scrollRectToVisible(notifications.table.getCellRect(next, 0, true));
      }
    });

    down.addActionListener(e -> {
      if (notifications.table.isEditing()) {
        try {
          notifications.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = notifications.table.getSelectedRow();
      if (row < 0) return;
      int next = notifications.model.moveRow(row, row + 1);
      if (next >= 0) {
        notifications.table.getSelectionModel().setSelectionInterval(next, next);
        notifications.table.scrollRectToVisible(notifications.table.getCellRect(next, 0, true));
      }
    });

    pickColor.addActionListener(e -> {
      if (notifications.table.isEditing()) {
        try {
          notifications.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = notifications.table.getSelectedRow();
      if (row < 0) return;
      pickNotificationRuleColor(notifications, row);
    });

    clearColor.addActionListener(e -> {
      if (notifications.table.isEditing()) {
        try {
          notifications.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }

      int row = notifications.table.getSelectedRow();
      if (row < 0) return;
      notifications.model.setHighlightFg(row, null);
    });


    // Double-click on a Color cell to pick a color (faster than using the button bar).
    notifications.table.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (e == null) return;
        if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return;
        if (e.getClickCount() != 2) return;

        int viewRow = notifications.table.rowAtPoint(e.getPoint());
        int viewCol = notifications.table.columnAtPoint(e.getPoint());
        if (viewRow < 0 || viewCol < 0) return;

        int modelCol = notifications.table.convertColumnIndexToModel(viewCol);
        if (modelCol != NotificationRulesTableModel.COL_COLOR) return;

        if (notifications.table.isEditing()) {
          try {
            notifications.table.getCellEditor().stopCellEditing();
          } catch (Exception ignored) {
          }
        }

        int modelRow = notifications.table.convertRowIndexToModel(viewRow);
        notifications.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        pickNotificationRuleColor(notifications, modelRow);
      }
    });
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

    JPanel testButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    testButtons.add(runTest);
    testButtons.add(clearTest);
    testButtons.add(notifications.testStatus);

    runTest.addActionListener(e -> {
      if (notifications.table.isEditing()) {
        try {
          notifications.table.getCellEditor().stopCellEditing();
        } catch (Exception ignored) {
        }
      }
      refreshNotificationRuleValidation(notifications);
      notifications.testRunner.runTest(notifications);
    });

    clearTest.addActionListener(e -> {
      notifications.testInput.setText("");
      notifications.testOutput.setText("");
      notifications.testStatus.setText(" ");
    });

    JPanel rulesTab = new JPanel(new MigLayout("insets 0, fill, wrap 2", "[right]10[grow,fill]", "[]6[]4[grow,fill]4[]4[]"));
    rulesTab.setOpaque(false);
    rulesTab.add(new JLabel("Cooldown (sec)"));
    rulesTab.add(notifications.cooldownSeconds, "w 110!, wrap");
    rulesTab.add(new JLabel("Rules"));
    rulesTab.add(buttons, "growx, wrap");
    rulesTab.add(scroll, "span 2, grow, push, h 260!, wrap");
    rulesTab.add(notifications.validationLabel, "span 2, growx, wmin 0, wrap");
    rulesTab.add(helpText("Tip: Double-click a rule's Color cell to quickly choose a color."),
        "span 2, growx, wmin 0, wrap");

    JPanel testTab = new JPanel(new MigLayout("insets 0, fill, wrap 2", "[right]10[grow,fill]", "[]6[]4[]4[]"));
    testTab.setOpaque(false);
    testTab.add(helpText(
            "Paste a sample message to see which rules match. This is just a preview; it won't create real notifications."),
        "span 2, growx, wmin 0, wrap");
    testTab.add(new JLabel("Sample"), "aligny top");
    testTab.add(testInScroll, "growx, h 100!, wrap");
    testTab.add(new JLabel("Matches"), "aligny top");
    testTab.add(testOutScroll, "growx, h 160!, wrap");
    testTab.add(new JLabel(""));
    testTab.add(testButtons, "growx, wrap");

    JTabbedPane subTabs = new JTabbedPane();
    Icon rulesTabIcon = SvgIcons.action("edit", 14);
    Icon testTabIcon = SvgIcons.action("check", 14);
    subTabs.addTab("Rules", rulesTabIcon, padSubTab(rulesTab), "Manage notification matching rules");
    subTabs.addTab("Test", testTabIcon, padSubTab(testTab), "Try a sample message against your rules");
    subTabs.addTab(
        "IRC Events",
        null,
        padSubTab(buildIrcEventNotificationsTab(ircEventNotifications)),
        "Configure notifications for IRC events like kick/ban/invite/mode updates");

    panel.add(subTabs, "grow, push, wmin 0");

    refreshNotificationRuleValidation(notifications);

    return panel;
  }

  private void attachNotificationRuleValidation(NotificationRulesControls notifications, JButton apply, JButton ok) {
    Runnable refresh = () -> {
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
      return "Row " + (rowIndex + 1) + " (" + effectiveLabel() + "):\n" + msg + "\n\nPattern:\n" + (pattern != null ? pattern : "");
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
      exec.submit(() -> {
        String report = buildRuleTestReport(rules, errors, sampleFinal);
        SwingUtilities.invokeLater(() -> {
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

  private static String buildRuleTestReport(List<NotificationRule> rules, List<ValidationError> errors, String sample) {
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
        out.append("  - row ").append(e.rowIndex + 1).append(": ").append(e.effectiveLabel()).append("\n");
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
    String label = (rule.label() != null && !rule.label().trim().isEmpty())
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

  private static RuleMatch findWordMatch(String msg, String pat, boolean caseSensitive, boolean wholeWord) {
    if (msg == null || pat == null) return null;
    if (pat.isEmpty()) return null;

    if (wholeWord) {
      int plen = pat.length();
      for (Token tok : tokenize(msg)) {
        int tlen = tok.end - tok.start;
        if (tlen != plen) continue;

        boolean ok = caseSensitive
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
    String s = java.util.Objects.toString(id, "").trim();
    if (s.isEmpty()) return "darcula";

    // Preserve case for IntelliJ theme ids and raw LookAndFeel class names.
    if (s.regionMatches(true, 0, IntelliJThemePack.ID_PREFIX, 0, IntelliJThemePack.ID_PREFIX.length())) {
      return IntelliJThemePack.ID_PREFIX + s.substring(IntelliJThemePack.ID_PREFIX.length());
    }
    if (looksLikeClassNameInternal(s)) return s;

    return s.toLowerCase();
  }

  private static boolean sameThemeInternal(String a, String b) {
    return normalizeThemeIdInternal(a).equals(normalizeThemeIdInternal(b));
  }

  private static boolean looksLikeClassNameInternal(String raw) {
    if (raw == null) return false;
    String s = raw.trim();
    if (!s.contains(".")) return false;
    if (s.startsWith("com.") || s.startsWith("org.") || s.startsWith("net.") || s.startsWith("io.")) return true;
    String last = s.substring(s.lastIndexOf('.') + 1);
    return !last.isBlank() && Character.isUpperCase(last.charAt(0));
  }


  private static JDialog createDialog(Window owner) {
    final JDialog d = new JDialog(owner, "Preferences", JDialog.ModalityType.APPLICATION_MODAL);
    d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    return d;
  }

  private static JSpinner numberSpinner(int value, int min, int max, int step, List<AutoCloseable> closeables) {
    JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, step));
    AutoCloseable ac = MouseWheelDecorator.decorateNumberSpinner(s);
    if (ac != null) closeables.add(ac);
    return s;
  }

  private static JSpinner doubleSpinner(double value, double min, double max, double step, List<AutoCloseable> closeables) {
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

  private static Icon createColorSwatchIcon(Color color, int w, int h, Color previewBackground) {
    // Simple swatch icon used in compact pickers/buttons.
    return new ColorSwatch(color, w, h);
  }

  private void pickNotificationRuleColor(NotificationRulesControls notifications, int row) {
    if (notifications == null) return;
    if (row < 0 || row >= notifications.model.getRowCount()) return;

    String raw = notifications.model.highlightFgAt(row);
    Color currentColor = parseHexColorLenient(raw);
    if (currentColor == null) {
      Color def = UIManager.getColor("TextPane.foreground");
      if (def == null) def = UIManager.getColor("Label.foreground");
      currentColor = def != null ? def : Color.WHITE;
    }

    Color chosen = showColorPickerDialog(dialog, "Choose Rule Highlight Color", currentColor, preferredPreviewBackground());
    if (chosen != null) {
      notifications.model.setHighlightFg(row, toHex(chosen));
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
    b.addActionListener(e -> {
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

  private static Color showColorPickerDialog(Window owner, String title, Color initial, Color previewBackground) {
    Color bg = previewBackground != null ? previewBackground : preferredPreviewBackground();
    Color init = initial != null ? initial : Color.WHITE;

    // A compact "hex + palette + preview" picker; way less chaotic than the stock Swing chooser.
    final JDialog d = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
    d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

    final Color[] current = new Color[] { init };
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

    final boolean[] internalUpdate = new boolean[] { false };

    Runnable updatePreview = () -> {
      Color fg = current[0];
      preview.setForeground(fg);
      preview.setText(" IRCafe preview  " + toHex(fg));
      double cr = contrastRatio(fg, bg);
      String verdict = cr >= 4.5 ? "OK" : (cr >= 3.0 ? "Low" : "Bad");
      contrast.setText(String.format(Locale.ROOT, "Contrast: %.1f (%s)", cr, verdict));
      ok.setEnabled(fg != null);
    };

    Consumer<Color> setColor = c -> {
      if (c == null) return;
      current[0] = c;
      internalUpdate[0] = true;
      hex.setText(toHex(c));
      internalUpdate[0] = false;
      hexStatus.setText(" ");
      updatePreview.run();
    };

    hex.getDocument().addDocumentListener(new SimpleDocListener(() -> {
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
    Color[] colors = new Color[] {
        new Color(0xFFFFFF), new Color(0xD9D9D9), new Color(0xA6A6A6), new Color(0x4D4D4D), new Color(0x000000), new Color(0xFF6B6B), new Color(0xFFA94D), new Color(0xFFD43B),
        new Color(0x69DB7C), new Color(0x38D9A9), new Color(0x22B8CF), new Color(0x4DABF7), new Color(0x748FFC), new Color(0x9775FA), new Color(0xDA77F2), new Color(0xF783AC),
        new Color(0xC92A2A), new Color(0xE8590C), new Color(0xF08C00), new Color(0x2F9E44), new Color(0x0CA678), new Color(0x1098AD), new Color(0x1971C2), new Color(0x5F3DC4)
    };
    for (Color c : colors) {
      palette.add(colorSwatchButton(c, setColor));
    }

    JPanel recent = new JPanel(new MigLayout("insets 0, wrap 8, gap 6", "[]", "[]"));
    Runnable refreshRecent = () -> {
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

    more.addActionListener(e -> {
      Color picked = JColorChooser.showDialog(d, "More Colors", current[0] != null ? current[0] : init);
      if (picked != null) setColor.accept(picked);
    });

    ok.addActionListener(e -> {
      if (current[0] == null) return;
      result[0] = current[0];
      rememberRecentColorHex(toHex(current[0]));
      d.dispose();
    });

    cancel.addActionListener(e -> {
      result[0] = null;
      d.dispose();
    });

    JPanel content = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[grow,fill]12[grow,fill]", "[]10[]6[]10[]6[]10[]"));
    content.add(preview, "span 2, growx, wrap");
    content.add(contrast, "span 2, growx, wrap");

    content.add(new JLabel("Hex"));
    JPanel hexRow = new JPanel(new MigLayout("insets 0, fillx, wrap 3", "[grow,fill]6[nogrid]6[nogrid]", "[]2[]"));
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
    d.getRootPane().registerKeyboardAction(
        ev -> cancel.doClick(),
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW
    );

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

    @Override public String toString() {
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
    if (matchesLookupRatePreset(s, LookupRatePreset.CONSERVATIVE)) return LookupRatePreset.CONSERVATIVE;
    if (matchesLookupRatePreset(s, LookupRatePreset.RAPID)) return LookupRatePreset.RAPID;
    return LookupRatePreset.CUSTOM;
  }

  private static boolean matchesLookupRatePreset(UiSettings s, LookupRatePreset preset) {
    return switch (preset) {
      case CONSERVATIVE -> (
          s.userhostMinIntervalSeconds() == 10 &&
              s.userhostMaxCommandsPerMinute() == 2 &&
              s.userhostNickCooldownMinutes() == 60 &&
              s.userhostMaxNicksPerCommand() == 5 &&

              s.userInfoEnrichmentUserhostMinIntervalSeconds() == 30 &&
              s.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 2 &&
              s.userInfoEnrichmentUserhostNickCooldownMinutes() == 180 &&
              s.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5 &&

              s.userInfoEnrichmentWhoisMinIntervalSeconds() == 120 &&
              s.userInfoEnrichmentWhoisNickCooldownMinutes() == 240 &&

              s.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 600 &&
              s.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 1
      );
      case BALANCED -> (
          s.userhostMinIntervalSeconds() == 5 &&
              s.userhostMaxCommandsPerMinute() == 6 &&
              s.userhostNickCooldownMinutes() == 30 &&
              s.userhostMaxNicksPerCommand() == 5 &&

              s.userInfoEnrichmentUserhostMinIntervalSeconds() == 15 &&
              s.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 4 &&
              s.userInfoEnrichmentUserhostNickCooldownMinutes() == 60 &&
              s.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5 &&

              s.userInfoEnrichmentWhoisMinIntervalSeconds() == 60 &&
              s.userInfoEnrichmentWhoisNickCooldownMinutes() == 120 &&

              s.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 300 &&
              s.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 2
      );
      case RAPID -> (
          s.userhostMinIntervalSeconds() == 2 &&
              s.userhostMaxCommandsPerMinute() == 15 &&
              s.userhostNickCooldownMinutes() == 10 &&
              s.userhostMaxNicksPerCommand() == 5 &&

              s.userInfoEnrichmentUserhostMinIntervalSeconds() == 5 &&
              s.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 10 &&
              s.userInfoEnrichmentUserhostNickCooldownMinutes() == 15 &&
              s.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5 &&

              s.userInfoEnrichmentWhoisMinIntervalSeconds() == 15 &&
              s.userInfoEnrichmentWhoisNickCooldownMinutes() == 30 &&

              s.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 60 &&
              s.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 3
      );
      default -> false;
    };
  }

  private record NotificationRulesControls(JSpinner cooldownSeconds,
                                JTable table,
                                NotificationRulesTableModel model,
                                JLabel validationLabel,
                                JTextArea testInput,
                                JTextArea testOutput,
                                JLabel testStatus,
                                RuleTestRunner testRunner) {
  }

  private record IrcEventNotificationControls(
      JTable table,
      IrcEventNotificationTableModel model,
      JComboBox<BuiltInSound> sound,
      JCheckBox useCustom,
      JTextField customPath,
      JButton browseCustom,
      JButton clearCustom,
      JButton testSound,
      JLabel selectionHint
  ) {
  }

  private static final class IrcEventNotificationTableModel extends AbstractTableModel {
    static final int COL_ENABLED = 0;
    static final int COL_EVENT = 1;
    static final int COL_SOURCE = 2;
    static final int COL_TOAST = 3;
    static final int COL_SOUND = 4;
    static final int COL_CHANNEL_WHITELIST = 5;
    static final int COL_CHANNEL_BLACKLIST = 6;

    private static final String[] COLS = new String[] {
        "Enabled",
        "Event",
        "Source",
        "Toast",
        "Sound",
        "Channels",
        "Exclude"
    };

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

    String soundIdAt(int row) {
      if (row < 0 || row >= rows.size()) return BuiltInSound.NOTIF_1.name();
      return rows.get(row).soundId;
    }

    boolean soundUseCustomAt(int row) {
      if (row < 0 || row >= rows.size()) return false;
      return rows.get(row).soundUseCustom;
    }

    String soundCustomPathAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      return rows.get(row).soundCustomPath;
    }

    void setSoundConfig(int row, String soundId, boolean soundUseCustom, String soundCustomPath) {
      if (row < 0 || row >= rows.size()) return;
      MutableRule r = rows.get(row);
      r.soundId = BuiltInSound.fromId(soundId).name();
      String path = Objects.toString(soundCustomPath, "").trim();
      if (path.isBlank()) path = null;
      r.soundCustomPath = path;
      r.soundUseCustom = soundUseCustom && path != null;
      fireTableRowsUpdated(row, row);
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
      return switch (columnIndex) {
        case COL_ENABLED, COL_TOAST, COL_SOUND -> Boolean.class;
        case COL_EVENT -> IrcEventNotificationRule.EventType.class;
        case COL_SOURCE -> IrcEventNotificationRule.SourceFilter.class;
        default -> String.class;
      };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return rowIndex >= 0 && rowIndex < rows.size() && columnIndex >= 0 && columnIndex < COLS.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return null;
      MutableRule r = rows.get(rowIndex);
      return switch (columnIndex) {
        case COL_ENABLED -> r.enabled;
        case COL_EVENT -> r.eventType;
        case COL_SOURCE -> r.sourceFilter;
        case COL_TOAST -> r.toastEnabled;
        case COL_SOUND -> r.soundEnabled;
        case COL_CHANNEL_WHITELIST -> r.channelWhitelist;
        case COL_CHANNEL_BLACKLIST -> r.channelBlacklist;
        default -> null;
      };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return;
      MutableRule r = rows.get(rowIndex);

      switch (columnIndex) {
        case COL_ENABLED -> r.enabled = asBool(aValue);
        case COL_EVENT -> r.eventType = asEventType(aValue);
        case COL_SOURCE -> r.sourceFilter = asSourceFilter(aValue);
        case COL_TOAST -> r.toastEnabled = asBool(aValue);
        case COL_SOUND -> r.soundEnabled = asBool(aValue);
        case COL_CHANNEL_WHITELIST -> r.channelWhitelist = trimToNull(aValue);
        case COL_CHANNEL_BLACKLIST -> r.channelBlacklist = trimToNull(aValue);
        default -> {
        }
      }

      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    private static boolean asBool(Object v) {
      if (v instanceof Boolean b) return b;
      return Boolean.parseBoolean(Objects.toString(v, "false"));
    }

    private static IrcEventNotificationRule.EventType asEventType(Object v) {
      if (v instanceof IrcEventNotificationRule.EventType t) return t;
      String s = Objects.toString(v, "").trim();
      if (s.isEmpty()) return IrcEventNotificationRule.EventType.INVITE_RECEIVED;
      try {
        return IrcEventNotificationRule.EventType.valueOf(s);
      } catch (Exception ignored) {
        return IrcEventNotificationRule.EventType.INVITE_RECEIVED;
      }
    }

    private static IrcEventNotificationRule.SourceFilter asSourceFilter(Object v) {
      if (v instanceof IrcEventNotificationRule.SourceFilter f) return f;
      String s = Objects.toString(v, "").trim();
      if (s.isEmpty()) return IrcEventNotificationRule.SourceFilter.ANY;
      try {
        return IrcEventNotificationRule.SourceFilter.valueOf(s);
      } catch (Exception ignored) {
        return IrcEventNotificationRule.SourceFilter.ANY;
      }
    }

    private static String trimToNull(Object v) {
      String s = Objects.toString(v, "").trim();
      return s.isEmpty() ? null : s;
    }

    private static final class MutableRule {
      boolean enabled;
      IrcEventNotificationRule.EventType eventType;
      IrcEventNotificationRule.SourceFilter sourceFilter;
      boolean toastEnabled;
      boolean soundEnabled;
      String soundId;
      boolean soundUseCustom;
      String soundCustomPath;
      String channelWhitelist;
      String channelBlacklist;

      IrcEventNotificationRule toRule() {
        return new IrcEventNotificationRule(
            enabled,
            eventType,
            sourceFilter,
            toastEnabled,
            soundEnabled,
            soundId,
            soundUseCustom,
            soundCustomPath,
            channelWhitelist,
            channelBlacklist);
      }

      MutableRule copy() {
        MutableRule m = new MutableRule();
        m.enabled = enabled;
        m.eventType = eventType;
        m.sourceFilter = sourceFilter;
        m.toastEnabled = toastEnabled;
        m.soundEnabled = soundEnabled;
        m.soundId = soundId;
        m.soundUseCustom = soundUseCustom;
        m.soundCustomPath = soundCustomPath;
        m.channelWhitelist = channelWhitelist;
        m.channelBlacklist = channelBlacklist;
        return m;
      }

      static MutableRule from(IrcEventNotificationRule r) {
        MutableRule m = new MutableRule();
        if (r == null) {
          m.enabled = false;
          m.eventType = IrcEventNotificationRule.EventType.INVITE_RECEIVED;
          m.sourceFilter = IrcEventNotificationRule.SourceFilter.ANY;
          m.toastEnabled = true;
          m.soundEnabled = false;
          m.soundId = BuiltInSound.NOTIF_1.name();
          m.soundUseCustom = false;
          m.soundCustomPath = null;
          m.channelWhitelist = null;
          m.channelBlacklist = null;
          return m;
        }

        m.enabled = r.enabled();
        m.eventType = r.eventType();
        m.sourceFilter = r.sourceFilter();
        m.toastEnabled = r.toastEnabled();
        m.soundEnabled = r.soundEnabled();
        m.soundId = BuiltInSound.fromId(r.soundId()).name();
        m.soundUseCustom = r.soundUseCustom();
        m.soundCustomPath = r.soundCustomPath();
        m.channelWhitelist = r.channelWhitelist();
        m.channelBlacklist = r.channelBlacklist();
        return m;
      }
    }
  }

  private static final class NotificationRulesTableModel extends AbstractTableModel {
    static final int COL_ENABLED = 0;
    static final int COL_TYPE = 1;
    static final int COL_LABEL = 2;
    static final int COL_PATTERN = 3;
    static final int COL_COLOR = 4;
    static final int COL_CASE = 5;
    static final int COL_WHOLE = 6;

    private static final String[] COLS = new String[] {
        "Enabled",
        "Type",
        "Label",
        "Pattern",
        "Color",
        "Case",
        "Whole"
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
      return switch (columnIndex) {
        case COL_ENABLED, COL_CASE, COL_WHOLE -> Boolean.class;
        case COL_TYPE -> NotificationRule.Type.class;
        default -> String.class;
      };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return false;
      if (columnIndex == COL_WHOLE) {
        return rows.get(rowIndex).type == NotificationRule.Type.WORD;
      }
      return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return null;
      MutableRule r = rows.get(rowIndex);
      return switch (columnIndex) {
        case COL_ENABLED -> r.enabled;
        case COL_TYPE -> r.type;
        case COL_LABEL -> r.label;
        case COL_PATTERN -> r.pattern;
        case COL_COLOR -> r.highlightFg;
        case COL_CASE -> r.caseSensitive;
        case COL_WHOLE -> r.wholeWord;
        default -> null;
      };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return;
      MutableRule r = rows.get(rowIndex);

      switch (columnIndex) {
        case COL_ENABLED -> r.enabled = asBool(aValue);
        case COL_TYPE -> {
          NotificationRule.Type t = asType(aValue);
          if (t == null) t = NotificationRule.Type.WORD;
          r.type = t;
          if (t == NotificationRule.Type.REGEX) {
            r.wholeWord = false;
          }
        }
        case COL_LABEL -> r.label = asStr(aValue);
        case COL_PATTERN -> {
          r.pattern = asStr(aValue);
          if (r.pattern.isBlank()) {
            r.enabled = false;
          }
        }
        case COL_COLOR -> r.highlightFg = normalizeHexColor(asStr(aValue));
        case COL_CASE -> r.caseSensitive = asBool(aValue);
        case COL_WHOLE -> {
          if (r.type == NotificationRule.Type.WORD) {
            r.wholeWord = asBool(aValue);
          }
        }
        default -> {
        }
      }

      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    private static String asStr(Object v) {
      String s = Objects.toString(v, "");
      return s.trim();
    }

    private static boolean asBool(Object v) {
      if (v instanceof Boolean b) return b;
      return Boolean.parseBoolean(Objects.toString(v, "false"));
    }

    private static NotificationRule.Type asType(Object v) {
      if (v instanceof NotificationRule.Type t) return t;
      String s = Objects.toString(v, "").trim();
      if (s.isEmpty()) return null;
      try {
        return NotificationRule.Type.valueOf(s);
      } catch (Exception ignored) {
        return null;
      }
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
        boolean ok = (c >= '0' && c <= '9')
            || (c >= 'a' && c <= 'f')
            || (c >= 'A' && c <= 'F');
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
    public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

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

    @Override public int getIconWidth() { return w; }
    @Override public int getIconHeight() { return h; }

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

  private record AccentControls(JCheckBox enabled,
                               JComboBox<AccentPreset> preset,
                               JTextField hex,
                               JButton pick,
                               JButton clear,
                               JSlider strength,
                               JComponent chip,
                               JPanel panel,
                               Runnable applyEnabledState,
                               Runnable syncPresetFromHex,
                               Runnable updateChip) {
  }

  private record ColorField(JTextField hex, JButton pick, JButton clear, JPanel panel, Runnable updateIcon) {}

  private record ChatThemeControls(JComboBox<ChatThemeSettings.Preset> preset, ColorField timestamp, ColorField system, ColorField mention, JSlider mentionStrength) {}


  private record ThemeControls(JComboBox<String> combo) {
  }

  private record FontControls(JComboBox<String> fontFamily, JSpinner fontSize) {
  }


  private record DensityOption(String id, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  private record TweakControls(JComboBox<DensityOption> density, JSlider cornerRadius) {
  }


  private record TrayControls(JCheckBox enabled,
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
                              JButton testNotification,
                              JCheckBox notificationSoundsEnabled,
                              JCheckBox notificationSoundUseCustom,
                              JTextField notificationSoundCustomPath,
                              JButton browseCustomSound,
                              JButton clearCustomSound,
                              JComboBox<BuiltInSound> notificationSound,
                              JButton testSound,
                              JPanel panel) {
  }

  private record ImageEmbedControls(JCheckBox enabled,
                                   JCheckBox collapsed,
                                   JSpinner maxWidth,
                                   JSpinner maxHeight,
                                   JCheckBox animateGifs,
                                   JPanel panel) {
  }

  private record LinkPreviewControls(JCheckBox enabled, JCheckBox collapsed, JPanel panel) {
  }

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

  private record NickColorControls(JCheckBox enabled,
                                  JSpinner minContrast,
                                  JButton overrides,
                                  NickColorPreviewPanel preview,
                                  JPanel panel) {
  }

  private record TimestampControls(JCheckBox enabled,
                                  JTextField format,
                                  JCheckBox includeChatMessages,
                                  JCheckBox includePresenceMessages,
                                  JPanel panel) {
  }
  private record HistoryControls(JSpinner initialLoadLines, JSpinner pageSize, JSpinner commandHistoryMaxSize, JPanel panel) {
  }

  private record LoggingControls(JCheckBox enabled,
                                JCheckBox logSoftIgnored,
                                JCheckBox logPrivateMessages,
                                JCheckBox savePrivateMessageList,
                                JButton managePrivateMessageList,
                                JCheckBox keepForever,
                                JSpinner retentionDays,
                                JTextField dbBaseName,
                                JCheckBox dbNextToConfig,
                                JTextArea info) {
  }

  private record OutgoingColorControls(JCheckBox enabled,
                                      JTextField hex,
                                      JLabel preview,
                                      JPanel panel) {
  }

  private record UserhostControls(JCheckBox enabled,
                                 JSpinner minIntervalSeconds,
                                 JSpinner maxPerMinute,
                                 JSpinner nickCooldownMinutes,
                                 JSpinner maxNicksPerCommand) {
  }

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
      JSpinner periodicRefreshNicksPerTick
  ) {
  }

  private record ProxyControls(JCheckBox enabled,
                               JTextField host,
                               JSpinner port,
                               JCheckBox remoteDns,
                               JTextField username,
                               JPasswordField password,
                               JButton clearPassword,
                               JSpinner connectTimeoutSeconds,
                               JSpinner readTimeoutSeconds) {
  }

  private record HeartbeatControls(JCheckBox enabled,
                                  JSpinner checkPeriodSeconds,
                                  JSpinner timeoutSeconds) {
  }

  private record NetworkAdvancedControls(ProxyControls proxy,
                                         UserhostControls userhost,
                                         UserInfoEnrichmentControls enrichment,
                                         HeartbeatControls heartbeat,
                                         JCheckBox trustAllTlsCertificates,
                                         JPanel networkPanel,
                                         JPanel userLookupsPanel) {
  }

  private static final class NickColorPreviewPanel extends JPanel {
    private static final String[] SAMPLE_NICKS = new String[] {
        "Alice", "Bob", "Carol", "Dave", "Eve", "Mallory"
    };

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

      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(border, 1),
          BorderFactory.createEmptyBorder(6, 8, 6, 8)
      ));

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

        Color c = (nickColorService != null)
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

    @Override public void insertUpdate(DocumentEvent e) { onChange.run(); }
    @Override public void removeUpdate(DocumentEvent e) { onChange.run(); }
    @Override public void changedUpdate(DocumentEvent e) { onChange.run(); }
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

    final FilterRulesTableModel rulesModel;
    final JTable rulesTable;

    final JButton addRule;
    final JButton editRule;
    final JButton deleteRule;

    final JButton moveRuleUp;
    final JButton moveRuleDown;

    private FilterControls(JCheckBox filtersEnabledByDefault,
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
                           FilterRulesTableModel rulesModel,
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
      this.rulesModel = rulesModel;
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
    Tri(String label) { this.label = label; }

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

    @Override public String toString() { return label; }
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
          rows.add(new FilterOverridesRow(
              o.scopePattern(),
              Tri.fromNullable(o.filtersEnabled()),
              Tri.fromNullable(o.placeholdersEnabled()),
              Tri.fromNullable(o.placeholdersCollapsed())
          ));
        }
      }
      fireTableDataChanged();
    }

    List<FilterScopeOverride> toOverrides() {
      List<FilterScopeOverride> out = new ArrayList<>();
      for (FilterOverridesRow r : rows) {
        String s = r.scope != null ? r.scope.trim() : "";
        if (s.isEmpty()) continue;
        out.add(new FilterScopeOverride(
            s,
            r.filters.toNullable(),
            r.placeholders.toNullable(),
            r.collapsed.toNullable()
        ));
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

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return 4; }

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


  
  private static final class CenteredBooleanRenderer extends JCheckBox implements TableCellRenderer {
    CenteredBooleanRenderer() {
      setHorizontalAlignment(SwingConstants.CENTER);
      setBorderPainted(false);
      setOpaque(true);
      setEnabled(true);
    }

    @Override
    public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
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

    @Override public int getRowCount() { return rules.size(); }
    @Override public int getColumnCount() { return 5; }

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

      FilterRule next = new FilterRule(
          cur.id(),
          cur.name(),
          enabled,
          cur.scopePattern(),
          cur.action(),
          cur.direction(),
          cur.kinds(),
          cur.fromNickGlobs(),
          cur.textRegex(),
          cur.tags()
      );
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
        String ks = r.kinds().stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("");
        if (!ks.isBlank()) parts.add("kinds=" + ks);
      }

      if (r.direction() != null && r.direction() != cafe.woden.ircclient.ui.filter.FilterDirection.ANY) {
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
          if (r.textRegex().flags().contains(cafe.woden.ircclient.ui.filter.RegexFlag.I)) sb.append('i');
          if (r.textRegex().flags().contains(cafe.woden.ircclient.ui.filter.RegexFlag.M)) sb.append('m');
          if (r.textRegex().flags().contains(cafe.woden.ircclient.ui.filter.RegexFlag.S)) sb.append('s');
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


  private FilterControls buildFilterControls(FilterSettings current, List<AutoCloseable> closeables) {
    Objects.requireNonNull(current);

    JCheckBox enabledByDefault = new JCheckBox("Enable filters by default");
    enabledByDefault.setSelected(current.filtersEnabledByDefault());

    JCheckBox placeholdersEnabledByDefault = new JCheckBox("Enable \"Filtered (N)\" placeholders by default");
    placeholdersEnabledByDefault.setSelected(current.placeholdersEnabledByDefault());

    JCheckBox placeholdersCollapsedByDefault = new JCheckBox("Collapse placeholders by default");
    placeholdersCollapsedByDefault.setSelected(current.placeholdersCollapsedByDefault());

    JSpinner previewLines = new JSpinner(new SpinnerNumberModel(
        Math.max(0, Math.min(25, current.placeholderMaxPreviewLines())),
        0, 25, 1
    ));
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


    JSpinner maxLinesPerRun = new JSpinner(new SpinnerNumberModel(
        Math.max(0, Math.min(50_000, current.placeholderMaxLinesPerRun())),
        0, 50_000, 50
    ));
    maxLinesPerRun.setToolTipText("Max hidden lines represented in a single placeholder run. 0 = unlimited.");
    if (closeables != null) {
      try { closeables.add(MouseWheelDecorator.decorateNumberSpinner(maxLinesPerRun)); } catch (Exception ignored) {}
    } else {
      try { MouseWheelDecorator.decorateNumberSpinner(maxLinesPerRun); } catch (Exception ignored) {}
    }

    JSpinner tooltipMaxTags = new JSpinner(new SpinnerNumberModel(
        Math.max(0, Math.min(500, current.placeholderTooltipMaxTags())),
        0, 500, 1
    ));
    tooltipMaxTags.setToolTipText("Max tags shown in placeholder/hint tooltips. 0 = hide tags.");
    if (closeables != null) {
      try { closeables.add(MouseWheelDecorator.decorateNumberSpinner(tooltipMaxTags)); } catch (Exception ignored) {}
    } else {
      try { MouseWheelDecorator.decorateNumberSpinner(tooltipMaxTags); } catch (Exception ignored) {}
    }

    JCheckBox historyPlaceholdersEnabledByDefault = new JCheckBox("Show placeholders for filtered history loads");
    historyPlaceholdersEnabledByDefault.setSelected(current.historyPlaceholdersEnabledByDefault());
    historyPlaceholdersEnabledByDefault.setToolTipText("If off, filtered lines loaded from history are silently hidden (no placeholder/hint rows).");

    JSpinner historyMaxRuns = new JSpinner(new SpinnerNumberModel(
        Math.max(0, Math.min(5_000, current.historyPlaceholderMaxRunsPerBatch())),
        0, 5_000, 1
    ));
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


    // If history placeholders are disabled, the batch cap is irrelevant (keep the value but disable the control).
    try {
      historyMaxRuns.setEnabled(historyPlaceholdersEnabledByDefault.isSelected());
      historyPlaceholdersEnabledByDefault.addActionListener(e ->
          historyMaxRuns.setEnabled(historyPlaceholdersEnabledByDefault.isSelected())
      );
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
    remove.setEnabled(false);

    table.getSelectionModel().addListSelectionListener(e -> {
      remove.setEnabled(table.getSelectedRow() >= 0);
    });

    add.addActionListener(e -> {
      String scope = JOptionPane.showInputDialog(dialog, "Scope pattern (e.g. libera/#llamas, libera/*, */status)", "Add Override", JOptionPane.PLAIN_MESSAGE);
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

    remove.addActionListener(e -> {
      int row = table.getSelectedRow();
      if (row < 0) return;
      int confirm = JOptionPane.showConfirmDialog(dialog, "Remove selected override?", "Remove Override", JOptionPane.OK_CANCEL_OPTION);
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
    PropertyChangeListener rulesListener = evt -> {
      if (!FilterSettingsBus.PROP_FILTER_SETTINGS.equals(evt.getPropertyName())) return;
      Object nv = evt.getNewValue();
      if (!(nv instanceof FilterSettings fs)) return;

      SwingUtilities.invokeLater(() -> {
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
    rulesModel.addTableModelListener(ev -> {
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

        FilterSettings next = new FilterSettings(
            snap.filtersEnabledByDefault(),
            snap.placeholdersEnabledByDefault(),
            snap.placeholdersCollapsedByDefault(),
            snap.placeholderMaxPreviewLines(),
            snap.placeholderMaxLinesPerRun(),
            snap.placeholderTooltipMaxTags(),
            snap.historyPlaceholderMaxRunsPerBatch(),
            snap.historyPlaceholdersEnabledByDefault(),
            nextRules,
            snap.overrides()
        );

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
    editRule.setEnabled(false);
    deleteRule.setEnabled(false);
    moveRuleUp.setEnabled(false);
    moveRuleDown.setEnabled(false);

    Runnable refreshRuleButtons = () -> {
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
            rowFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.Integer");
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
              return new DataFlavor[]{rowFlavor};
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

          FilterSettings next = new FilterSettings(
              snap.filtersEnabledByDefault(),
              snap.placeholdersEnabledByDefault(),
              snap.placeholdersCollapsedByDefault(),
              snap.placeholderMaxPreviewLines(),
              snap.placeholderMaxLinesPerRun(),
              snap.placeholderTooltipMaxTags(),
              snap.historyPlaceholderMaxRunsPerBatch(),
              snap.historyPlaceholdersEnabledByDefault(),
              nextRules,
              snap.overrides()
          );

          filterSettingsBus.set(next);
          runtimeConfig.rememberFilterRules(next.rules());

          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null) transcriptRebuildService.rebuild(active);
          } catch (Exception ignored) {
          }

          final int newRow = dropModelRow;
          SwingUtilities.invokeLater(() -> {
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

    Runnable moveSelectedRuleUp = () -> {
      int row = rulesTable.getSelectedRow();
      if (row <= 0) return;

      int newRow = row - 1;
      FilterSettings snap = filterSettingsBus.get();
      if (snap == null || snap.rules() == null) return;

      List<FilterRule> nextRules = new ArrayList<>(snap.rules());
      if (row >= nextRules.size() || newRow < 0) return;
      java.util.Collections.swap(nextRules, row, newRow);

      FilterSettings next = new FilterSettings(
          snap.filtersEnabledByDefault(),
          snap.placeholdersEnabledByDefault(),
          snap.placeholdersCollapsedByDefault(),
          snap.placeholderMaxPreviewLines(),
          snap.placeholderMaxLinesPerRun(),
          snap.placeholderTooltipMaxTags(),
          snap.historyPlaceholderMaxRunsPerBatch(),
          snap.historyPlaceholdersEnabledByDefault(),
          nextRules,
          snap.overrides()
      );

      filterSettingsBus.set(next);
      runtimeConfig.rememberFilterRules(next.rules());

      try {
        TargetRef active = targetCoordinator.getActiveTarget();
        if (active != null) transcriptRebuildService.rebuild(active);
      } catch (Exception ignored) {
      }

      SwingUtilities.invokeLater(() -> {
        try {
          rulesModel.setRules(next.rules());
          rulesTable.getSelectionModel().setSelectionInterval(newRow, newRow);
          rulesTable.scrollRectToVisible(rulesTable.getCellRect(newRow, 0, true));
          refreshRuleButtons.run();
        } catch (Exception ignored) {
        }
      });
    };

    Runnable moveSelectedRuleDown = () -> {
      int row = rulesTable.getSelectedRow();
      if (row < 0) return;
      if (row >= rulesModel.getRowCount() - 1) return;

      int newRow = row + 1;
      FilterSettings snap = filterSettingsBus.get();
      if (snap == null || snap.rules() == null) return;

      List<FilterRule> nextRules = new ArrayList<>(snap.rules());
      if (newRow >= nextRules.size()) return;
      java.util.Collections.swap(nextRules, row, newRow);

      FilterSettings next = new FilterSettings(
          snap.filtersEnabledByDefault(),
          snap.placeholdersEnabledByDefault(),
          snap.placeholdersCollapsedByDefault(),
          snap.placeholderMaxPreviewLines(),
          snap.placeholderMaxLinesPerRun(),
          snap.placeholderTooltipMaxTags(),
          snap.historyPlaceholderMaxRunsPerBatch(),
          snap.historyPlaceholdersEnabledByDefault(),
          nextRules,
          snap.overrides()
      );

      filterSettingsBus.set(next);
      runtimeConfig.rememberFilterRules(next.rules());

      try {
        TargetRef active = targetCoordinator.getActiveTarget();
        if (active != null) transcriptRebuildService.rebuild(active);
      } catch (Exception ignored) {
      }

      SwingUtilities.invokeLater(() -> {
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

    Runnable openEditRule = () -> {
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

      var edited = FilterRuleEntryDialog.open(dialog, "Edit Filter Rule", seed, reserved, seed.scopePattern());
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

      FilterSettings next = new FilterSettings(
          snap != null ? snap.filtersEnabledByDefault() : true,
          snap != null ? snap.placeholdersEnabledByDefault() : true,
          snap != null ? snap.placeholdersCollapsedByDefault() : true,
          snap != null ? snap.placeholderMaxPreviewLines() : 3,
          snap != null ? snap.placeholderMaxLinesPerRun() : 250,
          snap != null ? snap.placeholderTooltipMaxTags() : 12,
          snap != null ? snap.historyPlaceholderMaxRunsPerBatch() : 10,
          snap != null ? snap.historyPlaceholdersEnabledByDefault() : true,
          nextRules,
          snap != null ? snap.overrides() : List.of()
      );

      filterSettingsBus.set(next);
      runtimeConfig.rememberFilterRules(next.rules());

      // Rebuild active target so changes take effect immediately.
      try {
        TargetRef active = targetCoordinator.getActiveTarget();
        if (active != null) transcriptRebuildService.rebuild(active);
      } catch (Exception ignored) {
      }

      SwingUtilities.invokeLater(() -> {
        try {
          rulesModel.setRules(next.rules());
          // Re-select the edited rule.
          int idx = -1;
          for (int i = 0; i < next.rules().size(); i++) {
            FilterRule r = next.rules().get(i);
            if (r != null && edited.get().id() != null && edited.get().id().equals(r.id())) {
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

    deleteRule.addActionListener(e -> {
      int row = rulesTable.getSelectedRow();
      if (row < 0) return;
      FilterRule seed = rulesModel.ruleAt(row);
      if (seed == null) return;

      int confirm = JOptionPane.showConfirmDialog(
          dialog,
          "Delete filter rule '" + seed.name() + "'?",
          "Delete Filter Rule",
          JOptionPane.OK_CANCEL_OPTION
      );
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

      FilterSettings next = new FilterSettings(
          snap != null ? snap.filtersEnabledByDefault() : true,
          snap != null ? snap.placeholdersEnabledByDefault() : true,
          snap != null ? snap.placeholdersCollapsedByDefault() : true,
          snap != null ? snap.placeholderMaxPreviewLines() : 3,
          snap != null ? snap.placeholderMaxLinesPerRun() : 250,
          snap != null ? snap.placeholderTooltipMaxTags() : 12,
          snap != null ? snap.historyPlaceholderMaxRunsPerBatch() : 10,
          snap != null ? snap.historyPlaceholdersEnabledByDefault() : true,
          nextRules,
          snap != null ? snap.overrides() : List.of()
      );

      filterSettingsBus.set(next);
      runtimeConfig.rememberFilterRules(next.rules());

      // Rebuild active target so changes take effect immediately.
      try {
        TargetRef active = targetCoordinator.getActiveTarget();
        if (active != null) transcriptRebuildService.rebuild(active);
      } catch (Exception ignored) {
      }

      SwingUtilities.invokeLater(() -> {
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
    rulesTable.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
          openEditRule.run();
        }
      }
    });

    addRule.addActionListener(e -> {
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

      var created = FilterRuleEntryDialog.open(dialog, "Add Filter Rule", null, reserved, suggestedScope);
      if (created.isEmpty()) return;

      List<FilterRule> nextRules = new ArrayList<>();
      if (snap != null && snap.rules() != null) {
        nextRules.addAll(snap.rules());
      }
      nextRules.add(created.get());

      FilterSettings next = new FilterSettings(
          snap != null ? snap.filtersEnabledByDefault() : true,
          snap != null ? snap.placeholdersEnabledByDefault() : true,
          snap != null ? snap.placeholdersCollapsedByDefault() : true,
          snap != null ? snap.placeholderMaxPreviewLines() : 3,
          snap != null ? snap.placeholderMaxLinesPerRun() : 250,
          snap != null ? snap.placeholderTooltipMaxTags() : 12,
          snap != null ? snap.historyPlaceholderMaxRunsPerBatch() : 10,
          snap != null ? snap.historyPlaceholdersEnabledByDefault() : true,
          nextRules,
          snap != null ? snap.overrides() : List.of()
      );

      filterSettingsBus.set(next);
      runtimeConfig.rememberFilterRules(next.rules());

      // Rebuild active target so changes take effect immediately.
      try {
        TargetRef active = targetCoordinator.getActiveTarget();
        if (active != null) transcriptRebuildService.rebuild(active);
      } catch (Exception ignored) {
      }

      // Select the newly-added rule.
      SwingUtilities.invokeLater(() -> {
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
        rulesModel,
        rulesTable,
        addRule,
        editRule,
        deleteRule,
        moveRuleUp,
        moveRuleDown
    );
  }

  private JPanel buildFiltersPanel(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12", "[grow]", "[][grow]"));

    panel.add(tabTitle("Filters"), "growx, wrap");
    panel.add(new JLabel("Filters only affect transcript rendering; messages are still logged."), "growx, wrap 12");

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("General", buildFiltersGeneralTab(c));
    tabs.addTab("Placeholders", buildFiltersPlaceholdersTab(c));
    tabs.addTab("History", buildFiltersHistoryTab(c));
    tabs.addTab("Overrides", buildFiltersOverridesTab(c));
    tabs.addTab("Rules", buildFiltersRulesTab(c));

    panel.add(tabs, "grow");
    return panel;
  }

  private JPanel buildFiltersGeneralTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12", "[grow]", ""));

    panel.add(c.filtersEnabledByDefault, "growx, wrap 8");
    panel.add(new JLabel("When disabled, rules and placeholders are ignored unless a scope override enables them."), "growx, wrap");

    return panel;
  }

  private JPanel buildFiltersPlaceholdersTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12", "[grow]", ""));

    panel.add(c.placeholdersEnabledByDefault, "growx, wrap");
    panel.add(c.placeholdersCollapsedByDefault, "growx, wrap 12");

    JPanel previewRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    previewRow.add(new JLabel("Placeholder preview lines:"), "split 2");
    previewRow.add(c.placeholderPreviewLines, "w 80!");
    panel.add(previewRow, "growx, wrap");

    JPanel runCapRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    runCapRow.add(new JLabel("Max hidden lines per run:"), "split 2");
    runCapRow.add(c.placeholderMaxLinesPerRun, "w 80!");
    panel.add(runCapRow, "growx, wrap");
    panel.add(new JLabel("0 = unlimited. Prevents a single placeholder from representing an enormous filtered run."), "growx, wrap 12");

    JPanel tooltipTagsRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    tooltipTagsRow.add(new JLabel("Tooltip tag limit:"), "split 2");
    tooltipTagsRow.add(c.placeholderTooltipMaxTags, "w 80!");
    panel.add(tooltipTagsRow, "growx, wrap");
    panel.add(new JLabel("0 = hide tags in the tooltip (rule + count still shown)."), "growx, wrap");

    return panel;
  }

  private JPanel buildFiltersHistoryTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12", "[grow]", ""));

    panel.add(c.historyPlaceholdersEnabledByDefault, "growx, wrap 12");

    JPanel historyCapRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    historyCapRow.add(new JLabel("History placeholder run cap per batch:"), "split 2");
    historyCapRow.add(c.historyPlaceholderMaxRunsPerBatch, "w 80!");
    panel.add(historyCapRow, "growx, wrap");
    panel.add(new JLabel("0 = unlimited. Limits how many filtered placeholder/hint runs appear per history load."), "growx, wrap");

    return panel;
  }

  private JPanel buildFiltersOverridesTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12", "[grow]", ""));

    panel.add(new JLabel("Overrides apply by scope pattern. Most specific match wins."), "growx, wrap 12");

    JScrollPane tableScroll = new JScrollPane(c.overridesTable);
    tableScroll.setPreferredSize(new Dimension(520, 220));
    panel.add(tableScroll, "growx, wrap 8");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    buttons.add(c.addOverride);
    buttons.add(c.removeOverride);
    panel.add(buttons, "growx, wrap 8");

    panel.add(new JLabel("Tip: You can also manage overrides via /filter override ... and export with /filter export."), "growx, wrap");

    return panel;
  }

  private JPanel buildFiltersRulesTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12", "[grow]", ""));

    panel.add(new JLabel("Rules affect transcript rendering only (they do not prevent logging)."), "growx, wrap 12");

    JScrollPane rulesScroll = new JScrollPane(c.rulesTable);
    rulesScroll.setPreferredSize(new Dimension(760, 260));
    panel.add(rulesScroll, "growx, wrap 8");

    JPanel ruleButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    ruleButtons.add(c.addRule);
    ruleButtons.add(c.editRule);
    ruleButtons.add(c.deleteRule);
    ruleButtons.add(c.moveRuleUp);
    ruleButtons.add(c.moveRuleDown);
    panel.add(ruleButtons, "growx, wrap 8");

    panel.add(new JLabel("Tip: You can also manage rules via /filter add|del|set and export with /filter export."), "growx, wrap");

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

    boolean historyPlaceholdersEnabledByDefault = c.historyPlaceholdersEnabledByDefault.isSelected();

    int maxRunsPerBatch = ((Number) c.historyPlaceholderMaxRunsPerBatch.getValue()).intValue();
    if (maxRunsPerBatch < 0) maxRunsPerBatch = 0;
    if (maxRunsPerBatch > 5_000) maxRunsPerBatch = 5_000;

    List<FilterScopeOverride> overrides = c.overridesModel.toOverrides();

    FilterSettings next = new FilterSettings(
        enabledByDefault,
        placeholdersEnabledByDefault,
        placeholdersCollapsedByDefault,
        previewLines,
        maxLinesPerRun,
        tooltipMaxTags,
        maxRunsPerBatch,
        historyPlaceholdersEnabledByDefault,
        prev != null ? prev.rules() : List.of(),
        overrides
    );

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
    runtimeConfig.rememberFilterHistoryPlaceholdersEnabledByDefault(historyPlaceholdersEnabledByDefault);
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
