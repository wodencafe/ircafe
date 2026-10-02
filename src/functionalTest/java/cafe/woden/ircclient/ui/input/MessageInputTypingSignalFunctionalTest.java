package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MessageInputTypingSignalFunctionalTest {

  @ParameterizedTest
  @CsvSource({
    "send, true", "pause, true", "clear, true", "switch, true",
    "send, false", "pause, false", "clear, false", "switch, false"
  })
  void arrowReturnsToGreenWhenTypingStops(String action, boolean acknowledgeSend) throws Exception {
    MessageInputPanel[] panels = new MessageInputPanel[1];
    List<String> states = new CopyOnWriteArrayList<>();
    onEdt(
        () -> {
          MessageInputPanel panel =
              new MessageInputPanel(mock(UiSettingsBus.class), mock(CommandHistoryStore.class));
          panels[0] = panel;
          panel.setTypingSignalAvailable(true);
          panel.setOnTypingStateChanged(
              state -> {
                states.add(state);
                // Only ACTIVE is acknowledged: local cleanup must also work without a reply.
                if (acknowledgeSend && "active".equals(state))
                  panel.onLocalTypingIndicatorSent(state);
              });
          panel.addNotify();
        });
    MessageInputPanel panel = panels[0];
    try {
      JTextComponent input = findFirst(panel, JTextComponent.class);
      TypingSignalIndicator signal = findFirst(panel, TypingSignalIndicator.class);
      JButton send = findSendButton(panel);
      assertNotNull(input);
      assertNotNull(signal);
      assertNotNull(send);
      onEdt(
          () -> {
            assertEquals(0x35C86E, signal.debugArrowColorForTest().getRGB() & 0xFFFFFF);
            input.setText("hello");
          });
      waitFor(
          () -> {
            var color = signal.debugArrowColorForTest();
            return (acknowledgeSend
                    ? color.getBlue() > color.getGreen() && color.getGreen() > color.getRed()
                    : (color.getBlue() > color.getRed() && color.getRed() > color.getGreen())
                        || (color.getRed() == color.getGreen()
                            && color.getGreen() == color.getBlue()))
                && signal.debugArrowGlowForTest() > 0.2f;
          },
          Duration.ofSeconds(1));
      onEdt(
          () -> {
            switch (action) {
              case "send" -> send.doClick();
              case "clear" -> input.setText("");
              case "switch" -> panel.flushTypingForBufferSwitch();
              case "pause" -> {
                /* Let the inactivity timer fire. */
              }
              default -> throw new AssertionError(action);
            }
          });
      waitFor(
          () -> (signal.debugArrowColorForTest().getRGB() & 0xFFFFFF) == 0x35C86E,
          Duration.ofSeconds(4));
      panel.onLocalTypingIndicatorSent("active");
      onEdt(() -> assertEquals(0x35C86E, signal.debugArrowColorForTest().getRGB() & 0xFFFFFF));
      if ("send".equals(action)) {
        assertEquals(List.of("active"), states);
      } else if ("clear".equals(action)) {
        assertEquals(List.of("active", "done"), states);
      } else {
        assertEquals(List.of("active", "paused"), states);
      }
      onEdt(
          () -> {
            panel.removeNotify();
            assertFalse(signal.isDisplayable());
          });
    } finally {
      onEdt(
          () -> {
            panel.shutdownResources();
            if (panel.isDisplayable()) panel.removeNotify();
          });
    }
  }

  @Test
  void pauseAcknowledgementProducesSingleGreenPulse() throws Exception {
    MessageInputPanel[] panels = new MessageInputPanel[1];
    List<String> states = new CopyOnWriteArrayList<>();
    onEdt(
        () -> {
          MessageInputPanel panel =
              new MessageInputPanel(mock(UiSettingsBus.class), mock(CommandHistoryStore.class));
          panels[0] = panel;
          panel.setTypingSignalAvailable(true);
          panel.setOnTypingStateChanged(
              state -> {
                states.add(state);
                if ("active".equals(state)) panel.onLocalTypingIndicatorSent(state);
              });
          panel.addNotify();
        });
    MessageInputPanel panel = panels[0];
    try {
      JTextComponent input = findFirst(panel, JTextComponent.class);
      TypingSignalIndicator signal = findFirst(panel, TypingSignalIndicator.class);
      assertNotNull(input);
      assertNotNull(signal);
      onEdt(() -> input.setText("hello"));
      waitFor(() -> signal.debugArrowColorForTest().getBlue() > 200, Duration.ofSeconds(1));
      // Let the real inactivity timer emit PAUSED, but defer its acknowledgement.
      waitFor(
          () -> states.contains("paused") && signal.debugArrowGlowForTest() == 0.12f,
          Duration.ofSeconds(4));
      panel.onLocalTypingIndicatorSent("paused");
      waitFor(
          () ->
              (signal.debugArrowColorForTest().getRGB() & 0xFFFFFF) == 0x35C86E
                  && signal.debugArrowGlowForTest() > 0.4f,
          Duration.ofSeconds(1));
      waitFor(() -> signal.debugArrowGlowForTest() == 0.12f, Duration.ofSeconds(1));
      Thread.sleep(600);
      onEdt(
          () -> {
            assertEquals(0x35C86E, signal.debugArrowColorForTest().getRGB() & 0xFFFFFF);
            assertEquals(0.12f, signal.debugArrowGlowForTest());
          });
      assertEquals(List.of("active", "paused"), states);
    } finally {
      onEdt(
          () -> {
            panel.shutdownResources();
            if (panel.isDisplayable()) panel.removeNotify();
          });
    }
  }

  @Test
  void delayedSendAlternatesVioletAndGrayUntilBackgroundCompletion() throws Exception {
    MessageInputPanel[] panels = new MessageInputPanel[1];
    onEdt(
        () -> {
          panels[0] =
              new MessageInputPanel(mock(UiSettingsBus.class), mock(CommandHistoryStore.class));
          panels[0].setTypingSignalAvailable(true);
          panels[0].addNotify();
        });
    MessageInputPanel panel = panels[0];
    try {
      JTextComponent input = findFirst(panel, JTextComponent.class);
      TypingSignalIndicator signal = findFirst(panel, TypingSignalIndicator.class);
      assertNotNull(input);
      assertNotNull(signal);
      onEdt(() -> input.setText("hello"));
      waitFor(
          () -> {
            var color = signal.debugArrowColorForTest();
            return color.getBlue() > color.getRed()
                && color.getRed() > color.getGreen()
                && signal.debugArrowGlowForTest() > 0.2f;
          },
          Duration.ofSeconds(1));
      waitFor(
          () -> {
            var color = signal.debugArrowColorForTest();
            return color.getRed() == color.getGreen()
                && color.getGreen() == color.getBlue()
                && signal.debugArrowGlowForTest() >= 0.2f;
          },
          Duration.ofSeconds(1));
      // The test thread simulates a network send completing off the EDT.
      panel.onLocalTypingIndicatorSent("active");
      waitFor(
          () -> {
            var color = signal.debugArrowColorForTest();
            return color.getBlue() > color.getGreen()
                && color.getGreen() > color.getRed()
                && signal.debugArrowGlowForTest() > 0.2f;
          },
          Duration.ofSeconds(1));
    } finally {
      onEdt(
          () -> {
            panel.shutdownResources();
            if (panel.isDisplayable()) panel.removeNotify();
          });
    }
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    AtomicBoolean satisfied = new AtomicBoolean();
    do {
      onEdt(() -> satisfied.set(condition.getAsBoolean()));
      if (satisfied.get()) return;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    assertTrue(satisfied.get(), "typing arrow did not reach the expected color");
  }

  private static <T> T findFirst(Container root, Class<T> type) {
    for (Component child : root.getComponents()) {
      if (type.isInstance(child)) return type.cast(child);
      if (child instanceof Container nested) {
        T match = findFirst(nested, type);
        if (match != null) return match;
      }
    }
    return null;
  }

  private static JButton findSendButton(Container root) {
    for (Component child : root.getComponents()) {
      if (child instanceof JButton button && "messageSendButton".equals(button.getName())) {
        return button;
      }
      if (child instanceof Container nested) {
        JButton match = findSendButton(nested);
        if (match != null) return match;
      }
    }
    return null;
  }

  private static void onEdt(Runnable action) throws Exception {
    SwingUtilities.invokeAndWait(action);
  }
}
