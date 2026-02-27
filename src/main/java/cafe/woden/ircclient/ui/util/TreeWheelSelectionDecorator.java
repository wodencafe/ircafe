package cafe.woden.ircclient.ui.util;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.awt.Point;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.concurrent.TimeUnit;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;

/**
 * Decorates a {@link JTree} with Alt+wheel row navigation.
 *
 * <p>Default wheel behavior remains normal viewport scrolling. Holding Alt while wheeling moves the
 * tree selection up/down by one row at a time.
 */
public final class TreeWheelSelectionDecorator implements AutoCloseable {

  private static final long DEFAULT_MICROBURST_WINDOW_MS = 12L;

  private final JTree tree;
  private final JScrollPane scroll;

  private final boolean previousWheelScrollingEnabled;

  private final Subject<Integer> stepRequests = PublishSubject.<Integer>create().toSerialized();
  private final Disposable subscription;

  private final MouseWheelListener wheelListener;
  private final HierarchyListener hierarchyListener;

  private double wheelDeltaAccumulator = 0.0d;
  private long lastWheelEventNanos = 0L;

  private boolean closed = false;

  private TreeWheelSelectionDecorator(JTree tree, JScrollPane scroll, long microburstWindowMs) {
    this.tree = tree;
    this.scroll = scroll;

    this.previousWheelScrollingEnabled = scroll.isWheelScrollingEnabled();

    // Debounce micro-bursts so some touchpads don't skip nodes.
    this.subscription =
        stepRequests
            .throttleFirst(microburstWindowMs, TimeUnit.MILLISECONDS)
            .subscribe(dir -> SwingUtilities.invokeLater(() -> moveSelectionBy(dir)), err -> {});

    this.wheelListener = this::onMouseWheel;
    tree.addMouseWheelListener(wheelListener);

    // If the tree becomes undisplayable, dispose the Rx subscription to avoid leaks.
    this.hierarchyListener =
        e -> {
          if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
              && !tree.isDisplayable()) {
            if (!subscription.isDisposed()) subscription.dispose();
          }
        };
    tree.addHierarchyListener(hierarchyListener);
  }

  public static TreeWheelSelectionDecorator decorate(JTree tree, JScrollPane scroll) {
    if (tree == null) throw new IllegalArgumentException("tree is null");
    if (scroll == null) throw new IllegalArgumentException("scroll is null");
    return new TreeWheelSelectionDecorator(tree, scroll, DEFAULT_MICROBURST_WINDOW_MS);
  }

  private void onMouseWheel(MouseWheelEvent e) {
    if (closed) return;
    if (!tree.isShowing() || !tree.isEnabled()) return;
    if (!e.isAltDown()) {
      // Tree wheel listeners prevent Swing from bubbling to parent scroll panes; forward manually.
      dispatchToScrollPane(e);
      wheelDeltaAccumulator = 0.0d;
      lastWheelEventNanos = 0L;
      return;
    }

    int rowCount = tree.getRowCount();
    if (rowCount <= 0) return;

    // Reset accumulator after a brief idle period.
    long now = System.nanoTime();
    if (lastWheelEventNanos != 0L) {
      long dt = now - lastWheelEventNanos;
      if (dt > 250_000_000L) { // 250ms
        wheelDeltaAccumulator = 0.0d;
      }
    }
    lastWheelEventNanos = now;

    int wheelRotation = e.getWheelRotation();
    if (wheelRotation != 0) {
      int dir = wheelRotation > 0 ? 1 : -1;
      stepRequests.onNext(dir);
      e.consume();
      return;
    }

    double precise = e.getPreciseWheelRotation();
    if (precise == 0.0d) {
      e.consume();
      return;
    }

    wheelDeltaAccumulator += precise;

    // At most ONE row per event.
    if (wheelDeltaAccumulator >= 1.0d) {
      stepRequests.onNext(1);
      wheelDeltaAccumulator -= 1.0d;
    } else if (wheelDeltaAccumulator <= -1.0d) {
      stepRequests.onNext(-1);
      wheelDeltaAccumulator += 1.0d;
    }

    e.consume();
  }

  private void dispatchToScrollPane(MouseWheelEvent e) {
    if (e == null || scroll == null || e.isConsumed()) return;
    try {
      Point p = SwingUtilities.convertPoint(tree, e.getPoint(), scroll);
      MouseWheelEvent forwarded =
          new MouseWheelEvent(
              scroll,
              e.getID(),
              e.getWhen(),
              e.getModifiersEx(),
              p.x,
              p.y,
              e.getXOnScreen(),
              e.getYOnScreen(),
              e.getClickCount(),
              e.isPopupTrigger(),
              e.getScrollType(),
              e.getScrollAmount(),
              e.getWheelRotation(),
              e.getPreciseWheelRotation());
      scroll.dispatchEvent(forwarded);
      e.consume();
    } catch (Exception ignored) {
    }
  }

  private void moveSelectionBy(int deltaRows) {
    if (closed) return;
    if (deltaRows == 0) return;

    int rowCount = tree.getRowCount();
    if (rowCount <= 0) return;

    int dir = deltaRows > 0 ? 1 : -1;
    int current = tree.getLeadSelectionRow();
    if (current < 0) {
      current = (dir > 0) ? 0 : (rowCount - 1);
    }

    int next = current + deltaRows;
    next = Math.max(0, Math.min(rowCount - 1, next));

    if (next != current) {
      tree.setSelectionRow(next);
      tree.scrollRowToVisible(next);
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;

    try {
      tree.removeMouseWheelListener(wheelListener);
    } catch (Exception ignored) {
    }

    try {
      tree.removeHierarchyListener(hierarchyListener);
    } catch (Exception ignored) {
    }

    try {
      if (!subscription.isDisposed()) subscription.dispose();
    } catch (Exception ignored) {
    }

    try {
      scroll.setWheelScrollingEnabled(previousWheelScrollingEnabled);
    } catch (Exception ignored) {
    }
  }
}
