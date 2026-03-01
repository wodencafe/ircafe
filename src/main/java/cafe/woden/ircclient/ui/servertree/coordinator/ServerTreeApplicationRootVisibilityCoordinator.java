package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.TreePath;

/** Orchestrates showing/hiding the application root while preserving selection and expansion. */
public final class ServerTreeApplicationRootVisibilityCoordinator {

  public interface Context {
    Set<TreePath> snapshotExpandedTreePaths();

    void restoreExpandedTreePaths(Set<TreePath> expanded);

    boolean showApplicationRoot();

    boolean isApplicationRootAttached();

    int rootChildCount();

    void attachApplicationRoot(int index);

    void detachApplicationRoot();

    void rootStructureChanged();

    void expandApplicationRootPath();

    TargetRef selectedTargetRef();

    TargetRef firstServerStatusRef();

    void selectTarget(TargetRef ref);

    void selectDefaultPath();
  }

  private final Context context;

  public ServerTreeApplicationRootVisibilityCoordinator(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public void syncApplicationRootVisibility() {
    Set<TreePath> expandedBefore = context.snapshotExpandedTreePaths();
    boolean structureChanged = false;

    if (context.showApplicationRoot()) {
      if (!context.isApplicationRootAttached()) {
        context.attachApplicationRoot(Math.min(1, context.rootChildCount()));
        context.rootStructureChanged();
        structureChanged = true;
      }
      if (structureChanged) context.restoreExpandedTreePaths(expandedBefore);
      context.expandApplicationRootPath();
      return;
    }

    TargetRef selected = context.selectedTargetRef();
    if (selected != null && selected.isApplicationUi()) {
      TargetRef first = context.firstServerStatusRef();
      if (first != null) {
        context.selectTarget(first);
      } else {
        context.selectDefaultPath();
      }
    }

    if (context.isApplicationRootAttached()) {
      context.detachApplicationRoot();
      context.rootStructureChanged();
      structureChanged = true;
    }
    if (structureChanged) context.restoreExpandedTreePaths(expandedBefore);
  }
}
