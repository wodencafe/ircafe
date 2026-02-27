package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.settings.SpellcheckSettings;
import cafe.woden.ircclient.util.VirtualThreads;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
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
  private static final int MAX_SUGGESTION_POOL = 24;
  private static final int MIN_FUZZY_TOKEN_LENGTH = 3;
  private static final int SHORT_TOKEN_MAX_DISTANCE = 1;
  private static final int MEDIUM_TOKEN_MAX_DISTANCE = 2;
  private static final int LONG_TOKEN_MAX_DISTANCE = 3;
  private static final int MIN_PREFIX_LEXICON_WORD_LENGTH = 2;
  private static final int MAX_TOTAL_SUGGESTION_POOL = 64;
  private static final int MAX_TRACKED_WORDS = 2048;
  private static final int MAX_TRACKED_WORD_FREQ = 64;
  private static final int TRANSPOSE_BONUS_SCORE = 52;
  private static final int PREFIX_COMPLETION_DISTANCE_WEIGHT = 24;
  private static final int PREFIX_COMPLETION_LENGTH_DELTA_WEIGHT = 9;
  private static final long CHECKER_FAILURE_LOG_INTERVAL_MS = 60_000L;
  private static final String EN_PREFIX_COMPLETIONS_RESOURCE =
      "/cafe/woden/ircclient/ui/spellcheck/en-prefix-completions.txt";

  private static final Object SPELLCHECK_EXECUTOR_LOCK = new Object();
  private static ExecutorService spellcheckExecutor;
  private static io.reactivex.rxjava3.core.Scheduler spellcheckScheduler;

  private static final ThreadLocal<Map<String, JLanguageTool>> CHECKERS_BY_THREAD =
      ThreadLocal.withInitial(HashMap::new);
  private static final ConcurrentHashMap<String, List<String>> PREFIX_PRIORITY_BY_LANG =
      new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, List<String>> PREFIX_LEXICON_BY_LANG =
      new ConcurrentHashMap<>();
  private static final AtomicLong LAST_CHECKER_FAILURE_LOG_MS = new AtomicLong(0L);
  private static final SpellcheckSettings.CompletionProfile DEFAULT_COMPLETION_PROFILE =
      SpellcheckSettings.defaults().completionProfile();

  private final JTextField input;
  private final Subject<SpellcheckRequest> spellcheckRequests;
  private final Disposable spellcheckSubscription;
  private final AtomicLong spellcheckRequestSeq = new AtomicLong();
  private final List<Object> misspellingHighlightTags = new ArrayList<>();
  private final Highlighter.HighlightPainter misspellingPainter =
      new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 110, 110, 72));
  private final AsyncLoadingCache<SuggestionCacheKey, List<String>> suggestionCache;
  private volatile MisspellingSnapshot misspellingSnapshot = MisspellingSnapshot.empty();

  private volatile SpellcheckSettings settings;
  private volatile Map<String, String> knownNicksByLower = Map.of();
  private volatile Set<String> customDictionaryByLower = Set.of();
  private final ConcurrentHashMap<String, Integer> localWordFrequency = new ConcurrentHashMap<>();

  MessageInputSpellcheckSupport(JTextField input, SpellcheckSettings initialSettings) {
    this.input = Objects.requireNonNull(input, "input");
    this.settings = initialSettings != null ? initialSettings : SpellcheckSettings.defaults();
    applyCustomDictionarySnapshot(this.settings.customDictionary());
    this.suggestionCache =
        Caffeine.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(Duration.ofMillis(SUGGESTION_CACHE_TTL_MS))
            .executor(ensureSpellcheckExecutor())
            .buildAsync(
                (key, executor) ->
                    CompletableFuture.supplyAsync(() -> computeSuggestions(key), executor));

    // Warm up dictionaries once to avoid first-use jank on the EDT.
    ensureSpellcheckExecutor()
        .execute(() -> checkerForCurrentThreadOrNull(this.settings.languageTag()));
    this.spellcheckRequests = PublishSubject.<SpellcheckRequest>create().toSerialized();
    this.spellcheckSubscription =
        spellcheckRequests
            .debounce(SPELLCHECK_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
            .switchMapSingle(this::evaluateSpellcheckRequest)
            .observeOn(SwingEdt.scheduler())
            .subscribe(
                this::applySpellcheckOutcome,
                err -> log.debug("[MessageInputSpellcheckSupport] spellcheck stream failed", err));
  }

  void onSettingsApplied(SpellcheckSettings next) {
    SpellcheckSettings normalized = next != null ? next : SpellcheckSettings.defaults();
    this.settings = normalized;
    applyCustomDictionarySnapshot(normalized.customDictionary());
    suggestionCache.synchronous().invalidateAll();
    if (!normalized.enabled() || !normalized.underlineEnabled()) {
      spellcheckRequestSeq.incrementAndGet();
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
    String text = input.getText();
    recordWordsFromInputForRanking(text);
    long requestId = spellcheckRequestSeq.incrementAndGet();
    if (spellcheckSubscription.isDisposed()) return;
    spellcheckRequests.onNext(new SpellcheckRequest(requestId, text, settings, knownNicksByLower));
  }

  void onInputEnabledChanged(boolean enabled) {
    if (!enabled) {
      spellcheckRequestSeq.incrementAndGet();
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
    spellcheckRequestSeq.incrementAndGet();
    clearMisspellingHighlights();
  }

  void shutdown() {
    spellcheckRequestSeq.incrementAndGet();
    clearMisspellingHighlights();
    suggestionCache.synchronous().invalidateAll();
    localWordFrequency.clear();
    knownNicksByLower = Map.of();
    customDictionaryByLower = Set.of();
    try {
      spellcheckRequests.onComplete();
    } catch (Exception ignored) {
    }
    try {
      if (!spellcheckSubscription.isDisposed()) {
        spellcheckSubscription.dispose();
      }
    } catch (Exception ignored) {
    }
  }

  Optional<MisspelledWord> misspelledWordAtCaret() {
    return misspelledWordAt(input.getCaretPosition());
  }

  Optional<MisspelledWord> misspelledWordAt(int caretPosition) {
    String currentText = input.getText();
    if (currentText == null || currentText.isEmpty()) return Optional.empty();

    MisspellingSnapshot snapshot = misspellingSnapshot;
    if (snapshot.ranges().isEmpty()) return Optional.empty();
    if (!Objects.equals(snapshot.text(), currentText)) return Optional.empty();

    int caret = clamp(caretPosition, 0, currentText.length());
    for (MisspellingRange range : snapshot.ranges()) {
      if (!isCaretInsideMisspelling(caret, range.start(), range.end())) continue;
      return Optional.of(
          new MisspelledWord(range.start(), range.end(), range.token(), range.suggestions()));
    }
    return Optional.empty();
  }

  List<String> suggestionsForMisspelledWord(MisspelledWord misspelledWord, int maxSuggestions) {
    if (misspelledWord == null || maxSuggestions <= 0) return List.of();

    LinkedHashSet<String> merged =
        new LinkedHashSet<>(filterAndLimit(misspelledWord.suggestions(), maxSuggestions));
    if (merged.size() >= maxSuggestions) return List.copyOf(merged);

    List<String> fallback = suggestWordsBlocking(misspelledWord.token(), maxSuggestions);
    for (String suggestion : fallback) {
      if (merged.size() >= maxSuggestions) break;
      merged.add(suggestion);
    }
    return merged.isEmpty() ? List.of() : List.copyOf(merged);
  }

  boolean replaceMisspelledWord(MisspelledWord misspelledWord, String replacement) {
    if (misspelledWord == null) return false;

    String normalizedReplacement = normalizeToken(replacement);
    if (normalizedReplacement.isBlank()) return false;

    String currentText = input.getText();
    if (currentText == null) return false;

    int start = misspelledWord.start();
    int end = misspelledWord.end();
    if (start < 0 || end <= start || end > currentText.length()) return false;

    String currentToken = currentText.substring(start, end);
    if (!normalizeToken(currentToken).equalsIgnoreCase(normalizeToken(misspelledWord.token()))) {
      return false;
    }

    try {
      input.getDocument().remove(start, end - start);
      input.getDocument().insertString(start, normalizedReplacement, null);
      input.setCaretPosition(start + normalizedReplacement.length());
      return true;
    } catch (Exception ignored) {
      return false;
    }
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

    SuggestionCacheKey key =
        new SuggestionCacheKey(
            candidate.toLowerCase(Locale.ROOT),
            SpellcheckSettings.normalizeLanguageTag(snapshot.languageTag()),
            snapshot.completionProfile());

    Optional<List<String>> completed = completedSuggestionsIfReady(key);
    if (completed.isPresent()) return filterAndLimit(completed.get(), maxSuggestions);

    if (SwingUtilities.isEventDispatchThread()) {
      var unused = suggestionCache.get(key);
      return List.of();
    }

    try {
      return filterAndLimit(suggestionCache.get(key).join(), maxSuggestions);
    } catch (RuntimeException ex) {
      suggestionCache.synchronous().invalidate(key);
      log.debug("[MessageInputSpellcheckSupport] suggestion lookup failed for '{}'", candidate, ex);
      return List.of();
    }
  }

  private Optional<List<String>> completedSuggestionsIfReady(SuggestionCacheKey key) {
    CompletableFuture<List<String>> inFlight = suggestionCache.getIfPresent(key);
    if (inFlight == null || !inFlight.isDone()) return Optional.empty();
    try {
      List<String> ready = inFlight.join();
      return Optional.of(ready != null ? ready : List.of());
    } catch (RuntimeException ex) {
      suggestionCache.synchronous().invalidate(key);
      return Optional.of(List.of());
    }
  }

  private List<String> suggestWordsBlocking(String token, int maxSuggestions) {
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

    SuggestionCacheKey key =
        new SuggestionCacheKey(
            candidate.toLowerCase(Locale.ROOT),
            SpellcheckSettings.normalizeLanguageTag(snapshot.languageTag()),
            snapshot.completionProfile());
    try {
      return filterAndLimit(suggestionCache.get(key).join(), maxSuggestions);
    } catch (RuntimeException ex) {
      suggestionCache.synchronous().invalidate(key);
      log.debug(
          "[MessageInputSpellcheckSupport] blocking suggestion lookup failed for '{}'",
          candidate,
          ex);
      return List.of();
    }
  }

  private List<String> computeSuggestions(SuggestionCacheKey key) {
    String token = key.tokenLower();
    String languageTag = key.languageTag();
    SpellcheckSettings.CompletionProfile profile = key.completionProfile();
    try {
      List<RuleMatch> matches = List.of();
      JLanguageTool checker = checkerForCurrentThreadOrNull(languageTag);
      if (checker != null) {
        List<RuleMatch> checked = checker.check(token);
        if (checked != null) {
          matches = checked;
        }
      }
      String tokenLower = token.toLowerCase(Locale.ROOT);
      LinkedHashSet<String> raw = new LinkedHashSet<>();
      if (!matches.isEmpty()) {
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
            if (raw.size() >= MAX_SUGGESTION_POOL) break;
          }
          if (raw.size() >= MAX_SUGGESTION_POOL) break;
        }
      }

      for (String completion : prefixCandidatesFromLexicon(tokenLower, languageTag, profile)) {
        if (raw.size() >= MAX_TOTAL_SUGGESTION_POOL) break;
        if (completion.equalsIgnoreCase(token)) continue;
        if (isKnownNick(completion)) continue;
        if (isCustomDictionaryWord(completion)) continue;
        raw.add(completion);
      }

      if (raw.isEmpty()) return List.of();

      ArrayList<String> rawOrdered = new ArrayList<>(raw);
      ArrayList<ScoredSuggestion> ranked = new ArrayList<>(rawOrdered.size());
      for (int i = 0; i < rawOrdered.size(); i++) {
        String candidate = rawOrdered.get(i);
        String candidateLower = candidate.toLowerCase(Locale.ROOT);
        int distance = damerauLevenshteinDistance(tokenLower, candidateLower);
        if (!isPlausibleSuggestion(tokenLower, candidateLower, distance, profile)) continue;
        int frequency = localWordFrequency.getOrDefault(candidateLower, 0);
        int score = suggestionScore(tokenLower, candidateLower, distance, frequency, i, profile);
        ranked.add(new ScoredSuggestion(candidate, distance, frequency, score, i));
      }
      if (ranked.isEmpty()) return List.of();

      ranked.sort(
          (a, b) -> {
            int scoreCmp = Integer.compare(a.score(), b.score());
            if (scoreCmp != 0) return scoreCmp;
            int distanceCmp = Integer.compare(a.distance(), b.distance());
            if (distanceCmp != 0) return distanceCmp;
            int sourceCmp = Integer.compare(a.sourceRank(), b.sourceRank());
            if (sourceCmp != 0) return sourceCmp;
            int freqCmp = Integer.compare(b.frequency(), a.frequency());
            if (freqCmp != 0) return freqCmp;
            return String.CASE_INSENSITIVE_ORDER.compare(a.word(), b.word());
          });

      ArrayList<String> ordered = new ArrayList<>(ranked.size());
      for (ScoredSuggestion suggestion : ranked) {
        ordered.add(suggestion.word());
      }
      return List.copyOf(ordered);
    } catch (Throwable ex) {
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

  private Single<SpellcheckOutcome> evaluateSpellcheckRequest(SpellcheckRequest request) {
    return Single.fromCallable(() -> evaluateSpellcheckRequestBlocking(request))
        .subscribeOn(ensureSpellcheckScheduler());
  }

  static void shutdownSharedResources() {
    synchronized (SPELLCHECK_EXECUTOR_LOCK) {
      if (spellcheckExecutor != null) {
        try {
          spellcheckExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
      }
      spellcheckExecutor = null;
      spellcheckScheduler = null;
    }
    CHECKERS_BY_THREAD.remove();
    PREFIX_PRIORITY_BY_LANG.clear();
    PREFIX_LEXICON_BY_LANG.clear();
  }

  private static ExecutorService ensureSpellcheckExecutor() {
    synchronized (SPELLCHECK_EXECUTOR_LOCK) {
      if (spellcheckExecutor == null
          || spellcheckExecutor.isShutdown()
          || spellcheckExecutor.isTerminated()) {
        spellcheckExecutor = newSpellcheckExecutor();
        spellcheckScheduler = Schedulers.from(spellcheckExecutor);
      }
      return spellcheckExecutor;
    }
  }

  private static io.reactivex.rxjava3.core.Scheduler ensureSpellcheckScheduler() {
    synchronized (SPELLCHECK_EXECUTOR_LOCK) {
      ensureSpellcheckExecutor();
      return spellcheckScheduler;
    }
  }

  private static ExecutorService newSpellcheckExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-spellcheck");
  }

  private SpellcheckOutcome evaluateSpellcheckRequestBlocking(SpellcheckRequest request) {
    if (request == null) {
      return SpellcheckOutcome.clear(0L, "");
    }
    SpellcheckSettings snapshot = request.settings();
    String text = request.text();
    if (snapshot == null
        || !snapshot.enabled()
        || !snapshot.underlineEnabled()
        || text == null
        || text.isBlank()
        || startsWithSlashCommand(text)) {
      return SpellcheckOutcome.clear(request.requestId(), text);
    }
    List<MisspellingRange> ranges =
        findMisspellings(
            text, request.nickSnapshot(), snapshot.languageTag(), snapshot.completionProfile());
    return SpellcheckOutcome.apply(request.requestId(), text, ranges);
  }

  private void applySpellcheckOutcome(SpellcheckOutcome outcome) {
    if (outcome == null) return;
    if (outcome.requestId() != spellcheckRequestSeq.get()) return;
    if (outcome.clearOnly()) {
      clearMisspellingHighlights();
      return;
    }
    applyMisspellingHighlights(outcome.requestId(), outcome.checkedText(), outcome.ranges());
  }

  private List<MisspellingRange> findMisspellings(
      String text,
      Map<String, String> nickSnapshot,
      String languageTag,
      SpellcheckSettings.CompletionProfile completionProfile) {
    if (text == null || text.isBlank()) return List.of();
    try {
      JLanguageTool checker = checkerForCurrentThreadOrNull(languageTag);
      if (checker == null) return List.of();
      List<RuleMatch> matches = checker.check(text);
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

        out.add(
            new MisspellingRange(
                start,
                end,
                token,
                collectMisspellingSuggestions(
                    token, languageTag, completionProfile, match.getSuggestedReplacements())));
      }
      if (out.isEmpty()) return List.of();
      return List.copyOf(out);
    } catch (Throwable ex) {
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

    ArrayList<MisspellingRange> appliedRanges = new ArrayList<>(ranges.size());
    Highlighter highlighter = input.getHighlighter();
    for (MisspellingRange r : ranges) {
      try {
        Object tag = highlighter.addHighlight(r.start(), r.end(), misspellingPainter);
        misspellingHighlightTags.add(tag);
        appliedRanges.add(r);
      } catch (Exception ignored) {
      }
    }
    misspellingSnapshot = new MisspellingSnapshot(checkedText, List.copyOf(appliedRanges));
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
    misspellingSnapshot = MisspellingSnapshot.empty();
  }

  private List<String> collectMisspellingSuggestions(
      String token,
      String languageTag,
      SpellcheckSettings.CompletionProfile completionProfile,
      List<String> ruleSuggestions) {
    if (token == null || token.isBlank()) return List.of();
    String tokenLower = token.toLowerCase(Locale.ROOT);

    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (ruleSuggestions != null && !ruleSuggestions.isEmpty()) {
      for (String suggestion : ruleSuggestions) {
        String cleaned = normalizeToken(suggestion);
        if (cleaned.isEmpty()) continue;
        if (cleaned.equalsIgnoreCase(token)) continue;
        if (!isWordCandidate(cleaned)) continue;
        if (isKnownNick(cleaned)) continue;
        if (isCustomDictionaryWord(cleaned)) continue;
        out.add(cleaned);
        if (out.size() >= MAX_SUGGESTION_POOL) break;
      }
    }

    if (out.size() < MAX_TOTAL_SUGGESTION_POOL) {
      for (String completion :
          prefixCandidatesFromLexicon(tokenLower, languageTag, completionProfile)) {
        if (completion.equalsIgnoreCase(token)) continue;
        if (isKnownNick(completion)) continue;
        if (isCustomDictionaryWord(completion)) continue;
        out.add(completion);
        if (out.size() >= MAX_TOTAL_SUGGESTION_POOL) break;
      }
    }

    if (out.isEmpty()) return List.of();
    ArrayList<String> ordered = new ArrayList<>(out);
    return filterAndLimit(ordered, MAX_TOTAL_SUGGESTION_POOL);
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

  private void recordWordsFromInputForRanking(String text) {
    if (text == null || text.isBlank()) return;

    LinkedHashSet<String> observed = new LinkedHashSet<>();
    int start = -1;
    for (int i = 0; i <= text.length(); i++) {
      char c = (i < text.length()) ? text.charAt(i) : ' ';
      if (i < text.length() && isWordChar(c)) {
        if (start < 0) start = i;
        continue;
      }
      if (start < 0) continue;

      String token = normalizeToken(text.substring(start, i));
      start = -1;
      if (!shouldTrackTokenForRanking(token)) continue;
      observed.add(token.toLowerCase(Locale.ROOT));
    }

    if (observed.isEmpty()) return;
    for (String tokenLower : observed) {
      localWordFrequency.merge(
          tokenLower, 1, (prev, one) -> Math.min(MAX_TRACKED_WORD_FREQ, prev + 1));
    }
    pruneTrackedWordFrequency();
  }

  private boolean shouldTrackTokenForRanking(String token) {
    if (token == null || token.isBlank()) return false;
    if (!isWordCandidate(token)) return false;
    if (token.length() < MIN_FUZZY_TOKEN_LENGTH) return false;
    if (isKnownNick(token)) return false;
    if (isCustomDictionaryWord(token)) return false;
    return true;
  }

  private void pruneTrackedWordFrequency() {
    if (localWordFrequency.size() <= MAX_TRACKED_WORDS) return;

    localWordFrequency
        .entrySet()
        .removeIf(entry -> entry.getValue() <= 1 && localWordFrequency.size() > MAX_TRACKED_WORDS);
    if (localWordFrequency.size() <= MAX_TRACKED_WORDS) return;

    ArrayList<Map.Entry<String, Integer>> ranked = new ArrayList<>(localWordFrequency.entrySet());
    ranked.sort(Map.Entry.comparingByValue());
    int removeCount = localWordFrequency.size() - MAX_TRACKED_WORDS;
    for (int i = 0; i < ranked.size() && removeCount > 0; i++) {
      Map.Entry<String, Integer> entry = ranked.get(i);
      if (localWordFrequency.remove(entry.getKey(), entry.getValue())) {
        removeCount--;
      }
    }
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

  private static int commonPrefixLength(String a, String b) {
    int max = Math.min(a.length(), b.length());
    int i = 0;
    while (i < max && a.charAt(i) == b.charAt(i)) {
      i++;
    }
    return i;
  }

  private static boolean isPlausibleSuggestion(
      String tokenLower, String candidateLower, int distance) {
    return isPlausibleSuggestion(tokenLower, candidateLower, distance, DEFAULT_COMPLETION_PROFILE);
  }

  private static boolean isPlausibleSuggestion(
      String tokenLower,
      String candidateLower,
      int distance,
      SpellcheckSettings.CompletionProfile profile) {
    SpellcheckSettings.CompletionProfile effective =
        profile != null ? profile : DEFAULT_COMPLETION_PROFILE;
    if (isPrefixCompletionCandidate(tokenLower, candidateLower, effective)) return true;
    if (distance <= 0) return false;
    int tokenLen = tokenLower.length();
    int candidateLen = candidateLower.length();
    int lengthDelta = Math.abs(tokenLen - candidateLen);
    int maxDistance =
        tokenLen <= MIN_FUZZY_TOKEN_LENGTH
            ? SHORT_TOKEN_MAX_DISTANCE
            : (tokenLen <= 6 ? MEDIUM_TOKEN_MAX_DISTANCE : LONG_TOKEN_MAX_DISTANCE);
    if (distance > maxDistance) return false;
    if (lengthDelta > Math.max(1, tokenLen / 2)) return false;
    if (tokenLen <= MIN_FUZZY_TOKEN_LENGTH && tokenLower.charAt(0) != candidateLower.charAt(0))
      return false;
    return true;
  }

  private int suggestionScore(
      String tokenLower, String candidateLower, int distance, int localFrequency, int sourceRank) {
    SpellcheckSettings snapshot = settings;
    SpellcheckSettings.CompletionProfile profile =
        snapshot != null ? snapshot.completionProfile() : DEFAULT_COMPLETION_PROFILE;
    return suggestionScore(
        tokenLower, candidateLower, distance, localFrequency, sourceRank, profile);
  }

  private int suggestionScore(
      String tokenLower,
      String candidateLower,
      int distance,
      int localFrequency,
      int sourceRank,
      SpellcheckSettings.CompletionProfile profile) {
    SpellcheckSettings.CompletionProfile effective =
        profile != null ? profile : DEFAULT_COMPLETION_PROFILE;
    boolean prefixCompletion = isPrefixCompletionCandidate(tokenLower, candidateLower, effective);
    int lengthDelta = Math.abs(tokenLower.length() - candidateLower.length());
    int score = distance * (prefixCompletion ? PREFIX_COMPLETION_DISTANCE_WEIGHT : 100);
    score += lengthDelta * (prefixCompletion ? PREFIX_COMPLETION_LENGTH_DELTA_WEIGHT : 18);
    score += prefixPriority(tokenLower, candidateLower) * 45;
    score += Math.min(sourceRank, 8) * effective.sourceOrderWeight();
    score -= commonPrefixLength(tokenLower, candidateLower) * 14;
    if (tokenLower.charAt(0) != candidateLower.charAt(0)) {
      score += 35;
    }
    if (isAdjacentTranspositionTypo(tokenLower, candidateLower)) {
      score -= TRANSPOSE_BONUS_SCORE;
    }
    if (tokenLower.length() <= MIN_FUZZY_TOKEN_LENGTH && distance > 1) {
      score += 120;
    }
    if (prefixCompletion) {
      score -= effective.prefixCompletionBonusScore();
    }
    score -= Math.min(localFrequency, MAX_TRACKED_WORD_FREQ) * 7;
    return score;
  }

  private static boolean isPrefixCompletionCandidate(String tokenLower, String candidateLower) {
    return isPrefixCompletionCandidate(tokenLower, candidateLower, DEFAULT_COMPLETION_PROFILE);
  }

  private static boolean isPrefixCompletionCandidate(
      String tokenLower, String candidateLower, SpellcheckSettings.CompletionProfile profile) {
    SpellcheckSettings.CompletionProfile effective =
        profile != null ? profile : DEFAULT_COMPLETION_PROFILE;
    if (tokenLower == null || candidateLower == null) return false;
    if (tokenLower.length() < effective.minPrefixCompletionTokenLength()) return false;
    if (candidateLower.length() <= tokenLower.length()) return false;
    if (tokenLower.length() == 2 && candidateLower.length() < 4) return false;
    if (!candidateLower.startsWith(tokenLower)) return false;
    return (candidateLower.length() - tokenLower.length())
        <= effective.maxPrefixCompletionExtraChars();
  }

  private static List<String> prefixCandidatesFromLexicon(String tokenLower, String languageTag) {
    return prefixCandidatesFromLexicon(tokenLower, languageTag, DEFAULT_COMPLETION_PROFILE);
  }

  private static List<String> prefixCandidatesFromLexicon(
      String tokenLower, String languageTag, SpellcheckSettings.CompletionProfile profile) {
    SpellcheckSettings.CompletionProfile effective =
        profile != null ? profile : DEFAULT_COMPLETION_PROFILE;
    if (tokenLower == null || tokenLower.length() < effective.minPrefixCompletionTokenLength()) {
      return List.of();
    }
    String normalizedLanguageTag = SpellcheckSettings.normalizeLanguageTag(languageTag);
    List<String> priorityWords =
        PREFIX_PRIORITY_BY_LANG.computeIfAbsent(
            normalizedLanguageTag,
            MessageInputSpellcheckSupport::loadPriorityPrefixWordsForLanguage);
    List<String> lexicon =
        PREFIX_LEXICON_BY_LANG.computeIfAbsent(
            normalizedLanguageTag, MessageInputSpellcheckSupport::loadPrefixLexiconForLanguage);
    if (priorityWords.isEmpty() && lexicon.isEmpty()) return List.of();

    int maxCandidates = Math.max(1, effective.maxPrefixLexiconCandidates());
    LinkedHashSet<String> deduped = new LinkedHashSet<>(maxCandidates);
    if (!priorityWords.isEmpty()) {
      for (String priorityWord : priorityWords) {
        if (!isPrefixCompletionCandidate(tokenLower, priorityWord, effective)) continue;
        deduped.add(priorityWord);
        if (deduped.size() >= maxCandidates) break;
      }
    }
    if (deduped.size() >= maxCandidates || lexicon.isEmpty()) {
      return deduped.isEmpty() ? List.of() : List.copyOf(deduped);
    }

    int start = Collections.binarySearch(lexicon, tokenLower);
    if (start < 0) start = -(start + 1);
    for (int i = start; i < lexicon.size() && deduped.size() < maxCandidates; i++) {
      String candidate = lexicon.get(i);
      if (!candidate.startsWith(tokenLower)) break;
      if (!isPrefixCompletionCandidate(tokenLower, candidate, effective)) continue;
      deduped.add(candidate);
    }
    return deduped.isEmpty() ? List.of() : List.copyOf(deduped);
  }

  private static List<String> loadPriorityPrefixWordsForLanguage(String normalizedLanguageTag) {
    if (normalizedLanguageTag == null
        || !normalizedLanguageTag.toLowerCase(Locale.ROOT).startsWith("en")) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    addPriorityLexiconWords(out, EN_PREFIX_COMPLETIONS_RESOURCE);
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private static List<String> loadPrefixLexiconForLanguage(String normalizedLanguageTag) {
    if (normalizedLanguageTag == null
        || !normalizedLanguageTag.toLowerCase(Locale.ROOT).startsWith("en")) {
      return List.of();
    }

    TreeSet<String> out = new TreeSet<>();
    addLexiconWords(out, EN_PREFIX_COMPLETIONS_RESOURCE);
    addLexiconWords(out, "/org/languagetool/resource/en/common_words.txt");
    addLexiconWords(out, "/org/languagetool/resource/en/added.txt");
    addLexiconWords(out, "/org/languagetool/resource/en/hunspell/spelling.txt");

    if ("en-GB".equalsIgnoreCase(normalizedLanguageTag)) {
      addLexiconWords(out, "/org/languagetool/resource/en/hunspell/spelling_en-GB.txt");
    } else {
      addLexiconWords(out, "/org/languagetool/resource/en/hunspell/spelling_en-US.txt");
    }

    addConfusionSetLexiconWords(out, "/org/languagetool/resource/en/confusion_sets_extended.txt");
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private static void addLexiconWords(Set<String> out, String resourcePath) {
    try (InputStream in = MessageInputSpellcheckSupport.class.getResourceAsStream(resourcePath)) {
      if (in == null) return;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          int comment = line.indexOf('#');
          String cleaned = (comment >= 0 ? line.substring(0, comment) : line).trim();
          if (cleaned.isEmpty()) continue;
          addLexiconWord(out, cleaned);
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static void addPriorityLexiconWords(Set<String> out, String resourcePath) {
    try (InputStream in = MessageInputSpellcheckSupport.class.getResourceAsStream(resourcePath)) {
      if (in == null) return;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          int comment = line.indexOf('#');
          String cleaned = (comment >= 0 ? line.substring(0, comment) : line).trim();
          if (cleaned.isEmpty()) continue;
          String normalized = normalizeToken(cleaned).toLowerCase(Locale.ROOT);
          if (!isWordCandidate(normalized)) continue;
          if (normalized.length() < MIN_PREFIX_LEXICON_WORD_LENGTH) continue;
          out.add(normalized);
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static void addConfusionSetLexiconWords(Set<String> out, String resourcePath) {
    try (InputStream in = MessageInputSpellcheckSupport.class.getResourceAsStream(resourcePath)) {
      if (in == null) return;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          int comment = line.indexOf('#');
          String cleaned = (comment >= 0 ? line.substring(0, comment) : line).trim();
          if (cleaned.isEmpty()) continue;
          String[] parts = cleaned.split(";");
          for (String part : parts) {
            addLexiconWord(out, part.trim());
          }
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static void addLexiconWord(Set<String> out, String rawWord) {
    if (rawWord == null || rawWord.isBlank()) return;
    String normalized = normalizeToken(rawWord).toLowerCase(Locale.ROOT);
    if (!isWordCandidate(normalized)) return;
    if (normalized.length() < MIN_PREFIX_LEXICON_WORD_LENGTH) return;

    out.add(normalized);
    for (String derived : derivedBaseForms(normalized)) {
      if (isWordCandidate(derived) && derived.length() >= MIN_PREFIX_LEXICON_WORD_LENGTH) {
        out.add(derived);
      }
    }
  }

  private static List<String> derivedBaseForms(String word) {
    if (word == null || word.isBlank()) return List.of();
    ArrayList<String> out = new ArrayList<>(4);
    if (word.length() > 6 && word.endsWith("ing")) {
      out.add(word.substring(0, word.length() - 3));
    }
    if (word.length() > 5 && word.endsWith("ed")) {
      out.add(word.substring(0, word.length() - 2));
    }
    if (word.length() > 5 && word.endsWith("es")) {
      out.add(word.substring(0, word.length() - 2));
    }
    if (word.length() > 5 && word.endsWith("ies")) {
      out.add(word.substring(0, word.length() - 3) + "y");
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private static boolean isAdjacentTranspositionTypo(String tokenLower, String candidateLower) {
    if (tokenLower == null || candidateLower == null) return false;
    if (tokenLower.length() != candidateLower.length()) return false;
    if (tokenLower.length() < 2) return false;

    int firstMismatch = -1;
    int secondMismatch = -1;
    for (int i = 0; i < tokenLower.length(); i++) {
      if (tokenLower.charAt(i) == candidateLower.charAt(i)) continue;
      if (firstMismatch < 0) {
        firstMismatch = i;
        continue;
      }
      if (secondMismatch < 0) {
        secondMismatch = i;
        continue;
      }
      return false;
    }

    if (firstMismatch < 0 || secondMismatch < 0) return false;
    if (secondMismatch != firstMismatch + 1) return false;
    return tokenLower.charAt(firstMismatch) == candidateLower.charAt(secondMismatch)
        && tokenLower.charAt(secondMismatch) == candidateLower.charAt(firstMismatch);
  }

  private static int damerauLevenshteinDistance(String a, String b) {
    if (a.equals(b)) return 0;
    if (a.isEmpty()) return b.length();
    if (b.isEmpty()) return a.length();

    int aLen = a.length();
    int bLen = b.length();
    int[][] dp = new int[aLen + 1][bLen + 1];
    for (int i = 0; i <= aLen; i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j <= bLen; j++) {
      dp[0][j] = j;
    }

    for (int i = 1; i <= aLen; i++) {
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= bLen; j++) {
        int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
        int insertion = dp[i][j - 1] + 1;
        int deletion = dp[i - 1][j] + 1;
        int substitution = dp[i - 1][j - 1] + cost;
        int value = Math.min(Math.min(insertion, deletion), substitution);
        if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
          value = Math.min(value, dp[i - 2][j - 2] + 1);
        }
        dp[i][j] = value;
      }
    }
    return dp[aLen][bLen];
  }

  private static boolean isWordChar(char c) {
    return Character.isLetter(c) || c == '\'' || c == '-';
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  private static boolean isCaretInsideMisspelling(int caret, int start, int end) {
    return caret >= start && caret <= end;
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

  private static JLanguageTool checkerForCurrentThreadOrNull(String languageTag) {
    String normalized = SpellcheckSettings.normalizeLanguageTag(languageTag);
    String shortCode = "en-GB".equalsIgnoreCase(normalized) ? "en-GB" : "en-US";
    try {
      return checkerForCurrentThread(normalized);
    } catch (Throwable t) {
      maybeLogCheckerFailure(shortCode, t);
      return null;
    }
  }

  private static void maybeLogCheckerFailure(String shortCode, Throwable t) {
    long now = System.currentTimeMillis();
    long last = LAST_CHECKER_FAILURE_LOG_MS.get();
    if (last > 0 && (now - last) < CHECKER_FAILURE_LOG_INTERVAL_MS) return;
    if (!LAST_CHECKER_FAILURE_LOG_MS.compareAndSet(last, now)) return;
    log.warn(
        "[MessageInputSpellcheckSupport] LanguageTool unavailable for '{}'; "
            + "spell underlines/corrections are temporarily disabled",
        shortCode,
        t);
  }

  private static JLanguageTool createChecker(String languageTag) {
    String normalized = SpellcheckSettings.normalizeLanguageTag(languageTag);
    String shortCode = "en-GB".equalsIgnoreCase(normalized) ? "en-GB" : "en-US";
    Language lang = Languages.getLanguageForShortCode(shortCode);
    if (lang == null) {
      lang = Languages.getLanguageForShortCode("en-US");
    }
    if (lang == null) {
      throw new IllegalStateException("LanguageTool language unavailable for " + shortCode);
    }
    return new JLanguageTool(lang);
  }

  static record MisspelledWord(int start, int end, String token, List<String> suggestions) {
    MisspelledWord {
      token = token == null ? "" : token;
      suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
  }

  private record MisspellingRange(int start, int end, String token, List<String> suggestions) {
    MisspellingRange {
      token = token == null ? "" : token;
      suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
  }

  private record MisspellingSnapshot(String text, List<MisspellingRange> ranges) {
    private static MisspellingSnapshot empty() {
      return new MisspellingSnapshot("", List.of());
    }

    MisspellingSnapshot {
      text = text == null ? "" : text;
      ranges = ranges == null ? List.of() : List.copyOf(ranges);
    }
  }

  private record ScoredSuggestion(
      String word, int distance, int frequency, int score, int sourceRank) {}

  private record SuggestionCacheKey(
      String tokenLower,
      String languageTag,
      SpellcheckSettings.CompletionProfile completionProfile) {}

  private record SpellcheckRequest(
      long requestId, String text, SpellcheckSettings settings, Map<String, String> nickSnapshot) {
    SpellcheckRequest {
      text = text == null ? "" : text;
      settings = settings == null ? SpellcheckSettings.defaults() : settings;
      nickSnapshot = nickSnapshot == null ? Map.of() : Map.copyOf(nickSnapshot);
    }
  }

  private record SpellcheckOutcome(
      long requestId, String checkedText, List<MisspellingRange> ranges, boolean clearOnly) {
    private static SpellcheckOutcome clear(long requestId, String checkedText) {
      return new SpellcheckOutcome(requestId, checkedText, List.of(), true);
    }

    private static SpellcheckOutcome apply(
        long requestId, String checkedText, List<MisspellingRange> ranges) {
      return new SpellcheckOutcome(requestId, checkedText, ranges, false);
    }

    SpellcheckOutcome {
      checkedText = checkedText == null ? "" : checkedText;
      ranges = ranges == null ? List.of() : List.copyOf(ranges);
    }
  }
}
