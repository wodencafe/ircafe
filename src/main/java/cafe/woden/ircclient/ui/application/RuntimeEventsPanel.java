package cafe.woden.ircclient.ui.application;

import cafe.woden.ircclient.app.RuntimeDiagnosticEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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

/** Generic table panel for displaying rolling runtime diagnostic events with row detail dialog. */
public final class RuntimeEventsPanel extends JPanel {
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss")
          .withLocale(Locale.ROOT)
          .withZone(ZoneId.systemDefault());

  private static final int COL_TIME = 0;
  private static final int COL_LEVEL = 1;
  private static final int COL_TYPE = 2;
  private static final int COL_SUMMARY = 3;

  private final Supplier<List<RuntimeDiagnosticEvent>> eventsSupplier;
  private final RuntimeEventsTableModel model = new RuntimeEventsTableModel();
  private final JTable table = new JTable(model);
  private final JLabel title = new JLabel();
  private final JLabel subtitle = new JLabel();
  private final JButton detailsButton = new JButton("Details");
  private final JButton refreshButton = new JButton("Refresh");
  private final Timer refreshTimer;

  public RuntimeEventsPanel(
      String titleText,
      String subtitleText,
      Supplier<List<RuntimeDiagnosticEvent>> eventsSupplier) {
    super(new BorderLayout());
    this.eventsSupplier = Objects.requireNonNull(eventsSupplier, "eventsSupplier");

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

    JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 8));
    controls.setOpaque(false);
    detailsButton.addActionListener(e -> showDetailsForSelectedRow());
    refreshButton.addActionListener(e -> refreshNow());
    controls.add(detailsButton);
    controls.add(refreshButton);
    add(controls, BorderLayout.SOUTH);

    refreshTimer = new Timer(1000, e -> refreshNow());
    refreshTimer.setRepeats(true);
    refreshTimer.start();
    addHierarchyListener(this::onHierarchyChanged);

    refreshNow();
    updateButtons();
  }

  public void refreshNow() {
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
    int count = model.getRowCount();
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
      refreshTimer.stop();
      return;
    }
    if (!refreshTimer.isRunning()) {
      refreshTimer.start();
    }
  }

  private void updateButtons() {
    detailsButton.setEnabled(selectedEvent() != null);
  }

  private RuntimeDiagnosticEvent selectedEvent() {
    int row = table.getSelectedRow();
    if (row < 0) return null;
    int modelRow = table.convertRowIndexToModel(row);
    return model.rowAt(modelRow);
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

    void setRows(List<RuntimeDiagnosticEvent> nextRows) {
      rows.clear();
      if (nextRows != null && !nextRows.isEmpty()) {
        rows.addAll(nextRows);
      }
      fireTableDataChanged();
    }
  }
}
