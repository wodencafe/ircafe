package cafe.woden.ircclient.ui.ignore;

import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.api.IgnoreAddMaskResult;
import cafe.woden.ircclient.ignore.api.IgnoreLevels;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class IgnoreListDialog {

  public enum Tab {
    IGNORE,
    SOFT_IGNORE
  }

  private final IgnoreListService ignores;

  private JDialog dialog;
  private String currentServerId;
  private JTabbedPane tabs;
  private boolean hardIgnoreAdvancedMode;

  private DefaultListModel<MaskRow> ignoreModel;
  private DefaultListModel<MaskRow> softModel;

  public IgnoreListDialog(IgnoreListService ignores) {
    this.ignores = ignores;
  }

  public void open(Window owner, String serverId) {
    open(owner, serverId, Tab.IGNORE);
  }

  public void open(Window owner, String serverId, Tab initialTab) {
    final String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> open(owner, sid, initialTab));
      return;
    }

    // If already open for the same server, just focus it and switch tab.
    if (dialog != null && dialog.isShowing() && Objects.equals(currentServerId, sid)) {
      if (tabs != null) {
        tabs.setSelectedIndex(initialTab == Tab.SOFT_IGNORE ? 1 : 0);
      }
      dialog.toFront();
      dialog.requestFocus();
      return;
    }

    // If open for a different server, rebuild.
    if (dialog != null) {
      try {
        dialog.dispose();
      } catch (Exception ignored) {
      }
      dialog = null;
    }
    currentServerId = sid;
    hardIgnoreAdvancedMode = false;

    ignoreModel = new DefaultListModel<>();
    softModel = new DefaultListModel<>();
    refreshIgnore(ignoreModel, sid);
    refreshSoft(softModel, sid);

    JLabel help = new JLabel("Manage ignore and soft-ignore masks for this server only.");
    help.putClientProperty(FlatClientProperties.STYLE, "font: -1");

    tabs = new JTabbedPane();
    tabs.addTab("Ignore", buildMaskPanel(sid, Kind.IGNORE, ignoreModel));
    tabs.addTab("Soft ignore", buildMaskPanel(sid, Kind.SOFT_IGNORE, softModel));
    tabs.setSelectedIndex(initialTab == Tab.SOFT_IGNORE ? 1 : 0);

    JButton close = new JButton("Close");
    close.setIcon(SvgIcons.action("close", 16));
    close.setDisabledIcon(SvgIcons.actionDisabled("close", 16));
    close.addActionListener(e -> dialog.dispose());

    JCheckBox hardCtcpToggle = new JCheckBox("Hard ignore includes CTCP");
    hardCtcpToggle.setSelected(ignores != null && ignores.hardIgnoreIncludesCtcp());
    hardCtcpToggle.setToolTipText(
        "When enabled, CTCP messages (e.g., VERSION/PING/ACTION) from hard-ignored users are also dropped.");
    hardCtcpToggle.addActionListener(
        e -> {
          if (ignores == null) return;
          ignores.setHardIgnoreIncludesCtcp(hardCtcpToggle.isSelected());
        });

    JCheckBox softCtcpToggle = new JCheckBox("Soft ignore includes CTCP");
    softCtcpToggle.setSelected(ignores != null && ignores.softIgnoreIncludesCtcp());
    softCtcpToggle.setToolTipText(
        "When enabled, CTCP messages from soft-ignored users are fully dropped (not shown as spoilers).\n"
            + "This applies to CTCP requests/replies and /me actions.");
    softCtcpToggle.addActionListener(
        e -> {
          if (ignores == null) return;
          ignores.setSoftIgnoreIncludesCtcp(softCtcpToggle.isSelected());
        });

    JPanel toggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    toggles.setOpaque(false);
    toggles.add(hardCtcpToggle);
    toggles.add(softCtcpToggle);

    JPanel footer = new JPanel(new BorderLayout());
    footer.add(toggles, BorderLayout.WEST);
    footer.add(close, BorderLayout.EAST);

    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    root.add(help, BorderLayout.NORTH);
    root.add(tabs, BorderLayout.CENTER);
    root.add(footer, BorderLayout.SOUTH);

    dialog = new JDialog(owner, "Ignore Lists - " + sid);
    dialog.setModal(false);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog
        .getRootPane()
        .registerKeyboardAction(
            e -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    dialog.setContentPane(root);
    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private enum Kind {
    IGNORE,
    SOFT_IGNORE
  }

  private enum HardIgnoreEditorMode {
    ADD,
    EDIT
  }

  private JPanel buildMaskPanel(String serverId, Kind kind, DefaultListModel<MaskRow> model) {
    JList<MaskRow> list = new JList<>(model);
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(540, 300));

    JButton add = new JButton("Add...");
    JButton edit = new JButton("Edit rule...");
    JButton remove = new JButton("Remove");
    JButton copy = new JButton("Copy");

    add.setIcon(SvgIcons.action("plus", 16));
    add.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    edit.setIcon(SvgIcons.action("edit", 16));
    edit.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
    remove.setIcon(SvgIcons.action("trash", 16));
    remove.setDisabledIcon(SvgIcons.actionDisabled("trash", 16));
    copy.setIcon(SvgIcons.action("copy", 16));
    copy.setDisabledIcon(SvgIcons.actionDisabled("copy", 16));

    boolean allowEdit = kind == Kind.IGNORE;
    JCheckBox advancedModeToggle = null;
    JLabel modeHint = null;
    if (allowEdit) {
      advancedModeToggle = new JCheckBox("Advanced (irssi) mode");
      advancedModeToggle.setSelected(hardIgnoreAdvancedMode);
      advancedModeToggle.setToolTipText(
          "Simple mode: mask-only add.\nAdvanced mode: add/edit levels, channels, expiry, pattern, and replies.");
      modeHint = new JLabel();
      modeHint.putClientProperty(FlatClientProperties.STYLE, "font: -1");
      updateHardIgnoreModeHint(modeHint, hardIgnoreAdvancedMode);
    }
    edit.setEnabled(false);
    remove.setEnabled(false);
    copy.setEnabled(false);

    list.addListSelectionListener(
        e -> {
          if (e.getValueIsAdjusting()) return;
          boolean hasSel = list.getSelectedIndices().length > 0;
          if (allowEdit) {
            edit.setEnabled(list.getSelectedIndices().length == 1);
          }
          remove.setEnabled(hasSel);
          copy.setEnabled(list.getSelectedIndices().length == 1);
        });

    add.addActionListener(
        e -> {
          if (allowEdit && hardIgnoreAdvancedMode) {
            boolean changed = openHardIgnoreRuleEditor(serverId, "", HardIgnoreEditorMode.ADD);
            if (changed) {
              refresh(model, serverId, kind);
            }
            return;
          }
          String title = kind == Kind.SOFT_IGNORE ? "Add Soft Ignore" : "Add Ignore";
          String prompt =
              kind == Kind.SOFT_IGNORE
                  ? "Enter a hostmask / pattern to soft-ignore (stored per-server):"
                  : "Enter a hostmask / pattern to ignore (stored per-server):";

          String input =
              (String)
                  JOptionPane.showInputDialog(
                      dialog, prompt, title, JOptionPane.PLAIN_MESSAGE, null, null, "");
          if (input == null) return;
          String trimmed = input.trim();
          if (trimmed.isEmpty()) return;

          boolean added;
          if (kind == Kind.SOFT_IGNORE) {
            added = ignores.addSoftMask(serverId, trimmed);
          } else {
            added = ignores.addMask(serverId, trimmed);
          }

          String stored = IgnoreListService.normalizeMaskOrNickToHostmask(trimmed);
          if (!added) {
            JOptionPane.showMessageDialog(
                dialog, "Already in list: " + stored, title, JOptionPane.INFORMATION_MESSAGE);
          }

          refresh(model, serverId, kind);
        });

    edit.addActionListener(
        e -> {
          if (!allowEdit) return;
          MaskRow row = list.getSelectedValue();
          if (row == null || row.mask().isBlank()) return;
          boolean changed =
              openHardIgnoreRuleEditor(serverId, row.mask(), HardIgnoreEditorMode.EDIT);
          if (changed) {
            refresh(model, serverId, kind);
          }
        });

    if (advancedModeToggle != null) {
      final JCheckBox advancedToggle = advancedModeToggle;
      final JLabel hint = modeHint;
      advancedModeToggle.addActionListener(
          e -> {
            hardIgnoreAdvancedMode = advancedToggle.isSelected();
            updateHardIgnoreModeHint(hint, hardIgnoreAdvancedMode);
          });
    }

    remove.addActionListener(
        e -> {
          List<MaskRow> sel = list.getSelectedValuesList();
          if (sel == null || sel.isEmpty()) return;

          String title = kind == Kind.SOFT_IGNORE ? "Remove Soft Ignores" : "Remove Ignores";
          int ok =
              JOptionPane.showConfirmDialog(
                  dialog,
                  "Remove selected mask(s)?",
                  title,
                  JOptionPane.OK_CANCEL_OPTION,
                  JOptionPane.WARNING_MESSAGE);
          if (ok != JOptionPane.OK_OPTION) return;

          for (MaskRow row : sel) {
            if (row == null || row.mask().isBlank()) continue;
            if (kind == Kind.SOFT_IGNORE) {
              ignores.removeSoftMask(serverId, row.mask());
            } else {
              ignores.removeMask(serverId, row.mask());
            }
          }
          refresh(model, serverId, kind);
        });

    copy.addActionListener(
        e -> {
          MaskRow row = list.getSelectedValue();
          if (row == null || row.mask().isBlank()) return;
          try {
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(row.mask()), null);
          } catch (Exception ignored) {
          }
        });

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
    left.add(add);
    if (allowEdit) {
      left.add(edit);
    }
    left.add(remove);
    left.add(copy);

    JPanel footer = new JPanel(new BorderLayout());
    footer.add(left, BorderLayout.WEST);
    if (advancedModeToggle != null && modeHint != null) {
      JPanel right = new JPanel();
      right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
      right.add(advancedModeToggle);
      right.add(modeHint);
      footer.add(right, BorderLayout.EAST);
    }

    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    root.add(scroll, BorderLayout.CENTER);
    root.add(footer, BorderLayout.SOUTH);
    return root;
  }

  private void refresh(DefaultListModel<MaskRow> model, String serverId, Kind kind) {
    model.clear();
    if (ignores == null) return;
    List<String> masks =
        (kind == Kind.SOFT_IGNORE) ? ignores.listSoftMasks(serverId) : ignores.listMasks(serverId);
    for (String m : masks) {
      if (m == null || m.isBlank()) continue;
      if (kind == Kind.SOFT_IGNORE) {
        model.addElement(MaskRow.forSoftMask(m));
      } else {
        model.addElement(
            MaskRow.forHardMask(
                m,
                formatHardMaskDisplay(
                    m,
                    ignores.levelsForHardMask(serverId, m),
                    ignores.channelsForHardMask(serverId, m),
                    ignores.expiresAtEpochMsForHardMask(serverId, m),
                    ignores.patternForHardMask(serverId, m),
                    ignores.patternModeForHardMask(serverId, m),
                    ignores.repliesForHardMask(serverId, m))));
      }
    }
  }

  private void refreshIgnore(DefaultListModel<MaskRow> model, String serverId) {
    refresh(model, serverId, Kind.IGNORE);
  }

  private void refreshSoft(DefaultListModel<MaskRow> model, String serverId) {
    refresh(model, serverId, Kind.SOFT_IGNORE);
  }

  private boolean openHardIgnoreRuleEditor(
      String serverId, String mask, HardIgnoreEditorMode editorMode) {
    String sid = Objects.toString(serverId, "").trim();
    String m = Objects.toString(mask, "").trim();
    if (sid.isEmpty() || ignores == null) return false;
    if (editorMode == HardIgnoreEditorMode.EDIT && m.isEmpty()) return false;

    JTextField maskField = new JTextField(m);
    JTextField levelsField =
        new JTextField(renderLevelsForEditor(ignores.levelsForHardMask(sid, m)));
    JTextField channelsField =
        new JTextField(String.join(",", ignores.channelsForHardMask(sid, m)));
    JTextField expiresField =
        new JTextField(renderExpiryForEditor(ignores.expiresAtEpochMsForHardMask(sid, m)));
    JTextField patternField =
        new JTextField(Objects.toString(ignores.patternForHardMask(sid, m), ""));
    JComboBox<IgnoreTextPatternMode> patternModeBox =
        new JComboBox<>(IgnoreTextPatternMode.values());
    patternModeBox.setSelectedItem(ignores.patternModeForHardMask(sid, m));
    JCheckBox repliesBox = new JCheckBox("Ignore reply-targeted channel messages");
    repliesBox.setSelected(ignores.repliesForHardMask(sid, m));

    JPanel form = new JPanel();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.add(fieldRow("Mask", maskField));
    form.add(fieldRow("Levels", levelsField));
    form.add(fieldRow("Channels", channelsField));
    form.add(fieldRow("Expires at", expiresField));
    form.add(fieldRow("Pattern", patternField));
    form.add(fieldRow("Pattern mode", patternModeBox));
    form.add(fieldRow("", repliesBox));

    String message =
        "<html>"
            + (editorMode == HardIgnoreEditorMode.ADD
                ? "Add a hard-ignore rule.<br>"
                : "Edit hard-ignore metadata.<br>")
            + "Levels: comma/space separated (blank means ALL).<br>"
            + "Channels: comma/space separated #channel patterns.<br>"
            + "Expires at: ISO-8601 instant (e.g. 2026-03-01T12:34:56Z) or epoch millis.</html>";

    while (true) {
      int result =
          JOptionPane.showConfirmDialog(
              dialog,
              new Object[] {message, form},
              editorMode == HardIgnoreEditorMode.ADD ? "Add Ignore Rule" : "Edit Ignore Rule",
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE);
      if (result != JOptionPane.OK_OPTION) return false;

      ParseResult<String> normalizedMask = parseMaskInput(maskField.getText());
      if (normalizedMask.error() != null) {
        showValidationError(normalizedMask.error());
        continue;
      }

      ParseResult<List<String>> levels = parseLevelsInput(levelsField.getText());
      if (levels.error() != null) {
        showValidationError(levels.error());
        continue;
      }

      ParseResult<List<String>> channels = parseChannelsInput(channelsField.getText());
      if (channels.error() != null) {
        showValidationError(channels.error());
        continue;
      }

      ParseResult<Long> expiry = parseExpiryInputEpochMs(expiresField.getText());
      if (expiry.error() != null) {
        showValidationError(expiry.error());
        continue;
      }

      String pattern = Objects.toString(patternField.getText(), "").trim();
      IgnoreTextPatternMode patternMode =
          (IgnoreTextPatternMode)
              Objects.requireNonNullElse(
                  patternModeBox.getSelectedItem(), IgnoreTextPatternMode.GLOB);
      if (!pattern.isEmpty() && patternMode == IgnoreTextPatternMode.REGEXP) {
        if (!isValidRegexPattern(pattern)) {
          showValidationError("Pattern mode is regexp, but pattern is invalid.");
          continue;
        }
      }

      IgnoreAddMaskResult addResult =
          ignores.addMaskWithLevels(
              sid,
              normalizedMask.value(),
              levels.value(),
              channels.value(),
              expiry.value(),
              pattern,
              patternMode,
              repliesBox.isSelected());
      if (addResult == IgnoreAddMaskResult.UNCHANGED) {
        JOptionPane.showMessageDialog(
            dialog,
            "No changes detected for this ignore rule.",
            editorMode == HardIgnoreEditorMode.ADD ? "Add Ignore Rule" : "Edit Ignore Rule",
            JOptionPane.INFORMATION_MESSAGE);
        return false;
      }
      return true;
    }
  }

  private static void updateHardIgnoreModeHint(JLabel hint, boolean advancedMode) {
    if (hint == null) return;
    hint.setText(hardIgnoreModeHintText(advancedMode));
  }

  static String hardIgnoreModeHintText(boolean advancedMode) {
    if (advancedMode) {
      return "Advanced mode: Add/Edit uses irssi-style rule fields.";
    }
    return "Simple mode: Add is mask-only (legacy).";
  }

  private static JPanel fieldRow(String label, java.awt.Component input) {
    JPanel row = new JPanel(new BorderLayout(8, 0));
    if (!Objects.toString(label, "").isBlank()) {
      JLabel lbl = new JLabel(label + ":");
      lbl.setPreferredSize(new Dimension(110, lbl.getPreferredSize().height));
      row.add(lbl, BorderLayout.WEST);
    } else {
      JLabel spacer = new JLabel();
      spacer.setPreferredSize(new Dimension(110, 1));
      row.add(spacer, BorderLayout.WEST);
    }
    row.add(input, BorderLayout.CENTER);
    row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    return row;
  }

  private void showValidationError(String text) {
    JOptionPane.showMessageDialog(dialog, text, "Invalid Ignore Rule", JOptionPane.WARNING_MESSAGE);
  }

  static ParseResult<List<String>> parseLevelsInput(String raw) {
    String input = Objects.toString(raw, "").trim();
    if (input.isEmpty()) return ParseResult.ok(List.of("ALL"));

    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String token : input.split("[,\\s]+")) {
      String normalized = normalizeLevelToken(token);
      if (normalized.isEmpty()) {
        return ParseResult.error("Unknown ignore level: \"" + token + "\"");
      }
      out.add(normalized);
    }
    if (out.isEmpty()) return ParseResult.ok(List.of("ALL"));
    return ParseResult.ok(List.copyOf(out));
  }

  static ParseResult<List<String>> parseChannelsInput(String raw) {
    String input = Objects.toString(raw, "").trim();
    if (input.isEmpty()) return ParseResult.ok(List.of());

    ArrayList<String> out = new ArrayList<>();
    for (String token : input.split("[,\\s]+")) {
      String channel = Objects.toString(token, "").trim();
      if (channel.isEmpty()) continue;
      if (!(channel.startsWith("#") || channel.startsWith("&"))) {
        return ParseResult.error("Channel patterns must start with # or &: \"" + channel + "\"");
      }
      if (out.stream().noneMatch(existing -> existing.equalsIgnoreCase(channel))) {
        out.add(channel);
      }
    }
    return ParseResult.ok(List.copyOf(out));
  }

  static ParseResult<Long> parseExpiryInputEpochMs(String raw) {
    String input = Objects.toString(raw, "").trim();
    if (input.isEmpty()) return ParseResult.ok(null);

    if (input.chars().allMatch(Character::isDigit)) {
      try {
        long epochMs = Long.parseLong(input);
        if (epochMs <= 0L) {
          return ParseResult.error("Expiry must be a positive epoch-millis value.");
        }
        return ParseResult.ok(epochMs);
      } catch (Exception ex) {
        return ParseResult.error("Invalid epoch-millis expiry value.");
      }
    }

    try {
      long epochMs = Instant.parse(input).toEpochMilli();
      if (epochMs <= 0L) {
        return ParseResult.error("Expiry must be after the Unix epoch.");
      }
      return ParseResult.ok(epochMs);
    } catch (Exception ex) {
      return ParseResult.error("Invalid expiry format. Use ISO-8601 instant or epoch millis.");
    }
  }

  static ParseResult<String> parseMaskInput(String raw) {
    String normalized = IgnoreListService.normalizeMaskOrNickToHostmask(raw);
    if (normalized.isBlank()) {
      return ParseResult.error("Mask is required.");
    }
    return ParseResult.ok(normalized);
  }

  private static String normalizeLevelToken(String raw) {
    String token = Objects.toString(raw, "").trim().toUpperCase(Locale.ROOT);
    if (token.isEmpty()) return "";
    while (token.startsWith("+") || token.startsWith("-")) {
      token = token.substring(1).trim();
    }
    if (token.isEmpty()) return "";
    if ("*".equals(token)) token = "ALL";
    return IgnoreLevels.KNOWN.contains(token) ? token : "";
  }

  private static boolean isValidRegexPattern(String pattern) {
    try {
      Pattern.compile(pattern);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static String renderLevelsForEditor(List<String> levels) {
    List<String> normalized = IgnoreLevels.normalizeConfigured(levels);
    if (normalized.size() == 1 && "ALL".equalsIgnoreCase(normalized.getFirst())) {
      return "";
    }
    return String.join(",", normalized);
  }

  private static String renderExpiryForEditor(long expiresAtEpochMs) {
    if (expiresAtEpochMs <= 0L) return "";
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(expiresAtEpochMs));
  }

  static String formatHardMaskDisplay(
      String mask,
      List<String> levels,
      List<String> channels,
      long expiresAtEpochMs,
      String pattern,
      IgnoreTextPatternMode patternMode,
      boolean replies) {
    String m = Objects.toString(mask, "").trim();
    if (m.isEmpty()) return "";

    List<String> metadata = new ArrayList<>();
    List<String> normalizedLevels = IgnoreLevels.normalizeConfigured(levels);
    if (!(normalizedLevels.size() == 1 && "ALL".equalsIgnoreCase(normalizedLevels.getFirst()))) {
      metadata.add("levels=" + String.join(",", normalizedLevels));
    }
    if (channels != null && !channels.isEmpty()) {
      metadata.add("channels=" + String.join(",", channels));
    }
    if (expiresAtEpochMs > 0L) {
      metadata.add(
          "expires="
              + DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(expiresAtEpochMs)));
    }

    String normalizedPattern = Objects.toString(pattern, "").trim();
    if (!normalizedPattern.isEmpty()) {
      metadata.add("pattern=" + renderPattern(normalizedPattern, patternMode));
    }
    if (replies) {
      metadata.add("replies");
    }

    if (metadata.isEmpty()) return m;
    return m + " [" + String.join("; ", metadata) + "]";
  }

  private static String renderPattern(String pattern, IgnoreTextPatternMode mode) {
    String p = Objects.toString(pattern, "").trim();
    if (p.isEmpty()) return "";
    IgnoreTextPatternMode m = (mode == null) ? IgnoreTextPatternMode.GLOB : mode;
    return switch (m) {
      case REGEXP -> "/" + p + "/ (regexp)";
      case FULL -> p + " (full)";
      case GLOB -> p;
    };
  }

  private record MaskRow(String mask, String display) {
    static MaskRow forHardMask(String mask, String display) {
      String m = Objects.toString(mask, "").trim();
      String d = Objects.toString(display, "").trim();
      if (d.isEmpty()) d = m;
      return new MaskRow(m, d);
    }

    static MaskRow forSoftMask(String mask) {
      String m = Objects.toString(mask, "").trim();
      return new MaskRow(m, m);
    }

    @Override
    public String toString() {
      return display;
    }
  }

  record ParseResult<T>(T value, String error) {
    static <T> ParseResult<T> ok(T value) {
      return new ParseResult<>(value, null);
    }

    static <T> ParseResult<T> error(String error) {
      return new ParseResult<>(null, Objects.toString(error, "Invalid value."));
    }
  }
}
