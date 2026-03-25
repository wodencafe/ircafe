package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.UserCommandAlias;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.table.AbstractTableModel;

final class UserCommandAliasesTableModel extends AbstractTableModel {
  static final int COL_ENABLED = 0;
  static final int COL_COMMAND = 1;

  private static final String[] COLS = new String[] {"Enabled", "Command"};
  private static final Pattern COMMAND_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");

  private final List<MutableAlias> rows = new ArrayList<>();

  UserCommandAliasesTableModel(List<UserCommandAlias> initial) {
    if (initial != null) {
      for (UserCommandAlias alias : initial) {
        if (alias == null) continue;
        rows.add(MutableAlias.from(alias));
      }
    }
  }

  List<UserCommandAlias> snapshot() {
    return rows.stream().map(MutableAlias::toAlias).toList();
  }

  int addAlias(UserCommandAlias alias) {
    rows.add(MutableAlias.from(alias));
    int idx = rows.size() - 1;
    fireTableRowsInserted(idx, idx);
    return idx;
  }

  int duplicateRow(int row) {
    if (row < 0 || row >= rows.size()) return -1;
    MutableAlias src = rows.get(row);
    MutableAlias copy = src.copy();
    int idx = Math.min(rows.size(), row + 1);
    rows.add(idx, copy);
    fireTableRowsInserted(idx, idx);
    return idx;
  }

  void removeRow(int row) {
    if (row < 0 || row >= rows.size()) return;
    rows.remove(row);
    fireTableRowsDeleted(row, row);
  }

  int moveRow(int from, int to) {
    if (from < 0 || from >= rows.size()) return -1;
    if (to < 0 || to >= rows.size()) return -1;
    if (from == to) return from;
    MutableAlias alias = rows.remove(from);
    rows.add(to, alias);
    fireTableDataChanged();
    return to;
  }

  String templateAt(int row) {
    if (row < 0 || row >= rows.size()) return "";
    return Objects.toString(rows.get(row).template, "");
  }

  void setTemplateAt(int row, String template) {
    if (row < 0 || row >= rows.size()) return;
    rows.get(row).template = Objects.toString(template, "");
    fireTableRowsUpdated(row, row);
  }

  UserCommandAliasValidationError firstValidationError() {
    Map<String, Integer> seenEnabled = new LinkedHashMap<>();

    for (int i = 0; i < rows.size(); i++) {
      MutableAlias a = rows.get(i);
      if (a == null || !a.enabled) continue;

      String cmd = normalizeCommand(a.name);
      if (cmd.isEmpty()) {
        return new UserCommandAliasValidationError(
            i, a.name, "Enabled aliases require a command name.");
      }
      if (!COMMAND_NAME_PATTERN.matcher(cmd).matches()) {
        return new UserCommandAliasValidationError(
            i,
            a.name,
            "Command names must start with a letter and contain only letters, numbers, '_' or '-'.");
      }
      if (Objects.toString(a.template, "").isBlank()) {
        return new UserCommandAliasValidationError(i, cmd, "Enabled aliases require an expansion.");
      }

      String key = cmd.toLowerCase(Locale.ROOT);
      Integer prev = seenEnabled.putIfAbsent(key, i);
      if (prev != null) {
        return new UserCommandAliasValidationError(
            i, cmd, "Duplicate enabled alias: /" + cmd + " (also used on row " + (prev + 1) + ").");
      }
    }

    return null;
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
    if (column < 0 || column >= COLS.length) return "";
    return COLS[column];
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    if (columnIndex == COL_ENABLED) return Boolean.class;
    return String.class;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return rowIndex >= 0 && rowIndex < rows.size() && columnIndex >= 0 && columnIndex < COLS.length;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= rows.size()) return null;
    MutableAlias a = rows.get(rowIndex);
    return switch (columnIndex) {
      case COL_ENABLED -> a.enabled;
      case COL_COMMAND -> a.name;
      default -> null;
    };
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= rows.size()) return;
    MutableAlias a = rows.get(rowIndex);

    switch (columnIndex) {
      case COL_ENABLED -> a.enabled = aValue instanceof Boolean b && b;
      case COL_COMMAND -> a.name = normalizeCommand(Objects.toString(aValue, ""));
      default -> {}
    }

    fireTableRowsUpdated(rowIndex, rowIndex);
  }

  private static String normalizeCommand(String raw) {
    String cmd = Objects.toString(raw, "").trim();
    if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
    return cmd;
  }

  private static final class MutableAlias {
    boolean enabled;
    String name;
    String template;

    UserCommandAlias toAlias() {
      return new UserCommandAlias(enabled, normalizeCommand(name), Objects.toString(template, ""));
    }

    MutableAlias copy() {
      MutableAlias c = new MutableAlias();
      c.enabled = enabled;
      c.name = name;
      c.template = template;
      return c;
    }

    static MutableAlias from(UserCommandAlias alias) {
      MutableAlias m = new MutableAlias();
      if (alias == null) {
        m.enabled = true;
        m.name = "";
        m.template = "";
        return m;
      }
      m.enabled = alias.enabled();
      m.name = normalizeCommand(alias.name());
      m.template = Objects.toString(alias.template(), "");
      return m;
    }
  }
}
