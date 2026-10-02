package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.commands.BackendNamedCommandCatalog;
import cafe.woden.ircclient.app.commands.SlashCommandPresentationCatalog;
import cafe.woden.ircclient.app.commands.builtins.BuiltInQuasselBackendNamedCommandHandler;
import cafe.woden.ircclient.ui.input.spi.MessageInputWordSuggestionProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageInputNickCompletionSupportTest {

  @Test
  void armsPendingSuffixWhenTabOnlyExpandsSharedNickPrefix() throws Exception {
    MessageInputNickCompletionSupport support = newSupport(List.of("alice", "alina"));

    assertTrue(shouldArmPendingSuffix(support, "a", 1, "al", 2));
  }

  @Test
  void doesNotArmPendingSuffixWhenCompletionAlreadyResolvedToKnownNick() throws Exception {
    MessageInputNickCompletionSupport support = newSupport(List.of("alice", "alina"));

    assertFalse(shouldArmPendingSuffix(support, "ali", 3, "alice ", 6));
  }

  @Test
  void doesNotArmPendingSuffixWhenCaretIsOutsideFirstWord() throws Exception {
    MessageInputNickCompletionSupport support = newSupport(List.of("alice", "alina"));

    assertFalse(shouldArmPendingSuffix(support, "hello al", 8, "hello al", 8));
  }

  @Test
  void completionHintPrefersNickBeforeWordSuggestion() {
    MessageInputNickCompletionSupport support =
        newSupport(List.of("alice"), (token, maxSuggestions) -> List.of("align", "alike"));

    assertEquals("alice", support.firstCompletionHint("ali"));
  }

  @Test
  void completionHintDoesNotFallbackToWordSuggestionWhenNoNickMatches() {
    MessageInputNickCompletionSupport support =
        newSupport(List.of("alice"), (token, maxSuggestions) -> List.of("hello"));

    assertNull(support.firstCompletionHint("helo"));
  }

  @Test
  void includesWordSuggestionsInCompletionPopupForNonCommandInput() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(), input, undoSupport, (token, maxSuggestions) -> List.of("hello", "help"));

    input.setText("hel");
    input.setCaretPosition(3);

    List<String> replacements = replacementTextsForCurrentToken(support, input);
    assertTrue(replacements.contains("hello"));
    assertTrue(replacements.contains("help"));
  }

  @Test
  void completionPopupShowsNickSuggestionsBeforeWordsWhenNickPrefixMatchesExist() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(),
            input,
            undoSupport,
            (token, maxSuggestions) -> List.of("almost", "almond"));
    support.setNickCompletions(List.of("alice", "alina"));

    input.setText("al");
    input.setCaretPosition(2);

    List<String> replacements = replacementTextsForCurrentToken(support, input);
    assertTrue(replacements.contains("alice: "));
    assertTrue(replacements.contains("alina: "));
    assertTrue(replacements.contains("almost"));
    assertTrue(replacements.contains("almond"));
    assertTrue(replacements.indexOf("alice: ") < replacements.indexOf("almost"));
    assertTrue(replacements.indexOf("alina: ") < replacements.indexOf("almost"));
    assertTrue(replacements.indexOf("almost") < replacements.indexOf("almond"));
  }

  @Test
  void completionPopupRanksClosestNickFirstWithinNickSuggestions() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(List.of("aardvark", "al", "alice"));

    input.setText("a");
    input.setCaretPosition(1);

    List<String> replacements = replacementTextsForCurrentToken(support, input);
    assertEquals("al: ", replacements.getFirst());
    assertTrue(replacements.indexOf("al: ") < replacements.indexOf("alice: "));
    assertTrue(replacements.indexOf("alice: ") < replacements.indexOf("aardvark: "));
  }

  @Test
  void completionPopupIncludesOnlyNickPrefixMatches() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(List.of("otr", "otrbot", "xxotr", "other"));

    input.setText("otr");
    input.setCaretPosition(3);

    List<String> replacements = replacementTextsForCurrentToken(support, input);
    assertEquals(List.of("otr: ", "otrbot: "), replacements);
  }

  @Test
  void completionPopupDisplaysBareNickButReplacesWithAddressingSuffixForFirstWordNick()
      throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(List.of("agarose"));

    input.setText("aga");
    input.setCaretPosition(3);

    assertEquals(List.of("agarose"), inputTextsForCurrentToken(support, input));
    assertEquals(List.of("agarose: "), replacementTextsForCurrentToken(support, input));
  }

  @Test
  void completionPopupDoesNotUseAddressingReplacementAfterFirstWord() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(List.of("agarose"));

    input.setText("hello aga");
    input.setCaretPosition(input.getText().length());

    assertEquals(List.of("agarose"), replacementTextsForCurrentToken(support, input));
  }

  @Test
  void completionPopupShowsWordSuggestionsWhenNoNickPrefixMatchesExist() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(), input, undoSupport, (token, maxSuggestions) -> List.of("hello", "help"));
    support.setNickCompletions(List.of("alice", "alina"));

    input.setText("hel");
    input.setCaretPosition(3);

    List<String> replacements = replacementTextsForCurrentToken(support, input);
    assertTrue(replacements.contains("hello"));
    assertTrue(replacements.contains("help"));
    assertFalse(replacements.contains("alice"));
    assertFalse(replacements.contains("alina"));
  }

  @Test
  void suppressesWordSuggestionsWhenInputIsSlashCommand() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(), input, undoSupport, (token, maxSuggestions) -> List.of("help"));

    input.setText("/he");
    input.setCaretPosition(3);

    List<String> replacements = replacementTextsForCurrentToken(support, input);
    assertFalse(replacements.contains("help"));
  }

  @Test
  void tabGuardForcesPopupWhenNickHintIsVisible() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(), input, undoSupport, (token, maxSuggestions) -> List.of("almost"));
    support.setNickCompletions(List.of("alice"));

    input.setText("ali");
    input.setCaretPosition(3);

    assertTrue(shouldForcePopupInsteadOfImmediateCompletion(support, "ali", 3));
  }

  @Test
  void tabGuardForcesPopupWhenOnlyWordSuggestionsExist() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(), input, undoSupport, (token, maxSuggestions) -> List.of("forensic"));
    support.setNickCompletions(List.of("alice"));

    input.setText("forensi");
    input.setCaretPosition(7);

    assertTrue(shouldForcePopupInsteadOfImmediateCompletion(support, "forensi", 7));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ali", "hello ali", "/me waves at ali", "/msg ali", "  /ME waves at ali"})
  void nickPopupPreferenceAppliesToMessagesAndCommandArguments(String text) throws Exception {
    JTextField input = new JTextField(text);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, null);
    support.setNickCompletions(List.of("alice"));
    input.setCaretPosition(text.length());

    assertTrue(shouldForcePopupInsteadOfImmediateCompletion(support, text, text.length()));
    support.setCompletionPreferences(true, true);
    assertFalse(shouldForcePopupInsteadOfImmediateCompletion(support, text, text.length()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"forensi", "hello forensi", "/me feels forensi", "  /ME feels forensi"})
  void wordSuggestionsAlwaysUsePopupInBothNickModes(String text) throws Exception {
    JTextField input = new JTextField(text);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(), input, null, (token, maxSuggestions) -> List.of("forensic"));
    support.setNickCompletions(List.of("alice"));
    input.setCaretPosition(text.length());

    assertEquals(List.of("forensic"), replacementTextsForCurrentToken(support, input));
    for (boolean cycleWithTab : List.of(false, true)) {
      support.setCompletionPreferences(cycleWithTab, true);
      assertFalse(tryCycleNickCompletion(support, text, text.length()));
      assertTrue(shouldForcePopupInsteadOfImmediateCompletion(support, text, text.length()));
    }
    assertEquals(text, input.getText());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/me waves at ali today", "/msg ali today", "hello ali today"})
  void automaticNickCompletionCyclesArgumentsAndPreservesSurroundingText(String text)
      throws Exception {
    JTextField input = new JTextField(text);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, null);
    support.setNickCompletions(List.of("alice", "alina"));
    support.setCompletionPreferences(true, true);
    input.setCaretPosition(text.indexOf("ali") + 3);

    assertTrue(tryCycleNickCompletion(support, text, input.getCaretPosition()));
    assertEquals(text.replace("ali", "alice"), input.getText());
    assertTrue(tryCycleNickCompletion(support, input.getText(), input.getCaretPosition()));
    assertEquals(text.replace("ali", "alina"), input.getText());
    assertTrue(tryCycleNickCompletion(support, input.getText(), input.getCaretPosition()));
    assertEquals(text.replace("ali", "alice"), input.getText());
  }

  @Test
  void nicknamePreferenceDoesNotInterceptSlashCommandNameCompletion() throws Exception {
    JTextField input = new JTextField("/me");
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(), input, null, (token, maxSuggestions) -> List.of("message"));
    support.setNickCompletions(List.of("megan"));
    input.setCaretPosition(3);

    for (boolean cycleWithTab : List.of(false, true)) {
      support.setCompletionPreferences(cycleWithTab, true);
      assertFalse(tryCycleNickCompletion(support, "/me", 3));
      assertFalse(shouldForcePopupInsteadOfImmediateCompletion(support, "/me", 3));
    }
    assertEquals(List.of(), replacementTextsForCurrentToken(support, input));
  }

  @Test
  void switchingFromAutomaticToPopupStopsNickCycling() throws Exception {
    JTextField input = new JTextField("/me ali");
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, null);
    support.setNickCompletions(List.of("alice", "alina"));
    support.setCompletionPreferences(true, true);
    input.setCaretPosition(input.getText().length());
    assertTrue(tryCycleNickCompletion(support, input.getText(), input.getCaretPosition()));

    support.setCompletionPreferences(false, true);
    assertFalse(tryCycleNickCompletion(support, input.getText(), input.getCaretPosition()));
    assertTrue(
        shouldForcePopupInsteadOfImmediateCompletion(
            support, input.getText(), input.getCaretPosition()));
    assertEquals("/me alice", input.getText());
  }

  @Test
  void tabCyclingReplacesFirstWordNickAndCyclesThroughMatches() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(List.of("alice", "alina"));
    support.setCompletionPreferences(true, true);

    input.setText("ali");
    input.setCaretPosition(3);

    assertTrue(tryCycleNickCompletion(support, "ali", 3));
    assertEquals("alice: ", input.getText());
    assertEquals(input.getText().length(), input.getCaretPosition());

    assertTrue(tryCycleNickCompletion(support, input.getText(), input.getCaretPosition()));
    assertEquals("alina: ", input.getText());
    assertEquals(input.getText().length(), input.getCaretPosition());
  }

  @Test
  void tabCyclingHonorsDisabledAddressSuffixPreference() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(List.of("alice"));
    support.setCompletionPreferences(true, false);

    input.setText("ali");
    input.setCaretPosition(3);

    assertTrue(tryCycleNickCompletion(support, "ali", 3));
    assertEquals("alice", input.getText());
  }

  @Test
  void tabCyclingAddressSuffixNormalizesWhitespaceBeforeRemainingText() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(List.of("alice"));
    support.setCompletionPreferences(true, true);

    input.setText("ali  hello");
    input.setCaretPosition(3);

    assertTrue(tryCycleNickCompletion(support, "ali  hello", 3));
    assertEquals("alice: hello", input.getText());
  }

  @Test
  void disabledAddressSuffixPreferenceSuppressesPopupSelectionSuffix() throws Exception {
    MessageInputNickCompletionSupport support = newSupport(List.of("alice", "alina"));
    support.setCompletionPreferences(false, false);

    assertFalse(shouldArmPendingSuffix(support, "a", 1, "al", 2));
  }

  @Test
  void shutdownIsIdempotentAfterInstall() {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.install();

    assertDoesNotThrow(support::shutdown);
    assertDoesNotThrow(support::shutdown);
  }

  @Test
  void installedBackendNamedCommandsAppearInSlashCommandCompletions() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    BackendNamedCommandCatalog catalog =
        BackendNamedCommandCatalog.fromHandlers(
            List.of(new BuiltInQuasselBackendNamedCommandHandler()));
    SlashCommandPresentationCatalog slashCommandPresentationCatalog =
        new SlashCommandPresentationCatalog(List.of(), catalog);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(),
            input,
            undoSupport,
            null,
            slashCommandPresentationCatalog.autocompleteCommands());

    input.setText("/q");
    input.setCaretPosition(2);

    List<String> replacements = replacementTextsForCurrentToken(support, input);
    assertTrue(replacements.contains("/qsetup"));
    assertTrue(replacements.contains("/quasselsetup"));
    assertTrue(replacements.contains("/qnet"));
    assertTrue(replacements.contains("/quasselnet"));
  }

  private static MessageInputNickCompletionSupport newSupport(List<String> nicks) {
    return newSupport(nicks, null);
  }

  private static MessageInputNickCompletionSupport newSupport(
      List<String> nicks, MessageInputWordSuggestionProvider suggestionProvider) {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport, suggestionProvider);
    support.setNickCompletions(nicks);
    return support;
  }

  private static List<String> replacementTextsForCurrentToken(
      MessageInputNickCompletionSupport support, JTextField input) throws Exception {
    return completionsForCurrentToken(support, input).stream()
        .map(Completion::getReplacementText)
        .toList();
  }

  private static List<String> inputTextsForCurrentToken(
      MessageInputNickCompletionSupport support, JTextField input) throws Exception {
    return completionsForCurrentToken(support, input).stream()
        .map(Completion::getInputText)
        .toList();
  }

  private static List<Completion> completionsForCurrentToken(
      MessageInputNickCompletionSupport support, JTextField input) throws Exception {
    Field field = MessageInputNickCompletionSupport.class.getDeclaredField("completionProvider");
    field.setAccessible(true);
    CompletionProvider provider = (CompletionProvider) field.get(support);
    return provider.getCompletions(input);
  }

  private static boolean shouldArmPendingSuffix(
      MessageInputNickCompletionSupport support,
      String beforeText,
      int beforeCaret,
      String afterText,
      int afterCaret)
      throws Exception {
    Method method =
        MessageInputNickCompletionSupport.class.getDeclaredMethod(
            "shouldArmPendingNickAddressSuffix", String.class, int.class, String.class, int.class);
    method.setAccessible(true);
    return (boolean) method.invoke(support, beforeText, beforeCaret, afterText, afterCaret);
  }

  private static boolean shouldForcePopupInsteadOfImmediateCompletion(
      MessageInputNickCompletionSupport support, String beforeText, int beforeCaret)
      throws Exception {
    Method method =
        MessageInputNickCompletionSupport.class.getDeclaredMethod(
            "shouldForcePopupInsteadOfImmediateCompletion", String.class, int.class);
    method.setAccessible(true);
    return (boolean) method.invoke(support, beforeText, beforeCaret);
  }

  private static boolean tryCycleNickCompletion(
      MessageInputNickCompletionSupport support, String beforeText, int beforeCaret)
      throws Exception {
    Method method =
        MessageInputNickCompletionSupport.class.getDeclaredMethod(
            "tryCycleNickCompletion", String.class, int.class);
    method.setAccessible(true);
    return (boolean) method.invoke(support, beforeText, beforeCaret);
  }
}
