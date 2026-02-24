package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
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
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
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
  private JEditorPane transcriptPreview;
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
    committedThemeId = ThemeIdUtils.normalizeThemeId(cur != null ? cur.theme() : null);
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
        new PackChoice("DarkLaf", ThemeManager.ThemePack.DARKLAF),
        new PackChoice("Retro", ThemeManager.ThemePack.RETRO),
        new PackChoice("Modern", ThemeManager.ThemePack.MODERN),
        new PackChoice("IRCafe", ThemeManager.ThemePack.IRCAFE),
        new PackChoice("IntelliJ", ThemeManager.ThemePack.INTELLIJ)
    });

    JTextField search = new JTextField(14);
    search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search themes");

    JLabel count = new JLabel();
    count.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");

    Runnable refresh = () -> {
      String keepId = ThemeIdUtils.normalizeThemeId(selectedThemeId());
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
          case DARKLAF -> "DarkLaf";
          case RETRO -> "Retro";
          case MODERN -> "Modern";
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
      schedulePreview(ThemeIdUtils.normalizeThemeId(selectedThemeId()));
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
    listPanel.add(new JLabel("Themes"), BorderLayout.NORTH);
    listPanel.add(listScroll, BorderLayout.CENTER);

    transcriptPreview = new JEditorPane("text/html", "");
    transcriptPreview.setEditable(false);
    transcriptPreview.setFocusable(false);
    transcriptPreview.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    // Avoid FlatLaf style parsing here so cross-LAF previews (e.g. DarkLaf -> FlatLaf) stay robust.
    transcriptPreview.setBorder(BorderFactory.createEmptyBorder());
    refreshTranscriptPreview();

    JScrollPane previewScroll = new JScrollPane(transcriptPreview);
    previewScroll.setPreferredSize(new Dimension(420, 280));

    JLabel previewTitle = new JLabel("Transcript Preview");
    previewTitle.putClientProperty(FlatClientProperties.STYLE, "font: -1");

    JPanel previewPanel = new JPanel(new BorderLayout(0, 6));
    previewPanel.add(previewTitle, BorderLayout.NORTH);
    previewPanel.add(previewScroll, BorderLayout.CENTER);

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

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, previewPanel);
    split.setContinuousLayout(true);
    split.setOneTouchExpandable(true);
    split.setResizeWeight(0.36);
    split.setBorder(BorderFactory.createEmptyBorder());

    JPanel top = new JPanel(new BorderLayout(0, 6));
    top.add(help, BorderLayout.NORTH);
    top.add(filterBar, BorderLayout.SOUTH);

    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    root.add(top, BorderLayout.NORTH);
    root.add(split, BorderLayout.CENTER);
    root.add(buttons, BorderLayout.SOUTH);

    dialog = new JDialog(owner, "More Themes", JDialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override public void windowClosing(java.awt.event.WindowEvent e) {
        closeDialog();
      }
    });
    dialog.setContentPane(root);
    dialog.pack();
    dialog.setMinimumSize(new Dimension(760, 430));
    split.setDividerLocation(0.36);
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
    String next = ThemeIdUtils.normalizeThemeId(selectedThemeId());
    if (next.isBlank()) return;

    UiSettings cur = settingsBus.get();
    if (cur == null) return;

    if (!ThemeIdUtils.sameTheme(cur.theme(), next)) {
      UiSettings updated = cur.withTheme(next);
      settingsBus.set(updated);
      runtimeConfig.rememberUiSettings(updated.theme(), updated.chatFontFamily(), updated.chatFontSize());
    }

    if (!ThemeIdUtils.sameTheme(committedThemeId, next)) {
      themeManager.applyTheme(next);
      committedThemeId = next;
      refreshTranscriptPreview();
    }
  }

  private void selectThemeInList(String id) {
    if (themeList == null) return;
    String wanted = ThemeIdUtils.normalizeThemeId(id);
    for (int i = 0; i < themeList.getModel().getSize(); i++) {
      ThemeManager.ThemeOption opt = themeList.getModel().getElementAt(i);
      if (opt != null && ThemeIdUtils.sameTheme(opt.id(), wanted)) {
        themeList.setSelectedIndex(i);
        themeList.ensureIndexIsVisible(i);
        return;
      }
    }
    for (int i = 0; i < themeList.getModel().getSize(); i++) {
      ThemeManager.ThemeOption opt = themeList.getModel().getElementAt(i);
      if (opt != null && ThemeIdUtils.sameTheme(opt.id(), "darcula")) {
        themeList.setSelectedIndex(i);
        themeList.ensureIndexIsVisible(i);
        return;
      }
    }
    for (int i = 0; i < themeList.getModel().getSize(); i++) {
      ThemeManager.ThemeOption opt = themeList.getModel().getElementAt(i);
      if (opt != null && ThemeIdUtils.sameTheme(opt.id(), "dark")) {
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

  private void closeDialog() {
    if (dialog != null) {
      stopPreviewTimer();
      String committed = ThemeIdUtils.normalizeThemeId(committedThemeId);
      String preview = ThemeIdUtils.normalizeThemeId(previewThemeId);
      if (!ThemeIdUtils.sameTheme(committed, preview)) {
        themeManager.applyTheme(committed);
        previewThemeId = committed;
        refreshTranscriptPreview();
      }
      dialog.dispose();
      dialog = null;
      transcriptPreview = null;
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

    String wanted = ThemeIdUtils.normalizeThemeId(next);
    if (ThemeIdUtils.sameTheme(previewThemeId, wanted)) return;

    pendingPreviewThemeId = wanted;
    ensurePreviewTimer();
    previewTimer.restart();
  }

  private void applyPendingPreview() {
    if (dialog == null || !dialog.isShowing() || themeList == null) return;
    String pending = ThemeIdUtils.normalizeThemeId(pendingPreviewThemeId);
    if (pending.isBlank()) return;

    // Only preview if the pending theme is still the current selection.
    String current = ThemeIdUtils.normalizeThemeId(selectedThemeId());
    if (!ThemeIdUtils.sameTheme(current, pending)) return;
    if (ThemeIdUtils.sameTheme(previewThemeId, pending)) return;

    // Defer one more turn just to ensure we never apply LAF while Swing is mid-dispatch.
    SwingUtilities.invokeLater(() -> {
      if (dialog == null || !dialog.isShowing() || themeList == null) return;
      String stillSelected = ThemeIdUtils.normalizeThemeId(selectedThemeId());
      if (!ThemeIdUtils.sameTheme(stillSelected, pending)) return;
      if (ThemeIdUtils.sameTheme(previewThemeId, pending)) return;

      themeManager.applyTheme(pending);
      previewThemeId = pending;
      refreshTranscriptPreview();
    });
  }

  private void refreshTranscriptPreview() {
    if (transcriptPreview == null) return;

    Color panelBg = firstUiColor(Color.WHITE, "Panel.background", "control", "nimbusBase");
    Color textBg = firstUiColor(panelBg, "TextArea.background", "TextComponent.background", "Panel.background");
    Color textFg = firstUiColor(Color.BLACK, "TextArea.foreground", "TextComponent.foreground", "Label.foreground", "textText");
    Color accent = firstUiColor(new Color(0x2D, 0x6B, 0xFF), "@accentColor", "Component.linkColor", "Component.focusColor", "textHighlight");
    Color muted = ThemeColorUtils.mix(textFg, textBg, 0.45);
    Color system = ThemeColorUtils.mix(textFg, textBg, 0.30);
    Color nick = ThemeColorUtils.mix(accent, textFg, 0.20);
    Color self = ThemeColorUtils.mix(accent, textFg, 0.35);
    Color highlightBg = firstUiColor(null, "List.selectionBackground", "TextComponent.selectionBackground");
    if (highlightBg == null) highlightBg = ThemeColorUtils.mix(textBg, accent, 0.33);
    Color highlightFg = firstUiColor(null, "List.selectionForeground", "TextComponent.selectionForeground");
    if (highlightFg == null) highlightFg = ThemeColorUtils.bestTextColor(highlightBg);

    String html =
        "<html><body style='margin:0;padding:10px;background:" + toHex(textBg) + ";color:" + toHex(textFg) + ";'>"
            + "<div style='color:" + toHex(system) + ";'>[12:41] *** Connected to Libera.Chat as bob</div>"
            + "<div><span style='color:" + toHex(muted) + ";'>[12:42]</span> "
            + "<span style='color:" + toHex(nick) + ";'>&lt;alice&gt;</span> anyone up for #java?</div>"
            + "<div><span style='color:" + toHex(muted) + ";'>[12:42]</span> "
            + "<span style='color:" + toHex(self) + ";'>&lt;bob&gt;</span> sure, joining now.</div>"
            + "<div style='margin-top:4px;padding:2px 4px;background:" + toHex(highlightBg)
            + ";color:" + toHex(highlightFg) + ";'>"
            + "<span style='color:" + toHex(muted) + ";'>[12:43]</span> "
            + "<span style='color:" + toHex(nick) + ";'>&lt;dave&gt;</span> this is a highlight message for bob."
            + "</div>"
            + "<div style='color:" + toHex(system) + ";'>[12:44] * carol waves</div>"
            + "<div style='color:" + toHex(accent) + ";'>[12:45] -- Invite: dave invited you to #retro "
            + "(reason: old-school night)</div>"
            + "</body></html>";

    transcriptPreview.setBackground(panelBg);
    transcriptPreview.setText(html);
    transcriptPreview.setCaretPosition(0);
  }

  private static Color firstUiColor(Color fallback, String... keys) {
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      Color c = UIManager.getColor(key);
      if (c != null) return c;
    }
    return fallback;
  }

  private static String toHex(Color c) {
    Color use = c != null ? c : Color.WHITE;
    return String.format("#%02X%02X%02X", use.getRed(), use.getGreen(), use.getBlue());
  }

  private void stopPreviewTimer() {
    if (previewTimer != null) {
      previewTimer.stop();
    }
    pendingPreviewThemeId = null;
  }

}
