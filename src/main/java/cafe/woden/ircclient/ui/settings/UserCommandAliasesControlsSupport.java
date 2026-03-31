package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.UserCommandAlias;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;

final class UserCommandAliasesControlsSupport {
  private UserCommandAliasesControlsSupport() {}

  static UserCommandAliasesControls buildControls(
      java.util.List<UserCommandAlias> initial,
      boolean unknownCommandAsRawEnabled,
      Component owner) {
    UserCommandAliasesTableModel model = new UserCommandAliasesTableModel(initial);
    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(Math.max(22, table.getRowHeight()));

    TableColumn enabledCol =
        table.getColumnModel().getColumn(UserCommandAliasesTableModel.COL_ENABLED);
    enabledCol.setMaxWidth(80);
    enabledCol.setPreferredWidth(70);

    TableColumn commandCol =
        table.getColumnModel().getColumn(UserCommandAliasesTableModel.COL_COMMAND);
    commandCol.setPreferredWidth(220);

    JTextArea template = new JTextArea(7, 40);
    template.setLineWrap(true);
    template.setWrapStyleWord(true);
    template.setToolTipText(
        "Use %1..%9, %1-, %*, %c, %t, %s, %e, %n, &1..&9. "
            + "Separate commands with ';' or new lines.");
    template.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "/msg %1 Hello %2-");

    JButton add = new JButton("Add");
    JButton importHexChat = new JButton("Import HexChat...");
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");
    PreferencesDialog.configureIconOnlyButton(add, "plus", "Add command alias");
    PreferencesDialog.configureIconOnlyButton(
        importHexChat, "copy", "Import aliases from HexChat commands.conf");
    PreferencesDialog.configureIconOnlyButton(duplicate, "copy", "Duplicate selected alias");
    PreferencesDialog.configureIconOnlyButton(remove, "trash", "Remove selected alias");
    PreferencesDialog.configureIconOnlyButton(up, "arrow-up", "Move selected alias up");
    PreferencesDialog.configureIconOnlyButton(down, "arrow-down", "Move selected alias down");

    JCheckBox unknownCommandAsRaw =
        new JCheckBox("Fallback unknown /commands to raw IRC (HexChat-compatible)");
    unknownCommandAsRaw.setSelected(unknownCommandAsRawEnabled);
    unknownCommandAsRaw.setToolTipText(
        "When enabled, typing an unknown slash command sends it to the server "
            + "as raw IRC (same as /quote), instead of showing a local Unknown command message.");

    JLabel hint = new JLabel("Select an alias row to edit its expansion.");
    hint.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");

    final boolean[] syncing = new boolean[] {false};

    Runnable loadSelectedTemplate =
        () -> {
          int row = table.getSelectedRow();
          syncing[0] = true;
          if (row < 0) {
            template.setText("");
          } else {
            int modelRow = table.convertRowIndexToModel(row);
            template.setText(model.templateAt(modelRow));
          }
          syncing[0] = false;

          boolean selected = row >= 0;
          duplicate.setEnabled(selected);
          remove.setEnabled(selected);
          up.setEnabled(selected && row > 0);
          down.setEnabled(selected && row < table.getRowCount() - 1);
          template.setEnabled(selected);
          hint.setText(
              selected
                  ? "Expansion supports multi-command ';' / newline and placeholders (%1, %2-, %*)."
                  : "Select an alias row to edit its expansion.");
        };

    Runnable persistSelectedTemplate =
        () -> {
          if (syncing[0]) return;
          int row = table.getSelectedRow();
          if (row < 0) return;
          int modelRow = table.convertRowIndexToModel(row);
          model.setTemplateAt(modelRow, template.getText());
        };

    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e != null && e.getValueIsAdjusting()) return;
              loadSelectedTemplate.run();
            });
    template.getDocument().addDocumentListener(new DocChangeListener(persistSelectedTemplate));

    add.addActionListener(
        e -> {
          stopEditing(table);
          int idx = model.addAlias(new UserCommandAlias(true, "", ""));
          if (idx >= 0) {
            int view = table.convertRowIndexToView(idx);
            table.getSelectionModel().setSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
            table.editCellAt(view, UserCommandAliasesTableModel.COL_COMMAND);
            table.requestFocusInWindow();
          }
        });

    importHexChat.addActionListener(
        e -> HexChatAliasImportDialogSupport.importAliases(importHexChat, model, table));

    duplicate.addActionListener(
        e -> {
          stopEditing(table);
          int row = table.getSelectedRow();
          if (row < 0) return;
          int modelRow = table.convertRowIndexToModel(row);
          int dup = model.duplicateRow(modelRow);
          if (dup >= 0) {
            int view = table.convertRowIndexToView(dup);
            table.getSelectionModel().setSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
          }
        });

    remove.addActionListener(
        e -> {
          stopEditing(table);
          int row = table.getSelectedRow();
          if (row < 0) return;
          int res =
              JOptionPane.showConfirmDialog(
                  owner, "Remove selected alias?", "Remove alias", JOptionPane.OK_CANCEL_OPTION);
          if (res != JOptionPane.OK_OPTION) return;
          int modelRow = table.convertRowIndexToModel(row);
          model.removeRow(modelRow);
        });

    up.addActionListener(
        e -> {
          stopEditing(table);
          int row = table.getSelectedRow();
          if (row <= 0) return;
          int modelRow = table.convertRowIndexToModel(row);
          int modelPrevRow = table.convertRowIndexToModel(row - 1);
          int next = model.moveRow(modelRow, modelPrevRow);
          if (next >= 0) {
            int view = table.convertRowIndexToView(next);
            table.getSelectionModel().setSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
          }
        });

    down.addActionListener(
        e -> {
          stopEditing(table);
          int row = table.getSelectedRow();
          if (row < 0 || row >= table.getRowCount() - 1) return;
          int modelRow = table.convertRowIndexToModel(row);
          int modelNextRow = table.convertRowIndexToModel(row + 1);
          int next = model.moveRow(modelRow, modelNextRow);
          if (next >= 0) {
            int view = table.convertRowIndexToView(next);
            table.getSelectionModel().setSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
          }
        });

    loadSelectedTemplate.run();
    return new UserCommandAliasesControls(
        table,
        model,
        template,
        unknownCommandAsRaw,
        add,
        importHexChat,
        duplicate,
        remove,
        up,
        down,
        hint);
  }

  private static void stopEditing(JTable table) {
    if (table == null || !table.isEditing()) return;
    try {
      table.getCellEditor().stopCellEditing();
    } catch (Exception ignored) {
    }
  }

  private static final class DocChangeListener implements DocumentListener {
    private final Runnable onChange;

    private DocChangeListener(Runnable onChange) {
      this.onChange = onChange;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      onChange.run();
    }
  }
}
