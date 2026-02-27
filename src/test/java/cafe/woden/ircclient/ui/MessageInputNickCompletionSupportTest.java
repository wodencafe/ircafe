package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.junit.jupiter.api.Test;

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
  void completionPopupRanksNicksAboveWordsAndKeepsWordLikelihoodOrder() throws Exception {
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
    assertTrue(replacements.contains("alice"));
    assertTrue(replacements.contains("alina"));
    assertTrue(replacements.contains("almost"));
    assertTrue(replacements.contains("almond"));
    assertTrue(replacements.indexOf("alice") < replacements.indexOf("almost"));
    assertTrue(replacements.indexOf("alina") < replacements.indexOf("almost"));
    assertTrue(replacements.indexOf("almost") < replacements.indexOf("almond"));
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
  void tabGuardDoesNotForcePopupWhenOnlyWordSuggestionsExist() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(
            new JPanel(), input, undoSupport, (token, maxSuggestions) -> List.of("hello"));
    support.setNickCompletions(List.of("alice"));

    input.setText("helo");
    input.setCaretPosition(4);

    assertFalse(shouldForcePopupInsteadOfImmediateCompletion(support, "helo", 4));
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
    Field field = MessageInputNickCompletionSupport.class.getDeclaredField("completionProvider");
    field.setAccessible(true);
    CompletionProvider provider = (CompletionProvider) field.get(support);
    List<Completion> completions = provider.getCompletions(input);
    return completions.stream().map(Completion::getReplacementText).toList();
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
}
