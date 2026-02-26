package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpellcheckSettingsTest {

  @Test
  void defaultsUseAndroidLikePresetProfile() {
    SpellcheckSettings settings = SpellcheckSettings.defaults();
    SpellcheckSettings.CompletionProfile profile = settings.completionProfile();

    assertEquals(
        SpellcheckSettings.COMPLETION_PRESET_ANDROID_LIKE, settings.completionPreset());
    assertEquals(
        SpellcheckSettings.DEFAULT_CUSTOM_MIN_PREFIX_COMPLETION_TOKEN_LENGTH,
        profile.minPrefixCompletionTokenLength());
    assertEquals(
        SpellcheckSettings.DEFAULT_CUSTOM_MAX_PREFIX_COMPLETION_EXTRA_CHARS,
        profile.maxPrefixCompletionExtraChars());
    assertEquals(
        SpellcheckSettings.DEFAULT_CUSTOM_MAX_PREFIX_LEXICON_CANDIDATES,
        profile.maxPrefixLexiconCandidates());
    assertEquals(
        SpellcheckSettings.DEFAULT_CUSTOM_PREFIX_COMPLETION_BONUS_SCORE,
        profile.prefixCompletionBonusScore());
    assertEquals(
        SpellcheckSettings.DEFAULT_CUSTOM_SOURCE_ORDER_WEIGHT, profile.sourceOrderWeight());
  }

  @Test
  void conservativePresetUsesPresetValuesInsteadOfCustomOverrides() {
    SpellcheckSettings settings =
        new SpellcheckSettings(
            true,
            true,
            true,
            "en-US",
            List.of(),
            SpellcheckSettings.COMPLETION_PRESET_CONSERVATIVE,
            2,
            24,
            256,
            400,
            0);
    SpellcheckSettings.CompletionProfile profile = settings.completionProfile();

    assertEquals(3, profile.minPrefixCompletionTokenLength());
    assertEquals(9, profile.maxPrefixCompletionExtraChars());
    assertEquals(64, profile.maxPrefixLexiconCandidates());
    assertEquals(140, profile.prefixCompletionBonusScore());
    assertEquals(8, profile.sourceOrderWeight());
  }

  @Test
  void standardPresetUsesBalancedValues() {
    SpellcheckSettings settings =
        new SpellcheckSettings(
            true,
            true,
            true,
            "en-US",
            List.of(),
            SpellcheckSettings.COMPLETION_PRESET_STANDARD,
            2,
            24,
            256,
            400,
            0);
    SpellcheckSettings.CompletionProfile profile = settings.completionProfile();

    assertEquals(2, profile.minPrefixCompletionTokenLength());
    assertEquals(12, profile.maxPrefixCompletionExtraChars());
    assertEquals(80, profile.maxPrefixLexiconCandidates());
    assertEquals(180, profile.prefixCompletionBonusScore());
    assertEquals(7, profile.sourceOrderWeight());
  }

  @Test
  void customPresetNormalizesOutOfRangeKnobs() {
    SpellcheckSettings settings =
        new SpellcheckSettings(
            true,
            true,
            true,
            "en-US",
            List.of(),
            SpellcheckSettings.COMPLETION_PRESET_CUSTOM,
            1,
            100,
            999,
            -25,
            99);
    SpellcheckSettings.CompletionProfile profile = settings.completionProfile();

    assertEquals(2, profile.minPrefixCompletionTokenLength());
    assertEquals(24, profile.maxPrefixCompletionExtraChars());
    assertEquals(256, profile.maxPrefixLexiconCandidates());
    assertEquals(0, profile.prefixCompletionBonusScore());
    assertEquals(20, profile.sourceOrderWeight());
  }

  @Test
  void normalizeCompletionPresetFallsBackToAndroidLike() {
    assertEquals(
        SpellcheckSettings.COMPLETION_PRESET_ANDROID_LIKE,
        SpellcheckSettings.normalizeCompletionPreset("unknown"));
  }

  @Test
  void normalizeCompletionPresetAcceptsStandard() {
    assertEquals(
        SpellcheckSettings.COMPLETION_PRESET_STANDARD,
        SpellcheckSettings.normalizeCompletionPreset("standard"));
  }
}
