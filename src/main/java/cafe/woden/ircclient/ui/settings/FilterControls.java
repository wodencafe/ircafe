package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.FilterDirection;
import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.FilterScopeOverride;
import cafe.woden.ircclient.model.RegexFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

final class FilterControls {
  final JCheckBox filtersEnabledByDefault;
  final JCheckBox placeholdersEnabledByDefault;
  final JCheckBox placeholdersCollapsedByDefault;
  final JSpinner placeholderPreviewLines;
  final JSpinner placeholderMaxLinesPerRun;
  final JSpinner placeholderTooltipMaxTags;
  final JCheckBox historyPlaceholdersEnabledByDefault;
  final JSpinner historyPlaceholderMaxRunsPerBatch;

  final FilterOverridesTableModel overridesModel;
  final JTable overridesTable;
  final JButton addOverride;
  final JButton removeOverride;

  final JTable rulesTable;

  final JButton addRule;
  final JButton editRule;
  final JButton deleteRule;

  final JButton moveRuleUp;
  final JButton moveRuleDown;

  FilterControls(
      JCheckBox filtersEnabledByDefault,
      JCheckBox placeholdersEnabledByDefault,
      JCheckBox placeholdersCollapsedByDefault,
      JSpinner placeholderPreviewLines,
      JSpinner placeholderMaxLinesPerRun,
      JSpinner placeholderTooltipMaxTags,
      JCheckBox historyPlaceholdersEnabledByDefault,
      JSpinner historyPlaceholderMaxRunsPerBatch,
      FilterOverridesTableModel overridesModel,
      JTable overridesTable,
      JButton addOverride,
      JButton removeOverride,
      JTable rulesTable,
      JButton addRule,
      JButton editRule,
      JButton deleteRule,
      JButton moveRuleUp,
      JButton moveRuleDown) {
    this.filtersEnabledByDefault = filtersEnabledByDefault;
    this.placeholdersEnabledByDefault = placeholdersEnabledByDefault;
    this.placeholdersCollapsedByDefault = placeholdersCollapsedByDefault;
    this.placeholderPreviewLines = placeholderPreviewLines;
    this.placeholderMaxLinesPerRun = placeholderMaxLinesPerRun;
    this.placeholderTooltipMaxTags = placeholderTooltipMaxTags;
    this.historyPlaceholdersEnabledByDefault = historyPlaceholdersEnabledByDefault;
    this.historyPlaceholderMaxRunsPerBatch = historyPlaceholderMaxRunsPerBatch;
    this.overridesModel = overridesModel;
    this.overridesTable = overridesTable;
    this.addOverride = addOverride;
    this.removeOverride = removeOverride;
    this.rulesTable = rulesTable;
    this.addRule = addRule;
    this.editRule = editRule;
    this.deleteRule = deleteRule;
    this.moveRuleUp = moveRuleUp;
    this.moveRuleDown = moveRuleDown;
  }
}

enum Tri {
  DEFAULT("Default"),
  ON("On"),
  OFF("Off");

  final String label;

  Tri(String label) {
    this.label = label;
  }

  static Tri fromNullable(Boolean b) {
    if (b == null) return DEFAULT;
    return b ? ON : OFF;
  }

  Boolean toNullable() {
    return switch (this) {
      case DEFAULT -> null;
      case ON -> Boolean.TRUE;
      case OFF -> Boolean.FALSE;
    };
  }

  @Override
  public String toString() {
    return label;
  }
}

final class FilterOverridesRow {
  String scope;
  Tri filters;
  Tri placeholders;
  Tri collapsed;

  FilterOverridesRow(String scope, Tri filters, Tri placeholders, Tri collapsed) {
    this.scope = scope;
    this.filters = filters;
    this.placeholders = placeholders;
    this.collapsed = collapsed;
  }
}

final class FilterOverridesTableModel extends AbstractTableModel {
  private final List<FilterOverridesRow> rows = new ArrayList<>();

  void setOverrides(List<FilterScopeOverride> overrides) {
    rows.clear();
    if (overrides != null) {
      for (FilterScopeOverride o : overrides) {
        rows.add(
            new FilterOverridesRow(
                o.scopePattern(),
                Tri.fromNullable(o.filtersEnabled()),
                Tri.fromNullable(o.placeholdersEnabled()),
                Tri.fromNullable(o.placeholdersCollapsed())));
      }
    }
    fireTableDataChanged();
  }

  List<FilterScopeOverride> toOverrides() {
    List<FilterScopeOverride> out = new ArrayList<>();
    for (FilterOverridesRow r : rows) {
      String s = r.scope != null ? r.scope.trim() : "";
      if (s.isEmpty()) continue;
      out.add(
          new FilterScopeOverride(
              s, r.filters.toNullable(), r.placeholders.toNullable(), r.collapsed.toNullable()));
    }
    return out;
  }

  void addEmpty(String scope) {
    rows.add(new FilterOverridesRow(scope, Tri.DEFAULT, Tri.DEFAULT, Tri.DEFAULT));
    fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
  }

  void removeAt(int idx) {
    if (idx < 0 || idx >= rows.size()) return;
    rows.remove(idx);
    fireTableRowsDeleted(idx, idx);
  }

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
      case 0 -> "Scope";
      case 1 -> "Filters";
      case 2 -> "Placeholders";
      case 3 -> "Collapsed";
      default -> "";
    };
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return switch (columnIndex) {
      case 0 -> String.class;
      default -> Tri.class;
    };
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return true;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    FilterOverridesRow r = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> r.scope;
      case 1 -> r.filters;
      case 2 -> r.placeholders;
      case 3 -> r.collapsed;
      default -> null;
    };
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    FilterOverridesRow r = rows.get(rowIndex);
    switch (columnIndex) {
      case 0 -> r.scope = aValue != null ? String.valueOf(aValue) : "";
      case 1 -> r.filters = (aValue instanceof Tri t) ? t : r.filters;
      case 2 -> r.placeholders = (aValue instanceof Tri t) ? t : r.placeholders;
      case 3 -> r.collapsed = (aValue instanceof Tri t) ? t : r.collapsed;
      default -> {}
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }
}

final class CenteredBooleanRenderer extends JCheckBox implements TableCellRenderer {
  CenteredBooleanRenderer() {
    setHorizontalAlignment(SwingConstants.CENTER);
    setBorderPainted(false);
    setOpaque(true);
    setEnabled(true);
  }

  @Override
  public java.awt.Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    setSelected(Boolean.TRUE.equals(value));
    if (isSelected) {
      setBackground(table.getSelectionBackground());
      setForeground(table.getSelectionForeground());
    } else {
      setBackground(table.getBackground());
      setForeground(table.getForeground());
    }
    return this;
  }
}

final class FilterRulesTableModel extends AbstractTableModel {
  private final List<FilterRule> rules = new ArrayList<>();

  void setRules(List<FilterRule> next) {
    rules.clear();
    if (next != null) rules.addAll(next);
    fireTableDataChanged();
  }

  FilterRule ruleAt(int row) {
    if (row < 0 || row >= rules.size()) return null;
    return rules.get(row);
  }

  @Override
  public int getRowCount() {
    return rules.size();
  }

  @Override
  public int getColumnCount() {
    return 5;
  }

  @Override
  public String getColumnName(int column) {
    return switch (column) {
      case 0 -> "On";
      case 1 -> "Name";
      case 2 -> "Scope";
      case 3 -> "Action";
      case 4 -> "Summary";
      default -> "";
    };
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return switch (columnIndex) {
      case 0 -> Boolean.class;
      default -> String.class;
    };
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex == 0;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    FilterRule r = rules.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> r.enabled();
      case 1 -> r.name();
      case 2 -> r.scopePattern();
      case 3 -> prettyAction(r);
      case 4 -> summaryFor(r);
      default -> null;
    };
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (columnIndex != 0) return;
    if (rowIndex < 0 || rowIndex >= rules.size()) return;
    FilterRule cur = rules.get(rowIndex);
    if (cur == null) return;

    boolean enabled = Boolean.TRUE.equals(aValue);
    if (cur.enabled() == enabled) return;

    FilterRule next =
        new FilterRule(
            cur.id(),
            cur.name(),
            enabled,
            cur.scopePattern(),
            cur.action(),
            cur.direction(),
            cur.kinds(),
            cur.fromNickGlobs(),
            cur.textRegex(),
            cur.tags());
    rules.set(rowIndex, next);
    fireTableCellUpdated(rowIndex, columnIndex);
  }

  private static String prettyAction(FilterRule r) {
    if (r == null || r.action() == null) return "";
    String s = r.action().name().toLowerCase(Locale.ROOT);
    return s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String summaryFor(FilterRule r) {
    if (r == null) return "";
    List<String> parts = new ArrayList<>();

    if (r.hasKinds()) {
      String ks =
          r.kinds().stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("");
      if (!ks.isBlank()) parts.add("kinds=" + ks);
    }

    if (r.direction() != null && r.direction() != FilterDirection.ANY) {
      parts.add("dir=" + r.direction().name());
    }

    if (r.hasFromNickGlobs()) {
      String from = String.join(",", r.fromNickGlobs());
      parts.add("from=" + truncate(from, 48));
    }

    if (r.hasTextRegex()) {
      String pat = r.textRegex().pattern();
      String flags = "";
      if (r.textRegex().flags() != null && !r.textRegex().flags().isEmpty()) {
        StringBuilder sb = new StringBuilder();
        if (r.textRegex().flags().contains(RegexFlag.I)) sb.append('i');
        if (r.textRegex().flags().contains(RegexFlag.M)) sb.append('m');
        if (r.textRegex().flags().contains(RegexFlag.S)) sb.append('s');
        flags = sb.toString();
      }
      String re = "/" + truncate(pat, 48) + "/" + flags;
      parts.add("text=" + re);
    }

    if (r.hasTags()) {
      parts.add("tags=" + truncate(r.tags().expr(), 48));
    }

    if (parts.isEmpty()) return "(matches any)";
    return String.join(" ", parts);
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    String v = s.trim();
    if (v.length() <= max) return v;
    return v.substring(0, Math.max(0, max - 1)) + "…";
  }
}
