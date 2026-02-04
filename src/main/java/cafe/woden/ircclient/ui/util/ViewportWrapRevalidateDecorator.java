package cafe.woden.ircclient.ui.util;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/** Forces a revalidate/repaint of a wrapped component when its viewport resizes. */
public final class ViewportWrapRevalidateDecorator implements AutoCloseable {

  private final JViewport viewport;
  private final JComponent target;
  private final ComponentAdapter listener;

  // Coalesce resize events to avoid re-entrant resize/layout loops on some LAF/layout combinations.
  private final AtomicBoolean scheduled = new AtomicBoolean(false);

  private volatile boolean closed = false;

  private ViewportWrapRevalidateDecorator(JViewport viewport, JComponent target) {
    this.viewport = viewport;
    this.target = target;
    this.listener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (closed) return;

        // Calling revalidate() directly inside componentResized() can cause re-entrant layout
        // and an event storm (or even a hang) on startup or during docking transitions.
        // Coalesce to a single invokeLater() per resize burst.
        if (!scheduled.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
          scheduled.set(false);
          if (closed) return;
          target.revalidate();
          target.repaint();
        });
      }
    };

    this.viewport.addComponentListener(listener);
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
