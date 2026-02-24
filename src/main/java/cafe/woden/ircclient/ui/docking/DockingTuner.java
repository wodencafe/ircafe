package cafe.woden.ircclient.ui.docking;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Swing/docking helpers for keeping side docks at a stable size (nested {@link JSplitPane} tuning).
 */
public final class DockingTuner {
  private static final String CLIENT_PROP_WEST_LOCKED = "ircafe.lockWestDockWidth";
  private static final String CLIENT_PROP_EAST_LOCKED = "ircafe.lockEastDockWidth";
  private static final String CLIENT_PROP_WEST_LOCK_WIDTH = "ircafe.lockWestDockWidthPx";
  private static final String CLIENT_PROP_EAST_LOCK_WIDTH = "ircafe.lockEastDockWidthPx";
  private static final String CLIENT_PROP_ADJUSTING = "ircafe.splitAdjusting";
  private static final String CLIENT_PROP_PERSIST_WIDTH_PREFIX = "ircafe.persistDockWidth.";

  private static final Logger log = LoggerFactory.getLogger(DockingTuner.class);

  private DockingTuner() {}

  private enum Side {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
  }

  private record SplitCandidate(JSplitPane split, Side side, int depth, int score) {}

  public static boolean applyInitialWestDockWidth(
      Window window, Component dockable, int targetWidthPx) {
    if (window == null || dockable == null) return false;
    if (targetWidthPx <= 0) return false;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return false;

    JSplitPane split = best.split();
    int desiredLoc;

    if (best.side() == Side.LEFT) {
      desiredLoc = clampDivider(split, targetWidthPx);
    } else {
      // Inverted split; dockable lives on the RIGHT.
      if (split.getWidth() <= 0) return false;
      desiredLoc = clampDivider(split, desiredDividerForRightWidth(split, targetWidthPx));
    }

    int before = split.getDividerLocation();
    if (Math.abs(before - desiredLoc) > 2) {
      setDividerLocationSafely(split, desiredLoc);
      log.info(
          "dock-size: init WEST targetPx={} split#{} side={} divider {} -> {} (splitW={}, dividerSize={})",
          targetWidthPx,
          System.identityHashCode(split),
          best.side(),
          before,
          desiredLoc,
          split.getWidth(),
          split.getDividerSize());
    }
    return true;
  }

  public static boolean applyInitialEastDockWidth(
      Window window, Component dockable, int targetWidthPx) {
    if (window == null || dockable == null) return false;
    if (targetWidthPx <= 0) return false;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return false;

    JSplitPane split = best.split();
    int desiredLoc;

    if (best.side() == Side.RIGHT) {
      if (split.getWidth() <= 0) return false;
      desiredLoc = clampDivider(split, desiredDividerForRightWidth(split, targetWidthPx));
    } else {
      // Inverted split; dockable is on the LEFT.
      desiredLoc = clampDivider(split, targetWidthPx);
    }

    int before = split.getDividerLocation();
    if (Math.abs(before - desiredLoc) > 2) {
      setDividerLocationSafely(split, desiredLoc);
      log.info(
          "dock-size: init EAST targetPx={} split#{} side={} divider {} -> {} (splitW={}, dividerSize={})",
          targetWidthPx,
          System.identityHashCode(split),
          best.side(),
          before,
          desiredLoc,
          split.getWidth(),
          split.getDividerSize());
    }
    return true;
  }

  /**
   * Finds the vertical {@link JSplitPane} that separates {@code dockable} and makes the dock's
   * region keep its captured height on window resize.
   */

  /**
   * Finds the horizontal {@link JSplitPane} that separates {@code dockable} and makes the dock's
   * region keep its captured width on window resize.
   */
  public static void lockWestDockWidth(Window window, Component dockable) {
    if (window == null || dockable == null) return;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return;

    if (best.side() == Side.LEFT) {
      installWestWidthLock(best.split(), null);
    } else if (best.side() == Side.RIGHT) {
      // Inverted split: dockable is on the RIGHT, but we still want it to keep its width.
      installEastWidthLock(best.split(), null);
    }
  }

  /**
   * Like {@link #lockWestDockWidth(Window, Component)} but seeds the lock with a specific width.
   *
   * <p>This is useful during startup: ModernDocking may briefly lay out side docks too wide, and a
   * naive "capture current width" can lock in that transient value and fight later nudges. Seeding
   * ensures the dock starts (and stays) at the configured width unless the user drags.
   */
  public static void lockWestDockWidth(Window window, Component dockable, int seedWidthPx) {
    if (window == null || dockable == null) return;
    if (seedWidthPx <= 0) {
      lockWestDockWidth(window, dockable);
      return;
    }

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return;

    if (best.side() == Side.LEFT) {
      installWestWidthLock(best.split(), seedWidthPx);
    } else if (best.side() == Side.RIGHT) {
      // Inverted split: dockable is on the RIGHT; seed as a right-width lock.
      installEastWidthLock(best.split(), seedWidthPx);
    }
  }

  /**
   * Finds the horizontal {@link JSplitPane} that separates {@code dockable} and makes the dock's
   * region keep its captured width on window resize.
   */
  public static void lockEastDockWidth(Window window, Component dockable) {
    if (window == null || dockable == null) return;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return;

    if (best.side() == Side.RIGHT) {
      installEastWidthLock(best.split(), null);
    } else if (best.side() == Side.LEFT) {
      // Inverted split: dockable is on the LEFT, but we still want it to keep its width.
      installWestWidthLock(best.split(), null);
    }
  }

  /**
   * Like {@link #lockEastDockWidth(Window, Component)} but seeds the lock with a specific width.
   *
   * <p>This is useful during startup or when ModernDocking rebuilds split panes: a naive "capture
   * current width" can lock in a transient value and ignore configuration changes. Seeding ensures
   * the dock starts (and stays) at the configured width unless the user drags the divider.
   */
  public static void lockEastDockWidth(Window window, Component dockable, int seedWidthPx) {
    if (window == null || dockable == null) return;
    if (seedWidthPx <= 0) {
      lockEastDockWidth(window, dockable);
      return;
    }

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return;

    if (best.side() == Side.RIGHT) {
      installEastWidthLock(best.split(), seedWidthPx);
    } else if (best.side() == Side.LEFT) {
      // Inverted split: dockable is on the LEFT; seed as a left-width lock.
      installWestWidthLock(best.split(), seedWidthPx);
    }
  }

  /**
   * Watches the split pane that controls {@code dockable}'s width and calls {@code onWidthPx} when
   * the user drags the divider.
   *
   * <p>This ignores programmatic divider adjustments performed by this class (see {@link
   * #setDividerLocationSafely}).
   */
  public static void watchDockWidthOnUserResize(
      Window window, Component dockable, java.util.function.IntConsumer onWidthPx) {
    if (window == null || dockable == null || onWidthPx == null) return;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return;

    JSplitPane split = best.split();
    String key = CLIENT_PROP_PERSIST_WIDTH_PREFIX + System.identityHashCode(dockable);
    if (Boolean.TRUE.equals(split.getClientProperty(key))) return;
    split.putClientProperty(key, Boolean.TRUE);

    split.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        evt -> {
          if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_ADJUSTING))) return;

          // Divider move events can fire before the split/dock bounds have fully settled.
          // Persist a *stable* "side width" derived from the split pane (not the dockable's inner
          // width),
          // because the dockable is usually wrapped in tabs/padding and can be narrower than the
          // split side.
          SwingUtilities.invokeLater(
              () -> {
                int w = -1;

                if (best.side() == Side.LEFT) {
                  // DividerLocation is measured from the left edge and corresponds to the LEFT side
                  // width.
                  w = split.getDividerLocation();
                } else if (best.side() == Side.RIGHT) {
                  // Compute the RIGHT side width from the divider location.
                  int total = split.getWidth();
                  if (total > 0) {
                    int divider = split.getDividerSize();
                    int loc = split.getDividerLocation();
                    w = Math.max(0, total - divider - loc);
                  }
                }

                // Fallback: best-effort dockable width (still better than nothing).
                if (w <= 0) w = dockable.getWidth();

                if (w > 0) onWidthPx.accept(w);
              });
        });
  }

  /** Finds the "best" split pane to tune for a dockable. */
  private static SplitCandidate findBestSplitPane(
      Component root, Component dockable, int orientation) {
    SplitCandidate best = null;

    List<JSplitPane> allSplits = findAllSplitPanes(root);
    for (JSplitPane split : allSplits) {
      if (split.getOrientation() != orientation) continue;

      Component a =
          (orientation == JSplitPane.HORIZONTAL_SPLIT)
              ? split.getLeftComponent()
              : split.getTopComponent();
      Component b =
          (orientation == JSplitPane.HORIZONTAL_SPLIT)
              ? split.getRightComponent()
              : split.getBottomComponent();

      if (a == null || b == null) continue;

      boolean aHas = containsComponent(a, dockable);
      boolean bHas = containsComponent(b, dockable);
      if (aHas == bHas) continue; // either neither side has it, or both do

      Side side;
      Component sideComponent;
      if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
        side = aHas ? Side.LEFT : Side.RIGHT;
        sideComponent = aHas ? a : b;
      } else {
        side = aHas ? Side.TOP : Side.BOTTOM;
        sideComponent = aHas ? a : b;
      }

      int depth = depthToAncestor(dockable, split);
      if (depth < 0) continue;

      int score = countComponentNodes(sideComponent);
      SplitCandidate cand = new SplitCandidate(split, side, depth, score);

      // Choose the nearest ancestor split (smallest depth). Tie-break with smallest subtree
      // complexity.
      if (best == null
          || cand.depth() < best.depth()
          || (cand.depth() == best.depth() && cand.score() < best.score())) {
        best = cand;
      }
    }

    return best;
  }

  private static int depthToAncestor(Component start, Component ancestor) {
    int depth = 0;
    Component c = start;
    while (c != null) {
      if (c == ancestor) return depth;
      c = c.getParent();
      depth++;
    }
    return -1;
  }

  private static int countComponentNodes(Component root) {
    if (root == null) return Integer.MAX_VALUE;
    int count = 1;
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) {
        if (child != null) count += countComponentNodes(child);
      }
    }
    return count;
  }

  private static void installWestWidthLock(JSplitPane split, Integer seedLeftWidthPx) {
    if (split == null) return;

    int[] lockedLeftWidth = widthBox(split, CLIENT_PROP_WEST_LOCK_WIDTH);
    if (seedLeftWidthPx != null && seedLeftWidthPx > 0) {
      lockedLeftWidth[0] = seedLeftWidthPx;
    }
    if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_WEST_LOCKED))) {
      if (lockedLeftWidth[0] > 0) {
        int desired = clampDivider(split, lockedLeftWidth[0]);
        setDividerLocationSafely(split, desired);
      }
      return;
    }
    split.putClientProperty(CLIENT_PROP_WEST_LOCKED, Boolean.TRUE);

    log.info("dock-lock: install WEST (left-locked) split#{}", System.identityHashCode(split));

    // Give all future extra horizontal space to the right component.
    split.setResizeWeight(0.0);

    SwingUtilities.invokeLater(
        () -> {
          if (lockedLeftWidth[0] > 0) {
            int desired = clampDivider(split, lockedLeftWidth[0]);
            setDividerLocationSafely(split, desired);
            log.info(
                "dock-lock: seeded WEST leftWidthPx={} split#{} -> dividerLoc={} (splitW={}, dividerSize={})",
                lockedLeftWidth[0],
                System.identityHashCode(split),
                desired,
                split.getWidth(),
                split.getDividerSize());
            return;
          }

          Component left = split.getLeftComponent();
          if (left == null) return;
          int w = left.getWidth();
          if (w > 0) {
            lockedLeftWidth[0] = w;
            setDividerLocationSafely(split, w);

            log.info(
                "dock-lock: captured WEST leftWidthPx={} split#{} (splitW={}, dividerSize={})",
                w,
                System.identityHashCode(split),
                split.getWidth(),
                split.getDividerSize());
          }
        });

    // If the user drags the divider, treat that as the new locked width.
    split.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        evt -> {
          if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_ADJUSTING))) return;
          int loc = split.getDividerLocation();
          if (loc > 0) lockedLeftWidth[0] = loc;
        });

    split.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (lockedLeftWidth[0] < 0) {
              Component left = split.getLeftComponent();
              if (left == null) return;
              int w = left.getWidth();
              if (w <= 0) return;
              lockedLeftWidth[0] = w;
            }

            int desired = lockedLeftWidth[0];
            int min = split.getMinimumDividerLocation();
            int max = split.getMaximumDividerLocation();
            desired = Math.max(min, Math.min(max, desired));

            if (Math.abs(split.getDividerLocation() - desired) > 2) {
              setDividerLocationSafely(split, desired);
            }
          }
        });
  }

  private static void installEastWidthLock(JSplitPane split, Integer seedRightWidthPx) {
    if (split == null) return;

    int[] lockedRightWidth = widthBox(split, CLIENT_PROP_EAST_LOCK_WIDTH);
    if (seedRightWidthPx != null && seedRightWidthPx > 0) {
      lockedRightWidth[0] = seedRightWidthPx;
    }
    if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_EAST_LOCKED))) {
      if (lockedRightWidth[0] > 0) {
        int desiredLoc = desiredDividerForRightWidth(split, lockedRightWidth[0]);
        setDividerLocationSafely(split, clampDivider(split, desiredLoc));
      }
      return;
    }
    split.putClientProperty(CLIENT_PROP_EAST_LOCKED, Boolean.TRUE);

    log.info("dock-lock: install EAST (right-locked) split#{}", System.identityHashCode(split));

    // Give all future extra horizontal space to the left component.
    split.setResizeWeight(1.0);

    SwingUtilities.invokeLater(
        () -> {
          if (lockedRightWidth[0] > 0) {
            int desiredLoc = desiredDividerForRightWidth(split, lockedRightWidth[0]);
            setDividerLocationSafely(split, clampDivider(split, desiredLoc));

            log.info(
                "dock-lock: seeded EAST rightWidthPx={} split#{} -> dividerLoc={} (splitW={}, dividerSize={})",
                lockedRightWidth[0],
                System.identityHashCode(split),
                desiredLoc,
                split.getWidth(),
                split.getDividerSize());
            return;
          }

          Component right = split.getRightComponent();
          if (right == null) return;
          int w = right.getWidth();
          if (w > 0) {
            lockedRightWidth[0] = w;
            int desiredLoc = desiredDividerForRightWidth(split, w);
            setDividerLocationSafely(split, desiredLoc);

            log.info(
                "dock-lock: captured EAST rightWidthPx={} split#{} -> dividerLoc={} (splitW={}, dividerSize={})",
                w,
                System.identityHashCode(split),
                desiredLoc,
                split.getWidth(),
                split.getDividerSize());
          }
        });

    // If the user drags the divider, treat that as the new locked width.
    split.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        evt -> {
          if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_ADJUSTING))) return;
          int total = split.getWidth();
          if (total <= 0) return;
          int divider = split.getDividerSize();
          int loc = split.getDividerLocation();
          int w = Math.max(0, total - divider - loc);
          if (w > 0) lockedRightWidth[0] = w;
        });

    split.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (lockedRightWidth[0] < 0) {
              Component right = split.getRightComponent();
              if (right == null) return;
              int w = right.getWidth();
              if (w <= 0) return;
              lockedRightWidth[0] = w;
            }

            int desiredLoc = desiredDividerForRightWidth(split, lockedRightWidth[0]);
            int min = split.getMinimumDividerLocation();
            int max = split.getMaximumDividerLocation();
            desiredLoc = Math.max(min, Math.min(max, desiredLoc));

            if (Math.abs(split.getDividerLocation() - desiredLoc) > 2) {
              setDividerLocationSafely(split, desiredLoc);
            }
          }
        });
  }

  private static int desiredDividerForRightWidth(JSplitPane split, int rightWidth) {
    int total = split.getWidth();
    int divider = split.getDividerSize();
    // DividerLocation is measured from the left edge.
    return Math.max(0, total - divider - Math.max(0, rightWidth));
  }

  private static int[] widthBox(JSplitPane split, String key) {
    Object existing = split.getClientProperty(key);
    if (existing instanceof int[] arr && arr.length > 0) {
      return arr;
    }
    int[] arr = new int[] {-1};
    split.putClientProperty(key, arr);
    return arr;
  }

  private static int clampDivider(JSplitPane split, int desired) {
    if (desired < 0) desired = 0;
    int min = split.getMinimumDividerLocation();
    int max = split.getMaximumDividerLocation();
    // If the split isn't laid out yet, Swing can report 0/0; don't over-clamp.
    if (max <= 0) return Math.max(0, desired);
    return Math.max(min, Math.min(max, desired));
  }

  private static void setDividerLocationSafely(JSplitPane split, int location) {
    if (split == null) return;
    try {
      split.putClientProperty(CLIENT_PROP_ADJUSTING, Boolean.TRUE);
      split.setDividerLocation(location);
    } finally {
      split.putClientProperty(CLIENT_PROP_ADJUSTING, Boolean.FALSE);
    }
  }

  private static boolean containsComponent(Component root, Component target) {
    if (root == target) return true;
    if (!(root instanceof Container c)) return false;
    for (Component child : c.getComponents()) {
      if (containsComponent(child, target)) return true;
    }
    return false;
  }

  private static List<JSplitPane> findAllSplitPanes(Component root) {
    java.util.ArrayList<JSplitPane> out = new java.util.ArrayList<>();
    if (root == null) return out;

    Deque<Component> q = new ArrayDeque<>();
    q.add(root);

    while (!q.isEmpty()) {
      Component c = q.removeFirst();
      if (c instanceof JSplitPane sp) out.add(sp);
      if (c instanceof Container container) {
        for (Component child : container.getComponents()) {
          if (child != null) q.add(child);
        }
      }
    }

    return out;
  }
}
