package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.DialogCloseableScopeDecorator;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JColorChooser;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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

  private JDialog dialog;

  public PreferencesDialog(UiSettingsBus settingsBus,
                           ThemeManager themeManager,
                           RuntimeConfigStore runtimeConfig,
                           LogProperties logProps) {
    this.settingsBus = settingsBus;
    this.themeManager = themeManager;
    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;
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

    // Theme choices
    Map<String, String> themeLabelById = new LinkedHashMap<>();
    for (ThemeManager.ThemeOption opt : themeManager.supportedThemes()) {
      themeLabelById.put(opt.id(), opt.label());
    }

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

    // Fonts
    String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Arrays.sort(families, String.CASE_INSENSITIVE_ORDER);
    JComboBox<String> fontFamily = new JComboBox<>(families);
    fontFamily.setEditable(true);
    fontFamily.setSelectedItem(current.chatFontFamily());

    JSpinner fontSize = new JSpinner(new SpinnerNumberModel(current.chatFontSize(), 8, 48, 1));
    final var fontSizeMouseWheelAC = MouseWheelDecorator.decorateNumberSpinner(fontSize);

    JCheckBox autoConnectOnStart = new JCheckBox("Auto-connect to servers on startup");
    autoConnectOnStart.setSelected(current.autoConnectOnStart());
    autoConnectOnStart.setToolTipText("If enabled, IRCafe will connect to all configured servers automatically after the UI loads.\n" +
        "If disabled, IRCafe starts disconnected and you can connect manually using the Connect button.");

    JCheckBox imageEmbeds = new JCheckBox("Enable inline image embeds (direct links)");
    imageEmbeds.setSelected(current.imageEmbedsEnabled());
    imageEmbeds.setToolTipText("If enabled, IRCafe will download and render images from direct image URLs in chat.");

    JCheckBox imageEmbedsCollapsed = new JCheckBox("Collapse inline images by default");
    imageEmbedsCollapsed.setSelected(current.imageEmbedsCollapsedByDefault());
    imageEmbedsCollapsed.setToolTipText("If enabled, newly inserted inline images start collapsed (header shown; click to expand).");
    imageEmbedsCollapsed.setEnabled(imageEmbeds.isSelected());

    // Max width cap (0 = no extra cap; images still scale to fit the viewport).
    JSpinner imageMaxWidth = new JSpinner(new SpinnerNumberModel(current.imageEmbedsMaxWidthPx(), 0, 4096, 10));
    final var imageMaxWidthMouseWheelAC = MouseWheelDecorator.decorateNumberSpinner(imageMaxWidth);
    imageMaxWidth.setToolTipText("Maximum width for inline images (pixels).\n" +
        "If 0, IRCafe will only scale images down to fit the chat viewport.");
    imageMaxWidth.setEnabled(imageEmbeds.isSelected());

    // Max height cap (0 = no extra cap; images still scale to fit the viewport / max-width cap).
    JSpinner imageMaxHeight = new JSpinner(new SpinnerNumberModel(current.imageEmbedsMaxHeightPx(), 0, 4096, 10));
    final var imageMaxHeightMouseWheelAC = MouseWheelDecorator.decorateNumberSpinner(imageMaxHeight);
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
    imagePanel.add(imageMaxWidth, "w 90!");
    imagePanel.add(new JLabel("Max image height (px, 0 = no limit):"));
    imagePanel.add(imageMaxHeight, "w 90!");
    imagePanel.add(animateGifs, "span 2, wrap");

    JCheckBox linkPreviews = new JCheckBox("Enable link previews (OpenGraph cards)");
    linkPreviews.setSelected(current.linkPreviewsEnabled());
    linkPreviews.setToolTipText("If enabled, IRCafe will fetch page metadata (title/description/image) and show a preview card under messages.\nNote: this makes network requests to the linked sites.");

    JCheckBox linkPreviewsCollapsed = new JCheckBox("Collapse link previews by default");
    linkPreviewsCollapsed.setSelected(current.linkPreviewsCollapsedByDefault());
    linkPreviewsCollapsed.setToolTipText("If enabled, newly inserted link previews start collapsed (header shown; click to expand).");
    linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected());
    linkPreviews.addActionListener(e -> linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected()));

    JPanel linkPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]4[]"));
    linkPanel.setOpaque(false);
    linkPanel.add(linkPreviews);
    linkPanel.add(linkPreviewsCollapsed);

    JCheckBox chatTimestamps = new JCheckBox("Show timestamps on chat messages");
    chatTimestamps.setSelected(current.chatMessageTimestampsEnabled());
    chatTimestamps.setToolTipText("If enabled, IRCafe will prepend a timestamp to each regular user message line.");

    // ---- Chat history / paging (UI-level settings; stored under ircafe.ui.*) ----
    JSpinner historyInitialLoadLines = new JSpinner(new SpinnerNumberModel(current.chatHistoryInitialLoadLines(), 0, 10_000, 50));
    historyInitialLoadLines.setToolTipText("How many logged lines to prefill into a transcript when you select a channel/query.\n" +
        "Set to 0 to disable history prefill.");
    final var historyInitialLoadLinesAC = MouseWheelDecorator.decorateNumberSpinner(historyInitialLoadLines);

    JSpinner historyPageSize = new JSpinner(new SpinnerNumberModel(current.chatHistoryPageSize(), 50, 10_000, 50));
    historyPageSize.setToolTipText("How many lines to fetch per click when you use 'Load older messages…' inside the transcript.");
    final var historyPageSizeAC = MouseWheelDecorator.decorateNumberSpinner(historyPageSize);

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

    Runnable updateLoggingEnabledState = () -> {
      boolean en = loggingEnabled.isSelected();
      loggingSoftIgnore.setEnabled(en);
      // The history settings are meaningful only when logging is enabled, but users may want to pre-configure.
      // We leave them enabled; the info text communicates that logging must be enabled.
      dbBaseName.setEnabled(true);
      dbNextToConfig.setEnabled(true);
    };
    loggingEnabled.addActionListener(e -> updateLoggingEnabledState.run());
    updateLoggingEnabledState.run();

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

    // ---- Hostmask discovery / USERHOST anti-flood settings ----
    JPanel userhostPanel = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]8[]8[]8[]"));

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

    JSpinner userhostMinIntervalSeconds = new JSpinner(new SpinnerNumberModel(current.userhostMinIntervalSeconds(), 1, 60, 1));
    userhostMinIntervalSeconds.setToolTipText("Minimum seconds between USERHOST commands per server.");
    AutoCloseable userhostMinIntervalAC = MouseWheelDecorator.decorateNumberSpinner(userhostMinIntervalSeconds);

    JSpinner userhostMaxPerMinute = new JSpinner(new SpinnerNumberModel(current.userhostMaxCommandsPerMinute(), 1, 60, 1));
    userhostMaxPerMinute.setToolTipText("Maximum USERHOST commands per minute per server.");
    AutoCloseable userhostMaxPerMinuteAC = MouseWheelDecorator.decorateNumberSpinner(userhostMaxPerMinute);

    JSpinner userhostNickCooldownMinutes = new JSpinner(new SpinnerNumberModel(current.userhostNickCooldownMinutes(), 1, 240, 1));
    userhostNickCooldownMinutes.setToolTipText("Cooldown in minutes before re-querying the same nick.");
    AutoCloseable userhostNickCooldownAC = MouseWheelDecorator.decorateNumberSpinner(userhostNickCooldownMinutes);

    JSpinner userhostMaxNicksPerCommand = new JSpinner(new SpinnerNumberModel(current.userhostMaxNicksPerCommand(), 1, 5, 1));
    userhostMaxNicksPerCommand.setToolTipText("How many nicks to include per USERHOST command (servers typically allow up to 5).");
    AutoCloseable userhostMaxNicksPerCommandAC = MouseWheelDecorator.decorateNumberSpinner(userhostMaxNicksPerCommand);

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
    userhostPanel.add(userhostMinIntervalSeconds, "w 90!");
    userhostPanel.add(new JLabel("Max commands/min:"));
    userhostPanel.add(userhostMaxPerMinute, "w 90!");
    userhostPanel.add(new JLabel("Nick cooldown (min):"));
    userhostPanel.add(userhostNickCooldownMinutes, "w 90!");
    userhostPanel.add(new JLabel("Max nicks/command:"));
    userhostPanel.add(userhostMaxNicksPerCommand, "w 90!");

    // Layout (MiGLayout avoids the fragile GridBag sizing issues)
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]8[]8[]8[]8[]"));
    form.add(new JLabel("Theme"));
    form.add(theme, "growx");

    form.add(new JLabel("Chat font"));
    form.add(fontFamily, "growx");

    form.add(new JLabel("Chat font size"));
    form.add(fontSize, "w 90!");

    form.add(new JLabel("Startup"));
    form.add(autoConnectOnStart);

    form.add(new JLabel("Inline images"), "aligny top");
    form.add(imagePanel, "growx");

    form.add(new JLabel("Link previews"), "aligny top");
    form.add(linkPanel, "growx");

    form.add(new JLabel("Chat timestamps"));
    form.add(chatTimestamps);

    form.add(new JLabel("Outgoing messages"), "aligny top");
    form.add(outgoingColorPanel, "growx");

    // Logging tab panel (separate tab by request)
    // Keep labels right-aligned (common form pattern), but ensure checkboxes spanning both columns are left-aligned.
    JPanel loggingPanel = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]8[]8[]8[]"));
    loggingPanel.add(loggingInfo, "span 2, growx, wrap");
    loggingPanel.add(loggingEnabled, "span 2, alignx left, wrap");
    loggingPanel.add(loggingSoftIgnore, "span 2, alignx left, wrap");
    loggingPanel.add(new JLabel("DB file base name:"));
    loggingPanel.add(dbBaseName, "w 240!");
    loggingPanel.add(new JLabel("DB location:"));
    loggingPanel.add(dbNextToConfig, "alignx left, wrap");
    loggingPanel.add(new JLabel("History paging"), "aligny top");
    loggingPanel.add(historyPanel, "growx");

    JButton apply = new JButton("Apply");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");

    Runnable doApply = () -> {
      String t = String.valueOf(theme.getSelectedItem());
      String fam = String.valueOf(fontFamily.getSelectedItem());
      int size = ((Number) fontSize.getValue()).intValue();
      boolean autoConnectV = autoConnectOnStart.isSelected();
      int maxImageW = ((Number) imageMaxWidth.getValue()).intValue();
      int maxImageH = ((Number) imageMaxHeight.getValue()).intValue();

      int historyInitialLoadV = ((Number) historyInitialLoadLines.getValue()).intValue();
      int historyPageSizeV = ((Number) historyPageSize.getValue()).intValue();

      boolean userhostEnabledV = userhostEnabled.isSelected();
      int userhostMinIntervalV = ((Number) userhostMinIntervalSeconds.getValue()).intValue();
      int userhostMaxPerMinuteV = ((Number) userhostMaxPerMinute.getValue()).intValue();
      int userhostNickCooldownV = ((Number) userhostNickCooldownMinutes.getValue()).intValue();
      int userhostMaxNicksV = ((Number) userhostMaxNicksPerCommand.getValue()).intValue();

      UiSettings prev = settingsBus.get();
      boolean outgoingColorEnabledV = outgoingColorEnabled.isSelected();
      String outgoingHexV = UiSettings.normalizeHexOrDefault(outgoingColorHex.getText(), prev.clientLineColor());
      // Normalize the text field to a canonical #RRGGBB form.
      outgoingColorHex.setText(outgoingHexV);

      UiSettings next = new UiSettings(
          t,
          fam,
          size,
          autoConnectV,
          imageEmbeds.isSelected(),
          imageEmbedsCollapsed.isSelected(),
          maxImageW,
          maxImageH,
          animateGifs.isSelected(),
          linkPreviews.isSelected(),
          linkPreviewsCollapsed.isSelected(),
          prev.presenceFoldsEnabled(),
          chatTimestamps.isSelected(),
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
      runtimeConfig.rememberChatMessageTimestampsEnabled(next.chatMessageTimestampsEnabled());

      runtimeConfig.rememberChatHistoryInitialLoadLines(next.chatHistoryInitialLoadLines());
      runtimeConfig.rememberChatHistoryPageSize(next.chatHistoryPageSize());

      // Logging settings (take effect on next restart)
      runtimeConfig.rememberChatLoggingEnabled(loggingEnabled.isSelected());
      runtimeConfig.rememberChatLoggingLogSoftIgnoredLines(loggingSoftIgnore.isSelected());
      runtimeConfig.rememberChatLoggingDbFileBaseName(dbBaseName.getText());
      runtimeConfig.rememberChatLoggingDbNextToRuntimeConfig(dbNextToConfig.isSelected());

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
    final JDialog d = new JDialog(owner, "Preferences", JDialog.ModalityType.APPLICATION_MODAL);
    d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    this.dialog = d;

    final CloseableScope scope = DialogCloseableScopeDecorator.install(d);
    scope.add(fontSizeMouseWheelAC);
    scope.add(imageMaxWidthMouseWheelAC);
    scope.add(imageMaxHeightMouseWheelAC);
    scope.add(historyInitialLoadLinesAC);
    scope.add(historyPageSizeAC);
    scope.add(userhostMinIntervalAC);
    scope.add(userhostMaxPerMinuteAC);
    scope.add(userhostNickCooldownAC);
    scope.add(userhostMaxNicksPerCommandAC);
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
    JScrollPane generalScroll = new JScrollPane(form);
    generalScroll.setBorder(null);
    tabs.addTab("General", generalScroll);

    JScrollPane loggingScroll = new JScrollPane(loggingPanel);
    loggingScroll.setBorder(null);
    tabs.addTab("Logging", loggingScroll);

    JScrollPane userhostScroll = new JScrollPane(userhostPanel);
    userhostScroll.setBorder(null);
    tabs.addTab("Hostmask discovery", userhostScroll);

    d.setLayout(new BorderLayout());
    d.add(tabs, BorderLayout.CENTER);
    d.add(buttons, BorderLayout.SOUTH);
    d.setMinimumSize(new Dimension(520, 340));
    d.pack();
    d.setLocationRelativeTo(owner);
    d.setVisible(true);
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
