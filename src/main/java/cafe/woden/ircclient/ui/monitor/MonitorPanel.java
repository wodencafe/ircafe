package cafe.woden.ircclient.ui.monitor;

import cafe.woden.ircclient.app.monitor.MonitorListService;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/** Swing panel that displays and edits the per-server MONITOR nick list. */
public final class MonitorPanel extends JPanel {

  private static final int COL_STATUS = 0;
  private static final int COL_NICK = 1;
  private static final String DEFAULT_HINT = "Use add/remove controls or /monitor commands.";

  public record Row(String nick, Boolean online) {}

  private final MonitorTableModel model = new MonitorTableModel();
  private final JTable table = new JTable(model);
  private final JLabel title = new JLabel("Monitor");
  private final JLabel subtitle = new JLabel(DEFAULT_HINT);
  private final JTextField addField = new JTextField();
  private final JLabel addFieldLabel = new JLabel("Add nicks:");
  private final JButton addButton = new JButton("Add");
  private final JButton removeButton = new JButton("Remove Selected");
  private final JButton clearButton = new JButton("Clear");
  private final JButton refreshButton = new JButton("Refresh");
  private final TableRowSorter<MonitorTableModel> sorter = new TableRowSorter<>(model);
  private final JPopupMenu rowMenu = new JPopupMenu();
  private final JMenuItem openQueryMenuItem = new JMenuItem("Open Query");
  private final JMenuItem copyNickMenuItem = new JMenuItem("Copy Nick");
  private final JMenuItem removeMenuItem = new JMenuItem("Remove");

  private volatile String serverId = "";
  private volatile Consumer<String> onEmitCommand;

  public MonitorPanel() {
    super(new BorderLayout());

    title.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    title.setName("monitor.title");
    subtitle.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
    subtitle.setName("monitor.subtitle");

    JPanel header = new JPanel(new BorderLayout());
    header.add(title, BorderLayout.NORTH);
    header.add(subtitle, BorderLayout.SOUTH);
    add(header, BorderLayout.NORTH);

    table.setFillsViewportHeight(true);
    table.setRowSelectionAllowed(true);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.setRowSorter(sorter);
    table.setName("monitor.table");
    sorter.setSortsOnUpdates(true);
    table.getTableHeader().setReorderingAllowed(false);
    table.getColumnModel().getColumn(COL_STATUS).setPreferredWidth(140);
    table.getColumnModel().getColumn(COL_NICK).setPreferredWidth(460);
    table.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) updateButtonState();
    });
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
          openQueryRequested();
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
    buildRowMenu();

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    JPanel controls = new JPanel(new BorderLayout(8, 0));
    controls.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    addFieldLabel.setLabelFor(addField);
    addField.setToolTipText("Comma or space separated nick list.");
    addField.putClientProperty("JTextField.placeholderText", "alice, bob");
    addField.setName("monitor.addField");
    JPanel addRow = new JPanel(new BorderLayout(8, 0));
    addRow.setOpaque(false);
    addButton.setName("monitor.addButton");
    addRow.add(addFieldLabel, BorderLayout.WEST);
    addRow.add(addField, BorderLayout.CENTER);
    addRow.add(addButton, BorderLayout.EAST);

    JPanel actionRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    actionRow.setOpaque(false);
    removeButton.setName("monitor.removeButton");
    clearButton.setName("monitor.clearButton");
    refreshButton.setName("monitor.refreshButton");
    addButton.addActionListener(e -> addRequested());
    removeButton.addActionListener(e -> removeSelectedRequested());
    clearButton.addActionListener(e -> clearRequested());
    refreshButton.addActionListener(e -> refreshRequested());
    actionRow.add(removeButton);
    actionRow.add(clearButton);
    actionRow.add(refreshButton);

    JPanel controlsStack = new JPanel();
    controlsStack.setOpaque(false);
    controlsStack.setLayout(new javax.swing.BoxLayout(controlsStack, javax.swing.BoxLayout.Y_AXIS));
    controlsStack.add(addRow);
    controlsStack.add(javax.swing.Box.createVerticalStrut(6));
    controlsStack.add(actionRow);
    controls.add(controlsStack, BorderLayout.CENTER);
    add(controls, BorderLayout.SOUTH);

    addField.addActionListener(e -> addRequested());
    addField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        updateButtonState();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        updateButtonState();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        updateButtonState();
      }
    });

    updateHeader();
    updateButtonState();
  }

  private void buildRowMenu() {
    openQueryMenuItem.addActionListener(e -> openQueryRequested());
    copyNickMenuItem.addActionListener(e -> copySelectedNick());
    removeMenuItem.addActionListener(e -> removeSelectedRequested());
    rowMenu.add(openQueryMenuItem);
    rowMenu.add(copyNickMenuItem);
    rowMenu.addSeparator();
    rowMenu.add(removeMenuItem);
  }

  public void setServerId(String serverId) {
    this.serverId = normalizeServerId(serverId);
    updateHeader();
    updateButtonState();
  }

  public void setRows(List<Row> rows) {
    model.setRows(rows);
    updateHeader();
    updateButtonState();
  }

  public void setOnEmitCommand(Consumer<String> onEmitCommand) {
    this.onEmitCommand = onEmitCommand;
    updateButtonState();
  }

  private void addRequested() {
    List<String> nicks = MonitorListService.tokenizeNickInput(addField.getText());
    if (nicks.isEmpty()) return;
    emitCommand("/monitor +" + String.join(",", nicks));
    addField.selectAll();
    addField.requestFocusInWindow();
  }

  private void removeSelectedRequested() {
    List<String> selected = selectedNicks();
    if (selected.isEmpty()) return;
    emitCommand("/monitor -" + String.join(",", selected));
  }

  private void openQueryRequested() {
    String nick = primarySelectedNick();
    if (nick.isEmpty()) return;
    emitCommand("/query " + nick);
  }

  private void copySelectedNick() {
    String nick = primarySelectedNick();
    if (nick.isEmpty()) return;
    try {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(nick), null);
    } catch (Exception ignored) {
      // Clipboard may be unavailable in headless/test contexts.
    }
  }

  private void clearRequested() {
    emitCommand("/monitor clear");
  }

  private void refreshRequested() {
    emitCommand("/monitor list");
  }

  private void emitCommand(String line) {
    Consumer<String> cb = onEmitCommand;
    String cmd = Objects.toString(line, "").trim();
    if (cb == null || cmd.isEmpty()) return;
    cb.accept(cmd);
  }

  private List<String> selectedNicks() {
    int[] viewRows = table.getSelectedRows();
    if (viewRows == null || viewRows.length == 0) return List.of();

    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (int viewRow : viewRows) {
      int modelRow = table.convertRowIndexToModel(viewRow);
      Row row = model.rowAt(modelRow);
      String nick = (row == null) ? "" : Objects.toString(row.nick(), "").trim();
      if (!nick.isEmpty()) out.add(nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private String primarySelectedNick() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) return "";
    int modelRow = table.convertRowIndexToModel(viewRow);
    Row row = model.rowAt(modelRow);
    return (row == null) ? "" : Objects.toString(row.nick(), "").trim();
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
    updateButtonState();
    PopupMenuThemeSupport.prepareForDisplay(rowMenu);
    rowMenu.show(e.getComponent(), e.getX(), e.getY());
  }

  private void updateButtonState() {
    Consumer<String> cb = onEmitCommand;
    boolean hasEmitter = cb != null;
    boolean hasServer = !serverId.isBlank();
    boolean hasAddInput = !MonitorListService.tokenizeNickInput(addField.getText()).isEmpty();
    boolean hasRows = model.getRowCount() > 0;
    boolean hasSelection = table.getSelectedRowCount() > 0;
    boolean hasSingleSelection = table.getSelectedRowCount() == 1;

    addButton.setEnabled(hasEmitter && hasServer && hasAddInput);
    removeButton.setEnabled(hasEmitter && hasServer && hasSelection);
    clearButton.setEnabled(hasEmitter && hasServer && hasRows);
    refreshButton.setEnabled(hasEmitter && hasServer);
    openQueryMenuItem.setEnabled(hasEmitter && hasServer && hasSingleSelection && !primarySelectedNick().isEmpty());
    copyNickMenuItem.setEnabled(hasSingleSelection && !primarySelectedNick().isEmpty());
    removeMenuItem.setEnabled(hasEmitter && hasServer && hasSelection);
  }

  private void updateHeader() {
    String sid = this.serverId;
    if (sid.isEmpty()) {
      title.setText("Monitor");
      subtitle.setText(DEFAULT_HINT);
      return;
    }

    int total = model.getRowCount();
    int online = 0;
    int offline = 0;
    int unknown = 0;
    for (int i = 0; i < total; i++) {
      Row row = model.rowAt(i);
      if (row == null || row.online() == null) {
        unknown++;
      } else if (row.online()) {
        online++;
      } else {
        offline++;
      }
    }

    title.setText("Monitor - " + sid);
    if (total <= 0) {
      subtitle.setText("No monitored nicks.");
      return;
    }

    subtitle.setText(
        total
            + " nick(s): "
            + online
            + " online, "
            + offline
            + " offline, "
            + unknown
            + " unknown.");
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String statusText(Boolean online) {
    if (online == null) return "Unknown";
    return online ? "Online" : "Offline";
  }

  private static final class MonitorTableModel extends AbstractTableModel {

    private static final String[] COLS = {"Status", "Nick"};
    private final ArrayList<Row> rows = new ArrayList<>();

    void setRows(List<Row> rows) {
      this.rows.clear();
      if (rows != null && !rows.isEmpty()) this.rows.addAll(rows);
      fireTableDataChanged();
    }

    Row rowAt(int rowIndex) {
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
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      Row row = rowAt(rowIndex);
      if (row == null) return "";
      return switch (columnIndex) {
        case COL_STATUS -> statusText(row.online());
        case COL_NICK -> Objects.toString(row.nick(), "");
        default -> "";
      };
    }
  }
}
