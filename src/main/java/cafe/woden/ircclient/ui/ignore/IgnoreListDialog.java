package cafe.woden.ircclient.ui.ignore;

import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
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

  private DefaultListModel<String> ignoreModel;
  private DefaultListModel<String> softModel;

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
    hardCtcpToggle.addActionListener(e -> {
      if (ignores == null) return;
      ignores.setHardIgnoreIncludesCtcp(hardCtcpToggle.isSelected());
    });

    JCheckBox softCtcpToggle = new JCheckBox("Soft ignore includes CTCP");
    softCtcpToggle.setSelected(ignores != null && ignores.softIgnoreIncludesCtcp());
    softCtcpToggle.setToolTipText("When enabled, CTCP messages from soft-ignored users are fully dropped (not shown as spoilers).\n" +
        "This applies to CTCP requests/replies and /me actions.");
    softCtcpToggle.addActionListener(e -> {
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
    dialog.setContentPane(root);
    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private enum Kind {
    IGNORE,
    SOFT_IGNORE
  }

  private JPanel buildMaskPanel(String serverId, Kind kind, DefaultListModel<String> model) {
    JList<String> list = new JList<>(model);
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(540, 300));

    JButton add = new JButton("Add...");
    JButton remove = new JButton("Remove");
    JButton copy = new JButton("Copy");

    add.setIcon(SvgIcons.action("plus", 16));
    add.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    remove.setIcon(SvgIcons.action("trash", 16));
    remove.setDisabledIcon(SvgIcons.actionDisabled("trash", 16));
    copy.setIcon(SvgIcons.action("copy", 16));
    copy.setDisabledIcon(SvgIcons.actionDisabled("copy", 16));

    remove.setEnabled(false);
    copy.setEnabled(false);

    list.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      boolean hasSel = list.getSelectedIndices().length > 0;
      remove.setEnabled(hasSel);
      copy.setEnabled(list.getSelectedIndices().length == 1);
    });

    add.addActionListener(e -> {
      String title = kind == Kind.SOFT_IGNORE ? "Add Soft Ignore" : "Add Ignore";
      String prompt = kind == Kind.SOFT_IGNORE
          ? "Enter a hostmask / pattern to soft-ignore (stored per-server):"
          : "Enter a hostmask / pattern to ignore (stored per-server):";

      String input = (String) JOptionPane.showInputDialog(
          dialog,
          prompt,
          title,
          JOptionPane.PLAIN_MESSAGE,
          null,
          null,
          ""
      );
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
            dialog,
            "Already in list: " + stored,
            title,
            JOptionPane.INFORMATION_MESSAGE
        );
      }

      refresh(model, serverId, kind);
    });

    remove.addActionListener(e -> {
      List<String> sel = list.getSelectedValuesList();
      if (sel == null || sel.isEmpty()) return;

      String title = kind == Kind.SOFT_IGNORE ? "Remove Soft Ignores" : "Remove Ignores";
      int ok = JOptionPane.showConfirmDialog(
          dialog,
          "Remove selected mask(s)?",
          title,
          JOptionPane.OK_CANCEL_OPTION,
          JOptionPane.WARNING_MESSAGE
      );
      if (ok != JOptionPane.OK_OPTION) return;

      for (String m : sel) {
        if (m == null || m.isBlank()) continue;
        if (kind == Kind.SOFT_IGNORE) {
          ignores.removeSoftMask(serverId, m);
        } else {
          ignores.removeMask(serverId, m);
        }
      }
      refresh(model, serverId, kind);
    });

    copy.addActionListener(e -> {
      String m = list.getSelectedValue();
      if (m == null || m.isBlank()) return;
      try {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(m), null);
      } catch (Exception ignored) {
      }
    });

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
    left.add(add);
    left.add(remove);
    left.add(copy);

    JPanel footer = new JPanel(new BorderLayout());
    footer.add(left, BorderLayout.WEST);

    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    root.add(scroll, BorderLayout.CENTER);
    root.add(footer, BorderLayout.SOUTH);
    return root;
  }

  private void refresh(DefaultListModel<String> model, String serverId, Kind kind) {
    model.clear();
    if (ignores == null) return;
    List<String> masks = (kind == Kind.SOFT_IGNORE) ? ignores.listSoftMasks(serverId) : ignores.listMasks(serverId);
    for (String m : masks) {
      if (m == null || m.isBlank()) continue;
      model.addElement(m);
    }
  }

  private void refreshIgnore(DefaultListModel<String> model, String serverId) {
    refresh(model, serverId, Kind.IGNORE);
  }

  private void refreshSoft(DefaultListModel<String> model, String serverId) {
    refresh(model, serverId, Kind.SOFT_IGNORE);
  }
}
