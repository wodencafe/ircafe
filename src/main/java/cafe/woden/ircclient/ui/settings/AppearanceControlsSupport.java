package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.theme.ThemeAccentSettings;
import cafe.woden.ircclient.ui.settings.theme.ThemeIdUtils;
import cafe.woden.ircclient.ui.settings.theme.ThemeTweakSettings;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import net.miginfocom.swing.MigLayout;

final class AppearanceControlsSupport {
  private AppearanceControlsSupport() {}

  static ThemeControls buildThemeControls(UiSettings current, Map<String, String> themeLabelById) {
    String currentTheme = ThemeIdUtils.normalizeThemeId(current != null ? current.theme() : null);
    String matchKey = null;
    for (String key : themeLabelById.keySet()) {
      if (ThemeIdUtils.sameTheme(key, currentTheme)) {
        matchKey = key;
        break;
      }
    }

    Map<String, String> labelMap = themeLabelById;
    if (matchKey == null && currentTheme != null && !currentTheme.isBlank()) {
      LinkedHashMap<String, String> expanded = new LinkedHashMap<>();
      expanded.put(currentTheme, "Custom: " + currentTheme);
      expanded.putAll(themeLabelById);
      labelMap = expanded;
      matchKey = currentTheme;
    }

    final Map<String, String> rendererMap = labelMap;
    JComboBox<String> theme = new JComboBox<>(rendererMap.keySet().toArray(String[]::new));
    theme.setRenderer(
        (list, value, index, isSelected, cellHasFocus) -> {
          String key = value != null ? value : "";
          JLabel label = new JLabel(rendererMap.getOrDefault(key, key));
          label.setOpaque(true);
          if (isSelected) {
            label.setBackground(list.getSelectionBackground());
            label.setForeground(list.getSelectionForeground());
          } else {
            label.setBackground(list.getBackground());
            label.setForeground(list.getForeground());
          }
          label.setBorder(null);
          return label;
        });
    theme.setSelectedItem(matchKey != null ? matchKey : currentTheme);
    return new ThemeControls(theme);
  }

  static AccentControls buildAccentControls(ThemeAccentSettings current) {
    ThemeAccentSettings effective =
        current != null
            ? current
            : new ThemeAccentSettings(
                UiProperties.DEFAULT_ACCENT_COLOR, UiProperties.DEFAULT_ACCENT_STRENGTH);

    JCheckBox enabled = new JCheckBox("Override theme accent");
    enabled.setToolTipText(
        "If enabled, your chosen accent is blended into the current theme. Changes preview live; Apply/OK saves.");
    enabled.setSelected(effective.enabled());

    JComboBox<AccentPreset> preset = new JComboBox<>(AccentPreset.values());
    preset.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            AccentPreset presetValue =
                (value instanceof AccentPreset typed) ? typed : AccentPreset.THEME_DEFAULT;
            label.setText(presetValue.label);
            Color color = presetValue.colorOrNull();
            label.setIcon(color != null ? new ColorSwatch(color, 12, 12) : null);
            return label;
          }
        });
    preset.setToolTipText("Quick accent presets. 'Custom…' opens a color picker.");

    JTextField hex =
        new JTextField(effective.accentColor() != null ? effective.accentColor() : "", 10);
    hex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#RRGGBB");

    JButton pick = new JButton("Pick…");
    JButton clear = new JButton("Clear");

    JSlider strength = new JSlider(0, 100, effective.strength());
    strength.setPaintTicks(true);
    strength.setMajorTickSpacing(25);
    strength.setMinorTickSpacing(5);
    strength.setSnapToTicks(false);
    strength.setToolTipText("0 = theme default, 100 = fully your chosen accent");

    JLabel chip = new JLabel();
    chip.setOpaque(true);
    chip.setFont(chip.getFont().deriveFont(Math.max(11f, chip.getFont().getSize2D() - 1f)));
    chip.putClientProperty(
        FlatClientProperties.STYLE,
        "border: 2,8,2,8, $Component.borderColor, 1, 999; background: $Panel.background;");

    Runnable updatePickIcon =
        () -> {
          Color color = SettingsColorSupport.parseHexColorLenient(hex.getText());
          if (color != null) {
            pick.setIcon(new ColorSwatch(color, 14, 14));
            pick.setText("");
            pick.setToolTipText(SettingsColorSupport.toHex(color));
          } else {
            pick.setIcon(null);
            pick.setText("Pick…");
            pick.setToolTipText("Pick an accent color");
          }
        };

    Runnable updateChip =
        () -> {
          boolean active = enabled.isSelected();
          Color background;
          String text;
          String tooltip;

          if (!active) {
            background = UIManager.getColor("Component.accentColor");
            if (background == null) background = UIManager.getColor("Component.focusColor");
            if (background == null) background = UIManager.getColor("Focus.color");
            if (background == null) background = UIManager.getColor("Actions.Blue");
            if (background == null) background = UIManager.getColor("Button.default.focusColor");
            if (background == null) {
              background =
                  SettingsColorSupport.parseHexColorLenient(UiProperties.DEFAULT_ACCENT_COLOR);
            }
            if (background == null) background = new Color(0x2D6BFF);
            text = "Theme";
            tooltip = "Theme accent • " + strength.getValue() + "%";
          } else {
            String raw = hex.getText() != null ? hex.getText().trim() : "";
            Color chosen = SettingsColorSupport.parseHexColorLenient(raw);
            background =
                chosen != null
                    ? chosen
                    : SettingsColorSupport.parseHexColorLenient(UiProperties.DEFAULT_ACCENT_COLOR);
            if (background == null) background = new Color(0x2D6BFF);

            AccentPreset selected = (AccentPreset) preset.getSelectedItem();
            if (selected == null) {
              selected = AccentPreset.fromHexOrCustom(ThemeAccentSettings.normalizeHexOrNull(raw));
            }
            text =
                switch (selected) {
                  case IRCAFE_COBALT -> "Cobalt";
                  case INDIGO -> "Indigo";
                  case VIOLET -> "Violet";
                  case CUSTOM -> "Custom";
                  case THEME_DEFAULT -> "Theme";
                };
            tooltip =
                "Accent override: "
                    + (chosen != null ? SettingsColorSupport.toHex(chosen) : "(invalid)")
                    + " • "
                    + strength.getValue()
                    + "%";
          }

          chip.setText(text);
          chip.setBackground(background);
          chip.setForeground(SettingsColorSupport.contrastTextColor(background));
          chip.setToolTipText(tooltip);
        };

    java.util.concurrent.atomic.AtomicBoolean adjusting =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicReference<AccentPreset> lastPreset =
        new java.util.concurrent.atomic.AtomicReference<>();

    Runnable syncPresetFromHex =
        () -> {
          if (adjusting.get()) return;
          if (!enabled.isSelected()) {
            adjusting.set(true);
            try {
              preset.setSelectedItem(AccentPreset.THEME_DEFAULT);
            } finally {
              adjusting.set(false);
            }
            lastPreset.set(AccentPreset.THEME_DEFAULT);
            return;
          }

          String normalized = ThemeAccentSettings.normalizeHexOrNull(hex.getText());
          AccentPreset next = AccentPreset.fromHexOrCustom(normalized);
          adjusting.set(true);
          try {
            preset.setSelectedItem(next);
          } finally {
            adjusting.set(false);
          }
          lastPreset.set(next);
        };

    if (!effective.enabled()) {
      preset.setSelectedItem(AccentPreset.THEME_DEFAULT);
      lastPreset.set(AccentPreset.THEME_DEFAULT);
    } else {
      String normalized = ThemeAccentSettings.normalizeHexOrNull(effective.accentColor());
      AccentPreset initial = AccentPreset.fromHexOrCustom(normalized);
      preset.setSelectedItem(initial);
      lastPreset.set(initial);
    }

    Runnable applyEnabledState =
        () -> {
          boolean active = enabled.isSelected();
          hex.setEnabled(active);
          pick.setEnabled(active);
          clear.setEnabled(active);
          strength.setEnabled(active);
          if (!active) {
            pick.setIcon(null);
            pick.setText("Pick…");
          } else {
            updatePickIcon.run();
          }
          updateChip.run();
        };

    pick.addActionListener(
        event -> {
          Color initial = SettingsColorSupport.parseHexColorLenient(hex.getText());
          Color chosen =
              SettingsColorPickerDialogSupport.showColorPickerDialog(
                  SwingUtilities.getWindowAncestor(pick),
                  "Choose Accent Color",
                  initial,
                  SettingsColorSupport.preferredPreviewBackground());
          if (chosen != null) {
            hex.setText(SettingsColorSupport.toHex(chosen));
            updatePickIcon.run();
            syncPresetFromHex.run();
            updateChip.run();
          }
        });

    clear.addActionListener(
        event -> {
          adjusting.set(true);
          try {
            enabled.setSelected(false);
            preset.setSelectedItem(AccentPreset.THEME_DEFAULT);
            hex.setText("");
          } finally {
            adjusting.set(false);
          }
          updatePickIcon.run();
          applyEnabledState.run();
          updateChip.run();
        });

    preset.addActionListener(
        event -> {
          if (adjusting.get()) return;
          AccentPreset selected = (AccentPreset) preset.getSelectedItem();
          if (selected == null) return;

          AccentPreset previous =
              lastPreset.get() != null ? lastPreset.get() : AccentPreset.THEME_DEFAULT;
          boolean previousEnabled = enabled.isSelected();
          String previousHex = hex.getText();

          adjusting.set(true);
          try {
            if (selected == AccentPreset.THEME_DEFAULT) {
              enabled.setSelected(false);
            } else if (selected == AccentPreset.CUSTOM) {
              enabled.setSelected(true);
            } else {
              enabled.setSelected(true);
              if (selected.hex != null) {
                hex.setText(selected.hex);
              }
            }
          } finally {
            adjusting.set(false);
          }

          applyEnabledState.run();

          if (selected == AccentPreset.CUSTOM) {
            Color initial = SettingsColorSupport.parseHexColorLenient(hex.getText());
            Color chosen =
                SettingsColorPickerDialogSupport.showColorPickerDialog(
                    SwingUtilities.getWindowAncestor(preset),
                    "Choose Accent Color",
                    initial,
                    SettingsColorSupport.preferredPreviewBackground());
            if (chosen == null) {
              adjusting.set(true);
              try {
                preset.setSelectedItem(previous);
                enabled.setSelected(previousEnabled);
                hex.setText(previousHex);
              } finally {
                adjusting.set(false);
              }
              lastPreset.set(previous);
              applyEnabledState.run();
              updateChip.run();
              return;
            }
            hex.setText(SettingsColorSupport.toHex(chosen));
            updatePickIcon.run();
            syncPresetFromHex.run();
            updateChip.run();
          } else {
            lastPreset.set(selected);
            updateChip.run();
          }
        });

    enabled.addActionListener(event -> applyEnabledState.run());
    enabled.addActionListener(event -> syncPresetFromHex.run());
    enabled.addActionListener(event -> updateChip.run());
    hex.getDocument()
        .addDocumentListener(
            new PreferencesDialog.SimpleDocListener(
                () -> {
                  updatePickIcon.run();
                  syncPresetFromHex.run();
                  updateChip.run();
                }));
    strength.addChangeListener(event -> updateChip.run());

    JPanel row = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]"));
    row.setOpaque(false);

    JPanel top = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]10[grow,fill]", "[]"));
    top.setOpaque(false);
    top.add(enabled, "growx");
    top.add(preset, "growx, wmin 0");
    row.add(top, "growx, wrap");

    JPanel bottom = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]6[]", "[]"));
    bottom.setOpaque(false);
    bottom.add(hex, "w 110!");
    bottom.add(pick);
    bottom.add(clear);
    row.add(bottom, "growx");

    applyEnabledState.run();
    updateChip.run();

    return new AccentControls(
        enabled,
        preset,
        hex,
        pick,
        clear,
        strength,
        chip,
        row,
        applyEnabledState,
        syncPresetFromHex,
        updateChip);
  }

  static ChatThemeControls buildChatThemeControls(ChatThemeSettings current) {
    JComboBox<ChatThemeSettings.Preset> preset = new JComboBox<>(ChatThemeSettings.Preset.values());
    preset.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            ChatThemeSettings.Preset presetValue =
                (value instanceof ChatThemeSettings.Preset typed)
                    ? typed
                    : ChatThemeSettings.Preset.DEFAULT;
            label.setText(
                switch (presetValue) {
                  case DEFAULT -> "Default (follow theme)";
                  case SOFT -> "Soft";
                  case ACCENTED -> "Accented";
                  case HIGH_CONTRAST -> "High contrast";
                });
            return label;
          }
        });
    preset.setSelectedItem(current != null ? current.preset() : ChatThemeSettings.Preset.DEFAULT);

    ColorField timestamp =
        buildOptionalColorField(
            current != null ? current.timestampColor() : null, "Pick a timestamp color");
    ColorField system =
        buildOptionalColorField(
            current != null ? current.systemColor() : null, "Pick a system/status color");
    ColorField mention =
        buildOptionalColorField(
            current != null ? current.mentionBgColor() : null, "Pick a mention highlight color");
    ColorField message =
        buildOptionalColorField(
            current != null ? current.messageColor() : null, "Pick a user message color");
    ColorField notice =
        buildOptionalColorField(
            current != null ? current.noticeColor() : null, "Pick a notice message color");
    ColorField action =
        buildOptionalColorField(
            current != null ? current.actionColor() : null, "Pick an action message color");
    ColorField error =
        buildOptionalColorField(
            current != null ? current.errorColor() : null, "Pick an error message color");
    ColorField presence =
        buildOptionalColorField(
            current != null ? current.presenceColor() : null, "Pick a presence message color");

    int strength = current != null ? current.mentionStrength() : 35;
    JSlider mentionStrength = new JSlider(0, 100, Math.max(0, Math.min(100, strength)));
    mentionStrength.setMajorTickSpacing(25);
    mentionStrength.setMinorTickSpacing(5);
    mentionStrength.setPaintTicks(false);
    mentionStrength.setPaintLabels(false);
    mentionStrength.setToolTipText(
        "How strong the mention highlight is when using the preset highlight (0-100). Defaults to 35.");

    return new ChatThemeControls(
        preset,
        timestamp,
        system,
        mention,
        message,
        notice,
        action,
        error,
        presence,
        mentionStrength);
  }

  static ColorField buildOptionalColorField(String initialHex, String pickerTitle) {
    JTextField hex = new JTextField();
    hex.setColumns(10);
    hex.setToolTipText("Leave blank to use the preset/theme default.");
    hex.setText(initialHex != null ? initialHex.trim() : "");

    JButton pick = new JButton("Pick");
    JButton clear = new JButton("Clear");

    Runnable updateIcon =
        () -> {
          Color color = SettingsColorSupport.parseHexColorLenient(hex.getText());
          if (color == null) {
            pick.setIcon(null);
            pick.setText("Pick");
          } else {
            pick.setText("");
            pick.setIcon(SettingsColorSupport.createColorSwatchIcon(color, 14, 14));
          }
        };

    pick.addActionListener(
        event -> {
          Color initial = SettingsColorSupport.parseHexColorLenient(hex.getText());
          if (initial == null) {
            initial = UIManager.getColor("Label.foreground");
          }
          Color chosen =
              SettingsColorPickerDialogSupport.showColorPickerDialog(
                  SwingUtilities.getWindowAncestor(pick),
                  pickerTitle,
                  initial,
                  SettingsColorSupport.preferredPreviewBackground());
          if (chosen != null) {
            hex.setText(SettingsColorSupport.toHex(chosen));
            updateIcon.run();
          }
        });

    clear.addActionListener(
        event -> {
          hex.setText("");
          updateIcon.run();
        });

    hex.getDocument().addDocumentListener(new PreferencesDialog.SimpleDocListener(updateIcon));

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx", "[grow]6[]6[]"));
    panel.add(hex, "growx");
    panel.add(pick);
    panel.add(clear);

    updateIcon.run();
    return new ColorField(hex, pick, clear, panel, updateIcon);
  }

  static TweakControls buildTweakControls(
      ThemeTweakSettings current, List<AutoCloseable> closeables) {
    ThemeTweakSettings effective =
        current != null
            ? current
            : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);

    DensityOption[] options =
        new DensityOption[] {
          new DensityOption("auto", "Auto (theme default)"),
          new DensityOption("compact", "Compact"),
          new DensityOption("cozy", "Cozy"),
          new DensityOption("spacious", "Spacious")
        };

    JComboBox<DensityOption> density = new JComboBox<>(options);
    density.setToolTipText(PreferencesDialog.DENSITY_TOOLTIP);
    String currentId = effective.densityId();
    for (DensityOption option : options) {
      if (option != null && option.id.equalsIgnoreCase(currentId)) {
        density.setSelectedItem(option);
        break;
      }
    }

    JSlider cornerRadius = new JSlider(0, 20, effective.cornerRadius());
    cornerRadius.setPaintTicks(true);
    cornerRadius.setMajorTickSpacing(5);
    cornerRadius.setMinorTickSpacing(1);
    cornerRadius.setToolTipText(PreferencesDialog.CORNER_RADIUS_TOOLTIP);

    JComboBox<String> uiFontFamily = new JComboBox<>(availableFontFamiliesSorted());
    uiFontFamily.setEditable(true);
    uiFontFamily.setSelectedItem(effective.uiFontFamily());
    uiFontFamily.setToolTipText(PreferencesDialog.UI_FONT_OVERRIDE_TOOLTIP);
    applyEditableComboEditorPalette(uiFontFamily);
    uiFontFamily.addPropertyChangeListener(
        "UI", event -> applyEditableComboEditorPalette(uiFontFamily));
    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateComboBoxSelection(uiFontFamily));
      } catch (Exception ignored) {
      }
    }

    JSpinner uiFontSize =
        PreferencesDialog.numberSpinner(effective.uiFontSize(), 8, 48, 1, closeables);
    uiFontSize.setToolTipText(PreferencesDialog.UI_FONT_OVERRIDE_TOOLTIP);

    JCheckBox uiFontOverrideEnabled = new JCheckBox("Override system UI font");
    uiFontOverrideEnabled.setSelected(effective.uiFontOverrideEnabled());
    uiFontOverrideEnabled.setToolTipText(PreferencesDialog.UI_FONT_OVERRIDE_TOOLTIP);

    Runnable applyUiFontEnabledState =
        () -> {
          boolean enabled = uiFontOverrideEnabled.isSelected();
          uiFontFamily.setEnabled(enabled);
          uiFontSize.setEnabled(enabled);
        };
    applyUiFontEnabledState.run();

    return new TweakControls(
        density,
        cornerRadius,
        uiFontOverrideEnabled,
        uiFontFamily,
        uiFontSize,
        applyUiFontEnabledState);
  }

  static FontControls buildFontControls(UiSettings current, List<AutoCloseable> closeables) {
    JComboBox<String> fontFamily = new JComboBox<>(availableFontFamiliesSorted());
    fontFamily.setEditable(true);
    fontFamily.setSelectedItem(current.chatFontFamily());
    applyEditableComboEditorPalette(fontFamily);
    fontFamily.addPropertyChangeListener(
        "UI", event -> applyEditableComboEditorPalette(fontFamily));

    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateComboBoxSelection(fontFamily));
      } catch (Exception ignored) {
      }
    } else {
      try {
        MouseWheelDecorator.decorateComboBoxSelection(fontFamily);
      } catch (Exception ignored) {
      }
    }

    Font baseFont = fontFamily.getFont();
    String sampleText = "AaBbYyZz 0123";
    fontFamily.setRenderer(
        new javax.swing.ListCellRenderer<>() {
          private final JPanel panel = new JPanel(new BorderLayout(8, 0));
          private final JLabel left = new JLabel();
          private final JLabel right = new JLabel();

          {
            panel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            left.setOpaque(false);
            right.setOpaque(false);
            right.setHorizontalAlignment(SwingConstants.RIGHT);
            panel.add(left, BorderLayout.WEST);
            panel.add(right, BorderLayout.EAST);
            panel.setOpaque(true);
          }

          @Override
          public Component getListCellRendererComponent(
              JList<? extends String> list,
              String value,
              int index,
              boolean isSelected,
              boolean cellHasFocus) {
            String family = value != null ? value : "";

            Color background;
            Color foreground;
            if (list != null) {
              background = isSelected ? list.getSelectionBackground() : list.getBackground();
              foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
            } else {
              background = UIManager.getColor("ComboBox.background");
              foreground = UIManager.getColor("ComboBox.foreground");
            }
            panel.setBackground(background);
            left.setForeground(foreground);
            right.setForeground(foreground);

            left.setText(family);
            left.setFont(baseFont);

            String preview = "";
            Font previewFont = baseFont;
            if (!family.isBlank()) {
              Font candidate = new Font(family, baseFont.getStyle(), baseFont.getSize());
              if (candidate.getFamily().equalsIgnoreCase(family)
                  || candidate.getName().equalsIgnoreCase(family)) {
                if (candidate.canDisplayUpTo(sampleText) == -1) {
                  preview = sampleText;
                  previewFont = candidate;
                }
              }
            }
            right.setText(preview);
            right.setFont(previewFont);

            return panel;
          }
        });

    JSpinner fontSize =
        PreferencesDialog.numberSpinner(current.chatFontSize(), 8, 48, 1, closeables);
    return new FontControls(fontFamily, fontSize);
  }

  static AppearanceServerTreeControls buildServerTreeControls(UiSettings current) {
    ColorField unreadChannelColor =
        buildOptionalColorField(
            current != null ? current.serverTreeUnreadChannelColor() : null,
            "Pick a channel color for unread messages");
    ColorField highlightChannelColor =
        buildOptionalColorField(
            current != null ? current.serverTreeHighlightChannelColor() : null,
            "Pick a channel color for unread highlights/mentions");
    JCheckBox preserveDockLayoutBetweenSessions =
        new JCheckBox("Preserve dock layout between restarts");
    preserveDockLayoutBetweenSessions.setToolTipText(
        "When enabled, dock positions/splits are restored on next app launch.");
    preserveDockLayoutBetweenSessions.setSelected(
        current != null && current.preserveDockLayoutBetweenSessions());
    return new AppearanceServerTreeControls(
        unreadChannelColor, highlightChannelColor, preserveDockLayoutBetweenSessions);
  }

  private static String[] availableFontFamiliesSorted() {
    String[] families =
        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Arrays.sort(families, String.CASE_INSENSITIVE_ORDER);
    return families;
  }

  private static void applyEditableComboEditorPalette(JComboBox<?> combo) {
    if (combo == null || !combo.isEditable()) return;
    ComboBoxEditor editor = combo.getEditor();
    if (editor == null) return;

    Component editorComponent = editor.getEditorComponent();
    if (!(editorComponent instanceof JTextField field)) return;

    Color background =
        firstUiColor("ComboBox.background", "TextField.background", "TextComponent.background");
    Color foreground =
        firstUiColor("ComboBox.foreground", "TextField.foreground", "Label.foreground");
    Color selectionBackground =
        firstUiColor(
            "ComboBox.selectionBackground",
            "TextComponent.selectionBackground",
            "List.selectionBackground");
    Color selectionForeground =
        firstUiColor(
            "ComboBox.selectionForeground",
            "TextComponent.selectionForeground",
            "List.selectionForeground");

    if (background != null) field.setBackground(asUiResource(background));
    if (foreground != null) {
      Color uiForeground = asUiResource(foreground);
      field.setForeground(uiForeground);
      field.setCaretColor(uiForeground);
    }
    if (selectionBackground != null) field.setSelectionColor(asUiResource(selectionBackground));
    if (selectionForeground != null) {
      field.setSelectedTextColor(asUiResource(selectionForeground));
    }
  }

  private static Color firstUiColor(String... keys) {
    if (keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      Color color = UIManager.getColor(key);
      if (color != null) return color;
    }
    return null;
  }

  private static Color asUiResource(Color color) {
    if (color == null || color instanceof ColorUIResource) return color;
    return new ColorUIResource(color);
  }
}
