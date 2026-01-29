package cafe.woden.ircclient.ui.util;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 * Decorates a {@link JTree} with a right-click context menu.
 *
 */
public final class TreeContextMenuDecorator implements AutoCloseable {

  private final JTree tree;
  private final MouseAdapter mouseListener;
  private final boolean selectPathOnPopup;

  private boolean closed = false;

  private TreeContextMenuDecorator(
      JTree tree,
      BiFunction<TreePath, MouseEvent, JPopupMenu> menuFactory,
      boolean selectPathOnPopup
  ) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.selectPathOnPopup = selectPathOnPopup;

    Objects.requireNonNull(menuFactory, "menuFactory");

    this.mouseListener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        maybeShowPopup(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        maybeShowPopup(e);
      }

      private void maybeShowPopup(MouseEvent e) {
        if (closed) return;
        if (e == null || !e.isPopupTrigger()) return;
        if (!tree.isShowing() || !tree.isEnabled()) return;

        int x = e.getX();
        int y = e.getY();
        TreePath path = tree.getPathForLocation(x, y);
        if (path == null) return;

        if (TreeContextMenuDecorator.this.selectPathOnPopup) {
          try {
            tree.setSelectionPath(path);
          } catch (Exception ignored) {
          }
        }

        JPopupMenu menu = null;
        try {
          menu = menuFactory.apply(path, e);
        } catch (Exception ignored) {
        }

        if (menu == null || menu.getComponentCount() == 0) return;
        menu.show(tree, x, y);
      }
    };

    tree.addMouseListener(this.mouseListener);
  }

  /**
   * Installs a context menu decorator that does <b>not</b> change selection when opening the popup.
   */
  public static TreeContextMenuDecorator decorate(JTree tree, Function<TreePath, JPopupMenu> menuFactory) {
    Objects.requireNonNull(menuFactory, "menuFactory");
    return new TreeContextMenuDecorator(tree, (path, e) -> menuFactory.apply(path), false);
  }

  /**
   * Installs a context menu decorator.
   *
   * @param selectPathOnPopup if true, right-clicking will select the row before showing the menu.
   */
  public static TreeContextMenuDecorator decorate(
      JTree tree,
      Function<TreePath, JPopupMenu> menuFactory,
      boolean selectPathOnPopup
  ) {
    Objects.requireNonNull(menuFactory, "menuFactory");
    return new TreeContextMenuDecorator(tree, (path, e) -> menuFactory.apply(path), selectPathOnPopup);
  }

  /**
   * Advanced overload that can use the {@link MouseEvent} (e.g. modifiers) when building the menu.
   */
  public static TreeContextMenuDecorator decorate(
      JTree tree,
      BiFunction<TreePath, MouseEvent, JPopupMenu> menuFactory,
      boolean selectPathOnPopup
  ) {
    return new TreeContextMenuDecorator(tree, menuFactory, selectPathOnPopup);
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    try {
      tree.removeMouseListener(mouseListener);
    } catch (Exception ignored) {
    }
  }
}
