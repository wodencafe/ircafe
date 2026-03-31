package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettings;
import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;

enum AccentPreset {
  THEME_DEFAULT("Theme default", null),
  IRCAFE_COBALT("IRCafe cobalt", UiProperties.DEFAULT_ACCENT_COLOR),
  INDIGO("Indigo", "#4F46E5"),
  VIOLET("Violet", "#7C3AED"),
  CUSTOM("Custom…", null);

  final String label;
  final String hex;

  AccentPreset(String label, String hex) {
    this.label = label;
    this.hex = hex;
  }

  Color colorOrNull() {
    if (hex == null) return null;
    return SettingsColorSupport.parseHexColorLenient(hex);
  }

  static AccentPreset fromHexOrCustom(String normalizedHex) {
    if (normalizedHex == null || normalizedHex.isBlank()) return CUSTOM;
    for (AccentPreset preset : values()) {
      if (preset.hex != null && preset.hex.equalsIgnoreCase(normalizedHex)) return preset;
    }
    return CUSTOM;
  }
}

final class AccentControls {
  final JCheckBox enabled;
  final JComboBox<AccentPreset> preset;
  final JTextField hex;
  final JButton pick;
  final JButton clear;
  final JSlider strength;
  final JComponent chip;
  final JPanel panel;
  final Runnable applyEnabledState;
  final Runnable syncPresetFromHex;
  final Runnable updateChip;

  AccentControls(
      JCheckBox enabled,
      JComboBox<AccentPreset> preset,
      JTextField hex,
      JButton pick,
      JButton clear,
      JSlider strength,
      JComponent chip,
      JPanel panel,
      Runnable applyEnabledState,
      Runnable syncPresetFromHex,
      Runnable updateChip) {
    this.enabled = enabled;
    this.preset = preset;
    this.hex = hex;
    this.pick = pick;
    this.clear = clear;
    this.strength = strength;
    this.chip = chip;
    this.panel = panel;
    this.applyEnabledState = applyEnabledState;
    this.syncPresetFromHex = syncPresetFromHex;
    this.updateChip = updateChip;
  }
}

final class ColorField {
  final JTextField hex;
  final JButton pick;
  final JButton clear;
  final JPanel panel;
  final Runnable updateIcon;

  ColorField(JTextField hex, JButton pick, JButton clear, JPanel panel, Runnable updateIcon) {
    this.hex = hex;
    this.pick = pick;
    this.clear = clear;
    this.panel = panel;
    this.updateIcon = updateIcon;
  }
}

final class ChatThemeControls {
  final JComboBox<ChatThemeSettings.Preset> preset;
  final ColorField timestamp;
  final ColorField system;
  final ColorField mention;
  final ColorField message;
  final ColorField notice;
  final ColorField action;
  final ColorField error;
  final ColorField presence;
  final JSlider mentionStrength;

  ChatThemeControls(
      JComboBox<ChatThemeSettings.Preset> preset,
      ColorField timestamp,
      ColorField system,
      ColorField mention,
      ColorField message,
      ColorField notice,
      ColorField action,
      ColorField error,
      ColorField presence,
      JSlider mentionStrength) {
    this.preset = preset;
    this.timestamp = timestamp;
    this.system = system;
    this.mention = mention;
    this.message = message;
    this.notice = notice;
    this.action = action;
    this.error = error;
    this.presence = presence;
    this.mentionStrength = mentionStrength;
  }
}

final class ThemeControls {
  final JComboBox<String> combo;

  ThemeControls(JComboBox<String> combo) {
    this.combo = combo;
  }
}

final class FontControls {
  final JComboBox<String> fontFamily;
  final JSpinner fontSize;

  FontControls(JComboBox<String> fontFamily, JSpinner fontSize) {
    this.fontFamily = fontFamily;
    this.fontSize = fontSize;
  }
}

final class AppearanceServerTreeControls {
  final ColorField unreadChannelColor;
  final ColorField highlightChannelColor;
  final JCheckBox preserveDockLayoutBetweenSessions;

  AppearanceServerTreeControls(
      ColorField unreadChannelColor,
      ColorField highlightChannelColor,
      JCheckBox preserveDockLayoutBetweenSessions) {
    this.unreadChannelColor = unreadChannelColor;
    this.highlightChannelColor = highlightChannelColor;
    this.preserveDockLayoutBetweenSessions = preserveDockLayoutBetweenSessions;
  }
}

final class DensityOption {
  final String id;
  final String label;

  DensityOption(String id, String label) {
    this.id = id;
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}

final class TweakControls {
  final JComboBox<DensityOption> density;
  final JSlider cornerRadius;
  final JCheckBox uiFontOverrideEnabled;
  final JComboBox<String> uiFontFamily;
  final JSpinner uiFontSize;
  final Runnable applyUiFontEnabledState;

  TweakControls(
      JComboBox<DensityOption> density,
      JSlider cornerRadius,
      JCheckBox uiFontOverrideEnabled,
      JComboBox<String> uiFontFamily,
      JSpinner uiFontSize,
      Runnable applyUiFontEnabledState) {
    this.density = density;
    this.cornerRadius = cornerRadius;
    this.uiFontOverrideEnabled = uiFontOverrideEnabled;
    this.uiFontFamily = uiFontFamily;
    this.uiFontSize = uiFontSize;
    this.applyUiFontEnabledState = applyUiFontEnabledState;
  }
}
