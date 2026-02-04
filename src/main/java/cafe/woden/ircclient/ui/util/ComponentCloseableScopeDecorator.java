package cafe.woden.ircclient.ui.util;

import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import javax.swing.JComponent;

public final class ComponentCloseableScopeDecorator {

  private ComponentCloseableScopeDecorator() {}

  public static CloseableScope install(JComponent component) {
    CloseableScope scope = new CloseableScope();
    if (component == null) return scope;

    HierarchyListener listener = new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
        if (!component.isDisplayable()) {
          scope.closeQuietly();
        }
      }
    };

    component.addHierarchyListener(listener);
    scope.addCleanup(() -> component.removeHierarchyListener(listener));

    return scope;
  }
}
