package cafe.woden.ircclient.ui.util;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiFunction;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Decorates a {@link JList} with a standard right-click context menu behavior.
 *
 * <ul>
 *   <li>On popup trigger: determine index under mouse (if any)</li>
 *   <li>Optionally select that index</li>
 *   <li>Build a menu and show it at the mouse location</li>
 * </ul>
 */
public final class ListContextMenuDecorator<T> implements AutoCloseable {

  private final JList<T> list;
  private final MouseAdapter mouse;

  private ListContextMenuDecorator(
      JList<T> list,
      boolean selectOnPopup,
      BiFunction<Integer, MouseEvent, JPopupMenu> menuFactory
  ) {
    this.list = list;

    this.mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        maybeShow(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        maybeShow(e);
      }

      private void maybeShow(MouseEvent e) {
        if (e == null) return;
        if (!e.isPopupTrigger() && !SwingUtilities.isRightMouseButton(e)) return;

        int index = indexAt(e.getPoint());
        if (index < 0) return;

        if (selectOnPopup) {
          list.setSelectedIndex(index);
        }

        JPopupMenu menu = null;
        try {
          menu = (menuFactory != null) ? menuFactory.apply(index, e) : null;
        } catch (Exception ignored) {
        }

        if (menu == null || menu.getComponentCount() == 0) return;
        PopupMenuThemeSupport.prepareForDisplay(menu);
        menu.show(list, e.getX(), e.getY());
      }

      private int indexAt(Point p) {
        if (p == null) return -1;
        int idx = list.locationToIndex(p);
        if (idx < 0) return -1;
        // locationToIndex returns nearest row; verify the click is actually inside the cell bounds.
        var bounds = list.getCellBounds(idx, idx);
        if (bounds == null || !bounds.contains(p)) return -1;
        return idx;
      }
    };

    list.addMouseListener(this.mouse);
  }

  public static <T> ListContextMenuDecorator<T> decorate(
      JList<T> list,
      boolean selectOnPopup,
      BiFunction<Integer, MouseEvent, JPopupMenu> menuFactory
  ) {
    if (list == null) {
      throw new IllegalArgumentException("list must not be null");
    }
    return new ListContextMenuDecorator<>(list, selectOnPopup, menuFactory);
  }

  @Override
  public void close() {
    try {
      list.removeMouseListener(mouse);
    } catch (Exception ignored) {
    }
  }
}
