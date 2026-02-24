package cafe.woden.ircclient.ui.channellist;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/** Swing panel that displays /LIST results for one server at a time. */
public final class ChannelListPanel extends JPanel {

  private static final int COL_CHANNEL = 0;
  private static final int COL_USERS = 1;
  private static final int COL_TOPIC = 2;

  private static final String DEFAULT_HINT = "Run /list to load channels.";

  private final ChannelListTableModel model = new ChannelListTableModel();
  private final JTable table = new JTable(model);
  private final JLabel title = new JLabel("Channel List");
  private final JLabel subtitle = new JLabel(DEFAULT_HINT);
  private final JTextField filterField = new JTextField();
  private final TableRowSorter<ChannelListTableModel> sorter = new TableRowSorter<>(model);

  private final Map<String, ArrayList<Row>> rowsByServer = new HashMap<>();
  private final Map<String, String> statusByServer = new HashMap<>();

  private volatile String serverId = "";
  private volatile Consumer<String> onJoinChannel;

  public ChannelListPanel() {
    super(new BorderLayout());

    title.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    subtitle.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));

    JPanel header = new JPanel(new BorderLayout());
    header.add(title, BorderLayout.NORTH);
    JPanel mid = new JPanel(new BorderLayout());
    mid.add(subtitle, BorderLayout.NORTH);
    JPanel filterBar = new JPanel(new BorderLayout(6, 0));
    filterBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));
    filterBar.add(new JLabel("Filter:"), BorderLayout.WEST);
    filterBar.add(filterField, BorderLayout.CENTER);
    mid.add(filterBar, BorderLayout.SOUTH);
    header.add(mid, BorderLayout.SOUTH);
    add(header, BorderLayout.NORTH);

    table.setFillsViewportHeight(true);
    table.setRowSelectionAllowed(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.setRowSorter(sorter);
    sorter.setSortsOnUpdates(true);
    table.getTableHeader().setReorderingAllowed(false);
    table.getColumnModel().getColumn(COL_CHANNEL).setPreferredWidth(220);
    table.getColumnModel().getColumn(COL_USERS).setPreferredWidth(90);
    table.getColumnModel().getColumn(COL_TOPIC).setPreferredWidth(900);
    table.setToolTipText("Double-click a row to join that channel.");
    filterField.setToolTipText("Type to filter by channel, topic, or user count.");
    filterField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                applyFilter();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                applyFilter();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                applyFilter();
              }
            });

    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() < 2) return;
            int viewRow = table.rowAtPoint(e.getPoint());
            if (viewRow < 0) return;
            int modelRow = table.convertRowIndexToModel(viewRow);
            String channel = model.channelAt(modelRow);
            if (channel == null || channel.isBlank()) return;
            Consumer<String> cb = onJoinChannel;
            if (cb != null) cb.accept(channel);
          }
        });

    table.addMouseMotionListener(
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            int viewRow = table.rowAtPoint(e.getPoint());
            table.setCursor(
                viewRow >= 0
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
          }
        });

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);
  }

  public void setServerId(String serverId) {
    this.serverId = normalizeServerId(serverId);
    refresh();
  }

  public void setOnJoinChannel(Consumer<String> onJoinChannel) {
    this.onJoinChannel = onJoinChannel;
  }

  public void beginList(String serverId, String banner) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    rowsByServer.put(sid, new ArrayList<>());
    statusByServer.put(sid, normalizeBanner(banner));
    if (sid.equals(this.serverId)) {
      refresh();
    }
  }

  public void appendEntry(String serverId, String channel, int visibleUsers, String topic) {
    String sid = normalizeServerId(serverId);
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;

    Row row = new Row(ch, Math.max(0, visibleUsers), Objects.toString(topic, "").trim());
    ArrayList<Row> rows = rowsByServer.computeIfAbsent(sid, __ -> new ArrayList<>());
    rows.add(row);

    if (sid.equals(this.serverId)) {
      model.addRow(row);
      updateHeader();
    }
  }

  public void endList(String serverId, String summary) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    String base = Objects.toString(summary, "").trim();
    if (base.isEmpty()) base = "End of /LIST";
    statusByServer.put(sid, base);
    if (sid.equals(this.serverId)) {
      updateHeader();
    }
  }

  private void refresh() {
    String sid = this.serverId;
    List<Row> rows =
        (sid.isEmpty())
            ? List.of()
            : List.copyOf(rowsByServer.getOrDefault(sid, new ArrayList<>()));
    model.setRows(rows);
    updateHeader();
  }

  private void updateHeader() {
    String sid = this.serverId;
    if (sid.isEmpty()) {
      title.setText("Channel List");
      subtitle.setText(DEFAULT_HINT);
      return;
    }

    title.setText("Channel List - " + sid);
    int totalCount = model.getRowCount();
    int visibleCount = table.getRowCount();
    String filter = Objects.toString(filterField.getText(), "").trim();
    boolean filtered = !filter.isEmpty();
    String status = Objects.toString(statusByServer.get(sid), "").trim();
    if (status.isEmpty()) {
      if (totalCount == 0) {
        subtitle.setText(DEFAULT_HINT);
      } else if (filtered) {
        subtitle.setText(visibleCount + " of " + totalCount + " channels shown");
      } else {
        subtitle.setText(totalCount + " channels");
      }
      return;
    }
    if (totalCount > 0) {
      if (filtered) {
        subtitle.setText(status + " (" + visibleCount + " of " + totalCount + " channels shown)");
      } else {
        subtitle.setText(status + " (" + totalCount + " channels)");
      }
    } else {
      subtitle.setText(status);
    }
  }

  private void applyFilter() {
    String filter = Objects.toString(filterField.getText(), "").trim();
    if (filter.isEmpty()) {
      sorter.setRowFilter(null);
      updateHeader();
      return;
    }

    String[] terms = filter.toLowerCase(Locale.ROOT).split("\\s+");
    sorter.setRowFilter(
        new RowFilter<>() {
          @Override
          public boolean include(Entry<? extends ChannelListTableModel, ? extends Integer> entry) {
            String channel =
                Objects.toString(entry.getStringValue(COL_CHANNEL), "").toLowerCase(Locale.ROOT);
            String users =
                Objects.toString(entry.getStringValue(COL_USERS), "").toLowerCase(Locale.ROOT);
            String topic =
                Objects.toString(entry.getStringValue(COL_TOPIC), "").toLowerCase(Locale.ROOT);
            for (String term : terms) {
              if (term == null || term.isBlank()) continue;
              if (channel.contains(term) || users.contains(term) || topic.contains(term)) continue;
              return false;
            }
            return true;
          }
        });
    updateHeader();
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeBanner(String banner) {
    String b = Objects.toString(banner, "").trim();
    return b.isEmpty() ? "Loading channel list..." : b;
  }

  private static final class ChannelListTableModel extends AbstractTableModel {

    private static final String[] COLS = {"Channel", "Users", "Topic"};

    private final ArrayList<Row> rows = new ArrayList<>();

    void setRows(List<Row> rows) {
      this.rows.clear();
      if (rows != null && !rows.isEmpty()) this.rows.addAll(rows);
      fireTableDataChanged();
    }

    void addRow(Row row) {
      if (row == null) return;
      int idx = rows.size();
      rows.add(row);
      fireTableRowsInserted(idx, idx);
    }

    String channelAt(int row) {
      if (row < 0 || row >= rows.size()) return "";
      Row r = rows.get(row);
      return r == null ? "" : r.channel();
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
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == COL_USERS ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return "";
      Row row = rows.get(rowIndex);
      if (row == null) return "";
      return switch (columnIndex) {
        case COL_CHANNEL -> row.channel();
        case COL_USERS -> row.visibleUsers();
        case COL_TOPIC -> row.topic();
        default -> "";
      };
    }
  }

  private record Row(String channel, int visibleUsers, String topic) {}
}
