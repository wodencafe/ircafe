package cafe.woden.ircclient.ui.notifications;

import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.app.NotificationStore.HighlightEvent;
import cafe.woden.ircclient.app.NotificationStore.IrcEventRuleEvent;
import cafe.woden.ircclient.app.NotificationStore.RuleMatchEvent;
import cafe.woden.ircclient.app.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Swing panel that displays per-server highlight notifications.
 *
 */
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

  private volatile String serverId;
  private volatile Consumer<TargetRef> onSelectTarget;

  public NotificationsPanel(NotificationStore store) {
    this(store, null, null);
  }

  public NotificationsPanel(NotificationStore store, String serverId, Consumer<TargetRef> onSelectTarget) {
    super(new BorderLayout());
    this.store = Objects.requireNonNull(store, "store");
    this.serverId = serverId;
    this.onSelectTarget = onSelectTarget;

    title.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    add(title, BorderLayout.NORTH);

    table.setFillsViewportHeight(true);
    table.setRowSelectionAllowed(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.setAutoCreateRowSorter(false);
    table.getTableHeader().setReorderingAllowed(false);
    table.getColumnModel().getColumn(COL_TIME).setPreferredWidth(150);
    table.getColumnModel().getColumn(COL_CHANNEL).setPreferredWidth(180);
    table.getColumnModel().getColumn(COL_FROM).setPreferredWidth(160);
    table.getColumnModel().getColumn(COL_MATCH).setPreferredWidth(200);
    table.getColumnModel().getColumn(COL_SNIPPET).setPreferredWidth(600);

    // Render channel names as "link-like" text.
    table.getColumnModel().getColumn(COL_CHANNEL).setCellRenderer(new LinkCellRenderer());

    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) return;
        int viewRow = table.rowAtPoint(e.getPoint());
        int viewCol = table.columnAtPoint(e.getPoint());
        if (viewRow < 0 || viewCol < 0) return;
        int modelCol = table.convertColumnIndexToModel(viewCol);
        if (modelCol != COL_CHANNEL) return;

        int modelRow = table.convertRowIndexToModel(viewRow);
        String sid = NotificationsPanel.this.serverId;
        if (sid == null || sid.isBlank()) return;
        String channel = model.channelAt(modelRow);
        if (channel == null || channel.isBlank()) return;
        Consumer<TargetRef> cb = NotificationsPanel.this.onSelectTarget;
        if (cb != null) {
          cb.accept(new TargetRef(sid, channel));
        }
      }
    });

    table.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int viewRow = table.rowAtPoint(e.getPoint());
        int viewCol = table.columnAtPoint(e.getPoint());
        int modelCol = viewCol < 0 ? -1 : table.convertColumnIndexToModel(viewCol);
        boolean overLink = viewRow >= 0 && modelCol == COL_CHANNEL;
        table.setCursor(overLink ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : Cursor.getDefaultCursor());
      }
    });

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    // Refresh the view whenever the store changes for the active server.
    disposables.add(store.changes().subscribe(ch -> {
      String sid = NotificationsPanel.this.serverId;
      if (sid == null || sid.isBlank()) return;
      if (!sid.equals(ch.serverId())) return;
      SwingUtilities.invokeLater(NotificationsPanel.this::refresh);
    }, err -> {
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

  /** Force a reload of table contents from the store. */
  public void refresh() {
    String sid = this.serverId;
    if (sid == null || sid.isBlank()) {
      model.setRows(List.of());
      return;
    }
    List<Row> rows = new ArrayList<>();

    List<HighlightEvent> highlights = store.listAll(sid);
    for (HighlightEvent ev : highlights) {
      if (ev == null) continue;
      rows.add(new Row(ev.at(), ev.channel(), ev.fromNick(), "(mention)", ""));
    }

    List<RuleMatchEvent> rules = store.listAllRuleMatches(sid);
    for (RuleMatchEvent ev : rules) {
      if (ev == null) continue;
      rows.add(new Row(ev.at(), ev.channel(), ev.fromNick(), ev.ruleLabel(), ev.snippet()));
    }

    List<IrcEventRuleEvent> ircEvents = store.listAllIrcEventRules(sid);
    for (IrcEventRuleEvent ev : ircEvents) {
      if (ev == null) continue;
      rows.add(new Row(ev.at(), ev.channel(), ev.fromNick(), ev.title(), ev.body()));
    }

    if (!rows.isEmpty()) {
      rows.sort((a, b) -> {
        Instant aa = a.at();
        Instant bb = b.at();
        if (aa == null && bb == null) return 0;
        if (aa == null) return 1;
        if (bb == null) return -1;
        return bb.compareTo(aa);
      });
      model.setRows(rows);
    } else {
      model.setRows(List.of());
    }
  }

  @Override
  public void close() {
    disposables.dispose();
  }

  private static final class NotificationsTableModel extends AbstractTableModel {

    private static final String[] COLS = {"Time", "Channel", "From", "Match", "Snippet"};

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private List<Row> rows = List.of();

    public void setRows(List<Row> rows) {
      this.rows = rows == null ? List.of() : List.copyOf(rows);
      fireTableDataChanged();
    }

    public String channelAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      Row ev = rows.get(row);
      return ev == null ? null : ev.channel();
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
      Row ev = rows.get(rowIndex);
      if (ev == null) return "";
      return switch (columnIndex) {
        case COL_TIME -> formatTime(ev.at());
        case COL_CHANNEL -> Objects.toString(ev.channel(), "");
        case COL_FROM -> Objects.toString(ev.from(), "");
        case COL_MATCH -> Objects.toString(ev.match(), "");
        case COL_SNIPPET -> Objects.toString(ev.snippet(), "");
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

  private record Row(Instant at, String channel, String from, String match, String snippet) {}

  /** Renderer that draws the channel column as underlined text to suggest it's clickable. */
  private static final class LinkCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                  Object value,
                                                  boolean isSelected,
                                                  boolean hasFocus,
                                                  int row,
                                                  int column) {
      JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      String s = Objects.toString(value, "");
      // Underline via HTML keeps it simple and cross-LAF.
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
