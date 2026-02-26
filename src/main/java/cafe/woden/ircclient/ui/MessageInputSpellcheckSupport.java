package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.settings.SpellcheckSettings;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.language.BritishEnglish;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.spelling.SpellingCheckRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Spell-checking support for the message input (misspelling highlights + word suggestions). */
final class MessageInputSpellcheckSupport implements MessageInputWordSuggestionProvider {

  private static final Logger log = LoggerFactory.getLogger(MessageInputSpellcheckSupport.class);

  private static final int SPELLCHECK_DEBOUNCE_MS = 220;
  private static final long SUGGESTION_CACHE_TTL_MS = 30_000L;

  private static final ExecutorService SPELLCHECK_EXECUTOR =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "ircafe-spellcheck");
            t.setDaemon(true);
            return t;
          });

  private static final ThreadLocal<Map<String, JLanguageTool>> CHECKERS_BY_THREAD =
      ThreadLocal.withInitial(HashMap::new);

  private final JTextField input;
  private final Timer debounceTimer;
  private final AtomicLong spellcheckRequestSeq = new AtomicLong();
  private final List<Object> misspellingHighlightTags = new ArrayList<>();
  private final Highlighter.HighlightPainter misspellingPainter =
      new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 110, 110, 72));
  private final ConcurrentHashMap<String, CachedSuggestions> suggestionCache =
      new ConcurrentHashMap<>();

  private volatile SpellcheckSettings settings;
  private volatile Map<String, String> knownNicksByLower = Map.of();
  private volatile Set<String> customDictionaryByLower = Set.of();

  MessageInputSpellcheckSupport(JTextField input, SpellcheckSettings initialSettings) {
    this.input = Objects.requireNonNull(input, "input");
    this.debounceTimer = new Timer(SPELLCHECK_DEBOUNCE_MS, e -> runSpellcheckAsync());
    this.debounceTimer.setRepeats(false);
    this.settings = initialSettings != null ? initialSettings : SpellcheckSettings.defaults();
    applyCustomDictionarySnapshot(this.settings.customDictionary());

    // Warm up dictionaries once to avoid first-use jank on the EDT.
    SPELLCHECK_EXECUTOR.execute(
        () -> {
          try {
            checkerForCurrentThread(this.settings.languageTag());
          } catch (Exception ex) {
            log.debug("[MessageInputSpellcheckSupport] warmup failed", ex);
          }
        });
  }

  void onSettingsApplied(SpellcheckSettings next) {
    SpellcheckSettings normalized = next != null ? next : SpellcheckSettings.defaults();
    this.settings = normalized;
    applyCustomDictionarySnapshot(normalized.customDictionary());
    suggestionCache.clear();
    if (!normalized.enabled() || !normalized.underlineEnabled()) {
      clearMisspellingHighlights();
      return;
    }
    onDraftChanged();
  }

  void onDraftChanged() {
    if (!input.isEnabled() || !input.isEditable()) {
      clearMisspellingHighlights();
      return;
    }
    debounceTimer.restart();
  }

  void onInputEnabledChanged(boolean enabled) {
    if (!enabled) {
      debounceTimer.stop();
      clearMisspellingHighlights();
      return;
    }
    onDraftChanged();
  }

  void setNickWhitelist(List<String> nicks) {
    if (nicks == null || nicks.isEmpty()) {
      knownNicksByLower = Map.of();
      return;
    }
    ConcurrentHashMap<String, String> out = new ConcurrentHashMap<>();
    for (String nick : nicks) {
      if (nick == null) continue;
      String s = nick.trim();
      if (s.isEmpty()) continue;
      out.put(s.toLowerCase(Locale.ROOT), s);
    }
    knownNicksByLower = Map.copyOf(out);
  }

  void onRemoveNotify() {
    debounceTimer.stop();
    clearMisspellingHighlights();
  }

  @Override
  public List<String> suggestWords(String token, int maxSuggestions) {
    SpellcheckSettings snapshot = settings;
    if (snapshot == null || !snapshot.enabled() || !snapshot.suggestOnTabEnabled())
      return List.of();

    String raw = token == null ? "" : token.trim();
    if (raw.isEmpty()) return List.of();
    if (raw.startsWith("#") || raw.startsWith("&") || raw.startsWith("@")) return List.of();
    if (raw.startsWith("/") || raw.contains("://")) return List.of();

    String candidate = normalizeToken(raw);
    if (candidate.isEmpty() || maxSuggestions <= 0) return List.of();
    if (isKnownNick(candidate)) return List.of();
    if (isCustomDictionaryWord(candidate)) return List.of();
    if (!isWordCandidate(candidate)) return List.of();
    if (candidate.length() < 2) return List.of();

    String key = candidate.toLowerCase(Locale.ROOT);
    long now = System.currentTimeMillis();

    CachedSuggestions cached = suggestionCache.get(key);
    if (cached != null && (now - cached.createdAtMs()) < SUGGESTION_CACHE_TTL_MS) {
      return filterAndLimit(cached.words(), maxSuggestions);
    }

    List<String> computed = computeSuggestions(candidate, snapshot.languageTag());
    suggestionCache.put(key, new CachedSuggestions(now, computed));
    return filterAndLimit(computed, maxSuggestions);
  }

  private List<String> computeSuggestions(String token, String languageTag) {
    try {
      List<RuleMatch> matches = checkerForCurrentThread(languageTag).check(token);
      if (matches == null || matches.isEmpty()) return List.of();

      String tokenLower = token.toLowerCase(Locale.ROOT);
      LinkedHashSet<String> raw = new LinkedHashSet<>();
      for (RuleMatch match : matches) {
        if (!isSpellingRule(match)) continue;
        List<String> suggestions = match.getSuggestedReplacements();
        if (suggestions == null || suggestions.isEmpty()) continue;
        for (String suggestion : suggestions) {
          String cleaned = normalizeToken(suggestion);
          if (cleaned.isEmpty()) continue;
          if (cleaned.equalsIgnoreCase(token)) continue;
          if (!isWordCandidate(cleaned)) continue;
          if (isKnownNick(cleaned)) continue;
          if (isCustomDictionaryWord(cleaned)) continue;
          raw.add(cleaned);
          if (raw.size() >= 24) break;
        }
        if (raw.size() >= 24) break;
      }
      if (raw.isEmpty()) return List.of();

      ArrayList<String> ranked = new ArrayList<>(raw);
      ranked.sort(
          (a, b) -> {
            String al = a.toLowerCase(Locale.ROOT);
            String bl = b.toLowerCase(Locale.ROOT);
            int prefixCmp =
                Integer.compare(prefixPriority(tokenLower, al), prefixPriority(tokenLower, bl));
            if (prefixCmp != 0) return prefixCmp;
            int distanceCmp =
                Integer.compare(
                    levenshteinDistance(tokenLower, al), levenshteinDistance(tokenLower, bl));
            if (distanceCmp != 0) return distanceCmp;
            return String.CASE_INSENSITIVE_ORDER.compare(a, b);
          });
      return List.copyOf(ranked);
    } catch (Exception ex) {
      log.debug("[MessageInputSpellcheckSupport] suggestion lookup failed for '{}'", token, ex);
      return List.of();
    }
  }

  private List<String> filterAndLimit(List<String> suggestions, int maxSuggestions) {
    if (suggestions == null || suggestions.isEmpty() || maxSuggestions <= 0) return List.of();
    ArrayList<String> out = new ArrayList<>(Math.min(maxSuggestions, suggestions.size()));
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (String s : suggestions) {
      if (s == null) continue;
      String cleaned = normalizeToken(s);
      if (cleaned.isEmpty()) continue;
      String lower = cleaned.toLowerCase(Locale.ROOT);
      if (!seen.add(lower)) continue;
      if (isKnownNick(cleaned)) continue;
      if (isCustomDictionaryWord(cleaned)) continue;
      out.add(cleaned);
      if (out.size() >= maxSuggestions) break;
    }
    return List.copyOf(out);
  }

  private void runSpellcheckAsync() {
    SpellcheckSettings snapshot = settings;
    String text = input.getText();
    if (snapshot == null
        || !snapshot.enabled()
        || !snapshot.underlineEnabled()
        || text == null
        || text.isBlank()
        || startsWithSlashCommand(text)) {
      clearMisspellingHighlights();
      return;
    }
    long req = spellcheckRequestSeq.incrementAndGet();
    Map<String, String> nickSnapshot = knownNicksByLower;
    String languageTag = snapshot.languageTag();

    SPELLCHECK_EXECUTOR.execute(
        () -> {
          List<MisspellingRange> ranges = findMisspellings(text, nickSnapshot, languageTag);
          SwingUtilities.invokeLater(() -> applyMisspellingHighlights(req, text, ranges));
        });
  }

  private List<MisspellingRange> findMisspellings(
      String text, Map<String, String> nickSnapshot, String languageTag) {
    if (text == null || text.isBlank()) return List.of();
    try {
      List<RuleMatch> matches = checkerForCurrentThread(languageTag).check(text);
      if (matches == null || matches.isEmpty()) return List.of();

      ArrayList<MisspellingRange> out = new ArrayList<>();
      for (RuleMatch match : matches) {
        if (!isSpellingRule(match)) continue;
        int start = clamp(match.getFromPos(), 0, text.length());
        int end = clamp(match.getToPos(), 0, text.length());
        if (start >= end) continue;

        // Keep the highlight aligned to word characters only (ignore punctuation at boundaries).
        while (start < end && !isWordChar(text.charAt(start))) start++;
        while (end > start && !isWordChar(text.charAt(end - 1))) end--;
        if (start >= end) continue;

        String token = text.substring(start, end);
        if (token.isBlank()) continue;
        if (shouldIgnoreMisspellingToken(text, start, token, nickSnapshot)) continue;

        out.add(new MisspellingRange(start, end));
      }
      if (out.isEmpty()) return List.of();
      return List.copyOf(out);
    } catch (Exception ex) {
      log.debug("[MessageInputSpellcheckSupport] spellcheck failed", ex);
      return List.of();
    }
  }

  private void applyMisspellingHighlights(
      long requestId, String checkedText, List<MisspellingRange> ranges) {
    if (requestId != spellcheckRequestSeq.get()) return;
    if (!Objects.equals(input.getText(), checkedText)) return;

    clearMisspellingHighlights();
    if (ranges == null || ranges.isEmpty()) return;

    Highlighter highlighter = input.getHighlighter();
    for (MisspellingRange r : ranges) {
      try {
        Object tag = highlighter.addHighlight(r.start(), r.end(), misspellingPainter);
        misspellingHighlightTags.add(tag);
      } catch (Exception ignored) {
      }
    }
  }

  private void clearMisspellingHighlights() {
    Highlighter highlighter = input.getHighlighter();
    for (Object tag : misspellingHighlightTags) {
      try {
        highlighter.removeHighlight(tag);
      } catch (Exception ignored) {
      }
    }
    misspellingHighlightTags.clear();
  }

  private boolean shouldIgnoreMisspellingToken(
      String text, int tokenStart, String token, Map<String, String> nickSnapshot) {
    String normalized = normalizeToken(token);
    if (normalized.isEmpty()) return true;
    if (!isWordCandidate(normalized)) return true;
    if (normalized.length() < 2) return true;
    if (isCustomDictionaryWord(normalized)) return true;

    if (tokenStart > 0) {
      char prev = text.charAt(tokenStart - 1);
      if (prev == '#' || prev == '&' || prev == '@') return true;
    }

    String lower = normalized.toLowerCase(Locale.ROOT);
    return nickSnapshot.containsKey(lower);
  }

  private boolean isKnownNick(String token) {
    if (token == null || token.isBlank()) return false;
    return knownNicksByLower.containsKey(token.toLowerCase(Locale.ROOT));
  }

  private boolean isCustomDictionaryWord(String token) {
    if (token == null || token.isBlank()) return false;
    return customDictionaryByLower.contains(token.toLowerCase(Locale.ROOT));
  }

  private void applyCustomDictionarySnapshot(List<String> words) {
    if (words == null || words.isEmpty()) {
      customDictionaryByLower = Set.of();
      return;
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String word : words) {
      if (word == null) continue;
      String cleaned = normalizeToken(word);
      if (cleaned.isBlank()) continue;
      out.add(cleaned.toLowerCase(Locale.ROOT));
    }
    customDictionaryByLower = Set.copyOf(out);
  }

  private static String normalizeToken(String raw) {
    if (raw == null) return "";
    String token = raw.trim();
    if (token.isEmpty()) return "";
    int start = 0;
    int end = token.length();
    while (start < end && !isWordChar(token.charAt(start))) start++;
    while (end > start && !isWordChar(token.charAt(end - 1))) end--;
    if (start >= end) return "";
    return token.substring(start, end);
  }

  private static boolean isWordCandidate(String token) {
    if (token == null || token.isBlank()) return false;
    if (token.contains("://")) return false;
    boolean hasLetter = false;
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (Character.isLetter(c)) {
        hasLetter = true;
        continue;
      }
      if (c == '\'' || c == '-') continue;
      return false;
    }
    return hasLetter;
  }

  private static int prefixPriority(String tokenLower, String candidateLower) {
    if (candidateLower.startsWith(tokenLower)) return 0;
    if (candidateLower.contains(tokenLower)) return 1;
    return 2;
  }

  private static int levenshteinDistance(String a, String b) {
    if (a.equals(b)) return 0;
    if (a.isEmpty()) return b.length();
    if (b.isEmpty()) return a.length();

    int[] prev = new int[b.length() + 1];
    int[] cur = new int[b.length() + 1];

    for (int j = 0; j <= b.length(); j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      cur[0] = i;
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= b.length(); j++) {
        int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
        cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] tmp = prev;
      prev = cur;
      cur = tmp;
    }
    return prev[b.length()];
  }

  private static boolean isWordChar(char c) {
    return Character.isLetter(c) || c == '\'' || c == '-';
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  private static boolean isSpellingRule(RuleMatch match) {
    if (match == null) return false;
    Rule rule = match.getRule();
    if (rule instanceof SpellingCheckRule) return true;
    String id = rule != null ? rule.getId() : "";
    return id != null && id.toLowerCase(Locale.ROOT).contains("spell");
  }

  private static boolean startsWithSlashCommand(String text) {
    if (text == null || text.isEmpty()) return false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) continue;
      return c == '/';
    }
    return false;
  }

  private static JLanguageTool checkerForCurrentThread(String languageTag) {
    Map<String, JLanguageTool> byLang = CHECKERS_BY_THREAD.get();
    String normalized = SpellcheckSettings.normalizeLanguageTag(languageTag);
    return byLang.computeIfAbsent(normalized, MessageInputSpellcheckSupport::createChecker);
  }

  private static JLanguageTool createChecker(String languageTag) {
    return switch (SpellcheckSettings.normalizeLanguageTag(languageTag)) {
      case "en-GB" -> new JLanguageTool(new BritishEnglish());
      default -> new JLanguageTool(new AmericanEnglish());
    };
  }

  private record MisspellingRange(int start, int end) {}

  private record CachedSuggestions(long createdAtMs, List<String> words) {}
}
