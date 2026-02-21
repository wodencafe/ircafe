package cafe.woden.ircclient.ui.nickcolors;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * UI for managing per-nick color overrides.
 *
 * <p>Overrides are stored under {@code ircafe.ui.nickColorOverrides} in the runtime YAML.
 */
@Component
@Lazy
public class NickColorOverridesDialog {

  private final NickColorService nickColors;
  private final RuntimeConfigStore runtimeConfig;
  private final ChatTranscriptStore transcripts;

  private JDialog dialog;

  public NickColorOverridesDialog(NickColorService nickColors,
                                 RuntimeConfigStore runtimeConfig,
                                 ChatTranscriptStore transcripts) {
    this.nickColors = nickColors;
    this.runtimeConfig = runtimeConfig;
    this.transcripts = transcripts;
  }

  public void open(Window owner) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> open(owner));
      return;
    }

    if (dialog != null && dialog.isShowing()) {
      dialog.toFront();
      dialog.requestFocus();
      return;
    }

    OverridesTableModel model = new OverridesTableModel(seedFromService());
    JTable table = new JTable(model);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setAutoCreateRowSorter(true);
    table.setRowHeight(26);

    table.getColumnModel().getColumn(1).setCellRenderer(new ColorCellRenderer());

    JScrollPane scroll = new JScrollPane(table);
    scroll.setPreferredSize(new Dimension(640, 340));

    JLabel help = new JLabel("Overrides apply case-insensitively and take precedence over the palette.");
    help.putClientProperty(FlatClientProperties.STYLE, "font: -1");

    JButton add = new JButton("Add...");
    JButton edit = new JButton("Edit...");
    JButton remove = new JButton("Remove");
    JButton clear = new JButton("Clear");

    add.setIcon(SvgIcons.action("plus", 16));
    add.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    edit.setIcon(SvgIcons.action("edit", 16));
    edit.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
    remove.setIcon(SvgIcons.action("trash", 16));
    remove.setDisabledIcon(SvgIcons.actionDisabled("trash", 16));
    clear.setIcon(SvgIcons.action("close", 16));
    clear.setDisabledIcon(SvgIcons.actionDisabled("close", 16));

    edit.setEnabled(false);
    remove.setEnabled(false);
    clear.setEnabled(model.getRowCount() > 0);

    // Enable/disable actions based on selection.
    ListSelectionListener selListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        int count = table.getSelectedRowCount();
        edit.setEnabled(count == 1);
        remove.setEnabled(count > 0);
      }
    };
    table.getSelectionModel().addListSelectionListener(selListener);

    Runnable doAdd = () -> {
      NickColorOverrideEntryDialog.Entry seed = null;
      NickColorOverrideEntryDialog.open(owner, "Add Nick Color", seed).ifPresent(entry -> {
        model.upsert(entry.nickLower(), entry.hex());
        clear.setEnabled(model.getRowCount() > 0);
      });
    };

    Runnable doEdit = () -> {
      int viewRow = table.getSelectedRow();
      if (viewRow < 0) return;
      int row = table.convertRowIndexToModel(viewRow);
      NickColorOverrideEntryDialog.Entry cur = model.getEntry(row);
      NickColorOverrideEntryDialog.open(owner, "Edit Nick Color", cur).ifPresent(entry -> {
        model.remove(row);
        model.upsert(entry.nickLower(), entry.hex());
        clear.setEnabled(model.getRowCount() > 0);
      });
    };

    Runnable doRemove = () -> {
      int[] viewRows = table.getSelectedRows();
      if (viewRows == null || viewRows.length == 0) return;
      // Remove in descending model-index order.
      List<Integer> rows = new ArrayList<>();
      for (int vr : viewRows) {
        rows.add(table.convertRowIndexToModel(vr));
      }
      rows.sort(Comparator.reverseOrder());
      for (int r : rows) {
        model.remove(r);
      }
      clear.setEnabled(model.getRowCount() > 0);
    };

    add.addActionListener(e -> doAdd.run());
    edit.addActionListener(e -> doEdit.run());
    remove.addActionListener(e -> doRemove.run());
    clear.addActionListener(e -> {
      if (JOptionPane.showConfirmDialog(dialog,
          "Clear all nick color overrides?",
          "Clear Overrides",
          JOptionPane.OK_CANCEL_OPTION,
          JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
        model.clear();
        clear.setEnabled(false);
      }
    });

    table.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (e.getClickCount() == 2 && table.getSelectedRowCount() == 1) {
          doEdit.run();
        }
      }
    });

    JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
    leftButtons.add(add);
    leftButtons.add(edit);
    leftButtons.add(remove);
    leftButtons.add(clear);

    JButton apply = new JButton("Apply");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");

    apply.setIcon(SvgIcons.action("check", 16));
    apply.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    ok.setIcon(SvgIcons.action("check", 16));
    ok.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    cancel.setIcon(SvgIcons.action("close", 16));
    cancel.setDisabledIcon(SvgIcons.actionDisabled("close", 16));
    apply.putClientProperty(FlatClientProperties.BUTTON_TYPE, "primary");

    Runnable doApply = () -> {
      Map<String, String> out = model.toMapLowerCaseKeys();
      // Apply live
      if (nickColors != null) {
        nickColors.setOverrides(out);
      }
      // Persist
      if (runtimeConfig != null) {
        runtimeConfig.rememberNickColorOverrides(out);
      }
      // Restyle existing transcripts
      if (transcripts != null) {
        transcripts.restyleAllDocumentsCoalesced();
      }
      // Nudge repaint for other components (e.g., user list)
      for (Window w : Window.getWindows()) {
        try {
          w.repaint();
        } catch (Exception ignored) {
        }
      }
    };

    apply.addActionListener(e -> doApply.run());
    ok.addActionListener(e -> {
      doApply.run();
      dialog.dispose();
    });
    cancel.addActionListener(e -> dialog.dispose());

    JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    rightButtons.add(apply);
    rightButtons.add(ok);
    rightButtons.add(cancel);

    JPanel footer = new JPanel(new BorderLayout());
    footer.add(leftButtons, BorderLayout.WEST);
    footer.add(rightButtons, BorderLayout.EAST);

    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    root.add(help, BorderLayout.NORTH);
    root.add(scroll, BorderLayout.CENTER);
    root.add(footer, BorderLayout.SOUTH);

    dialog = new JDialog(owner, "Nick Color Overrides", JDialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setLayout(new BorderLayout());
    dialog.add(root, BorderLayout.CENTER);
    dialog.pack();
    dialog.setMinimumSize(new Dimension(700, 460));
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private Map<String, String> seedFromService() {
    if (nickColors == null) return Map.of();
    Map<String, String> seed = nickColors.overridesHex();
    return seed != null ? seed : Map.of();
  }

  private static String normalizeNickKey(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return "";
    return n.toLowerCase(Locale.ROOT);
  }

  private static class OverridesTableModel extends AbstractTableModel {

    private static final String[] COLS = new String[]{"Nick", "Color"};

    private final List<Row> rows = new ArrayList<>();

    OverridesTableModel(Map<String, String> seed) {
      if (seed != null) {
        for (Map.Entry<String, String> e : seed.entrySet()) {
          String nick = normalizeNickKey(e.getKey());
          String hex = Objects.toString(e.getValue(), "").trim();
          if (!nick.isEmpty() && !hex.isEmpty()) {
            rows.add(new Row(nick, hex));
          }
        }
      }
      sort();
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
      return COLS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      Row r = rows.get(rowIndex);
      return columnIndex == 0 ? r.nickLower : r.hex;
    }

    NickColorOverrideEntryDialog.Entry getEntry(int rowIndex) {
      Row r = rows.get(rowIndex);
      return new NickColorOverrideEntryDialog.Entry(r.nickLower, r.hex);
    }

    void upsert(String nickLower, String hex) {
      String k = normalizeNickKey(nickLower);
      if (k.isEmpty()) return;
      String v = Objects.toString(hex, "").trim();
      if (v.isEmpty()) return;

      for (int i = 0; i < rows.size(); i++) {
        Row r = rows.get(i);
        if (r.nickLower.equalsIgnoreCase(k)) {
          rows.set(i, new Row(k, v));
          fireTableRowsUpdated(i, i);
          sort();
          return;
        }
      }
      rows.add(new Row(k, v));
      sort();
      fireTableDataChanged();
    }

    void remove(int rowIndex) {
      if (rowIndex < 0 || rowIndex >= rows.size()) return;
      rows.remove(rowIndex);
      fireTableDataChanged();
    }

    void clear() {
      rows.clear();
      fireTableDataChanged();
    }

    Map<String, String> toMapLowerCaseKeys() {
      LinkedHashMap<String, String> out = new LinkedHashMap<>();
      for (Row r : rows) {
        String k = normalizeNickKey(r.nickLower);
        String v = Objects.toString(r.hex, "").trim();
        if (k.isEmpty() || v.isEmpty()) continue;
        out.put(k, v);
      }
      return out;
    }

    private void sort() {
      rows.sort(Comparator.comparing(a -> a.nickLower, String.CASE_INSENSITIVE_ORDER));
    }

    private record Row(String nickLower, String hex) {
    }
  }

  private static class ColorCellRenderer extends DefaultTableCellRenderer {

    @Override
    public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      String hex = Objects.toString(value, "").trim();
      Color c = NickColorOverrideEntryDialog.parseHexColor(hex);

      setText(NickColorOverrideEntryDialog.normalizeHex(hex));
      setIcon(new ColorSwatchIcon(c != null ? c : UIManager.getColor("Label.disabledForeground"), 12, 12));
      setIconTextGap(8);
      return this;
    }
  }
}
