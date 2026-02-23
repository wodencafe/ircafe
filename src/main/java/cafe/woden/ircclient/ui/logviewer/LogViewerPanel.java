package cafe.woden.ircclient.ui.logviewer;

import cafe.woden.ircclient.logging.viewer.ChatLogViewerMatchMode;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerQuery;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerResult;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerRow;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.util.VirtualThreads;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Insets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-server log viewer panel backed by the chat log database.
 *
 * <p>Searches and exports are performed off the EDT.
 */
public final class LogViewerPanel extends JPanel implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(LogViewerPanel.class);

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private static final int COL_TIME = 0;
  private static final int COL_NICK = 1;
  private static final int COL_MESSAGE = 2;
  private static final int COL_CHANNEL = 3;
  private static final int COL_HOSTMASK = 4;
  private static final int COL_KIND = 5;
  private static final int COL_DIRECTION = 6;
  private static final int COL_MESSAGE_ID = 7;
  private static final int COL_TAGS = 8;
  private static final int COL_META = 9;

  private static final int[] OPTIONAL_COLUMNS = {
      COL_HOSTMASK,
      COL_KIND,
      COL_DIRECTION,
      COL_MESSAGE_ID,
      COL_TAGS,
      COL_META
  };

  private final ChatLogViewerService service;
  private final ExecutorService exec = VirtualThreads.newSingleThreadExecutor("ircafe-log-viewer");
  private final AtomicLong requestSeq = new AtomicLong(0);

  private Future<?> runningTask;

  private final JLabel title = new JLabel("Log Viewer");
  private final JLabel subtitle = new JLabel("Select filters and click Search.");
  private final JLabel status = new JLabel(" ");
  private final JPanel north = new JPanel(new BorderLayout());
  private final JPanel footer = new JPanel(new BorderLayout());

  private final javax.swing.JTextField nickField = new javax.swing.JTextField();
  private final JComboBox<ChatLogViewerMatchMode> nickMode = new JComboBox<>(ChatLogViewerMatchMode.values());
  private final javax.swing.JTextField hostmaskField = new javax.swing.JTextField();
  private final JComboBox<ChatLogViewerMatchMode> hostmaskMode = new JComboBox<>(ChatLogViewerMatchMode.values());
  private final javax.swing.JTextField channelField = new javax.swing.JTextField();
  private final JComboBox<ChatLogViewerMatchMode> channelMode = new JComboBox<>(ChatLogViewerMatchMode.values());
  private final JComboBox<DateRangePreset> datePreset = new JComboBox<>(DateRangePreset.values());

  private final JLabel fromLabel = new JLabel("From:");
  private final JLabel toLabel = new JLabel("To:");
  private final JSpinner fromSpinner;
  private final JSpinner toSpinner;
  private final JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(500, 10, 10_000, 10));

  private final JButton searchButton = new JButton("Search");
  private final JButton resetButton = new JButton("Reset");
  private final JButton exportButton = new JButton("Export...");
  private final JButton columnsButton = new JButton("Columns");
  private final JPanel advancedPanel = new JPanel(new MigLayout(
      "insets 2 18 0 0,fillx,hidemode 3",
      "[right][grow,fill][pref!][grow,fill]",
      "[]"));

  private final LogViewerTableModel model = new LogViewerTableModel();
  private final JTable table = new JTable(model);
  private final TableRowSorter<LogViewerTableModel> sorter = new TableRowSorter<>(model);
  private final LinkedHashMap<Integer, TableColumn> allColumnByModelIndex = new LinkedHashMap<>();

  private volatile String serverId = "";

  public LogViewerPanel(ChatLogViewerService service) {
    super(new BorderLayout());
    this.service = Objects.requireNonNull(service, "service");

    Date now = new Date();
    Date dayAgo = new Date(Math.max(0L, now.getTime() - Duration.ofDays(1).toMillis()));
    fromSpinner = new JSpinner(new SpinnerDateModel(dayAgo, null, null, java.util.Calendar.MINUTE));
    toSpinner = new JSpinner(new SpinnerDateModel(now, null, null, java.util.Calendar.MINUTE));
    fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd HH:mm:ss"));
    toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd HH:mm:ss"));
    datePreset.setSelectedItem(DateRangePreset.ALL_TIME);

    buildHeader();
    add(north, BorderLayout.NORTH);
    buildTable();
    buildFilters();
    buildStatusBar();
    add(footer, BorderLayout.SOUTH);
    installListeners();
    refreshAvailability();
  }

  public void setServerId(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (Objects.equals(this.serverId, sid)) return;
    this.serverId = sid;
    updateHeader();
    model.setRows(List.of());
    updateButtons(false);
    if (!sid.isEmpty() && service.enabled()) {
      runSearch(true);
    }
  }

  @Override
  public void close() {
    requestSeq.incrementAndGet();
    Future<?> f = runningTask;
    if (f != null) {
      try {
        f.cancel(true);
      } catch (Exception ignored) {
      }
    }
    exec.shutdownNow();
  }

  private void buildHeader() {
    title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
    configureTopActionButtons();

    JPanel header = new JPanel(new MigLayout(
        "insets 8 10 6 10,fillx,wrap 7",
        "[grow,fill][right][pref!][pref!][pref!][pref!][pref!]",
        "[]2[]"));
    header.add(title, "growx");
    header.add(new JLabel("Max rows:"), "");
    header.add(limitSpinner, "w 84!");
    header.add(searchButton, "sg viewer-btn");
    header.add(resetButton, "sg viewer-btn");
    header.add(columnsButton, "sg viewer-btn");
    header.add(exportButton, "sg viewer-btn");
    header.add(subtitle, "span 7,growx");
    north.add(header, BorderLayout.CENTER);
  }

  private void buildFilters() {
    JPanel filters = new JPanel(new MigLayout(
        "insets 4 10 8 10,fillx,wrap 1,hidemode 3",
        "[grow,fill]",
        "[]2[]2[]"));

    nickMode.setRenderer(modeRenderer());
    hostmaskMode.setRenderer(modeRenderer());
    channelMode.setRenderer(modeRenderer());
    datePreset.setRenderer(datePresetRenderer());
    nickMode.setSelectedItem(ChatLogViewerMatchMode.CONTAINS);
    hostmaskMode.setSelectedItem(ChatLogViewerMatchMode.CONTAINS);
    channelMode.setSelectedItem(ChatLogViewerMatchMode.CONTAINS);

    nickField.setToolTipText("Filter by nick (quick filter).");
    nickField.putClientProperty("JTextField.placeholderText", "Nick filter");
    hostmaskField.setToolTipText("Filter by full hostmask when available.");
    hostmaskField.putClientProperty("JTextField.placeholderText", "Hostmask filter");
    channelField.setToolTipText("Filter by channel/target.");
    channelField.putClientProperty("JTextField.placeholderText", "Channel filter (e.g. #ircafe)");
    datePreset.setToolTipText("Date window for search.");

    JPanel quickRow = new JPanel(new MigLayout(
        "insets 0,fillx,hidemode 3",
        "[right][grow,fill][pref!][right][grow,fill][pref!]",
        "[]"));
    quickRow.add(new JLabel("Nick:"), "");
    quickRow.add(nickField, "pushx,growx");
    quickRow.add(nickMode, "w 88!");
    quickRow.add(new JLabel("Channel:"), "");
    quickRow.add(channelField, "pushx,growx");
    quickRow.add(channelMode, "w 88!");
    filters.add(quickRow, "growx");

    JPanel dateRow = new JPanel(new MigLayout(
        "insets 0,fillx,hidemode 3",
        "[right][pref!][right][pref!][right][pref!][grow,fill]",
        "[]"));
    dateRow.add(new JLabel("Range:"), "");
    dateRow.add(datePreset, "w 100!");
    dateRow.add(fromLabel, "");
    dateRow.add(fromSpinner, "w 145!");
    dateRow.add(toLabel, "");
    dateRow.add(toSpinner, "w 145!");
    dateRow.add(new JLabel(""), "pushx,growx");
    filters.add(dateRow, "growx");

    advancedPanel.add(new JLabel("Hostmask:"), "");
    advancedPanel.add(hostmaskField, "pushx,growx");
    advancedPanel.add(hostmaskMode, "w 88!");
    advancedPanel.add(new JLabel(""), "pushx,growx");
    filters.add(advancedPanel, "growx");

    footer.add(filters, BorderLayout.NORTH);
    updateDatePresetUi();
  }

  private void buildTable() {
    table.setFillsViewportHeight(true);
    table.setRowSelectionAllowed(true);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.getTableHeader().setReorderingAllowed(false);
    table.getTableHeader().setToolTipText("Click a column header to sort.");

    table.setRowSorter(sorter);
    sorter.setSortsOnUpdates(true);
    sorter.setSortKeys(List.of(new RowSorter.SortKey(COL_TIME, SortOrder.DESCENDING)));
    for (int i = 0; i < model.getColumnCount(); i++) {
      sorter.setSortable(i, true);
    }

    captureColumns();
    for (int col : OPTIONAL_COLUMNS) {
      hideColumn(col);
    }
    applyPreferredWidths();

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);
  }

  private void buildStatusBar() {
    JPanel south = new JPanel(new BorderLayout());
    south.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
    south.add(status, BorderLayout.CENTER);
    footer.add(south, BorderLayout.SOUTH);
  }

  private void installListeners() {
    datePreset.addActionListener(e -> updateDatePresetUi());
    searchButton.addActionListener(e -> runSearch(false));
    resetButton.addActionListener(e -> resetFiltersAndSearch());
    exportButton.addActionListener(e -> exportVisibleRows());
    columnsButton.addActionListener(e -> showColumnsMenu(columnsButton));

    nickField.addActionListener(e -> runSearch(false));
    hostmaskField.addActionListener(e -> runSearch(false));
    channelField.addActionListener(e -> runSearch(false));
  }

  private void refreshAvailability() {
    if (!service.enabled()) {
      title.setText("Log Viewer");
      subtitle.setText("Chat logging is disabled.");
      status.setText("Enable ircafe.logging.enabled=true to use this view.");
      setControlsEnabled(false);
      model.setRows(List.of());
      return;
    }
    setControlsEnabled(true);
    updateButtons(false);
  }

  private void setControlsEnabled(boolean enabled) {
    nickField.setEnabled(enabled);
    nickMode.setEnabled(enabled);
    hostmaskField.setEnabled(enabled);
    hostmaskMode.setEnabled(enabled);
    channelField.setEnabled(enabled);
    channelMode.setEnabled(enabled);
    datePreset.setEnabled(enabled);
    limitSpinner.setEnabled(enabled);
    fromSpinner.setEnabled(enabled && selectedDatePreset() == DateRangePreset.CUSTOM);
    toSpinner.setEnabled(enabled && selectedDatePreset() == DateRangePreset.CUSTOM);
    updateButtons(false);
  }

  private void updateHeader() {
    String sid = serverId;
    if (sid.isEmpty()) {
      title.setText("Log Viewer");
      subtitle.setText("Select a server node first.");
      status.setText(" ");
      return;
    }
    title.setText("Log Viewer - " + sid);
    subtitle.setText("Query persisted logs for " + sid + ".");
    status.setText(" ");
  }

  private void resetFiltersAndSearch() {
    nickField.setText("");
    hostmaskField.setText("");
    channelField.setText("");
    nickMode.setSelectedItem(ChatLogViewerMatchMode.CONTAINS);
    hostmaskMode.setSelectedItem(ChatLogViewerMatchMode.CONTAINS);
    channelMode.setSelectedItem(ChatLogViewerMatchMode.CONTAINS);
    datePreset.setSelectedItem(DateRangePreset.ALL_TIME);
    updateDatePresetUi();
    Date now = new Date();
    fromSpinner.setValue(new Date(Math.max(0L, now.getTime() - Duration.ofDays(1).toMillis())));
    toSpinner.setValue(now);
    limitSpinner.setValue(500);
    runSearch(false);
  }

  private void runSearch(boolean auto) {
    if (!service.enabled()) return;
    String sid = serverId;
    if (sid.isEmpty()) {
      status.setText("Select a server first.");
      return;
    }

    ChatLogViewerQuery query = buildQuery(sid);
    long req = requestSeq.incrementAndGet();

    Future<?> prev = runningTask;
    if (prev != null) {
      try {
        prev.cancel(true);
      } catch (Exception ignored) {
      }
    }

    setBusy(true, auto ? "Loading logs..." : "Searching logs...");
    runningTask = exec.submit(() -> {
      long startedNs = System.nanoTime();
      try {
        ChatLogViewerResult result = service.search(query);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedNs).toMillis();
        SwingUtilities.invokeLater(() -> applySearchResult(req, query, result, elapsedMs));
      } catch (IllegalArgumentException ex) {
        SwingUtilities.invokeLater(() -> applySearchError(req, ex.getMessage()));
      } catch (Exception ex) {
        log.warn("[ircafe] log viewer search failed", ex);
        SwingUtilities.invokeLater(() -> applySearchError(req, "Search failed: " + ex.getMessage()));
      }
    });
  }

  private ChatLogViewerQuery buildQuery(String sid) {
    Long fromMs = null;
    Long toMs = null;
    DateRangePreset preset = selectedDatePreset();
    long now = System.currentTimeMillis();
    if (preset == DateRangePreset.CUSTOM) {
      Date from = (Date) fromSpinner.getValue();
      Date to = (Date) toSpinner.getValue();
      if (from != null) fromMs = from.getTime();
      if (to != null) toMs = to.getTime();
    } else if (preset == DateRangePreset.LAST_1_HOUR) {
      fromMs = Math.max(0L, now - Duration.ofHours(1).toMillis());
      toMs = now;
    } else if (preset == DateRangePreset.LAST_24_HOURS) {
      fromMs = Math.max(0L, now - Duration.ofHours(24).toMillis());
      toMs = now;
    } else if (preset == DateRangePreset.LAST_7_DAYS) {
      fromMs = Math.max(0L, now - Duration.ofDays(7).toMillis());
      toMs = now;
    }

    int limit = ((Number) limitSpinner.getValue()).intValue();
    return new ChatLogViewerQuery(
        sid,
        nickField.getText(),
        selectedMode(nickMode),
        hostmaskField.getText(),
        selectedMode(hostmaskMode),
        channelField.getText(),
        selectedMode(channelMode),
        fromMs,
        toMs,
        limit
    );
  }

  private void applySearchResult(long req, ChatLogViewerQuery query, ChatLogViewerResult result, long elapsedMs) {
    if (req != requestSeq.get()) return;

    model.setRows(result.rows());
    sorter.sort();
    updateButtons(false);

    int shown = result.rows().size();
    int limit = query.limit();
    StringBuilder sb = new StringBuilder(96);
    sb.append("Showing ").append(shown).append(" row(s)");
    if (result.scannedRows() > 0) {
      sb.append(" (scanned ").append(result.scannedRows()).append(")");
    }
    if (result.truncated()) {
      sb.append(" - limited");
      if (result.scanCapped()) {
        sb.append(" by scan cap");
      }
      sb.append(".");
    } else {
      sb.append(".");
    }
    sb.append(" Search took ").append(elapsedMs).append(" ms.");
    status.setText(sb.toString());

    subtitle.setText("Rows: " + shown + " (max " + limit + ")");
    setBusy(false, "");
  }

  private void applySearchError(long req, String message) {
    if (req != requestSeq.get()) return;
    setBusy(false, "");
    status.setText(Objects.toString(message, "Search failed."));
  }

  private void setBusy(boolean busy, String statusText) {
    Cursor c = busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor();
    setCursor(c);
    table.setCursor(c);
    searchButton.setEnabled(!busy && service.enabled() && !serverId.isEmpty());
    resetButton.setEnabled(!busy && service.enabled());
    columnsButton.setEnabled(!busy && service.enabled());
    exportButton.setEnabled(!busy && service.enabled() && model.getRowCount() > 0);
    if (statusText != null && !statusText.isBlank()) {
      status.setText(statusText);
    }
  }

  private void updateButtons(boolean busy) {
    searchButton.setEnabled(!busy && service.enabled() && !serverId.isEmpty());
    resetButton.setEnabled(!busy && service.enabled());
    columnsButton.setEnabled(!busy && service.enabled());
    exportButton.setEnabled(!busy && service.enabled() && model.getRowCount() > 0);
  }

  private void configureTopActionButtons() {
    limitSpinner.setToolTipText("Maximum rows returned per search.");
    configureTopButton(searchButton, "check", "Search logs");
    configureTopButton(resetButton, "refresh", "Reset all filters");
    configureTopButton(columnsButton, "settings", "Choose visible columns");
    configureTopButton(exportButton, "copy", "Export visible rows to CSV");
  }

  private static void configureTopButton(JButton button, String iconName, String tooltip) {
    if (button == null) return;
    button.setText("");
    button.setIcon(SvgIcons.action(iconName, 16));
    button.setDisabledIcon(SvgIcons.actionDisabled(iconName, 16));
    button.setMargin(new Insets(2, 6, 2, 6));
    button.setToolTipText(tooltip);
    button.setFocusable(false);
  }

  private void exportVisibleRows() {
    if (model.getRowCount() <= 0) {
      status.setText("No rows to export.");
      return;
    }

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Export Logs");
    chooser.setSelectedFile(new java.io.File(defaultExportFileName()));
    int result = chooser.showSaveDialog(this);
    if (result != JFileChooser.APPROVE_OPTION) return;

    Path path = chooser.getSelectedFile().toPath();
    if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
      path = path.resolveSibling(path.getFileName().toString() + ".csv");
    }

    ExportSnapshot snapshot = captureExportSnapshot();
    if (snapshot.rows().isEmpty()) {
      status.setText("No visible rows to export.");
      return;
    }

    setBusy(true, "Exporting " + snapshot.rows().size() + " row(s)...");
    final Path outPath = path;
    runningTask = exec.submit(() -> {
      try {
        writeCsv(outPath, snapshot);
        SwingUtilities.invokeLater(() -> {
          setBusy(false, "");
          status.setText("Exported " + snapshot.rows().size() + " row(s) to " + outPath.toAbsolutePath());
        });
      } catch (Exception ex) {
        log.warn("[ircafe] log viewer export failed", ex);
        SwingUtilities.invokeLater(() -> {
          setBusy(false, "");
          status.setText("Export failed: " + ex.getMessage());
          JOptionPane.showMessageDialog(
              LogViewerPanel.this,
              "Export failed:\n" + ex.getMessage(),
              "Log Export Error",
              JOptionPane.ERROR_MESSAGE
          );
        });
      }
    });
  }

  private ExportSnapshot captureExportSnapshot() {
    TableColumnModel cm = table.getColumnModel();
    ArrayList<Integer> modelCols = new ArrayList<>();
    ArrayList<String> headers = new ArrayList<>();
    for (int i = 0; i < cm.getColumnCount(); i++) {
      TableColumn tc = cm.getColumn(i);
      int mi = tc.getModelIndex();
      modelCols.add(mi);
      headers.add(model.getColumnName(mi));
    }

    ArrayList<List<String>> rows = new ArrayList<>();
    for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
      int modelRow = table.convertRowIndexToModel(viewRow);
      ArrayList<String> row = new ArrayList<>(modelCols.size());
      for (int mi : modelCols) {
        row.add(Objects.toString(model.getValueAt(modelRow, mi), ""));
      }
      rows.add(List.copyOf(row));
    }
    return new ExportSnapshot(List.copyOf(headers), List.copyOf(rows));
  }

  private static void writeCsv(Path path, ExportSnapshot snapshot) throws Exception {
    if (path == null) throw new IllegalArgumentException("Output path is required.");
    if (snapshot == null) throw new IllegalArgumentException("Export snapshot is required.");
    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }

    try (var out = Files.newBufferedWriter(
        path,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
    )) {
      out.write(joinCsv(snapshot.headers()));
      out.newLine();
      for (List<String> row : snapshot.rows()) {
        out.write(joinCsv(row));
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
    boolean needsQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
    if (!needsQuote) return s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private String defaultExportFileName() {
    String sid = serverId.isBlank() ? "server" : serverId.replaceAll("[^A-Za-z0-9._-]+", "_");
    String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    return "ircafe-logs-" + sid + "-" + ts + ".csv";
  }

  private void showColumnsMenu(Component invoker) {
    JPopupMenu menu = new JPopupMenu();
    for (int modelIndex : OPTIONAL_COLUMNS) {
      String name = model.getColumnName(modelIndex);
      JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, isColumnVisible(modelIndex));
      item.addActionListener(e -> {
        if (item.isSelected()) {
          showColumn(modelIndex);
        } else {
          hideColumn(modelIndex);
        }
      });
      menu.add(item);
    }
    menu.show(invoker, 0, invoker.getHeight());
  }

  private void captureColumns() {
    TableColumnModel cm = table.getColumnModel();
    allColumnByModelIndex.clear();
    for (int i = 0; i < cm.getColumnCount(); i++) {
      TableColumn tc = cm.getColumn(i);
      allColumnByModelIndex.put(tc.getModelIndex(), tc);
    }
  }

  private boolean isColumnVisible(int modelIndex) {
    TableColumnModel cm = table.getColumnModel();
    for (int i = 0; i < cm.getColumnCount(); i++) {
      if (cm.getColumn(i).getModelIndex() == modelIndex) return true;
    }
    return false;
  }

  private void hideColumn(int modelIndex) {
    TableColumnModel cm = table.getColumnModel();
    for (int i = 0; i < cm.getColumnCount(); i++) {
      TableColumn tc = cm.getColumn(i);
      if (tc.getModelIndex() != modelIndex) continue;
      cm.removeColumn(tc);
      return;
    }
  }

  private void showColumn(int modelIndex) {
    if (isColumnVisible(modelIndex)) return;
    TableColumn tc = allColumnByModelIndex.get(modelIndex);
    if (tc == null) return;
    TableColumnModel cm = table.getColumnModel();
    cm.addColumn(tc);
    reorderVisibleColumns();
    applyPreferredWidth(tc.getModelIndex(), tc);
  }

  private void reorderVisibleColumns() {
    TableColumnModel cm = table.getColumnModel();
    int desiredView = 0;
    for (int modelIndex = 0; modelIndex < model.getColumnCount(); modelIndex++) {
      int current = viewIndexForModel(modelIndex, cm);
      if (current < 0) continue;
      if (current != desiredView) {
        cm.moveColumn(current, desiredView);
      }
      desiredView++;
    }
  }

  private static int viewIndexForModel(int modelIndex, TableColumnModel cm) {
    for (int i = 0; i < cm.getColumnCount(); i++) {
      if (cm.getColumn(i).getModelIndex() == modelIndex) return i;
    }
    return -1;
  }

  private void applyPreferredWidths() {
    TableColumnModel cm = table.getColumnModel();
    for (int i = 0; i < cm.getColumnCount(); i++) {
      TableColumn tc = cm.getColumn(i);
      applyPreferredWidth(tc.getModelIndex(), tc);
    }
  }

  private static void applyPreferredWidth(int modelIndex, TableColumn tc) {
    if (tc == null) return;
    switch (modelIndex) {
      case COL_TIME -> tc.setPreferredWidth(165);
      case COL_NICK -> tc.setPreferredWidth(120);
      case COL_MESSAGE -> tc.setPreferredWidth(640);
      case COL_CHANNEL -> tc.setPreferredWidth(160);
      case COL_HOSTMASK -> tc.setPreferredWidth(250);
      case COL_KIND -> tc.setPreferredWidth(90);
      case COL_DIRECTION -> tc.setPreferredWidth(95);
      case COL_MESSAGE_ID -> tc.setPreferredWidth(230);
      case COL_TAGS -> tc.setPreferredWidth(320);
      case COL_META -> tc.setPreferredWidth(420);
      default -> {}
    }
  }

  private static DefaultListCellRenderer modeRenderer() {
    return new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
          javax.swing.JList<?> list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus
      ) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof ChatLogViewerMatchMode mode) {
          setText(modeLabel(mode));
        }
        return this;
      }
    };
  }

  private static String modeLabel(ChatLogViewerMatchMode mode) {
    if (mode == null) return "Like";
    return switch (mode) {
      case CONTAINS -> "Like";
      case GLOB -> "Glob (* ?)";
      case REGEX -> "Regex";
    };
  }

  private static ChatLogViewerMatchMode selectedMode(JComboBox<ChatLogViewerMatchMode> combo) {
    Object o = combo.getSelectedItem();
    if (o instanceof ChatLogViewerMatchMode mode) return mode;
    return ChatLogViewerMatchMode.CONTAINS;
  }

  private static DefaultListCellRenderer datePresetRenderer() {
    return new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
          javax.swing.JList<?> list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus
      ) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof DateRangePreset preset) {
          setText(preset.label);
        }
        return this;
      }
    };
  }

  private DateRangePreset selectedDatePreset() {
    Object o = datePreset.getSelectedItem();
    if (o instanceof DateRangePreset preset) return preset;
    return DateRangePreset.ALL_TIME;
  }

  private void updateDatePresetUi() {
    boolean custom = selectedDatePreset() == DateRangePreset.CUSTOM;
    fromLabel.setVisible(custom);
    fromSpinner.setVisible(custom);
    toLabel.setVisible(custom);
    toSpinner.setVisible(custom);
    boolean enabled = service.enabled();
    fromSpinner.setEnabled(enabled && custom);
    toSpinner.setEnabled(enabled && custom);
    revalidate();
    repaint();
  }

  private static String formatTags(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(tags.size() * 16);
    boolean first = true;
    for (Map.Entry<String, String> e : tags.entrySet()) {
      if (!first) sb.append("; ");
      String key = Objects.toString(e.getKey(), "").trim();
      String val = Objects.toString(e.getValue(), "").trim();
      if (val.isEmpty()) sb.append(key);
      else sb.append(key).append('=').append(val);
      first = false;
    }
    return sb.toString();
  }

  private enum DateRangePreset {
    ALL_TIME("All time"),
    LAST_1_HOUR("Last 1h"),
    LAST_24_HOURS("Last 24h"),
    LAST_7_DAYS("Last 7d"),
    CUSTOM("Custom");

    private final String label;

    DateRangePreset(String label) {
      this.label = label;
    }
  }

  private static final class LogViewerTableModel extends AbstractTableModel {
    private static final String[] COLS = {
        "Time",
        "Nick",
        "Message",
        "Channel",
        "Hostmask",
        "Kind",
        "Direction",
        "Message ID",
        "Message Tags",
        "Meta"
    };

    private List<ChatLogViewerRow> rows = List.of();

    void setRows(List<ChatLogViewerRow> rows) {
      this.rows = (rows == null) ? List.of() : List.copyOf(rows);
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
      return (column >= 0 && column < COLS.length) ? COLS[column] : "";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return "";
      ChatLogViewerRow row = rows.get(rowIndex);
      if (row == null) return "";

      return switch (columnIndex) {
        case COL_TIME -> formatTs(row.tsEpochMs());
        case COL_NICK -> row.fromNick();
        case COL_MESSAGE -> row.text();
        case COL_CHANNEL -> row.target();
        case COL_HOSTMASK -> row.hostmask();
        case COL_KIND -> row.kind().name();
        case COL_DIRECTION -> row.direction().name();
        case COL_MESSAGE_ID -> row.messageId();
        case COL_TAGS -> formatTags(row.ircv3Tags());
        case COL_META -> row.metaJson();
        default -> "";
      };
    }

    private static String formatTs(long epochMs) {
      if (epochMs <= 0L) return "";
      try {
        return TS_FMT.format(java.time.Instant.ofEpochMilli(epochMs));
      } catch (Exception e) {
        return String.valueOf(epochMs);
      }
    }
  }

  private record ExportSnapshot(List<String> headers, List<List<String>> rows) {}
}
