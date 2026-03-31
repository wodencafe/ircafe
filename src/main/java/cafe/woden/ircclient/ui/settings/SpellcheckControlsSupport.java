package cafe.woden.ircclient.ui.settings;

import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;

final class SpellcheckControlsSupport {
  private SpellcheckControlsSupport() {}

  static SpellcheckControls buildControls(SpellcheckSettings settings) {
    SpellcheckSettings initial = settings != null ? settings : SpellcheckSettings.defaults();

    javax.swing.JCheckBox enabled =
        new javax.swing.JCheckBox("Enable spell checking in the input bar");
    enabled.setSelected(initial.enabled());
    enabled.setToolTipText(
        "When enabled, IRCafe checks words in your current draft and can provide corrections.");

    javax.swing.JCheckBox underline =
        new javax.swing.JCheckBox("Highlight misspelled words while typing");
    underline.setSelected(initial.underlineEnabled());
    underline.setToolTipText(
        "When enabled, misspelled words are highlighted directly in the input field.");

    javax.swing.JCheckBox suggestOnTab =
        new javax.swing.JCheckBox("Include dictionary suggestions in Tab completion");
    suggestOnTab.setSelected(initial.suggestOnTabEnabled());
    suggestOnTab.setToolTipText(
        "When enabled, Tab completion can suggest dictionary words and spelling corrections.");

    javax.swing.JCheckBox hoverSuggestions =
        new javax.swing.JCheckBox("Show hover correction popup for misspelled words");
    hoverSuggestions.setSelected(initial.hoverSuggestionsEnabled());
    hoverSuggestions.setToolTipText(
        "When enabled, hovering over a misspelled word shows quick correction suggestions.");

    SpellcheckLanguageOption[] languages =
        new SpellcheckLanguageOption[] {
          new SpellcheckLanguageOption("en-US", "English (US)"),
          new SpellcheckLanguageOption("en-GB", "English (UK)")
        };
    JComboBox<SpellcheckLanguageOption> languageTag = new JComboBox<>(languages);
    languageTag.setToolTipText("Choose the dictionary language used for spell checking.");
    languageTag.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel c =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            if (value instanceof SpellcheckLanguageOption o) {
              c.setText(o.label());
            }
            return c;
          }
        });
    String initialLanguage = SpellcheckSettings.normalizeLanguageTag(initial.languageTag());
    for (SpellcheckLanguageOption option : languages) {
      if (option.id().equalsIgnoreCase(initialLanguage)) {
        languageTag.setSelectedItem(option);
        break;
      }
    }

    JTextArea customDictionary = new JTextArea(4, 24);
    customDictionary.setLineWrap(true);
    customDictionary.setWrapStyleWord(true);
    customDictionary.setText(String.join("\n", initial.customDictionary()));
    customDictionary.setToolTipText(
        "One word per line. Words in this list are treated as correct and not highlighted.");
    JScrollPane customScroll =
        new JScrollPane(
            customDictionary,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    SpellcheckPresetOption[] presets =
        new SpellcheckPresetOption[] {
          new SpellcheckPresetOption(
              SpellcheckSettings.COMPLETION_PRESET_ANDROID_LIKE, "Android-like (default)"),
          new SpellcheckPresetOption(SpellcheckSettings.COMPLETION_PRESET_STANDARD, "Standard"),
          new SpellcheckPresetOption(
              SpellcheckSettings.COMPLETION_PRESET_CONSERVATIVE, "Conservative"),
          new SpellcheckPresetOption(SpellcheckSettings.COMPLETION_PRESET_AGGRESSIVE, "Aggressive"),
          new SpellcheckPresetOption(SpellcheckSettings.COMPLETION_PRESET_CUSTOM, "Custom")
        };
    JComboBox<SpellcheckPresetOption> completionPreset = new JComboBox<>(presets);
    completionPreset.setToolTipText(
        "Controls how strongly TAB completion prefers word completions.");
    completionPreset.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel c =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            if (value instanceof SpellcheckPresetOption o) {
              c.setText(o.label());
            }
            return c;
          }
        });
    String initialPreset = SpellcheckSettings.normalizeCompletionPreset(initial.completionPreset());
    for (SpellcheckPresetOption option : presets) {
      if (option.id().equalsIgnoreCase(initialPreset)) {
        completionPreset.setSelectedItem(option);
        break;
      }
    }

    JSpinner customMinPrefixCompletionTokenLength =
        new JSpinner(
            new SpinnerNumberModel(
                initial.customMinPrefixCompletionTokenLength(),
                SpellcheckSettings.MIN_PREFIX_COMPLETION_TOKEN_LENGTH_MIN,
                SpellcheckSettings.MIN_PREFIX_COMPLETION_TOKEN_LENGTH_MAX,
                1));
    customMinPrefixCompletionTokenLength.setToolTipText(
        "Minimum typed letters before prefix completions are considered.");

    JSpinner customMaxPrefixCompletionExtraChars =
        new JSpinner(
            new SpinnerNumberModel(
                initial.customMaxPrefixCompletionExtraChars(),
                SpellcheckSettings.MAX_PREFIX_COMPLETION_EXTRA_CHARS_MIN,
                SpellcheckSettings.MAX_PREFIX_COMPLETION_EXTRA_CHARS_MAX,
                1));
    customMaxPrefixCompletionExtraChars.setToolTipText(
        "Maximum additional letters allowed for a completion candidate.");

    JSpinner customMaxPrefixLexiconCandidates =
        new JSpinner(
            new SpinnerNumberModel(
                initial.customMaxPrefixLexiconCandidates(),
                SpellcheckSettings.MAX_PREFIX_LEXICON_CANDIDATES_MIN,
                SpellcheckSettings.MAX_PREFIX_LEXICON_CANDIDATES_MAX,
                8));
    customMaxPrefixLexiconCandidates.setToolTipText(
        "Upper bound for dictionary candidates considered for prefix completion.");

    JSpinner customPrefixCompletionBonusScore =
        new JSpinner(
            new SpinnerNumberModel(
                initial.customPrefixCompletionBonusScore(),
                SpellcheckSettings.PREFIX_COMPLETION_BONUS_SCORE_MIN,
                SpellcheckSettings.PREFIX_COMPLETION_BONUS_SCORE_MAX,
                10));
    customPrefixCompletionBonusScore.setToolTipText(
        "Higher values make exact prefix completions rank more aggressively.");

    JSpinner customSourceOrderWeight =
        new JSpinner(
            new SpinnerNumberModel(
                initial.customSourceOrderWeight(),
                SpellcheckSettings.SOURCE_ORDER_WEIGHT_MIN,
                SpellcheckSettings.SOURCE_ORDER_WEIGHT_MAX,
                1));
    customSourceOrderWeight.setToolTipText(
        "Penalty for later suggestions from upstream spelling results.");

    JPanel customKnobsPanel =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2, hidemode 3", "[right]8[grow,fill]", "[]2[]2[]2[]2[]"));
    customKnobsPanel.setOpaque(false);
    customKnobsPanel.add(new JLabel("Min prefix length"));
    customKnobsPanel.add(customMinPrefixCompletionTokenLength, "w 120!");
    customKnobsPanel.add(new JLabel("Max completion tail"));
    customKnobsPanel.add(customMaxPrefixCompletionExtraChars, "w 120!");
    customKnobsPanel.add(new JLabel("Lexicon candidate cap"));
    customKnobsPanel.add(customMaxPrefixLexiconCandidates, "w 120!");
    customKnobsPanel.add(new JLabel("Prefix bonus"));
    customKnobsPanel.add(customPrefixCompletionBonusScore, "w 120!");
    customKnobsPanel.add(new JLabel("Source-order weight"));
    customKnobsPanel.add(customSourceOrderWeight, "w 120!");

    Runnable syncEnabled =
        () -> {
          boolean on = enabled.isSelected();
          boolean suggestionsOn = on && suggestOnTab.isSelected();
          boolean hoverOn = on && underline.isSelected();
          boolean customSelected =
              SpellcheckSettings.COMPLETION_PRESET_CUSTOM.equals(
                  completionPresetValue(completionPreset));
          underline.setEnabled(on);
          suggestOnTab.setEnabled(on);
          hoverSuggestions.setEnabled(hoverOn);
          languageTag.setEnabled(on);
          customDictionary.setEnabled(on);
          completionPreset.setEnabled(suggestionsOn);
          customKnobsPanel.setVisible(customSelected);
          customMinPrefixCompletionTokenLength.setEnabled(suggestionsOn && customSelected);
          customMaxPrefixCompletionExtraChars.setEnabled(suggestionsOn && customSelected);
          customMaxPrefixLexiconCandidates.setEnabled(suggestionsOn && customSelected);
          customPrefixCompletionBonusScore.setEnabled(suggestionsOn && customSelected);
          customSourceOrderWeight.setEnabled(suggestionsOn && customSelected);
          customKnobsPanel.revalidate();
          customKnobsPanel.repaint();
        };
    enabled.addActionListener(e -> syncEnabled.run());
    suggestOnTab.addActionListener(e -> syncEnabled.run());
    underline.addActionListener(e -> syncEnabled.run());
    completionPreset.addActionListener(e -> syncEnabled.run());
    syncEnabled.run();

    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 1, hidemode 3", "[grow,fill]", "[]2[]2[]4[]2[]2[]2[]2[]"));
    panel.setOpaque(false);
    panel.add(enabled, "growx, wmin 0, wrap");
    panel.add(underline, "growx, wmin 0, gapleft 18, wrap");
    panel.add(suggestOnTab, "growx, wmin 0, gapleft 18, wrap");
    panel.add(hoverSuggestions, "growx, wmin 0, gapleft 18, wrap");

    JPanel langRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[grow,fill]", "[]"));
    langRow.setOpaque(false);
    langRow.add(new JLabel("Dictionary language"));
    langRow.add(languageTag, "growx, wmin 160");
    panel.add(langRow, "growx, wmin 0, gapleft 18, wrap");

    JPanel presetRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[grow,fill]", "[]"));
    presetRow.setOpaque(false);
    presetRow.add(new JLabel("Completion preset"));
    presetRow.add(completionPreset, "growx, wmin 180");
    panel.add(presetRow, "growx, wmin 0, gapleft 18, wrap");
    panel.add(
        PreferencesDialog.helpText(
            "Presets tune TAB completion ranking. Select Custom to reveal manual tuning knobs."),
        "growx, wmin 0, gapleft 18, wrap");
    panel.add(customKnobsPanel, "growx, wmin 0, gapleft 36, wrap");

    panel.add(new JLabel("Custom dictionary"), "growx, wmin 0, gapleft 18, wrap");
    panel.add(customScroll, "growx, wmin 0, h 80:110:180, gapleft 18, wrap");
    panel.add(
        PreferencesDialog.helpText(
            "Add channel slang, nick-like words, or terms you use frequently so they are ignored."),
        "growx, wmin 0, gapleft 18, wrap");

    return new SpellcheckControls(
        enabled,
        underline,
        suggestOnTab,
        hoverSuggestions,
        languageTag,
        customDictionary,
        completionPreset,
        customMinPrefixCompletionTokenLength,
        customMaxPrefixCompletionExtraChars,
        customMaxPrefixLexiconCandidates,
        customPrefixCompletionBonusScore,
        customSourceOrderWeight,
        panel);
  }

  static String languageTagValue(JComboBox<SpellcheckLanguageOption> combo) {
    Object selected = combo != null ? combo.getSelectedItem() : null;
    if (selected instanceof SpellcheckLanguageOption o) {
      return SpellcheckSettings.normalizeLanguageTag(o.id());
    }
    return SpellcheckSettings.DEFAULT_LANGUAGE_TAG;
  }

  static String completionPresetValue(JComboBox<SpellcheckPresetOption> combo) {
    Object selected = combo != null ? combo.getSelectedItem() : null;
    if (selected instanceof SpellcheckPresetOption o) {
      return SpellcheckSettings.normalizeCompletionPreset(o.id());
    }
    return SpellcheckSettings.DEFAULT_COMPLETION_PRESET;
  }

  static List<String> parseCustomDictionary(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String line : raw.split("\\R")) {
      String trimmed = line == null ? "" : line.trim();
      if (trimmed.isEmpty()) continue;
      String[] tokens = trimmed.split("\\s+");
      for (String token : tokens) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) continue;
        out.add(t);
      }
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }
}
