package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.SingleLineEmojiTextPane;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MessageInputDockEditingFunctionalTest {
  @ParameterizedTest
  @CsvSource({
    "nimbus-dark-orange, false",
    "nimbus-dark-orange, true",
    "darcula, false",
    "darcula, true",
    "darklaf, false",
    "darklaf, true"
  })
  void backspaceWorksAfterOpeningAndRefreshingChatDocks(String theme, boolean floating)
      throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
    assumeTrue(
        waylandDisplay == null || waylandDisplay.isBlank(),
        "Robot requires X11 with WAYLAND_DISPLAY unset");
    String originalLaf = UIManager.getLookAndFeel().getClass().getName();
    ThemeManager themes =
        new ThemeManager(mock(ChatStyles.class), mock(ChatTranscriptStore.class), null, null, null);
    JFrame[] frame = new JFrame[1];
    InputDock[] main = new InputDock[1];
    InputDock[] separate = new InputDock[1];
    Robot robot = new Robot();
    robot.setAutoDelay(50);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            themes.installLookAndFeel(theme);
            frame[0] = new JFrame("Input dock regression");
            Docking.initialize(frame[0]);
            frame[0].add(new RootDockingPanel(frame[0]));
            main[0] = new InputDock("main");
            Docking.registerDockable(main[0]);
            Docking.dock(main[0], frame[0]);
            frame[0].setSize(700, 400);
            frame[0].setVisible(true);
            main[0].input.focusInput();
          });
      focus(robot, main[0]);
      typeAndBackspace(robot, main[0]);
      SwingUtilities.invokeAndWait(
          () -> {
            separate[0] = new InputDock("separate");
            Docking.registerDockable(separate[0]);
            Docking.dock(separate[0], main[0], DockingRegion.CENTER);
            Docking.display(separate[0]);
            if (floating)
              Docking.newWindow(
                  separate[0], new java.awt.Point(730, 20), new java.awt.Dimension(500, 300));
            SwingUtilities.updateComponentTreeUI(frame[0]);
            for (var window : Docking.getRootPanels().keySet()) {
              if (window != frame[0]) SwingUtilities.updateComponentTreeUI(window);
            }
          });
      focus(robot, separate[0]);
      typeAndBackspace(robot, separate[0]);
      SwingUtilities.invokeAndWait(() -> Docking.display(main[0]));
      focus(robot, main[0]);
      typeAndBackspace(robot, main[0]);
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            if (main[0] != null) main[0].input.shutdownResources();
            if (separate[0] != null) separate[0].input.shutdownResources();
            var windows = java.util.List.copyOf(Docking.getRootPanels().keySet());
            Docking.uninitialize();
            windows.forEach(java.awt.Window::dispose);
            if (frame[0] != null) frame[0].dispose();
            themes.installLookAndFeel("nimbus");
            try {
              UIManager.setLookAndFeel(originalLaf);
            } catch (Exception failure) {
              throw new AssertionError(failure);
            }
          });
    }
  }

  private static void focus(Robot robot, InputDock dock) throws Exception {
    java.util.concurrent.atomic.AtomicReference<java.awt.Point> click =
        new java.util.concurrent.atomic.AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          SwingUtilities.getWindowAncestor(dock).toFront();
          dock.input.focusInput();
          var editor = findInput(dock);
          var point = editor.getLocationOnScreen();
          point.translate(10, Math.max(1, editor.getHeight() / 2));
          click.set(point);
        });
    robot.mouseMove(click.get().x, click.get().y);
    robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
    robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
    robot.waitForIdle();
    AtomicBoolean focused = new AtomicBoolean();
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    do {
      SwingUtilities.invokeAndWait(() -> focused.set(findInput(dock).hasFocus()));
      if (focused.get()) return;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    assertTrue(focused.get(), "input should gain focus");
  }

  private static void typeAndBackspace(Robot robot, InputDock dock) throws Exception {
    SwingUtilities.invokeAndWait(() -> dock.input.setDraftText("draft"));
    robot.keyPress(KeyEvent.VK_X);
    robot.keyRelease(KeyEvent.VK_X);
    robot.waitForIdle();
    SwingUtilities.invokeAndWait(() -> assertEquals("draftx", dock.input.getDraftText()));
    robot.keyPress(KeyEvent.VK_BACK_SPACE);
    robot.keyRelease(KeyEvent.VK_BACK_SPACE);
    robot.waitForIdle();
    SwingUtilities.invokeAndWait(() -> assertEquals("draft", dock.input.getDraftText()));
  }

  private static SingleLineEmojiTextPane findInput(Component component) {
    if (component instanceof SingleLineEmojiTextPane input) return input;
    if (component instanceof Container parent) {
      for (Component child : parent.getComponents()) {
        SingleLineEmojiTextPane found = findInput(child);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static final class InputDock extends JPanel implements Dockable {
    private final String id;
    private final MessageInputPanel input =
        new MessageInputPanel(mock(UiSettingsBus.class), mock(CommandHistoryStore.class));

    private InputDock(String id) {
      super(new BorderLayout());
      this.id = id;
      add(input, BorderLayout.SOUTH);
    }

    public String getPersistentID() {
      return id;
    }

    public String getTabText() {
      return id;
    }
  }
}
