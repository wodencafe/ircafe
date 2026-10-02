package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.SingleLineEmojiTextPane;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageInputThemeFunctionalTest {
  private final ThemeManager themes =
      new ThemeManager(mock(ChatStyles.class), mock(ChatTranscriptStore.class), null, null, null);

  @ParameterizedTest
  @ValueSource(strings = {"nimbus-dark-orange", "nimbus-dark-blue", "nimbus-orange", "darcula"})
  void typingKeepsInputShellVisibleAcrossThemeChanges(String theme) throws Exception {
    String originalLaf = UIManager.getLookAndFeel().getClass().getName();
    AtomicReference<MessageInputPanel> panelRef = new AtomicReference<>();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            themes.installLookAndFeel(theme);
            MessageInputPanel panel =
                new MessageInputPanel(mock(UiSettingsBus.class), mock(CommandHistoryStore.class));
            panelRef.set(panel);
            SingleLineEmojiTextPane input = findInput(panel);
            assertNotNull(input);
            input.setSize(240, 36);
            input.replaceSelection("            ");
            assertTransparentPaint(input, theme);
          });
      // Let the queued draft restyle run before checking the painted result again.
      SwingUtilities.invokeAndWait(
          () -> {
            SingleLineEmojiTextPane input = findInput(panelRef.get());
            assertTransparentPaint(input, theme + " after restyle");
            themes.installLookAndFeel("nimbus-dark-orange");
            SwingUtilities.updateComponentTreeUI(panelRef.get());
            input.replaceSelection("  ");
            assertTransparentPaint(input, theme + " after theme change");
            input.selectAll();
            input.getCaret().setSelectionVisible(true);
            assertTrue(changedPixels(input) > 0, "selection highlight must remain visible");
            input.setText("hello 😀");
            assertTrue(changedPixels(input) > 0, "typed text must remain visible");
          });
      SwingUtilities.invokeAndWait(
          () -> {
            SingleLineEmojiTextPane input = findInput(panelRef.get());
            assertTrue(
                cafe.woden.ircclient.ui.util.EmojiFontSupport.isEmojiRun(
                    input.getStyledDocument().getCharacterElement(6).getAttributes()));
            assertTrue(
                changedPixels(input) > 0, "text and emoji must remain visible after restyle");
          });
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            if (panelRef.get() != null) panelRef.get().shutdownResources();
            themes.installLookAndFeel("nimbus");
            try {
              UIManager.setLookAndFeel(originalLaf);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          });
    }
  }

  private static void assertTransparentPaint(SingleLineEmojiTextPane input, String context) {
    assertFalse(input.isOpaque(), context);
    Color shell = UIManager.getColor("TextField.background");
    BufferedImage image = paint(input, shell);
    for (int y = 6; y < 30; y++) {
      for (int x = 8; x < 232; x++) {
        assertEquals(shell.getRGB(), image.getRGB(x, y), context + " at " + x + "," + y);
      }
    }
  }

  private static int changedPixels(SingleLineEmojiTextPane input) {
    Color shell = UIManager.getColor("TextField.background");
    BufferedImage image = paint(input, shell);
    int changed = 0;
    for (int y = 6; y < 30; y++) {
      for (int x = 8; x < 232; x++) {
        if (image.getRGB(x, y) != shell.getRGB()) changed++;
      }
    }
    return changed;
  }

  private static BufferedImage paint(SingleLineEmojiTextPane input, Color shell) {
    BufferedImage image = new BufferedImage(240, 36, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    try {
      graphics.setColor(shell);
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      input.paint(graphics);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static SingleLineEmojiTextPane findInput(Component component) {
    if (component instanceof SingleLineEmojiTextPane input) return input;
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        SingleLineEmojiTextPane found = findInput(child);
        if (found != null) return found;
      }
    }
    return null;
  }
}
