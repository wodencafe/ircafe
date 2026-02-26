package cafe.woden.ircclient.ui.application;

import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
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

/**
 * Dedicated diagnostics UI for the Application -> JFR node.
 *
 * <p>Layout is split into a {@code Status} tab (gauges) and a {@code JFR Events} tab (event table +
 * row actions).
 */
public final class JfrDiagnosticsPanel extends JPanel {
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss")
          .withLocale(Locale.ROOT)
          .withZone(ZoneId.systemDefault());

  private static final long GB = 1000L * 1000L * 1000L;
  private static final double GC_ALERT_EVENTS_PER_MINUTE = 10.0d;
  private static final int ACTION_ICON_SIZE = 16;
  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(28, 28);
  private static final String GC_EVENT_TYPE = "jdk.GarbageCollection";

  private static final int COL_TIME = 0;
  private static final int COL_LEVEL = 1;
  private static final int COL_TYPE = 2;
  private static final int COL_SUMMARY = 3;

  private final JfrRuntimeEventsService service;
  private final PropertyChangeListener stateListener = __ -> refreshOnEdt();
  private final RuntimeEventsTableModel model = new RuntimeEventsTableModel();
  private final JTable table = new JTable(model);
  private final JCheckBox enabledCheck = new JCheckBox("Enable JFR diagnostics");
  private final JCheckBox pauseRowsCheck = new JCheckBox("Pause event table logging");
  private final JTextField streamValue = newSummaryField();
  private final JTextField cpuMachineValue = newSummaryField();
  private final JTextField cpuJvmUserValue = newSummaryField();
  private final JTextField cpuJvmSystemValue = newSummaryField();
  private final JTextField cpuSampleValue = newSummaryField();
  private final JTextField heapUsedValue = newSummaryField();
  private final JTextField heapCommittedValue = newSummaryField();
  private final JTextField heapMaxValue = newSummaryField();
  private final JTextField heapSampleValue = newSummaryField();
  private final JTextField gcCountValue = newSummaryField();
  private final JTextField gcRateValue = newSummaryField();
  private final JTextField gcLastValue = newSummaryField();
  private final JLabel rowsLabel = new JLabel("Rows: 0");
  private final JButton clearAllRowsButton = new JButton();
  private final JButton clearSelectedRowButton = new JButton();
  private final JButton detailsButton = new JButton();
  private final JButton refreshButton = new JButton();
  private final CircularGauge cpuGauge = new CircularGauge("CPU");
  private final CircularGauge heapGauge = new CircularGauge("Heap");
  private final CircularGauge gcGauge = new CircularGauge("GC Rate");

  private boolean syncingControls;
  private boolean stateListenerRegistered;

  public JfrDiagnosticsPanel(JfrRuntimeEventsService service) {
    super(new BorderLayout(0, 8));
    this.service = service;

    JLabel title = new JLabel("JFR Diagnostics");
    title.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    JLabel subtitle =
        new JLabel("Status gauges (CPU / heap / GC rate) + runtime JFR event table controls.");
    subtitle.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
    JPanel header = new JPanel(new BorderLayout());
    header.add(title, BorderLayout.NORTH);
    header.add(subtitle, BorderLayout.SOUTH);
    add(header, BorderLayout.NORTH);

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Status", buildStatusTab());
    tabs.addTab("JFR Events", buildEventsTab());
    add(tabs, BorderLayout.CENTER);

    configureEventActionButtons();
    installControlActions();
    installTableInteractions();
    applyServiceAvailability();
    startStateSubscription();
    refreshNow();
  }

  public void refreshNow() {
    syncStatus();
    syncRows();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    startStateSubscription();
  }

  @Override
  public void removeNotify() {
    stopStateSubscription();
    super.removeNotify();
  }

  private JPanel buildStatusTab() {
    JPanel root = new JPanel(new BorderLayout(8, 8));
    root.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

    JPanel controls = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[]16[]push[]", "[]"));
    enabledCheck.setOpaque(false);
    pauseRowsCheck.setOpaque(false);
    controls.add(enabledCheck);
    controls.add(pauseRowsCheck);
    root.add(controls, BorderLayout.NORTH);

    JPanel gauges = new JPanel(new GridLayout(1, 3, 10, 0));
    gauges.setOpaque(false);
    gauges.add(wrapGauge(cpuGauge));
    gauges.add(wrapGauge(heapGauge));
    gauges.add(wrapGauge(gcGauge));
    root.add(gauges, BorderLayout.CENTER);

    JPanel detailGrid = new JPanel(new GridLayout(1, 3, 10, 0));
    detailGrid.setOpaque(false);
    detailGrid.add(buildCpuSummaryPanel());
    detailGrid.add(buildHeapSummaryPanel());
    detailGrid.add(buildGcSummaryPanel());
    root.add(detailGrid, BorderLayout.SOUTH);

    return root;
  }

  private JPanel buildEventsTab() {
    JPanel root = new JPanel(new BorderLayout(8, 8));
    root.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

    table.setFillsViewportHeight(true);
    table.setRowSelectionAllowed(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.getTableHeader().setReorderingAllowed(false);
    table.getColumnModel().getColumn(COL_TIME).setPreferredWidth(84);
    table.getColumnModel().getColumn(COL_LEVEL).setPreferredWidth(64);
    table.getColumnModel().getColumn(COL_TYPE).setPreferredWidth(210);
    table.getColumnModel().getColumn(COL_SUMMARY).setPreferredWidth(760);
    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) updateRowButtons();
            });

    JPanel controls =
        new JPanel(new MigLayout("insets 0, fillx, wrap 5", "[]4[]4[]4[]push[]", "[]"));
    controls.add(refreshButton);
    controls.add(detailsButton);
    controls.add(clearSelectedRowButton);
    controls.add(clearAllRowsButton);
    controls.add(rowsLabel, "alignx right");

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);

    root.add(controls, BorderLayout.NORTH);
    root.add(scroll, BorderLayout.CENTER);
    return root;
  }

  private void configureEventActionButtons() {
    configureEventActionButton(
        refreshButton,
        "refresh",
        "Request an immediate runtime sample and refresh rows",
        "Refresh events");
    configureEventActionButton(
        detailsButton, "eye", "Show details for the selected row", "Row details");
    configureEventActionButton(
        clearSelectedRowButton, "close", "Remove the selected event row", "Remove selected row");
    configureEventActionButton(
        clearAllRowsButton, "trash", "Clear all event rows", "Clear all rows");
  }

  private void configureEventActionButton(
      JButton button, String iconName, String tooltip, String accessibleName) {
    if (button == null) return;
    button.setText("");
    button.setIcon(SvgIcons.action(iconName, ACTION_ICON_SIZE));
    button.setDisabledIcon(SvgIcons.actionDisabled(iconName, ACTION_ICON_SIZE));
    button.setToolTipText(tooltip);
    button.setFocusable(false);
    button.setPreferredSize(ACTION_BUTTON_SIZE);
    button.getAccessibleContext().setAccessibleName(accessibleName);
  }

  private JPanel buildCpuSummaryPanel() {
    JPanel panel =
        new JPanel(new MigLayout("insets 6, fillx, wrap 2", "[right]8[grow,fill]", "[]4[]4[]4[]"));
    panel.setBorder(BorderFactory.createTitledBorder("CPU"));
    panel.setOpaque(false);
    addSummaryField(panel, "Machine", cpuMachineValue);
    addSummaryField(panel, "JVM User", cpuJvmUserValue);
    addSummaryField(panel, "JVM System", cpuJvmSystemValue);
    addSummaryField(panel, "Sample", cpuSampleValue);
    return panel;
  }

  private JPanel buildHeapSummaryPanel() {
    JPanel panel =
        new JPanel(new MigLayout("insets 6, fillx, wrap 2", "[right]8[grow,fill]", "[]4[]4[]4[]"));
    panel.setBorder(BorderFactory.createTitledBorder("Heap"));
    panel.setOpaque(false);
    addSummaryField(panel, "Used", heapUsedValue);
    addSummaryField(panel, "Committed", heapCommittedValue);
    addSummaryField(panel, "Max", heapMaxValue);
    addSummaryField(panel, "Sample", heapSampleValue);
    return panel;
  }

  private JPanel buildGcSummaryPanel() {
    JPanel panel =
        new JPanel(new MigLayout("insets 6, fillx, wrap 2", "[right]8[grow,fill]", "[]4[]4[]4[]"));
    panel.setBorder(BorderFactory.createTitledBorder("GC"));
    panel.setOpaque(false);
    addSummaryField(panel, "Events (2m)", gcCountValue);
    addSummaryField(panel, "Rate", gcRateValue);
    addSummaryField(panel, "Last Event", gcLastValue);
    addSummaryField(panel, "Stream", streamValue);
    return panel;
  }

  private static JTextField newSummaryField() {
    JTextField field = new JTextField("n/a");
    field.setEditable(false);
    field.setFocusable(false);
    field.setColumns(14);
    return field;
  }

  private static void addSummaryField(JPanel panel, String label, JTextField field) {
    if (panel == null || field == null) return;
    panel.add(new JLabel(Objects.toString(label, "")));
    panel.add(field, "growx, wmin 0");
  }

  private void installControlActions() {
    enabledCheck.addActionListener(
        e -> {
          if (syncingControls || service == null) return;
          service.setEnabled(enabledCheck.isSelected());
          refreshNow();
        });
    pauseRowsCheck.addActionListener(
        e -> {
          if (syncingControls || service == null) return;
          service.setTableLoggingPaused(pauseRowsCheck.isSelected());
          refreshNow();
        });
    refreshButton.addActionListener(
        e -> {
          if (service != null) service.requestImmediateRefresh();
          refreshNow();
        });
    detailsButton.addActionListener(e -> showDetailsForSelectedRow());
    clearAllRowsButton.addActionListener(
        e -> {
          if (service == null) return;
          service.clearEvents();
          refreshNow();
        });
    clearSelectedRowButton.addActionListener(e -> removeSelectedRow());
  }

  private void installTableInteractions() {
    JPopupMenu popup = new JPopupMenu();
    popup
        .add(new javax.swing.JMenuItem("Details"))
        .addActionListener(e -> showDetailsForSelectedRow());
    popup
        .add(new javax.swing.JMenuItem("Remove Selected Row"))
        .addActionListener(e -> removeSelectedRow());
    popup
        .add(new javax.swing.JMenuItem("Clear All Rows"))
        .addActionListener(
            e -> {
              if (service == null) return;
              service.clearEvents();
              refreshNow();
            });

    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            if (e.getClickCount() >= 2) {
              showDetailsForSelectedRow();
            }
          }

          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
          }

          private void maybeShowPopup(MouseEvent e) {
            if (e == null || !e.isPopupTrigger()) return;
            int row = table.rowAtPoint(e.getPoint());
            if (row >= 0) {
              table.setRowSelectionInterval(row, row);
            }
            updateRowButtons();
            popup.show(table, e.getX(), e.getY());
          }
        });
  }

  private JPanel wrapGauge(CircularGauge gauge) {
    JPanel box = new JPanel(new BorderLayout());
    box.setOpaque(false);
    box.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    box.add(gauge, BorderLayout.CENTER);
    return box;
  }

  private void syncStatus() {
    if (service == null) {
      syncingControls = true;
      try {
        enabledCheck.setSelected(false);
        pauseRowsCheck.setSelected(false);
      } finally {
        syncingControls = false;
      }
      cpuGauge.setGauge("n/a", -1, false, false);
      heapGauge.setGauge("n/a", -1, false, false);
      gcGauge.setGauge("n/a", -1, false, false);
      setSummaryUnavailable("JFR service unavailable");
      return;
    }

    JfrRuntimeEventsService.StatusSnapshot s = service.statusSnapshot();
    syncingControls = true;
    try {
      enabledCheck.setSelected(s.enabled());
      pauseRowsCheck.setSelected(s.tableLoggingPaused());
    } finally {
      syncingControls = false;
    }

    streamValue.setText(s.streamActive() ? "Active" : "Inactive");

    int cpuPercent =
        s.cpuMachineTotalRatio() == null
            ? -1
            : Math.max(0, Math.min(100, (int) Math.round(s.cpuMachineTotalRatio() * 100.0d)));
    cpuGauge.setGauge(
        cpuPercent < 0 ? "n/a" : cpuPercent + "%", cpuPercent, cpuPercent >= 90, false);
    cpuMachineValue.setText(JfrRuntimeEventsService.formatRatio(s.cpuMachineTotalRatio()));
    cpuJvmUserValue.setText(JfrRuntimeEventsService.formatRatio(s.cpuJvmUserRatio()));
    cpuJvmSystemValue.setText(JfrRuntimeEventsService.formatRatio(s.cpuJvmSystemRatio()));
    cpuSampleValue.setText(formatInstant(s.lastCpuSampleAt()));

    int heapPercent = s.runtimeHeapPercent();
    heapGauge.setGauge(
        heapPercent < 0 ? "n/a" : heapPercent + "%", heapPercent, heapPercent >= 90, false);
    heapUsedValue.setText(toGb(s.runtimeUsedBytes()));
    heapCommittedValue.setText(toGb(s.runtimeCommittedBytes()));
    heapMaxValue.setText(s.runtimeMaxBytes() > 0 ? toGb(s.runtimeMaxBytes()) : "n/a");
    heapSampleValue.setText(formatInstant(s.lastRuntimeSampleAt()));

    int gcGaugePercent =
        Math.max(
            0,
            Math.min(
                100,
                (int)
                    Math.round(
                        (s.gcEventsPerMinute() / Math.max(1.0d, GC_ALERT_EVENTS_PER_MINUTE))
                            * 100.0d)));
    boolean pulse =
        s.lastGcEventAt() != null && s.lastGcEventAt().isAfter(Instant.now().minusSeconds(2));
    gcGauge.setGauge(
        String.format(Locale.ROOT, "%.1f/min", s.gcEventsPerMinute()),
        gcGaugePercent,
        s.gcAlert(),
        pulse);
    gcCountValue.setText(Integer.toString(s.gcEventsInWindow()));
    gcRateValue.setText(
        String.format(
            Locale.ROOT, "%.1f/min%s", s.gcEventsPerMinute(), s.gcAlert() ? " (alert)" : ""));
    gcLastValue.setText(formatInstant(s.lastGcEventAt()));
  }

  private void syncRows() {
    RuntimeDiagnosticEvent selected = selectedEvent();
    List<RuntimeDiagnosticEvent> rows = service != null ? service.recentEvents(800) : List.of();
    if (!rows.isEmpty()) {
      rows =
          rows.stream()
              .filter(row -> !GC_EVENT_TYPE.equalsIgnoreCase(Objects.toString(row.type(), "")))
              .toList();
    }
    model.setRows(rows);
    restoreSelection(selected);
    rowsLabel.setText("Rows: " + model.getRowCount());
    updateRowButtons();
  }

  private void applyServiceAvailability() {
    boolean available = service != null;
    enabledCheck.setEnabled(available);
    pauseRowsCheck.setEnabled(available);
    clearAllRowsButton.setEnabled(available);
    refreshButton.setEnabled(available);
  }

  private void setSummaryUnavailable(String streamStatusText) {
    streamValue.setText(Objects.toString(streamStatusText, "n/a"));
    cpuMachineValue.setText("n/a");
    cpuJvmUserValue.setText("n/a");
    cpuJvmSystemValue.setText("n/a");
    cpuSampleValue.setText("n/a");
    heapUsedValue.setText("n/a");
    heapCommittedValue.setText("n/a");
    heapMaxValue.setText("n/a");
    heapSampleValue.setText("n/a");
    gcCountValue.setText("n/a");
    gcRateValue.setText("n/a");
    gcLastValue.setText("n/a");
  }

  private void restoreSelection(RuntimeDiagnosticEvent selected) {
    if (selected == null) {
      table.clearSelection();
      return;
    }
    int modelRow = model.indexOf(selected);
    if (modelRow < 0) {
      table.clearSelection();
      return;
    }
    int viewRow = table.convertRowIndexToView(modelRow);
    if (viewRow < 0) {
      table.clearSelection();
      return;
    }
    if (table.getSelectedRow() != viewRow) {
      table.setRowSelectionInterval(viewRow, viewRow);
    }
  }

  private void startStateSubscription() {
    if (service == null) return;
    if (stateListenerRegistered) return;
    service.addStateListener(stateListener);
    stateListenerRegistered = true;
  }

  private void stopStateSubscription() {
    if (service == null) return;
    if (!stateListenerRegistered) return;
    service.removeStateListener(stateListener);
    stateListenerRegistered = false;
  }

  private void refreshOnEdt() {
    if (SwingUtilities.isEventDispatchThread()) {
      refreshNow();
    } else {
      SwingUtilities.invokeLater(this::refreshNow);
    }
  }

  private void updateRowButtons() {
    boolean hasSelection = selectedEvent() != null;
    detailsButton.setEnabled(hasSelection);
    clearSelectedRowButton.setEnabled(hasSelection && service != null);
  }

  private RuntimeDiagnosticEvent selectedEvent() {
    int row = table.getSelectedRow();
    if (row < 0) return null;
    int modelRow = table.convertRowIndexToModel(row);
    return model.rowAt(modelRow);
  }

  private void removeSelectedRow() {
    RuntimeDiagnosticEvent event = selectedEvent();
    if (event == null || service == null) return;
    service.removeEvent(event);
    refreshNow();
  }

  private void showDetailsForSelectedRow() {
    RuntimeDiagnosticEvent event = selectedEvent();
    if (event == null) return;

    JPanel content = buildDetailPanel(event);
    content.setPreferredSize(new Dimension(860, 560));
    JOptionPane.showMessageDialog(
        SwingUtilities.getWindowAncestor(this),
        content,
        "Event Details",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private static JPanel buildDetailPanel(RuntimeDiagnosticEvent event) {
    JPanel root = new JPanel(new BorderLayout(0, 10));

    JPanel fields =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]10[grow,fill]", "[]4[]4[]4[]"));
    addDetailRow(fields, "Time", event.at() == null ? "" : TIME_FMT.format(event.at()));
    addDetailRow(fields, "Level", Objects.toString(event.level(), ""));
    addDetailRow(fields, "Event type", Objects.toString(event.type(), ""));
    addDetailRow(fields, "Summary", Objects.toString(event.summary(), ""));

    Map<String, String> parsed = parseKeyValueLines(event.details());
    addDetailRowIfPresent(fields, "Timestamp", parsed.get("timestamp"));
    addDetailRowIfPresent(fields, "Source type", parsed.get("sourceType"));
    addDetailRowIfPresent(fields, "Context ID", parsed.get("contextId"));
    addDetailRowIfPresent(fields, "Context Name", parsed.get("contextDisplayName"));
    addDetailRowIfPresent(fields, "Availability", parsed.get("availabilityState"));
    addDetailRowIfPresent(fields, "Payload type", parsed.get("payloadType"));

    JTextArea text = new JTextArea(Objects.toString(event.details(), ""));
    text.setEditable(false);
    text.setLineWrap(false);
    text.setWrapStyleWord(false);
    text.setCaretPosition(0);

    JPanel detailsPanel = new JPanel(new BorderLayout(0, 6));
    detailsPanel.add(new JLabel("Details"), BorderLayout.NORTH);
    detailsPanel.add(new JScrollPane(text), BorderLayout.CENTER);

    root.add(fields, BorderLayout.NORTH);
    root.add(detailsPanel, BorderLayout.CENTER);
    return root;
  }

  private static void addDetailRow(JPanel panel, String label, String value) {
    panel.add(new JLabel(Objects.toString(label, "")));
    JTextArea v = new JTextArea(Objects.toString(value, ""));
    v.setEditable(false);
    v.setLineWrap(true);
    v.setWrapStyleWord(true);
    v.setOpaque(false);
    v.setBorder(null);
    v.setFocusable(false);
    panel.add(v, "growx, wmin 0");
  }

  private static void addDetailRowIfPresent(JPanel panel, String label, String value) {
    String v = Objects.toString(value, "").trim();
    if (v.isEmpty()) return;
    addDetailRow(panel, label, v);
  }

  private static Map<String, String> parseKeyValueLines(String details) {
    String raw = Objects.toString(details, "");
    if (raw.isBlank()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    String[] lines = raw.split("\\R");
    for (String line : lines) {
      String s = Objects.toString(line, "").trim();
      if (s.isEmpty()) continue;
      int idx = s.indexOf('=');
      if (idx <= 0 || idx >= (s.length() - 1)) continue;
      String key = s.substring(0, idx).trim();
      String value = s.substring(idx + 1).trim();
      if (key.isEmpty() || value.isEmpty()) continue;
      out.putIfAbsent(key, value);
    }
    return out;
  }

  private static String formatInstant(Instant at) {
    return at == null ? "n/a" : TIME_FMT.format(at);
  }

  private static String toGb(long bytes) {
    double gb = bytes / (double) GB;
    return String.format(Locale.ROOT, "%.2f GB", gb);
  }

  private static final class RuntimeEventsTableModel extends AbstractTableModel {
    private final List<RuntimeDiagnosticEvent> rows = new ArrayList<>();

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
        case COL_TIME -> "Time";
        case COL_LEVEL -> "Level";
        case COL_TYPE -> "Event";
        case COL_SUMMARY -> "Summary";
        default -> "";
      };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      RuntimeDiagnosticEvent row = rowAt(rowIndex);
      if (row == null) return "";
      return switch (columnIndex) {
        case COL_TIME -> row.at() == null ? "" : TIME_FMT.format(row.at());
        case COL_LEVEL -> Objects.toString(row.level(), "");
        case COL_TYPE -> Objects.toString(row.type(), "");
        case COL_SUMMARY -> Objects.toString(row.summary(), "");
        default -> "";
      };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    RuntimeDiagnosticEvent rowAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      return rows.get(row);
    }

    int indexOf(RuntimeDiagnosticEvent event) {
      if (event == null || rows.isEmpty()) return -1;
      for (int i = 0; i < rows.size(); i++) {
        if (event.equals(rows.get(i))) return i;
      }
      return -1;
    }

    void setRows(List<RuntimeDiagnosticEvent> nextRows) {
      rows.clear();
      if (nextRows != null && !nextRows.isEmpty()) {
        rows.addAll(nextRows);
      }
      fireTableDataChanged();
    }
  }

  private static final class CircularGauge extends JComponent {
    private final String title;
    private String valueLabel = "n/a";
    private int valuePercent = -1;
    private boolean alert;
    private boolean pulse;

    private CircularGauge(String title) {
      this.title = Objects.toString(title, "");
      setOpaque(false);
      setPreferredSize(new Dimension(170, 170));
      setMinimumSize(new Dimension(130, 130));
    }

    private void setGauge(String valueLabel, int valuePercent, boolean alert, boolean pulse) {
      this.valueLabel = Objects.toString(valueLabel, "n/a");
      this.valuePercent = valuePercent;
      this.alert = alert;
      this.pulse = pulse;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (!(g instanceof Graphics2D g2)) return;

      Object oldAa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      Stroke oldStroke = g2.getStroke();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int pad = 12;
      int size = Math.min(getWidth(), getHeight()) - (pad * 2);
      if (size < 20) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
        return;
      }
      int x = (getWidth() - size) / 2;
      int y = (getHeight() - size) / 2;
      int stroke = Math.max(10, size / 9);

      Color track = uiColor("ProgressBar.background", new Color(230, 230, 230));
      Color text = uiColor("Label.foreground", new Color(40, 40, 40));
      Color ok = new Color(46, 170, 85);
      Color warn = new Color(227, 171, 32);
      Color bad = new Color(220, 77, 66);

      Color arcColor =
          alert
              ? bad
              : (valuePercent >= 0 && valuePercent >= 80)
                  ? warn
                  : (valuePercent >= 0)
                      ? ok
                      : uiColor("Label.disabledForeground", new Color(140, 140, 140));

      g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.setColor(track);
      g2.drawArc(x, y, size, size, 225, -270);
      if (valuePercent >= 0) {
        int extent = (int) Math.round(-270.0d * Math.max(0, Math.min(100, valuePercent)) / 100.0d);
        g2.setColor(arcColor);
        g2.drawArc(x, y, size, size, 225, extent);
      }

      if (pulse) {
        g2.setStroke(new BasicStroke(Math.max(2f, stroke * 0.18f)));
        g2.setColor(new Color(246, 199, 48, 180));
        g2.drawOval(x - 3, y - 3, size + 6, size + 6);
      }

      Font baseFont = getFont();
      if (baseFont == null) baseFont = javax.swing.UIManager.getFont("Label.font");
      if (baseFont == null) baseFont = new Font("Dialog", Font.PLAIN, 12);

      g2.setColor(text);
      g2.setFont(baseFont.deriveFont(Font.BOLD, Math.max(14f, size * 0.11f)));
      FontMetrics titleMetrics = g2.getFontMetrics();
      int titleX = (getWidth() - titleMetrics.stringWidth(title)) / 2;
      int titleY = y + Math.max(16, stroke);
      g2.drawString(title, titleX, titleY);

      g2.setFont(baseFont.deriveFont(Font.BOLD, Math.max(18f, size * 0.18f)));
      FontMetrics valueMetrics = g2.getFontMetrics();
      int valueX = (getWidth() - valueMetrics.stringWidth(valueLabel)) / 2;
      int valueY = y + (size / 2) + (valueMetrics.getAscent() / 3);
      g2.drawString(valueLabel, valueX, valueY);

      g2.setStroke(oldStroke);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
    }

    private static Color uiColor(String key, Color fallback) {
      Color c = javax.swing.UIManager.getColor(key);
      return c != null ? c : fallback;
    }
  }
}
