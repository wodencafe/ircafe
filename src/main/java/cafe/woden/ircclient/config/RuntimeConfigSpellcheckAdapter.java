package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.SpellcheckRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for spellcheck settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigSpellcheckAdapter implements SpellcheckRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigSpellcheckAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberSpellcheckEnabled(boolean enabled) {
    runtimeConfig.rememberSpellcheckEnabled(enabled);
  }

  @Override
  public void rememberSpellcheckUnderlineEnabled(boolean enabled) {
    runtimeConfig.rememberSpellcheckUnderlineEnabled(enabled);
  }

  @Override
  public void rememberSpellcheckSuggestOnTabEnabled(boolean enabled) {
    runtimeConfig.rememberSpellcheckSuggestOnTabEnabled(enabled);
  }

  @Override
  public void rememberSpellcheckHoverSuggestionsEnabled(boolean enabled) {
    runtimeConfig.rememberSpellcheckHoverSuggestionsEnabled(enabled);
  }

  @Override
  public void rememberSpellcheckCompletionPreset(String preset) {
    runtimeConfig.rememberSpellcheckCompletionPreset(preset);
  }

  @Override
  public void rememberSpellcheckCustomMinPrefixCompletionTokenLength(int value) {
    runtimeConfig.rememberSpellcheckCustomMinPrefixCompletionTokenLength(value);
  }

  @Override
  public void rememberSpellcheckCustomMaxPrefixCompletionExtraChars(int value) {
    runtimeConfig.rememberSpellcheckCustomMaxPrefixCompletionExtraChars(value);
  }

  @Override
  public void rememberSpellcheckCustomMaxPrefixLexiconCandidates(int value) {
    runtimeConfig.rememberSpellcheckCustomMaxPrefixLexiconCandidates(value);
  }

  @Override
  public void rememberSpellcheckCustomPrefixCompletionBonusScore(int value) {
    runtimeConfig.rememberSpellcheckCustomPrefixCompletionBonusScore(value);
  }

  @Override
  public void rememberSpellcheckCustomSourceOrderWeight(int value) {
    runtimeConfig.rememberSpellcheckCustomSourceOrderWeight(value);
  }

  @Override
  public void rememberSpellcheckLanguageTag(String languageTag) {
    runtimeConfig.rememberSpellcheckLanguageTag(languageTag);
  }

  @Override
  public void rememberSpellcheckCustomDictionary(List<String> words) {
    runtimeConfig.rememberSpellcheckCustomDictionary(words);
  }
}
