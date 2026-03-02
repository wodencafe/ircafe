package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ChatSmoothWheelScrollDecoratorTest {

  @Test
  void wheelJumpMovesOnlySingleUnitStep() throws Exception {
    onEdt(
        () -> {
          JTextPane transcript = new JTextPane();
          StubScrollPane scroll = new StubScrollPane(transcript);
          JScrollBar bar = new JScrollBar(JScrollBar.VERTICAL);
          scroll.setStubVerticalBar(bar);

          ChatSmoothWheelScrollDecorator decorator =
              ChatSmoothWheelScrollDecorator.decorate(scroll, () -> true);
          try {
            bar.setModel(new DefaultBoundedRangeModel(100, 80, 0, 600));
            bar.setUnitIncrement(24);
            int initialValue = bar.getValue();
            int step = Math.max(1, bar.getUnitIncrement(1));
            invokeWheel(decorator, wheelEvent(scroll, 5, 1_000L));
            assertEquals(
                initialValue + step,
                bar.getValue(),
                "hardware jumps should still scroll by one unit");
          } finally {
            decorator.close();
          }
        });
  }

  @Test
  void microburstDebounceSuppressesImmediateSecondStep() throws Exception {
    onEdt(
        () -> {
          JTextPane transcript = new JTextPane();
          StubScrollPane scroll = new StubScrollPane(transcript);
          JScrollBar bar = new JScrollBar(JScrollBar.VERTICAL);
          scroll.setStubVerticalBar(bar);

          ChatSmoothWheelScrollDecorator decorator =
              ChatSmoothWheelScrollDecorator.decorate(scroll, () -> true);
          try {
            bar.setModel(new DefaultBoundedRangeModel(100, 80, 0, 600));
            bar.setUnitIncrement(20);
            int initialValue = bar.getValue();
            int step = Math.max(1, bar.getUnitIncrement(1));
            invokeWheel(decorator, wheelEvent(scroll, 1, 1_000L));
            invokeWheel(decorator, wheelEvent(scroll, 1, 1_005L));
            assertEquals(
                initialValue + step,
                bar.getValue(),
                "second microburst event should be dropped");
          } finally {
            decorator.close();
          }
        });
  }

  @Test
  void disabledSupplierLeavesScrollbarUnchanged() throws Exception {
    onEdt(
        () -> {
          JTextPane transcript = new JTextPane();
          StubScrollPane scroll = new StubScrollPane(transcript);
          JScrollBar bar = new JScrollBar(JScrollBar.VERTICAL);
          scroll.setStubVerticalBar(bar);
          bar.setModel(new DefaultBoundedRangeModel(100, 80, 0, 600));
          bar.setUnitIncrement(20);
          AtomicBoolean enabled = new AtomicBoolean(false);

          ChatSmoothWheelScrollDecorator decorator =
              ChatSmoothWheelScrollDecorator.decorate(scroll, enabled::get);
          try {
            int initialValue = bar.getValue();
            invokeWheel(decorator, wheelEvent(scroll, 1, 1_000L));
            assertEquals(initialValue, bar.getValue());
          } finally {
            decorator.close();
          }
        });
  }

  private static MouseWheelEvent wheelEvent(JScrollPane scroll, int rotation, long when) {
    return new MouseWheelEvent(
        scroll,
        MouseEvent.MOUSE_WHEEL,
        when,
        0,
        10,
        10,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        1,
        rotation);
  }

  private static void invokeWheel(ChatSmoothWheelScrollDecorator decorator, MouseWheelEvent event) {
    try {
      Method m =
          ChatSmoothWheelScrollDecorator.class.getDeclaredMethod("onWheel", MouseWheelEvent.class);
      m.setAccessible(true);
      m.invoke(decorator, event);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void onEdt(Runnable task) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      task.run();
      return;
    }
    SwingUtilities.invokeAndWait(task);
  }

  private static final class StubScrollPane extends JScrollPane {
    private JScrollBar stubVerticalBar;

    private StubScrollPane(JTextPane transcript) {
      super(transcript);
    }

    void setStubVerticalBar(JScrollBar bar) {
      this.stubVerticalBar = bar;
    }

    @Override
    public JScrollBar getVerticalScrollBar() {
      return stubVerticalBar != null ? stubVerticalBar : super.getVerticalScrollBar();
    }
  }
}
