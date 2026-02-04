package cafe.woden.ircclient.ui.docking;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Small Swing/Docking utility tweaks.
 *
 * <p>ModernDocking (and many docking frameworks) use nested {@link JSplitPane}s internally.
 * By default, extra width/height is shared proportionally across nested splits during resize.
 *
 * <p>For IRCafe we generally want:
 * <ul>
 *   <li>Chat transcript in the middle to receive most extra space</li>
 *   <li>Side docks (Server / Users) to keep a stable width</li>
 *   <li>Input dock to keep a stable height</li>
 * </ul>
 */
public final class DockingTuner {
  private static final String CLIENT_PROP_SOUTH_LOCKED = "ircafe.lockSouthDockHeight";
  private static final String CLIENT_PROP_WEST_LOCKED = "ircafe.lockWestDockWidth";
  private static final String CLIENT_PROP_EAST_LOCKED = "ircafe.lockEastDockWidth";
  private static final String CLIENT_PROP_ADJUSTING = "ircafe.splitAdjusting";

  private DockingTuner() {}

  private enum Side {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
  }

  private record SplitCandidate(JSplitPane split, Side side, int depth, int score) {}

  /**
   * Best-effort: set an initial WEST dock width (pixels) on first open.
   *
   * <p>This is intentionally separate from {@link #lockWestDockWidth(Window, Component)}.
   * The lock will preserve whatever size is present when it captures. On first open,
   * docking frameworks can lay out side docks wider than desired (timing + preferred sizes).
   * This method nudges the divider to a reasonable default after the docking layout exists.
   */
  public static boolean applyInitialWestDockWidth(Window window, Component dockable, int targetWidthPx) {
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

    if (Math.abs(split.getDividerLocation() - desiredLoc) > 2) {
      setDividerLocationSafely(split, desiredLoc);
    }
    return true;
  }

  /**
   * Best-effort: set an initial EAST dock width (pixels) on first open.
   */
  public static boolean applyInitialEastDockWidth(Window window, Component dockable, int targetWidthPx) {
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

    if (Math.abs(split.getDividerLocation() - desiredLoc) > 2) {
      setDividerLocationSafely(split, desiredLoc);
    }
    return true;
  }

  /**
   * Best-effort: set an initial SOUTH dock height (pixels) on first open.
   */
  public static boolean applyInitialSouthDockHeight(Window window, Component dockable, int targetHeightPx) {
    if (window == null || dockable == null) return false;
    if (targetHeightPx <= 0) return false;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.VERTICAL_SPLIT);
    if (best == null) return false;

    JSplitPane split = best.split();
    int desiredLoc;

    if (best.side() == Side.BOTTOM) {
      if (split.getHeight() <= 0) return false;
      desiredLoc = clampDivider(split, desiredDividerForBottomHeight(split, targetHeightPx));
    } else {
      // Inverted split; dockable is on the TOP.
      desiredLoc = clampDivider(split, targetHeightPx);
    }

    if (Math.abs(split.getDividerLocation() - desiredLoc) > 2) {
      setDividerLocationSafely(split, desiredLoc);
    }
    return true;
  }

  /**
   * Finds the vertical {@link JSplitPane} that separates {@code dockable} and makes the dock's region
   * keep its captured height on window resize.
   */
  public static void lockSouthDockHeight(Window window, Component dockable) {
    if (window == null || dockable == null) return;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.VERTICAL_SPLIT);
    if (best == null) return;

    if (best.side() == Side.BOTTOM) {
      installSouthHeightLock(best.split());
    } else if (best.side() == Side.TOP) {
      // Some layouts invert the split; handle it by locking the TOP height instead.
      installNorthHeightLock(best.split());
    }
  }

  /**
   * Finds the horizontal {@link JSplitPane} that separates {@code dockable} and makes the dock's region
   * keep its captured width on window resize.
   */
  public static void lockWestDockWidth(Window window, Component dockable) {
    if (window == null || dockable == null) return;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return;

    if (best.side() == Side.LEFT) {
      installWestWidthLock(best.split());
    } else if (best.side() == Side.RIGHT) {
      // Inverted split: dockable is on the RIGHT, but we still want it to keep its width.
      installEastWidthLock(best.split());
    }
  }

  /**
   * Finds the horizontal {@link JSplitPane} that separates {@code dockable} and makes the dock's region
   * keep its captured width on window resize.
   */
  public static void lockEastDockWidth(Window window, Component dockable) {
    if (window == null || dockable == null) return;

    SplitCandidate best = findBestSplitPane(window, dockable, JSplitPane.HORIZONTAL_SPLIT);
    if (best == null) return;

    if (best.side() == Side.RIGHT) {
      installEastWidthLock(best.split());
    } else if (best.side() == Side.LEFT) {
      // Inverted split: dockable is on the LEFT, but we still want it to keep its width.
      installWestWidthLock(best.split());
    }
  }

  /**
   * Finds the "best" split pane to tune for a dockable.
   *
   * <p>We only want to adjust the *nearest* split pane that separates the dockable from the rest of the UI.
   * In nested docking layouts, a dockable can appear in many ancestor splits (containment is recursive).
   * Picking all of them leads to broken layouts.
   */
  private static SplitCandidate findBestSplitPane(Component root, Component dockable, int orientation) {
    SplitCandidate best = null;

    List<JSplitPane> allSplits = findAllSplitPanes(root);
    for (JSplitPane split : allSplits) {
      if (split.getOrientation() != orientation) continue;

      Component a = (orientation == JSplitPane.HORIZONTAL_SPLIT)
          ? split.getLeftComponent()
          : split.getTopComponent();
      Component b = (orientation == JSplitPane.HORIZONTAL_SPLIT)
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

      // Choose the nearest ancestor split (smallest depth). Tie-break with smallest subtree complexity.
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

  private static void installNorthHeightLock(JSplitPane split) {
    if (split == null) return;
    if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_SOUTH_LOCKED))) return;
    split.putClientProperty(CLIENT_PROP_SOUTH_LOCKED, Boolean.TRUE);

    // Give all future extra space to the bottom component.
    split.setResizeWeight(0.0);

    final int[] lockedTopHeight = new int[] { -1 };

    SwingUtilities.invokeLater(() -> {
      Component top = split.getTopComponent();
      if (top == null) return;
      int h = top.getHeight();
      if (h > 0) {
        lockedTopHeight[0] = h;
        setDividerLocationSafely(split, h);
      }
    });

    // If the user drags the divider, treat that as the new locked height.
    split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
      if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_ADJUSTING))) return;
      int loc = split.getDividerLocation();
      if (loc > 0) lockedTopHeight[0] = loc;
    });

    split.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (lockedTopHeight[0] < 0) {
          Component top = split.getTopComponent();
          if (top == null) return;
          int h = top.getHeight();
          if (h <= 0) return;
          lockedTopHeight[0] = h;
        }

        int desired = lockedTopHeight[0];
        int min = split.getMinimumDividerLocation();
        int max = split.getMaximumDividerLocation();
        desired = Math.max(min, Math.min(max, desired));

        if (Math.abs(split.getDividerLocation() - desired) > 2) {
          setDividerLocationSafely(split, desired);
        }
      }
    });
  }

  private static void installSouthHeightLock(JSplitPane split) {
    if (split == null) return;
    if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_SOUTH_LOCKED))) return;
    split.putClientProperty(CLIENT_PROP_SOUTH_LOCKED, Boolean.TRUE);

    // Give all future extra space to the top component.
    split.setResizeWeight(1.0);

    final int[] lockedBottomHeight = new int[] { -1 };

    // Try once immediately after layout.
    SwingUtilities.invokeLater(() -> {
      Component bottom = split.getBottomComponent();
      if (bottom == null) return;
      int h = bottom.getHeight();
      if (h > 0) {
        lockedBottomHeight[0] = h;
        int divider = split.getDividerSize();
        int total = split.getHeight();
        int newDividerLocation = Math.max(0, total - h - divider);
        setDividerLocationSafely(split, newDividerLocation);
      }
    });

    split.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        Component bottom = split.getBottomComponent();
        if (bottom == null) return;

        if (lockedBottomHeight[0] < 0) {
          int h = bottom.getHeight();
          if (h <= 0) return; // not laid out yet
          lockedBottomHeight[0] = h;
        }

        int desiredBottom = lockedBottomHeight[0];
        int divider = split.getDividerSize();
        int total = split.getHeight();
        int newDividerLocation = Math.max(0, total - desiredBottom - divider);

        if (Math.abs(split.getDividerLocation() - newDividerLocation) > 2) {
          setDividerLocationSafely(split, newDividerLocation);
        }
      }
    });
  }

  private static void installWestWidthLock(JSplitPane split) {
    if (split == null) return;
    if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_WEST_LOCKED))) return;
    split.putClientProperty(CLIENT_PROP_WEST_LOCKED, Boolean.TRUE);

    // Give all future extra horizontal space to the right component.
    split.setResizeWeight(0.0);

    final int[] lockedLeftWidth = new int[] { -1 };

    SwingUtilities.invokeLater(() -> {
      Component left = split.getLeftComponent();
      if (left == null) return;
      int w = left.getWidth();
      if (w > 0) {
        lockedLeftWidth[0] = w;
        setDividerLocationSafely(split, w);
      }
    });

    // If the user drags the divider, treat that as the new locked width.
    split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
      if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_ADJUSTING))) return;
      int loc = split.getDividerLocation();
      if (loc > 0) lockedLeftWidth[0] = loc;
    });

    split.addComponentListener(new ComponentAdapter() {
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

  private static void installEastWidthLock(JSplitPane split) {
    if (split == null) return;
    if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_EAST_LOCKED))) return;
    split.putClientProperty(CLIENT_PROP_EAST_LOCKED, Boolean.TRUE);

    // Give all future extra horizontal space to the left component.
    split.setResizeWeight(1.0);

    final int[] lockedRightWidth = new int[] { -1 };

    SwingUtilities.invokeLater(() -> {
      Component right = split.getRightComponent();
      if (right == null) return;
      int w = right.getWidth();
      if (w > 0) {
        lockedRightWidth[0] = w;
        int desiredLoc = desiredDividerForRightWidth(split, w);
        setDividerLocationSafely(split, desiredLoc);
      }
    });

    // If the user drags the divider, treat that as the new locked width.
    split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
      if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_ADJUSTING))) return;
      int total = split.getWidth();
      if (total <= 0) return;
      int divider = split.getDividerSize();
      int loc = split.getDividerLocation();
      int w = Math.max(0, total - divider - loc);
      if (w > 0) lockedRightWidth[0] = w;
    });

    split.addComponentListener(new ComponentAdapter() {
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

  private static int desiredDividerForBottomHeight(JSplitPane split, int bottomHeight) {
    int total = split.getHeight();
    int divider = split.getDividerSize();
    // DividerLocation is measured from the top edge.
    return Math.max(0, total - divider - Math.max(0, bottomHeight));
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
