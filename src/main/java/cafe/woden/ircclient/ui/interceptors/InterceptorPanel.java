package cafe.woden.ircclient.ui.interceptors;

import cafe.woden.ircclient.app.interceptors.InterceptorDefinition;
import cafe.woden.ircclient.app.interceptors.InterceptorEventType;
import cafe.woden.ircclient.app.interceptors.InterceptorHit;
import cafe.woden.ircclient.app.interceptors.InterceptorRule;
import cafe.woden.ircclient.app.interceptors.InterceptorRuleMode;
import cafe.woden.ircclient.app.interceptors.InterceptorStore;
import cafe.woden.ircclient.notify.sound.BuiltInSound;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.util.VirtualThreads;
import com.formdev.flatlaf.FlatClientProperties;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.swing.MigLayout;

/** Editor/view for a single interceptor node. */
public final class InterceptorPanel extends JPanel implements AutoCloseable {
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private static final String RULE_ANY_EVENT_LABEL = "Any Message";
  private static final InterceptorRuleMode[] CHANNEL_FILTER_MODES = {
    InterceptorRuleMode.ALL,
    InterceptorRuleMode.NONE,
    InterceptorRuleMode.LIKE,
    InterceptorRuleMode.GLOB,
    InterceptorRuleMode.REGEX
  };
  private static final InterceptorRuleMode[] RULE_DIMENSION_MODES = {
    InterceptorRuleMode.LIKE,
    InterceptorRuleMode.GLOB,
    InterceptorRuleMode.REGEX
  };

  private final InterceptorStore store;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final ExecutorService refreshExecutor =
      VirtualThreads.newSingleThreadExecutor("ircafe-interceptor-panel-refresh");
  private final AtomicLong refreshSeq = new AtomicLong(0L);

  private final JLabel title = new JLabel("Interceptor");
  private final JLabel subtitle = new JLabel("Select an interceptor node.");
  private final JLabel status = new JLabel(" ");

  private final JTextField interceptorName = new JTextField();
  private final JCheckBox enabled = new JCheckBox("Enabled");
  private final JComboBox<ServerScopeOption> serverScope =
      new JComboBox<>(new ServerScopeOption[] {ServerScopeOption.THIS_SERVER, ServerScopeOption.ANY_SERVER});

  private final JComboBox<InterceptorRuleMode> includeMode = new JComboBox<>(CHANNEL_FILTER_MODES.clone());
  private final JTextField includes = new JTextField();
  private final JComboBox<InterceptorRuleMode> excludeMode = new JComboBox<>(CHANNEL_FILTER_MODES.clone());
  private final JTextField excludes = new JTextField();

  private final JCheckBox actionStatusBarEnabled = new JCheckBox("Status bar notice");
  private final JCheckBox actionToastEnabled = new JCheckBox("Desktop toast");
  private final JCheckBox actionSoundEnabled = new JCheckBox("Play sound");
  private final JComboBox<BuiltInSound> actionSoundId = new JComboBox<>(BuiltInSound.valuesForUi());
  private final JCheckBox actionSoundUseCustom = new JCheckBox("Custom file");
  private final JTextField actionSoundCustomPath = new JTextField();
  private final JButton testSound = new JButton("Test sound");

  private final JCheckBox actionScriptEnabled = new JCheckBox("Run script");
  private final JTextField actionScriptPath = new JTextField();
  private final JTextField actionScriptArgs = new JTextField();
  private final JTextField actionScriptWorkingDirectory = new JTextField();
  private final JButton browseScriptPath = new JButton("Browse...");
  private final JButton browseScriptWorkingDirectory = new JButton("Browse...");

  private final JButton addRule = new JButton("Add...");
  private final JButton editRule = new JButton("Edit...");
  private final JButton removeRule = new JButton("Remove");
  private final JPopupMenu rulesPopupMenu = new JPopupMenu();
  private final JMenuItem rulesPopupEdit = new JMenuItem("Edit...");
  private final JMenuItem rulesPopupRemove = new JMenuItem("Delete...");
  private final JButton clearHits = new JButton("Clear");

  private final RulesTableModel rulesModel = new RulesTableModel();
  private final JTable rulesTable = new JTable(rulesModel);
  private final HitsTableModel hitsModel = new HitsTableModel();
  private final JTable hitsTable = new JTable(hitsModel);

  private volatile String serverId = "";
  private volatile String interceptorId = "";
  private boolean loading = false;
  private boolean controlsEnabled = false;

  public InterceptorPanel(InterceptorStore store) {
    super(new BorderLayout());
    this.store = Objects.requireNonNull(store, "store");

    buildHeader();
    buildBody();
    installListeners();
    setControlsEnabled(false);

    disposables.add(
        store
            .changes()
            .subscribe(
                ch -> {
                  String sid = serverId;
                  String iid = interceptorId;
                  if (sid.isBlank() || iid.isBlank()) return;
                  if (!sid.equals(ch.serverId())) return;
                  if (!iid.equals(ch.interceptorId())) return;
                  SwingUtilities.invokeLater(this::refreshFromStore);
                },
                err -> {
                  // Keep UI alive even if the update stream fails.
                }));
  }

  public void setInterceptorTarget(String serverId, String interceptorId) {
    this.serverId = Objects.toString(serverId, "").trim();
    this.interceptorId = Objects.toString(interceptorId, "").trim();
    refreshFromStore();
  }

  @Override
  public void close() {
    refreshSeq.incrementAndGet();
    disposables.dispose();
    refreshExecutor.shutdownNow();
  }

  private void buildHeader() {
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    JPanel header = new JPanel(new MigLayout("insets 8 10 4 10,fillx,wrap 1", "[grow,fill]", "[]2[]"));
    header.add(title, "growx");
    header.add(subtitle, "growx");
    add(header, BorderLayout.NORTH);
  }

  private void buildBody() {
    interceptorName.setToolTipText("Name shown in the server tree and interceptor tab.");
    serverScope.setToolTipText("Match on this server only, or all servers.");
    includes.setToolTipText(
        "Include channel patterns (comma/newline separated). Use mode All to match all channels or None to match none.");
    excludes.setToolTipText(
        "Exclude channel patterns (comma/newline separated). Use mode All to exclude all channels or None to disable exclusion.");
    includes.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        "e.g. #general, #help, #staff-*");
    excludes.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        "e.g. #offtopic, #bots");

    includeMode.setRenderer(modeComboRenderer());
    excludeMode.setRenderer(modeComboRenderer());

    actionSoundId.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              javax.swing.JList<?> list,
              Object value,
              int index,
              boolean isSelected,
              boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof BuiltInSound sound) {
              setText(sound.displayNameForUi());
            }
            return this;
          }
        });

    actionSoundCustomPath.setToolTipText("Custom sound path relative to runtime config directory.");
    actionScriptPath.setToolTipText("Executable script/program path.");
    actionScriptArgs.setToolTipText("Optional script arguments (quote-aware). Environment variables are injected.");
    actionScriptWorkingDirectory.setToolTipText("Optional working directory for script execution.");
    configureActionButtons();

    configureRulesTable();
    configureHitsTable();

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Definition", buildDefinitionTab());
    tabs.addTab("Triggers", buildTriggersTab());
    tabs.addTab("Actions", wrapVerticalScroll(buildActionsTab()));
    tabs.addTab("Hits", buildHitsTab());
    add(tabs, BorderLayout.CENTER);

    JPanel footer = new JPanel(new BorderLayout());
    footer.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));
    footer.add(status, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
  }

  private JPanel buildDefinitionTab() {
    JPanel tab =
        new JPanel(
            new MigLayout(
                "insets 8 10 8 10,fillx,wrap 1,hidemode 3",
                "[grow,fill]",
                "[]8[]6[]"));

    JPanel identity =
        new JPanel(
            new MigLayout(
                "insets 8,fillx,wrap 4,hidemode 3",
                "[right][grow,fill]16[right][pref!]",
                "[]6[]"));
    identity.setBorder(BorderFactory.createTitledBorder("Interceptor"));
    identity.add(new JLabel("Name:"));
    identity.add(interceptorName, "growx");
    identity.add(new JLabel("Enabled:"));
    identity.add(enabled, "wrap");
    identity.add(new JLabel("Server scope:"));
    identity.add(serverScope, "w 170!, span 3, wrap");

    JPanel channels =
        new JPanel(
            new MigLayout(
                "insets 8,fillx,wrap 3,hidemode 3",
                "[right][pref!][grow,fill]",
                "[]6[]6[]"));
    channels.setBorder(BorderFactory.createTitledBorder("Channel Filtering"));
    channels.add(new JLabel("Include:"));
    channels.add(includeMode, "w 78!");
    channels.add(includes, "growx, pushx, wmin 0, wrap");
    channels.add(new JLabel("Exclude:"));
    channels.add(excludeMode, "w 78!");
    channels.add(excludes, "growx, pushx, wmin 0, wrap");
    channels.add(
        wrappedHint(
            "Use comma, semicolon, or newline-separated patterns. "
                + "All/None modes override pattern text."),
        "span 3, growx, pushx, wmin 0");

    tab.add(identity, "growx, wrap");
    tab.add(channels, "growx, wrap");
    tab.add(
        wrappedHint("Rule editing lives in Triggers; notifications and scripts are under Actions."),
        "growx, pushx, wmin 0");
    return tab;
  }

  private JPanel buildTriggersTab() {
    JPanel tab = new JPanel(new BorderLayout());

    JPanel toolbar = new JPanel(new MigLayout("insets 6 0 6 0,fillx", "[][ ][][grow,fill]", "[]"));
    toolbar.add(addRule);
    toolbar.add(editRule);
    toolbar.add(removeRule);
    toolbar.add(new JLabel(""), "growx");

    tab.add(toolbar, BorderLayout.NORTH);
    tab.add(new JScrollPane(rulesTable), BorderLayout.CENTER);
    return tab;
  }

  private JPanel buildActionsTab() {
    JPanel tab =
        new JPanel(
            new MigLayout(
                "insets 8 10 8 10,fillx,wrap 1,hidemode 3",
                "[grow,fill]",
                "[]8[]8[]"));

    JPanel notifications =
        new JPanel(
            new MigLayout(
                "insets 8,fillx,wrap 2,hidemode 3",
                "[grow,fill][grow,fill]",
                "[]"));
    notifications.setBorder(BorderFactory.createTitledBorder("Notifications"));
    notifications.add(actionStatusBarEnabled, "growx");
    notifications.add(actionToastEnabled, "growx");

    JPanel sound =
        new JPanel(
            new MigLayout(
                "insets 8,fillx,wrap 3,hidemode 3",
                "[pref!][grow,fill][pref!]",
                "[]6[]"));
    sound.setBorder(BorderFactory.createTitledBorder("Sound"));
    sound.add(actionSoundEnabled, "span 2,growx");
    sound.add(testSound, "align right,wrap");
    sound.add(new JLabel("Built-in:"));
    sound.add(actionSoundId, "growx");
    sound.add(new JLabel(""));
    sound.add(actionSoundUseCustom, "span 3,wrap");
    sound.add(new JLabel("File:"));
    sound.add(actionSoundCustomPath, "span 2,growx,wrap");

    JPanel script =
        new JPanel(
            new MigLayout(
                "insets 8,fillx,wrap 3,hidemode 3",
                "[pref!][grow,fill][pref!]",
                "[]6[]6[]"));
    script.setBorder(BorderFactory.createTitledBorder("Script"));
    script.add(actionScriptEnabled, "span 3,wrap");
    script.add(new JLabel("Path:"));
    script.add(actionScriptPath, "growx");
    script.add(browseScriptPath, "w 105!");
    script.add(new JLabel("Args:"));
    script.add(actionScriptArgs, "span 2,growx,wrap");
    script.add(new JLabel("CWD:"));
    script.add(actionScriptWorkingDirectory, "growx");
    script.add(browseScriptWorkingDirectory, "w 105!");

    tab.add(notifications, "growx");
    tab.add(sound, "growx");
    tab.add(script, "growx");

    return tab;
  }

  private static JScrollPane wrapVerticalScroll(JPanel content) {
    JScrollPane scroll =
        new JScrollPane(
            content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    return scroll;
  }

  private JPanel buildHitsTab() {
    JPanel tab = new JPanel(new BorderLayout());

    JPanel toolbar = new JPanel(new MigLayout("insets 6 0 6 0,fillx", "[grow,fill][]", "[]"));
    toolbar.add(new JLabel(""), "growx");
    toolbar.add(clearHits);

    tab.add(toolbar, BorderLayout.NORTH);
    tab.add(new JScrollPane(hitsTable), BorderLayout.CENTER);
    return tab;
  }

  private void configureRulesTable() {
    rulesTable.setFillsViewportHeight(true);
    rulesTable.setRowSelectionAllowed(true);
    rulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    rulesTable.setShowHorizontalLines(false);
    rulesTable.setShowVerticalLines(false);
    rulesTable.setAutoCreateRowSorter(true);
    rulesTable.getTableHeader().setReorderingAllowed(false);
    // Force dialog-only editing flow (no inline cell editor).
    rulesTable.setDefaultEditor(Object.class, null);
    rulesTable.setDefaultEditor(Boolean.class, null);
    rulesTable.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);

    rulesPopupEdit.addActionListener(e -> editSelectedRule());
    rulesPopupRemove.addActionListener(e -> removeSelectedRule());
    rulesPopupEdit.setIcon(SvgIcons.action("edit", 16));
    rulesPopupRemove.setIcon(SvgIcons.action("trash", 16));
    rulesPopupEdit.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
    rulesPopupRemove.setDisabledIcon(SvgIcons.actionDisabled("trash", 16));
    rulesPopupMenu.setLightWeightPopupEnabled(true);
    rulesPopupMenu.add(rulesPopupEdit);
    rulesPopupMenu.add(rulesPopupRemove);

    rulesTable.getColumnModel().getColumn(0).setPreferredWidth(56); // On
    rulesTable.getColumnModel().getColumn(1).setPreferredWidth(220); // Why
    rulesTable.getColumnModel().getColumn(2).setPreferredWidth(220); // Events
    rulesTable.getColumnModel().getColumn(3).setPreferredWidth(260); // Message
    rulesTable.getColumnModel().getColumn(4).setPreferredWidth(180); // Nick
    rulesTable.getColumnModel().getColumn(5).setPreferredWidth(220); // Hostmask
  }

  private void configureHitsTable() {
    hitsTable.setFillsViewportHeight(true);
    hitsTable.setRowSelectionAllowed(true);
    hitsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    hitsTable.setShowHorizontalLines(false);
    hitsTable.setShowVerticalLines(false);
    hitsTable.getTableHeader().setReorderingAllowed(false);
    hitsTable.setAutoCreateRowSorter(true);

    hitsTable.getColumnModel().getColumn(0).setPreferredWidth(160); // Time
    hitsTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Server
    hitsTable.getColumnModel().getColumn(2).setPreferredWidth(120); // From
    hitsTable.getColumnModel().getColumn(3).setPreferredWidth(180); // Hostmask
    hitsTable.getColumnModel().getColumn(4).setPreferredWidth(120); // Channel
    hitsTable.getColumnModel().getColumn(5).setPreferredWidth(150); // Why
    hitsTable.getColumnModel().getColumn(6).setPreferredWidth(90); // Event
    hitsTable.getColumnModel().getColumn(7).setPreferredWidth(600); // Message
  }

  private void installListeners() {
    installSaveOnEnterAndBlur(interceptorName);
    enabled.addActionListener(e -> saveCurrentDefinition());
    serverScope.addActionListener(e -> saveCurrentDefinition());

    includeMode.addActionListener(e -> saveCurrentDefinition());
    excludeMode.addActionListener(e -> saveCurrentDefinition());
    installSaveOnEnterAndBlur(includes);
    installSaveOnEnterAndBlur(excludes);

    actionStatusBarEnabled.addActionListener(e -> saveCurrentDefinition());
    actionToastEnabled.addActionListener(e -> saveCurrentDefinition());

    actionSoundEnabled.addActionListener(
        e -> {
          refreshActionControlEnabledState();
          saveCurrentDefinition();
        });
    actionSoundId.addActionListener(e -> saveCurrentDefinition());
    actionSoundUseCustom.addActionListener(
        e -> {
          refreshActionControlEnabledState();
          saveCurrentDefinition();
        });
    installSaveOnEnterAndBlur(actionSoundCustomPath);
    testSound.addActionListener(e -> previewSelectedSound());

    actionScriptEnabled.addActionListener(
        e -> {
          refreshActionControlEnabledState();
          saveCurrentDefinition();
        });
    browseScriptPath.addActionListener(e -> browseForScriptPath());
    browseScriptWorkingDirectory.addActionListener(e -> browseForScriptWorkingDirectory());
    installSaveOnEnterAndBlur(actionScriptPath);
    installSaveOnEnterAndBlur(actionScriptArgs);
    installSaveOnEnterAndBlur(actionScriptWorkingDirectory);

    addRule.addActionListener(
        e -> {
          InterceptorRule next = promptRuleDialog("Add Trigger Rule", defaultRule(rulesModel.getRowCount() + 1));
          if (next == null) return;
          int row = rulesModel.addRule(next);
          if (row >= 0) {
            int viewRow = rulesTable.convertRowIndexToView(row);
            rulesTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
          }
          saveCurrentDefinition();
        });

    editRule.addActionListener(e -> editSelectedRule());
    rulesTable.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowRulesPopup(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowRulesPopup(e);
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            if (e.getClickCount() < 2) return;
            int viewRow = rulesTable.rowAtPoint(e.getPoint());
            if (viewRow >= 0) {
              rulesTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            }
            editSelectedRule();
          }
        });

    removeRule.addActionListener(e -> removeSelectedRule());

    rulesTable.getSelectionModel().addListSelectionListener(e -> updateRuleButtons());

    clearHits.addActionListener(
        e -> {
          String sid = serverId;
          String iid = interceptorId;
          if (sid.isBlank() || iid.isBlank()) return;
          store.clearHits(sid, iid);
        });
  }

  private void editSelectedRule() {
    int row = selectedRuleModelRow();
    if (row < 0) return;
    InterceptorRule current = rulesModel.ruleAt(row);
    if (current == null) return;

    InterceptorRule updated = promptRuleDialog("Edit Trigger Rule", current);
    if (updated == null) return;

    rulesModel.setRule(row, updated);
    saveCurrentDefinition();
  }

  private void maybeShowRulesPopup(MouseEvent e) {
    if (e == null || !e.isPopupTrigger()) return;
    int viewRow = rulesTable.rowAtPoint(e.getPoint());
    if (viewRow >= 0) {
      rulesTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
    } else {
      rulesTable.clearSelection();
    }
    boolean hasSelection = controlsEnabled && selectedRuleModelRow() >= 0;
    rulesPopupEdit.setEnabled(hasSelection);
    rulesPopupRemove.setEnabled(hasSelection);
    // Popup menus may be created before theme changes; refresh delegates before showing.
    try {
      SwingUtilities.updateComponentTreeUI(rulesPopupMenu);
    } catch (Exception ignored) {
    }
    rulesPopupMenu.show(e.getComponent(), e.getX(), e.getY());
  }

  private void removeSelectedRule() {
    int row = selectedRuleModelRow();
    if (row < 0) return;
    if (!confirmRuleRemoval(row)) return;
    rulesModel.removeRow(row);
    saveCurrentDefinition();
    updateRuleButtons();
  }

  private boolean confirmRuleRemoval(int modelRow) {
    InterceptorRule rule = rulesModel.ruleAt(modelRow);
    String label = rule == null ? "" : Objects.toString(rule.label(), "").trim();
    if (label.isEmpty()) label = "selected rule";
    Window owner = SwingUtilities.getWindowAncestor(this);
    int choice =
        JOptionPane.showConfirmDialog(
            owner,
            "Delete trigger rule \"" + label + "\"?",
            "Delete Trigger Rule",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return choice == JOptionPane.YES_OPTION;
  }

  private int selectedRuleModelRow() {
    int row = rulesTable.getSelectedRow();
    if (row < 0) return -1;
    return rulesTable.convertRowIndexToModel(row);
  }

  private DefaultListCellRenderer modeComboRenderer() {
    return new DefaultListCellRenderer() {
      @Override
      public java.awt.Component getListCellRendererComponent(
          javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof InterceptorRuleMode mode) {
          setText(modeLabel(mode));
        }
        return this;
      }
    };
  }

  private void installSaveOnEnterAndBlur(JTextField field) {
    field.addActionListener(e -> saveCurrentDefinition());
    field.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            saveCurrentDefinition();
          }
        });
  }

  private InterceptorRule promptRuleDialog(String dialogTitle, InterceptorRule seed) {
    InterceptorRule base = seed == null ? defaultRule(rulesModel.getRowCount() + 1) : seed;

    JCheckBox ruleEnabled = new JCheckBox("Enabled", base.enabled());
    JTextField ruleLabel = new JTextField(base.label());
    JCheckBox anyEventType =
        new JCheckBox(RULE_ANY_EVENT_LABEL, Objects.toString(base.eventTypesCsv(), "").trim().isBlank());
    LinkedHashMap<InterceptorEventType, JCheckBox> eventSelectors = buildRuleEventSelectors(base.eventTypesCsv());

    JComboBox<InterceptorRuleMode> messageMode = new JComboBox<>(RULE_DIMENSION_MODES.clone());
    messageMode.setRenderer(modeComboRenderer());
    messageMode.setSelectedItem(base.messageMode());
    JTextField messagePattern = new JTextField(base.messagePattern());
    messagePattern.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        "Message pattern");

    JComboBox<InterceptorRuleMode> nickMode = new JComboBox<>(RULE_DIMENSION_MODES.clone());
    nickMode.setRenderer(modeComboRenderer());
    nickMode.setSelectedItem(base.nickMode());
    JTextField nickPattern = new JTextField(base.nickPattern());
    nickPattern.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        "Nick pattern");

    JComboBox<InterceptorRuleMode> hostmaskMode = new JComboBox<>(RULE_DIMENSION_MODES.clone());
    hostmaskMode.setRenderer(modeComboRenderer());
    hostmaskMode.setSelectedItem(base.hostmaskMode());
    JTextField hostmaskPattern = new JTextField(base.hostmaskPattern());
    hostmaskPattern.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        "Hostmask pattern");

    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 3,hidemode 3",
                "[right][pref!][grow,fill]",
                "[]6[]6[]6[]6[]6[]"));

    panel.add(ruleEnabled, "span 3,wrap");

    panel.add(new JLabel("Label:"));
    panel.add(ruleLabel, "span 2,growx,pushx,wmin 0,wrap");

    JPanel eventsGrid =
        new JPanel(
            new MigLayout(
                "insets 0,fillx,wrap 2,hidemode 3",
                "[grow,fill][grow,fill]",
                "[]2[]"));
    for (JCheckBox selector : eventSelectors.values()) {
      selector.setToolTipText("Match " + selector.getText() + " events.");
      eventsGrid.add(selector, "growx");
    }

    JScrollPane eventsScroll = new JScrollPane(eventsGrid);
    eventsScroll.setBorder(BorderFactory.createEmptyBorder());
    eventsScroll.setPreferredSize(new java.awt.Dimension(340, 130));
    eventsScroll.getVerticalScrollBar().setUnitIncrement(14);

    JPanel eventsPanel =
        new JPanel(
            new MigLayout(
                "insets 0,fillx,wrap 1,hidemode 3",
                "[grow,fill]",
                "[]4[]"));
    eventsPanel.add(anyEventType, "growx");
    eventsPanel.add(eventsScroll, "growx,pushx,wmin 0");

    Runnable refreshEventSelectorState =
        () -> {
          boolean enabledSelectors = !anyEventType.isSelected();
          for (JCheckBox selector : eventSelectors.values()) {
            selector.setEnabled(enabledSelectors);
          }
        };
    anyEventType.addActionListener(e -> refreshEventSelectorState.run());
    refreshEventSelectorState.run();

    panel.add(new JLabel("Events:"));
    panel.add(eventsPanel, "span 2,growx,pushx,wmin 0,wrap");

    panel.add(new JLabel("Message:"));
    panel.add(messageMode, "w 78!");
    panel.add(messagePattern, "growx,pushx,wmin 0,wrap");

    panel.add(new JLabel("Nick:"));
    panel.add(nickMode, "w 78!");
    panel.add(nickPattern, "growx,pushx,wmin 0,wrap");

    panel.add(new JLabel("Hostmask:"));
    panel.add(hostmaskMode, "w 78!");
    panel.add(hostmaskPattern, "growx,pushx,wmin 0,wrap");

    Window owner = SwingUtilities.getWindowAncestor(this);
    int choice =
        JOptionPane.showConfirmDialog(
            owner,
            panel,
            dialogTitle,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) return null;

    String eventTypesCsv = selectedRuleEvents(anyEventType, eventSelectors);
    if (eventTypesCsv == null) {
      JOptionPane.showMessageDialog(
          owner,
          "Select at least one event type, or enable \"" + RULE_ANY_EVENT_LABEL + "\".",
          "Event Types Required",
          JOptionPane.WARNING_MESSAGE);
      return null;
    }

    return new InterceptorRule(
        ruleEnabled.isSelected(),
        ruleLabel.getText(),
        eventTypesCsv,
        selectedMode(messageMode, InterceptorRuleMode.LIKE),
        messagePattern.getText(),
        selectedMode(nickMode, InterceptorRuleMode.LIKE),
        nickPattern.getText(),
        selectedMode(hostmaskMode, InterceptorRuleMode.GLOB),
        hostmaskPattern.getText());
  }

  private LinkedHashMap<InterceptorEventType, JCheckBox> buildRuleEventSelectors(String selectedCsv) {
    LinkedHashMap<InterceptorEventType, JCheckBox> out = new LinkedHashMap<>();
    EnumSet<InterceptorEventType> selected = InterceptorEventType.parseCsv(selectedCsv);
    boolean any = Objects.toString(selectedCsv, "").trim().isBlank();
    for (InterceptorEventType eventType : InterceptorEventType.values()) {
      if (eventType == null) continue;
      JCheckBox box = new JCheckBox(eventType.toString(), !any && selected.contains(eventType));
      out.put(eventType, box);
    }
    return out;
  }

  private static String formatEventTypesForDisplay(String rawCsv) {
    String raw = Objects.toString(rawCsv, "").trim();
    if (raw.isEmpty()) return RULE_ANY_EVENT_LABEL;

    String[] parts = raw.split("[,\\n;]");
    ArrayList<String> labels = new ArrayList<>(parts.length);
    for (String part : parts) {
      String token = Objects.toString(part, "").trim();
      if (token.isEmpty()) continue;
      InterceptorEventType type = resolveEventType(token);
      labels.add(type != null ? type.toString() : token);
    }
    if (labels.isEmpty()) return RULE_ANY_EVENT_LABEL;
    return String.join(", ", labels);
  }

  private static String selectedRuleEvents(
      JCheckBox anyEventType,
      LinkedHashMap<InterceptorEventType, JCheckBox> selectors
  ) {
    if (anyEventType == null || anyEventType.isSelected()) return "";
    if (selectors == null || selectors.isEmpty()) return "";

    ArrayList<String> tokens = new ArrayList<>(selectors.size());
    for (var entry : selectors.entrySet()) {
      InterceptorEventType type = entry.getKey();
      JCheckBox box = entry.getValue();
      if (type == null || box == null || !box.isSelected()) continue;
      tokens.add(type.token());
    }
    if (tokens.isEmpty()) return null;
    if (tokens.size() == selectors.size()) return "";
    return String.join(",", tokens);
  }

  private static InterceptorEventType resolveEventType(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return null;

    InterceptorEventType byToken = InterceptorEventType.fromToken(value);
    if (byToken != null) return byToken;

    String foldedValue = foldEventLabel(value);
    for (InterceptorEventType type : InterceptorEventType.values()) {
      if (type == null) continue;
      if (value.equalsIgnoreCase(type.toString())) return type;
      if (foldedValue.equals(foldEventLabel(type.toString()))) return type;
    }
    return null;
  }

  private static String foldEventLabel(String raw) {
    String value = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty()) return "";
    value = value.replace('-', ' ').replace('_', ' ');
    return value.replaceAll("\\s+", " ").trim();
  }

  private void configureActionButtons() {
    configureIconButton(addRule, "plus", "Add trigger rule");
    configureIconButton(editRule, "edit", "Edit selected trigger rule");
    configureIconButton(removeRule, "trash", "Remove selected trigger rule");
    configureIconButton(clearHits, "close", "Clear interceptor hits");
    configureIconButton(testSound, "play", "Test selected sound");
    configureIconButton(browseScriptPath, "terminal", "Browse for script/program");
    configureIconButton(browseScriptWorkingDirectory, "settings", "Browse for script working directory");
  }

  private static void configureIconButton(JButton button, String iconName, String tooltip) {
    if (button == null) return;
    button.setText("");
    button.setIcon(SvgIcons.action(iconName, 16));
    button.setDisabledIcon(SvgIcons.actionDisabled(iconName, 16));
    button.setMargin(new Insets(2, 6, 2, 6));
    button.setToolTipText(tooltip);
    button.setFocusable(false);
  }

  private void previewSelectedSound() {
    if (!controlsEnabled) return;
    if (!actionSoundEnabled.isSelected()) return;
    store.previewSoundOverride(
        selectedSoundId(),
        actionSoundUseCustom.isSelected(),
        actionSoundCustomPath.getText());
  }

  private void browseForScriptPath() {
    if (!controlsEnabled) return;
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Choose external script");
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    chooser.setAcceptAllFileFilterUsed(true);
    seedChooserPath(chooser, actionScriptPath.getText(), false);
    int result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));
    if (result != JFileChooser.APPROVE_OPTION) return;

    File selected = chooser.getSelectedFile();
    if (selected == null) return;

    actionScriptPath.setText(selected.getAbsolutePath());
    actionScriptEnabled.setSelected(true);
    refreshActionControlEnabledState();
    saveCurrentDefinition();
  }

  private void browseForScriptWorkingDirectory() {
    if (!controlsEnabled) return;
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Choose script working directory");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    seedChooserPath(chooser, actionScriptWorkingDirectory.getText(), true);
    int result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));
    if (result != JFileChooser.APPROVE_OPTION) return;

    File selected = chooser.getSelectedFile();
    if (selected == null) return;

    actionScriptWorkingDirectory.setText(selected.getAbsolutePath());
    actionScriptEnabled.setSelected(true);
    refreshActionControlEnabledState();
    saveCurrentDefinition();
  }

  private static void seedChooserPath(JFileChooser chooser, String rawPath, boolean preferDirectory) {
    if (chooser == null) return;
    String path = Objects.toString(rawPath, "").trim();
    if (path.isEmpty()) return;
    File candidate = new File(path);

    if (preferDirectory) {
      if (candidate.isDirectory()) {
        chooser.setCurrentDirectory(candidate);
        chooser.setSelectedFile(candidate);
        return;
      }
      File parent = candidate.getParentFile();
      if (parent != null && parent.isDirectory()) {
        chooser.setCurrentDirectory(parent);
      }
      return;
    }

    if (candidate.isDirectory()) {
      chooser.setCurrentDirectory(candidate);
      return;
    }
    File parent = candidate.getParentFile();
    if (parent != null && parent.isDirectory()) {
      chooser.setCurrentDirectory(parent);
    }
    chooser.setSelectedFile(candidate);
  }

  private static JTextArea wrappedHint(String text) {
    JTextArea hint = new JTextArea(2, 1);
    hint.setEditable(false);
    hint.setFocusable(false);
    hint.setOpaque(false);
    hint.setLineWrap(true);
    hint.setWrapStyleWord(true);
    hint.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
    hint.setFont(new JLabel().getFont());
    hint.setForeground(new JLabel().getForeground());
    hint.setText(Objects.toString(text, ""));
    return hint;
  }

  private void refreshFromStore() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::refreshFromStore);
      return;
    }

    long req = refreshSeq.incrementAndGet();
    String sid = serverId;
    String iid = interceptorId;
    applyLoadingState();

    if (sid.isBlank() || iid.isBlank()) {
      applyNoSelectionState(req);
      return;
    }

    final String loadSid = sid;
    final String loadIid = iid;
    refreshExecutor.execute(() -> loadInterceptorSnapshot(req, loadSid, loadIid));
  }

  private void applyLoadingState() {
    loading = true;
    title.setText("Interceptor");
    subtitle.setText("Loading interceptor...");
    status.setText("Loading...");
    setControlsEnabled(false);
  }

  private void applyNoSelectionState(long req) {
    if (req != refreshSeq.get()) return;
    loading = true;
    title.setText("Interceptor");
    subtitle.setText("Select an interceptor node.");
    rulesModel.setRows(List.of());
    hitsModel.setRows(List.of());
    status.setText(" ");
    resetControls();
    loading = false;
    setControlsEnabled(false);
  }

  private void applyMissingInterceptorState(long req) {
    if (req != refreshSeq.get()) return;
    loading = true;
    title.setText("Interceptor");
    subtitle.setText("Interceptor was removed.");
    rulesModel.setRows(List.of());
    hitsModel.setRows(List.of());
    status.setText(" ");
    resetControls();
    loading = false;
    setControlsEnabled(false);
  }

  private void loadInterceptorSnapshot(long req, String sid, String iid) {
    InterceptorDefinition def;
    List<InterceptorHit> sorted;
    try {
      def = store.interceptor(sid, iid);
      if (def == null) {
        SwingUtilities.invokeLater(() -> applyMissingInterceptorState(req));
        return;
      }

      List<InterceptorHit> hits = store.listHits(sid, iid, 2_000);
      ArrayList<InterceptorHit> tmp = new ArrayList<>(hits);
      tmp.sort(
          (a, b) -> {
            Instant aa = a == null ? null : a.at();
            Instant bb = b == null ? null : b.at();
            if (aa == null && bb == null) return 0;
            if (aa == null) return 1;
            if (bb == null) return -1;
            return bb.compareTo(aa);
          });
      sorted = List.copyOf(tmp);
    } catch (Exception ignored) {
      SwingUtilities.invokeLater(() -> applyMissingInterceptorState(req));
      return;
    }

    InterceptorDefinition loadedDef = def;
    List<InterceptorHit> loadedHits = sorted;
    SwingUtilities.invokeLater(() -> applyLoadedInterceptorSnapshot(req, sid, iid, loadedDef, loadedHits));
  }

  private void applyLoadedInterceptorSnapshot(
      long req,
      String sid,
      String iid,
      InterceptorDefinition def,
      List<InterceptorHit> sortedHits
  ) {
    if (req != refreshSeq.get()) return;
    if (!Objects.equals(serverId, sid) || !Objects.equals(interceptorId, iid)) return;
    if (def == null) {
      applyMissingInterceptorState(req);
      return;
    }

    loading = true;
    title.setText("Interceptor - " + def.name());
    subtitle.setText(def.scopeAnyServer() ? "Scope: any server" : "Scope: this server");

    interceptorName.setText(def.name());
    enabled.setSelected(def.enabled());
    serverScope.setSelectedItem(def.scopeAnyServer() ? ServerScopeOption.ANY_SERVER : ServerScopeOption.THIS_SERVER);

    includeMode.setSelectedItem(def.channelIncludeMode());
    includes.setText(def.channelIncludes());
    excludeMode.setSelectedItem(def.channelExcludeMode());
    excludes.setText(def.channelExcludes());

    actionStatusBarEnabled.setSelected(def.actionStatusBarEnabled());
    actionToastEnabled.setSelected(def.actionToastEnabled());

    actionSoundEnabled.setSelected(def.actionSoundEnabled());
    actionSoundId.setSelectedItem(BuiltInSound.fromId(def.actionSoundId()));
    actionSoundUseCustom.setSelected(def.actionSoundUseCustom());
    actionSoundCustomPath.setText(def.actionSoundCustomPath());

    actionScriptEnabled.setSelected(def.actionScriptEnabled());
    actionScriptPath.setText(def.actionScriptPath());
    actionScriptArgs.setText(def.actionScriptArgs());
    actionScriptWorkingDirectory.setText(def.actionScriptWorkingDirectory());

    rulesModel.setRows(def.rules());
    List<InterceptorHit> hits = sortedHits == null ? List.of() : sortedHits;
    hitsModel.setRows(hits);
    status.setText("Hits: " + hits.size() + "  Rules: " + def.rules().size());

    loading = false;
    setControlsEnabled(true);
    refreshActionControlEnabledState();
    updateRuleButtons();
  }

  private void resetControls() {
    enabled.setSelected(false);
    interceptorName.setText("");
    serverScope.setSelectedItem(ServerScopeOption.THIS_SERVER);

    includeMode.setSelectedItem(InterceptorRuleMode.GLOB);
    includes.setText("");
    excludeMode.setSelectedItem(InterceptorRuleMode.GLOB);
    excludes.setText("");

    actionStatusBarEnabled.setSelected(false);
    actionToastEnabled.setSelected(false);

    actionSoundEnabled.setSelected(false);
    actionSoundId.setSelectedItem(BuiltInSound.NOTIF_1);
    actionSoundUseCustom.setSelected(false);
    actionSoundCustomPath.setText("");

    actionScriptEnabled.setSelected(false);
    actionScriptPath.setText("");
    actionScriptArgs.setText("");
    actionScriptWorkingDirectory.setText("");

    refreshActionControlEnabledState();
    updateRuleButtons();
  }

  private void refreshActionControlEnabledState() {
    boolean soundOn = controlsEnabled && actionSoundEnabled.isSelected();
    actionSoundId.setEnabled(soundOn);
    actionSoundUseCustom.setEnabled(soundOn);
    actionSoundCustomPath.setEnabled(soundOn && actionSoundUseCustom.isSelected());
    testSound.setEnabled(soundOn);

    boolean scriptOn = controlsEnabled && actionScriptEnabled.isSelected();
    actionScriptPath.setEnabled(scriptOn);
    actionScriptArgs.setEnabled(scriptOn);
    actionScriptWorkingDirectory.setEnabled(scriptOn);
    browseScriptPath.setEnabled(scriptOn);
    browseScriptWorkingDirectory.setEnabled(scriptOn);
  }

  private void setControlsEnabled(boolean enabled) {
    controlsEnabled = enabled;

    this.enabled.setEnabled(enabled);
    interceptorName.setEnabled(enabled);
    serverScope.setEnabled(enabled);
    includeMode.setEnabled(enabled);
    includes.setEnabled(enabled);
    excludeMode.setEnabled(enabled);
    excludes.setEnabled(enabled);

    actionStatusBarEnabled.setEnabled(enabled);
    actionToastEnabled.setEnabled(enabled);

    actionSoundEnabled.setEnabled(enabled);
    actionScriptEnabled.setEnabled(enabled);

    clearHits.setEnabled(enabled);
    rulesTable.setEnabled(enabled);
    hitsTable.setEnabled(enabled);

    refreshActionControlEnabledState();
    updateRuleButtons();
  }

  private void updateRuleButtons() {
    boolean hasSelection = selectedRuleModelRow() >= 0;
    addRule.setEnabled(controlsEnabled);
    editRule.setEnabled(controlsEnabled && hasSelection);
    removeRule.setEnabled(controlsEnabled && hasSelection);
  }

  private void saveCurrentDefinition() {
    if (loading) return;
    String sid = serverId;
    String iid = interceptorId;
    if (sid.isBlank() || iid.isBlank()) return;

    InterceptorDefinition current = store.interceptor(sid, iid);
    if (current == null) return;

    InterceptorDefinition updated =
        new InterceptorDefinition(
            current.id(),
            interceptorName.getText(),
            enabled.isSelected(),
            serverScope.getSelectedItem() == ServerScopeOption.ANY_SERVER ? "" : sid,
            selectedMode(includeMode, InterceptorRuleMode.GLOB),
            includes.getText(),
            selectedMode(excludeMode, InterceptorRuleMode.GLOB),
            excludes.getText(),
            actionSoundEnabled.isSelected(),
            actionStatusBarEnabled.isSelected(),
            actionToastEnabled.isSelected(),
            selectedSoundId(),
            actionSoundUseCustom.isSelected(),
            actionSoundCustomPath.getText(),
            actionScriptEnabled.isSelected(),
            actionScriptPath.getText(),
            actionScriptArgs.getText(),
            actionScriptWorkingDirectory.getText(),
            rulesModel.snapshot());

    store.saveInterceptor(sid, updated);
  }

  private static InterceptorRuleMode selectedMode(
      JComboBox<InterceptorRuleMode> combo, InterceptorRuleMode fallback) {
    Object selected = combo.getSelectedItem();
    if (selected instanceof InterceptorRuleMode mode) return mode;
    return fallback;
  }

  private static InterceptorRule defaultRule(int index) {
    int n = Math.max(1, index);
    return new InterceptorRule(
        true,
        "Rule " + n,
        "",
        InterceptorRuleMode.LIKE,
        "",
        InterceptorRuleMode.LIKE,
        "",
        InterceptorRuleMode.GLOB,
        "");
  }

  private String selectedSoundId() {
    Object selected = actionSoundId.getSelectedItem();
    if (selected instanceof BuiltInSound sound) return sound.name();
    return BuiltInSound.NOTIF_1.name();
  }

  private static String modeLabel(InterceptorRuleMode mode) {
    if (mode == null) return "Like";
    return switch (mode) {
      case ALL -> "All";
      case NONE -> "None";
      case LIKE -> "Like";
      case GLOB -> "Glob";
      case REGEX -> "Regex";
    };
  }

  private enum ServerScopeOption {
    THIS_SERVER("This server"),
    ANY_SERVER("Any server");

    private final String label;

    ServerScopeOption(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static final class RulesTableModel extends AbstractTableModel {
    private static final String[] COLS = {"On", "Why", "Events", "Message", "Nick", "Hostmask"};
    private final List<InterceptorRule> rows = new ArrayList<>();

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
      return column >= 0 && column < COLS.length ? COLS[column] : "";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return "";
      InterceptorRule r = rows.get(rowIndex);
      if (r == null) return "";
      return switch (columnIndex) {
        case 0 -> r.enabled();
        case 1 -> r.label();
        case 2 -> formatEventTypesForDisplay(r.eventTypesCsv());
        case 3 -> summarizeDimension(r.messageMode(), r.messagePattern());
        case 4 -> summarizeDimension(r.nickMode(), r.nickPattern());
        case 5 -> summarizeDimension(r.hostmaskMode(), r.hostmaskPattern());
        default -> "";
      };
    }

    void setRows(List<InterceptorRule> rules) {
      rows.clear();
      if (rules != null) {
        for (InterceptorRule r : rules) {
          if (r == null) continue;
          rows.add(r);
        }
      }
      fireTableDataChanged();
    }

    int addRule(InterceptorRule rule) {
      if (rule == null) return -1;
      rows.add(rule);
      int row = rows.size() - 1;
      fireTableRowsInserted(row, row);
      return row;
    }

    InterceptorRule ruleAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      return rows.get(row);
    }

    void setRule(int row, InterceptorRule updated) {
      if (row < 0 || row >= rows.size() || updated == null) return;
      rows.set(row, updated);
      fireTableRowsUpdated(row, row);
    }

    void removeRow(int row) {
      if (row < 0 || row >= rows.size()) return;
      rows.remove(row);
      fireTableRowsDeleted(row, row);
    }

    List<InterceptorRule> snapshot() {
      return List.copyOf(rows);
    }

    private static String summarizeDimension(InterceptorRuleMode mode, String pattern) {
      String p = Objects.toString(pattern, "").trim();
      if (p.isEmpty()) return "(any)";
      return modeLabel(mode) + ": " + p;
    }
  }

  private static final class HitsTableModel extends AbstractTableModel {
    private static final String[] COLS = {
      "Time", "Server", "From", "Hostmask", "Channel", "Why", "Event", "Message"
    };

    private List<InterceptorHit> rows = List.of();

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
      return column >= 0 && column < COLS.length ? COLS[column] : "";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return "";
      InterceptorHit r = rows.get(rowIndex);
      if (r == null) return "";
      return switch (columnIndex) {
        case 0 -> formatTime(r.at());
        case 1 -> r.serverId();
        case 2 -> r.fromNick();
        case 3 -> r.fromHostmask();
        case 4 -> r.channel();
        case 5 -> r.reason();
        case 6 -> r.eventType();
        case 7 -> r.message();
        default -> "";
      };
    }

    void setRows(List<InterceptorHit> rows) {
      this.rows = rows == null ? List.of() : List.copyOf(rows);
      fireTableDataChanged();
    }

    private static String formatTime(Instant at) {
      if (at == null) return "";
      try {
        return TIME_FMT.format(at);
      } catch (Exception ignored) {
        return at.toString();
      }
    }
  }
}
