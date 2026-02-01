package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.DialogCloseableScopeDecorator;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Simple preferences dialog.
 */
@Component
@Lazy
public class PreferencesDialog {

  private final UiSettingsBus settingsBus;
  private final ThemeManager themeManager;
  private final RuntimeConfigStore runtimeConfig;

  private JDialog dialog;

  public PreferencesDialog(UiSettingsBus settingsBus,
                           ThemeManager themeManager,
                           RuntimeConfigStore runtimeConfig) {
    this.settingsBus = settingsBus;
    this.themeManager = themeManager;
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

    UiSettings current = settingsBus.get();

    // Theme choices
    Map<String, String> themeLabelById = new LinkedHashMap<>();
    for (ThemeManager.ThemeOption opt : themeManager.supportedThemes()) {
      themeLabelById.put(opt.id(), opt.label());
    }

    JComboBox<String> theme = new JComboBox<>(themeLabelById.keySet().toArray(String[]::new));
    theme.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
      JLabel l = new JLabel(themeLabelById.getOrDefault(value, value));
      l.setOpaque(true);
      if (isSelected) {
        l.setBackground(list.getSelectionBackground());
        l.setForeground(list.getSelectionForeground());
      } else {
        l.setBackground(list.getBackground());
        l.setForeground(list.getForeground());
      }
      l.setBorder(null);
      return l;
    });
    theme.setSelectedItem(current.theme());

    // Fonts
    String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Arrays.sort(families, String.CASE_INSENSITIVE_ORDER);
    JComboBox<String> fontFamily = new JComboBox<>(families);
    fontFamily.setEditable(true);
    fontFamily.setSelectedItem(current.chatFontFamily());

    JSpinner fontSize = new JSpinner(new SpinnerNumberModel(current.chatFontSize(), 8, 48, 1));
    final var fontSizeMouseWheelAC = MouseWheelDecorator.decorateNumberSpinner(fontSize);

    JCheckBox imageEmbeds = new JCheckBox("Enable inline image embeds (direct links)");
    imageEmbeds.setSelected(current.imageEmbedsEnabled());
    imageEmbeds.setToolTipText("If enabled, IRCafe will download and render images from direct image URLs in chat.");

    JCheckBox imageEmbedsCollapsed = new JCheckBox("Collapse inline images by default");
    imageEmbedsCollapsed.setSelected(current.imageEmbedsCollapsedByDefault());
    imageEmbedsCollapsed.setToolTipText("If enabled, newly inserted inline images start collapsed (header shown; click to expand).");
    imageEmbedsCollapsed.setEnabled(imageEmbeds.isSelected());

    // Max width cap (0 = no extra cap; images still scale to fit the viewport).
    JSpinner imageMaxWidth = new JSpinner(new SpinnerNumberModel(current.imageEmbedsMaxWidthPx(), 0, 4096, 10));
    final var imageMaxWidthMouseWheelAC = MouseWheelDecorator.decorateNumberSpinner(imageMaxWidth);
    imageMaxWidth.setToolTipText("Maximum width for inline images (pixels).\n" +
        "If 0, IRCafe will only scale images down to fit the chat viewport.");
    imageMaxWidth.setEnabled(imageEmbeds.isSelected());

    // Max height cap (0 = no extra cap; images still scale to fit the viewport / max-width cap).
    JSpinner imageMaxHeight = new JSpinner(new SpinnerNumberModel(current.imageEmbedsMaxHeightPx(), 0, 4096, 10));
    final var imageMaxHeightMouseWheelAC = MouseWheelDecorator.decorateNumberSpinner(imageMaxHeight);
    imageMaxHeight.setToolTipText("Maximum height for inline images (pixels).\n" +
        "If 0, IRCafe will only scale images down based on viewport width (and max width cap, if set).");
    imageMaxHeight.setEnabled(imageEmbeds.isSelected());

    JCheckBox animateGifs = new JCheckBox("Animate GIFs");
    animateGifs.setSelected(current.imageEmbedsAnimateGifs());
    animateGifs.setToolTipText("If disabled, animated GIFs render as a still image (first frame).");
    animateGifs.setEnabled(imageEmbeds.isSelected());

    imageEmbeds.addActionListener(e -> {
      boolean enabled = imageEmbeds.isSelected();
      imageEmbedsCollapsed.setEnabled(enabled);
      imageMaxWidth.setEnabled(enabled);
      imageMaxHeight.setEnabled(enabled);
      animateGifs.setEnabled(enabled);
    });

    JPanel imagePanel = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]8[nogrid]", "[]4[]4[]4[]4[]"));
    imagePanel.setOpaque(false);
    imagePanel.add(imageEmbeds, "span 2, wrap");
    imagePanel.add(imageEmbedsCollapsed, "span 2, wrap");
    imagePanel.add(new JLabel("Max image width (px, 0 = no limit):"));
    imagePanel.add(imageMaxWidth, "w 90!");
    imagePanel.add(new JLabel("Max image height (px, 0 = no limit):"));
    imagePanel.add(imageMaxHeight, "w 90!");
    imagePanel.add(animateGifs, "span 2, wrap");

    JCheckBox linkPreviews = new JCheckBox("Enable link previews (OpenGraph cards)");
    linkPreviews.setSelected(current.linkPreviewsEnabled());
    linkPreviews.setToolTipText("If enabled, IRCafe will fetch page metadata (title/description/image) and show a preview card under messages.\nNote: this makes network requests to the linked sites.");

    JCheckBox linkPreviewsCollapsed = new JCheckBox("Collapse link previews by default");
    linkPreviewsCollapsed.setSelected(current.linkPreviewsCollapsedByDefault());
    linkPreviewsCollapsed.setToolTipText("If enabled, newly inserted link previews start collapsed (header shown; click to expand).");
    linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected());
    linkPreviews.addActionListener(e -> linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected()));

    JPanel linkPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]4[]"));
    linkPanel.setOpaque(false);
    linkPanel.add(linkPreviews);
    linkPanel.add(linkPreviewsCollapsed);

    JCheckBox chatTimestamps = new JCheckBox("Show timestamps on chat messages");
    chatTimestamps.setSelected(current.chatMessageTimestampsEnabled());
    chatTimestamps.setToolTipText("If enabled, IRCafe will prepend a timestamp to each regular user message line.");

    // ---- Hostmask discovery / USERHOST anti-flood settings ----
    JPanel userhostPanel = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]8[]8[]8[]"));

    JTextArea userhostInfo = new JTextArea(
        "IRCafe prefers IRCv3 userhost-in-names (free). " +
            "If hostmasks are still missing and you have hostmask-based ignore rules, IRCafe can " +
            "use USERHOST carefully with conservative rate limits."
    );
    userhostInfo.setEditable(false);
    userhostInfo.setLineWrap(true);
    userhostInfo.setWrapStyleWord(true);
    userhostInfo.setOpaque(false);
    userhostInfo.setFocusable(false);
    userhostInfo.setBorder(null);
    userhostInfo.setFont(UIManager.getFont("Label.font"));
    userhostInfo.setForeground(UIManager.getColor("Label.foreground"));
    // Keep the dialog from packing excessively wide; MigLayout will allow it to grow if needed.
    userhostInfo.setColumns(48);

    JCheckBox userhostEnabled = new JCheckBox("Resolve missing hostmasks using USERHOST (rate-limited)");
    userhostEnabled.setSelected(current.userhostDiscoveryEnabled());
    userhostEnabled.setToolTipText("When enabled, IRCafe may send USERHOST only when hostmask-based ignore rules exist and some nicks are missing hostmasks.");

    JSpinner userhostMinIntervalSeconds = new JSpinner(new SpinnerNumberModel(current.userhostMinIntervalSeconds(), 1, 60, 1));
    userhostMinIntervalSeconds.setToolTipText("Minimum seconds between USERHOST commands per server.");
    AutoCloseable userhostMinIntervalAC = MouseWheelDecorator.decorateNumberSpinner(userhostMinIntervalSeconds);

    JSpinner userhostMaxPerMinute = new JSpinner(new SpinnerNumberModel(current.userhostMaxCommandsPerMinute(), 1, 60, 1));
    userhostMaxPerMinute.setToolTipText("Maximum USERHOST commands per minute per server.");
    AutoCloseable userhostMaxPerMinuteAC = MouseWheelDecorator.decorateNumberSpinner(userhostMaxPerMinute);

    JSpinner userhostNickCooldownMinutes = new JSpinner(new SpinnerNumberModel(current.userhostNickCooldownMinutes(), 1, 240, 1));
    userhostNickCooldownMinutes.setToolTipText("Cooldown in minutes before re-querying the same nick.");
    AutoCloseable userhostNickCooldownAC = MouseWheelDecorator.decorateNumberSpinner(userhostNickCooldownMinutes);

    JSpinner userhostMaxNicksPerCommand = new JSpinner(new SpinnerNumberModel(current.userhostMaxNicksPerCommand(), 1, 5, 1));
    userhostMaxNicksPerCommand.setToolTipText("How many nicks to include per USERHOST command (servers typically allow up to 5).");
    AutoCloseable userhostMaxNicksPerCommandAC = MouseWheelDecorator.decorateNumberSpinner(userhostMaxNicksPerCommand);

    Runnable updateUserhostEnabledState = () -> {
      boolean enabled = userhostEnabled.isSelected();
      userhostMinIntervalSeconds.setEnabled(enabled);
      userhostMaxPerMinute.setEnabled(enabled);
      userhostNickCooldownMinutes.setEnabled(enabled);
      userhostMaxNicksPerCommand.setEnabled(enabled);
    };
    userhostEnabled.addActionListener(e -> updateUserhostEnabledState.run());
    updateUserhostEnabledState.run();

    userhostPanel.add(userhostInfo, "span 2, growx, wrap");
    userhostPanel.add(userhostEnabled, "span 2, wrap");
    userhostPanel.add(new JLabel("Min interval (sec):"));
    userhostPanel.add(userhostMinIntervalSeconds, "w 90!");
    userhostPanel.add(new JLabel("Max commands/min:"));
    userhostPanel.add(userhostMaxPerMinute, "w 90!");
    userhostPanel.add(new JLabel("Nick cooldown (min):"));
    userhostPanel.add(userhostNickCooldownMinutes, "w 90!");
    userhostPanel.add(new JLabel("Max nicks/command:"));
    userhostPanel.add(userhostMaxNicksPerCommand, "w 90!");

    // Layout (MiGLayout avoids the fragile GridBag sizing issues)
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]8[]8[]8[]8[]"));
    form.add(new JLabel("Theme"));
    form.add(theme, "growx");

    form.add(new JLabel("Chat font"));
    form.add(fontFamily, "growx");

    form.add(new JLabel("Chat font size"));
    form.add(fontSize, "w 90!");

    form.add(new JLabel("Inline images"), "aligny top");
    form.add(imagePanel, "growx");

    form.add(new JLabel("Link previews"), "aligny top");
    form.add(linkPanel, "growx");

    form.add(new JLabel("Chat timestamps"));
    form.add(chatTimestamps);

    JButton apply = new JButton("Apply");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");

    Runnable doApply = () -> {
      String t = String.valueOf(theme.getSelectedItem());
      String fam = String.valueOf(fontFamily.getSelectedItem());
      int size = ((Number) fontSize.getValue()).intValue();
      int maxImageW = ((Number) imageMaxWidth.getValue()).intValue();
      int maxImageH = ((Number) imageMaxHeight.getValue()).intValue();

      boolean userhostEnabledV = userhostEnabled.isSelected();
      int userhostMinIntervalV = ((Number) userhostMinIntervalSeconds.getValue()).intValue();
      int userhostMaxPerMinuteV = ((Number) userhostMaxPerMinute.getValue()).intValue();
      int userhostNickCooldownV = ((Number) userhostNickCooldownMinutes.getValue()).intValue();
      int userhostMaxNicksV = ((Number) userhostMaxNicksPerCommand.getValue()).intValue();

      UiSettings prev = settingsBus.get();
      UiSettings next = new UiSettings(
          t,
          fam,
          size,
          imageEmbeds.isSelected(),
          imageEmbedsCollapsed.isSelected(),
          maxImageW,
          maxImageH,
          animateGifs.isSelected(),
          linkPreviews.isSelected(),
          linkPreviewsCollapsed.isSelected(),
          prev.presenceFoldsEnabled(),
          chatTimestamps.isSelected(),
          userhostEnabledV,
          userhostMinIntervalV,
          userhostMaxPerMinuteV,
          userhostNickCooldownV,
          userhostMaxNicksV
      );

      boolean themeChanged = !next.theme().equalsIgnoreCase(prev.theme());

      settingsBus.set(next);
      runtimeConfig.rememberUiSettings(next.theme(), next.chatFontFamily(), next.chatFontSize());
      runtimeConfig.rememberImageEmbedsEnabled(next.imageEmbedsEnabled());
      runtimeConfig.rememberImageEmbedsCollapsedByDefault(next.imageEmbedsCollapsedByDefault());
      runtimeConfig.rememberImageEmbedsMaxWidthPx(next.imageEmbedsMaxWidthPx());
      runtimeConfig.rememberImageEmbedsMaxHeightPx(next.imageEmbedsMaxHeightPx());
      runtimeConfig.rememberImageEmbedsAnimateGifs(next.imageEmbedsAnimateGifs());
      runtimeConfig.rememberLinkPreviewsEnabled(next.linkPreviewsEnabled());
      runtimeConfig.rememberLinkPreviewsCollapsedByDefault(next.linkPreviewsCollapsedByDefault());
      runtimeConfig.rememberChatMessageTimestampsEnabled(next.chatMessageTimestampsEnabled());

      runtimeConfig.rememberUserhostDiscoveryEnabled(next.userhostDiscoveryEnabled());
      runtimeConfig.rememberUserhostMinIntervalSeconds(next.userhostMinIntervalSeconds());
      runtimeConfig.rememberUserhostMaxCommandsPerMinute(next.userhostMaxCommandsPerMinute());
      runtimeConfig.rememberUserhostNickCooldownMinutes(next.userhostNickCooldownMinutes());
      runtimeConfig.rememberUserhostMaxNicksPerCommand(next.userhostMaxNicksPerCommand());

      if (themeChanged) {
        themeManager.applyTheme(next.theme());
      }
    };

    apply.addActionListener(e -> doApply.run());

    // Construct dialog early so we can attach a CloseableScope that cleans up decorators/listeners
    // regardless of exit path (OK, Cancel, window close).
    final JDialog d = new JDialog(owner, "Preferences", JDialog.ModalityType.APPLICATION_MODAL);
    d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    this.dialog = d;

    final CloseableScope scope = DialogCloseableScopeDecorator.install(d);
    scope.add(fontSizeMouseWheelAC);
    scope.add(imageMaxWidthMouseWheelAC);
    scope.add(imageMaxHeightMouseWheelAC);
    scope.add(userhostMinIntervalAC);
    scope.add(userhostMaxPerMinuteAC);
    scope.add(userhostNickCooldownAC);
    scope.add(userhostMaxNicksPerCommandAC);
    scope.addCleanup(() -> {
      if (this.dialog == d) this.dialog = null;
    });

    ok.addActionListener(e -> {
      doApply.run();
      d.dispose();
    });
    cancel.addActionListener(e -> d.dispose());

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(apply);
    buttons.add(ok);
    buttons.add(cancel);

    // Tabs
    JTabbedPane tabs = new JTabbedPane();
    JScrollPane generalScroll = new JScrollPane(form);
    generalScroll.setBorder(null);
    tabs.addTab("General", generalScroll);

    JScrollPane userhostScroll = new JScrollPane(userhostPanel);
    userhostScroll.setBorder(null);
    tabs.addTab("Hostmask discovery", userhostScroll);

    d.setLayout(new BorderLayout());
    d.add(tabs, BorderLayout.CENTER);
    d.add(buttons, BorderLayout.SOUTH);
    d.setMinimumSize(new Dimension(520, 340));
    d.pack();
    d.setLocationRelativeTo(owner);
    d.setVisible(true);
  }
}
