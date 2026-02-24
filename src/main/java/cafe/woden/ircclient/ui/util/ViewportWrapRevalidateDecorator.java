package cafe.woden.ircclient.ui.util;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/** Revalidates a wrapped component when viewport width changes (for line wrapping). */
public final class ViewportWrapRevalidateDecorator implements AutoCloseable {

  private final JViewport viewport;
  private final JComponent target;
  private final ComponentAdapter listener;

  // Coalesce resize events to avoid re-entrant resize/layout loops on some LAF/layout combinations.
  private final AtomicBoolean scheduled = new AtomicBoolean(false);
  private volatile int pendingViewportWidth = -1;
  private volatile int lastAppliedViewportWidth = -1;

  private volatile boolean closed = false;

  private ViewportWrapRevalidateDecorator(JViewport viewport, JComponent target) {
    this.viewport = viewport;
    this.target = target;
    this.listener =
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (closed) return;
            int width = viewport.getWidth();
            if (width <= 0) return;
            if (width == lastAppliedViewportWidth) return;
            pendingViewportWidth = width;

            // Calling revalidate() directly inside componentResized() can cause re-entrant layout
            // and an event storm (or even a hang) on startup or during docking transitions.
            // Coalesce to a single invokeLater() per resize burst.
            if (!scheduled.compareAndSet(false, true)) return;
            SwingUtilities.invokeLater(
                ViewportWrapRevalidateDecorator.this::applyPendingViewportWidth);
          }
        };

    this.viewport.addComponentListener(listener);
  }

  private void applyPendingViewportWidth() {
    scheduled.set(false);
    if (closed) return;
    int width = pendingViewportWidth;
    if (width <= 0) return;
    if (width == lastAppliedViewportWidth) return;
    if (!target.isDisplayable()) return;
    lastAppliedViewportWidth = width;
    target.revalidate();
  }

  public static ViewportWrapRevalidateDecorator decorate(JViewport viewport, JComponent target) {
    if (viewport == null) throw new IllegalArgumentException("viewport must not be null");
    if (target == null) throw new IllegalArgumentException("target must not be null");
    return new ViewportWrapRevalidateDecorator(viewport, target);
  }

  @Override
  public void close() {
    closed = true;
    viewport.removeComponentListener(listener);
  }
}
