package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.chat.embed.EmbedLoadPolicyMatcher;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import net.miginfocom.swing.MigLayout;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Modal editor for advanced embed/link loading policy settings. */
@Component
@Lazy
public class EmbedLoadPolicyDialog {

  private final RuntimeConfigStore runtimeConfig;

  public EmbedLoadPolicyDialog(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  public Optional<RuntimeConfigStore.EmbedLoadPolicySnapshot> open(
      Window owner, RuntimeConfigStore.EmbedLoadPolicySnapshot seed) {
    if (!SwingUtilities.isEventDispatchThread()) {
      final RuntimeConfigStore.EmbedLoadPolicySnapshot[] out =
          {RuntimeConfigStore.EmbedLoadPolicySnapshot.defaults()};
      final boolean[] changed = {false};
      try {
        SwingUtilities.invokeAndWait(
            () -> {
              Optional<RuntimeConfigStore.EmbedLoadPolicySnapshot> result = open(owner, seed);
              changed[0] = result.isPresent();
              out[0] = result.orElse(RuntimeConfigStore.EmbedLoadPolicySnapshot.defaults());
            });
      } catch (Exception ignored) {
      }
      return changed[0] ? Optional.of(out[0]) : Optional.empty();
    }

    RuntimeConfigStore.EmbedLoadPolicySnapshot initial =
        seed == null ? RuntimeConfigStore.EmbedLoadPolicySnapshot.defaults() : seed;

    final RuntimeConfigStore.EmbedLoadPolicyScope[] globalRef = {initial.global()};
    final LinkedHashMap<String, RuntimeConfigStore.EmbedLoadPolicyScope> byServerRef =
        new LinkedHashMap<>(initial.byServer());

    List<ScopeOption> options = buildScopeOptions(initial);
    JComboBox<ScopeOption> scope = new JComboBox<>(options.toArray(new ScopeOption[0]));
    scope.setSelectedIndex(0);

    JCheckBox inheritGlobal = new JCheckBox("Use global policy for this network");

    PolicyControls controls = buildPolicyControls();

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab(
        "Users",
        buildDualTablePanel(
            "Whitelist", "Blacklist", controls.userWhitelist(), controls.userBlacklist()));
    tabs.addTab(
        "Channels",
        buildDualTablePanel(
            "Whitelist", "Blacklist", controls.channelWhitelist(), controls.channelBlacklist()));
    tabs.addTab(
        "Links",
        buildDualTablePanel(
            "Whitelist", "Blacklist", controls.linkWhitelist(), controls.linkBlacklist()));
    tabs.addTab(
        "Domains",
        buildDualTablePanel(
            "Whitelist", "Blacklist", controls.domainWhitelist(), controls.domainBlacklist()));
    tabs.addTab("Gates", buildGatePanel(controls));

    JPanel scopePanel = new JPanel(new MigLayout("insets 10, fillx, wrap 2", "[][grow,fill]", "[]4[]4[]"));
    scopePanel.add(new JLabel("Scope:"));
    scopePanel.add(scope, "growx, wrap");
    scopePanel.add(inheritGlobal, "span 2, wrap");
    scopePanel.add(
        new JLabel("Patterns are glob by default (`*`/`?`). Use `re:<regex>` for regex patterns."),
        "span 2, wrap");
    scopePanel.add(
        new JLabel(
            "User rules support `nick:` and `host:` prefixes. Link/domain rules match URL/domain text."),
        "span 2, wrap");

    JButton save = new JButton("Save");
    JButton cancel = new JButton("Cancel");
    JPanel buttons =
        new JPanel(new MigLayout("insets 0, fillx", "[grow,fill][pref!][pref!]", "[]"));
    buttons.add(new JPanel(), "growx, pushx");
    buttons.add(save);
    buttons.add(cancel);

    JPanel root =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[][grow,fill][]"));
    root.add(scopePanel, "growx");
    root.add(tabs, "grow");
    root.add(buttons, "growx");

    final RuntimeConfigStore.EmbedLoadPolicySnapshot[] result = {null};
    Runnable refreshValidation = () -> save.setEnabled(validateAllPatternTables(controls));
    installValidationListeners(controls, refreshValidation);

    Runnable applySelection =
        () -> {
          ScopeOption selected = (ScopeOption) scope.getSelectedItem();
          if (selected == null) return;
          stopTableEditing(controls);
          RuntimeConfigStore.EmbedLoadPolicyScope currentScope =
              readScopeFromControls(controls);
          if (selected.global()) {
            globalRef[0] = currentScope;
          } else {
            if (inheritGlobal.isSelected()) {
              byServerRef.remove(selected.serverId());
            } else {
              byServerRef.put(selected.serverId(), currentScope);
            }
          }
        };

    Runnable loadSelection =
        () -> {
          ScopeOption selected = (ScopeOption) scope.getSelectedItem();
          if (selected == null) return;
          RuntimeConfigStore.EmbedLoadPolicyScope show;
          boolean editable = true;
          if (selected.global()) {
            inheritGlobal.setVisible(false);
            show = globalRef[0];
          } else {
            inheritGlobal.setVisible(true);
            RuntimeConfigStore.EmbedLoadPolicyScope override = byServerRef.get(selected.serverId());
            boolean usesGlobal = override == null;
            inheritGlobal.setSelected(usesGlobal);
            show = usesGlobal ? globalRef[0] : override;
            editable = !usesGlobal;
          }

          writeScopeToControls(show, controls);
          setEditable(editable || selected.global(), controls);
          refreshValidation.run();
        };

    scope.addActionListener(
        e -> {
          applySelection.run();
          loadSelection.run();
        });

    inheritGlobal.addActionListener(
        e -> {
          ScopeOption selected = (ScopeOption) scope.getSelectedItem();
          if (selected == null || selected.global()) return;
          if (inheritGlobal.isSelected()) {
            byServerRef.remove(selected.serverId());
          } else if (!byServerRef.containsKey(selected.serverId())) {
            byServerRef.put(selected.serverId(), RuntimeConfigStore.EmbedLoadPolicyScope.defaults());
          }
          loadSelection.run();
        });

    JDialog dialog = new JDialog(owner, "Advanced Embed/Link Loading Policy", Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setContentPane(root);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setMinimumSize(new Dimension(920, 680));

    save.addActionListener(
        e -> {
          stopTableEditing(controls);
          if (!validateAllPatternTables(controls)) {
            JOptionPane.showMessageDialog(
                dialog,
                "One or more patterns are invalid.\nUse valid glob patterns or `re:<regex>` values.",
                "Invalid Pattern",
                JOptionPane.WARNING_MESSAGE);
            save.setEnabled(false);
            return;
          }
          applySelection.run();
          result[0] = new RuntimeConfigStore.EmbedLoadPolicySnapshot(globalRef[0], byServerRef);
          dialog.dispose();
        });
    cancel.addActionListener(e -> dialog.dispose());

    loadSelection.run();
    refreshValidation.run();
    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);

    return Optional.ofNullable(result[0]);
  }

  private List<ScopeOption> buildScopeOptions(RuntimeConfigStore.EmbedLoadPolicySnapshot initial) {
    LinkedHashMap<String, ScopeOption> out = new LinkedHashMap<>();
    out.put("", new ScopeOption("", "Global (all networks)", true));

    List<String> configured = runtimeConfig != null ? runtimeConfig.readServerIds() : List.of();
    for (String serverId : configured) {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) continue;
      out.putIfAbsent(
          sid.toLowerCase(java.util.Locale.ROOT), new ScopeOption(sid, "Network: " + sid, false));
    }
    if (initial != null && initial.byServer() != null) {
      for (String serverId : initial.byServer().keySet()) {
        String sid = Objects.toString(serverId, "").trim();
        if (sid.isEmpty()) continue;
        out.putIfAbsent(
            sid.toLowerCase(java.util.Locale.ROOT), new ScopeOption(sid, "Network: " + sid, false));
      }
    }
    return new ArrayList<>(out.values());
  }

  private PolicyControls buildPolicyControls() {
    PatternTableControls userWhitelist =
        buildPatternTable(
            "User whitelist",
            "Allow only these users when list is non-empty. Use `nick:` or `host:` prefixes.");
    PatternTableControls userBlacklist =
        buildPatternTable("User blacklist", "Deny users matching these nick/host patterns.");
    PatternTableControls channelWhitelist =
        buildPatternTable(
            "Channel whitelist", "Allow only these channels when list is non-empty.");
    PatternTableControls channelBlacklist =
        buildPatternTable("Channel blacklist", "Deny these channels.");
    PatternTableControls linkWhitelist =
        buildPatternTable("Link whitelist", "Allow only these URLs when list is non-empty.");
    PatternTableControls linkBlacklist =
        buildPatternTable("Link blacklist", "Deny these URLs.");
    PatternTableControls domainWhitelist =
        buildPatternTable("Domain whitelist", "Allow only these domains when list is non-empty.");
    PatternTableControls domainBlacklist =
        buildPatternTable("Domain blacklist", "Deny these domains.");

    JCheckBox requireVoiceOrOp = new JCheckBox("Only users with voice/op status");
    JCheckBox requireLoggedIn = new JCheckBox("Only users logged into an account");
    JSpinner minAccountAgeDays = new JSpinner(new SpinnerNumberModel(0, 0, 36500, 1));

    return new PolicyControls(
        userWhitelist,
        userBlacklist,
        channelWhitelist,
        channelBlacklist,
        linkWhitelist,
        linkBlacklist,
        domainWhitelist,
        domainBlacklist,
        requireVoiceOrOp,
        requireLoggedIn,
        minAccountAgeDays);
  }

  private static JPanel buildDualTablePanel(
      String leftTitle,
      String rightTitle,
      PatternTableControls left,
      PatternTableControls right) {
    JPanel panel =
        new JPanel(new MigLayout("insets 10, fill, wrap 2", "[grow,fill][grow,fill]", "[grow,fill]"));
    panel.add(buildLabeledPanel(leftTitle, left.panel()), "grow");
    panel.add(buildLabeledPanel(rightTitle, right.panel()), "grow");
    return panel;
  }

  private static JPanel buildLabeledPanel(String title, JPanel content) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[][grow,fill]"));
    panel.add(new JLabel(title), "growx");
    panel.add(content, "grow");
    return panel;
  }

  private static JPanel buildGatePanel(PolicyControls controls) {
    JPanel panel = new JPanel(new MigLayout("insets 10, fillx, wrap 2", "[][grow,fill]", "[]6[]6[]6[]"));
    panel.add(controls.requireVoiceOrOp(), "span 2, wrap");
    panel.add(controls.requireLoggedIn(), "span 2, wrap");
    panel.add(new JLabel("Minimum account age (days, 0 = disabled):"));
    panel.add(controls.minAccountAgeDays(), "w 120!, wrap");
    panel.add(
        new JLabel("If account age metadata is unavailable for a sender, this check fails closed."),
        "span 2, wrap");
    return panel;
  }

  private static PatternTableControls buildPatternTable(String title, String hint) {
    DefaultTableModel model =
        new DefaultTableModel(new Object[] {"Pattern"}, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return true;
          }
        };
    JTable table = new JTable(model);
    Set<Integer> invalidRows = new LinkedHashSet<>();
    Color errorBg = resolveValidationErrorBackground();
    Color errorFg = resolveValidationErrorForeground();
    table.setDefaultRenderer(
        Object.class,
        new DefaultTableCellRenderer() {
          @Override
          public java.awt.Component getTableCellRendererComponent(
              JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            java.awt.Component c =
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
              if (invalidRows.contains(row)) {
                c.setBackground(errorBg);
                c.setForeground(errorFg);
              } else {
                c.setBackground(table.getBackground());
                c.setForeground(table.getForeground());
              }
            }
            return c;
          }
        });
    table.setFillsViewportHeight(true);
    table.setRowHeight(24);
    table.getTableHeader().setReorderingAllowed(false);

    JButton add = new JButton("Add");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");

    add.addActionListener(
        e -> {
          model.addRow(new Object[] {""});
          int row = model.getRowCount() - 1;
          if (row >= 0) {
            table.getSelectionModel().setSelectionInterval(row, row);
            table.editCellAt(row, 0);
            if (table.getEditorComponent() != null) {
              table.getEditorComponent().requestFocus();
            }
          }
        });
    remove.addActionListener(
        e -> {
          if (table.isEditing() && table.getCellEditor() != null) {
            table.getCellEditor().stopCellEditing();
          }
          int[] rows = table.getSelectedRows();
          for (int i = rows.length - 1; i >= 0; i--) {
            model.removeRow(rows[i]);
          }
        });
    up.addActionListener(
        e -> {
          if (table.isEditing() && table.getCellEditor() != null) {
            table.getCellEditor().stopCellEditing();
          }
          int row = table.getSelectedRow();
          if (row <= 0) return;
          Object value = model.getValueAt(row, 0);
          model.removeRow(row);
          model.insertRow(row - 1, new Object[] {value});
          table.getSelectionModel().setSelectionInterval(row - 1, row - 1);
        });
    down.addActionListener(
        e -> {
          if (table.isEditing() && table.getCellEditor() != null) {
            table.getCellEditor().stopCellEditing();
          }
          int row = table.getSelectedRow();
          if (row < 0 || row >= model.getRowCount() - 1) return;
          Object value = model.getValueAt(row, 0);
          model.removeRow(row);
          model.insertRow(row + 1, new Object[] {value});
          table.getSelectionModel().setSelectionInterval(row + 1, row + 1);
        });

    JPanel actions =
        new JPanel(new MigLayout("insets 0, wrap 1", "[grow,fill]", "[]4[]4[]4[]"));
    actions.add(add, "growx");
    actions.add(remove, "growx");
    actions.add(up, "growx");
    actions.add(down, "growx");

    JLabel validation = new JLabel(" ");
    validation.setForeground(errorFg);

    JPanel panel =
        new JPanel(
            new MigLayout("insets 0, fill, wrap 2", "[grow,fill][pref!]", "[][][grow,fill][]"));
    panel.add(new JLabel(title), "span 2, growx, wrap");
    panel.add(new JLabel(hint), "span 2, growx, wrap");
    panel.add(new JScrollPane(table), "grow");
    panel.add(actions, "top");
    panel.add(validation, "span 2, growx, wrap");

    return new PatternTableControls(model, table, add, remove, up, down, panel, validation, invalidRows);
  }

  private static RuntimeConfigStore.EmbedLoadPolicyScope readScopeFromControls(
      PolicyControls controls) {
    stopTableEditing(controls);
    return new RuntimeConfigStore.EmbedLoadPolicyScope(
        readPatternRows(controls.userWhitelist().model()),
        readPatternRows(controls.userBlacklist().model()),
        readPatternRows(controls.channelWhitelist().model()),
        readPatternRows(controls.channelBlacklist().model()),
        controls.requireVoiceOrOp().isSelected(),
        controls.requireLoggedIn().isSelected(),
        ((Number) controls.minAccountAgeDays().getValue()).intValue(),
        readPatternRows(controls.linkWhitelist().model()),
        readPatternRows(controls.linkBlacklist().model()),
        readPatternRows(controls.domainWhitelist().model()),
        readPatternRows(controls.domainBlacklist().model()));
  }

  private static void writeScopeToControls(
      RuntimeConfigStore.EmbedLoadPolicyScope scope, PolicyControls controls) {
    RuntimeConfigStore.EmbedLoadPolicyScope s =
        scope == null ? RuntimeConfigStore.EmbedLoadPolicyScope.defaults() : scope;
    writePatternRows(controls.userWhitelist().model(), s.userWhitelist());
    writePatternRows(controls.userBlacklist().model(), s.userBlacklist());
    writePatternRows(controls.channelWhitelist().model(), s.channelWhitelist());
    writePatternRows(controls.channelBlacklist().model(), s.channelBlacklist());
    controls.requireVoiceOrOp().setSelected(s.requireVoiceOrOp());
    controls.requireLoggedIn().setSelected(s.requireLoggedIn());
    controls.minAccountAgeDays().setValue(Math.max(0, s.minAccountAgeDays()));
    writePatternRows(controls.linkWhitelist().model(), s.linkWhitelist());
    writePatternRows(controls.linkBlacklist().model(), s.linkBlacklist());
    writePatternRows(controls.domainWhitelist().model(), s.domainWhitelist());
    writePatternRows(controls.domainBlacklist().model(), s.domainBlacklist());
  }

  private static void setEditable(boolean enabled, PolicyControls controls) {
    setPatternTableEditable(controls.userWhitelist(), enabled);
    setPatternTableEditable(controls.userBlacklist(), enabled);
    setPatternTableEditable(controls.channelWhitelist(), enabled);
    setPatternTableEditable(controls.channelBlacklist(), enabled);
    setPatternTableEditable(controls.linkWhitelist(), enabled);
    setPatternTableEditable(controls.linkBlacklist(), enabled);
    setPatternTableEditable(controls.domainWhitelist(), enabled);
    setPatternTableEditable(controls.domainBlacklist(), enabled);
    controls.requireVoiceOrOp().setEnabled(enabled);
    controls.requireLoggedIn().setEnabled(enabled);
    controls.minAccountAgeDays().setEnabled(enabled);
  }

  private static void setPatternTableEditable(PatternTableControls controls, boolean enabled) {
    controls.table().setEnabled(enabled);
    controls.add().setEnabled(enabled);
    controls.remove().setEnabled(enabled);
    controls.up().setEnabled(enabled);
    controls.down().setEnabled(enabled);
  }

  private static void installValidationListeners(PolicyControls controls, Runnable onChanged) {
    installPatternTableValidationListener(controls.userWhitelist(), onChanged);
    installPatternTableValidationListener(controls.userBlacklist(), onChanged);
    installPatternTableValidationListener(controls.channelWhitelist(), onChanged);
    installPatternTableValidationListener(controls.channelBlacklist(), onChanged);
    installPatternTableValidationListener(controls.linkWhitelist(), onChanged);
    installPatternTableValidationListener(controls.linkBlacklist(), onChanged);
    installPatternTableValidationListener(controls.domainWhitelist(), onChanged);
    installPatternTableValidationListener(controls.domainBlacklist(), onChanged);
  }

  private static void installPatternTableValidationListener(
      PatternTableControls controls, Runnable onChanged) {
    if (controls == null || controls.model() == null || onChanged == null) return;
    controls.model().addTableModelListener(e -> onChanged.run());
  }

  private static boolean validateAllPatternTables(PolicyControls controls) {
    boolean valid = true;
    valid &= validatePatternTable(controls.userWhitelist());
    valid &= validatePatternTable(controls.userBlacklist());
    valid &= validatePatternTable(controls.channelWhitelist());
    valid &= validatePatternTable(controls.channelBlacklist());
    valid &= validatePatternTable(controls.linkWhitelist());
    valid &= validatePatternTable(controls.linkBlacklist());
    valid &= validatePatternTable(controls.domainWhitelist());
    valid &= validatePatternTable(controls.domainBlacklist());
    return valid;
  }

  private static boolean validatePatternTable(PatternTableControls controls) {
    if (controls == null) return true;
    PatternValidation validation = validatePatternRows(controls.model());
    controls.invalidRows().clear();
    controls.invalidRows().addAll(validation.invalidRows());
    controls.table().repaint();
    if (validation.isValid()) {
      controls.validation().setText(" ");
      return true;
    }
    controls.validation().setText(validation.message());
    return false;
  }

  private static PatternValidation validatePatternRows(DefaultTableModel model) {
    if (model == null || model.getRowCount() == 0) {
      return PatternValidation.clean();
    }
    List<Integer> invalidRows = new ArrayList<>();
    String message = "";
    for (int row = 0; row < model.getRowCount(); row++) {
      String value = Objects.toString(model.getValueAt(row, 0), "").trim();
      if (value.isEmpty()) continue;
      Optional<String> error = EmbedLoadPolicyMatcher.validatePatternSyntax(value);
      if (error.isEmpty()) continue;
      invalidRows.add(row);
      if (message.isBlank()) {
        message = "Row " + (row + 1) + ": " + error.get();
      }
    }
    if (invalidRows.isEmpty()) {
      return PatternValidation.clean();
    }
    return new PatternValidation(List.copyOf(invalidRows), message);
  }

  private static Color resolveValidationErrorBackground() {
    Color c = UIManager.getColor("Component.error.background");
    if (c != null) return c;
    c = UIManager.getColor("TextField.error.background");
    if (c != null) return c;
    return new Color(255, 236, 236);
  }

  private static Color resolveValidationErrorForeground() {
    Color c = UIManager.getColor("Component.error.foreground");
    if (c != null) return c;
    c = UIManager.getColor("Component.error.focusedBorderColor");
    if (c != null) return c;
    return new Color(150, 25, 25);
  }

  private static void stopTableEditing(PolicyControls controls) {
    stopEditing(controls.userWhitelist().table());
    stopEditing(controls.userBlacklist().table());
    stopEditing(controls.channelWhitelist().table());
    stopEditing(controls.channelBlacklist().table());
    stopEditing(controls.linkWhitelist().table());
    stopEditing(controls.linkBlacklist().table());
    stopEditing(controls.domainWhitelist().table());
    stopEditing(controls.domainBlacklist().table());
  }

  private static void stopEditing(JTable table) {
    if (table == null) return;
    if (table.isEditing() && table.getCellEditor() != null) {
      table.getCellEditor().stopCellEditing();
    }
  }

  private static List<String> readPatternRows(DefaultTableModel model) {
    if (model == null || model.getRowCount() == 0) return List.of();
    LinkedHashMap<String, String> seen = new LinkedHashMap<>();
    for (int row = 0; row < model.getRowCount(); row++) {
      String v = Objects.toString(model.getValueAt(row, 0), "").trim();
      if (v.isEmpty()) continue;
      seen.putIfAbsent(v, v);
    }
    return seen.isEmpty() ? List.of() : List.copyOf(seen.values());
  }

  private static void writePatternRows(DefaultTableModel model, List<String> rows) {
    if (model == null) return;
    model.setRowCount(0);
    if (rows == null || rows.isEmpty()) return;
    LinkedHashMap<String, String> seen = new LinkedHashMap<>();
    for (String row : rows) {
      String v = Objects.toString(row, "").trim();
      if (v.isEmpty()) continue;
      seen.putIfAbsent(v, v);
    }
    for (String value : seen.values()) {
      model.addRow(new Object[] {value});
    }
  }

  private record PolicyControls(
      PatternTableControls userWhitelist,
      PatternTableControls userBlacklist,
      PatternTableControls channelWhitelist,
      PatternTableControls channelBlacklist,
      PatternTableControls linkWhitelist,
      PatternTableControls linkBlacklist,
      PatternTableControls domainWhitelist,
      PatternTableControls domainBlacklist,
      JCheckBox requireVoiceOrOp,
      JCheckBox requireLoggedIn,
      JSpinner minAccountAgeDays) {}

  private record PatternTableControls(
      DefaultTableModel model,
      JTable table,
      JButton add,
      JButton remove,
      JButton up,
      JButton down,
      JPanel panel,
      JLabel validation,
      Set<Integer> invalidRows) {}

  private record PatternValidation(List<Integer> invalidRows, String message) {
    private static PatternValidation clean() {
      return new PatternValidation(List.of(), "");
    }

    private boolean isValid() {
      return invalidRows == null || invalidRows.isEmpty();
    }
  }

  private record ScopeOption(String serverId, String label, boolean global) {
    @Override
    public String toString() {
      return label;
    }
  }
}
