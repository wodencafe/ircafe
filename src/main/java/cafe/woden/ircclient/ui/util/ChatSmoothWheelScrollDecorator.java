package cafe.woden.ircclient.ui.util;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

/**
 * Smoothes transcript wheel scrolling by collapsing noisy wheel bursts into single unit steps.
 *
 * <p>This is intentionally conservative: at most one unit step is applied per wheel event to avoid
 * jumpy hardware reporting from some mice/touchpads.
 */
public final class ChatSmoothWheelScrollDecorator implements AutoCloseable {

  private static final long MICROBURST_WINDOW_MS = 18L;

  private final JScrollPane scroll;
  private final BooleanSupplier enabledSupplier;
  private final MouseWheelListener wheelListener;

  private double preciseAccumulator = 0.0d;
  private long lastAppliedAtMs = -1L;
  private int lastAppliedDirection = 0;

  private ChatSmoothWheelScrollDecorator(JScrollPane scroll, BooleanSupplier enabledSupplier) {
    this.scroll = scroll;
    this.enabledSupplier = Objects.requireNonNull(enabledSupplier, "enabledSupplier");
    this.wheelListener = this::onWheel;
    this.scroll.addMouseWheelListener(wheelListener);
  }

  public static ChatSmoothWheelScrollDecorator decorate(JScrollPane scroll) {
    return decorate(scroll, () -> true);
  }

  public static ChatSmoothWheelScrollDecorator decorate(
      JScrollPane scroll, BooleanSupplier enabledSupplier) {
    if (scroll == null) return null;
    BooleanSupplier supplier = enabledSupplier != null ? enabledSupplier : () -> true;
    return new ChatSmoothWheelScrollDecorator(scroll, supplier);
  }

  private void onWheel(MouseWheelEvent e) {
    if (e == null) return;
    if (!enabledSupplier.getAsBoolean()) {
      resetAccumulation();
      return;
    }
    if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;

    JScrollBar bar = scroll.getVerticalScrollBar();
    if (bar == null) return;

    int dir = directionFor(e);
    if (dir == 0) {
      e.consume();
      return;
    }

    long when = e.getWhen();
    if (lastAppliedAtMs > 0L
        && dir == lastAppliedDirection
        && (when - lastAppliedAtMs) <= MICROBURST_WINDOW_MS) {
      e.consume();
      return;
    }

    int before = bar.getValue();
    int delta = Math.max(1, bar.getUnitIncrement(dir));
    int min = bar.getMinimum();
    int max = Math.max(min, bar.getMaximum() - bar.getVisibleAmount());
    int target = clamp(before + (delta * dir), min, max);
    if (target != before) {
      bar.setValue(target);
      lastAppliedAtMs = when;
      lastAppliedDirection = dir;
    }

    e.consume();
  }

  private int directionFor(MouseWheelEvent e) {
    int raw = e.getWheelRotation();
    if (raw != 0) {
      preciseAccumulator = 0.0d;
      return Integer.signum(raw);
    }

    preciseAccumulator += e.getPreciseWheelRotation();
    if (Math.abs(preciseAccumulator) < 1.0d) {
      return 0;
    }

    int dir = (int) Math.signum(preciseAccumulator);
    preciseAccumulator -= dir;
    return dir;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private void resetAccumulation() {
    preciseAccumulator = 0.0d;
    lastAppliedAtMs = -1L;
    lastAppliedDirection = 0;
  }

  @Override
  public void close() {
    try {
      scroll.removeMouseWheelListener(wheelListener);
    } catch (Exception ignored) {
    }
  }
}
