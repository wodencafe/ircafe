package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ThemeSelectionDialog {

  private record ToneChoice(String label, ThemeManager.ThemeTone tone) {
    @Override public String toString() { return label; }
  }

  private record PackChoice(String label, ThemeManager.ThemePack pack) {
    @Override public String toString() { return label; }
  }

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

    ThemeManager.ThemeOption[] allThemes = themeManager.supportedThemes();

    DefaultListModel<ThemeManager.ThemeOption> model = new DefaultListModel<>();

    JComboBox<ToneChoice> toneFilter = new JComboBox<>(new ToneChoice[] {
        new ToneChoice("All", null),
        new ToneChoice("Dark", ThemeManager.ThemeTone.DARK),
        new ToneChoice("Light", ThemeManager.ThemeTone.LIGHT),
        new ToneChoice("System", ThemeManager.ThemeTone.SYSTEM)
    });

    JComboBox<PackChoice> packFilter = new JComboBox<>(new PackChoice[] {
        new PackChoice("All packs", null),
        new PackChoice("System", ThemeManager.ThemePack.SYSTEM),
        new PackChoice("FlatLaf", ThemeManager.ThemePack.FLATLAF),
        new PackChoice("IRCafe", ThemeManager.ThemePack.IRCAFE)
    });

    JTextField search = new JTextField(14);
    search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search themes");

    Runnable refresh = () -> {
      String keepId = normalizeThemeId(selectedThemeId());
      rebuildModel(model, allThemes, (ToneChoice) toneFilter.getSelectedItem(),
          (PackChoice) packFilter.getSelectedItem(), search.getText());
      selectThemeInList(keepId);
    };

    ActionListener filterListener = e -> refresh.run();
    toneFilter.addActionListener(filterListener);
    packFilter.addActionListener(filterListener);
    search.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { refresh.run(); }
      @Override public void removeUpdate(DocumentEvent e) { refresh.run(); }
      @Override public void changedUpdate(DocumentEvent e) { refresh.run(); }
    });

    // initial fill
    rebuildModel(model, allThemes, (ToneChoice) toneFilter.getSelectedItem(),
        (PackChoice) packFilter.getSelectedItem(), search.getText());

    themeList = new JList<>(model);
    themeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    themeList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
      JLabel l = (JLabel) new DefaultListCellRenderer()
          .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value != null) {
        String pack = switch (value.pack()) {
          case SYSTEM -> "System";
          case FLATLAF -> "FlatLaf";
          case IRCAFE -> "IRCafe";
        };
        String tone = switch (value.tone()) {
          case SYSTEM -> "System";
          case DARK -> "Dark";
          case LIGHT -> "Light";
        };
        l.setText("<html>" + esc(value.label()) + " <span style='color:gray'>&mdash; " + pack + "</span></html>");
        l.setToolTipText(tone + " theme \u00B7 " + pack + " pack");
      }
      return l;
    });

    selectThemeInList(committedThemeId);

    themeList.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      String next = normalizeThemeId(selectedThemeId());
      if (next.isBlank()) return;
      if (!sameTheme(previewThemeId, next)) {
        themeManager.applyTheme(next);
        previewThemeId = next;
      }
    });

    JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    filterBar.add(new JLabel("Tone"));
    filterBar.add(toneFilter);
    filterBar.add(new JLabel("Pack"));
    filterBar.add(packFilter);
    filterBar.add(search);

    JScrollPane listScroll = new JScrollPane(themeList);
    listScroll.setPreferredSize(new Dimension(250, 280));

    JPanel listPanel = new JPanel(new BorderLayout(8, 8));
    JPanel header = new JPanel(new BorderLayout(0, 6));
    header.add(new JLabel("Themes"), BorderLayout.NORTH);
    header.add(filterBar, BorderLayout.SOUTH);
    listPanel.add(header, BorderLayout.NORTH);
    listPanel.add(listScroll, BorderLayout.CENTER);

    JButton apply = new JButton("Apply");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");
    apply.putClientProperty(FlatClientProperties.BUTTON_TYPE, "primary");

    apply.addActionListener(e -> commitSelectedTheme());
    ok.addActionListener(e -> {
      commitSelectedTheme();
      closeDialog();
    });
    cancel.addActionListener(e -> closeDialog());

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(apply);
    buttons.add(ok);
    buttons.add(cancel);

    JLabel help = new JLabel("Select a theme to preview it live. Click Apply/OK to save your selection.");
    help.putClientProperty(FlatClientProperties.STYLE, "font: -1");

    JPanel center = new JPanel(new BorderLayout(12, 0));
    center.add(listPanel, BorderLayout.WEST);

    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    root.add(help, BorderLayout.NORTH);
    root.add(center, BorderLayout.CENTER);
    root.add(buttons, BorderLayout.SOUTH);

    dialog = new JDialog(owner, "Theme Selector", JDialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override public void windowClosing(java.awt.event.WindowEvent e) {
        closeDialog();
      }
    });
    dialog.setContentPane(root);
    dialog.pack();
    dialog.setMinimumSize(new Dimension(420, 430));
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private static void rebuildModel(DefaultListModel<ThemeManager.ThemeOption> model,
                                  ThemeManager.ThemeOption[] all,
                                  ToneChoice toneChoice,
                                  PackChoice packChoice,
                                  String queryRaw) {
    ThemeManager.ThemeTone tone = toneChoice != null ? toneChoice.tone() : null;
    ThemeManager.ThemePack pack = packChoice != null ? packChoice.pack() : null;
    String q = Objects.toString(queryRaw, "").trim().toLowerCase();

    model.clear();
    Arrays.stream(all)
        .filter(o -> o != null)
        .filter(o -> tone == null || o.tone() == tone)
        .filter(o -> pack == null || o.pack() == pack)
        .filter(o -> q.isEmpty() || o.label().toLowerCase().contains(q) || o.id().toLowerCase().contains(q))
        .forEach(model::addElement);
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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

    if (!sameTheme(committedThemeId, next)) {
      themeManager.applyTheme(next);
      committedThemeId = next;
    }
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
      String committed = normalizeThemeId(committedThemeId);
      String preview = normalizeThemeId(previewThemeId);
      if (!sameTheme(committed, preview)) {
        themeManager.applyTheme(committed);
        previewThemeId = committed;
      }
      dialog.dispose();
      dialog = null;
    }
  }
}
