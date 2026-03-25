package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.NotificationRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.table.AbstractTableModel;

final class NotificationRulesTableModel extends AbstractTableModel {
  static final int COL_ENABLED = 0;
  static final int COL_LABEL = 1;
  static final int COL_MATCH = 2;
  static final int COL_OPTIONS = 3;
  static final int COL_COLOR = 4;

  private static final String[] COLS =
      new String[] {
        "Enabled", "Label", "Match", "Options", "Color",
      };

  private final List<MutableRule> rows = new ArrayList<>();

  NotificationRulesTableModel(List<NotificationRule> initial) {
    if (initial != null) {
      for (NotificationRule r : initial) {
        if (r == null) continue;
        rows.add(MutableRule.from(r));
      }
    }
  }

  List<NotificationRule> snapshot() {
    return rows.stream().map(MutableRule::toRule).toList();
  }

  NotificationRule ruleAt(int row) {
    if (row < 0 || row >= rows.size()) return null;
    MutableRule m = rows.get(row);
    return m != null ? m.toRule() : null;
  }

  void setRule(int row, NotificationRule rule) {
    if (row < 0 || row >= rows.size()) return;
    rows.set(row, MutableRule.from(rule));
    fireTableRowsUpdated(row, row);
  }

  static String effectiveRuleLabel(NotificationRule rule) {
    if (rule == null) return "(unnamed)";
    String label = Objects.toString(rule.label(), "").trim();
    if (!label.isEmpty()) return label;
    String pattern = Objects.toString(rule.pattern(), "").trim();
    return pattern.isEmpty() ? "(unnamed)" : pattern;
  }

  String highlightFgAt(int row) {
    if (row < 0 || row >= rows.size()) return null;
    MutableRule r = rows.get(row);
    return r != null ? r.highlightFg : null;
  }

  void setHighlightFg(int row, String hex) {
    if (row < 0 || row >= rows.size()) return;
    MutableRule r = rows.get(row);
    if (r == null) return;
    r.highlightFg = normalizeHexColor(Objects.toString(hex, "").trim());
    fireTableRowsUpdated(row, row);
  }

  List<ValidationError> validationErrors() {
    List<ValidationError> out = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      MutableRule r = rows.get(i);
      if (r == null) continue;
      if (!r.enabled) continue;
      if (r.type != NotificationRule.Type.REGEX) continue;

      String pat = r.pattern != null ? r.pattern.trim() : "";
      if (pat.isEmpty()) continue;

      try {
        int flags = Pattern.UNICODE_CASE;
        if (!r.caseSensitive) flags |= Pattern.CASE_INSENSITIVE;
        Pattern.compile(pat, flags);
      } catch (Exception ex) {
        out.add(new ValidationError(i, r.label, pat, ex.getMessage()));
      }
    }
    return out;
  }

  ValidationError firstValidationError() {
    List<ValidationError> errs = validationErrors();
    return errs.isEmpty() ? null : errs.get(0);
  }

  int addRule(NotificationRule rule) {
    rows.add(MutableRule.from(rule));
    int idx = rows.size() - 1;
    fireTableRowsInserted(idx, idx);
    return idx;
  }

  int duplicateRow(int row) {
    if (row < 0 || row >= rows.size()) return -1;
    MutableRule src = rows.get(row);
    MutableRule copy = src.copy();
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
    MutableRule r = rows.remove(from);
    rows.add(to, r);
    fireTableDataChanged();
    return to;
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
    return false;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= rows.size()) return null;
    MutableRule r = rows.get(rowIndex);
    return switch (columnIndex) {
      case COL_ENABLED -> r.enabled;
      case COL_LABEL -> effectiveRuleLabel(r.toRule());
      case COL_MATCH -> summarizeMatch(r);
      case COL_OPTIONS -> summarizeOptions(r);
      case COL_COLOR -> Objects.toString(r.highlightFg, "");
      default -> null;
    };
  }

  private static String summarizeMatch(MutableRule r) {
    if (r == null) return "";
    String pattern = Objects.toString(r.pattern, "").trim();
    if (pattern.isEmpty()) pattern = "(empty)";
    String type = r.type == NotificationRule.Type.REGEX ? "REGEX" : "WORD";
    return type + ": " + pattern;
  }

  private static String summarizeOptions(MutableRule r) {
    if (r == null) return "";
    String caseLabel = r.caseSensitive ? "Case" : "No case";
    if (r.type == NotificationRule.Type.WORD) {
      return caseLabel + ", " + (r.wholeWord ? "Whole word" : "Substring");
    }
    return caseLabel;
  }

  private static String normalizeHexColor(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;

    if (s.startsWith("#")) s = s.substring(1).trim();
    if (s.length() == 3) {
      char r = s.charAt(0);
      char g = s.charAt(1);
      char b = s.charAt(2);
      s = "" + r + r + g + g + b + b;
    } else if (s.length() != 6) {
      return null;
    }

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!ok) return null;
    }

    return "#" + s.toUpperCase(Locale.ROOT);
  }

  private static final class MutableRule {
    boolean enabled;
    NotificationRule.Type type;
    String label;
    String pattern;
    boolean caseSensitive;
    boolean wholeWord;
    String highlightFg;

    NotificationRule toRule() {
      boolean ww = (type == NotificationRule.Type.WORD) && wholeWord;
      return new NotificationRule(label, type, pattern, enabled, caseSensitive, ww, highlightFg);
    }

    MutableRule copy() {
      MutableRule m = new MutableRule();
      m.enabled = enabled;
      m.type = type;
      m.label = label;
      m.pattern = pattern;
      m.caseSensitive = caseSensitive;
      m.wholeWord = wholeWord;
      m.highlightFg = highlightFg;
      return m;
    }

    static MutableRule from(NotificationRule r) {
      MutableRule m = new MutableRule();
      if (r == null) {
        m.enabled = false;
        m.type = NotificationRule.Type.WORD;
        m.label = "";
        m.pattern = "";
        m.caseSensitive = false;
        m.wholeWord = true;
        m.highlightFg = null;
        return m;
      }

      m.enabled = r.enabled();
      m.type = r.type();
      m.label = Objects.toString(r.label(), "");
      m.pattern = Objects.toString(r.pattern(), "");
      m.caseSensitive = r.caseSensitive();
      m.wholeWord = r.wholeWord();
      m.highlightFg = r.highlightFg();
      return m;
    }
  }
}
