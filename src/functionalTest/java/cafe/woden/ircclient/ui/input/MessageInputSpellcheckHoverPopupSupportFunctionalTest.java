package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.settings.SpellcheckSettings;
import java.awt.Component;
import java.awt.Point;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Popup;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MessageInputSpellcheckHoverPopupSupportFunctionalTest {

  @Test
  void popupShowsAndClickingSuggestionReplacesMisspelledWord() throws Exception {
    JTextField input = new JTextField("teh cat");
    SpellcheckSettings settings = withHoverSuggestionsEnabled(SpellcheckSettings.defaults());
    MessageInputSpellcheckSupport spellcheckSupport =
        new MessageInputSpellcheckSupport(input, settings);
    CountingPopupDisplay popupDisplay = new CountingPopupDisplay();
    MessageInputSpellcheckHoverPopupSupport popupSupport =
        new MessageInputSpellcheckHoverPopupSupport(
            new JPanel(), input, spellcheckSupport, settings, popupDisplay);

    try {
      MessageInputSpellcheckSupport.MisspelledWord misspelled =
          new MessageInputSpellcheckSupport.MisspelledWord(
              0, 3, "teh", List.of("the", "ten", "tech"));

      onEdt(
          () ->
              popupSupport.showSuggestionsAtScreen(
                  misspelled, List.of("the", "ten", "tech"), new Point(20, 20)));

      assertTrue(onEdtCall(popupSupport::isPopupVisible));
      assertEquals(1, popupDisplay.showCount.get());

      JButton suggestionButton = onEdtCall(() -> firstSuggestionButton(popupSupport));
      assertNotNull(suggestionButton, "expected at least one suggestion button");
      onEdt(suggestionButton::doClick);

      assertEquals("the cat", onEdtCall(input::getText));
      assertFalse(onEdtCall(popupSupport::isPopupVisible));
    } finally {
      onEdt(popupSupport::shutdown);
      spellcheckSupport.shutdown();
    }
  }

  @Test
  void hoverPopupDoesNotShowWhenHoverSuggestionsSettingIsDisabled() throws Exception {
    JTextField input = new JTextField("teh cat");
    SpellcheckSettings enabledSettings = SpellcheckSettings.defaults();
    SpellcheckSettings disabledHoverSettings =
        new SpellcheckSettings(
            enabledSettings.enabled(),
            enabledSettings.underlineEnabled(),
            enabledSettings.suggestOnTabEnabled(),
            false,
            enabledSettings.languageTag(),
            enabledSettings.customDictionary(),
            enabledSettings.completionPreset(),
            enabledSettings.customMinPrefixCompletionTokenLength(),
            enabledSettings.customMaxPrefixCompletionExtraChars(),
            enabledSettings.customMaxPrefixLexiconCandidates(),
            enabledSettings.customPrefixCompletionBonusScore(),
            enabledSettings.customSourceOrderWeight());

    MessageInputSpellcheckSupport spellcheckSupport =
        new MessageInputSpellcheckSupport(input, enabledSettings);
    CountingPopupDisplay popupDisplay = new CountingPopupDisplay();
    MessageInputSpellcheckHoverPopupSupport popupSupport =
        new MessageInputSpellcheckHoverPopupSupport(
            new JPanel(), input, spellcheckSupport, disabledHoverSettings, popupDisplay);

    try {
      MessageInputSpellcheckSupport.MisspelledWord misspelled =
          new MessageInputSpellcheckSupport.MisspelledWord(0, 3, "teh", List.of("the"));

      onEdt(
          () ->
              popupSupport.showSuggestionsAtScreen(misspelled, List.of("the"), new Point(20, 20)));

      assertFalse(onEdtCall(popupSupport::isPopupVisible));
      assertEquals(0, popupDisplay.showCount.get());
    } finally {
      onEdt(popupSupport::shutdown);
      spellcheckSupport.shutdown();
    }
  }

  @Test
  void popupPositionStaysStableForSameMisspelledWordAcrossMouseMoves() throws Exception {
    JTextField input = new JTextField("teh cat");
    SpellcheckSettings settings = withHoverSuggestionsEnabled(SpellcheckSettings.defaults());
    MessageInputSpellcheckSupport spellcheckSupport =
        new MessageInputSpellcheckSupport(input, settings);
    CountingPopupDisplay popupDisplay = new CountingPopupDisplay();
    MessageInputSpellcheckHoverPopupSupport popupSupport =
        new MessageInputSpellcheckHoverPopupSupport(
            new JPanel(), input, spellcheckSupport, settings, popupDisplay);

    try {
      MessageInputSpellcheckSupport.MisspelledWord misspelled =
          new MessageInputSpellcheckSupport.MisspelledWord(0, 3, "teh", List.of("the", "ten"));

      onEdt(
          () ->
              popupSupport.showSuggestionsAtScreen(
                  misspelled, List.of("the", "ten"), new Point(20, 20)));
      int firstX = onEdtCall(() -> popupX(popupSupport));
      int firstY = onEdtCall(() -> popupY(popupSupport));
      assertEquals(1, popupDisplay.showCount.get(), "popup should be shown once initially");

      onEdt(
          () ->
              invokeShowPopup(popupSupport, misspelled, List.of("the", "ten"), new Point(240, 20)));

      assertEquals(1, popupDisplay.showCount.get(), "popup should not be recreated for same word");
      assertEquals(firstX, onEdtCall(() -> popupX(popupSupport)));
      assertEquals(firstY, onEdtCall(() -> popupY(popupSupport)));
    } finally {
      onEdt(popupSupport::shutdown);
      spellcheckSupport.shutdown();
    }
  }

  private static JButton firstSuggestionButton(MessageInputSpellcheckHoverPopupSupport support)
      throws Exception {
    JPanel panel = popupPanel(support);
    for (Component c : panel.getComponents()) {
      if (c instanceof JButton b) {
        return b;
      }
    }
    return null;
  }

  private static JPanel popupPanel(MessageInputSpellcheckHoverPopupSupport support)
      throws Exception {
    Field field = MessageInputSpellcheckHoverPopupSupport.class.getDeclaredField("popupPanel");
    field.setAccessible(true);
    return (JPanel) field.get(support);
  }

  private static int popupX(MessageInputSpellcheckHoverPopupSupport support) throws Exception {
    Field field = MessageInputSpellcheckHoverPopupSupport.class.getDeclaredField("popupX");
    field.setAccessible(true);
    return (int) field.get(support);
  }

  private static int popupY(MessageInputSpellcheckHoverPopupSupport support) throws Exception {
    Field field = MessageInputSpellcheckHoverPopupSupport.class.getDeclaredField("popupY");
    field.setAccessible(true);
    return (int) field.get(support);
  }

  private static void invokeShowPopup(
      MessageInputSpellcheckHoverPopupSupport support,
      MessageInputSpellcheckSupport.MisspelledWord word,
      List<String> suggestions,
      Point inputPoint)
      throws Exception {
    Method method =
        MessageInputSpellcheckHoverPopupSupport.class.getDeclaredMethod(
            "showPopup",
            MessageInputSpellcheckSupport.MisspelledWord.class,
            List.class,
            Point.class);
    method.setAccessible(true);
    method.invoke(support, word, suggestions, inputPoint);
  }

  private static void onEdt(ThrowingRunnable runnable) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    AtomicReference<T> out = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  private static SpellcheckSettings withHoverSuggestionsEnabled(SpellcheckSettings current) {
    return new SpellcheckSettings(
        current.enabled(),
        current.underlineEnabled(),
        current.suggestOnTabEnabled(),
        true,
        current.languageTag(),
        current.customDictionary(),
        current.completionPreset(),
        current.customMinPrefixCompletionTokenLength(),
        current.customMaxPrefixCompletionExtraChars(),
        current.customMaxPrefixLexiconCandidates(),
        current.customPrefixCompletionBonusScore(),
        current.customSourceOrderWeight());
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private static final class CountingPopupDisplay
      implements MessageInputSpellcheckHoverPopupSupport.PopupDisplay {
    private final AtomicInteger showCount = new AtomicInteger();

    @Override
    public Popup create(
        javax.swing.JComponent owner, javax.swing.JComponent content, int x, int y) {
      return new Popup() {
        @Override
        public void show() {
          showCount.incrementAndGet();
        }

        @Override
        public void hide() {
          // no-op
        }
      };
    }
  }
}
