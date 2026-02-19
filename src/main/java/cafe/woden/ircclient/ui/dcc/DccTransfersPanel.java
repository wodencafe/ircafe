package cafe.woden.ircclient.ui.dcc;

import cafe.woden.ircclient.app.DccTransferStore;
import cafe.woden.ircclient.app.DccTransferStore.ActionHint;
import cafe.woden.ircclient.app.DccTransferStore.Entry;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/** Swing panel that displays per-server DCC transfers and chat offers. */
public final class DccTransfersPanel extends JPanel implements AutoCloseable {

  private static final int COL_TIME = 0;
  private static final int COL_NICK = 1;
  private static final int COL_KIND = 2;
  private static final int COL_STATUS = 3;
  private static final int COL_PROGRESS = 4;
  private static final int COL_DETAIL = 5;

  private final DccTransferStore store;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final DccTransfersTableModel model = new DccTransfersTableModel();

  private final JLabel title = new JLabel("DCC Transfers");
  private final JLabel subtitle = new JLabel("No active DCC transfers.");
  private final JTable table = new JTable(model);
  private final JButton actionButton = new JButton("Run Action");
  private final JButton openPmButton = new JButton("Open PM");
  private final JButton copyPathButton = new JButton("Copy Save Path");
  private final JButton refreshButton = new JButton("Refresh");

  private volatile String serverId;
  private volatile Consumer<String> onEmitCommand;

  public DccTransfersPanel(DccTransferStore store) {
    this(store, null, null);
  }

  public DccTransfersPanel(DccTransferStore store, String serverId, Consumer<String> onEmitCommand) {
    super(new BorderLayout());
    this.store = Objects.requireNonNull(store, "store");
    this.serverId = serverId;
    this.onEmitCommand = onEmitCommand;

    title.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    subtitle.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));

    // SVG action icons
    actionButton.setIcon(SvgIcons.action("terminal", 16));
    actionButton.setDisabledIcon(SvgIcons.actionDisabled("terminal", 16));
    openPmButton.setIcon(SvgIcons.action("dock-right", 16));
    openPmButton.setDisabledIcon(SvgIcons.actionDisabled("dock-right", 16));
    copyPathButton.setIcon(SvgIcons.action("copy", 16));
    copyPathButton.setDisabledIcon(SvgIcons.actionDisabled("copy", 16));
    refreshButton.setIcon(SvgIcons.action("refresh", 16));
    refreshButton.setDisabledIcon(SvgIcons.actionDisabled("refresh", 16));

    JPanel header = new JPanel(new BorderLayout());
    header.add(title, BorderLayout.NORTH);
    header.add(subtitle, BorderLayout.SOUTH);
    add(header, BorderLayout.NORTH);

    table.setFillsViewportHeight(true);
    table.setRowSelectionAllowed(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.setAutoCreateRowSorter(false);
    table.getTableHeader().setReorderingAllowed(false);
    table.getColumnModel().getColumn(COL_TIME).setPreferredWidth(155);
    table.getColumnModel().getColumn(COL_NICK).setPreferredWidth(140);
    table.getColumnModel().getColumn(COL_KIND).setPreferredWidth(170);
    table.getColumnModel().getColumn(COL_STATUS).setPreferredWidth(190);
    table.getColumnModel().getColumn(COL_PROGRESS).setPreferredWidth(90);
    table.getColumnModel().getColumn(COL_DETAIL).setPreferredWidth(760);

    table.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        updateActionButtonState();
      }
    });

    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() < 2) return;
        runSelectedAction();
      }
    });

    table.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow < 0) {
          table.setCursor(Cursor.getDefaultCursor());
          return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        Entry row = model.entryAt(modelRow);
        table.setCursor((row != null && row.actionHint() != ActionHint.NONE)
            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : Cursor.getDefaultCursor());
      }
    });

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    JPanel footer = new JPanel(new BorderLayout(8, 0));
    footer.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

    JPanel rowActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    rowActions.setOpaque(false);
    actionButton.addActionListener(e -> runSelectedAction());
    openPmButton.addActionListener(e -> openPmForSelected());
    copyPathButton.addActionListener(e -> copySavePathForSelected());
    refreshButton.addActionListener(e -> refresh());
    rowActions.add(actionButton);
    rowActions.add(openPmButton);
    rowActions.add(copyPathButton);
    footer.add(rowActions, BorderLayout.WEST);
    footer.add(refreshButton, BorderLayout.EAST);
    add(footer, BorderLayout.SOUTH);

    disposables.add(store.changes().subscribe(ch -> {
      String sid = Objects.toString(DccTransfersPanel.this.serverId, "").trim();
      if (sid.isEmpty()) return;
      if (!sid.equals(ch.serverId())) return;
      SwingUtilities.invokeLater(DccTransfersPanel.this::refresh);
    }, err -> {
      // Never crash the UI due to store update failures.
    }));

    refresh();
  }

  public void setServerId(String serverId) {
    this.serverId = Objects.toString(serverId, "").trim();
    refresh();
  }

  public void setOnEmitCommand(Consumer<String> onEmitCommand) {
    this.onEmitCommand = onEmitCommand;
    updateActionButtonState();
  }

  public void refresh() {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      title.setText("DCC Transfers");
      subtitle.setText("No server selected.");
      model.setRows(List.of());
      updateActionButtonState();
      return;
    }

    List<Entry> rows = store.listAll(sid);
    model.setRows(rows);

    int total = rows.size();
    long actionable = rows.stream().filter(r -> r != null && r.actionHint() != ActionHint.NONE).count();
    title.setText("DCC Transfers - " + sid);
    if (total == 0) {
      subtitle.setText("No DCC activity recorded for this server.");
    } else if (actionable > 0) {
      subtitle.setText(total + " item(s), " + actionable + " action required.");
    } else {
      subtitle.setText(total + " item(s).");
    }
    updateActionButtonState();
  }

  @Override
  public void close() {
    disposables.dispose();
  }

  private void runSelectedAction() {
    Entry selected = selectedEntry();
    if (selected == null) return;
    String cmd = commandFor(selected);
    if (cmd.isBlank()) return;
    Consumer<String> cb = onEmitCommand;
    if (cb != null) cb.accept(cmd);
  }

  private void openPmForSelected() {
    Entry selected = selectedEntry();
    if (selected == null) return;
    String nick = Objects.toString(selected.nick(), "").trim();
    if (nick.isEmpty()) return;
    Consumer<String> cb = onEmitCommand;
    if (cb != null) cb.accept("/query " + nick);
  }

  private void copySavePathForSelected() {
    Entry selected = selectedEntry();
    if (selected == null) return;
    String path = savePathFor(selected);
    if (path.isBlank()) return;
    try {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(path), null);
    } catch (Exception ignored) {
      // Clipboard can be unavailable on some platforms/headless contexts.
    }
  }

  private void updateActionButtonState() {
    Entry selected = selectedEntry();
    if (selected == null) {
      actionButton.setEnabled(false);
      actionButton.setText("Run Action");
      openPmButton.setEnabled(false);
      copyPathButton.setEnabled(false);
      return;
    }

    String nick = Objects.toString(selected.nick(), "").trim();
    String path = savePathFor(selected);
    String cmd = commandFor(selected);
    actionButton.setEnabled(!cmd.isBlank() && onEmitCommand != null);
    actionButton.setText(labelFor(selected.actionHint()));
    openPmButton.setEnabled(!nick.isBlank() && onEmitCommand != null);
    copyPathButton.setEnabled(!path.isBlank());
  }

  private Entry selectedEntry() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) return null;
    int modelRow = table.convertRowIndexToModel(viewRow);
    return model.entryAt(modelRow);
  }

  private static String commandFor(Entry entry) {
    if (entry == null) return "";
    String nick = Objects.toString(entry.nick(), "").trim();
    if (nick.isEmpty()) return "";
    return switch (entry.actionHint()) {
      case ACCEPT_CHAT -> "/dcc accept " + nick;
      case GET_FILE -> "/dcc get " + nick;
      case CLOSE_CHAT -> "/dcc close " + nick;
      case NONE -> "";
    };
  }

  private static String savePathFor(Entry entry) {
    if (entry == null) return "";
    return Objects.toString(entry.localPath(), "").trim();
  }

  private static String labelFor(ActionHint hint) {
    if (hint == null) return "Run Action";
    return switch (hint) {
      case ACCEPT_CHAT -> "Accept Chat";
      case GET_FILE -> "Get File";
      case CLOSE_CHAT -> "Close Chat";
      case NONE -> "Run Action";
    };
  }

  private static final class DccTransfersTableModel extends AbstractTableModel {

    private static final String[] COLS = {"Updated", "Nick", "Kind", "Status", "Progress", "Detail"};
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private List<Entry> rows = List.of();

    void setRows(List<Entry> rows) {
      this.rows = (rows == null) ? List.of() : List.copyOf(rows);
      fireTableDataChanged();
    }

    Entry entryAt(int rowIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return null;
      return rows.get(rowIndex);
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
      Entry row = entryAt(rowIndex);
      if (row == null) return "";
      return switch (columnIndex) {
        case COL_TIME -> formatTime(row.updatedAt());
        case COL_NICK -> Objects.toString(row.nick(), "");
        case COL_KIND -> Objects.toString(row.kind(), "");
        case COL_STATUS -> Objects.toString(row.status(), "");
        case COL_PROGRESS -> formatProgress(row.progressPercent());
        case COL_DETAIL -> Objects.toString(row.detail(), "");
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

    private static String formatProgress(Integer progressPercent) {
      if (progressPercent == null) return "";
      return progressPercent + "%";
    }
  }
}
