package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.util.MigConstraints;
import cafe.woden.ircclient.ui.util.MigLayoutConstraints;
import cafe.woden.ircclient.ui.util.MigLayouts;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import cafe.woden.ircclient.ui.util.UiColorKeys;
import cafe.woden.ircclient.ui.util.UiFontKeys;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import net.miginfocom.swing.MigLayout;

public final class PreferencesUiSupport {
  private PreferencesUiSupport() {}

  public static JPanel padSubTab(JComponent panel) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    wrapper.add(panel, BorderLayout.NORTH);
    return wrapper;
  }

  public static JLabel tabTitle(String text) {
    JLabel label = new JLabel(text);
    label.putClientProperty(FlatClientProperties.STYLE, "font:+4");
    Font font = label.getFont();
    if (font != null) {
      label.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 4f));
    }
    label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    return label;
  }

  public static JLabel sectionTitle(String text) {
    JLabel label = new JLabel(text);
    label.putClientProperty(FlatClientProperties.STYLE, "font:+2");
    Font font = label.getFont();
    if (font != null) {
      label.setFont(font.deriveFont(Font.BOLD));
    }
    label.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
    return label;
  }

  public static JPanel captionPanel(String title, String layout, String columns, String rows) {
    return captionPanel(title, new MigLayout(layout, columns, rows));
  }

  public static JPanel captionPanel(String title) {
    return captionPanel(title, MigLayouts.singleColumn());
  }

  public static JPanel captionPanel(String title, MigLayout layout) {
    return captionPanelWithPadding(title, layout, 6, 8, 8, 8);
  }

  public static JPanel captionPanelWithPadding(
      String title,
      String layout,
      String columns,
      String rows,
      int top,
      int left,
      int bottom,
      int right) {
    JPanel panel = new JPanel(new MigLayout(layout, columns, rows));
    return captionPanelWithPadding(title, panel, top, left, bottom, right);
  }

  public static JPanel captionPanelWithPadding(
      String title, MigLayout layout, int top, int left, int bottom, int right) {
    JPanel panel = new JPanel(layout);
    return captionPanelWithPadding(title, panel, top, left, bottom, right);
  }

  private static JPanel captionPanelWithPadding(
      String title, JPanel panel, int top, int left, int bottom, int right) {
    panel.setOpaque(false);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title),
            BorderFactory.createEmptyBorder(top, left, bottom, right)));
    return panel;
  }

  public static JTextArea helpText(String text) {
    JTextArea area = new JTextArea(text);
    // Nimbus paints non-opaque text areas unless their background is transparent.
    area.setBackground(new Color(0, 0, 0, 0));
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setFocusable(false);
    area.setBorder(BorderFactory.createEmptyBorder());
    area.setFont(UIManager.getFont(UiFontKeys.LABEL_FONT));
    area.setForeground(UIManager.getColor(UiColorKeys.LABEL_FOREGROUND));
    Dimension preferred = area.getPreferredSize();
    area.setMinimumSize(new Dimension(0, preferred != null ? preferred.height : 0));
    return area;
  }

  public static JTextArea subtleInfoText() {
    JTextArea area = new JTextArea();
    // Nimbus paints non-opaque text areas unless their background is transparent.
    area.setBackground(new Color(0, 0, 0, 0));
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setFocusable(false);
    area.setBorder(BorderFactory.createEmptyBorder());

    Font font = UIManager.getFont(UiFontKeys.LABEL_FONT);
    if (font != null) {
      area.setFont(font.deriveFont(Font.ITALIC));
    } else {
      area.setFont(area.getFont().deriveFont(Font.ITALIC));
    }

    Color hintColor = UIManager.getColor(UiColorKeys.LABEL_DISABLED_FOREGROUND);
    if (hintColor != null) area.setForeground(hintColor);

    Dimension preferred = area.getPreferredSize();
    area.setMinimumSize(new Dimension(0, preferred != null ? preferred.height : 0));
    return area;
  }

  public static JTextArea subtleInfoTextWith(String text) {
    JTextArea area = subtleInfoText();
    area.setText(text);
    return area;
  }

  public static JTextArea textArea(int rows, int columns, boolean wrap) {
    JTextArea area = new JTextArea(rows, columns);
    area.setLineWrap(wrap);
    area.setWrapStyleWord(wrap);
    return area;
  }

  public static void placeholder(JComponent component, String text) {
    if (component == null) return;
    component.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, text);
  }

  public static void setTextInputAvailable(JTextComponent component, boolean available) {
    if (component == null) return;
    component.setEnabled(available);
    component.setEditable(available);
  }

  public static String trimmedText(JTextComponent component) {
    return SettingsValueSupport.trimmedString(component != null ? component.getText() : null);
  }

  public static String trimmedTextOrNull(JTextComponent component) {
    String value = trimmedText(component);
    return value.isEmpty() ? null : value;
  }

  public static String trimmedString(Object value) {
    return SettingsValueSupport.trimmedString(value);
  }

  public static String trimmedStringOrNull(Object value) {
    return SettingsValueSupport.trimmedStringOrNull(value);
  }

  public static List<String> trimmedLines(String text) {
    return SettingsValueSupport.trimmedLines(text);
  }

  public static String truncateText(Object value, int maxLength) {
    String trimmed = trimmedString(value);
    if (trimmed.length() <= maxLength) return trimmed;
    return trimmed.substring(0, Math.max(0, maxLength - 1)) + "\u2026";
  }

  public static String passwordText(JPasswordField component) {
    return component != null ? new String(component.getPassword()) : "";
  }

  public static String trimmedPasswordText(JPasswordField component) {
    return passwordText(component).trim();
  }

  public static <T> T selectedComboItem(JComboBox<?> combo, Class<T> type, T fallback) {
    if (combo == null || type == null) return fallback;
    Object selected = combo.getSelectedItem();
    return type.isInstance(selected) ? type.cast(selected) : fallback;
  }

  public static String selectedComboText(JComboBox<?> combo) {
    return SettingsValueSupport.trimmedString(combo != null ? combo.getSelectedItem() : null);
  }

  public static JComponent wrapCheckBox(JCheckBox box, String labelText) {
    box.setText("");
    JPanel row = new JPanel(MigLayouts.fillX(MigLayoutConstraints.ROW_6_GROW_FILL, "[]"));
    row.setOpaque(false);

    JTextArea label = buttonWrapText(labelText);
    label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    label.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent event) {
            if (box.isEnabled()) box.doClick();
          }
        });

    row.add(box, MigConstraints.alignYTop());
    row.add(label, MigConstraints.growXPushXMinWidth0());
    return row;
  }

  public static JButton whyHelpButton(String title, String message) {
    JButton button = new JButton("?");
    button.putClientProperty("JButton.buttonType", "help");
    button.setFocusable(false);
    button.setMargin(new Insets(0, 8, 0, 8));
    button.setToolTipText("Why do I need this?");
    button.addActionListener(
        event -> showHelpDialog(SwingUtilities.getWindowAncestor(button), title, message));
    return button;
  }

  public static void configureBuiltInSoundCombo(JComboBox<BuiltInSound> combo) {
    if (combo == null) return;
    combo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof BuiltInSound sound) {
              setText(sound.displayNameForUi());
            }
            return this;
          }
        });
  }

  public static void configureIconOnlyButton(JButton button, String iconName, String tooltip) {
    if (button == null) return;
    button.setText("");
    configureButtonIcon(button, iconName, 16);
    button.setMargin(new Insets(2, 6, 2, 6));
    button.setToolTipText(tooltip);
    button.setFocusable(false);
  }

  public static JButton iconOnlyButton(String text, String iconName, String tooltip) {
    JButton button = new JButton(text);
    configureIconOnlyButton(button, iconName, tooltip);
    return button;
  }

  public static JPanel actionButtonRow(JComponent... components) {
    return leftComponentRow(4, 0, components);
  }

  public static JPanel actionButtonRow(int horizontalGap, JComponent... components) {
    return leftComponentRow(horizontalGap, 0, components);
  }

  public static JPanel leftComponentRow(
      int horizontalGap, int verticalGap, JComponent... components) {
    return componentRow(FlowLayout.LEFT, horizontalGap, verticalGap, components);
  }

  public static JPanel rightComponentRow(
      int horizontalGap, int verticalGap, JComponent... components) {
    return componentRow(FlowLayout.RIGHT, horizontalGap, verticalGap, components);
  }

  private static JPanel componentRow(
      int alignment, int horizontalGap, int verticalGap, JComponent... components) {
    JPanel panel = new JPanel(new FlowLayout(alignment, horizontalGap, verticalGap));
    if (components == null) return panel;
    for (JComponent component : components) {
      if (component != null) panel.add(component);
    }
    return panel;
  }

  public static boolean confirmOkCancel(Component parent, String message, String title) {
    return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION;
  }

  public static boolean confirmPlainOkCancel(Component parent, Object message, String title) {
    return JOptionPane.showConfirmDialog(
            parent, message, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION;
  }

  public static void showErrorMessage(Component parent, String message, String title) {
    showMessage(parent, message, title, JOptionPane.ERROR_MESSAGE);
  }

  public static void showInfoMessage(Component parent, String message, String title) {
    showMessage(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
  }

  public static void showWarningMessage(Component parent, String message, String title) {
    showMessage(parent, message, title, JOptionPane.WARNING_MESSAGE);
  }

  public static Color errorForeground() {
    Color color = UIManager.getColor(UiColorKeys.LABEL_ERROR_FOREGROUND);
    if (color != null) return color;
    color = UIManager.getColor(UiColorKeys.COMPONENT_ERROR_COLOR);
    if (color != null) return color;
    color = UIManager.getColor(UiColorKeys.COMPONENT_ERROR_OUTLINE_COLOR);
    if (color != null) return color;
    color = UIManager.getColor(UiColorKeys.COMPONENT_ERROR_BORDER_COLOR);
    if (color != null) return color;
    color = UIManager.getColor(UiColorKeys.COMPONENT_ERROR_FOCUSED_BORDER_COLOR);
    if (color != null) return color;
    return new Color(180, 0, 0);
  }

  public static JButton buttonWithIcon(String text, String iconName) {
    JButton button = new JButton(text);
    configureButtonIcon(button, iconName, 16);
    return button;
  }

  public static void configureButtonIcon(JButton button, String iconName, int size) {
    if (button == null) return;
    button.setIcon(SvgIcons.action(iconName, size));
    button.setDisabledIcon(SvgIcons.actionDisabled(iconName, size));
  }

  public static void decorateComboBoxSelection(JComboBox<?> combo, List<AutoCloseable> closeables) {
    decorateComboBoxSelection(combo, closeables, false);
  }

  public static void decorateComboBoxSelection(
      JComboBox<?> combo, List<AutoCloseable> closeables, boolean decorateWhenUntracked) {
    if (combo == null) return;
    if (closeables == null && !decorateWhenUntracked) return;
    try {
      addCloseable(MouseWheelDecorator.decorateComboBoxSelection(combo), closeables);
    } catch (Exception ignored) {
    }
  }

  public static JSpinner numberSpinner(
      int value, int min, int max, int step, List<AutoCloseable> closeables) {
    JSpinner spinner = numberSpinner(value, min, max, step);
    decorateNumberSpinner(spinner, closeables);
    return spinner;
  }

  public static JSpinner numberSpinner(int value, int min, int max, int step) {
    return new JSpinner(new SpinnerNumberModel(value, min, max, step));
  }

  public static JSpinner numberSpinner(
      double value, double min, double max, double step, List<AutoCloseable> closeables) {
    JSpinner spinner = numberSpinner(value, min, max, step);
    decorateNumberSpinner(spinner, closeables);
    return spinner;
  }

  public static JSpinner numberSpinner(double value, double min, double max, double step) {
    return new JSpinner(new SpinnerNumberModel(value, min, max, step));
  }

  public static int spinnerInt(JSpinner spinner) {
    return ((Number) spinner.getValue()).intValue();
  }

  public static double spinnerDouble(JSpinner spinner) {
    return ((Number) spinner.getValue()).doubleValue();
  }

  public static int clampedSpinnerInt(JSpinner spinner, int min, int max) {
    return clampInt(spinnerInt(spinner), min, max);
  }

  public static int clampInt(int value, int min, int max) {
    return SettingsValueSupport.clampInt(value, min, max);
  }

  private static void decorateNumberSpinner(JSpinner spinner, List<AutoCloseable> closeables) {
    if (spinner == null) return;
    try {
      addCloseable(MouseWheelDecorator.decorateNumberSpinner(spinner), closeables);
    } catch (Exception ignored) {
    }
  }

  private static void addCloseable(AutoCloseable closeable, List<AutoCloseable> closeables) {
    if (closeable != null && closeables != null) closeables.add(closeable);
  }

  private static void showMessage(Component parent, String message, String title, int messageType) {
    JOptionPane.showMessageDialog(parent, message, title, messageType);
  }

  private static JTextArea buttonWrapText(String text) {
    JTextArea area = new JTextArea(text);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setFocusable(false);
    area.setBorder(null);

    Font font = UIManager.getFont(UiFontKeys.CHECK_BOX_FONT);
    if (font == null) font = UIManager.getFont(UiFontKeys.BUTTON_FONT);
    if (font == null) font = UIManager.getFont(UiFontKeys.LABEL_FONT);
    if (font != null) area.setFont(font);

    Color color = UIManager.getColor(UiColorKeys.CHECK_BOX_FOREGROUND);
    if (color == null) color = UIManager.getColor(UiColorKeys.LABEL_FOREGROUND);
    if (color != null) area.setForeground(color);

    Dimension preferred = area.getPreferredSize();
    area.setMinimumSize(new Dimension(0, preferred != null ? preferred.height : 0));
    return area;
  }

  private static void showHelpDialog(Component parent, String title, String message) {
    JTextArea area = new JTextArea(message);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    area.setFont(UIManager.getFont(UiFontKeys.LABEL_FONT));

    JScrollPane scroll = new JScrollPane(area);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setPreferredSize(new Dimension(460, 240));

    JOptionPane.showMessageDialog(parent, scroll, title, JOptionPane.INFORMATION_MESSAGE);
  }
}
