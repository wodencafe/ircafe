package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
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

  public static Context context(
      Supplier<Set<TreePath>> snapshotExpandedTreePaths,
      Consumer<Set<TreePath>> restoreExpandedTreePaths,
      BooleanSupplier showApplicationRoot,
      BooleanSupplier isApplicationRootAttached,
      IntSupplier rootChildCount,
      IntConsumer attachApplicationRoot,
      Runnable detachApplicationRoot,
      Runnable rootStructureChanged,
      Runnable expandApplicationRootPath,
      Supplier<TargetRef> selectedTargetRef,
      Supplier<TargetRef> firstServerStatusRef,
      Consumer<TargetRef> selectTarget,
      Runnable selectDefaultPath) {
    Objects.requireNonNull(snapshotExpandedTreePaths, "snapshotExpandedTreePaths");
    Objects.requireNonNull(restoreExpandedTreePaths, "restoreExpandedTreePaths");
    Objects.requireNonNull(showApplicationRoot, "showApplicationRoot");
    Objects.requireNonNull(isApplicationRootAttached, "isApplicationRootAttached");
    Objects.requireNonNull(rootChildCount, "rootChildCount");
    Objects.requireNonNull(attachApplicationRoot, "attachApplicationRoot");
    Objects.requireNonNull(detachApplicationRoot, "detachApplicationRoot");
    Objects.requireNonNull(rootStructureChanged, "rootStructureChanged");
    Objects.requireNonNull(expandApplicationRootPath, "expandApplicationRootPath");
    Objects.requireNonNull(selectedTargetRef, "selectedTargetRef");
    Objects.requireNonNull(firstServerStatusRef, "firstServerStatusRef");
    Objects.requireNonNull(selectTarget, "selectTarget");
    Objects.requireNonNull(selectDefaultPath, "selectDefaultPath");
    return new Context() {
      @Override
      public Set<TreePath> snapshotExpandedTreePaths() {
        return snapshotExpandedTreePaths.get();
      }

      @Override
      public void restoreExpandedTreePaths(Set<TreePath> expanded) {
        restoreExpandedTreePaths.accept(expanded);
      }

      @Override
      public boolean showApplicationRoot() {
        return showApplicationRoot.getAsBoolean();
      }

      @Override
      public boolean isApplicationRootAttached() {
        return isApplicationRootAttached.getAsBoolean();
      }

      @Override
      public int rootChildCount() {
        return rootChildCount.getAsInt();
      }

      @Override
      public void attachApplicationRoot(int index) {
        attachApplicationRoot.accept(index);
      }

      @Override
      public void detachApplicationRoot() {
        detachApplicationRoot.run();
      }

      @Override
      public void rootStructureChanged() {
        rootStructureChanged.run();
      }

      @Override
      public void expandApplicationRootPath() {
        expandApplicationRootPath.run();
      }

      @Override
      public TargetRef selectedTargetRef() {
        return selectedTargetRef.get();
      }

      @Override
      public TargetRef firstServerStatusRef() {
        return firstServerStatusRef.get();
      }

      @Override
      public void selectTarget(TargetRef ref) {
        selectTarget.accept(ref);
      }

      @Override
      public void selectDefaultPath() {
        selectDefaultPath.run();
      }
    };
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
