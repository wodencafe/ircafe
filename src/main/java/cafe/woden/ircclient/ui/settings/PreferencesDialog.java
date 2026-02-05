package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettings;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.DialogCloseableScopeDecorator;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JColorChooser;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class PreferencesDialog {

  private final UiSettingsBus settingsBus;
  private final ThemeManager themeManager;
  private final RuntimeConfigStore runtimeConfig;
  private final LogProperties logProps;
  private final NickColorSettingsBus nickColorSettingsBus;
  private final NickColorService nickColorService;
  private final NickColorOverridesDialog nickColorOverridesDialog;

  private JDialog dialog;

  public PreferencesDialog(UiSettingsBus settingsBus,
                           ThemeManager themeManager,
                           RuntimeConfigStore runtimeConfig,
                           LogProperties logProps,
                           NickColorSettingsBus nickColorSettingsBus,
                           NickColorService nickColorService,
                           NickColorOverridesDialog nickColorOverridesDialog) {
    this.settingsBus = settingsBus;
    this.themeManager = themeManager;
    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;
    this.nickColorSettingsBus = nickColorSettingsBus;
    this.nickColorService = nickColorService;
    this.nickColorOverridesDialog = nickColorOverridesDialog;
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
    JCheckBox autoConnectOnStart = buildAutoConnectCheckbox(current);

    ImageEmbedControls imageEmbeds = buildImageEmbedControls(current, closeables);
    LinkPreviewControls linkPreviews = buildLinkPreviewControls(current);
    TimestampControls timestamps = buildTimestampControls(current);

    JCheckBox presenceFolds = buildPresenceFoldsCheckbox(current);
    JCheckBox ctcpRequestsInActiveTarget = buildCtcpRequestsInActiveTargetCheckbox(current);
    NickColorControls nickColors = buildNickColorControls(owner, closeables);

    HistoryControls history = buildHistoryControls(current, closeables);
    LoggingControls logging = buildLoggingControls(logProps, closeables);

    OutgoingColorControls outgoing = buildOutgoingColorControls(current);
    UserhostControls userhost = buildUserhostControls(current, closeables);

    JPanel appearancePanel = buildAppearancePanel(theme, fonts);
    JPanel startupPanel = buildStartupPanel(autoConnectOnStart);
    JPanel chatPanel = buildChatPanel(presenceFolds, ctcpRequestsInActiveTarget, nickColors, timestamps, outgoing);
    JPanel embedsPanel = buildEmbedsAndPreviewsPanel(imageEmbeds, linkPreviews);
    JPanel historyStoragePanel = buildHistoryAndStoragePanel(logging, history);
    JPanel userhostPanel = userhost.panel;

    JButton apply = new JButton("Apply");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");

    Runnable doApply = () -> {
      String t = String.valueOf(theme.combo.getSelectedItem());
      String fam = String.valueOf(fonts.fontFamily.getSelectedItem());
      int size = ((Number) fonts.fontSize.getValue()).intValue();
      boolean autoConnectV = autoConnectOnStart.isSelected();

      boolean timestampsEnabledV = timestamps.enabled.isSelected();
      boolean timestampsIncludeChatMessagesV = timestamps.includeChatMessages.isSelected();
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
      // Normalize what we persist (trimmed + non-blank default)
      timestamps.format.setText(timestampFormatV);

      boolean presenceFoldsV = presenceFolds.isSelected();
      boolean ctcpRequestsInActiveTargetV = ctcpRequestsInActiveTarget.isSelected();

      boolean nickColoringEnabledV = nickColors.enabled.isSelected();
      double nickColorMinContrastV = ((Number) nickColors.minContrast.getValue()).doubleValue();
      if (nickColorMinContrastV <= 0) nickColorMinContrastV = 3.0;

      int maxImageW = ((Number) imageEmbeds.maxWidth.getValue()).intValue();
      int maxImageH = ((Number) imageEmbeds.maxHeight.getValue()).intValue();

      int historyInitialLoadV = ((Number) history.initialLoadLines.getValue()).intValue();
      int historyPageSizeV = ((Number) history.pageSize.getValue()).intValue();

      boolean userhostEnabledV = userhost.enabled.isSelected();
      int userhostMinIntervalV = ((Number) userhost.minIntervalSeconds.getValue()).intValue();
      int userhostMaxPerMinuteV = ((Number) userhost.maxPerMinute.getValue()).intValue();
      int userhostNickCooldownV = ((Number) userhost.nickCooldownMinutes.getValue()).intValue();
      int userhostMaxNicksV = ((Number) userhost.maxNicksPerCommand.getValue()).intValue();

      UiSettings prev = settingsBus.get();
      boolean outgoingColorEnabledV = outgoing.enabled.isSelected();
      String outgoingHexV = UiSettings.normalizeHexOrDefault(outgoing.hex.getText(), prev.clientLineColor());
      // Normalize the text field to a canonical #RRGGBB form.
      outgoing.hex.setText(outgoingHexV);

      UiSettings next = new UiSettings(
          t,
          fam,
          size,
          autoConnectV,
          imageEmbeds.enabled.isSelected(),
          imageEmbeds.collapsed.isSelected(),
          maxImageW,
          maxImageH,
          imageEmbeds.animateGifs.isSelected(),
          linkPreviews.enabled.isSelected(),
          linkPreviews.collapsed.isSelected(),
          presenceFoldsV,
          ctcpRequestsInActiveTargetV,
          timestampsEnabledV,
          timestampFormatV,
          timestampsIncludeChatMessagesV,
          historyInitialLoadV,
          historyPageSizeV,
          outgoingColorEnabledV,
          outgoingHexV,
          userhostEnabledV,
          userhostMinIntervalV,
          userhostMaxPerMinuteV,
          userhostNickCooldownV,
          userhostMaxNicksV
      );

      boolean themeChanged = !next.theme().equalsIgnoreCase(prev.theme());

      settingsBus.set(next);
      runtimeConfig.rememberUiSettings(next.theme(), next.chatFontFamily(), next.chatFontSize());
      runtimeConfig.rememberAutoConnectOnStart(next.autoConnectOnStart());
      runtimeConfig.rememberImageEmbedsEnabled(next.imageEmbedsEnabled());
      runtimeConfig.rememberImageEmbedsCollapsedByDefault(next.imageEmbedsCollapsedByDefault());
      runtimeConfig.rememberImageEmbedsMaxWidthPx(next.imageEmbedsMaxWidthPx());
      runtimeConfig.rememberImageEmbedsMaxHeightPx(next.imageEmbedsMaxHeightPx());
      runtimeConfig.rememberImageEmbedsAnimateGifs(next.imageEmbedsAnimateGifs());
      runtimeConfig.rememberLinkPreviewsEnabled(next.linkPreviewsEnabled());
      runtimeConfig.rememberLinkPreviewsCollapsedByDefault(next.linkPreviewsCollapsedByDefault());
      runtimeConfig.rememberPresenceFoldsEnabled(next.presenceFoldsEnabled());
      runtimeConfig.rememberCtcpRequestsInActiveTargetEnabled(next.ctcpRequestsInActiveTargetEnabled());

      if (nickColorSettingsBus != null) {
        nickColorSettingsBus.set(new NickColorSettings(nickColoringEnabledV, nickColorMinContrastV));
      }
      runtimeConfig.rememberNickColoringEnabled(nickColoringEnabledV);
      runtimeConfig.rememberNickColorMinContrast(nickColorMinContrastV);
      runtimeConfig.rememberTimestampsEnabled(next.timestampsEnabled());
      runtimeConfig.rememberTimestampFormat(next.timestampFormat());
      runtimeConfig.rememberTimestampsIncludeChatMessages(next.timestampsIncludeChatMessages());

      runtimeConfig.rememberChatHistoryInitialLoadLines(next.chatHistoryInitialLoadLines());
      runtimeConfig.rememberChatHistoryPageSize(next.chatHistoryPageSize());

      // Logging settings (take effect on next restart)
      runtimeConfig.rememberChatLoggingEnabled(logging.enabled.isSelected());
      runtimeConfig.rememberChatLoggingLogSoftIgnoredLines(logging.logSoftIgnored.isSelected());
      runtimeConfig.rememberChatLoggingDbFileBaseName(logging.dbBaseName.getText());
      runtimeConfig.rememberChatLoggingDbNextToRuntimeConfig(logging.dbNextToConfig.isSelected());

      runtimeConfig.rememberChatLoggingKeepForever(logging.keepForever.isSelected());
      runtimeConfig.rememberChatLoggingRetentionDays(((Number) logging.retentionDays.getValue()).intValue());

      runtimeConfig.rememberClientLineColorEnabled(next.clientLineColorEnabled());
      runtimeConfig.rememberClientLineColor(next.clientLineColor());

      runtimeConfig.rememberUserhostDiscoveryEnabled(next.userhostDiscoveryEnabled());
      runtimeConfig.rememberUserhostMinIntervalSeconds(next.userhostMinIntervalSeconds());
      runtimeConfig.rememberUserhostMaxCommandsPerMinute(next.userhostMaxCommandsPerMinute());
      runtimeConfig.rememberUserhostNickCooldownMinutes(next.userhostNickCooldownMinutes());
      runtimeConfig.rememberUserhostMaxNicksPerCommand(next.userhostMaxNicksPerCommand());

      if (themeChanged) {
        themeManager.applyTheme(next.theme());
      }
    };

    apply.addActionListener(e -> doApply.run());

    // Construct dialog early so we can attach a CloseableScope that cleans up decorators/listeners
    // regardless of exit path (OK, Cancel, window close).
    final JDialog d = createDialog(owner);
    this.dialog = d;

    final CloseableScope scope = DialogCloseableScopeDecorator.install(d);
    closeables.forEach(scope::add);
    scope.addCleanup(() -> {
      if (this.dialog == d) this.dialog = null;
    });

    ok.addActionListener(e -> {
      doApply.run();
      d.dispose();
    });
    cancel.addActionListener(e -> d.dispose());

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(apply);
    buttons.add(ok);
    buttons.add(cancel);

    // Tabs
    JTabbedPane tabs = new JTabbedPane();

    // Step 2 scaffolding: add the future tab layout, while leaving the current tabs intact.
    // We'll migrate settings into these tabs step-by-step.
    tabs.addTab("Appearance", wrapTab(appearancePanel));
    tabs.addTab("Startup & Connection", wrapTab(startupPanel));
    tabs.addTab("Chat", wrapTab(chatPanel));
    tabs.addTab("Embeds & Previews", wrapTab(embedsPanel));
    tabs.addTab("History & Storage", wrapTab(historyStoragePanel));
    tabs.addTab("Network / Advanced", wrapTab(userhostPanel));

    d.setLayout(new BorderLayout());
    d.add(tabs, BorderLayout.CENTER);
    d.add(buttons, BorderLayout.SOUTH);
    d.setMinimumSize(new Dimension(520, 340));
    d.pack();
    d.setLocationRelativeTo(owner);
    d.setVisible(true);
  }

  private Map<String, String> buildThemeLabelById() {
    Map<String, String> themeLabelById = new LinkedHashMap<>();
    for (ThemeManager.ThemeOption opt : themeManager.supportedThemes()) {
      themeLabelById.put(opt.id(), opt.label());
    }
    return themeLabelById;
  }

  private static JScrollPane wrapTab(JPanel panel) {
    JScrollPane scroll = new JScrollPane(
        panel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    );
    scroll.setBorder(null);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    return scroll;
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
    t.setColumns(48);
    return t;
  }

  private void addPlaceholderTab(JTabbedPane tabs, String title, String message) {
    JPanel placeholder = new JPanel(new MigLayout("insets 12, fill", "[grow]", "[]12[grow]"));

    JLabel header = new JLabel(title);
    header.putClientProperty(FlatClientProperties.STYLE, "font:+4");

    JTextArea body = new JTextArea(message + "\n\n" +
        "This tab is a placeholder (Step 2). We'll move the actual controls here in later steps.");
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
    JComboBox<String> theme = new JComboBox<>(themeLabelById.keySet().toArray(String[]::new));
    theme.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
      JLabel l = new JLabel(themeLabelById.getOrDefault(value, value));
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
    theme.setSelectedItem(current.theme());
    return new ThemeControls(theme);
  }

  private FontControls buildFontControls(UiSettings current, List<AutoCloseable> closeables) {
    String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Arrays.sort(families, String.CASE_INSENSITIVE_ORDER);

    JComboBox<String> fontFamily = new JComboBox<>(families);
    fontFamily.setEditable(true);
    fontFamily.setSelectedItem(current.chatFontFamily());

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
      // If overrides were changed, refresh the preview (even though samples may not be overridden).
      updatePreview.run();
    });

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]8[nogrid]", "[]6[]6[]6[]"));
    panel.setOpaque(false);
    panel.add(enabled, "span 2, wrap");
    panel.add(new JLabel("Minimum contrast ratio:"));
    panel.add(minContrast, "w 110!, wrap");
    panel.add(overrides, "span 2, alignx left, wrap");
    panel.add(helpText("Tip: If nick colors look too similar to the background, increase the contrast ratio.\n" +
        "Overrides always win over the palette."), "span 2, growx, wrap");
    panel.add(new JLabel("Preview:"), "span 2, wrap");
    panel.add(preview, "span 2, growx");

    // Seed preview + enabled state.
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

    // Max width cap (0 = no extra cap; images still scale to fit the viewport).
    JSpinner imageMaxWidth = numberSpinner(current.imageEmbedsMaxWidthPx(), 0, 4096, 10, closeables);
    imageMaxWidth.setToolTipText("Maximum width for inline images (pixels).\n" +
        "If 0, IRCafe will only scale images down to fit the chat viewport.");
    imageMaxWidth.setEnabled(imageEmbeds.isSelected());

    // Max height cap (0 = no extra cap; images still scale to fit the viewport / max-width cap).
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

    Runnable syncEnabled = () -> {
      boolean on = enabled.isSelected();
      format.setEnabled(on);
      includeChatMessages.setEnabled(on);
    };
    enabled.addActionListener(e -> syncEnabled.run());
    syncEnabled.run();

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]6[]"));
    panel.setOpaque(false);
    panel.add(enabled);
    JPanel fmtRow = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[][grow,fill]", "[]"));
    fmtRow.setOpaque(false);
    fmtRow.add(new JLabel("Format"));
    fmtRow.add(format, "w 200!");
    panel.add(fmtRow);
    panel.add(includeChatMessages);

    return new TimestampControls(enabled, format, includeChatMessages, panel);
  }

  private HistoryControls buildHistoryControls(UiSettings current, List<AutoCloseable> closeables) {
    JSpinner historyInitialLoadLines = numberSpinner(current.chatHistoryInitialLoadLines(), 0, 10_000, 50, closeables);
    historyInitialLoadLines.setToolTipText("How many logged lines to prefill into a transcript when you select a channel/query.\n" +
        "Set to 0 to disable history prefill.");

    JSpinner historyPageSize = numberSpinner(current.chatHistoryPageSize(), 50, 10_000, 50, closeables);
    historyPageSize.setToolTipText("How many lines to fetch per click when you use 'Load older messages…' inside the transcript.");

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

    JPanel historyPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]"));
    historyPanel.setOpaque(false);
    historyPanel.add(historyInfo, "span 2, growx, wrap");
    historyPanel.add(new JLabel("Initial load (lines):"));
    historyPanel.add(historyInitialLoadLines, "w 110!");
    historyPanel.add(new JLabel("Page size (Load older):"));
    historyPanel.add(historyPageSize, "w 110!");

    return new HistoryControls(historyInitialLoadLines, historyPageSize, historyPanel);
  }

  private LoggingControls buildLoggingControls(LogProperties logProps, List<AutoCloseable> closeables) {
    // ---- Logging (persisted under ircafe.logging.*; takes effect on next restart) ----
    boolean loggingEnabledCurrent = logProps != null && Boolean.TRUE.equals(logProps.enabled());
    boolean logSoftIgnoredCurrent = logProps == null || Boolean.TRUE.equals(logProps.logSoftIgnoredLines());

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
      // The history settings are meaningful only when logging is enabled, but users may want to pre-configure.
      // We leave them enabled; the info text communicates that logging must be enabled.
      dbBaseName.setEnabled(true);
      dbNextToConfig.setEnabled(true);

      // Retention UI state
      updateRetentionUi.run();
    };
    loggingEnabled.addActionListener(e -> updateLoggingEnabledState.run());
    updateLoggingEnabledState.run();

    return new LoggingControls(loggingEnabled, loggingSoftIgnore, keepForever, retentionDays, dbBaseName, dbNextToConfig, loggingInfo);
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
      if (currentColor == null) currentColor = Color.WHITE;
      Color chosen = JColorChooser.showDialog(dialog, "Choose Outgoing Message Color", currentColor);
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

  private UserhostControls buildUserhostControls(UiSettings current, List<AutoCloseable> closeables) {
    // ---- Hostmask discovery / USERHOST anti-flood settings ----
    JPanel userhostPanel = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]6[]6[]6[]"));

    userhostPanel.add(tabTitle("Network / Advanced"), "span 2, growx, wrap");
    userhostPanel.add(sectionTitle("Hostmask discovery"), "span 2, growx, wrap");

    JTextArea userhostInfo = new JTextArea(
        "IRCafe prefers IRCv3 userhost-in-names (free). " +
            "If hostmasks are still missing and you have hostmask-based ignore rules, IRCafe can " +
            "use USERHOST carefully with conservative rate limits."
    );
    userhostInfo.setEditable(false);
    userhostInfo.setLineWrap(true);
    userhostInfo.setWrapStyleWord(true);
    userhostInfo.setOpaque(false);
    userhostInfo.setFocusable(false);
    userhostInfo.setBorder(null);
    userhostInfo.setFont(UIManager.getFont("Label.font"));
    userhostInfo.setForeground(UIManager.getColor("Label.foreground"));
    // Keep the dialog from packing excessively wide; MigLayout will allow it to grow if needed.
    userhostInfo.setColumns(48);

    JCheckBox userhostEnabled = new JCheckBox("Resolve missing hostmasks using USERHOST (rate-limited)");
    userhostEnabled.setSelected(current.userhostDiscoveryEnabled());
    userhostEnabled.setToolTipText("When enabled, IRCafe may send USERHOST only when hostmask-based ignore rules exist and some nicks are missing hostmasks.");

    JSpinner userhostMinIntervalSeconds = numberSpinner(current.userhostMinIntervalSeconds(), 1, 60, 1, closeables);
    userhostMinIntervalSeconds.setToolTipText("Minimum seconds between USERHOST commands per server.");

    JSpinner userhostMaxPerMinute = numberSpinner(current.userhostMaxCommandsPerMinute(), 1, 60, 1, closeables);
    userhostMaxPerMinute.setToolTipText("Maximum USERHOST commands per minute per server.");

    JSpinner userhostNickCooldownMinutes = numberSpinner(current.userhostNickCooldownMinutes(), 1, 240, 1, closeables);
    userhostNickCooldownMinutes.setToolTipText("Cooldown in minutes before re-querying the same nick.");

    JSpinner userhostMaxNicksPerCommand = numberSpinner(current.userhostMaxNicksPerCommand(), 1, 5, 1, closeables);
    userhostMaxNicksPerCommand.setToolTipText("How many nicks to include per USERHOST command (servers typically allow up to 5).");

    Runnable updateUserhostEnabledState = () -> {
      boolean enabled = userhostEnabled.isSelected();
      userhostMinIntervalSeconds.setEnabled(enabled);
      userhostMaxPerMinute.setEnabled(enabled);
      userhostNickCooldownMinutes.setEnabled(enabled);
      userhostMaxNicksPerCommand.setEnabled(enabled);
    };
    userhostEnabled.addActionListener(e -> updateUserhostEnabledState.run());
    updateUserhostEnabledState.run();

    userhostPanel.add(userhostInfo, "span 2, growx, wrap");
    userhostPanel.add(userhostEnabled, "span 2, wrap");
    userhostPanel.add(new JLabel("Min interval (sec):"));
    userhostPanel.add(userhostMinIntervalSeconds, "w 110!");
    userhostPanel.add(new JLabel("Max commands/min:"));
    userhostPanel.add(userhostMaxPerMinute, "w 110!");
    userhostPanel.add(new JLabel("Nick cooldown (min):"));
    userhostPanel.add(userhostNickCooldownMinutes, "w 110!");
    userhostPanel.add(new JLabel("Max nicks/command:"));
    userhostPanel.add(userhostMaxNicksPerCommand, "w 110!");

    return new UserhostControls(userhostEnabled, userhostMinIntervalSeconds, userhostMaxPerMinute, userhostNickCooldownMinutes, userhostMaxNicksPerCommand, userhostPanel);
  }

  private JPanel buildAppearancePanel(ThemeControls theme, FontControls fonts) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]6[]10[]6[]6[]"));

    form.add(tabTitle("Appearance"), "span 2, growx, wrap");
    form.add(sectionTitle("Look & feel"), "span 2, growx, wrap");
    form.add(new JLabel("Theme"));
    form.add(theme.combo, "growx");

    form.add(sectionTitle("Chat text"), "span 2, growx, wrap");
    form.add(new JLabel("Font family"));
    form.add(fonts.fontFamily, "growx");
    form.add(new JLabel("Font size"));
    form.add(fonts.fontSize, "w 110!");

    return form;
  }

  private JPanel buildStartupPanel(JCheckBox autoConnectOnStart) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]10[]6[]"));
    form.add(tabTitle("Startup & Connection"), "growx, wrap");
    form.add(sectionTitle("On launch"), "growx, wrap");
    form.add(autoConnectOnStart, "growx, wrap");
    form.add(helpText("If enabled, IRCafe will connect to all configured servers automatically after the UI loads."), "growx");
    return form;
  }

  private JPanel buildChatPanel(JCheckBox presenceFolds,
                               JCheckBox ctcpRequestsInActiveTarget,
                               NickColorControls nickColors,
                               TimestampControls timestamps,
                               OutgoingColorControls outgoing) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]"));

    form.add(tabTitle("Chat"), "span 2, growx, wrap");
    form.add(sectionTitle("Display"), "span 2, growx, wrap");
    form.add(new JLabel("Presence events"), "aligny top");
    form.add(presenceFolds, "alignx left");

    form.add(new JLabel("CTCP requests"), "aligny top");
    form.add(ctcpRequestsInActiveTarget, "alignx left");

    form.add(new JLabel("Nick colors"), "aligny top");
    form.add(nickColors.panel, "growx");

    form.add(new JLabel("Timestamps"), "aligny top");
    form.add(timestamps.panel, "growx");

    form.add(sectionTitle("Your messages"), "span 2, growx, wrap");
    form.add(new JLabel("Outgoing messages"), "aligny top");
    form.add(outgoing.panel, "growx");

    return form;
  }

  private JPanel buildEmbedsAndPreviewsPanel(ImageEmbedControls image,
                                            LinkPreviewControls links) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]"));

    form.add(tabTitle("Embeds & Previews"), "span 2, growx, wrap");
    form.add(sectionTitle("Inline images"), "span 2, growx, wrap");
    form.add(new JLabel("Direct image links"), "aligny top");
    form.add(image.panel, "growx");

    form.add(sectionTitle("Link previews"), "span 2, growx, wrap");
    form.add(new JLabel("OpenGraph cards"), "aligny top");
    form.add(links.panel, "growx");

    return form;
  }

  private JPanel buildHistoryAndStoragePanel(LoggingControls logging, HistoryControls history) {
    // History & Storage tab panel (history paging + logging/DB settings)
    // Keep labels right-aligned (common form pattern), but ensure checkboxes spanning both columns are left-aligned.
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]6[]6[]6[]10[]6[]"));

    panel.add(tabTitle("History & Storage"), "span 2, growx, wrap");

    panel.add(sectionTitle("Logging"), "span 2, growx, wrap");
    panel.add(logging.info, "span 2, growx, wrap");
    panel.add(logging.enabled, "span 2, alignx left, wrap");
    panel.add(logging.logSoftIgnored, "span 2, alignx left, wrap");
    panel.add(logging.keepForever, "span 2, alignx left, wrap");
    panel.add(new JLabel("Retention (days)"));
    panel.add(logging.retentionDays, "w 110!, wrap");
    panel.add(new JLabel("DB file base name"));
    panel.add(logging.dbBaseName, "w 260!");
    panel.add(new JLabel("DB location"));
    panel.add(logging.dbNextToConfig, "alignx left, wrap");

    panel.add(sectionTitle("History paging"), "span 2, growx, wrap");
    panel.add(history.panel, "span 2, growx");

    return panel;
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

  private record ThemeControls(JComboBox<String> combo) {
  }

  private record FontControls(JComboBox<String> fontFamily, JSpinner fontSize) {
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

  private record NickColorControls(JCheckBox enabled,
                                  JSpinner minContrast,
                                  JButton overrides,
                                  NickColorPreviewPanel preview,
                                  JPanel panel) {
  }

  private record TimestampControls(JCheckBox enabled, JTextField format, JCheckBox includeChatMessages, JPanel panel) {
  }
  private record HistoryControls(JSpinner initialLoadLines, JSpinner pageSize, JPanel panel) {
  }

  private record LoggingControls(JCheckBox enabled,
                                JCheckBox logSoftIgnored,
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
                                 JSpinner maxNicksPerCommand,
                                 JPanel panel) {
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

}
