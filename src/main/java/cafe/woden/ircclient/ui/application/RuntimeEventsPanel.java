package cafe.woden.ircclient.ui.application;

import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.swing.MigLayout;

/** Generic table panel for displaying rolling runtime diagnostic events with row actions. */
public final class RuntimeEventsPanel extends JPanel {
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss")
          .withLocale(Locale.ROOT)
          .withZone(ZoneId.systemDefault());

  private static final int ACTION_ICON_SIZE = 16;
  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(28, 28);

  private static final int COL_TIME = 0;
  private static final int COL_LEVEL = 1;
  private static final int COL_TYPE = 2;
  private static final int COL_SUMMARY = 3;

  private final Supplier<List<RuntimeDiagnosticEvent>> eventsSupplier;
  private final Runnable clearAllAction;
  private final String exportBaseName;
  private final Flowable<?> refreshTrigger;
  private final RuntimeEventsTableModel model = new RuntimeEventsTableModel();
  private final JTable table = new JTable(model);
  private final JLabel title = new JLabel();
  private final JLabel subtitle = new JLabel();
  private final JLabel rowsLabel = new JLabel("Rows: 0");
  private final JButton detailsButton = new JButton();
  private final JButton refreshButton = new JButton();
  private final JButton clearButton = new JButton();
  private final JButton exportButton = new JButton();
  private final Timer refreshTimer;
  private Disposable refreshTriggerSubscription;

  public RuntimeEventsPanel(
      String titleText,
      String subtitleText,
      Supplier<List<RuntimeDiagnosticEvent>> eventsSupplier) {
    this(titleText, subtitleText, eventsSupplier, null, "runtime-events", null);
  }

  public RuntimeEventsPanel(
      String titleText,
      String subtitleText,
      Supplier<List<RuntimeDiagnosticEvent>> eventsSupplier,
      Runnable clearAllAction,
      String exportBaseName) {
    this(titleText, subtitleText, eventsSupplier, clearAllAction, exportBaseName, null);
  }

  public RuntimeEventsPanel(
      String titleText,
      String subtitleText,
      Supplier<List<RuntimeDiagnosticEvent>> eventsSupplier,
      Runnable clearAllAction,
      String exportBaseName,
      Flowable<?> refreshTrigger) {
    super(new BorderLayout());
    this.eventsSupplier = Objects.requireNonNull(eventsSupplier, "eventsSupplier");
    this.clearAllAction = clearAllAction;
    this.exportBaseName = normalizeExportBaseName(exportBaseName);
    this.refreshTrigger = refreshTrigger;

    title.setText(Objects.toString(titleText, "Events"));
    title.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    subtitle.setText(Objects.toString(subtitleText, ""));
    subtitle.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
    JPanel header = new JPanel(new BorderLayout());
    header.add(title, BorderLayout.NORTH);
    header.add(subtitle, BorderLayout.SOUTH);
    add(header, BorderLayout.NORTH);

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
              if (!e.getValueIsAdjusting()) updateButtons();
            });
    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() < 2) return;
            showDetailsForSelectedRow();
          }
        });

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    configureActionButton(refreshButton, "refresh", "Refresh rows", "Refresh rows");
    configureActionButton(detailsButton, "eye", "Show details for selected row", "Show details");
    configureActionButton(clearButton, "trash", "Clear all rows", "Clear all rows");
    configureActionButton(exportButton, "copy", "Export rows as CSV", "Export rows as CSV");

    JPanel controls = new JPanel(new MigLayout("insets 6 8 8 8, fillx", "[]4[]4[]4[]push[]", "[]"));
    controls.setOpaque(false);
    refreshButton.addActionListener(e -> refreshNow());
    detailsButton.addActionListener(e -> showDetailsForSelectedRow());
    clearButton.addActionListener(
        e -> {
          if (clearAllAction == null) return;
          clearAllAction.run();
          refreshNow();
        });
    exportButton.addActionListener(e -> exportRowsAsCsv());
    controls.add(refreshButton);
    controls.add(detailsButton);
    controls.add(clearButton);
    controls.add(exportButton);
    controls.add(rowsLabel, "alignx right");
    add(controls, BorderLayout.SOUTH);

    refreshTimer = new Timer(1000, e -> refreshNow());
    refreshTimer.setRepeats(true);
    addHierarchyListener(this::onHierarchyChanged);
    startAutoRefresh();

    refreshNow();
    updateButtons();
  }

  public void refreshNow() {
    RuntimeDiagnosticEvent selected = selectedEvent();
    List<RuntimeDiagnosticEvent> rows;
    try {
      rows = eventsSupplier.get();
    } catch (Exception e) {
      rows =
          List.of(
              new RuntimeDiagnosticEvent(
                  java.time.Instant.now(),
                  "ERROR",
                  "RuntimeEventsPanel",
                  "Failed to load events.",
                  Objects.toString(e.getMessage(), "")));
    }
    model.setRows(rows);
    restoreSelection(selected);

    int count = model.getRowCount();
    rowsLabel.setText("Rows: " + count);
    String base = subtitle.getText();
    String prefix = base == null ? "" : base;
    int marker = prefix.indexOf("  |  rows:");
    if (marker >= 0) prefix = prefix.substring(0, marker);
    subtitle.setText(prefix + "  |  rows: " + count);
    updateButtons();
  }

  private void onHierarchyChanged(HierarchyEvent event) {
    if (event == null) return;
    if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0L) return;
    if (!isDisplayable()) {
      stopAutoRefresh();
      return;
    }
    startAutoRefresh();
  }

  private void startAutoRefresh() {
    if (refreshTrigger != null) {
      subscribeRefreshTriggerIfNeeded();
      return;
    }
    if (!refreshTimer.isRunning()) {
      refreshTimer.start();
    }
  }

  private void stopAutoRefresh() {
    if (refreshTriggerSubscription != null) {
      refreshTriggerSubscription.dispose();
      refreshTriggerSubscription = null;
    }
    refreshTimer.stop();
  }

  private void subscribeRefreshTriggerIfNeeded() {
    if (refreshTrigger == null) return;
    if (refreshTriggerSubscription != null && !refreshTriggerSubscription.isDisposed()) return;
    refreshTriggerSubscription =
        refreshTrigger.subscribe(
            __ -> {
              if (SwingUtilities.isEventDispatchThread()) {
                refreshNow();
              } else {
                SwingUtilities.invokeLater(this::refreshNow);
              }
            },
            err -> {
              if (SwingUtilities.isEventDispatchThread()) {
                refreshNow();
              } else {
                SwingUtilities.invokeLater(this::refreshNow);
              }
            });
  }

  private void updateButtons() {
    boolean hasRows = model.getRowCount() > 0;
    detailsButton.setEnabled(selectedEvent() != null);
    clearButton.setEnabled(clearAllAction != null && hasRows);
    exportButton.setEnabled(hasRows);
  }

  private RuntimeDiagnosticEvent selectedEvent() {
    int row = table.getSelectedRow();
    if (row < 0) return null;
    int modelRow = table.convertRowIndexToModel(row);
    return model.rowAt(modelRow);
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
    table.setRowSelectionInterval(viewRow, viewRow);
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

  private void exportRowsAsCsv() {
    if (model.getRowCount() <= 0) return;

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Export Runtime Events");
    chooser.setSelectedFile(new File(defaultExportFileName()));
    int result = chooser.showSaveDialog(this);
    if (result != JFileChooser.APPROVE_OPTION) return;

    Path path = chooser.getSelectedFile().toPath();
    if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
      path = path.resolveSibling(path.getFileName().toString() + ".csv");
    }

    List<RuntimeDiagnosticEvent> rows = model.rows();
    try {
      writeCsv(path, rows);
      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "Exported " + rows.size() + " row(s) to:\n" + path.toAbsolutePath(),
          "Export Complete",
          JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "Export failed:\n" + Objects.toString(e.getMessage(), ""),
          "Export Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private static void configureActionButton(
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

  private String defaultExportFileName() {
    String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    return "ircafe-" + exportBaseName + "-" + ts + ".csv";
  }

  private static String normalizeExportBaseName(String raw) {
    String s = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return "runtime-events";
    return s.replaceAll("[^a-z0-9._-]+", "-");
  }

  private static void writeCsv(Path path, List<RuntimeDiagnosticEvent> rows) throws Exception {
    if (path == null) throw new IllegalArgumentException("Output path is required.");
    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }

    try (var out =
        Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      out.write("time,level,event,summary,details");
      out.newLine();
      if (rows == null || rows.isEmpty()) return;
      for (RuntimeDiagnosticEvent row : rows) {
        if (row == null) continue;
        List<String> cols =
            List.of(
                row.at() == null ? "" : TIME_FMT.format(row.at()),
                Objects.toString(row.level(), ""),
                Objects.toString(row.type(), ""),
                Objects.toString(row.summary(), ""),
                Objects.toString(row.details(), ""));
        out.write(joinCsv(cols));
        out.newLine();
      }
    }
  }

  private static String joinCsv(List<String> cols) {
    if (cols == null || cols.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(cols.size() * 16);
    for (int i = 0; i < cols.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append(csvCell(cols.get(i)));
    }
    return sb.toString();
  }

  private static String csvCell(String value) {
    String s = Objects.toString(value, "");
    boolean needsQuote =
        s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
    if (!needsQuote) return s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
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
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
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
      return rows.indexOf(event);
    }

    List<RuntimeDiagnosticEvent> rows() {
      if (rows.isEmpty()) return List.of();
      return List.copyOf(rows);
    }

    void setRows(List<RuntimeDiagnosticEvent> nextRows) {
      rows.clear();
      if (nextRows != null && !nextRows.isEmpty()) {
        rows.addAll(nextRows);
      }
      fireTableDataChanged();
    }
  }
}
