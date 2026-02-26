package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.settings.SpellcheckSettings;
import java.util.List;
import java.util.Map;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.languagetool.Languages;

class MessageInputSpellcheckSupportTest {

  @Test
  void suppressesSuggestionsForKnownNick() {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());
    support.setNickWhitelist(List.of("foobarNick"));

    List<String> suggestions = support.suggestWords("foobarNick", 5);

    assertTrue(suggestions.isEmpty(), "known nick should not be treated as misspelled");
  }

  @Test
  void skipsSuggestionsForNonWordTokens() {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());

    assertTrue(support.suggestWords("https://example.com", 5).isEmpty());
    assertTrue(support.suggestWords("#ircafe", 5).isEmpty());
  }

  @Test
  void checkerCreationUsesLanguageRegistryAfterRegistryWarmup() {
    // Regression: direct new AmericanEnglish/BritishEnglish can conflict with registry singleton
    // guards in newer LT versions and throw ExceptionInInitializerError.
    Languages.getLanguageForShortCode("en-US");
    assertDoesNotThrow(() -> invokeCreateChecker("en-US"));
    assertDoesNotThrow(() -> invokeCreateChecker("en-GB"));
  }

  @Test
  void doesNotInitializeCheckerOnEdtWhenSuggestionCacheMisses() throws Exception {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());

    SwingUtilities.invokeAndWait(
        () -> {
          try {
            clearCheckerCacheForCurrentThread();
            List<String> suggestions = support.suggestWords("teh", 1);
            assertTrue(
                suggestions.isEmpty(),
                "EDT suggestion lookup should return immediately and prefetch asynchronously");
            assertEquals(
                0,
                checkerCacheSizeForCurrentThread(),
                "EDT path should not initialize LanguageTool checkers");
          } catch (Exception ex) {
            throw new AssertionError(ex);
          }
        });
  }

  @Test
  void damerauDistanceTreatsAdjacentTransposeAsSingleEdit() throws Exception {
    assertEquals(1, invokeDamerauDistance("teh", "the"));
    assertTrue(invokeDamerauDistance("teh", "there") > 1);
  }

  @Test
  void plausibilityFilterRejectsNoisyShortTokenSuggestions() throws Exception {
    assertTrue(invokeIsPlausibleSuggestion("teh", "the", 1));
    assertTrue(invokeIsPlausibleSuggestion("teh", "ten", 1));
    assertFalse(invokeIsPlausibleSuggestion("teh", "through", 4));
    assertFalse(invokeIsPlausibleSuggestion("at", "it", 1));
  }

  @Test
  void localWordFrequencyBoostsSuggestionScore() throws Exception {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());

    int baseline = invokeSuggestionScore(support, "adress", "address", 1, 0, 0);
    int boosted = invokeSuggestionScore(support, "adress", "address", 1, 6, 0);

    assertTrue(boosted < baseline);
  }

  @Test
  void transpositionCorrectionOutranksSimpleSubstitutionAtSameDistance() throws Exception {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());

    int transposeScore = invokeSuggestionScore(support, "teh", "the", 1, 0, 0);
    int substitutionScore = invokeSuggestionScore(support, "teh", "ten", 1, 0, 0);

    assertTrue(transposeScore < substitutionScore);
  }

  @Test
  void earlierLanguageToolOrderGetsSmallPriorityWhenScoresAreOtherwiseEqual() throws Exception {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());

    int early = invokeSuggestionScore(support, "adress", "address", 1, 0, 0);
    int late = invokeSuggestionScore(support, "adress", "address", 1, 0, 5);

    assertTrue(early < late);
  }

  @Test
  void plausibilityFilterAllowsLongerPrefixCompletionCandidates() throws Exception {
    assertTrue(invokeIsPlausibleSuggestion("torp", "torpedo", 3));
  }

  @Test
  void longerPrefixCompletionCanOutrankCloserNonPrefixCorrection() throws Exception {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());

    int prefixDistance = invokeDamerauDistance("torp", "torpedo");
    int nonPrefixDistance = invokeDamerauDistance("torp", "trip");
    int prefixScore = invokeSuggestionScore(support, "torp", "torpedo", prefixDistance, 0, 0);
    int nonPrefixScore = invokeSuggestionScore(support, "torp", "trip", nonPrefixDistance, 0, 0);

    assertTrue(prefixScore < nonPrefixScore);
  }

  @Test
  void prefixLexiconOffersTorpedoStyleCompletions() throws Exception {
    List<String> candidates = invokePrefixCandidatesFromLexicon("torp", "en-US");
    assertTrue(
        candidates.contains("torpedo"),
        "prefix lexicon should offer longer completion candidates for torp");
  }

  @Test
  void prefixLexiconOffersNopeForNop() throws Exception {
    List<String> candidates = invokePrefixCandidatesFromLexicon("nop", "en-US");
    assertTrue(
        candidates.contains("nope"),
        "prefix lexicon should offer conversational completion candidates for nop");
  }

  @Test
  void prefixLexiconOffersSuggestionsForNoWhenWordIsAlreadyValid() throws Exception {
    List<String> candidates = invokePrefixCandidatesFromLexicon("no", "en-US");
    assertFalse(candidates.isEmpty());
    assertTrue(candidates.contains("nope"));
    assertTrue(candidates.contains("norway"));
  }

  @Test
  void misspelledWordLookupReturnsHighlightedWordAtCaret() throws Exception {
    JTextField input = new JTextField("teh cat");
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(input, SpellcheckSettings.defaults());

    Object range = newMisspellingRange(0, 3, "teh", List.of("the"));
    invokeApplyMisspellingHighlights(support, 0L, "teh cat", List.of(range));

    input.setCaretPosition(1);
    var misspelled = support.misspelledWordAtCaret();
    assertTrue(misspelled.isPresent());
    assertEquals("teh", misspelled.get().token());
    assertEquals(List.of("the"), misspelled.get().suggestions());

    input.setCaretPosition(5);
    assertTrue(support.misspelledWordAtCaret().isEmpty());
  }

  @Test
  void misspelledWordLookupSkipsStaleSnapshotWhenInputChanges() throws Exception {
    JTextField input = new JTextField("teh cat");
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(input, SpellcheckSettings.defaults());

    Object range = newMisspellingRange(0, 3, "teh", List.of("the"));
    invokeApplyMisspellingHighlights(support, 0L, "teh cat", List.of(range));

    input.setText("the cat");
    input.setCaretPosition(1);
    assertTrue(support.misspelledWordAtCaret().isEmpty());
  }

  @Test
  void replaceMisspelledWordReplacesRangeWithSelectedSuggestion() {
    JTextField input = new JTextField("teh cat");
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(input, SpellcheckSettings.defaults());

    MessageInputSpellcheckSupport.MisspelledWord misspelledWord =
        new MessageInputSpellcheckSupport.MisspelledWord(0, 3, "teh", List.of("the"));

    assertTrue(support.replaceMisspelledWord(misspelledWord, "the"));
    assertEquals("the cat", input.getText());
  }

  private static void invokeCreateChecker(String languageTag) throws Exception {
    var method =
        MessageInputSpellcheckSupport.class.getDeclaredMethod("createChecker", String.class);
    method.setAccessible(true);
    method.invoke(null, languageTag);
  }

  private static int invokeDamerauDistance(String a, String b) throws Exception {
    var method =
        MessageInputSpellcheckSupport.class.getDeclaredMethod(
            "damerauLevenshteinDistance", String.class, String.class);
    method.setAccessible(true);
    return (int) method.invoke(null, a, b);
  }

  private static boolean invokeIsPlausibleSuggestion(String token, String candidate, int distance)
      throws Exception {
    var method =
        MessageInputSpellcheckSupport.class.getDeclaredMethod(
            "isPlausibleSuggestion", String.class, String.class, int.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, token, candidate, distance);
  }

  private static int invokeSuggestionScore(
      MessageInputSpellcheckSupport support,
      String token,
      String candidate,
      int distance,
      int localFrequency,
      int sourceRank)
      throws Exception {
    var method =
        MessageInputSpellcheckSupport.class.getDeclaredMethod(
            "suggestionScore", String.class, String.class, int.class, int.class, int.class);
    method.setAccessible(true);
    return (int) method.invoke(support, token, candidate, distance, localFrequency, sourceRank);
  }

  @SuppressWarnings("unchecked")
  private static List<String> invokePrefixCandidatesFromLexicon(String token, String languageTag)
      throws Exception {
    var method =
        MessageInputSpellcheckSupport.class.getDeclaredMethod(
            "prefixCandidatesFromLexicon", String.class, String.class);
    method.setAccessible(true);
    return (List<String>) method.invoke(null, token, languageTag);
  }

  private static Object newMisspellingRange(
      int start, int end, String token, List<String> suggestions) throws Exception {
    Class<?> rangeClass =
        Class.forName("cafe.woden.ircclient.ui.MessageInputSpellcheckSupport$MisspellingRange");
    var ctor = rangeClass.getDeclaredConstructor(int.class, int.class, String.class, List.class);
    ctor.setAccessible(true);
    return ctor.newInstance(start, end, token, suggestions);
  }

  private static void invokeApplyMisspellingHighlights(
      MessageInputSpellcheckSupport support, long requestId, String checkedText, List<?> ranges)
      throws Exception {
    var method =
        MessageInputSpellcheckSupport.class.getDeclaredMethod(
            "applyMisspellingHighlights", long.class, String.class, List.class);
    method.setAccessible(true);
    method.invoke(support, requestId, checkedText, ranges);
  }

  @SuppressWarnings("unchecked")
  private static ThreadLocal<Map<String, ?>> checkerCacheByThread() throws Exception {
    var field = MessageInputSpellcheckSupport.class.getDeclaredField("CHECKERS_BY_THREAD");
    field.setAccessible(true);
    return (ThreadLocal<Map<String, ?>>) field.get(null);
  }

  private static void clearCheckerCacheForCurrentThread() throws Exception {
    checkerCacheByThread().remove();
  }

  private static int checkerCacheSizeForCurrentThread() throws Exception {
    return checkerCacheByThread().get().size();
  }
}
