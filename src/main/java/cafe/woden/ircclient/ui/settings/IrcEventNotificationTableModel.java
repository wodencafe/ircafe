package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class IrcEventNotificationTableModel extends AbstractTableModel {
  static final int COL_ENABLED = 0;
  static final int COL_EVENT = 1;
  static final int COL_SOURCE_SUMMARY = 2;
  static final int COL_CHANNEL_SUMMARY = 3;
  static final int COL_ACTIONS_SUMMARY = 4;

  private static final String[] COLS =
      new String[] {"Enabled", "Event", "Source", "Channel", "Actions"};

  private final List<MutableRule> rows = new ArrayList<>();

  IrcEventNotificationTableModel(List<IrcEventNotificationRule> initial) {
    if (initial != null) {
      for (IrcEventNotificationRule r : initial) {
        if (r == null) continue;
        rows.add(MutableRule.from(r));
      }
    }
  }

  List<IrcEventNotificationRule> snapshot() {
    return rows.stream().map(MutableRule::toRule).toList();
  }

  IrcEventNotificationRule ruleAt(int row) {
    if (row < 0 || row >= rows.size()) return null;
    MutableRule m = rows.get(row);
    return m != null ? m.toRule() : null;
  }

  void setRule(int row, IrcEventNotificationRule rule) {
    if (row < 0 || row >= rows.size()) return;
    rows.set(row, MutableRule.from(rule));
    fireTableRowsUpdated(row, row);
  }

  void setEnabledAt(int row, boolean enabled) {
    if (row < 0 || row >= rows.size()) return;
    MutableRule current = rows.get(row);
    if (current == null || current.enabled == enabled) return;
    current.enabled = enabled;
    fireTableRowsUpdated(row, row);
  }

  static String effectiveRuleLabel(IrcEventNotificationRule rule) {
    if (rule == null) return "(rule)";
    String event = rule.eventType() != null ? Objects.toString(rule.eventType(), "").trim() : "";
    String source = rule.sourceMode() != null ? Objects.toString(rule.sourceMode(), "").trim() : "";
    if (event.isEmpty()) event = "Event";
    if (source.isEmpty()) return event;
    return event + " (" + source + ")";
  }

  int addRule(IrcEventNotificationRule rule) {
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

  int firstRowForEvent(IrcEventNotificationRule.EventType eventType) {
    if (eventType == null) return -1;
    for (int i = 0; i < rows.size(); i++) {
      MutableRule r = rows.get(i);
      if (r == null) continue;
      if (r.eventType == eventType) return i;
    }
    return -1;
  }

  void applyPreset(List<IrcEventNotificationRule> presetRules) {
    if (presetRules == null || presetRules.isEmpty()) return;
    for (IrcEventNotificationRule rule : presetRules) {
      if (rule == null) continue;
      int idx = firstRowForEvent(rule.eventType());
      if (idx >= 0) {
        rows.set(idx, MutableRule.from(rule));
      } else {
        rows.add(MutableRule.from(rule));
      }
    }
    fireTableDataChanged();
  }

  void replaceAll(List<IrcEventNotificationRule> replacement) {
    rows.clear();
    if (replacement != null) {
      for (IrcEventNotificationRule rule : replacement) {
        if (rule == null) continue;
        rows.add(MutableRule.from(rule));
      }
    }
    fireTableDataChanged();
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
    return columnIndex == COL_ENABLED ? Boolean.class : String.class;
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
      case COL_EVENT -> Objects.toString(r.eventType, "");
      case COL_SOURCE_SUMMARY -> summarizeSource(r);
      case COL_CHANNEL_SUMMARY -> summarizeChannel(r);
      case COL_ACTIONS_SUMMARY -> summarizeActions(r);
      default -> null;
    };
  }

  private static String summarizeSource(MutableRule r) {
    if (r == null) return "";
    IrcEventNotificationRule.SourceMode mode =
        r.sourceMode != null ? r.sourceMode : IrcEventNotificationRule.SourceMode.ANY;
    String label = Objects.toString(mode, "");
    String base;
    if (!sourcePatternAllowed(mode)) {
      base = label;
    } else {
      String pattern = trimToNull(r.sourcePattern);
      base = pattern == null ? label + ": (empty)" : label + ": " + truncate(pattern, 56);
    }

    String ctcp = summarizeCtcp(r);
    if (ctcp.isEmpty()) return base;
    if (base.isEmpty()) return ctcp;
    return base + " | " + ctcp;
  }

  private static String summarizeCtcp(MutableRule r) {
    if (r == null) return "";
    if (r.eventType != IrcEventNotificationRule.EventType.CTCP_RECEIVED) return "";
    IrcEventNotificationRule.CtcpMatchMode commandMode =
        r.ctcpCommandMode != null ? r.ctcpCommandMode : IrcEventNotificationRule.CtcpMatchMode.ANY;
    IrcEventNotificationRule.CtcpMatchMode valueMode =
        r.ctcpValueMode != null ? r.ctcpValueMode : IrcEventNotificationRule.CtcpMatchMode.ANY;
    String commandPattern = trimToNull(r.ctcpCommandPattern);
    String valuePattern = trimToNull(r.ctcpValuePattern);

    String commandSummary =
        commandMode == IrcEventNotificationRule.CtcpMatchMode.ANY
            ? "cmd:any"
            : "cmd:"
                + commandMode
                + "="
                + truncate(Objects.toString(commandPattern, "(empty)"), 24);
    String valueSummary =
        valueMode == IrcEventNotificationRule.CtcpMatchMode.ANY
            ? "val:any"
            : "val:" + valueMode + "=" + truncate(Objects.toString(valuePattern, "(empty)"), 24);
    return commandSummary + ", " + valueSummary;
  }

  private static boolean sourcePatternAllowed(IrcEventNotificationRule.SourceMode mode) {
    return mode == IrcEventNotificationRule.SourceMode.NICK_LIST
        || mode == IrcEventNotificationRule.SourceMode.GLOB
        || mode == IrcEventNotificationRule.SourceMode.REGEX;
  }

  private static boolean channelPatternAllowed(IrcEventNotificationRule.ChannelScope scope) {
    return scope == IrcEventNotificationRule.ChannelScope.ONLY
        || scope == IrcEventNotificationRule.ChannelScope.ALL_EXCEPT;
  }

  private static String summarizeChannel(MutableRule r) {
    if (r == null) return "";
    IrcEventNotificationRule.ChannelScope scope =
        r.channelScope != null ? r.channelScope : IrcEventNotificationRule.ChannelScope.ALL;
    String label = Objects.toString(scope, "");
    if (!channelPatternAllowed(scope)) return label;
    String patterns = trimToNull(r.channelPatterns);
    return patterns == null ? label + ": (empty)" : label + ": " + truncate(patterns, 56);
  }

  private static String summarizeActions(MutableRule r) {
    if (r == null) return "";
    List<String> parts = new ArrayList<>();
    if (r.toastEnabled) {
      IrcEventNotificationRule.FocusScope focus =
          r.focusScope != null ? r.focusScope : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;
      parts.add("Toast(" + focus + ")");
    }
    if (r.statusBarEnabled) parts.add("Status bar");
    if (r.notificationsNodeEnabled) parts.add("Node");
    if (r.soundEnabled) {
      if (r.soundUseCustom && trimToNull(r.soundCustomPath) != null) {
        parts.add("Sound(custom)");
      } else {
        BuiltInSound sound = BuiltInSound.fromId(r.soundId);
        parts.add("Sound(" + sound.displayNameForUi() + ")");
      }
    }
    if (r.scriptEnabled) {
      String script = trimToNull(r.scriptPath);
      if (script == null) {
        parts.add("Script");
      } else {
        int slash = Math.max(script.lastIndexOf('/'), script.lastIndexOf('\\'));
        String leaf =
            (slash >= 0 && slash < (script.length() - 1)) ? script.substring(slash + 1) : script;
        parts.add("Script(" + truncate(leaf, 26) + ")");
      }
    }
    if (parts.isEmpty()) return "(none)";
    return String.join(", ", parts);
  }

  private static String trimToNull(String raw) {
    String value = Objects.toString(raw, "").trim();
    return value.isEmpty() ? null : value;
  }

  private static String truncate(String value, int maxLen) {
    if (value == null) return "";
    String v = value.trim();
    if (v.length() <= maxLen) return v;
    return v.substring(0, Math.max(0, maxLen - 1)) + "…";
  }

  private static final class MutableRule {
    boolean enabled;
    IrcEventNotificationRule.EventType eventType;
    IrcEventNotificationRule.SourceMode sourceMode;
    String sourcePattern;
    IrcEventNotificationRule.ChannelScope channelScope;
    String channelPatterns;
    boolean toastEnabled;
    boolean statusBarEnabled;
    IrcEventNotificationRule.FocusScope focusScope;
    boolean notificationsNodeEnabled;
    boolean soundEnabled;
    String soundId;
    boolean soundUseCustom;
    String soundCustomPath;
    boolean scriptEnabled;
    String scriptPath;
    String scriptArgs;
    String scriptWorkingDirectory;
    IrcEventNotificationRule.CtcpMatchMode ctcpCommandMode;
    String ctcpCommandPattern;
    IrcEventNotificationRule.CtcpMatchMode ctcpValueMode;
    String ctcpValuePattern;

    IrcEventNotificationRule toRule() {
      return new IrcEventNotificationRule(
          enabled,
          eventType,
          sourceMode,
          sourcePattern,
          channelScope,
          channelPatterns,
          toastEnabled,
          focusScope,
          statusBarEnabled,
          notificationsNodeEnabled,
          soundEnabled,
          soundId,
          soundUseCustom,
          soundCustomPath,
          scriptEnabled,
          scriptPath,
          scriptArgs,
          scriptWorkingDirectory,
          ctcpCommandMode,
          ctcpCommandPattern,
          ctcpValueMode,
          ctcpValuePattern);
    }

    MutableRule copy() {
      MutableRule m = new MutableRule();
      m.enabled = enabled;
      m.eventType = eventType;
      m.sourceMode = sourceMode;
      m.sourcePattern = sourcePattern;
      m.channelScope = channelScope;
      m.channelPatterns = channelPatterns;
      m.toastEnabled = toastEnabled;
      m.statusBarEnabled = statusBarEnabled;
      m.focusScope = focusScope;
      m.notificationsNodeEnabled = notificationsNodeEnabled;
      m.soundEnabled = soundEnabled;
      m.soundId = soundId;
      m.soundUseCustom = soundUseCustom;
      m.soundCustomPath = soundCustomPath;
      m.scriptEnabled = scriptEnabled;
      m.scriptPath = scriptPath;
      m.scriptArgs = scriptArgs;
      m.scriptWorkingDirectory = scriptWorkingDirectory;
      m.ctcpCommandMode = ctcpCommandMode;
      m.ctcpCommandPattern = ctcpCommandPattern;
      m.ctcpValueMode = ctcpValueMode;
      m.ctcpValuePattern = ctcpValuePattern;
      return m;
    }

    static MutableRule from(IrcEventNotificationRule r) {
      MutableRule m = new MutableRule();
      if (r == null) {
        m.enabled = false;
        m.eventType = IrcEventNotificationRule.EventType.INVITE_RECEIVED;
        m.sourceMode = IrcEventNotificationRule.SourceMode.ANY;
        m.sourcePattern = null;
        m.channelScope = IrcEventNotificationRule.ChannelScope.ALL;
        m.channelPatterns = null;
        m.toastEnabled = true;
        m.statusBarEnabled = true;
        m.focusScope = IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;
        m.notificationsNodeEnabled = true;
        m.soundEnabled = false;
        m.soundId = PreferencesDialog.defaultBuiltInSoundForIrcEventRule(m.eventType).name();
        m.soundUseCustom = false;
        m.soundCustomPath = null;
        m.scriptEnabled = false;
        m.scriptPath = null;
        m.scriptArgs = null;
        m.scriptWorkingDirectory = null;
        m.ctcpCommandMode = IrcEventNotificationRule.CtcpMatchMode.ANY;
        m.ctcpCommandPattern = null;
        m.ctcpValueMode = IrcEventNotificationRule.CtcpMatchMode.ANY;
        m.ctcpValuePattern = null;
        return m;
      }

      m.enabled = r.enabled();
      m.eventType = r.eventType();
      m.sourceMode = r.sourceMode();
      m.sourcePattern = r.sourcePattern();
      m.channelScope = r.channelScope();
      m.channelPatterns = r.channelPatterns();
      m.toastEnabled = r.toastEnabled();
      m.statusBarEnabled = r.statusBarEnabled();
      m.focusScope = r.focusScope();
      m.notificationsNodeEnabled = r.notificationsNodeEnabled();
      m.soundEnabled = r.soundEnabled();
      m.soundId = BuiltInSound.fromId(r.soundId()).name();
      m.soundUseCustom = r.soundUseCustom();
      m.soundCustomPath = r.soundCustomPath();
      m.scriptEnabled = r.scriptEnabled();
      m.scriptPath = r.scriptPath();
      m.scriptArgs = r.scriptArgs();
      m.scriptWorkingDirectory = r.scriptWorkingDirectory();
      m.ctcpCommandMode = r.ctcpCommandMode();
      m.ctcpCommandPattern = r.ctcpCommandPattern();
      m.ctcpValueMode = r.ctcpValueMode();
      m.ctcpValuePattern = r.ctcpValuePattern();
      return m;
    }
  }
}
