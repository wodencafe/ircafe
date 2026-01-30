package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.DialogCloseableScopeDecorator;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
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
    imageEmbeds.addActionListener(e -> imageEmbedsCollapsed.setEnabled(imageEmbeds.isSelected()));

    JPanel imagePanel = new JPanel();
    imagePanel.setOpaque(false);
    imagePanel.setLayout(new BoxLayout(imagePanel, BoxLayout.Y_AXIS));
    imagePanel.add(imageEmbeds);
    imagePanel.add(imageEmbedsCollapsed);

    JCheckBox linkPreviews = new JCheckBox("Enable link previews (OpenGraph cards)");
    linkPreviews.setSelected(current.linkPreviewsEnabled());
    linkPreviews.setToolTipText("If enabled, IRCafe will fetch page metadata (title/description/image) and show a preview card under messages.\nNote: this makes network requests to the linked sites.");

    JCheckBox linkPreviewsCollapsed = new JCheckBox("Collapse link previews by default");
    linkPreviewsCollapsed.setSelected(current.linkPreviewsCollapsedByDefault());
    linkPreviewsCollapsed.setToolTipText("If enabled, newly inserted link previews start collapsed (header shown; click to expand).");
    linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected());
    linkPreviews.addActionListener(e -> linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected()));

    JPanel linkPanel = new JPanel();
    linkPanel.setOpaque(false);
    linkPanel.setLayout(new BoxLayout(linkPanel, BoxLayout.Y_AXIS));
    linkPanel.add(linkPreviews);
    linkPanel.add(linkPreviewsCollapsed);

    JCheckBox chatTimestamps = new JCheckBox("Show timestamps on chat messages");
    chatTimestamps.setSelected(current.chatMessageTimestampsEnabled());
    chatTimestamps.setToolTipText("If enabled, IRCafe will prepend a timestamp to each regular user message line.");

    // Layout
    JPanel form = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(8, 10, 8, 10);
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1.0;

    c.gridx = 0;
    c.gridy = 0;
    form.add(new JLabel("Theme"), c);

    c.gridx = 1;
    form.add(theme, c);

    c.gridx = 0;
    c.gridy++;
    form.add(new JLabel("Chat font"), c);

    c.gridx = 1;
    form.add(fontFamily, c);

    c.gridx = 0;
    c.gridy++;
    form.add(new JLabel("Chat font size"), c);

    c.gridx = 1;
    form.add(fontSize, c);

    c.gridx = 0;
    c.gridy++;
    form.add(new JLabel("Inline images"), c);

    c.gridx = 1;
    form.add(imagePanel, c);

    c.gridx = 0;
    c.gridy++;
    form.add(new JLabel("Link previews"), c);

    c.gridx = 1;
    form.add(linkPanel, c);

    c.gridx = 0;
    c.gridy++;
    form.add(new JLabel("Chat timestamps"), c);

    c.gridx = 1;
    form.add(chatTimestamps, c);

    JButton apply = new JButton("Apply");
    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");

    Runnable doApply = () -> {
      String t = String.valueOf(theme.getSelectedItem());
      String fam = String.valueOf(fontFamily.getSelectedItem());
      int size = ((Number) fontSize.getValue()).intValue();

      UiSettings prev = settingsBus.get();
      UiSettings next = new UiSettings(
          t,
          fam,
          size,
          imageEmbeds.isSelected(),
          imageEmbedsCollapsed.isSelected(),
          linkPreviews.isSelected(),
          linkPreviewsCollapsed.isSelected(),
          prev.presenceFoldsEnabled(),
          chatTimestamps.isSelected()
      );

      boolean themeChanged = !next.theme().equalsIgnoreCase(prev.theme());

      settingsBus.set(next);
      runtimeConfig.rememberUiSettings(next.theme(), next.chatFontFamily(), next.chatFontSize());
      runtimeConfig.rememberImageEmbedsEnabled(next.imageEmbedsEnabled());
      runtimeConfig.rememberImageEmbedsCollapsedByDefault(next.imageEmbedsCollapsedByDefault());
      runtimeConfig.rememberLinkPreviewsEnabled(next.linkPreviewsEnabled());
      runtimeConfig.rememberLinkPreviewsCollapsedByDefault(next.linkPreviewsCollapsedByDefault());
      runtimeConfig.rememberChatMessageTimestampsEnabled(next.chatMessageTimestampsEnabled());

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

    d.setLayout(new BorderLayout());
    d.add(form, BorderLayout.CENTER);
    d.add(buttons, BorderLayout.SOUTH);
    d.setMinimumSize(new Dimension(560, 340));
    d.pack();
    d.setLocationRelativeTo(owner);
    d.setVisible(true);
  }
}
