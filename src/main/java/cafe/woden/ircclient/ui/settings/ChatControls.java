package cafe.woden.ircclient.ui.settings;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;

record SpellcheckLanguageOption(String id, String label) {}

record SpellcheckPresetOption(String id, String label) {}

final class SpellcheckControls {
  final JCheckBox enabled;
  final JCheckBox underlineEnabled;
  final JCheckBox suggestOnTabEnabled;
  final JCheckBox hoverSuggestionsEnabled;
  final JComboBox<SpellcheckLanguageOption> languageTag;
  final JTextArea customDictionary;
  final JComboBox<SpellcheckPresetOption> completionPreset;
  final JSpinner customMinPrefixCompletionTokenLength;
  final JSpinner customMaxPrefixCompletionExtraChars;
  final JSpinner customMaxPrefixLexiconCandidates;
  final JSpinner customPrefixCompletionBonusScore;
  final JSpinner customSourceOrderWeight;
  final JPanel panel;

  SpellcheckControls(
      JCheckBox enabled,
      JCheckBox underlineEnabled,
      JCheckBox suggestOnTabEnabled,
      JCheckBox hoverSuggestionsEnabled,
      JComboBox<SpellcheckLanguageOption> languageTag,
      JTextArea customDictionary,
      JComboBox<SpellcheckPresetOption> completionPreset,
      JSpinner customMinPrefixCompletionTokenLength,
      JSpinner customMaxPrefixCompletionExtraChars,
      JSpinner customMaxPrefixLexiconCandidates,
      JSpinner customPrefixCompletionBonusScore,
      JSpinner customSourceOrderWeight,
      JPanel panel) {
    this.enabled = enabled;
    this.underlineEnabled = underlineEnabled;
    this.suggestOnTabEnabled = suggestOnTabEnabled;
    this.hoverSuggestionsEnabled = hoverSuggestionsEnabled;
    this.languageTag = languageTag;
    this.customDictionary = customDictionary;
    this.completionPreset = completionPreset;
    this.customMinPrefixCompletionTokenLength = customMinPrefixCompletionTokenLength;
    this.customMaxPrefixCompletionExtraChars = customMaxPrefixCompletionExtraChars;
    this.customMaxPrefixLexiconCandidates = customMaxPrefixLexiconCandidates;
    this.customPrefixCompletionBonusScore = customPrefixCompletionBonusScore;
    this.customSourceOrderWeight = customSourceOrderWeight;
    this.panel = panel;
  }
}

final class NickColorControls {
  final JCheckBox enabled;
  final JSpinner minContrast;
  final JButton overrides;
  final JPanel panel;

  NickColorControls(JCheckBox enabled, JSpinner minContrast, JButton overrides, JPanel panel) {
    this.enabled = enabled;
    this.minContrast = minContrast;
    this.overrides = overrides;
    this.panel = panel;
  }
}

final class TimestampControls {
  final JCheckBox enabled;
  final JTextField format;
  final JCheckBox includeChatMessages;
  final JCheckBox includePresenceMessages;
  final JPanel panel;

  TimestampControls(
      JCheckBox enabled,
      JTextField format,
      JCheckBox includeChatMessages,
      JCheckBox includePresenceMessages,
      JPanel panel) {
    this.enabled = enabled;
    this.format = format;
    this.includeChatMessages = includeChatMessages;
    this.includePresenceMessages = includePresenceMessages;
    this.panel = panel;
  }
}

final class OutgoingColorControls {
  final JCheckBox enabled;
  final JTextField hex;
  final JLabel preview;
  final JPanel panel;

  OutgoingColorControls(JCheckBox enabled, JTextField hex, JLabel preview, JPanel panel) {
    this.enabled = enabled;
    this.hex = hex;
    this.preview = preview;
    this.panel = panel;
  }
}
