package cafe.woden.ircclient.ui.docking;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Small Swing/Docking utility tweaks.
 *
 * ModernDocking (and many docking frameworks) use nested split panes internally.
 * When the main window grows vertically, any SOUTH dock in a vertical split will
 * typically grow proportionally too.
 *
 * For the IRCafe "Input" dock, we generally want the chat transcript to get all
 * extra vertical space while the input stays at a stable, usable height.
 */
public final class DockingTuner {
  private static final String CLIENT_PROP_LOCKED = "ircafe.lockSouthDockHeight";

  private DockingTuner() {}

  /**
   * Finds the vertical JSplitPane that contains {@code dockable} in its bottom component
   * and makes that bottom region keep its initial height on window resize.
   */
  public static void lockSouthDockHeight(Window window, Component dockable) {
    if (window == null || dockable == null) return;
    for (JSplitPane split : findAllSplitPanes(window)) {
      if (split.getOrientation() != JSplitPane.VERTICAL_SPLIT) continue;
      Component bottom = split.getBottomComponent();
      if (bottom == null) continue;
      if (!containsComponent(bottom, dockable)) continue;

      installSouthHeightLock(split);
    }
  }

  private static void installSouthHeightLock(JSplitPane split) {
    if (split == null) return;
    if (Boolean.TRUE.equals(split.getClientProperty(CLIENT_PROP_LOCKED))) return;
    split.putClientProperty(CLIENT_PROP_LOCKED, Boolean.TRUE);

    // Give all *future* extra space to the top component.
    split.setResizeWeight(1.0);

    // Capture the initial bottom region height once it's non-zero, then keep it fixed.
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
        split.setDividerLocation(newDividerLocation);
      }
    });

    split.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        Component bottom = split.getBottomComponent();
        if (bottom == null) return;

        if (lockedBottomHeight[0] < 0) {
          int h = bottom.getHeight();
          if (h <= 0) {
            // Not laid out yet; try again on the next resize event.
            return;
          }
          lockedBottomHeight[0] = h;
        }

        int desiredBottom = lockedBottomHeight[0];
        int divider = split.getDividerSize();
        int total = split.getHeight();
        int newDividerLocation = Math.max(0, total - desiredBottom - divider);

        // Avoid thrashing if we're already close.
        if (Math.abs(split.getDividerLocation() - newDividerLocation) > 2) {
          split.setDividerLocation(newDividerLocation);
        }
      }
    });
  }

  private static boolean containsComponent(Component root, Component target) {
    if (root == target) return true;
    if (!(root instanceof Container c)) return false;
    for (Component child : c.getComponents()) {
      if (containsComponent(child, target)) return true;
    }
    return false;
  }

  private static java.util.List<JSplitPane> findAllSplitPanes(Component root) {
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
