package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.theme.ThemeTweakSettings;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import net.miginfocom.swing.MigLayout;

final class AppearancePanelSupport {
  private AppearancePanelSupport() {}

  static JPanel buildPanel(
      ThemeControls theme,
      AccentControls accent,
      ChatThemeControls chatTheme,
      FontControls fonts,
      TweakControls tweaks,
      AppearanceServerTreeControls serverTree) {
    JPanel form =
        new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]8[grow,push]8[]"));

    form.add(PreferencesDialog.tabTitle("Appearance"), "growx, wmin 0, wrap");

    JTabbedPane appearanceTabs = new JTabbedPane();
    appearanceTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    appearanceTabs.addTab(
        "Theme", PreferencesDialog.padSubTab(buildThemeSubTab(theme, accent, tweaks)));
    appearanceTabs.addTab("UI font", PreferencesDialog.padSubTab(buildUiFontSubTab(tweaks)));
    appearanceTabs.addTab(
        "Chat colors", PreferencesDialog.padSubTab(buildChatColorsSubTab(chatTheme)));
    appearanceTabs.addTab("Chat text", PreferencesDialog.padSubTab(buildChatTextSubTab(fonts)));
    appearanceTabs.addTab(
        "Server tree", PreferencesDialog.padSubTab(buildServerTreeSubTab(serverTree)));
    form.add(appearanceTabs, "grow, push, wmin 0");

    JButton reset = new JButton("Reset to defaults");
    reset.setToolTipText(
        "Revert the appearance controls to default values. Changes preview live; Apply/OK saves.");
    reset.addActionListener(
        event -> {
          theme.combo.setSelectedItem("darcula");
          fonts.fontFamily.setSelectedItem("Monospaced");
          fonts.fontSize.setValue(12);
          accent.preset.setSelectedItem(AccentPreset.IRCAFE_COBALT);
          accent.enabled.setSelected(true);
          accent.hex.setText(UiProperties.DEFAULT_ACCENT_COLOR);
          accent.strength.setValue(UiProperties.DEFAULT_ACCENT_STRENGTH);

          for (int i = 0; i < tweaks.density.getItemCount(); i++) {
            DensityOption option = tweaks.density.getItemAt(i);
            if (option != null && "auto".equalsIgnoreCase(option.id)) {
              tweaks.density.setSelectedIndex(i);
              break;
            }
          }
          tweaks.cornerRadius.setValue(10);
          tweaks.uiFontOverrideEnabled.setSelected(false);
          tweaks.uiFontFamily.setSelectedItem(ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY);
          tweaks.uiFontSize.setValue(ThemeTweakSettings.DEFAULT_UI_FONT_SIZE);

          chatTheme.preset.setSelectedItem(ChatThemeSettings.Preset.DEFAULT);
          clearColorField(chatTheme.timestamp);
          clearColorField(chatTheme.system);
          clearColorField(chatTheme.mention);
          clearColorField(chatTheme.message);
          clearColorField(chatTheme.notice);
          clearColorField(chatTheme.action);
          clearColorField(chatTheme.error);
          clearColorField(chatTheme.presence);
          chatTheme.mentionStrength.setValue(35);

          clearColorField(serverTree.unreadChannelColor);
          clearColorField(serverTree.highlightChannelColor);
          serverTree.preserveDockLayoutBetweenSessions.setSelected(false);

          accent.applyEnabledState.run();
          accent.syncPresetFromHex.run();
          tweaks.applyUiFontEnabledState.run();
        });
    form.add(reset, "split 2, alignx left");
    form.add(
        PreferencesDialog.helpText("Changes preview live. Use Apply or OK to save."),
        "alignx left, gapleft 12, growx, wmin 0");

    return form;
  }

  private static JPanel buildThemeSubTab(
      ThemeControls theme, AccentControls accent, TweakControls tweaks) {
    JPanel panel =
        new JPanel(
            new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]6[]6[]6[]6[]"));
    panel.setOpaque(false);

    panel.add(PreferencesDialog.sectionTitle("Look & feel"), "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Theme"));
    panel.add(theme.combo, "growx");

    JPanel accentLabel = new JPanel(new MigLayout("insets 0", "[]6[]", "[]"));
    accentLabel.setOpaque(false);
    accentLabel.add(new JLabel("Accent"));
    accentLabel.add(accent.chip);
    panel.add(accentLabel);
    panel.add(accent.panel, "growx");

    panel.add(new JLabel("Accent strength"));
    panel.add(accent.strength, "growx");

    panel.add(new JLabel("Density"));
    panel.add(tweaks.density, "growx");

    panel.add(new JLabel("Corner radius"));
    panel.add(tweaks.cornerRadius, "growx");

    JTextArea tweakHint = PreferencesDialog.subtleInfoText();
    tweakHint.setText("Density and corner radius are available for FlatLaf-based themes.");
    panel.add(new JLabel(""));
    panel.add(tweakHint, "growx, wmin 0");

    return panel;
  }

  private static JPanel buildUiFontSubTab(TweakControls tweaks) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]6[]6[]"));
    panel.setOpaque(false);

    panel.add(PreferencesDialog.sectionTitle("UI text"), "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Font override"));
    panel.add(tweaks.uiFontOverrideEnabled, "growx");
    panel.add(new JLabel("Font family"));
    panel.add(tweaks.uiFontFamily, "growx");
    panel.add(new JLabel("Font size"));
    panel.add(tweaks.uiFontSize, "w 110!");

    JTextArea uiFontHint = PreferencesDialog.subtleInfoText();
    uiFontHint.setText(
        "Applies globally to menus, dialogs, tabs, forms, and controls for all themes.");
    panel.add(new JLabel(""));
    panel.add(uiFontHint, "growx, wmin 0");

    return panel;
  }

  private static JPanel buildChatColorsSubTab(ChatThemeControls chatTheme) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]8[]12[]8[]"));
    panel.setOpaque(false);

    panel.add(PreferencesDialog.sectionTitle("Palette"), "growx, wmin 0, wrap");
    panel.add(buildChatThemePaletteSubTab(chatTheme), "growx, wmin 0, wrap");

    panel.add(PreferencesDialog.sectionTitle("Message colors"), "growx, wmin 0, wrap");
    panel.add(buildChatMessageColorsSubTab(chatTheme), "growx, wmin 0");

    return panel;
  }

  private static JPanel buildChatTextSubTab(FontControls fonts) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]6[]"));
    panel.setOpaque(false);

    panel.add(PreferencesDialog.sectionTitle("Chat text"), "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Font family"));
    panel.add(fonts.fontFamily, "growx");
    panel.add(new JLabel("Font size"));
    panel.add(fonts.fontSize, "w 110!");

    return panel;
  }

  private static JPanel buildServerTreeSubTab(AppearanceServerTreeControls serverTree) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]6[]"));
    panel.setOpaque(false);

    panel.add(PreferencesDialog.sectionTitle("Server tree"), "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Unread channel color"));
    panel.add(serverTree.unreadChannelColor.panel, "growx");
    panel.add(new JLabel("Highlight channel color"));
    panel.add(serverTree.highlightChannelColor.panel, "growx");
    panel.add(new JLabel("Dock layout"));
    panel.add(serverTree.preserveDockLayoutBetweenSessions, "growx");

    JTextArea hint = PreferencesDialog.subtleInfoText();
    hint.setText(
        "Leave colors blank to use theme defaults. Dock layout restore applies on next launch.");
    panel.add(new JLabel(""));
    panel.add(hint, "growx, wmin 0");

    return panel;
  }

  private static JPanel buildChatThemePaletteSubTab(ChatThemeControls chatTheme) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    panel.setOpaque(false);

    panel.add(new JLabel("Chat theme preset"));
    panel.add(chatTheme.preset, "growx");

    panel.add(new JLabel("Timestamp color"));
    panel.add(chatTheme.timestamp.panel, "growx");

    panel.add(new JLabel("Mention highlight"));
    panel.add(chatTheme.mention.panel, "growx");

    panel.add(new JLabel("Mention strength"));
    panel.add(chatTheme.mentionStrength, "growx");

    panel.add(new JLabel(""));
    panel.add(
        PreferencesDialog.helpText(
            "Use Message colors when you want to override specific line types."),
        "growx, wmin 0");
    return panel;
  }

  private static JPanel buildChatMessageColorsSubTab(ChatThemeControls chatTheme) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]6[]6[]6[]"));
    panel.setOpaque(false);

    panel.add(new JLabel("Server/system"));
    panel.add(chatTheme.system.panel, "growx");

    panel.add(new JLabel("User messages"));
    panel.add(chatTheme.message.panel, "growx");

    panel.add(new JLabel("Notice messages"));
    panel.add(chatTheme.notice.panel, "growx");

    panel.add(new JLabel("Action messages"));
    panel.add(chatTheme.action.panel, "growx");

    panel.add(new JLabel("Presence messages"));
    panel.add(chatTheme.presence.panel, "growx");

    panel.add(new JLabel("Error messages"));
    panel.add(chatTheme.error.panel, "growx");

    panel.add(new JLabel(""));
    panel.add(
        PreferencesDialog.helpText("Leave any field blank to use the theme default."),
        "growx, wmin 0");

    return panel;
  }

  private static void clearColorField(ColorField colorField) {
    colorField.hex.setText("");
    colorField.updateIcon.run();
  }
}
