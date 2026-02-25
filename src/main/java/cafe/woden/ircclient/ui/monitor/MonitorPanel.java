package cafe.woden.ircclient.ui.monitor;

import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/** Swing panel that displays and edits the per-server MONITOR nick list. */
public final class MonitorPanel extends JPanel {

  private static final int COL_STATUS = 0;
  private static final int COL_NICK = 1;
  private static final String DEFAULT_HINT = "Use add/remove controls or /monitor commands.";
  private static final int ACTION_ICON_SIZE = 16;
  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(28, 28);

  public record Row(String nick, Boolean online) {}

  private final MonitorTableModel model = new MonitorTableModel();
  private final JTable table = new JTable(model);
  private final JLabel title = new JLabel("Monitor");
  private final JLabel subtitle = new JLabel(DEFAULT_HINT);
  private final JButton addButton = new JButton();
  private final JButton removeButton = new JButton();
  private final JButton clearButton = new JButton();
  private final JButton refreshButton = new JButton();
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
    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) updateButtonState();
            });
    table.addMouseListener(
        new MouseAdapter() {
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
    configureActionButtons();
    addButton.setName("monitor.addButton");

    JPanel actionRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    actionRow.setOpaque(false);
    removeButton.setName("monitor.removeButton");
    clearButton.setName("monitor.clearButton");
    refreshButton.setName("monitor.refreshButton");
    addButton.addActionListener(e -> addRequested());
    removeButton.addActionListener(e -> removeSelectedRequested());
    clearButton.addActionListener(e -> clearRequested());
    refreshButton.addActionListener(e -> refreshRequested());
    actionRow.add(addButton);
    actionRow.add(removeButton);
    actionRow.add(clearButton);
    actionRow.add(refreshButton);

    controls.add(actionRow, BorderLayout.CENTER);
    add(controls, BorderLayout.SOUTH);

    updateHeader();
    updateButtonState();
  }

  private void configureActionButtons() {
    configureActionButton(addButton, "plus", "Add user to monitor list", "Add...");
    configureActionButton(
        removeButton, "trash", "Remove selected users from monitor list", "Remove selected");
    configureActionButton(clearButton, "close", "Clear monitor list", "Clear");
    configureActionButton(refreshButton, "refresh", "Refresh monitor list", "Refresh");
  }

  private void configureActionButton(
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
    List<String> nicks = promptForNicks();
    if (nicks.isEmpty()) return;
    emitCommand("/monitor +" + String.join(",", nicks));
  }

  private List<String> promptForNicks() {
    JTextField field = new JTextField();
    field.putClientProperty("JTextField.placeholderText", "alice");
    Object[] message = {"Nick:", field};
    int option =
        JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(this),
            message,
            "Add Monitor User",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (option != JOptionPane.OK_OPTION) return List.of();
    return MonitorListService.tokenizeNickInput(field.getText());
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
    boolean hasRows = model.getRowCount() > 0;
    boolean hasSelection = table.getSelectedRowCount() > 0;
    boolean hasSingleSelection = table.getSelectedRowCount() == 1;

    addButton.setEnabled(hasEmitter && hasServer);
    removeButton.setEnabled(hasEmitter && hasServer && hasSelection);
    clearButton.setEnabled(hasEmitter && hasServer && hasRows);
    refreshButton.setEnabled(hasEmitter && hasServer);
    openQueryMenuItem.setEnabled(
        hasEmitter && hasServer && hasSingleSelection && !primarySelectedNick().isEmpty());
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
