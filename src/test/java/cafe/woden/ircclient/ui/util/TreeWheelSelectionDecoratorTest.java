package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TreeWheelSelectionDecoratorTest {

  @Test
  void nonAltWheelIsForwardedToScrollPane() throws Exception {
    Fixture fixture = createFixture();
    try {
      MouseWheelEvent event = wheelEvent(fixture.tree, 0, 1);
      onEdt(() -> fireWheelOnTree(fixture.tree, event));
      flushEdt();
      assertEquals(1, fixture.scrollWheelEvents.get());
      assertTrue(event.isConsumed(), "original tree wheel event should be consumed");
    } finally {
      fixture.close();
    }
  }

  @Test
  void altWheelMovesSelectionWithoutForwardingToScrollPane() throws Exception {
    Fixture fixture = createFixture();
    try {
      MouseWheelEvent event = wheelEvent(fixture.tree, InputEvent.ALT_DOWN_MASK, 1);
      onEdt(() -> fireWheelOnTree(fixture.tree, event));
      waitFor(() -> leadSelectionRow(fixture.tree) == 1, Duration.ofSeconds(1));

      assertEquals(0, fixture.scrollWheelEvents.get(), "Alt+wheel should not scroll viewport");
      assertTrue(event.isConsumed(), "Alt+wheel should be consumed by decorator");
      assertEquals(1, leadSelectionRow(fixture.tree));
    } finally {
      fixture.close();
    }
  }

  private static Fixture createFixture() throws InvocationTargetException, InterruptedException {
    AtomicReference<Fixture> ref = new AtomicReference<>();
    onEdt(
        () -> {
          JTree tree = alwaysShowingTree();
          tree.expandRow(0);
          tree.setSelectionRow(0);
          JScrollPane scroll = new JScrollPane(tree);
          AtomicInteger scrollEvents = new AtomicInteger();
          scroll.addMouseWheelListener(e -> scrollEvents.incrementAndGet());
          TreeWheelSelectionDecorator decorator =
              TreeWheelSelectionDecorator.decorate(tree, scroll);
          ref.set(new Fixture(tree, decorator, scrollEvents));
        });
    return ref.get();
  }

  private record Fixture(
      JTree tree, TreeWheelSelectionDecorator decorator, AtomicInteger scrollWheelEvents) {
    void close() throws InvocationTargetException, InterruptedException {
      onEdt(decorator::close);
    }
  }

  private static int leadSelectionRow(JTree tree) {
    AtomicReference<Integer> out = new AtomicReference<>(-1);
    try {
      onEdt(() -> out.set(tree.getLeadSelectionRow()));
    } catch (InvocationTargetException e) {
      throw new AssertionError(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    return out.get();
  }

  private static JTree alwaysShowingTree() {
    return new JTree() {
      @Override
      public boolean isShowing() {
        return true;
      }
    };
  }

  private static MouseWheelEvent wheelEvent(JTree tree, int modifiersEx, int wheelRotation) {
    return new MouseWheelEvent(
        tree,
        MouseEvent.MOUSE_WHEEL,
        System.currentTimeMillis(),
        modifiersEx,
        10,
        10,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        1,
        wheelRotation);
  }

  private static void fireWheelOnTree(JTree tree, MouseWheelEvent event) {
    for (MouseWheelListener listener : tree.getMouseWheelListeners()) {
      listener.mouseWheelMoved(event);
    }
  }

  private static void onEdt(Runnable task) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      task.run();
      return;
    }
    SwingUtilities.invokeAndWait(task);
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    onEdt(() -> {});
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout)
      throws InvocationTargetException, InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      }
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }
}
