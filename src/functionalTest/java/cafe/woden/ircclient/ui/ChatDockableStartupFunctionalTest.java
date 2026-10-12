package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class ChatDockableStartupFunctionalTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void darklafStartupKeepsComponentSizesValid(boolean installThemeBeforeConstruction)
      throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a desktop or xvfb-run");
    String originalLaf = UIManager.getLookAndFeel().getClass().getName();
    ThemeManager themes =
        new ThemeManager(mock(ChatStyles.class), mock(ChatTranscriptStore.class), null, null, null);
    AtomicReference<ChatDockableWiringSmokeTest.Fixture> fixture = new AtomicReference<>();
    AtomicReference<JFrame> window = new AtomicReference<>();
    try {
      SwingUtilities.invokeAndWait(
          () -> themes.installLookAndFeel(installThemeBeforeConstruction ? "darklaf" : "nimbus"));
      fixture.set(ChatDockableWiringSmokeTest.createFixture());
      SwingUtilities.invokeAndWait(
          () -> {
            themes.installLookAndFeel("darklaf");
            ChatDockable chat = fixture.get().chat();
            JFrame frame = new JFrame("Chat startup test");
            window.set(frame);
            frame.setContentPane(chat);
            frame.setSize(1100, 700);
            SwingUtilities.updateComponentTreeUI(frame);
            assertMigComponentSizes(chat);
            frame.setVisible(true);
            frame.validate();
          });
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            if (window.get() != null) window.get().dispose();
          });
      if (fixture.get() != null) fixture.get().shutdown();
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              UIManager.setLookAndFeel(originalLaf);
            } catch (Exception ex) {
              throw new IllegalStateException(ex);
            }
          });
    }
  }

  private static void assertMigComponentSizes(Container parent) {
    for (Component child : parent.getComponents()) {
      if (parent.getLayout() instanceof MigLayout) {
        String context = child.getClass().getName() + " in " + parent.getClass().getName();
        assertNotNull(child.getMaximumSize(), "maximum size: " + context);
        assertNotNull(child.getPreferredSize(), "preferred size: " + context);
        assertNotNull(child.getMinimumSize(), "minimum size: " + context);
      }
      if (child instanceof Container container) assertMigComponentSizes(container);
    }
  }
}
