package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.NotificationRule;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;

final class NotificationRuleDialogSupport {
  private NotificationRuleDialogSupport() {}

  static NotificationRule promptNotificationRuleDialog(
      Window owner, String title, NotificationRule seed) {
    NotificationRule base =
        seed != null
            ? seed
            : new NotificationRule("", NotificationRule.Type.WORD, "", true, false, true, null);

    JCheckBox enabled = new JCheckBox("Enabled", base.enabled());
    JTextField label = new JTextField(Objects.toString(base.label(), ""));
    JComboBox<NotificationRule.Type> type = new JComboBox<>(NotificationRule.Type.values());
    type.setSelectedItem(base.type() != null ? base.type() : NotificationRule.Type.WORD);

    JTextField pattern = new JTextField(Objects.toString(base.pattern(), ""));
    pattern.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, "Keyword or regular expression");
    JCheckBox caseSensitive = new JCheckBox("Case sensitive", base.caseSensitive());
    JCheckBox wholeWord = new JCheckBox("Whole word", base.wholeWord());
    wholeWord.setToolTipText("Only applies to WORD rules.");

    Color seedColor = SettingsColorSupport.parseHexColorLenient(base.highlightFg());
    final String[] colorHex =
        new String[] {seedColor != null ? SettingsColorSupport.toHex(seedColor) : null};
    JLabel colorPreview = new JLabel();
    JButton pickColor = new JButton("Choose…");
    JButton clearColor = new JButton("Clear");
    pickColor.setIcon(SvgIcons.action("palette", 14));
    pickColor.setDisabledIcon(SvgIcons.actionDisabled("palette", 14));
    clearColor.setIcon(SvgIcons.action("close", 14));
    clearColor.setDisabledIcon(SvgIcons.actionDisabled("close", 14));

    Runnable refreshWholeWordState =
        () -> {
          boolean wordRule = NotificationRule.Type.WORD.equals(type.getSelectedItem());
          wholeWord.setEnabled(wordRule);
          if (!wordRule) {
            wholeWord.setSelected(false);
          }
        };
    type.addActionListener(e -> refreshWholeWordState.run());
    refreshWholeWordState.run();

    Runnable refreshColorPreview =
        () -> {
          Color c = SettingsColorSupport.parseHexColorLenient(colorHex[0]);
          if (c == null) {
            colorPreview.setIcon(null);
            colorPreview.setText("Default");
            Color fg = UIManager.getColor("Label.foreground");
            if (fg != null) colorPreview.setForeground(fg);
            return;
          }
          colorPreview.setIcon(new ColorSwatch(c, 14, 14));
          colorPreview.setText(SettingsColorSupport.toHex(c));
          Color fg = UIManager.getColor("Label.foreground");
          if (fg != null) colorPreview.setForeground(fg);
        };
    refreshColorPreview.run();

    pickColor.addActionListener(
        e -> {
          Color current = SettingsColorSupport.parseHexColorLenient(colorHex[0]);
          if (current == null) {
            Color fallback = UIManager.getColor("TextPane.foreground");
            if (fallback == null) fallback = UIManager.getColor("Label.foreground");
            current = fallback != null ? fallback : Color.WHITE;
          }
          Color chosen =
              SettingsColorPickerDialogSupport.showColorPickerDialog(
                  owner,
                  "Choose Rule Highlight Color",
                  current,
                  SettingsColorSupport.preferredPreviewBackground());
          if (chosen == null) return;
          colorHex[0] = SettingsColorSupport.toHex(chosen);
          refreshColorPreview.run();
        });

    clearColor.addActionListener(
        e -> {
          colorHex[0] = null;
          refreshColorPreview.run();
        });

    JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    colorRow.setOpaque(false);
    colorRow.add(colorPreview);
    colorRow.add(pickColor);
    colorRow.add(clearColor);

    JTextArea hint =
        helpText("WORD supports whole-word matching; REGEX supports Java regular expressions.");

    JPanel form =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 2,hidemode 3", "[right][grow,fill]", "[]6[]6[]6[]6[]6[]6[]"));
    form.add(enabled, "span 2,wrap");
    form.add(new JLabel("Label:"));
    form.add(label, "growx,pushx,wmin 0,wrap");
    form.add(new JLabel("Type:"));
    form.add(type, "w 140!,wrap");
    form.add(new JLabel("Pattern:"));
    form.add(pattern, "growx,pushx,wmin 0,wrap");
    form.add(new JLabel("Options:"));
    JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    options.setOpaque(false);
    options.add(caseSensitive);
    options.add(wholeWord);
    form.add(options, "growx,wrap");
    form.add(new JLabel("Color:"));
    form.add(colorRow, "growx,wrap");
    form.add(new JLabel(""));
    form.add(hint, "growx,wmin 0,wrap");

    String dialogTitle = Objects.toString(title, "Notification Rule");

    while (true) {
      int choice =
          JOptionPane.showConfirmDialog(
              owner, form, dialogTitle, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (choice != JOptionPane.OK_OPTION) return null;

      NotificationRule.Type selectedType =
          type.getSelectedItem() instanceof NotificationRule.Type t
              ? t
              : NotificationRule.Type.WORD;

      String patternText = Objects.toString(pattern.getText(), "").trim();
      if (selectedType == NotificationRule.Type.REGEX && !patternText.isEmpty()) {
        try {
          int flags = Pattern.UNICODE_CASE;
          if (!caseSensitive.isSelected()) flags |= Pattern.CASE_INSENSITIVE;
          Pattern.compile(patternText, flags);
        } catch (Exception ex) {
          String msg = Objects.toString(ex.getMessage(), "Invalid regular expression");
          JOptionPane.showMessageDialog(
              owner,
              "Invalid REGEX pattern:\n" + msg,
              "Invalid Notification Rule",
              JOptionPane.ERROR_MESSAGE);
          continue;
        }
      }

      return new NotificationRule(
          label.getText(),
          selectedType,
          patternText,
          enabled.isSelected(),
          caseSensitive.isSelected(),
          selectedType == NotificationRule.Type.WORD && wholeWord.isSelected(),
          colorHex[0]);
    }
  }

  private static JTextArea helpText(String text) {
    JTextArea t = new JTextArea(text);
    t.setEditable(false);
    t.setLineWrap(true);
    t.setWrapStyleWord(true);
    t.setOpaque(false);
    t.setFocusable(false);
    t.setBorder(null);
    t.setFont(UIManager.getFont("Label.font"));
    t.setForeground(UIManager.getColor("Label.foreground"));
    Dimension pref = t.getPreferredSize();
    t.setMinimumSize(new Dimension(0, pref != null ? pref.height : 0));
    return t;
  }
}
