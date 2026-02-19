package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.icons.SvgIcons;
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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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

  private Timer previewTimer;
  private String pendingPreviewThemeId;

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

    // Use curated themes by default, but allow the selector to expand to all IntelliJ themes.
    // This keeps menus/dropdowns compact while still letting power users browse everything.
    JCheckBox allIntelliJ = new JCheckBox("All IntelliJ themes");
    allIntelliJ.setToolTipText("Loads the full IntelliJ Themes Pack list (large). Use search to find themes quickly.");

    ThemeManager.ThemeOption[] allThemes = themeManager.themesForPicker(false);

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
        new PackChoice("IRCafe", ThemeManager.ThemePack.IRCAFE),
        new PackChoice("IntelliJ", ThemeManager.ThemePack.INTELLIJ)
    });

    JTextField search = new JTextField(14);
    search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search themes");

    JLabel count = new JLabel();
    count.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");

    Runnable refresh = () -> {
      String keepId = normalizeThemeId(selectedThemeId());
      ThemeManager.ThemeOption[] pool = themeManager.themesForPicker(allIntelliJ.isSelected());
      rebuildModel(model, pool, (ToneChoice) toneFilter.getSelectedItem(),
          (PackChoice) packFilter.getSelectedItem(), search.getText());
      count.setText("Showing " + model.getSize() + " of " + pool.length);
      selectThemeInList(keepId);
    };

    ActionListener filterListener = e -> refresh.run();
    toneFilter.addActionListener(filterListener);
    packFilter.addActionListener(filterListener);
    allIntelliJ.addActionListener(filterListener);
    search.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { refresh.run(); }
      @Override public void removeUpdate(DocumentEvent e) { refresh.run(); }
      @Override public void changedUpdate(DocumentEvent e) { refresh.run(); }
    });

    // initial fill
    rebuildModel(model, allThemes, (ToneChoice) toneFilter.getSelectedItem(),
        (PackChoice) packFilter.getSelectedItem(), search.getText());
    count.setText("Showing " + model.getSize() + " of " + allThemes.length);

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
          case INTELLIJ -> "IntelliJ";
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

    // NOTE: Applying a LookAndFeel *inside* the list selection callback can cause the list UI
    // to be uninstalled while Swing is still dispatching the same selection event.
    // BasicListUI's internal handler then sees a null "list" and throws an NPE.
    //
    // Fix: debounce + defer the live preview until after the current event finishes.
    ensurePreviewTimer();
    themeList.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      schedulePreview(normalizeThemeId(selectedThemeId()));
    });

    JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    filterBar.add(new JLabel("Tone"));
    filterBar.add(toneFilter);
    filterBar.add(new JLabel("Pack"));
    filterBar.add(packFilter);
    filterBar.add(allIntelliJ);
    filterBar.add(search);
    filterBar.add(count);

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

    apply.setIcon(SvgIcons.action("check", 16));
    apply.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    ok.setIcon(SvgIcons.action("check", 16));
    ok.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    cancel.setIcon(SvgIcons.action("close", 16));
    cancel.setDisabledIcon(SvgIcons.actionDisabled("close", 16));
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
      if (opt != null && sameTheme(opt.id(), "darcula")) {
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
    if (s.isEmpty()) return "darcula";

    // Preserve case for IntelliJ theme ids and raw class names.
    if (s.regionMatches(true, 0, IntelliJThemePack.ID_PREFIX, 0, IntelliJThemePack.ID_PREFIX.length())) {
      return IntelliJThemePack.ID_PREFIX + s.substring(IntelliJThemePack.ID_PREFIX.length());
    }
    if (looksLikeClassName(s)) return s;

    return s.toLowerCase();
  }

  private static boolean sameTheme(String a, String b) {
    return normalizeThemeId(a).equals(normalizeThemeId(b));
  }

  private void closeDialog() {
    if (dialog != null) {
      stopPreviewTimer();
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

  private void ensurePreviewTimer() {
    if (previewTimer != null) return;

    // Debounce live previews so quick scrolling/searching doesn't thrash LookAndFeel updates.
    // Timer events run on the EDT.
    previewTimer = new Timer(175, e -> applyPendingPreview());
    previewTimer.setRepeats(false);
  }

  private void schedulePreview(String next) {
    if (dialog == null || !dialog.isShowing() || themeList == null) return;

    String wanted = normalizeThemeId(next);
    if (sameTheme(previewThemeId, wanted)) return;

    pendingPreviewThemeId = wanted;
    ensurePreviewTimer();
    previewTimer.restart();
  }

  private void applyPendingPreview() {
    if (dialog == null || !dialog.isShowing() || themeList == null) return;
    String pending = normalizeThemeId(pendingPreviewThemeId);
    if (pending.isBlank()) return;

    // Only preview if the pending theme is still the current selection.
    String current = normalizeThemeId(selectedThemeId());
    if (!sameTheme(current, pending)) return;
    if (sameTheme(previewThemeId, pending)) return;

    // Defer one more turn just to ensure we never apply LAF while Swing is mid-dispatch.
    SwingUtilities.invokeLater(() -> {
      if (dialog == null || !dialog.isShowing() || themeList == null) return;
      String stillSelected = normalizeThemeId(selectedThemeId());
      if (!sameTheme(stillSelected, pending)) return;
      if (sameTheme(previewThemeId, pending)) return;

      themeManager.applyTheme(pending);
      previewThemeId = pending;
    });
  }

  private void stopPreviewTimer() {
    if (previewTimer != null) {
      previewTimer.stop();
    }
    pendingPreviewThemeId = null;
  }

  private static boolean looksLikeClassName(String raw) {
    if (raw == null) return false;
    String s = raw.trim();
    if (!s.contains(".")) return false;
    if (s.startsWith("com.") || s.startsWith("org.") || s.startsWith("net.") || s.startsWith("io.")) return true;
    String last = s.substring(s.lastIndexOf('.') + 1);
    return !last.isBlank() && Character.isUpperCase(last.charAt(0));
  }
}
