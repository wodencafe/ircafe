package cafe.woden.ircclient.ui.channellist;

import cafe.woden.ircclient.ui.icons.SvgIcons;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import net.miginfocom.swing.MigLayout;

/** Swing panel for server /LIST results and managed channel state/actions. */
public final class ChannelListPanel extends JPanel {
  private enum ListRequestType {
    UNKNOWN,
    FULL_LIST,
    ALIS
  }

  private enum AlisActivityState {
    IDLE,
    SPINNER,
    CONFIRMED
  }

  public enum ManagedSortMode {
    ALPHABETICAL,
    CUSTOM
  }

  public enum ChannelDetailsSource {
    MANAGED,
    SERVER_LIST
  }

  public record ManagedChannelRow(
      String channel,
      boolean detached,
      boolean autoReattach,
      int users,
      int notifications,
      String modes) {

    public ManagedChannelRow(String channel, boolean detached, boolean autoReattach) {
      this(channel, detached, autoReattach, 0, 0, "");
    }

    public ManagedChannelRow {
      channel = Objects.toString(channel, "").trim();
      users = Math.max(0, users);
      notifications = Math.max(0, notifications);
      modes = Objects.toString(modes, "").trim();
    }
  }

  public record ChannelDetails(
      String serverId,
      ChannelDetailsSource source,
      String channel,
      String state,
      String topic,
      String modes,
      int users,
      int notifications,
      boolean autoReattach) {}

  public record ListEntryRow(String channel, int visibleUsers, String topic) {}

  enum AlisRegistrationFilter {
    ANY,
    REGISTERED_ONLY,
    UNREGISTERED_ONLY
  }

  record AlisSearchOptions(
      boolean includeTopic,
      Integer minUsers,
      Integer maxUsers,
      Integer skipCount,
      boolean showModes,
      boolean showTopicSetter,
      AlisRegistrationFilter registrationFilter) {

    static AlisSearchOptions defaults(boolean includeTopic) {
      return new AlisSearchOptions(
          includeTopic, null, null, null, false, false, AlisRegistrationFilter.ANY);
    }
  }

  private static final int LIST_COL_CHANNEL = 0;
  private static final int LIST_COL_USERS = 1;
  private static final int LIST_COL_TOPIC = 2;

  private static final int MANAGED_COL_CHANNEL = 0;
  private static final int MANAGED_COL_STATE = 1;
  private static final int MANAGED_COL_USERS = 2;
  private static final int MANAGED_COL_NOTIFICATIONS = 3;
  private static final int MANAGED_COL_MODES = 4;
  private static final int MANAGED_COL_AUTO_REATTACH = 5;

  private static final String DEFAULT_HINT =
      "Use the refresh button to request /list (heavy) or the ALIS search button for filtered results.";
  private static final String MANAGED_HINT =
      "Managed channels include attached and detached channels for this server.";

  private static final int ACTION_ICON_SIZE = 16;
  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(28, 28);

  private final ChannelListTableModel listModel = new ChannelListTableModel();
  private final JTable listTable = new JTable(listModel);
  private final JTextArea listSubtitle = createSubtitleArea(DEFAULT_HINT);
  private final JTextField filterField = new JTextField();
  private final TableRowSorter<ChannelListTableModel> listSorter = new TableRowSorter<>(listModel);
  private final JButton runListButton = new JButton();
  private final JButton runAlisButton = new JButton();
  private final JButton listDetailsButton = new JButton();

  private final ManagedChannelTableModel managedModel = new ManagedChannelTableModel();
  private final JTable managedTable = new JTable(managedModel);
  private final JButton addChannelButton = new JButton();
  private final JButton attachDetachButton = new JButton();
  private final JButton closeChannelButton = new JButton();
  private final JButton managedDetailsButton = new JButton();
  private final JButton moveUpButton = new JButton();
  private final JButton moveDownButton = new JButton();
  private final JComboBox<ManagedSortMode> sortModeCombo =
      new JComboBox<>(ManagedSortMode.values());
  private final JLabel managedSubtitle = new JLabel(MANAGED_HINT);

  private final Map<String, ArrayList<Row>> rowsByServer = new HashMap<>();
  private final Map<String, String> statusByServer = new HashMap<>();
  private final Map<String, Boolean> loadingByServer = new HashMap<>();
  private final Map<String, ListRequestType> requestTypeByServer = new HashMap<>();
  private final Map<String, ArrayList<ManagedChannelRow>> managedRowsByServer = new HashMap<>();
  private final Map<String, ManagedSortMode> managedSortModeByServer = new HashMap<>();
  private final Icon runAlisDefaultIcon = SvgIcons.action("help", ACTION_ICON_SIZE);
  private final Icon runAlisDefaultDisabledIcon = SvgIcons.actionDisabled("help", ACTION_ICON_SIZE);
  private final Icon runAlisActivityIcon = new AlisActivityIcon();
  private final Timer alisActivityTimer = new Timer(33, e -> onAlisActivityTick());
  private AlisActivityState alisActivityState = AlisActivityState.IDLE;
  private int alisSpinnerAngleDeg;
  private long alisConfirmedStartMs;
  private float alisConfirmedAlpha = 1f;

  private volatile String serverId = "";
  private volatile Consumer<String> onJoinChannel;
  private volatile Runnable onRunListRequest;
  private volatile Consumer<String> onRunAlisRequest;
  private volatile Consumer<String> onAddChannelRequest;
  private volatile Consumer<String> onAttachChannelRequest;
  private volatile Consumer<String> onDetachChannelRequest;
  private volatile Consumer<String> onCloseChannelRequest;
  private volatile BiConsumer<String, Boolean> onAutoReattachChanged;
  private volatile Consumer<ManagedSortMode> onManagedSortModeChanged;
  private volatile Consumer<List<String>> onManagedCustomOrderChanged;
  private volatile Consumer<String> onManagedChannelSelected;
  private volatile BiFunction<String, String, String> onChannelTopicRequest;
  private volatile BiFunction<String, String, List<String>> onChannelBanListSnapshotRequest;
  private volatile BiConsumer<String, String> onChannelBanListRefreshRequest;
  private boolean syncingSortModeCombo;
  private ChannelDetailsDialogState channelDetailsDialog;

  public ChannelListPanel() {
    super(new BorderLayout());

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Managed Channels", buildManagedChannelsTab());
    tabs.addTab("Server /LIST", buildListTab());
    tabs.setSelectedIndex(0);
    add(tabs, BorderLayout.CENTER);

    updateListHeader();
    updateManagedHeader();
    updateManagedButtons();
  }

  private JPanel buildListTab() {
    JPanel root = new JPanel(new BorderLayout(0, 8));
    root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JPanel controls = new JPanel(new MigLayout("insets 0, fillx", "[][][]push[][grow,fill]", "[]"));
    configureActionButton(
        runListButton,
        "refresh",
        "Request full /list from server (heavy; confirmation required)",
        "Run /list");
    runListButton.setToolTipText("Request full /list from server (heavy; confirmation required)");
    runListButton.addActionListener(e -> runFullListRequested());

    configureActionButton(
        runAlisButton,
        "help",
        "Run filtered ALIS search (topic, min/max users, and display filters)",
        "Run ALIS search");
    runAlisButton.setIcon(runAlisDefaultIcon);
    runAlisButton.setDisabledIcon(runAlisDefaultDisabledIcon);
    runAlisButton.addActionListener(e -> runAlisRequested());

    configureActionButton(
        listDetailsButton, "eye", "Show details for selected channel", "Show details");
    listDetailsButton.addActionListener(e -> showServerListDetailsForSelection());

    controls.add(runListButton);
    controls.add(runAlisButton);
    controls.add(listDetailsButton);
    controls.add(new JLabel("Filter:"), "gapleft 12");
    controls.add(filterField, "pushx,growx");

    listSubtitle.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
    JPanel north = new JPanel(new BorderLayout(0, 6));
    north.add(controls, BorderLayout.NORTH);
    north.add(listSubtitle, BorderLayout.SOUTH);
    root.add(north, BorderLayout.NORTH);

    listTable.setFillsViewportHeight(true);
    listTable.setRowSelectionAllowed(true);
    listTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    listTable.setShowHorizontalLines(false);
    listTable.setShowVerticalLines(false);
    listTable.setRowSorter(listSorter);
    listSorter.setSortsOnUpdates(false);
    listTable.getTableHeader().setReorderingAllowed(false);
    listTable.getColumnModel().getColumn(LIST_COL_CHANNEL).setPreferredWidth(220);
    listTable.getColumnModel().getColumn(LIST_COL_USERS).setPreferredWidth(90);
    listTable.getColumnModel().getColumn(LIST_COL_TOPIC).setPreferredWidth(880);
    listTable.setToolTipText("Double-click a row to join that channel.");

    listTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) updateListButtons();
            });

    filterField.setToolTipText("Type to filter by channel, topic, or user count.");
    filterField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                applyListFilter();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                applyListFilter();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                applyListFilter();
              }
            });

    listTable.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            int viewRow = listTable.rowAtPoint(e.getPoint());
            if (viewRow < 0) return;
            if (e.getClickCount() < 2) return;
            int modelRow = listTable.convertRowIndexToModel(viewRow);
            String channel = listModel.channelAt(modelRow);
            if (channel == null || channel.isBlank()) return;
            Consumer<String> cb = onJoinChannel;
            if (cb != null) cb.accept(channel);
          }
        });

    listTable.addMouseMotionListener(
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            if (isCurrentServerListLoading()) {
              listTable.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
              return;
            }
            int viewRow = listTable.rowAtPoint(e.getPoint());
            listTable.setCursor(
                viewRow >= 0
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
          }
        });

    JScrollPane scroll = new JScrollPane(listTable);
    scroll.setBorder(null);
    root.add(scroll, BorderLayout.CENTER);
    updateListButtons();
    return root;
  }

  private JPanel buildManagedChannelsTab() {
    JPanel root = new JPanel(new BorderLayout(0, 8));
    root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    managedSubtitle.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

    JPanel toolbar =
        new JPanel(new MigLayout("insets 0, fillx", "[][][][][][]push[][pref!]", "[]"));
    configureActionButton(addChannelButton, "plus", "Add a channel and attempt to join", "Add");
    configureActionButton(
        attachDetachButton,
        "play",
        "Attach selected channel if detached, or detach if attached",
        "Attach/Detach");
    configureActionButton(
        closeChannelButton, "close", "Close selected channel and remove it from the list", "Close");
    configureActionButton(
        managedDetailsButton, "eye", "Show details for selected managed channel", "Show details");
    configureActionButton(moveUpButton, "arrow-up", "Move selected channel up", "Move up");
    configureActionButton(moveDownButton, "arrow-down", "Move selected channel down", "Move down");

    sortModeCombo.setToolTipText("Sort mode for managed channels on this server.");
    sortModeCombo.setRenderer(
        (list, value, index, isSelected, cellHasFocus) ->
            new JLabel(value == ManagedSortMode.ALPHABETICAL ? "Alphabetical" : "Manual"));

    addChannelButton.addActionListener(e -> addChannelRequested());
    attachDetachButton.addActionListener(e -> attachDetachSelectedRequested());
    closeChannelButton.addActionListener(e -> closeSelectedRequested());
    managedDetailsButton.addActionListener(e -> showManagedDetailsForSelection());
    moveUpButton.addActionListener(e -> moveSelectedBy(-1));
    moveDownButton.addActionListener(e -> moveSelectedBy(+1));
    sortModeCombo.addActionListener(e -> onSortModeChangedByUser());

    toolbar.add(addChannelButton);
    toolbar.add(attachDetachButton);
    toolbar.add(closeChannelButton);
    toolbar.add(managedDetailsButton);
    toolbar.add(moveUpButton);
    toolbar.add(moveDownButton);
    toolbar.add(new JLabel("Order:"));
    toolbar.add(sortModeCombo, "w 150!");

    managedTable.setFillsViewportHeight(true);
    managedTable.setRowSelectionAllowed(true);
    managedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    managedTable.setShowHorizontalLines(false);
    managedTable.setShowVerticalLines(false);
    managedTable.setAutoCreateRowSorter(false);
    managedTable.setToolTipText("Manual order mode supports both drag/drop and move buttons.");
    managedTable.getTableHeader().setReorderingAllowed(false);
    managedTable.getColumnModel().getColumn(MANAGED_COL_CHANNEL).setPreferredWidth(180);
    managedTable.getColumnModel().getColumn(MANAGED_COL_STATE).setPreferredWidth(110);
    managedTable.getColumnModel().getColumn(MANAGED_COL_USERS).setPreferredWidth(70);
    managedTable.getColumnModel().getColumn(MANAGED_COL_NOTIFICATIONS).setPreferredWidth(100);
    managedTable.getColumnModel().getColumn(MANAGED_COL_MODES).setPreferredWidth(100);
    managedTable.getColumnModel().getColumn(MANAGED_COL_AUTO_REATTACH).setPreferredWidth(120);
    managedTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) {
                updateManagedButtons();
                notifyManagedChannelSelectionChanged();
              }
            });
    managedTable.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() < 2) return;
            showManagedDetailsForSelection();
          }
        });

    managedModel.setOnAutoReattachChanged(
        (channel, enabled) -> {
          BiConsumer<String, Boolean> cb = onAutoReattachChanged;
          if (cb != null) cb.accept(channel, enabled);
        });

    installManagedTableRowReorderDnD();

    JPanel north = new JPanel(new BorderLayout(0, 6));
    north.add(toolbar, BorderLayout.NORTH);
    north.add(managedSubtitle, BorderLayout.SOUTH);

    JScrollPane scroll = new JScrollPane(managedTable);
    scroll.setBorder(null);

    root.add(north, BorderLayout.NORTH);
    root.add(scroll, BorderLayout.CENTER);
    return root;
  }

  private void installManagedTableRowReorderDnD() {
    try {
      managedTable.setDragEnabled(true);
      managedTable.setDropMode(javax.swing.DropMode.INSERT_ROWS);
    } catch (Exception ignored) {
      return;
    }

    class ManagedRowTransferHandler extends TransferHandler {
      private final DataFlavor rowFlavor;

      ManagedRowTransferHandler() {
        try {
          rowFlavor =
              new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.Integer");
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      protected Transferable createTransferable(JComponent c) {
        if (!(c instanceof JTable table)) return null;
        int row = table.getSelectedRow();
        if (row < 0) return null;
        final Integer payload = row;
        return new Transferable() {
          @Override
          public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {rowFlavor};
          }

          @Override
          public boolean isDataFlavorSupported(DataFlavor flavor) {
            return rowFlavor.equals(flavor);
          }

          @Override
          public Object getTransferData(DataFlavor flavor) {
            if (!isDataFlavorSupported(flavor)) return null;
            return payload;
          }
        };
      }

      @Override
      public int getSourceActions(JComponent c) {
        return MOVE;
      }

      @Override
      public boolean canImport(TransferSupport support) {
        if (!support.isDrop()) return false;
        if (!(support.getComponent() instanceof JTable)) return false;
        if (!support.isDataFlavorSupported(rowFlavor)) return false;
        if (currentManagedSortMode() != ManagedSortMode.CUSTOM) return false;
        support.setShowDropLocation(true);
        return true;
      }

      @Override
      public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        if (!(support.getComponent() instanceof JTable table)) return false;
        if (!(support.getDropLocation() instanceof JTable.DropLocation dl)) return false;

        int dropRow = dl.getRow();
        if (dropRow < 0) dropRow = table.getRowCount();

        Integer fromRow;
        try {
          Object payload = support.getTransferable().getTransferData(rowFlavor);
          if (!(payload instanceof Integer i)) return false;
          fromRow = i;
        } catch (Exception ex) {
          return false;
        }

        if (fromRow == dropRow || fromRow + 1 == dropRow) return false;
        int newRow = managedModel.moveRow(fromRow, dropRow);
        if (newRow < 0) return false;

        notifyManagedCustomOrderChanged();
        SwingUtilities.invokeLater(
            () -> {
              if (newRow >= 0 && newRow < managedModel.getRowCount()) {
                managedTable.getSelectionModel().setSelectionInterval(newRow, newRow);
                managedTable.scrollRectToVisible(managedTable.getCellRect(newRow, 0, true));
              }
              updateManagedButtons();
            });
        return true;
      }
    }

    managedTable.setTransferHandler(new ManagedRowTransferHandler());
  }

  public void setServerId(String serverId) {
    this.serverId = normalizeServerId(serverId);
    refreshCurrentServerViews();
    refreshOpenDetailsDialog();
  }

  public String currentServerId() {
    return serverId;
  }

  public void setOnJoinChannel(Consumer<String> onJoinChannel) {
    this.onJoinChannel = onJoinChannel;
  }

  public void setOnRunListRequest(Runnable onRunListRequest) {
    this.onRunListRequest = onRunListRequest;
  }

  public void setOnRunAlisRequest(Consumer<String> onRunAlisRequest) {
    this.onRunAlisRequest = onRunAlisRequest;
  }

  public void setOnAddChannelRequest(Consumer<String> onAddChannelRequest) {
    this.onAddChannelRequest = onAddChannelRequest;
  }

  public void setOnAttachChannelRequest(Consumer<String> onAttachChannelRequest) {
    this.onAttachChannelRequest = onAttachChannelRequest;
  }

  public void setOnDetachChannelRequest(Consumer<String> onDetachChannelRequest) {
    this.onDetachChannelRequest = onDetachChannelRequest;
  }

  public void setOnCloseChannelRequest(Consumer<String> onCloseChannelRequest) {
    this.onCloseChannelRequest = onCloseChannelRequest;
  }

  public void setOnAutoReattachChanged(BiConsumer<String, Boolean> onAutoReattachChanged) {
    this.onAutoReattachChanged = onAutoReattachChanged;
  }

  public void setOnManagedSortModeChanged(Consumer<ManagedSortMode> onManagedSortModeChanged) {
    this.onManagedSortModeChanged = onManagedSortModeChanged;
  }

  public void setOnManagedCustomOrderChanged(Consumer<List<String>> onManagedCustomOrderChanged) {
    this.onManagedCustomOrderChanged = onManagedCustomOrderChanged;
  }

  public void setOnManagedChannelSelected(Consumer<String> onManagedChannelSelected) {
    this.onManagedChannelSelected = onManagedChannelSelected;
  }

  public void setOnChannelTopicRequest(BiFunction<String, String, String> onChannelTopicRequest) {
    this.onChannelTopicRequest = onChannelTopicRequest;
  }

  public void setOnChannelBanListSnapshotRequest(
      BiFunction<String, String, List<String>> onChannelBanListSnapshotRequest) {
    this.onChannelBanListSnapshotRequest = onChannelBanListSnapshotRequest;
  }

  public void setOnChannelBanListRefreshRequest(
      BiConsumer<String, String> onChannelBanListRefreshRequest) {
    this.onChannelBanListRefreshRequest = onChannelBanListRefreshRequest;
  }

  public void beginList(String serverId, String banner) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    rowsByServer.put(sid, new ArrayList<>());
    statusByServer.put(sid, normalizeBanner(banner));
    loadingByServer.put(sid, Boolean.TRUE);
    if (requestTypeByServer.getOrDefault(sid, ListRequestType.UNKNOWN) == ListRequestType.UNKNOWN) {
      requestTypeByServer.put(sid, inferRequestTypeFromBanner(banner));
    }
    if (sid.equals(this.serverId)) {
      refreshListRows();
    }
    refreshOpenDetailsDialog();
  }

  public void appendEntry(String serverId, String channel, int visibleUsers, String topic) {
    appendEntries(serverId, List.of(new ListEntryRow(channel, visibleUsers, topic)));
  }

  public void appendEntries(String serverId, List<ListEntryRow> entries) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || entries == null || entries.isEmpty()) return;

    ArrayList<Row> toAppend = new ArrayList<>(entries.size());
    for (ListEntryRow entry : entries) {
      if (entry == null) continue;
      String ch = Objects.toString(entry.channel(), "").trim();
      if (ch.isEmpty()) continue;
      toAppend.add(
          new Row(
              ch, Math.max(0, entry.visibleUsers()), Objects.toString(entry.topic(), "").trim()));
    }
    if (toAppend.isEmpty()) return;

    ArrayList<Row> rows = rowsByServer.computeIfAbsent(sid, __ -> new ArrayList<>());
    rows.addAll(toAppend);

    if (sid.equals(this.serverId)) {
      listModel.addRows(toAppend);
      updateListHeader();
      updateListButtons();
    }
    refreshOpenDetailsDialog();
  }

  public void endList(String serverId, String summary) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    String base = Objects.toString(summary, "").trim();
    if (base.isEmpty()) base = "End of /LIST";
    statusByServer.put(sid, base);
    loadingByServer.put(sid, Boolean.FALSE);
    if (sid.equals(this.serverId)) {
      updateListHeader();
      updateListButtons();
    }
    refreshOpenDetailsDialog();
  }

  public void setManagedChannels(
      String serverId, List<ManagedChannelRow> rows, ManagedSortMode mode) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    ArrayList<ManagedChannelRow> normalized = new ArrayList<>();
    if (rows != null) {
      LinkedHashSet<String> seen = new LinkedHashSet<>();
      for (ManagedChannelRow row : rows) {
        if (row == null) continue;
        String channel = normalizeChannel(row.channel());
        if (channel.isEmpty()) continue;
        String key = channel.toLowerCase(Locale.ROOT);
        if (!seen.add(key)) continue;
        normalized.add(
            new ManagedChannelRow(
                channel,
                row.detached(),
                row.autoReattach(),
                row.users(),
                row.notifications(),
                row.modes()));
      }
    }

    managedRowsByServer.put(sid, normalized);
    managedSortModeByServer.put(sid, mode == null ? ManagedSortMode.CUSTOM : mode);
    if (sid.equals(this.serverId)) {
      refreshManagedRows();
    }
    refreshOpenDetailsDialog();
  }

  private void refreshCurrentServerViews() {
    refreshListRows();
    refreshManagedRows();
  }

  private void refreshListRows() {
    String sid = this.serverId;
    List<Row> rows =
        sid.isEmpty() ? List.of() : List.copyOf(rowsByServer.getOrDefault(sid, new ArrayList<>()));
    listModel.setRows(rows);
    updateListHeader();
    updateListButtons();
  }

  private void refreshManagedRows() {
    String sid = this.serverId;
    if (sid.isEmpty()) {
      syncingSortModeCombo = true;
      try {
        sortModeCombo.setSelectedItem(ManagedSortMode.CUSTOM);
      } finally {
        syncingSortModeCombo = false;
      }
      managedModel.setRows(List.of());
      updateManagedHeader();
      updateManagedButtons();
      return;
    }

    ManagedSortMode mode = managedSortModeByServer.getOrDefault(sid, ManagedSortMode.CUSTOM);
    List<ManagedChannelRow> rows =
        List.copyOf(managedRowsByServer.getOrDefault(sid, new ArrayList<>()));

    syncingSortModeCombo = true;
    try {
      sortModeCombo.setSelectedItem(mode);
    } finally {
      syncingSortModeCombo = false;
    }

    if (mode == ManagedSortMode.ALPHABETICAL) {
      ArrayList<ManagedChannelRow> sorted = new ArrayList<>(rows);
      sorted.sort((a, b) -> a.channel().compareToIgnoreCase(b.channel()));
      rows = List.copyOf(sorted);
    }
    managedModel.setRows(rows);

    updateManagedHeader();
    updateManagedButtons();
    notifyManagedChannelSelectionChanged();
  }

  private void updateListHeader() {
    String sid = this.serverId;
    if (sid.isEmpty()) {
      listSubtitle.setText(DEFAULT_HINT);
      return;
    }

    int totalCount = listModel.getRowCount();
    int visibleCount = listTable.getRowCount();
    String filter = Objects.toString(filterField.getText(), "").trim();
    boolean filtered = !filter.isEmpty();
    String status = Objects.toString(statusByServer.get(sid), "").trim();
    if (status.isEmpty()) {
      if (totalCount == 0) {
        listSubtitle.setText(DEFAULT_HINT);
      } else if (filtered) {
        listSubtitle.setText(sid + " - " + visibleCount + " of " + totalCount + " channels shown");
      } else {
        listSubtitle.setText(sid + " - " + totalCount + " channels");
      }
      return;
    }
    if (totalCount > 0) {
      if (filtered) {
        listSubtitle.setText(
            sid + " - " + status + " (" + visibleCount + " of " + totalCount + " shown)");
      } else {
        listSubtitle.setText(sid + " - " + status + " (" + totalCount + " channels)");
      }
    } else {
      listSubtitle.setText(sid + " - " + status);
    }
  }

  private void updateManagedHeader() {
    String sid = this.serverId;
    if (sid.isEmpty()) {
      managedSubtitle.setText(MANAGED_HINT);
      return;
    }
    int total = managedModel.getRowCount();
    int detached = managedModel.detachedCount();
    int attached = Math.max(0, total - detached);
    managedSubtitle.setText(
        sid
            + " - Managed: "
            + total
            + " channels ("
            + attached
            + " attached, "
            + detached
            + " detached)");
  }

  private void applyListFilter() {
    String filter = Objects.toString(filterField.getText(), "").trim();
    if (filter.isEmpty()) {
      listSorter.setRowFilter(null);
      updateListHeader();
      return;
    }

    String[] terms = filter.toLowerCase(Locale.ROOT).split("\\s+");
    listSorter.setRowFilter(
        new RowFilter<>() {
          @Override
          public boolean include(Entry<? extends ChannelListTableModel, ? extends Integer> entry) {
            String channel =
                Objects.toString(entry.getStringValue(LIST_COL_CHANNEL), "")
                    .toLowerCase(Locale.ROOT);
            String users =
                Objects.toString(entry.getStringValue(LIST_COL_USERS), "").toLowerCase(Locale.ROOT);
            String topic =
                Objects.toString(entry.getStringValue(LIST_COL_TOPIC), "").toLowerCase(Locale.ROOT);
            for (String term : terms) {
              if (term == null || term.isBlank()) continue;
              if (channel.contains(term) || users.contains(term) || topic.contains(term)) continue;
              return false;
            }
            return true;
          }
        });
    updateListHeader();
  }

  private void runFullListRequested() {
    String sid = this.serverId;
    if (sid.isEmpty()) return;
    if (!confirmFullListRequest()) return;
    rememberRequestType(sid, ListRequestType.FULL_LIST);
    if (!filterField.getText().isBlank()) {
      filterField.setText("");
    }

    Runnable cb = onRunListRequest;
    if (cb != null) SwingUtilities.invokeLater(cb);
  }

  private boolean confirmFullListRequest() {
    Window owner = SwingUtilities.getWindowAncestor(this);
    int choice =
        JOptionPane.showConfirmDialog(
            owner,
            "Request the full /list for this server?\n\n"
                + "This can be expensive on large networks and may take a while.",
            "Run Full /LIST",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return choice == JOptionPane.YES_OPTION;
  }

  private void runAlisRequested() {
    String sid = this.serverId;
    if (sid.isEmpty()) return;

    JTextField queryField = new JTextField(28);
    JCheckBox includeTopic = new JCheckBox("Include topic matching (-topic)", true);
    JCheckBox minEnabled = new JCheckBox("Minimum users (-min)");
    JSpinner minUsers = new JSpinner(new SpinnerNumberModel(10, 0, 1_000_000, 1));
    JCheckBox maxEnabled = new JCheckBox("Maximum users (-max)");
    JSpinner maxUsers = new JSpinner(new SpinnerNumberModel(500, 0, 1_000_000, 1));
    JCheckBox skipEnabled = new JCheckBox("Skip first results (-skip)");
    JSpinner skipCount = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));
    JCheckBox showModes = new JCheckBox("Show channel modes (-show m)", false);
    JCheckBox showTopicSetter = new JCheckBox("Show topic setter (-show t)", false);
    JComboBox<String> registrationScope =
        new JComboBox<>(
            new String[] {
              "Any channel registration",
              "Registered channels only (-show r)",
              "Unregistered channels only (-show u)"
            });
    JPanel showFlagsPanel = new JPanel(new MigLayout("insets 0, fillx", "[][grow]", "[]"));
    showFlagsPanel.add(showModes);
    showFlagsPanel.add(showTopicSetter, "gapleft 10");

    minUsers.setEnabled(false);
    maxUsers.setEnabled(false);
    skipCount.setEnabled(false);
    minEnabled.addActionListener(e -> minUsers.setEnabled(minEnabled.isSelected()));
    maxEnabled.addActionListener(e -> maxUsers.setEnabled(maxEnabled.isSelected()));
    skipEnabled.addActionListener(e -> skipCount.setEnabled(skipEnabled.isSelected()));

    JPanel form =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right][grow,fill]", "[]6[]6[]6[]6[]6[]6[]6[]"));
    form.add(new JLabel("Query pattern:"));
    form.add(queryField, "growx");
    form.add(new JLabel("Topic filter:"));
    form.add(includeTopic, "growx");
    form.add(minEnabled);
    form.add(minUsers, "w 120!");
    form.add(maxEnabled);
    form.add(maxUsers, "w 120!");
    form.add(skipEnabled);
    form.add(skipCount, "w 120!");
    form.add(new JLabel("Display extras:"));
    form.add(showFlagsPanel, "growx");
    form.add(new JLabel("Registration:"));
    form.add(registrationScope, "growx");

    Window owner = SwingUtilities.getWindowAncestor(this);
    int choice =
        JOptionPane.showConfirmDialog(
            owner,
            form,
            "Run ALIS Search",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) return;

    String query = Objects.toString(queryField.getText(), "").trim();
    Integer minUsersValue =
        minEnabled.isSelected() ? ((Number) minUsers.getValue()).intValue() : null;
    Integer maxUsersValue =
        maxEnabled.isSelected() ? ((Number) maxUsers.getValue()).intValue() : null;
    Integer skipValue =
        skipEnabled.isSelected() ? ((Number) skipCount.getValue()).intValue() : null;
    if (minUsersValue != null && maxUsersValue != null && minUsersValue > maxUsersValue) {
      int t = minUsersValue;
      minUsersValue = maxUsersValue;
      maxUsersValue = t;
    }
    AlisRegistrationFilter registrationFilter =
        switch (registrationScope.getSelectedIndex()) {
          case 1 -> AlisRegistrationFilter.REGISTERED_ONLY;
          case 2 -> AlisRegistrationFilter.UNREGISTERED_ONLY;
          default -> AlisRegistrationFilter.ANY;
        };
    AlisSearchOptions options =
        new AlisSearchOptions(
            includeTopic.isSelected(),
            minUsersValue,
            maxUsersValue,
            skipValue,
            showModes.isSelected(),
            showTopicSetter.isSelected(),
            registrationFilter);
    String cmd = buildAlisCommand(query, options);
    rememberRequestType(sid, ListRequestType.ALIS);
    beginList(sid, "Loading ALIS search results...");

    Consumer<String> cb = onRunAlisRequest;
    if (cb != null) SwingUtilities.invokeLater(() -> cb.accept(cmd));
  }

  static String buildAlisCommand(String query, boolean includeTopic) {
    return buildAlisCommand(query, AlisSearchOptions.defaults(includeTopic));
  }

  static String buildAlisCommand(String query, AlisSearchOptions options) {
    AlisSearchOptions opts = options == null ? AlisSearchOptions.defaults(false) : options;
    String q = Objects.toString(query, "").trim();
    StringBuilder raw = new StringBuilder("LIST ");
    raw.append(opts.includeTopic() ? "*" : (q.isEmpty() ? "*" : q));
    if (opts.includeTopic()) {
      raw.append(" -topic");
      raw.append(" ").append(q.isEmpty() ? "*" : q);
    }
    if (opts.minUsers() != null && opts.minUsers() >= 0) {
      raw.append(" -min ").append(opts.minUsers());
    }
    if (opts.maxUsers() != null && opts.maxUsers() >= 0) {
      raw.append(" -max ").append(opts.maxUsers());
    }
    if (opts.skipCount() != null && opts.skipCount() > 0) {
      raw.append(" -skip ").append(opts.skipCount());
    }

    StringBuilder showFlags = new StringBuilder();
    if (opts.showModes()) showFlags.append("m");
    if (opts.showTopicSetter()) showFlags.append("t");
    if (showFlags.length() > 0) {
      raw.append(" -show ").append(showFlags);
    }

    AlisRegistrationFilter registration =
        opts.registrationFilter() == null ? AlisRegistrationFilter.ANY : opts.registrationFilter();
    if (registration == AlisRegistrationFilter.REGISTERED_ONLY) {
      raw.append(" -show r");
    } else if (registration == AlisRegistrationFilter.UNREGISTERED_ONLY) {
      raw.append(" -show u");
    }

    return "/quote PRIVMSG ALIS :" + raw.toString().trim();
  }

  private void addChannelRequested() {
    String sid = this.serverId;
    if (sid.isEmpty()) return;
    String channel =
        Objects.toString(
                JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Channel name:",
                    "Add Channel",
                    JOptionPane.PLAIN_MESSAGE),
                "")
            .trim();
    channel = normalizeChannel(channel);
    if (channel.isEmpty()) return;

    Consumer<String> cb = onAddChannelRequest;
    if (cb != null) cb.accept(channel);
  }

  private void attachDetachSelectedRequested() {
    int row = managedTable.getSelectedRow();
    if (row < 0) return;
    ManagedChannelRow current = managedModel.rowAt(row);
    if (current == null) return;
    String channel = normalizeChannel(current.channel());
    if (channel.isEmpty()) return;

    if (current.detached()) {
      Consumer<String> cb = onAttachChannelRequest;
      if (cb != null) cb.accept(channel);
    } else {
      Consumer<String> cb = onDetachChannelRequest;
      if (cb != null) cb.accept(channel);
    }
  }

  private void closeSelectedRequested() {
    int row = managedTable.getSelectedRow();
    if (row < 0) return;
    ManagedChannelRow current = managedModel.rowAt(row);
    if (current == null) return;
    String channel = normalizeChannel(current.channel());
    if (channel.isEmpty()) return;

    int choice =
        JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(this),
            "Close managed channel \""
                + channel
                + "\"?\n\n"
                + "This removes it from the managed list.",
            "Close Channel",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) return;

    Consumer<String> cb = onCloseChannelRequest;
    if (cb != null) cb.accept(channel);
  }

  private void moveSelectedBy(int delta) {
    if (currentManagedSortMode() != ManagedSortMode.CUSTOM) return;
    int row = managedTable.getSelectedRow();
    if (row < 0) return;
    int moved = managedModel.moveRelative(row, delta);
    if (moved < 0) return;
    notifyManagedCustomOrderChanged();
    managedTable.getSelectionModel().setSelectionInterval(moved, moved);
    managedTable.scrollRectToVisible(managedTable.getCellRect(moved, 0, true));
    updateManagedButtons();
  }

  private void onSortModeChangedByUser() {
    if (syncingSortModeCombo) return;
    ManagedSortMode mode = currentManagedSortMode();
    String sid = this.serverId;
    if (sid.isEmpty() || mode == null) return;

    managedSortModeByServer.put(sid, mode);
    if (mode == ManagedSortMode.ALPHABETICAL) {
      ArrayList<ManagedChannelRow> sorted = new ArrayList<>(managedModel.rowsSnapshot());
      sorted.sort((a, b) -> a.channel().compareToIgnoreCase(b.channel()));
      managedModel.setRows(sorted);
    } else {
      managedModel.setRows(managedRowsByServer.getOrDefault(sid, new ArrayList<>()));
    }

    Consumer<ManagedSortMode> cb = onManagedSortModeChanged;
    if (cb != null) cb.accept(mode);
    updateManagedButtons();
  }

  private void notifyManagedCustomOrderChanged() {
    if (currentManagedSortMode() != ManagedSortMode.CUSTOM) return;
    String sid = this.serverId;
    if (sid.isEmpty()) return;

    ArrayList<ManagedChannelRow> snapshot = new ArrayList<>(managedModel.rowsSnapshot());
    managedRowsByServer.put(sid, snapshot);

    ArrayList<String> order = new ArrayList<>(snapshot.size());
    for (ManagedChannelRow row : snapshot) {
      String channel = normalizeChannel(row.channel());
      if (channel.isEmpty()) continue;
      order.add(channel);
    }

    Consumer<List<String>> cb = onManagedCustomOrderChanged;
    if (cb != null) cb.accept(List.copyOf(order));
  }

  private ManagedSortMode currentManagedSortMode() {
    Object selected = sortModeCombo.getSelectedItem();
    if (selected instanceof ManagedSortMode mode) return mode;
    return ManagedSortMode.CUSTOM;
  }

  private void updateListButtons() {
    int row = listTable.getSelectedRow();
    boolean hasServer = !this.serverId.isBlank();
    boolean busy = isCurrentServerListLoading();
    listDetailsButton.setEnabled(row >= 0 && hasServer);
    runListButton.setEnabled(hasServer && !busy);
    runAlisButton.setEnabled(hasServer && !busy);
    updateListBusyIndicator(busy);
  }

  private void updateManagedButtons() {
    int row = managedTable.getSelectedRow();
    boolean hasSelection = row >= 0 && managedModel.rowAt(row) != null;
    ManagedChannelRow selected = hasSelection ? managedModel.rowAt(row) : null;

    if (selected != null && selected.detached()) {
      attachDetachButton.setIcon(SvgIcons.action("play", ACTION_ICON_SIZE));
      attachDetachButton.setDisabledIcon(SvgIcons.actionDisabled("play", ACTION_ICON_SIZE));
      attachDetachButton.setToolTipText("Attach selected channel");
      attachDetachButton.getAccessibleContext().setAccessibleName("Attach");
    } else {
      attachDetachButton.setIcon(SvgIcons.action("pause", ACTION_ICON_SIZE));
      attachDetachButton.setDisabledIcon(SvgIcons.actionDisabled("pause", ACTION_ICON_SIZE));
      attachDetachButton.setToolTipText("Detach selected channel");
      attachDetachButton.getAccessibleContext().setAccessibleName("Detach");
    }

    boolean customMode = currentManagedSortMode() == ManagedSortMode.CUSTOM;
    int total = managedModel.getRowCount();
    addChannelButton.setEnabled(!this.serverId.isBlank());
    attachDetachButton.setEnabled(hasSelection);
    closeChannelButton.setEnabled(hasSelection);
    managedDetailsButton.setEnabled(hasSelection);
    moveUpButton.setEnabled(customMode && hasSelection && row > 0);
    moveDownButton.setEnabled(customMode && hasSelection && row >= 0 && row < total - 1);
  }

  private void notifyManagedChannelSelectionChanged() {
    int row = managedTable.getSelectedRow();
    String channel = "";
    if (row >= 0) {
      ManagedChannelRow selected = managedModel.rowAt(row);
      if (selected != null) {
        channel = normalizeChannel(selected.channel());
      }
    }
    Consumer<String> cb = onManagedChannelSelected;
    if (cb != null && !channel.isEmpty()) cb.accept(channel);
  }

  private void showManagedDetailsForSelection() {
    int row = managedTable.getSelectedRow();
    if (row < 0) return;
    ManagedChannelRow selected = managedModel.rowAt(row);
    if (selected == null) return;

    String sid = this.serverId;
    if (sid.isBlank()) return;

    Row listRow = findListRowByChannel(sid, selected.channel());
    String listTopic = listRow == null ? "" : listRow.topic();
    String topic = topicSnapshotForChannel(sid, selected.channel(), listTopic);

    ChannelDetails details =
        new ChannelDetails(
            sid,
            ChannelDetailsSource.MANAGED,
            selected.channel(),
            selected.detached() ? "Detached" : "Attached",
            topic,
            selected.modes(),
            selected.detached() ? -1 : selected.users(),
            selected.notifications(),
            selected.autoReattach());
    showChannelDetailsDialog(details);
  }

  private void showServerListDetailsForSelection() {
    int viewRow = listTable.getSelectedRow();
    if (viewRow < 0) return;
    int modelRow = listTable.convertRowIndexToModel(viewRow);
    Row selected = listModel.rowAt(modelRow);
    if (selected == null) return;

    String sid = this.serverId;
    if (sid.isBlank()) return;

    ManagedChannelRow managed = findManagedRowByChannel(sid, selected.channel());
    String state = managed == null ? "Not managed" : (managed.detached() ? "Detached" : "Attached");
    String modes = managed == null ? "(Unknown)" : managed.modes();
    int notifications = managed == null ? 0 : managed.notifications();
    boolean autoReattach = managed != null && managed.autoReattach();

    ChannelDetails details =
        new ChannelDetails(
            sid,
            ChannelDetailsSource.SERVER_LIST,
            selected.channel(),
            state,
            topicSnapshotForChannel(sid, selected.channel(), selected.topic()),
            modes,
            selected.visibleUsers(),
            notifications,
            autoReattach);
    showChannelDetailsDialog(details);
  }

  private String topicSnapshotForChannel(String sid, String channel, String fallbackTopic) {
    BiFunction<String, String, String> cb = onChannelTopicRequest;
    if (cb != null) {
      try {
        String fromCallback = Objects.toString(cb.apply(sid, channel), "").trim();
        if (!fromCallback.isEmpty()) return fromCallback;
      } catch (Exception ignored) {
      }
    }
    Row row = findListRowByChannel(sid, channel);
    String fromList = row == null ? "" : Objects.toString(row.topic(), "").trim();
    if (!fromList.isEmpty()) return fromList;
    return Objects.toString(fallbackTopic, "").trim();
  }

  private String banListDisplayTextForChannel(String sid, String channel) {
    BiFunction<String, String, List<String>> cb = onChannelBanListSnapshotRequest;
    if (cb == null) {
      return "Ban-list integration is not available.";
    }
    try {
      List<String> rows = cb.apply(sid, channel);
      if (rows == null || rows.isEmpty()) {
        return "No ban entries cached.\nUse Refresh Ban List to request MODE +b.";
      }
      return String.join("\n", rows);
    } catch (Exception ignored) {
      return "Ban list unavailable.";
    }
  }

  private void requestBanListRefresh(String sid, String channel) {
    BiConsumer<String, String> cb = onChannelBanListRefreshRequest;
    if (cb == null) return;
    try {
      cb.accept(sid, channel);
    } catch (Exception ignored) {
    }
  }

  private ManagedChannelRow findManagedRowByChannel(String sid, String channel) {
    String needle = normalizeChannel(channel);
    if (needle.isEmpty()) return null;
    List<ManagedChannelRow> rows = managedRowsByServer.getOrDefault(sid, new ArrayList<>());
    for (ManagedChannelRow row : rows) {
      if (row == null) continue;
      if (needle.equalsIgnoreCase(normalizeChannel(row.channel()))) return row;
    }
    return null;
  }

  private Row findListRowByChannel(String sid, String channel) {
    String needle = normalizeChannel(channel);
    if (needle.isEmpty()) return null;
    List<Row> rows = rowsByServer.getOrDefault(sid, new ArrayList<>());
    for (Row row : rows) {
      if (row == null) continue;
      if (needle.equalsIgnoreCase(normalizeChannel(row.channel()))) return row;
    }
    return null;
  }

  public void refreshOpenChannelDetails(String serverId, String channel) {
    ChannelDetailsDialogState state = channelDetailsDialog;
    if (state == null) return;
    String sid = normalizeServerId(serverId);
    String ch = normalizeChannel(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;
    if (!sid.equals(state.serverId())) return;
    if (!ch.equalsIgnoreCase(state.channel())) return;
    refreshOpenDetailsDialog();
  }

  private void refreshOpenDetailsDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::refreshOpenDetailsDialog);
      return;
    }

    ChannelDetailsDialogState state = channelDetailsDialog;
    if (state == null) return;
    if (!state.dialog().isDisplayable()) {
      channelDetailsDialog = null;
      return;
    }

    String sid = state.serverId();
    String channel = state.channel();
    ManagedChannelRow managed = findManagedRowByChannel(sid, channel);
    Row list = findListRowByChannel(sid, channel);

    String statusText =
        managed == null ? "Not managed" : (managed.detached() ? "Detached" : "Attached");
    String modesText = managed == null ? "(Unknown)" : displayManagedModes(managed);
    String notificationsText =
        managed == null ? "0" : String.valueOf(Math.max(0, managed.notifications()));
    String autoReattachText =
        managed == null ? "Not managed" : (managed.autoReattach() ? "Enabled" : "Disabled");
    String usersText;
    if (state.source() == ChannelDetailsSource.MANAGED) {
      if (managed == null) {
        usersText = "Not available";
      } else {
        usersText =
            managed.detached() ? "Unavailable while detached" : String.valueOf(managed.users());
      }
    } else if (list != null) {
      usersText = String.valueOf(Math.max(0, list.visibleUsers()));
    } else if (managed != null && !managed.detached()) {
      usersText = String.valueOf(managed.users());
    } else {
      usersText = "Not available";
    }
    String topicText = topicSnapshotForChannel(sid, channel, list == null ? "" : list.topic());
    String banListText = banListDisplayTextForChannel(sid, channel);

    setFieldText(state.stateField(), statusText);
    setFieldText(state.usersField(), usersText);
    setFieldText(state.notificationsField(), notificationsText);
    setFieldText(state.modesField(), modesText);
    setFieldText(state.autoReattachField(), autoReattachText);
    setAreaText(state.topicArea(), fallback(topicText, "(none)"));
    setAreaText(state.banListArea(), banListText);
  }

  private void showChannelDetailsDialog(ChannelDetails details) {
    if (details == null) return;

    ChannelDetailsDialogState existing = channelDetailsDialog;
    if (existing != null && existing.dialog().isDisplayable()) {
      existing.dialog().dispose();
    }

    String sid = normalizeServerId(details.serverId());
    String channel = normalizeChannel(details.channel());

    JTextField stateField = readOnlyField(details.state());
    JTextField usersField =
        readOnlyField(
            details.users() < 0 ? "Unavailable while detached" : String.valueOf(details.users()));
    JTextField notificationsField =
        readOnlyField(String.valueOf(Math.max(0, details.notifications())));
    JTextField modesField = readOnlyField(fallback(details.modes(), "(Unknown)"));
    JTextField autoReattachField = readOnlyField(details.autoReattach() ? "Enabled" : "Disabled");
    JTextArea topicArea = readOnlyArea(fallback(details.topic(), "(none)"), 4);
    JTextArea banListArea = readOnlyArea(banListDisplayTextForChannel(sid, channel), 6);

    JScrollPane topicScroll = new JScrollPane(topicArea);
    JScrollPane banListScroll = new JScrollPane(banListArea);
    topicScroll.setMinimumSize(new Dimension(180, 120));
    banListScroll.setMinimumSize(new Dimension(180, 120));

    JButton refreshBanListButton = new JButton("Refresh Ban List");
    refreshBanListButton.setFocusable(false);
    refreshBanListButton.addActionListener(
        e -> {
          requestBanListRefresh(sid, channel);
          setAreaText(banListArea, "Requested MODE +b...\nWaiting for server response.");
        });

    JPanel content =
        new JPanel(
            new MigLayout(
                "insets 12, fill, wrap 6",
                "[right][grow,fill][right][grow,fill][right][grow,fill]",
                "[]8[]8[]8[grow,fill]8[]"));
    content.add(new JLabel("Server"));
    content.add(readOnlyField(details.serverId()), "growx");
    content.add(new JLabel("Channel"));
    content.add(readOnlyField(details.channel()), "span 3,growx");

    content.add(new JLabel("Source"));
    content.add(
        readOnlyField(
            details.source() == ChannelDetailsSource.MANAGED ? "Managed" : "Server /LIST"),
        "growx");
    content.add(new JLabel("State"));
    content.add(stateField, "growx");
    content.add(new JLabel("Auto-join"));
    content.add(autoReattachField, "growx");

    content.add(new JLabel("Users"));
    content.add(usersField, "growx");
    content.add(new JLabel("Notifications"));
    content.add(notificationsField, "growx");
    content.add(new JLabel("Modes"));
    content.add(modesField, "growx");

    content.add(new JLabel("Topic"), "top");
    content.add(topicScroll, "span 5,grow,pushy");

    content.add(new JLabel("Ban List"), "top");
    JPanel banPanel = new JPanel(new BorderLayout(0, 6));
    banPanel.add(refreshBanListButton, BorderLayout.NORTH);
    banPanel.add(banListScroll, BorderLayout.CENTER);
    content.add(banPanel, "span 5,growx,hmin 120");

    JButton closeButton = new JButton("Close");
    JPanel south = new JPanel(new MigLayout("insets 0 12 12 12, fillx", "[grow, right]", "[]"));
    south.add(closeButton);

    JPanel root = new JPanel(new BorderLayout(0, 8));
    root.add(content, BorderLayout.CENTER);
    root.add(south, BorderLayout.SOUTH);

    Window owner = SwingUtilities.getWindowAncestor(this);
    JDialog dialog =
        new JDialog(
            owner, "Channel Details - " + details.channel(), Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setContentPane(root);
    dialog.setSize(700, 520);
    dialog.setMinimumSize(new Dimension(620, 420));
    dialog.setLocationRelativeTo(owner);
    dialog.getRootPane().setDefaultButton(closeButton);
    closeButton.addActionListener(e -> dialog.dispose());

    channelDetailsDialog =
        new ChannelDetailsDialogState(
            dialog,
            sid,
            channel,
            details.source(),
            stateField,
            usersField,
            notificationsField,
            modesField,
            autoReattachField,
            topicArea,
            banListArea);
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            if (channelDetailsDialog != null && channelDetailsDialog.dialog() == dialog) {
              channelDetailsDialog = null;
            }
          }
        });
    refreshOpenDetailsDialog();
    dialog.setVisible(true);
  }

  private static void setFieldText(JTextField field, String value) {
    if (field == null) return;
    String next = Objects.toString(value, "");
    if (!Objects.equals(field.getText(), next)) {
      field.setText(next);
    }
    field.setCaretPosition(0);
  }

  private static void setAreaText(JTextArea area, String value) {
    if (area == null) return;
    String next = Objects.toString(value, "");
    if (!Objects.equals(area.getText(), next)) {
      area.setText(next);
    }
    area.setCaretPosition(0);
  }

  private static JTextField readOnlyField(String value) {
    JTextField field = new JTextField(fallback(value, "(Unknown)"));
    field.setEditable(false);
    field.setCaretPosition(0);
    return field;
  }

  private static JTextArea readOnlyArea(String value, int rows) {
    JTextArea area = new JTextArea(Objects.toString(value, ""));
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setRows(Math.max(1, rows));
    area.setCaretPosition(0);
    return area;
  }

  private static JTextArea createSubtitleArea(String text) {
    JTextArea area = new JTextArea(Objects.toString(text, ""));
    area.setRows(2);
    area.setEditable(false);
    area.setOpaque(false);
    area.setFocusable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setBorder(BorderFactory.createEmptyBorder());
    return area;
  }

  private static String fallback(String value, String fallback) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? Objects.toString(fallback, "") : v;
  }

  private static void configureActionButton(
      JButton button, String iconName, String tooltip, String accessibleName) {
    button.setText("");
    button.setIcon(SvgIcons.action(iconName, ACTION_ICON_SIZE));
    button.setDisabledIcon(SvgIcons.actionDisabled(iconName, ACTION_ICON_SIZE));
    button.setToolTipText(tooltip);
    button.setFocusable(false);
    button.setPreferredSize(ACTION_BUTTON_SIZE);
    button.getAccessibleContext().setAccessibleName(accessibleName);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private boolean isCurrentServerListLoading() {
    String sid = this.serverId;
    return !sid.isBlank() && Boolean.TRUE.equals(loadingByServer.get(sid));
  }

  private void updateListBusyIndicator(boolean busy) {
    String sid = normalizeServerId(this.serverId);
    ListRequestType requestType =
        sid.isEmpty()
            ? ListRequestType.UNKNOWN
            : requestTypeByServer.getOrDefault(sid, ListRequestType.UNKNOWN);

    if (!busy) {
      listTable.setCursor(Cursor.getDefaultCursor());
      if (requestType == ListRequestType.ALIS && alisActivityState == AlisActivityState.SPINNER) {
        startAlisConfirmedIndicator();
      } else if (alisActivityState != AlisActivityState.IDLE) {
        restoreAlisDefaultIcon();
      }
      if (!sid.isEmpty()) {
        requestTypeByServer.remove(sid);
      }
      return;
    }

    if (requestType == ListRequestType.ALIS) {
      startAlisSpinnerIndicator();
    } else if (alisActivityState != AlisActivityState.IDLE) {
      restoreAlisDefaultIcon();
    }
    listTable.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
  }

  private void rememberRequestType(String sid, ListRequestType type) {
    String serverId = normalizeServerId(sid);
    if (serverId.isEmpty() || type == null) return;
    requestTypeByServer.put(serverId, type);
  }

  private static ListRequestType inferRequestTypeFromBanner(String banner) {
    String text = Objects.toString(banner, "").trim().toLowerCase(Locale.ROOT);
    if (text.contains("alis")) return ListRequestType.ALIS;
    return ListRequestType.UNKNOWN;
  }

  private void startAlisSpinnerIndicator() {
    if (alisActivityState == AlisActivityState.SPINNER) return;
    alisActivityState = AlisActivityState.SPINNER;
    alisSpinnerAngleDeg = 0;
    applyAlisActivityIcon();
    if (!alisActivityTimer.isRunning()) {
      alisActivityTimer.start();
    }
  }

  private void startAlisConfirmedIndicator() {
    alisActivityState = AlisActivityState.CONFIRMED;
    alisConfirmedStartMs = System.currentTimeMillis();
    alisConfirmedAlpha = 1f;
    applyAlisActivityIcon();
    if (!alisActivityTimer.isRunning()) {
      alisActivityTimer.start();
    }
  }

  private void applyAlisActivityIcon() {
    runAlisButton.setIcon(runAlisActivityIcon);
    runAlisButton.setDisabledIcon(runAlisActivityIcon);
    runAlisButton.repaint();
  }

  private void onAlisActivityTick() {
    if (alisActivityState == AlisActivityState.SPINNER) {
      alisSpinnerAngleDeg += 18;
      if (alisSpinnerAngleDeg >= 360) {
        alisSpinnerAngleDeg -= 360;
      }
      runAlisButton.repaint();
      return;
    }

    if (alisActivityState == AlisActivityState.CONFIRMED) {
      long elapsed = Math.max(0L, System.currentTimeMillis() - alisConfirmedStartMs);
      if (elapsed <= 200L) {
        alisConfirmedAlpha = 1f;
      } else {
        float p = Math.min(1f, (float) (elapsed - 200L) / 900f);
        alisConfirmedAlpha = 1f - p;
        if (p >= 1f) {
          restoreAlisDefaultIcon();
          return;
        }
      }
      runAlisButton.repaint();
      return;
    }

    if (alisActivityTimer.isRunning()) {
      alisActivityTimer.stop();
    }
  }

  private void restoreAlisDefaultIcon() {
    alisActivityState = AlisActivityState.IDLE;
    alisSpinnerAngleDeg = 0;
    alisConfirmedStartMs = 0L;
    alisConfirmedAlpha = 1f;
    if (alisActivityTimer.isRunning()) {
      alisActivityTimer.stop();
    }
    runAlisButton.setIcon(runAlisDefaultIcon);
    runAlisButton.setDisabledIcon(runAlisDefaultDisabledIcon);
    runAlisButton.repaint();
  }

  @Override
  public void removeNotify() {
    restoreAlisDefaultIcon();
    super.removeNotify();
  }

  private static String normalizeBanner(String banner) {
    String b = Objects.toString(banner, "").trim();
    return b.isEmpty() ? "Loading channel list..." : b;
  }

  private static String normalizeChannel(String channel) {
    String c = Objects.toString(channel, "").trim();
    if (c.isEmpty()) return "";
    return (c.startsWith("#") || c.startsWith("&")) ? c : "";
  }

  private static String displayManagedUsers(ManagedChannelRow row) {
    if (row == null) return "";
    if (row.detached()) return "-";
    return String.valueOf(Math.max(0, row.users()));
  }

  private static String displayManagedNotifications(ManagedChannelRow row) {
    if (row == null) return "0";
    return String.valueOf(Math.max(0, row.notifications()));
  }

  private static String displayManagedModes(ManagedChannelRow row) {
    if (row == null) return "";
    String modes = Objects.toString(row.modes(), "").trim();
    if (!modes.isEmpty()) return modes;
    return "(Unknown)";
  }

  private final class AlisActivityIcon implements Icon {
    @Override
    public int getIconWidth() {
      return ACTION_ICON_SIZE;
    }

    @Override
    public int getIconHeight() {
      return ACTION_ICON_SIZE;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = Math.max(8, ACTION_ICON_SIZE - 4);
        int px = x + (ACTION_ICON_SIZE - size) / 2;
        int py = y + (ACTION_ICON_SIZE - size) / 2;

        if (alisActivityState == AlisActivityState.SPINNER) {
          Color spinnerColor =
              c != null && c.getForeground() != null ? c.getForeground() : Color.GRAY;
          g2.setColor(spinnerColor);
          g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawArc(px, py, size, size, alisSpinnerAngleDeg, 270);
          return;
        }

        if (alisActivityState == AlisActivityState.CONFIRMED) {
          g2.setComposite(
              AlphaComposite.getInstance(
                  AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alisConfirmedAlpha))));
          g2.setColor(new Color(0x2ecc71));
          g2.fillOval(px, py, size, size);
        }
      } finally {
        g2.dispose();
      }
    }
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

    void addRows(List<Row> rows) {
      if (rows == null || rows.isEmpty()) return;
      int from = this.rows.size();
      for (Row row : rows) {
        if (row == null) continue;
        this.rows.add(row);
      }
      int to = this.rows.size() - 1;
      if (to >= from) {
        fireTableRowsInserted(from, to);
      }
    }

    String channelAt(int row) {
      if (row < 0 || row >= rows.size()) return "";
      Row r = rows.get(row);
      return r == null ? "" : r.channel();
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
      return (column >= 0 && column < COLS.length) ? COLS[column] : "";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == LIST_COL_USERS ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return "";
      Row row = rows.get(rowIndex);
      if (row == null) return "";
      return switch (columnIndex) {
        case LIST_COL_CHANNEL -> row.channel();
        case LIST_COL_USERS -> row.visibleUsers();
        case LIST_COL_TOPIC -> row.topic();
        default -> "";
      };
    }
  }

  private final class ManagedChannelTableModel extends AbstractTableModel {
    private static final String[] COLS = {
      "Channel", "State", "Users", "Notifications", "Modes", "Auto-join"
    };

    private final ArrayList<ManagedChannelRow> rows = new ArrayList<>();
    private BiConsumer<String, Boolean> onAutoReattachChanged;

    void setRows(List<ManagedChannelRow> rows) {
      this.rows.clear();
      if (rows != null && !rows.isEmpty()) this.rows.addAll(rows);
      fireTableDataChanged();
    }

    void setOnAutoReattachChanged(BiConsumer<String, Boolean> onAutoReattachChanged) {
      this.onAutoReattachChanged = onAutoReattachChanged;
    }

    List<ManagedChannelRow> rowsSnapshot() {
      return List.copyOf(rows);
    }

    int detachedCount() {
      int count = 0;
      for (ManagedChannelRow row : rows) {
        if (row != null && row.detached()) count++;
      }
      return count;
    }

    ManagedChannelRow rowAt(int row) {
      if (row < 0 || row >= rows.size()) return null;
      return rows.get(row);
    }

    int moveRelative(int row, int delta) {
      int next = row + delta;
      if (row < 0 || row >= rows.size()) return -1;
      if (next < 0 || next >= rows.size()) return -1;
      ManagedChannelRow moving = rows.remove(row);
      rows.add(next, moving);
      fireTableDataChanged();
      return next;
    }

    int moveRow(int fromIndex, int insertBefore) {
      if (fromIndex < 0 || fromIndex >= rows.size()) return -1;
      int boundedInsert = Math.max(0, Math.min(insertBefore, rows.size()));
      ManagedChannelRow moving = rows.remove(fromIndex);
      if (boundedInsert > fromIndex) boundedInsert--;
      boundedInsert = Math.max(0, Math.min(boundedInsert, rows.size()));
      rows.add(boundedInsert, moving);
      fireTableDataChanged();
      return boundedInsert;
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
      return columnIndex == MANAGED_COL_AUTO_REATTACH ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == MANAGED_COL_AUTO_REATTACH;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return "";
      ManagedChannelRow row = rows.get(rowIndex);
      if (row == null) return "";
      return switch (columnIndex) {
        case MANAGED_COL_CHANNEL -> row.channel();
        case MANAGED_COL_STATE -> row.detached() ? "Detached" : "Attached";
        case MANAGED_COL_USERS -> displayManagedUsers(row);
        case MANAGED_COL_NOTIFICATIONS -> displayManagedNotifications(row);
        case MANAGED_COL_MODES -> displayManagedModes(row);
        case MANAGED_COL_AUTO_REATTACH -> row.autoReattach();
        default -> "";
      };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex != MANAGED_COL_AUTO_REATTACH) return;
      if (rowIndex < 0 || rowIndex >= rows.size()) return;
      ManagedChannelRow before = rows.get(rowIndex);
      if (before == null) return;
      boolean nextValue = Boolean.TRUE.equals(aValue);
      if (before.autoReattach() == nextValue) return;

      ManagedChannelRow next =
          new ManagedChannelRow(
              before.channel(),
              before.detached(),
              nextValue,
              before.users(),
              before.notifications(),
              before.modes());
      rows.set(rowIndex, next);
      fireTableCellUpdated(rowIndex, columnIndex);

      BiConsumer<String, Boolean> cb = onAutoReattachChanged;
      if (cb != null) cb.accept(before.channel(), nextValue);
    }
  }

  private record ChannelDetailsDialogState(
      JDialog dialog,
      String serverId,
      String channel,
      ChannelDetailsSource source,
      JTextField stateField,
      JTextField usersField,
      JTextField notificationsField,
      JTextField modesField,
      JTextField autoReattachField,
      JTextArea topicArea,
      JTextArea banListArea) {}

  private record Row(String channel, int visibleUsers, String topic) {}
}
