package cafe.woden.ircclient.ui.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Settings for message-input spell checking and tab suggestions. */
public record SpellcheckSettings(
    boolean enabled,
    boolean underlineEnabled,
    boolean suggestOnTabEnabled,
    String languageTag,
    List<String> customDictionary,
    String completionPreset,
    int customMinPrefixCompletionTokenLength,
    int customMaxPrefixCompletionExtraChars,
    int customMaxPrefixLexiconCandidates,
    int customPrefixCompletionBonusScore,
    int customSourceOrderWeight) {

  public static final String DEFAULT_LANGUAGE_TAG = "en-US";
  public static final String COMPLETION_PRESET_ANDROID_LIKE = "android-like";
  public static final String COMPLETION_PRESET_STANDARD = "standard";
  public static final String COMPLETION_PRESET_CONSERVATIVE = "conservative";
  public static final String COMPLETION_PRESET_AGGRESSIVE = "aggressive";
  public static final String COMPLETION_PRESET_CUSTOM = "custom";
  public static final String DEFAULT_COMPLETION_PRESET = COMPLETION_PRESET_ANDROID_LIKE;
  private static final List<String> SUPPORTED_LANGUAGE_TAGS = List.of("en-US", "en-GB");
  private static final List<String> SUPPORTED_COMPLETION_PRESETS =
      List.of(
          COMPLETION_PRESET_ANDROID_LIKE,
          COMPLETION_PRESET_STANDARD,
          COMPLETION_PRESET_CONSERVATIVE,
          COMPLETION_PRESET_AGGRESSIVE,
          COMPLETION_PRESET_CUSTOM);

  public static final int MIN_PREFIX_COMPLETION_TOKEN_LENGTH_MIN = 2;
  public static final int MIN_PREFIX_COMPLETION_TOKEN_LENGTH_MAX = 6;
  public static final int MAX_PREFIX_COMPLETION_EXTRA_CHARS_MIN = 4;
  public static final int MAX_PREFIX_COMPLETION_EXTRA_CHARS_MAX = 24;
  public static final int MAX_PREFIX_LEXICON_CANDIDATES_MIN = 16;
  public static final int MAX_PREFIX_LEXICON_CANDIDATES_MAX = 256;
  public static final int PREFIX_COMPLETION_BONUS_SCORE_MIN = 0;
  public static final int PREFIX_COMPLETION_BONUS_SCORE_MAX = 400;
  public static final int SOURCE_ORDER_WEIGHT_MIN = 0;
  public static final int SOURCE_ORDER_WEIGHT_MAX = 20;

  public static final int DEFAULT_CUSTOM_MIN_PREFIX_COMPLETION_TOKEN_LENGTH = 2;
  public static final int DEFAULT_CUSTOM_MAX_PREFIX_COMPLETION_EXTRA_CHARS = 14;
  public static final int DEFAULT_CUSTOM_MAX_PREFIX_LEXICON_CANDIDATES = 96;
  public static final int DEFAULT_CUSTOM_PREFIX_COMPLETION_BONUS_SCORE = 220;
  public static final int DEFAULT_CUSTOM_SOURCE_ORDER_WEIGHT = 6;

  private static final CompletionProfile ANDROID_LIKE_PROFILE =
      new CompletionProfile(
          DEFAULT_CUSTOM_MIN_PREFIX_COMPLETION_TOKEN_LENGTH,
          DEFAULT_CUSTOM_MAX_PREFIX_COMPLETION_EXTRA_CHARS,
          DEFAULT_CUSTOM_MAX_PREFIX_LEXICON_CANDIDATES,
          DEFAULT_CUSTOM_PREFIX_COMPLETION_BONUS_SCORE,
          DEFAULT_CUSTOM_SOURCE_ORDER_WEIGHT);
  private static final CompletionProfile CONSERVATIVE_PROFILE =
      new CompletionProfile(3, 9, 64, 140, 8);
  private static final CompletionProfile STANDARD_PROFILE =
      new CompletionProfile(2, 12, 80, 180, 7);
  private static final CompletionProfile AGGRESSIVE_PROFILE =
      new CompletionProfile(2, 18, 144, 280, 4);

  public SpellcheckSettings {
    languageTag = normalizeLanguageTag(languageTag);
    customDictionary = normalizeCustomDictionary(customDictionary);
    completionPreset = normalizeCompletionPreset(completionPreset);
    customMinPrefixCompletionTokenLength =
        normalizeCustomMinPrefixCompletionTokenLength(customMinPrefixCompletionTokenLength);
    customMaxPrefixCompletionExtraChars =
        normalizeCustomMaxPrefixCompletionExtraChars(customMaxPrefixCompletionExtraChars);
    customMaxPrefixLexiconCandidates =
        normalizeCustomMaxPrefixLexiconCandidates(customMaxPrefixLexiconCandidates);
    customPrefixCompletionBonusScore =
        normalizeCustomPrefixCompletionBonusScore(customPrefixCompletionBonusScore);
    customSourceOrderWeight = normalizeCustomSourceOrderWeight(customSourceOrderWeight);
  }

  public static SpellcheckSettings defaults() {
    return new SpellcheckSettings(
        true,
        true,
        true,
        DEFAULT_LANGUAGE_TAG,
        List.of(),
        DEFAULT_COMPLETION_PRESET,
        DEFAULT_CUSTOM_MIN_PREFIX_COMPLETION_TOKEN_LENGTH,
        DEFAULT_CUSTOM_MAX_PREFIX_COMPLETION_EXTRA_CHARS,
        DEFAULT_CUSTOM_MAX_PREFIX_LEXICON_CANDIDATES,
        DEFAULT_CUSTOM_PREFIX_COMPLETION_BONUS_SCORE,
        DEFAULT_CUSTOM_SOURCE_ORDER_WEIGHT);
  }

  public static String normalizeLanguageTag(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return DEFAULT_LANGUAGE_TAG;
    String folded = s.replace('_', '-');
    for (String supported : SUPPORTED_LANGUAGE_TAGS) {
      if (supported.equalsIgnoreCase(folded)) return supported;
    }
    return DEFAULT_LANGUAGE_TAG;
  }

  public static List<String> supportedLanguageTags() {
    return SUPPORTED_LANGUAGE_TAGS;
  }

  public static String normalizeCompletionPreset(String raw) {
    String s = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return DEFAULT_COMPLETION_PRESET;
    for (String supported : SUPPORTED_COMPLETION_PRESETS) {
      if (supported.equalsIgnoreCase(s)) return supported;
    }
    return DEFAULT_COMPLETION_PRESET;
  }

  public static List<String> supportedCompletionPresets() {
    return SUPPORTED_COMPLETION_PRESETS;
  }

  public static int normalizeCustomMinPrefixCompletionTokenLength(int raw) {
    return clamp(
        raw, MIN_PREFIX_COMPLETION_TOKEN_LENGTH_MIN, MIN_PREFIX_COMPLETION_TOKEN_LENGTH_MAX);
  }

  public static int normalizeCustomMaxPrefixCompletionExtraChars(int raw) {
    return clamp(
        raw, MAX_PREFIX_COMPLETION_EXTRA_CHARS_MIN, MAX_PREFIX_COMPLETION_EXTRA_CHARS_MAX);
  }

  public static int normalizeCustomMaxPrefixLexiconCandidates(int raw) {
    return clamp(raw, MAX_PREFIX_LEXICON_CANDIDATES_MIN, MAX_PREFIX_LEXICON_CANDIDATES_MAX);
  }

  public static int normalizeCustomPrefixCompletionBonusScore(int raw) {
    return clamp(raw, PREFIX_COMPLETION_BONUS_SCORE_MIN, PREFIX_COMPLETION_BONUS_SCORE_MAX);
  }

  public static int normalizeCustomSourceOrderWeight(int raw) {
    return clamp(raw, SOURCE_ORDER_WEIGHT_MIN, SOURCE_ORDER_WEIGHT_MAX);
  }

  public CompletionProfile completionProfile() {
    return switch (completionPreset) {
      case COMPLETION_PRESET_STANDARD -> STANDARD_PROFILE;
      case COMPLETION_PRESET_CONSERVATIVE -> CONSERVATIVE_PROFILE;
      case COMPLETION_PRESET_AGGRESSIVE -> AGGRESSIVE_PROFILE;
      case COMPLETION_PRESET_CUSTOM ->
          new CompletionProfile(
              customMinPrefixCompletionTokenLength,
              customMaxPrefixCompletionExtraChars,
              customMaxPrefixLexiconCandidates,
              customPrefixCompletionBonusScore,
              customSourceOrderWeight);
      case COMPLETION_PRESET_ANDROID_LIKE -> ANDROID_LIKE_PROFILE;
      default -> ANDROID_LIKE_PROFILE;
    };
  }

  public static List<String> normalizeCustomDictionary(List<String> raw) {
    if (raw == null || raw.isEmpty()) return List.of();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    ArrayList<String> out = new ArrayList<>(raw.size());
    for (String entry : raw) {
      String token = Objects.toString(entry, "").trim();
      if (token.isEmpty()) continue;
      String lower = token.toLowerCase(Locale.ROOT);
      if (!seen.add(lower)) continue;
      out.add(token);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  public record CompletionProfile(
      int minPrefixCompletionTokenLength,
      int maxPrefixCompletionExtraChars,
      int maxPrefixLexiconCandidates,
      int prefixCompletionBonusScore,
      int sourceOrderWeight) {}
}
