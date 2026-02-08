package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.NetHeartbeatContext;
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
import javax.swing.JComponent;
import javax.swing.JColorChooser;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
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
  private final PircbotxIrcClientService ircClientService;

  private JDialog dialog;

  public PreferencesDialog(UiSettingsBus settingsBus,
                           ThemeManager themeManager,
                           RuntimeConfigStore runtimeConfig,
                           LogProperties logProps,
                           NickColorSettingsBus nickColorSettingsBus,
                           NickColorService nickColorService,
                           NickColorOverridesDialog nickColorOverridesDialog,
                           PircbotxIrcClientService ircClientService) {
    this.settingsBus = settingsBus;
    this.themeManager = themeManager;
    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;
    this.nickColorSettingsBus = nickColorSettingsBus;
    this.nickColorService = nickColorService;
    this.nickColorOverridesDialog = nickColorOverridesDialog;
    this.ircClientService = ircClientService;
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
    NetworkAdvancedControls network = buildNetworkAdvancedControls(current, closeables);
    ProxyControls proxy = network.proxy;
    UserhostControls userhost = network.userhost;
    UserInfoEnrichmentControls enrichment = network.enrichment;
    HeartbeatControls heartbeat = network.heartbeat;
    JCheckBox trustAllTlsCertificates = network.trustAllTlsCertificates;

    JPanel networkPanel = network.networkPanel;
    JPanel userLookupsPanel = network.userLookupsPanel;

    JPanel appearancePanel = buildAppearancePanel(theme, fonts);
    JPanel startupPanel = buildStartupPanel(autoConnectOnStart);
    JPanel chatPanel = buildChatPanel(presenceFolds, ctcpRequestsInActiveTarget, nickColors, timestamps, outgoing);
    JPanel embedsPanel = buildEmbedsAndPreviewsPanel(imageEmbeds, linkPreviews);
    JPanel historyStoragePanel = buildHistoryAndStoragePanel(logging, history);

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
          uiePeriodicRefreshNicksPerTickV
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

      if (themeChanged) {
        themeManager.applyTheme(next.theme());
      }
    };

    apply.addActionListener(e -> doApply.run());
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
    JTabbedPane tabs = new JTabbedPane();

    tabs.addTab("Appearance", wrapTab(appearancePanel));
    tabs.addTab("Startup & Connection", wrapTab(startupPanel));
    tabs.addTab("Chat", wrapTab(chatPanel));
    tabs.addTab("Embeds & Previews", wrapTab(embedsPanel));
    tabs.addTab("History & Storage", wrapTab(historyStoragePanel));
    tabs.addTab("Network", wrapTab(networkPanel));
    tabs.addTab("User lookups", wrapTab(userLookupsPanel));

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
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    return scroll;
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
    historyPanel.add(historyInfo, "span 2, growx, wmin 0, wrap");
    historyPanel.add(new JLabel("Initial load (lines):"));
    historyPanel.add(historyInitialLoadLines, "w 110!");
    historyPanel.add(new JLabel("Page size (Load older):"));
    historyPanel.add(historyPageSize, "w 110!");

    return new HistoryControls(historyInitialLoadLines, historyPageSize, historyPanel);
  }

  private LoggingControls buildLoggingControls(LogProperties logProps, List<AutoCloseable> closeables) {
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
      dbBaseName.setEnabled(true);
      dbNextToConfig.setEnabled(true);
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

  
  
  private NetworkAdvancedControls buildNetworkAdvancedControls(UiSettings current, List<AutoCloseable> closeables) {
    IrcProperties.Proxy p = NetProxyContext.settings();
    if (p == null) p = new IrcProperties.Proxy(false, "", 1080, "", "", true, 10_000, 30_000);
    JPanel networkPanel = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]6[]6[]6[]"));
    JPanel userLookupsPanel = new JPanel(new MigLayout("insets 12, fillx, wrap 1, hidemode 3", "[grow,fill]", ""));

    networkPanel.add(tabTitle("Network"), "span 2, growx, wmin 0, wrap");
    networkPanel.add(sectionTitle("SOCKS5 proxy"), "span 2, growx, wmin 0, wrap");
    networkPanel.add(helpText(
        "When enabled, IRCafe routes IRC connections, link previews, embedded images, and file downloads through a SOCKS5 proxy.\n\n" +
            "Heads up: proxy credentials are stored in your runtime config file in plain text."),
        "span 2, growx, wmin 0, wrap");

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
    char defaultEcho = proxyPassword.getEchoChar();
    JCheckBox showPassword = new JCheckBox("Show");
    JButton clearPassword = new JButton("Clear");
    showPassword.addActionListener(e -> proxyPassword.setEchoChar(showPassword.isSelected() ? (char) 0 : defaultEcho));
    clearPassword.addActionListener(e -> proxyPassword.setText(""));

    int connectTimeoutSec = (int) Math.max(1, p.connectTimeoutMs() / 1000L);
    int readTimeoutSec = (int) Math.max(1, p.readTimeoutMs() / 1000L);
    JSpinner connectTimeoutSeconds = numberSpinner(connectTimeoutSec, 1, 300, 1, closeables);
    JSpinner readTimeoutSeconds = numberSpinner(readTimeoutSec, 1, 600, 1, closeables);

    JPanel passwordRow = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]6[]", "[]"));
    passwordRow.setOpaque(false);
    passwordRow.add(proxyPassword, "growx, pushx, wmin 0");
    passwordRow.add(showPassword);
    passwordRow.add(clearPassword);

    Runnable updateProxyEnabledState = () -> {
      boolean enabled = proxyEnabled.isSelected();
      proxyHost.setEnabled(enabled);
      proxyPort.setEnabled(enabled);
      proxyRemoteDns.setEnabled(enabled);
      proxyUsername.setEnabled(enabled);
      proxyPassword.setEnabled(enabled);
      showPassword.setEnabled(enabled);
      clearPassword.setEnabled(enabled);
      connectTimeoutSeconds.setEnabled(enabled);
      readTimeoutSeconds.setEnabled(enabled);
    };
    proxyEnabled.addActionListener(e -> updateProxyEnabledState.run());
    updateProxyEnabledState.run();

    networkPanel.add(proxyEnabled, "span 2, wrap");
    networkPanel.add(new JLabel("Host:"));
    networkPanel.add(proxyHost, "growx, wmin 0");
    networkPanel.add(new JLabel("Port:"));
    networkPanel.add(proxyPort, "w 110!");
    networkPanel.add(new JLabel(""));
    networkPanel.add(proxyRemoteDnsRow, "growx, wmin 0");
    networkPanel.add(new JLabel("Username:"));
    networkPanel.add(proxyUsername, "growx, wmin 0");
    networkPanel.add(new JLabel("Password:"));
    networkPanel.add(passwordRow, "growx, wmin 0");
    networkPanel.add(new JLabel("Connect timeout (sec):"));
    networkPanel.add(connectTimeoutSeconds, "w 110!");
    networkPanel.add(new JLabel("Read timeout (sec):"));
    networkPanel.add(readTimeoutSeconds, "w 110!");
    networkPanel.add(sectionTitle("TLS / SSL"), "span 2, growx, wmin 0, wrap");
    networkPanel.add(helpText(
        "This setting is intentionally dangerous. If enabled, IRCafe will accept any TLS certificate (expired, mismatched, self-signed, etc)\n" +
            "for IRC-over-TLS connections and for HTTPS fetching (link previews, embedded images, etc).\n\n" +
            "Only enable this if you understand the risk (MITM becomes trivial)."),
        "span 2, growx, wmin 0, wrap");

    JCheckBox trustAllTlsCertificates = new JCheckBox();
    trustAllTlsCertificates.setSelected(NetTlsContext.trustAllCertificates());
    JComponent trustAllTlsRow = wrapCheckBox(trustAllTlsCertificates, "Trust all TLS/SSL certificates (insecure)");
    networkPanel.add(trustAllTlsRow, "span 2, growx, wmin 0, wrap");
    networkPanel.add(sectionTitle("Connection heartbeat"), "span 2, growx, wmin 0, wrap");
    networkPanel.add(helpText(
        "IRCafe can detect 'silent' disconnects by monitoring inbound traffic.\n" +
            "If no IRC messages are received for the configured timeout, IRCafe will close the socket\n" +
            "and let the reconnect logic take over (if enabled).\n\n" +
            "Tip: If your network is very quiet, increase the timeout."
    ), "span 2, growx, wmin 0, wrap");

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

    networkPanel.add(heartbeatEnabledRow, "span 2, growx, wmin 0, wrap");
    networkPanel.add(new JLabel("Check period (sec):"));
    networkPanel.add(heartbeatCheckPeriodSeconds, "w 110!");
    networkPanel.add(new JLabel("Timeout (sec):"));
    networkPanel.add(heartbeatTimeoutSeconds, "w 110!");

    ProxyControls proxyControls = new ProxyControls(
        proxyEnabled,
        proxyHost,
        proxyPort,
        proxyRemoteDns,
        proxyUsername,
        proxyPassword,
        showPassword,
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
    userLookupsPanel.add(userLookupsIntro, "growx, wrap");
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
    userLookupsPanel.add(lookupPresetPanel, "growx, wrap");
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

    userLookupsPanel.add(hostmaskPanel, "growx, wrap");
    userLookupsPanel.add(enrichmentPanel, "growx, wrap");

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

  private JPanel buildAppearancePanel(ThemeControls theme, FontControls fonts) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]6[]10[]6[]6[]"));

    form.add(tabTitle("Appearance"), "span 2, growx, wmin 0, wrap");
    form.add(sectionTitle("Look & feel"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Theme"));
    form.add(theme.combo, "growx");

    form.add(sectionTitle("Chat text"), "span 2, growx, wmin 0, wrap");
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
                               JCheckBox showPassword,
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

}