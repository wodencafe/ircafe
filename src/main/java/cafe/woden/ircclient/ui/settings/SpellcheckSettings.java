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
    List<String> customDictionary) {

  public static final String DEFAULT_LANGUAGE_TAG = "en-US";
  private static final List<String> SUPPORTED_LANGUAGE_TAGS = List.of("en-US", "en-GB");

  public SpellcheckSettings {
    languageTag = normalizeLanguageTag(languageTag);
    customDictionary = normalizeCustomDictionary(customDictionary);
  }

  public static SpellcheckSettings defaults() {
    return new SpellcheckSettings(true, true, true, DEFAULT_LANGUAGE_TAG, List.of());
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
}
