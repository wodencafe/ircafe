package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.ui.filter.FilterSettings;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;

final class FilterOverrideControlsSupport {
  private FilterOverrideControlsSupport() {}

  static FilterOverrideControls buildControls(FilterSettings current, java.awt.Window owner) {
    FilterOverridesTableModel model = new FilterOverridesTableModel();
    model.setOverrides(current.overrides());

    JTable table = new JTable(model);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    JComboBox<Tri> triCombo = new JComboBox<>(Tri.values());
    TableColumn c1 = table.getColumnModel().getColumn(1);
    TableColumn c2 = table.getColumnModel().getColumn(2);
    TableColumn c3 = table.getColumnModel().getColumn(3);
    c1.setCellEditor(new DefaultCellEditor(triCombo));
    c2.setCellEditor(new DefaultCellEditor(new JComboBox<>(Tri.values())));
    c3.setCellEditor(new DefaultCellEditor(new JComboBox<>(Tri.values())));

    JButton add = new JButton("Add override...");
    JButton remove = new JButton("Remove");
    PreferencesDialog.configureIconOnlyButton(add, "plus", "Add scope override");
    PreferencesDialog.configureIconOnlyButton(remove, "trash", "Remove selected scope override");
    remove.setEnabled(false);

    table
        .getSelectionModel()
        .addListSelectionListener(e -> remove.setEnabled(table.getSelectedRow() >= 0));

    add.addActionListener(
        e -> {
          String scope =
              JOptionPane.showInputDialog(
                  owner,
                  "Scope pattern (e.g. libera/#llamas, libera/*, */status)",
                  "Add Override",
                  JOptionPane.PLAIN_MESSAGE);
          if (scope == null) return;
          scope = scope.trim();
          if (scope.isEmpty()) return;
          model.addEmpty(scope);
          int idx = model.getRowCount() - 1;
          if (idx >= 0) {
            table.getSelectionModel().setSelectionInterval(idx, idx);
            table.scrollRectToVisible(table.getCellRect(idx, 0, true));
          }
        });

    remove.addActionListener(
        e -> {
          int row = table.getSelectedRow();
          if (row < 0) return;
          int confirm =
              JOptionPane.showConfirmDialog(
                  owner,
                  "Remove selected override?",
                  "Remove Override",
                  JOptionPane.OK_CANCEL_OPTION);
          if (confirm != JOptionPane.OK_OPTION) return;
          model.removeAt(row);
          SwingUtilities.invokeLater(() -> remove.setEnabled(table.getSelectedRow() >= 0));
        });

    return new FilterOverrideControls(model, table, add, remove);
  }
}

final class FilterOverrideControls {
  final FilterOverridesTableModel model;
  final JTable table;
  final JButton add;
  final JButton remove;

  FilterOverrideControls(
      FilterOverridesTableModel model, JTable table, JButton add, JButton remove) {
    this.model = model;
    this.table = table;
    this.add = add;
    this.remove = remove;
  }
}
