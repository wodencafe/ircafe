package cafe.woden.ircclient.ui.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TerminalDockableFunctionalTest {

  @Test
  void outputHistoryFollowAndClearBehaviorWork() throws Exception {
    ConsoleTeeService console = mock(ConsoleTeeService.class);
    when(console.snapshot()).thenReturn("boot line\n");

    AtomicReference<Consumer<String>> listenerRef = new AtomicReference<>();
    AtomicBoolean closed = new AtomicBoolean(false);
    when(console.addListener(any()))
        .thenAnswer(
            inv -> {
              listenerRef.set(inv.getArgument(0));
              return (AutoCloseable) () -> closed.set(true);
            });

    TerminalDockable dockable = onEdtCall(() -> new TerminalDockable(console));
    JTextArea area = readField(dockable, "area", JTextArea.class);
    JCheckBox followTail = readField(dockable, "followTail", JCheckBox.class);
    JButton clearButton = findButton(dockable, "Clear");
    assertNotNull(clearButton, "clear button should be present");

    try {
      onEdt(() -> invokeNoArgs(dockable, "attach"));
      flushEdt();

      assertEquals("boot line\n", onEdtCall(area::getText));

      Consumer<String> listener = listenerRef.get();
      assertNotNull(listener, "console listener should be attached");
      listener.accept("next line\n");

      waitFor(
          () -> onEdtBoolean(() -> area.getText().contains("next line")), Duration.ofSeconds(2));
      assertTrue(onEdtCall(() -> area.getCaretPosition() == area.getDocument().getLength()));

      onEdt(
          () -> {
            followTail.setSelected(false);
            area.setCaretPosition(0);
          });
      listener.accept("third line\n");
      waitFor(
          () -> onEdtBoolean(() -> area.getText().contains("third line")), Duration.ofSeconds(2));
      onEdt(() -> assertEquals(0, area.getCaretPosition()));

      onEdt(clearButton::doClick);
      onEdt(() -> assertEquals("", area.getText()));
    } finally {
      onEdt(dockable::shutdown);
      flushEdt();
      assertTrue(closed.get(), "terminal subscription should be closed on shutdown");
    }
  }

  private static JButton findButton(java.awt.Component root, String text) {
    if (root == null || text == null) return null;
    if (root instanceof JButton button && text.equals(button.getText())) return button;
    if (!(root instanceof java.awt.Container container)) return null;
    for (java.awt.Component child : container.getComponents()) {
      JButton found = findButton(child, text);
      if (found != null) return found;
    }
    return null;
  }

  private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }

  private static void invokeNoArgs(Object target, String methodName) {
    try {
      Method method = target.getClass().getDeclaredMethod(methodName);
      method.setAccessible(true);
      method.invoke(target);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
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

  private static boolean onEdtBoolean(ThrowingBooleanSupplier supplier) {
    try {
      return onEdtCall(supplier::getAsBoolean);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }
}
