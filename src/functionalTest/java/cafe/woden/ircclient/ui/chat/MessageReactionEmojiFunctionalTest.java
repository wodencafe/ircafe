package cafe.woden.ircclient.ui.chat;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.chat.fold.MessageReactionsComponent;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageReactionEmojiFunctionalTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rendersIconsCountsAndFallbackWhilePreservingWireTokens(boolean dark) throws Exception {
    AtomicReference<MessageReactionsComponent> component = new AtomicReference<>();
    AtomicReference<String> react = new AtomicReference<>();
    AtomicReference<String> unreact = new AtomicReference<>();
    Object oldBackground = UIManager.get("TextPane.background");
    try {
      onEdt(
          () -> {
            UIManager.put("TextPane.background", dark ? new Color(0x243244) : Color.WHITE);
            MessageReactionsComponent chips = new MessageReactionsComponent();
            chips.setTranscriptFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
            chips.setOnReactRequested(react::set);
            chips.setOnUnreactRequested(unreact::set);
            Map<String, List<String>> reactions = new LinkedHashMap<>();
            reactions.put(":+1:", List.of("alice", "bob"));
            reactions.put("👩‍💻", List.of("alice"));
            reactions.put(":custom_reaction:", List.of("carol"));
            chips.setReactions(reactions);
            component.set(chips);
            chips.setSize(600, 120);
            chips.addNotify();
            chips.doLayout();
          });
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  onEdt(
                      () -> {
                        JLabel first = (JLabel) component.get().getComponent(0);
                        JLabel unicode = (JLabel) component.get().getComponent(1);
                        JLabel unknown = (JLabel) component.get().getComponent(2);
                        assertNotNull(first.getIcon());
                        assertEquals("2", first.getText());
                        assertNotNull(unicode.getIcon());
                        assertEquals("", unicode.getText());
                        assertNull(unknown.getIcon());
                        assertEquals(":custom_reaction:", unknown.getText());
                        assertTrue(first.getToolTipText().contains("alice, bob"));
                        assertTrue(
                            first.getAccessibleContext().getAccessibleName().contains(":+1:"));
                      }));
      onEdt(
          () -> {
            JLabel first = (JLabel) component.get().getComponent(0);
            for (int button : new int[] {MouseEvent.BUTTON1, MouseEvent.BUTTON3}) {
              first.dispatchEvent(
                  new MouseEvent(
                      first,
                      MouseEvent.MOUSE_RELEASED,
                      System.currentTimeMillis(),
                      0,
                      4,
                      4,
                      4,
                      4,
                      1,
                      false,
                      button));
            }
            assertEquals(":+1:", react.get());
            assertEquals(":+1:", unreact.get());
            component.get().setTranscriptFont(new Font(Font.MONOSPACED, Font.PLAIN, 24));
          });
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  onEdt(
                      () -> {
                        JLabel first = (JLabel) component.get().getComponent(0);
                        assertTrue(
                            first.getIcon().getIconHeight() >= 24,
                            "icons follow transcript font changes");
                      }));
      onEdt(
          () -> {
            component.get().setReactions(Map.of(":eyes:", List.of("dave")));
            component.get().setReactions(Map.of(":heart:", List.of("erin")));
          });
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  onEdt(
                      () -> {
                        assertEquals(1, component.get().getComponentCount());
                        JLabel first = (JLabel) component.get().getComponent(0);
                        assertNotNull(first.getIcon());
                        assertEquals(
                            ":heart:",
                            first.getClientProperty(
                                MessageReactionsComponent.REACTION_TOKEN_PROPERTY));
                      }));
    } finally {
      onEdt(
          () -> {
            if (component.get() != null) component.get().removeNotify();
            UIManager.put("TextPane.background", oldBackground);
          });
    }
  }

  private static void onEdt(Runnable action) throws Exception {
    try {
      SwingUtilities.invokeAndWait(action);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      if (failure.getCause() instanceof AssertionError assertion) throw assertion;
      throw failure;
    }
  }
}
