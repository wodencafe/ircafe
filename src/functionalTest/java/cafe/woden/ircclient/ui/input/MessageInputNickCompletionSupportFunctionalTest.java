package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageInputNickCompletionSupportFunctionalTest {

  @ParameterizedTest
  @ValueSource(strings = {"ali", "hello ali", "/me waves at ali", "/msg ali"})
  @EnabledIfSystemProperty(named = "java.awt.headless", matches = "false")
  void tabShowsNickPopupAndSelectionCompletesInMessageAndCommandContexts(String text)
      throws Exception {
    for (List<String> nicks : List.of(List.of("alice"), List.of("alice", "alina"))) {
      try (CompletionFixture fixture = completionFixture(text, nicks, false, false)) {
        onEdt(() -> triggerTab(fixture.input()));
        assertTrue(onEdtCall(() -> fixture.support().getAutoCompletion().isPopupVisible()));
        assertEquals(text, onEdtCall(() -> fixture.input().getText()));

        onEdt(() -> triggerKey(fixture.input(), KeyEvent.VK_ENTER));
        String expected = text.equals("ali") ? "alice: " : text.replace("ali", "alice");
        waitFor(
            () -> onEdtBoolean(() -> expected.equals(fixture.input().getText())),
            Duration.ofSeconds(2));
        assertFalse(onEdtCall(() -> fixture.support().getAutoCompletion().isPopupVisible()));
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"ali", "hello ali", "/me waves at ali", "/msg ali"})
  @EnabledIfSystemProperty(named = "java.awt.headless", matches = "false")
  void tabCompletesAndCyclesNicksImmediatelyWhenAutomaticModeEnabled(String text) throws Exception {
    try (CompletionFixture fixture =
        completionFixture(text, List.of("alice", "alina"), true, false)) {
      onEdt(() -> triggerTab(fixture.input()));
      String first = text.equals("ali") ? "alice: " : text.replace("ali", "alice");
      assertEquals(first, onEdtCall(() -> fixture.input().getText()));
      assertFalse(onEdtCall(() -> fixture.support().getAutoCompletion().isPopupVisible()));

      onEdt(() -> triggerTab(fixture.input()));
      String second = text.equals("ali") ? "alina: " : text.replace("ali", "alina");
      assertEquals(second, onEdtCall(() -> fixture.input().getText()));
      assertFalse(onEdtCall(() -> fixture.support().getAutoCompletion().isPopupVisible()));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"helo", "says helo", "/me says helo"})
  @EnabledIfSystemProperty(named = "java.awt.headless", matches = "false")
  void singleSpellingSuggestionAlwaysOpensPopupInBothNickModes(String text) throws Exception {
    for (boolean automatic : List.of(false, true)) {
      try (CompletionFixture fixture = completionFixture(text, List.of("alice"), automatic, true)) {
        onEdt(() -> triggerTab(fixture.input()));
        assertTrue(onEdtCall(() -> fixture.support().getAutoCompletion().isPopupVisible()));
        assertEquals(text, onEdtCall(() -> fixture.input().getText()));

        onEdt(() -> triggerKey(fixture.input(), KeyEvent.VK_ENTER));
        assertEquals(text.replace("helo", "hello"), onEdtCall(() -> fixture.input().getText()));
      }
    }
  }

  private static CompletionFixture completionFixture(
      String text, List<String> nicks, boolean automatic, boolean spelling) throws Exception {
    CompletionFixture fixture =
        onEdtCall(
            () -> {
              JTextField input = new JTextField();
              input.setFocusTraversalKeysEnabled(false);
              JPanel owner = new JPanel(new java.awt.BorderLayout());
              owner.add(input);
              JFrame frame = new JFrame("Nick completion test");
              frame.setContentPane(owner);
              frame.setSize(500, 120);
              MessageInputNickCompletionSupport support =
                  new MessageInputNickCompletionSupport(
                      owner,
                      input,
                      new MessageInputUndoSupport(input, () -> false),
                      spelling ? (token, maxSuggestions) -> List.of("hello") : null);
              support.setNickCompletions(nicks);
              support.setCompletionPreferences(automatic, true);
              support.install();
              frame.setVisible(true);
              input.setText(text);
              input.setCaretPosition(text.length());
              input.requestFocusInWindow();
              return new CompletionFixture(frame, input, support);
            });
    waitFor(() -> onEdtBoolean(() -> fixture.input().hasFocus()), Duration.ofSeconds(2));
    return fixture;
  }

  private record CompletionFixture(
      JFrame frame, JTextField input, MessageInputNickCompletionSupport support)
      implements AutoCloseable {
    @Override
    public void close() throws Exception {
      onEdt(
          () -> {
            support.getAutoCompletion().uninstall();
            support.shutdown();
            frame.dispose();
          });
    }
  }

  @Test
  void popupStyleDeferredNickSelectionAppendsAddressSuffixForFirstWord() throws Exception {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(List.of("alice", "alina"));

    AtomicInteger tabInvocations = new AtomicInteger();
    onEdt(
        () -> {
          installFakeTabCompletionAction(input, tabInvocations);
          installNickAddressSuffixWrapping(support);
          input.setText("a");
          input.setCaretPosition(1);
          triggerTab(input);
        });

    waitFor(() -> onEdtBoolean(() -> "ali".equals(input.getText())), Duration.ofSeconds(2));
    assertEquals(1, tabInvocations.get(), "expected one TAB completion trigger");

    // Simulate selecting one nick from a completion popup after TAB expanded to a shared prefix.
    onEdt(
        () -> {
          input.setCaretPosition(input.getText().length());
          input.replaceSelection("ce");
        });

    waitFor(() -> onEdtBoolean(() -> "alice: ".equals(input.getText())), Duration.ofSeconds(2));
  }

  private static void installFakeTabCompletionAction(JTextField input, AtomicInteger invocations) {
    KeyStroke tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
    InputMap inputMap = input.getInputMap(JComponent.WHEN_FOCUSED);
    ActionMap actionMap = input.getActionMap();
    inputMap.put(tab, "functional.fakeTabCompletion");
    actionMap.put(
        "functional.fakeTabCompletion",
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            invocations.incrementAndGet();
            // Mimic the autocomplete behavior when multiple nicks match: expand only shared prefix.
            input.setText("ali");
            input.setCaretPosition(3);
          }
        });
  }

  private static void installNickAddressSuffixWrapping(MessageInputNickCompletionSupport support)
      throws Exception {
    Method m =
        MessageInputNickCompletionSupport.class.getDeclaredMethod(
            "installNickCompletionAddressingSuffix");
    m.setAccessible(true);
    m.invoke(support);
  }

  private static void triggerTab(JTextField input) {
    triggerKey(input, KeyEvent.VK_TAB);
  }

  private static void triggerKey(JTextField input, int keyCode) {
    KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, 0);
    InputMap inputMap = input.getInputMap(JComponent.WHEN_FOCUSED);
    Object actionKey = inputMap != null ? inputMap.get(keyStroke) : null;
    if (actionKey == null) {
      throw new AssertionError("Key binding should be installed: " + keyCode);
    }
    ActionMap actionMap = input.getActionMap();
    Action action = actionMap != null ? actionMap.get(actionKey) : null;
    if (action == null) {
      throw new AssertionError("Key action should be installed: " + keyCode);
    }
    action.actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "functionalTab"));
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static boolean onEdtBoolean(ThrowingSupplier<Boolean> supplier) {
    try {
      return onEdtCall(supplier);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static void onEdt(ThrowingRunnable r) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            r.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
