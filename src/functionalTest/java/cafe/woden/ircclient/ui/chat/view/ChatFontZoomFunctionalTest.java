package cafe.woden.ircclient.ui.chat.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsTestFixtures;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChatFontZoomFunctionalTest {

  @ParameterizedTest
  @MethodSource("zoomKeys")
  void ctrlZoomKeysChangeFontAndSharedSettingsWithoutChangingText(KeyStroke key, int delta)
      throws Exception {
    onEdt(
        () -> {
          UiSettingsBus bus = liveSettingsBus(settings());
          TestChatView view = new TestChatView(bus);
          TestChatView detached = new TestChatView(bus);
          JTextField input = new JTextField("draft + text - stays");
          view.add(input, BorderLayout.SOUTH);
          withFocus(
              view,
              input,
              () -> {
                view.addNotify();
                detached.addNotify();
                try {
                  view.chat.setText("alice: hello world 😀\n".repeat(100));
                  String transcript = view.chat.getText();
                  view.setSize(480, 240);
                  layout(view);
                  view.scroll.getVerticalScrollBar().setValue(100);
                  int initialScroll = view.scroll.getVerticalScrollBar().getValue();
                  Font initialFont = view.chat.getFont();

                  assertTrue(view.pressKey(key));

                  assertEquals(12 + delta, view.chat.getFont().getSize());
                  assertEquals(initialFont.getFamily(), view.chat.getFont().getFamily());
                  assertEquals(12 + delta, bus.get().chatFontSize());
                  assertEquals(12 + delta, detached.chat.getFont().getSize());
                  assertEquals(initialScroll, view.scroll.getVerticalScrollBar().getValue());
                  assertEquals(transcript, view.chat.getText());
                  assertEquals("draft + text - stays", input.getText());
                } finally {
                  view.removeNotify();
                  detached.removeNotify();
                  detached.closeDecorators();
                }
              });
        });
  }

  private static Stream<Arguments> zoomKeys() {
    int ctrl = InputEvent.CTRL_DOWN_MASK;
    int ctrlShift = ctrl | InputEvent.SHIFT_DOWN_MASK;
    return Stream.of(
        Arguments.of(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl), 1),
        Arguments.of(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrlShift), 1),
        Arguments.of(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ctrl), 1),
        Arguments.of(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ctrlShift), 1),
        Arguments.of(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ctrl), 1),
        Arguments.of(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ctrl), -1),
        Arguments.of(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ctrl), -1));
  }

  @Test
  void keyboardZoomIsBoundedAndResetsFractionalWheelZoom() throws Exception {
    onEdt(
        () -> {
          TestChatView view = new TestChatView(liveSettingsBus(settings()));
          withFocus(
              view,
              view.chat,
              () -> {
                KeyStroke plus =
                    KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK);
                KeyStroke minus =
                    KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK);
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -0.75));
                assertTrue(view.pressKey(plus));
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -0.25));
                assertEquals(13, view.chat.getFont().getSize());
                for (int i = 0; i < 60; i++) assertTrue(view.pressKey(plus));
                assertEquals(48, view.chat.getFont().getSize());
                assertTrue(view.pressKey(minus));
                assertEquals(47, view.chat.getFont().getSize());
                for (int i = 0; i < 60; i++) assertTrue(view.pressKey(minus));
                assertEquals(8, view.chat.getFont().getSize());
                assertTrue(view.pressKey(plus));
                assertEquals(9, view.chat.getFont().getSize());
              });
        });
  }

  @Test
  void zoomKeysRequireControlAndDoNotInstallWindowWideBindings() throws Exception {
    onEdt(
        () -> {
          TestChatView view = new TestChatView(null);
          withFocus(
              view,
              view.chat,
              () -> {
                Font initialFont = view.chat.getFont();
                for (int key :
                    new int[] {KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_MINUS}) {
                  for (int modifiers :
                      new int[] {
                        0,
                        InputEvent.SHIFT_DOWN_MASK,
                        InputEvent.ALT_DOWN_MASK,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK
                      }) {
                    assertFalse(view.pressKey(KeyStroke.getKeyStroke(key, modifiers)));
                  }
                  assertEquals(
                      null,
                      view.getInputMap(TestChatView.WHEN_IN_FOCUSED_WINDOW)
                          .get(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK)));
                }
                assertEquals(initialFont, view.chat.getFont());
              });
        });
  }

  @Test
  void ctrlWheelChangesRenderedTextSizeWithoutScrollingOrChangingText() throws Exception {
    TestChatView[] views = new TestChatView[1];
    int[] initialHeights = new int[1];
    Font[] initialFonts = new Font[1];
    onEdt(
        () -> {
          TestChatView view = new TestChatView(null);
          views[0] = view;
          withFocus(
              view,
              view.chat,
              () -> {
                DefaultStyledDocument document = new DefaultStyledDocument();
                SimpleAttributeSet bold = new SimpleAttributeSet();
                StyleConstants.setBold(bold, true);
                String text = "alice: hello world 😀\n".repeat(100);
                try {
                  document.insertString(0, text, bold);
                } catch (Exception ex) {
                  throw new AssertionError(ex);
                }
                view.setDocument(document);
                view.setSize(480, 240);
                layout(view);
                initialHeights[0] = view.chat.getPreferredSize().height;
                Font initialFont = view.chat.getFont();
                initialFonts[0] = initialFont;
                view.scroll.getVerticalScrollBar().setValue(100);
                int initialScroll = view.scroll.getVerticalScrollBar().getValue();

                MouseWheelEvent up = wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -1);
                view.scroll.dispatchEvent(up);

                assertTrue(up.isConsumed());
                assertEquals(initialScroll, view.scroll.getVerticalScrollBar().getValue());
                assertEquals(initialFont.getSize() + 1, view.chat.getFont().getSize());
                assertEquals(initialFont.getFamily(), view.chat.getFont().getFamily());
                assertEquals(text, view.chat.getText());
                assertTrue(StyleConstants.isBold(document.getCharacterElement(0).getAttributes()));
              });
        });
    // Styled-document font changes notify text views on a later EDT turn.
    onEdt(
        () -> {
          TestChatView view = views[0];
          withFocus(
              view,
              view.chat,
              () -> {
                layout(view);
                assertTrue(view.chat.getPreferredSize().height > initialHeights[0]);
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, 1));
                assertEquals(initialFonts[0], view.chat.getFont());
              });
        });
  }

  @Test
  void wheelOverTranscriptAlsoZoomsWhenMessageInputHasFocus() throws Exception {
    onEdt(
        () -> {
          TestChatView view = new TestChatView(null);
          JTextField input = new JTextField();
          view.add(input, BorderLayout.SOUTH);
          withFocus(
              view,
              input,
              () -> {
                int initialSize = view.chat.getFont().getSize();
                view.addNotify();
                try {
                  view.setSize(480, 240);
                  layout(view);
                  view.chat.dispatchEvent(wheel(view.chat, InputEvent.CTRL_DOWN_MASK, -1));
                  assertEquals(initialSize + 1, view.chat.getFont().getSize());
                } finally {
                  view.removeNotify();
                }
              });
        });
  }

  @Test
  void normalWheelStillScrollsWithoutChangingFont() throws Exception {
    onEdt(
        () -> {
          TestChatView view = new TestChatView(null);
          withFocus(
              view,
              view.chat,
              () -> {
                view.chat.setText("alice: hello world\n".repeat(100));
                view.setSize(480, 240);
                layout(view);
                view.scroll.getVerticalScrollBar().setUnitIncrement(16);
                view.scroll.getVerticalScrollBar().setValue(100);
                int initialScroll = view.scroll.getVerticalScrollBar().getValue();
                Font initialFont = view.chat.getFont();
                view.scroll.dispatchEvent(wheel(view.scroll, 0, 1));
                assertTrue(
                    view.scroll.getVerticalScrollBar().getValue() > initialScroll,
                    "initial scroll="
                        + initialScroll
                        + ", after="
                        + view.scroll.getVerticalScrollBar().getValue());
                assertEquals(initialFont, view.chat.getFont());
              });
        });
  }

  @Test
  void ctrlWheelDoesNotZoomWhenFocusIsOutsideChat() throws Exception {
    onEdt(
        () -> {
          TestChatView view = new TestChatView(null);
          withFocus(
              view,
              new JTextField(),
              () -> {
                Font initialFont = view.chat.getFont();
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -1));
                assertEquals(initialFont, view.chat.getFont());
              });
        });
  }

  @Test
  void zoomIsBoundedAndReversesImmediatelyAtLimits() throws Exception {
    onEdt(
        () -> {
          TestChatView view = new TestChatView(null);
          withFocus(
              view,
              view.chat,
              () -> {
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -100));
                assertEquals(48, view.chat.getFont().getSize());
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -1));
                assertEquals(48, view.chat.getFont().getSize());
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, 1));
                assertEquals(47, view.chat.getFont().getSize());
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, 100));
                assertEquals(8, view.chat.getFont().getSize());
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, 1));
                assertEquals(8, view.chat.getFont().getSize());
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -1));
                assertEquals(9, view.chat.getFont().getSize());
              });
        });
  }

  @Test
  void fractionalWheelEventsAccumulateAndResetOnDirectionChangeOrNormalScrolling()
      throws Exception {
    onEdt(
        () -> {
          TestChatView view = new TestChatView(null);
          withFocus(
              view,
              view.chat,
              () -> {
                int initialSize = view.chat.getFont().getSize();
                for (int i = 0; i < 4; i++) {
                  MouseWheelEvent event = wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -0.25);
                  view.scroll.dispatchEvent(event);
                  assertTrue(event.isConsumed());
                }
                assertEquals(initialSize + 1, view.chat.getFont().getSize());
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -0.75));
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, 0.5));
                assertEquals(initialSize + 1, view.chat.getFont().getSize());
                view.scroll.dispatchEvent(wheel(view.scroll, 0, 0));
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, 0.5));
                assertEquals(initialSize + 1, view.chat.getFont().getSize());
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, 0.5));
                assertEquals(initialSize, view.chat.getFont().getSize());
              });
        });
  }

  @Test
  void ctrlWheelBypassesHistoryAndScrollingEvenWhenSmoothScrollingIsDisabled() throws Exception {
    onEdt(
        () -> {
          UiSettingsBus bus = mock(UiSettingsBus.class);
          when(bus.get()).thenReturn(settings());
          when(bus.chatSmoothWheelScrollingEnabled()).thenReturn(false);
          TestChatView view = new TestChatView(bus);
          withFocus(
              view,
              view.chat,
              () -> {
                int initialSize = view.chat.getFont().getSize();
                int initialScroll = view.scroll.getVerticalScrollBar().getValue();
                view.scroll.addMouseWheelListener(
                    event -> {
                      throw new AssertionError("zoom must bypass scroll and history listeners");
                    });
                view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -1));
                assertEquals(initialSize + 1, view.chat.getFont().getSize());
                assertEquals(initialScroll, view.scroll.getVerticalScrollBar().getValue());
              });
        });
  }

  @Test
  void zoomUpdatesSharedViewsAndSurvivesUnrelatedSettingsAndChannelSwitches() throws Exception {
    onEdt(
        () -> {
          UiSettings initial = settings();
          UiSettingsBus bus = liveSettingsBus(initial);
          TestChatView view = new TestChatView(bus);
          TestChatView detached = new TestChatView(bus);
          withFocus(
              view,
              view.chat,
              () -> {
                view.addNotify();
                detached.addNotify();
                try {
                  DefaultStyledDocument shared = new DefaultStyledDocument();
                  view.setDocument(shared);
                  detached.setDocument(shared);
                  view.scroll.dispatchEvent(wheel(view.scroll, InputEvent.CTRL_DOWN_MASK, -1));
                  assertEquals(13, bus.get().chatFontSize());
                  assertEquals(13, detached.chat.getFont().getSize());
                  view.setDocument(new DefaultStyledDocument());
                  bus.set(bus.get().withTimestampsEnabled(!initial.timestampsEnabled()));
                  assertEquals(13, view.chat.getFont().getSize());
                  bus.set(bus.get().withChatFontSize(18));
                  assertEquals(18, view.chat.getFont().getSize());
                  assertEquals(18, detached.chat.getFont().getSize());
                  assertFalse(view.chat.isEditable());
                } finally {
                  view.removeNotify();
                  detached.removeNotify();
                  detached.closeDecorators();
                }
              });
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nativeZoomKeepsInputTextAndKeyboardFocus(boolean keyboard) throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a desktop or xvfb-run");
    String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
    assumeTrue(
        waylandDisplay == null || waylandDisplay.isBlank(),
        "Java Robot requires X11 with WAYLAND_DISPLAY unset for this test");
    AtomicReference<JFrame> frame = new AtomicReference<>();
    AtomicReference<TestChatView> view = new AtomicReference<>();
    AtomicReference<JTextField> input = new AtomicReference<>();
    AtomicReference<Point> wheelPosition = new AtomicReference<>();
    Robot robot = new Robot();
    robot.setAutoDelay(50);
    try {
      onEdt(
          () -> {
            TestChatView chatView = new TestChatView(liveSettingsBus(settings()));
            JTextField field = new JTextField("draft + text - stays");
            chatView.chat.setText("alice: hello world\n".repeat(100));
            chatView.add(field, BorderLayout.SOUTH);
            JFrame window = new JFrame("Chat font zoom test");
            window.setContentPane(chatView);
            window.setSize(480, 320);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
            frame.set(window);
            view.set(chatView);
            input.set(field);
          });
      robot.waitForIdle();
      onEdt(
          () -> {
            input.get().requestFocusInWindow();
            Point point = view.get().chat.getLocationOnScreen();
            point.translate(30, 30);
            wheelPosition.set(point);
          });
      robot.waitForIdle();
      onEdt(() -> assertTrue(input.get().isFocusOwner()));
      robot.mouseMove(wheelPosition.get().x, wheelPosition.get().y);
      robot.keyPress(KeyEvent.VK_CONTROL);
      if (keyboard) {
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_EQUALS);
        robot.keyRelease(KeyEvent.VK_EQUALS);
        robot.keyRelease(KeyEvent.VK_SHIFT);
      } else {
        robot.mouseWheel(-1);
      }
      robot.keyRelease(KeyEvent.VK_CONTROL);
      robot.waitForIdle();
      onEdt(
          () -> {
            assertEquals(13, view.get().chat.getFont().getSize());
            assertTrue(input.get().isFocusOwner());
            assertEquals("draft + text - stays", input.get().getText());
          });
      robot.keyPress(KeyEvent.VK_CONTROL);
      if (keyboard) {
        robot.keyPress(KeyEvent.VK_MINUS);
        robot.keyRelease(KeyEvent.VK_MINUS);
      } else {
        robot.mouseWheel(1);
      }
      robot.keyRelease(KeyEvent.VK_CONTROL);
      robot.waitForIdle();
      onEdt(
          () -> {
            assertEquals(12, view.get().chat.getFont().getSize());
            assertTrue(input.get().isFocusOwner());
            assertEquals("draft + text - stays", input.get().getText());
          });
    } finally {
      robot.keyRelease(KeyEvent.VK_SHIFT);
      robot.keyRelease(KeyEvent.VK_CONTROL);
      onEdt(
          () -> {
            if (frame.get() != null) frame.get().dispose();
            if (view.get() != null) view.get().closeDecorators();
          });
    }
  }

  private static UiSettingsBus liveSettingsBus(UiSettings initial) {
    UiSettingsBus bus = mock(UiSettingsBus.class);
    AtomicReference<UiSettings> current = new AtomicReference<>(initial);
    PropertyChangeSupport changes = new PropertyChangeSupport(bus);
    when(bus.get()).thenAnswer(invocation -> current.get());
    doAnswer(
            invocation -> {
              changes.addPropertyChangeListener(
                  invocation.getArgument(0, PropertyChangeListener.class));
              return null;
            })
        .when(bus)
        .addListener(any(PropertyChangeListener.class));
    doAnswer(
            invocation -> {
              changes.removePropertyChangeListener(
                  invocation.getArgument(0, PropertyChangeListener.class));
              return null;
            })
        .when(bus)
        .removeListener(any(PropertyChangeListener.class));
    doAnswer(
            invocation -> {
              UiSettings next = invocation.getArgument(0);
              changes.firePropertyChange(
                  UiSettingsBus.PROP_UI_SETTINGS, current.getAndSet(next), next);
              return null;
            })
        .when(bus)
        .set(any(UiSettings.class));
    return bus;
  }

  private static UiSettings settings() {
    return UiSettingsTestFixtures.builder().chatFontSize(12).build();
  }

  private static MouseWheelEvent wheel(Component source, int modifiers, double rotation) {
    return new MouseWheelEvent(
        source,
        MouseEvent.MOUSE_WHEEL,
        System.currentTimeMillis(),
        modifiers,
        10,
        10,
        10,
        10,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        3,
        (int) rotation,
        rotation);
  }

  private static void layout(Container container) {
    container.doLayout();
    for (Component component : container.getComponents()) {
      if (component instanceof Container child) layout(child);
    }
  }

  private static void withFocus(TestChatView view, Component focusOwner, Runnable task) {
    KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    KeyboardFocusManager.setCurrentKeyboardFocusManager(new TestFocusManager(focusOwner));
    try {
      task.run();
    } finally {
      view.closeDecorators();
      KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
    }
  }

  private static void onEdt(Runnable task) throws Exception {
    SwingUtilities.invokeAndWait(task);
  }

  private static final class TestFocusManager extends DefaultKeyboardFocusManager {
    private final Component focusOwner;

    private TestFocusManager(Component focusOwner) {
      this.focusOwner = focusOwner;
    }

    @Override
    public Component getFocusOwner() {
      return focusOwner;
    }
  }

  private static final class TestChatView extends ChatViewPanel {
    private TestChatView(UiSettingsBus bus) {
      super(bus);
    }

    private boolean pressKey(KeyStroke key) {
      KeyEvent event =
          new KeyEvent(
              chat,
              KeyEvent.KEY_PRESSED,
              System.currentTimeMillis(),
              key.getModifiers(),
              key.getKeyCode(),
              KeyEvent.CHAR_UNDEFINED);
      return processKeyBinding(key, event, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, true);
    }

    @Override
    protected boolean isFollowTail() {
      return false;
    }

    @Override
    protected void setFollowTail(boolean followTail) {}

    @Override
    protected int getSavedScrollValue() {
      return 0;
    }

    @Override
    protected void setSavedScrollValue(int value) {}
  }
}
