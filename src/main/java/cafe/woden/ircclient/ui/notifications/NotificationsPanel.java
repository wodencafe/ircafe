package cafe.woden.ircclient.ui.notifications;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.notifications.NotificationStore.HighlightEvent;
import cafe.woden.ircclient.notifications.NotificationStore.IrcEventRuleEvent;
import cafe.woden.ircclient.notifications.NotificationStore.NotificationEvent;
import cafe.woden.ircclient.notifications.NotificationStore.RuleMatchEvent;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Swing panel that displays per-server highlight notifications. */
public class NotificationsPanel extends JPanel implements AutoCloseable {

  private static final int COL_TIME = 0;
  private static final int COL_CHANNEL = 1;
  private static final int COL_FROM = 2;
  private static final int COL_MATCH = 3;
  private static final int COL_SNIPPET = 4;

  private final NotificationStore store;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final NotificationsTableModel model = new NotificationsTableModel();

  private final JLabel title = new JLabel("Notifications");
  private final JTable table = new JTable(model);
  private final JPopupMenu rowMenu = new JPopupMenu();
  private final JMenuItem jumpToMessageMenuItem = new JMenuItem("Jump to message");
  private final JMenuItem clearSelectedMenuItem = new JMenuItem("Clear selected row(s)");
  private final JMenuItem clearAllMenuItem = new JMenuItem("Clear all rows");
  private final JMenuItem exportSelectedMenuItem = new JMenuItem("Export selected to CSV");
  private final JMenuItem exportAllMenuItem = new JMenuItem("Export all to CSV");

  private volatile String serverId;
  private volatile Consumer<TargetRef> onSelectTarget;
  private volatile BiConsumer<TargetRef, String> onJumpToMessage = (target, messageId) -> {};

  public NotificationsPanel(NotificationStore store) {
    this(store, null, null);
  }

  public NotificationsPanel(
      NotificationStore store, String serverId, Consumer<TargetRef> onSelectTarget) {
    super(new BorderLayout());
    this.store = Objects.requireNonNull(store, "store");
    this.serverId = serverId;
    this.onSelectTarget = onSelectTarget;

    title.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    add(title, BorderLayout.NORTH);

    table.setFillsViewportHeight(true);
    table.setRowSelectionAllowed(true);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.setAutoCreateRowSorter(false);
    table.getTableHeader().setReorderingAllowed(false);
    table.getColumnModel().getColumn(COL_TIME).setPreferredWidth(150);
    table.getColumnModel().getColumn(COL_CHANNEL).setPreferredWidth(180);
    table.getColumnModel().getColumn(COL_FROM).setPreferredWidth(160);
    table.getColumnModel().getColumn(COL_MATCH).setPreferredWidth(200);
    table.getColumnModel().getColumn(COL_SNIPPET).setPreferredWidth(600);
    table.getSelectionModel().addListSelectionListener(event -> updateActionState());

    // Render channel names as "link-like" text.
    table.getColumnModel().getColumn(COL_CHANNEL).setCellRenderer(new LinkCellRenderer());

    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e == null || e.getButton() != MouseEvent.BUTTON1 || e.isPopupTrigger()) return;
            int viewRow = table.rowAtPoint(e.getPoint());
            int viewCol = table.columnAtPoint(e.getPoint());
            if (viewRow < 0 || viewCol < 0) return;
            int modelCol = table.convertColumnIndexToModel(viewCol);
            if (modelCol != COL_CHANNEL) return;

            Row row = rowAtView(viewRow);
            TargetRef target = targetRefForRow(row);
            Consumer<TargetRef> cb = NotificationsPanel.this.onSelectTarget;
            if (target != null && cb != null) {
              cb.accept(target);
            }
          }

          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowRowMenu(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowRowMenu(e);
          }
        });

    table.addMouseMotionListener(
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            int viewRow = table.rowAtPoint(e.getPoint());
            int viewCol = table.columnAtPoint(e.getPoint());
            int modelCol = viewCol < 0 ? -1 : table.convertColumnIndexToModel(viewCol);
            boolean overLink = viewRow >= 0 && modelCol == COL_CHANNEL;
            table.setCursor(
                overLink
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
          }
        });

    configureMenuItem(jumpToMessageMenuItem, "chat");
    configureMenuItem(clearSelectedMenuItem, "trash");
    configureMenuItem(clearAllMenuItem, "reset");
    configureMenuItem(exportSelectedMenuItem, "copy");
    configureMenuItem(exportAllMenuItem, "copy");
    jumpToMessageMenuItem.addActionListener(event -> jumpToSelectedMessage());
    clearSelectedMenuItem.addActionListener(event -> clearSelectedRows());
    clearAllMenuItem.addActionListener(event -> clearAllRows());
    exportSelectedMenuItem.addActionListener(event -> exportSelectedToCsv());
    exportAllMenuItem.addActionListener(event -> exportAllToCsv());
    rowMenu.add(jumpToMessageMenuItem);
    rowMenu.addSeparator();
    rowMenu.add(clearSelectedMenuItem);
    rowMenu.add(clearAllMenuItem);
    rowMenu.addSeparator();
    rowMenu.add(exportSelectedMenuItem);
    rowMenu.add(exportAllMenuItem);

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    disposables.add(
        store
            .changes()
            .subscribe(
                change -> {
                  String sid = NotificationsPanel.this.serverId;
                  if (sid == null || sid.isBlank()) return;
                  if (!sid.equals(change.serverId())) return;
                  SwingUtilities.invokeLater(NotificationsPanel.this::refresh);
                },
                err -> {
                  // Never crash the UI on store update failures.
                }));

    refresh();
  }

  /** Set the server whose notifications are displayed. */
  public void setServerId(String serverId) {
    this.serverId = serverId;
    refresh();
  }

  /** Called when the user clicks a channel name. */
  public void setOnSelectTarget(Consumer<TargetRef> onSelectTarget) {
    this.onSelectTarget = onSelectTarget;
  }

  /** Called when the user requests jump-to-message for a selected notification row. */
  public void setOnJumpToMessage(BiConsumer<TargetRef, String> onJumpToMessage) {
    this.onJumpToMessage = onJumpToMessage == null ? (target, messageId) -> {} : onJumpToMessage;
  }

  /** Force a reload of table contents from the store. */
  public void refresh() {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      model.setRows(List.of());
      updateActionState();
      return;
    }

    List<Row> rows = new ArrayList<>();

    List<HighlightEvent> highlights = store.listAll(sid);
    for (HighlightEvent event : highlights) {
      if (event != null) {
        rows.add(new Row(event, "(mention)", event.snippet()));
      }
    }

    List<RuleMatchEvent> rules = store.listAllRuleMatches(sid);
    for (RuleMatchEvent event : rules) {
      if (event != null) {
        rows.add(new Row(event, event.ruleLabel(), event.snippet()));
      }
    }

    List<IrcEventRuleEvent> ircEvents = store.listAllIrcEventRules(sid);
    for (IrcEventRuleEvent event : ircEvents) {
      if (event != null) {
        rows.add(new Row(event, event.title(), event.body()));
      }
    }

    rows.sort(
        (left, right) -> {
          Instant leftAt = left.at();
          Instant rightAt = right.at();
          if (leftAt == null && rightAt == null) return 0;
          if (leftAt == null) return 1;
          if (rightAt == null) return -1;
          return rightAt.compareTo(leftAt);
        });
    model.setRows(rows);
    updateActionState();
  }

  @Override
  public void close() {
    disposables.dispose();
  }

  private void maybeShowRowMenu(MouseEvent e) {
    if (e == null || !e.isPopupTrigger()) return;
    int viewRow = table.rowAtPoint(e.getPoint());
    if (viewRow >= 0) {
      if (!table.isRowSelected(viewRow)) {
        table.setRowSelectionInterval(viewRow, viewRow);
      }
    } else {
      table.clearSelection();
    }
    updateActionState();
    PopupMenuThemeSupport.prepareForDisplay(rowMenu);
    rowMenu.show(e.getComponent(), e.getX(), e.getY());
  }

  private void updateActionState() {
    boolean hasRows = model.getRowCount() > 0;
    boolean hasSelection = table.getSelectedRowCount() > 0;
    Row selectedRow = selectedSingleRow();
    boolean canJump = false;
    if (selectedRow != null) {
      String messageId = Objects.toString(selectedRow.messageId(), "").trim();
      canJump = !messageId.isEmpty() && targetRefForRow(selectedRow) != null;
    }

    jumpToMessageMenuItem.setEnabled(canJump);
    clearSelectedMenuItem.setEnabled(hasSelection);
    clearAllMenuItem.setEnabled(hasRows);
    exportSelectedMenuItem.setEnabled(hasSelection);
    exportAllMenuItem.setEnabled(hasRows);
  }

  private void clearSelectedRows() {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    List<NotificationEvent> selectedEvents = selectedEvents();
    if (selectedEvents.isEmpty()) return;
    store.clearSelected(sid, selectedEvents);
  }

  private void clearAllRows() {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || model.getRowCount() <= 0) return;
    store.clearServer(sid);
  }

  private void jumpToSelectedMessage() {
    Row row = selectedSingleRow();
    TargetRef target = targetRefForRow(row);
    if (row == null || target == null) return;

    String messageId = Objects.toString(row.messageId(), "").trim();
    if (messageId.isEmpty()) return;
    onJumpToMessage.accept(target, messageId);
  }

  private void exportSelectedToCsv() {
    exportRowsToCsv(selectedViewRows(), "Export Selected Notifications", true);
  }

  private void exportAllToCsv() {
    exportRowsToCsv(allViewRows(), "Export Notifications", false);
  }

  private void exportRowsToCsv(List<Integer> viewRows, String dialogTitle, boolean selectedOnly) {
    if (viewRows == null || viewRows.isEmpty()) return;

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(dialogTitle);
    chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
    chooser.setAcceptAllFileFilterUsed(true);
    chooser.setSelectedFile(new File(defaultExportFileName(selectedOnly)));

    int result = chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this));
    if (result != JFileChooser.APPROVE_OPTION) return;

    File selected = chooser.getSelectedFile();
    if (selected == null) return;

    Path path = selected.toPath();
    String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      path = path.resolveSibling(fileName + ".csv");
    }

    try {
      writeCsv(path, viewRows);
    } catch (Exception ex) {
      String msg = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "Failed to export notifications:\n" + msg,
          "CSV Export Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void writeCsv(Path path, List<Integer> viewRows) throws Exception {
    if (path == null) throw new IllegalArgumentException("Output path is required.");
    if (viewRows == null || viewRows.isEmpty()) {
      throw new IllegalArgumentException("At least one row is required.");
    }
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
      int viewColumnCount = table.getColumnCount();
      ArrayList<String> headers = new ArrayList<>(viewColumnCount);
      for (int viewCol = 0; viewCol < viewColumnCount; viewCol++) {
        headers.add(Objects.toString(table.getColumnName(viewCol), ""));
      }
      out.write(joinCsv(headers));
      out.newLine();

      for (int viewRow : viewRows) {
        if (viewRow < 0 || viewRow >= table.getRowCount()) continue;
        int modelRow = table.convertRowIndexToModel(viewRow);
        ArrayList<String> row = new ArrayList<>(viewColumnCount);
        for (int viewCol = 0; viewCol < viewColumnCount; viewCol++) {
          int modelCol = table.convertColumnIndexToModel(viewCol);
          row.add(Objects.toString(model.getValueAt(modelRow, modelCol), ""));
        }
        out.write(joinCsv(row));
        out.newLine();
      }
    }
  }

  private List<Integer> allViewRows() {
    int rowCount = table.getRowCount();
    if (rowCount <= 0) return List.of();
    ArrayList<Integer> rows = new ArrayList<>(rowCount);
    for (int viewRow = 0; viewRow < rowCount; viewRow++) {
      rows.add(viewRow);
    }
    return rows;
  }

  private List<Integer> selectedViewRows() {
    int[] selectedRows = table.getSelectedRows();
    if (selectedRows == null || selectedRows.length == 0) return List.of();
    Arrays.sort(selectedRows);
    ArrayList<Integer> rows = new ArrayList<>(selectedRows.length);
    for (int selectedRow : selectedRows) {
      if (selectedRow >= 0) {
        rows.add(selectedRow);
      }
    }
    return rows;
  }

  private List<NotificationEvent> selectedEvents() {
    List<Integer> viewRows = selectedViewRows();
    if (viewRows.isEmpty()) return List.of();

    ArrayList<NotificationEvent> events = new ArrayList<>(viewRows.size());
    for (int viewRow : viewRows) {
      Row row = rowAtView(viewRow);
      if (row != null && row.event() != null) {
        events.add(row.event());
      }
    }
    if (events.isEmpty()) return List.of();
    return List.copyOf(events);
  }

  private Row selectedSingleRow() {
    if (table.getSelectedRowCount() != 1) return null;
    return rowAtView(table.getSelectedRow());
  }

  private Row rowAtView(int viewRow) {
    if (viewRow < 0 || viewRow >= table.getRowCount()) return null;
    int modelRow = table.convertRowIndexToModel(viewRow);
    return model.rowAt(modelRow);
  }

  private TargetRef targetRefForRow(Row row) {
    if (row == null || row.event() == null) return null;
    String sid = Objects.toString(row.event().serverId(), "").trim();
    if (sid.isEmpty()) {
      sid = Objects.toString(serverId, "").trim();
    }
    String channel = Objects.toString(row.channel(), "").trim();
    if (sid.isEmpty() || channel.isEmpty()) return null;
    return new TargetRef(sid, channel);
  }

  private String defaultExportFileName(boolean selectedOnly) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) sid = "server";
    sid = sid.replaceAll("[^A-Za-z0-9._-]+", "_");
    String ts =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
    return "ircafe-notifications"
        + (selectedOnly ? "-selected" : "")
        + "-"
        + sid
        + "-"
        + ts
        + ".csv";
  }

  private static void configureMenuItem(JMenuItem item, String iconName) {
    if (item == null) return;
    item.setIcon(SvgIcons.action(iconName, 16));
    item.setDisabledIcon(SvgIcons.actionDisabled(iconName, 16));
  }

  private static String joinCsv(List<String> cols) {
    if (cols == null || cols.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(cols.size() * 24);
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

  private static final class NotificationsTableModel extends AbstractTableModel {

    private static final String[] COLS = {"Time", "Channel", "From", "Match", "Snippet"};
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private List<Row> rows = List.of();

    void setRows(List<Row> rows) {
      this.rows = rows == null ? List.of() : List.copyOf(rows);
      fireTableDataChanged();
    }

    Row rowAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      return rows.get(row);
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
      return column >= 0 && column < COLS.length ? COLS[column] : "";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return "";
      Row row = rows.get(rowIndex);
      if (row == null) return "";
      return switch (columnIndex) {
        case COL_TIME -> formatTime(row.at());
        case COL_CHANNEL -> Objects.toString(row.channel(), "");
        case COL_FROM -> Objects.toString(row.from(), "");
        case COL_MATCH -> Objects.toString(row.match(), "");
        case COL_SNIPPET -> Objects.toString(row.snippet(), "");
        default -> "";
      };
    }

    private static String formatTime(Instant at) {
      if (at == null) return "";
      try {
        return TIME_FMT.format(at);
      } catch (Exception e) {
        return at.toString();
      }
    }
  }

  private record Row(NotificationEvent event, String match, String snippet) {
    Instant at() {
      return event == null ? null : event.at();
    }

    String channel() {
      return event == null ? null : event.channel();
    }

    String from() {
      return event == null ? null : event.fromNick();
    }

    String messageId() {
      return event == null ? null : event.messageId();
    }
  }

  /** Renderer that draws the channel column as underlined text to suggest it's clickable. */
  private static final class LinkCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel c =
          (JLabel)
              super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      String s = Objects.toString(value, "");
      c.setText("<html><u>" + escapeHtml(s) + "</u></html>");
      c.setToolTipText(s);
      return c;
    }

    private static String escapeHtml(String s) {
      if (s == null) return "";
      return s.replace("&", "&amp;")
          .replace("<", "&lt;")
          .replace(">", "&gt;")
          .replace("\"", "&quot;");
    }
  }
}
