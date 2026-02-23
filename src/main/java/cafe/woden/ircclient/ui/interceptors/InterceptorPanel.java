package cafe.woden.ircclient.ui.interceptors;

import cafe.woden.ircclient.app.interceptors.InterceptorDefinition;
import cafe.woden.ircclient.app.interceptors.InterceptorHit;
import cafe.woden.ircclient.app.interceptors.InterceptorRule;
import cafe.woden.ircclient.app.interceptors.InterceptorRuleMode;
import cafe.woden.ircclient.app.interceptors.InterceptorStore;
import cafe.woden.ircclient.notify.sound.BuiltInSound;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.swing.MigLayout;

/** Editor/view for a single interceptor node. */
public final class InterceptorPanel extends JPanel implements AutoCloseable {
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private static final String RULE_EVENT_TOKEN_HINT =
      "CSV tokens: message, action, notice, mode, join, part, quit, nick, topic, invite, kick, "
          + "ctcp, private-message, private-action, server, error";

  private final InterceptorStore store;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final JLabel title = new JLabel("Interceptor");
  private final JLabel subtitle = new JLabel("Select an interceptor node.");
  private final JLabel status = new JLabel(" ");

  private final JCheckBox enabled = new JCheckBox("Enabled");
  private final JComboBox<ServerScopeOption> serverScope =
      new JComboBox<>(new ServerScopeOption[] {ServerScopeOption.THIS_SERVER, ServerScopeOption.ANY_SERVER});

  private final JComboBox<InterceptorRuleMode> includeMode = new JComboBox<>(InterceptorRuleMode.values());
  private final JTextField includes = new JTextField();
  private final JComboBox<InterceptorRuleMode> excludeMode = new JComboBox<>(InterceptorRuleMode.values());
  private final JTextField excludes = new JTextField();

  private final JCheckBox actionStatusBarEnabled = new JCheckBox("Status bar notice");
  private final JCheckBox actionToastEnabled = new JCheckBox("Desktop toast");
  private final JCheckBox actionSoundEnabled = new JCheckBox("Play sound");
  private final JComboBox<BuiltInSound> actionSoundId = new JComboBox<>(BuiltInSound.values());
  private final JCheckBox actionSoundUseCustom = new JCheckBox("Custom file");
  private final JTextField actionSoundCustomPath = new JTextField();

  private final JCheckBox actionScriptEnabled = new JCheckBox("Run script");
  private final JTextField actionScriptPath = new JTextField();
  private final JTextField actionScriptArgs = new JTextField();
  private final JTextField actionScriptWorkingDirectory = new JTextField();

  private final JButton addRule = new JButton("Add...");
  private final JButton editRule = new JButton("Edit...");
  private final JButton removeRule = new JButton("Remove");
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
    disposables.dispose();
  }

  private void buildHeader() {
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    JPanel header = new JPanel(new MigLayout("insets 8 10 4 10,fillx,wrap 1", "[grow,fill]", "[]2[]"));
    header.add(title, "growx");
    header.add(subtitle, "growx");
    add(header, BorderLayout.NORTH);
  }

  private void buildBody() {
    serverScope.setToolTipText("Match on this server only, or all servers.");
    includes.setToolTipText("Include channel patterns (comma/newline separated). Empty = all channels.");
    excludes.setToolTipText("Exclude channel patterns (comma/newline separated).");

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
              setText(sound.displayName());
            }
            return this;
          }
        });

    actionSoundCustomPath.setToolTipText("Custom sound path relative to runtime config directory.");
    actionScriptPath.setToolTipText("Executable script/program path.");
    actionScriptArgs.setToolTipText("Optional script arguments (quote-aware). Environment variables are injected.");
    actionScriptWorkingDirectory.setToolTipText("Optional working directory for script execution.");

    JPanel general =
        new JPanel(
            new MigLayout(
                "insets 0 10 6 10,fillx,wrap 6",
                "[pref!][pref!][pref!][grow,fill][pref!][grow,fill]",
                "[]4[]4[]"));
    general.add(enabled, "split 2");
    general.add(new JLabel("Server:"), "gapleft 10");
    general.add(serverScope, "w 140!");

    general.add(new JLabel("Channels include:"), "gapleft 12");
    general.add(includeMode, "w 90!");
    general.add(includes, "growx,wrap");

    general.add(new JLabel(""), "skip 3");
    general.add(new JLabel("Channels exclude:"));
    general.add(excludeMode, "w 90!");
    general.add(excludes, "growx,wrap");

    configureRulesTable();
    configureHitsTable();

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Triggers", buildTriggersTab());
    tabs.addTab("Actions", buildActionsTab());
    tabs.addTab("Hits", buildHitsTab());

    JPanel center = new JPanel(new BorderLayout());
    center.add(general, BorderLayout.NORTH);
    center.add(tabs, BorderLayout.CENTER);
    add(center, BorderLayout.CENTER);

    JPanel footer = new JPanel(new BorderLayout());
    footer.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));
    footer.add(status, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
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
                "insets 8 10 8 10,fillx,wrap 4,hidemode 3",
                "[pref!][grow,fill][pref!][grow,fill]",
                "[]6[]6[]6[]"));

    tab.add(actionStatusBarEnabled, "span 2");
    tab.add(actionToastEnabled, "span 2,wrap");

    tab.add(actionSoundEnabled);
    tab.add(actionSoundId, "growx");
    tab.add(actionSoundUseCustom);
    tab.add(actionSoundCustomPath, "growx,wrap");

    tab.add(actionScriptEnabled);
    tab.add(actionScriptPath, "span 3,growx,wrap");

    tab.add(new JLabel("Script args:"));
    tab.add(actionScriptArgs, "span 3,growx,wrap");

    tab.add(new JLabel("Script cwd:"));
    tab.add(actionScriptWorkingDirectory, "span 3,growx,wrap");

    return tab;
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

    actionScriptEnabled.addActionListener(
        e -> {
          refreshActionControlEnabledState();
          saveCurrentDefinition();
        });
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
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            if (e.getClickCount() < 2) return;
            editSelectedRule();
          }
        });

    removeRule.addActionListener(
        e -> {
          int row = selectedRuleModelRow();
          if (row < 0) return;
          rulesModel.removeRow(row);
          saveCurrentDefinition();
          updateRuleButtons();
        });

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
    JTextField ruleEvents = new JTextField(base.eventTypesCsv());
    ruleEvents.setToolTipText(RULE_EVENT_TOKEN_HINT);

    JComboBox<InterceptorRuleMode> messageMode = new JComboBox<>(InterceptorRuleMode.values());
    messageMode.setRenderer(modeComboRenderer());
    messageMode.setSelectedItem(base.messageMode());
    JTextField messagePattern = new JTextField(base.messagePattern());

    JComboBox<InterceptorRuleMode> nickMode = new JComboBox<>(InterceptorRuleMode.values());
    nickMode.setRenderer(modeComboRenderer());
    nickMode.setSelectedItem(base.nickMode());
    JTextField nickPattern = new JTextField(base.nickPattern());

    JComboBox<InterceptorRuleMode> hostmaskMode = new JComboBox<>(InterceptorRuleMode.values());
    hostmaskMode.setRenderer(modeComboRenderer());
    hostmaskMode.setSelectedItem(base.hostmaskMode());
    JTextField hostmaskPattern = new JTextField(base.hostmaskPattern());

    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 4",
                "[pref!][grow,fill][pref!][grow,fill]",
                "[]6[]6[]6[]6[]"));

    panel.add(ruleEnabled, "span 4,wrap");

    panel.add(new JLabel("Label:"));
    panel.add(ruleLabel, "span 3,growx,wrap");

    panel.add(new JLabel("Events:"));
    panel.add(ruleEvents, "span 3,growx,wrap");

    panel.add(new JLabel("Message:"));
    panel.add(messageMode, "w 95!");
    panel.add(messagePattern, "span 2,growx,wrap");

    panel.add(new JLabel("Nick:"));
    panel.add(nickMode, "w 95!");
    panel.add(nickPattern, "span 2,growx,wrap");

    panel.add(new JLabel("Hostmask:"));
    panel.add(hostmaskMode, "w 95!");
    panel.add(hostmaskPattern, "span 2,growx,wrap");

    Window owner = SwingUtilities.getWindowAncestor(this);
    int choice =
        JOptionPane.showConfirmDialog(
            owner,
            panel,
            dialogTitle,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) return null;

    return new InterceptorRule(
        ruleEnabled.isSelected(),
        ruleLabel.getText(),
        ruleEvents.getText(),
        selectedMode(messageMode, InterceptorRuleMode.LIKE),
        messagePattern.getText(),
        selectedMode(nickMode, InterceptorRuleMode.LIKE),
        nickPattern.getText(),
        selectedMode(hostmaskMode, InterceptorRuleMode.GLOB),
        hostmaskPattern.getText());
  }

  private void refreshFromStore() {
    String sid = serverId;
    String iid = interceptorId;
    if (sid.isBlank() || iid.isBlank()) {
      loading = true;
      title.setText("Interceptor");
      subtitle.setText("Select an interceptor node.");
      rulesModel.setRows(List.of());
      hitsModel.setRows(List.of());
      status.setText(" ");
      resetControls();
      loading = false;
      setControlsEnabled(false);
      return;
    }

    InterceptorDefinition def = store.interceptor(sid, iid);
    if (def == null) {
      loading = true;
      title.setText("Interceptor");
      subtitle.setText("Interceptor was removed.");
      rulesModel.setRows(List.of());
      hitsModel.setRows(List.of());
      status.setText(" ");
      resetControls();
      loading = false;
      setControlsEnabled(false);
      return;
    }

    List<InterceptorHit> hits = store.listHits(sid, iid, 2_000);
    ArrayList<InterceptorHit> sorted = new ArrayList<>(hits);
    sorted.sort(
        (a, b) -> {
          Instant aa = a == null ? null : a.at();
          Instant bb = b == null ? null : b.at();
          if (aa == null && bb == null) return 0;
          if (aa == null) return 1;
          if (bb == null) return -1;
          return bb.compareTo(aa);
        });

    loading = true;
    title.setText("Interceptor - " + def.name());
    subtitle.setText(def.scopeAnyServer() ? "Scope: any server" : "Scope: this server");

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
    hitsModel.setRows(sorted);
    status.setText("Hits: " + sorted.size() + "  Rules: " + def.rules().size());

    loading = false;
    setControlsEnabled(true);
    refreshActionControlEnabledState();
    updateRuleButtons();
  }

  private void resetControls() {
    enabled.setSelected(false);
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

    boolean scriptOn = controlsEnabled && actionScriptEnabled.isSelected();
    actionScriptPath.setEnabled(scriptOn);
    actionScriptArgs.setEnabled(scriptOn);
    actionScriptWorkingDirectory.setEnabled(scriptOn);
  }

  private void setControlsEnabled(boolean enabled) {
    controlsEnabled = enabled;

    this.enabled.setEnabled(enabled);
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
            current.name(),
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
        case 2 -> Objects.toString(r.eventTypesCsv(), "");
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
