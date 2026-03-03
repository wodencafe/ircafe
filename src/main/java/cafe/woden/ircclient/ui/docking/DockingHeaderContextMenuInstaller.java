package cafe.woden.ircclient.ui.docking;

import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.internal.CustomTabbedPane;
import io.github.andrewauclair.moderndocking.internal.DisplayPanel;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Installs right-click context menus for docking tabs. */
public final class DockingHeaderContextMenuInstaller {
  private static volatile boolean installed;

  private DockingHeaderContextMenuInstaller() {}

  public static synchronized void install() {
    if (installed) return;
    AWTEventListener listener = DockingHeaderContextMenuInstaller::handleMouseEvent;
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);

    installed = true;
  }

  private static void handleMouseEvent(AWTEvent event) {
    if (!(event instanceof MouseEvent mouseEvent)) return;
    switch (mouseEvent.getID()) {
      case MouseEvent.MOUSE_PRESSED:
      case MouseEvent.MOUSE_RELEASED:
        maybeShowTabPopup(mouseEvent);
        break;
      default:
        break;
    }
  }

  private static void maybeShowTabPopup(MouseEvent event) {
    if (event == null || event.isConsumed() || !event.isPopupTrigger()) return;
    Component source = event.getComponent();
    if (source == null) return;

    CustomTabbedPane tabs = nearestCustomTabbedPane(source);
    if (tabs == null) return;

    java.awt.Point tabPoint = SwingUtilities.convertPoint(source, event.getPoint(), tabs);
    int tabIndex = tabs.indexAtLocation(tabPoint.x, tabPoint.y);
    if (tabIndex < 0) {
      int fallbackIndex = tabs.getTargetTabIndex(tabPoint, false);
      // getTargetTabIndex can return insertion-index semantics, so clamp to tab range.
      if (fallbackIndex >= 0 && fallbackIndex < tabs.getTabCount()) {
        tabIndex = fallbackIndex;
      }
    }
    if (tabIndex < 0) return;

    Dockable dockable = dockableForTabIndex(tabs, tabIndex);
    if (dockable == null) return;

    JPopupMenu menu = buildTabPopupMenu(dockable, () -> Docking.undock(dockable));
    if (menu.getComponentCount() == 0) return;

    menu.show(tabs, tabPoint.x, tabPoint.y);
    event.consume();
  }

  private static Dockable dockableForTabIndex(CustomTabbedPane tabs, int tabIndex) {
    try {
      Component panel = tabs.getComponentAt(tabIndex);
      if (!(panel instanceof DisplayPanel displayPanel)) return null;
      var wrapper = displayPanel.getWrapper();
      return wrapper == null ? null : wrapper.getDockable();
    } catch (Exception ignored) {
      return null;
    }
  }

  static JPopupMenu buildTabPopupMenu(Dockable dockable, Runnable closeAction) {
    Dockable safeDockable = Objects.requireNonNull(dockable, "dockable");
    Runnable safeCloseAction = Objects.requireNonNull(closeAction, "closeAction");

    JPopupMenu menu = new JPopupMenu();
    JMenuItem close = new JMenuItem("Close");
    close.setEnabled(safeDockable.isClosable());
    close.addActionListener(
        e -> {
          if (!safeDockable.isClosable()) return;
          if (!safeDockable.requestClose()) return;
          safeCloseAction.run();
        });
    menu.add(close);
    return menu;
  }

  private static CustomTabbedPane nearestCustomTabbedPane(Component source) {
    if (source instanceof CustomTabbedPane customTabbedPane) return customTabbedPane;
    return (CustomTabbedPane) SwingUtilities.getAncestorOfClass(CustomTabbedPane.class, source);
  }
}
