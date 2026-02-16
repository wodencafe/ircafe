package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ThemeSelectionDialog {

  private final ThemeManager themeManager;
  private final UiSettingsBus settingsBus;
  private final RuntimeConfigStore runtimeConfig;

  private JDialog dialog;
  private JList<ThemeManager.ThemeOption> themeList;
  private String committedThemeId;
  private String previewThemeId;

  public ThemeSelectionDialog(ThemeManager themeManager, UiSettingsBus settingsBus, RuntimeConfigStore runtimeConfig) {
    this.themeManager = themeManager;
    this.settingsBus = settingsBus;
    this.runtimeConfig = runtimeConfig;
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

    UiSettings cur = settingsBus.get();
    committedThemeId = normalizeThemeId(cur != null ? cur.theme() : null);
    previewThemeId = committedThemeId;

    DefaultListModel<ThemeManager.ThemeOption> model = new DefaultListModel<>();
    ThemeManager.ThemeOption[] opts = themeManager.supportedThemes();
    for (ThemeManager.ThemeOption opt : opts) {
      model.addElement(opt);
    }

    themeList = new JList<>(model);
    themeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    themeList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
      JLabel l = (JLabel) new DefaultListCellRenderer()
          .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value != null) {
        l.setText(value.label());
      }
      return l;
    });

    selectThemeInList(previewThemeId);
    themeList.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      queuePreviewTheme(selectedThemeId());
    });

    JScrollPane listScroll = new JScrollPane(themeList);
    listScroll.setPreferredSize(new Dimension(250, 280));

    JPanel listPanel = new JPanel(new BorderLayout(8, 8));
    listPanel.add(new JLabel("Themes"), BorderLayout.NORTH);
    listPanel.add(listScroll, BorderLayout.CENTER);

    JPanel previewPanel = buildPreviewPanel();

    JButton apply = new JButton("Apply");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");
    apply.putClientProperty(FlatClientProperties.BUTTON_TYPE, "primary");

    apply.addActionListener(e -> commitSelectedTheme());
    ok.addActionListener(e -> {
      commitSelectedTheme();
      closeDialog();
    });
    cancel.addActionListener(e -> {
      restoreCommittedTheme();
      closeDialog();
    });

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(apply);
    buttons.add(ok);
    buttons.add(cancel);

    JLabel help = new JLabel("Select a theme to preview it live. Apply/OK saves it; Cancel restores the last saved theme.");
    help.putClientProperty(FlatClientProperties.STYLE, "font: -1");

    JPanel center = new JPanel(new BorderLayout(12, 0));
    center.add(listPanel, BorderLayout.WEST);
    center.add(previewPanel, BorderLayout.CENTER);

    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    root.add(help, BorderLayout.NORTH);
    root.add(center, BorderLayout.CENTER);
    root.add(buttons, BorderLayout.SOUTH);

    dialog = new JDialog(owner, "Theme Selector", JDialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        restoreCommittedTheme();
      }
    });
    dialog.setContentPane(root);
    dialog.pack();
    dialog.setMinimumSize(new Dimension(760, 430));
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private JPanel buildPreviewPanel() {
    JPanel preview = new JPanel(new BorderLayout(10, 10));
    preview.setBorder(BorderFactory.createTitledBorder("Live Preview"));

    JPanel top = new JPanel(new BorderLayout(8, 8));
    JLabel header = new JLabel("IRCafe Preview");
    header.putClientProperty(FlatClientProperties.STYLE, "font: +2 bold");
    JLabel status = new JLabel("Connected to irc.example.net #cafe");
    status.setHorizontalAlignment(SwingConstants.RIGHT);
    top.add(header, BorderLayout.WEST);
    top.add(status, BorderLayout.EAST);

    JPanel controls = new JPanel(new GridLayout(0, 2, 8, 8));
    controls.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    controls.add(new JLabel("Message"));
    controls.add(new JTextField("Hello from theme preview"));

    controls.add(new JLabel("Nickname color"));
    JComboBox<String> nickColor = new JComboBox<>(new String[] {"Default", "Blue", "Orange"});
    nickColor.setSelectedIndex(1);
    controls.add(nickColor);

    controls.add(new JLabel("Unread marker"));
    JCheckBox unread = new JCheckBox("Enabled", true);
    controls.add(unread);

    controls.add(new JLabel("History load"));
    JSlider load = new JSlider(0, 100, 35);
    controls.add(load);

    controls.add(new JLabel("Network usage"));
    JProgressBar p = new JProgressBar(0, 100);
    p.setValue(62);
    p.setStringPainted(true);
    controls.add(p);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actions.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
    JButton primary = new JButton("Send");
    primary.putClientProperty(FlatClientProperties.BUTTON_TYPE, "primary");
    actions.add(primary);
    actions.add(new JButton("Reconnect"));
    actions.add(new JButton("Open Preferences"));

    JPanel body = new JPanel(new BorderLayout(8, 8));
    body.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
    body.add(controls, BorderLayout.NORTH);
    body.add(actions, BorderLayout.SOUTH);

    preview.add(top, BorderLayout.NORTH);
    preview.add(body, BorderLayout.CENTER);
    return preview;
  }

  private void applyPreviewTheme(String id) {
    String next = normalizeThemeId(id);
    if (sameTheme(next, previewThemeId)) return;
    themeManager.applyTheme(next);
    previewThemeId = next;
  }

  private void queuePreviewTheme(String id) {
    String next = normalizeThemeId(id);
    SwingUtilities.invokeLater(() -> {
      if (dialog == null || !dialog.isShowing()) return;
      applyPreviewTheme(next);
    });
  }

  private void restoreCommittedTheme() {
    if (sameTheme(previewThemeId, committedThemeId)) return;
    themeManager.applyTheme(committedThemeId);
    previewThemeId = committedThemeId;
  }

  private void commitSelectedTheme() {
    String next = normalizeThemeId(selectedThemeId());
    if (next.isBlank()) return;

    UiSettings cur = settingsBus.get();
    if (cur == null) return;

    if (!sameTheme(cur.theme(), next)) {
      UiSettings updated = cur.withTheme(next);
      settingsBus.set(updated);
      runtimeConfig.rememberUiSettings(updated.theme(), updated.chatFontFamily(), updated.chatFontSize());
    }

    if (!sameTheme(previewThemeId, next)) {
      themeManager.applyTheme(next);
      previewThemeId = next;
    }

    committedThemeId = next;
  }

  private void selectThemeInList(String id) {
    if (themeList == null) return;
    String wanted = normalizeThemeId(id);
    for (int i = 0; i < themeList.getModel().getSize(); i++) {
      ThemeManager.ThemeOption opt = themeList.getModel().getElementAt(i);
      if (opt != null && sameTheme(opt.id(), wanted)) {
        themeList.setSelectedIndex(i);
        themeList.ensureIndexIsVisible(i);
        return;
      }
    }
    for (int i = 0; i < themeList.getModel().getSize(); i++) {
      ThemeManager.ThemeOption opt = themeList.getModel().getElementAt(i);
      if (opt != null && sameTheme(opt.id(), "dark")) {
        themeList.setSelectedIndex(i);
        themeList.ensureIndexIsVisible(i);
        return;
      }
    }
    if (themeList.getModel().getSize() > 0) {
      themeList.setSelectedIndex(0);
    }
  }

  private String selectedThemeId() {
    ThemeManager.ThemeOption sel = themeList != null ? themeList.getSelectedValue() : null;
    if (sel == null) return committedThemeId;
    return sel.id();
  }

  private static String normalizeThemeId(String id) {
    String s = Objects.toString(id, "").trim();
    if (s.isEmpty()) return "dark";
    return s.toLowerCase();
  }

  private static boolean sameTheme(String a, String b) {
    return normalizeThemeId(a).equals(normalizeThemeId(b));
  }

  private void closeDialog() {
    if (dialog != null) {
      dialog.dispose();
      dialog = null;
    }
  }
}
