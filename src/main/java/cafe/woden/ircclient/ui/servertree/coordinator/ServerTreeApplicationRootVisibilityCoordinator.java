package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.tree.TreePath;
import org.springframework.stereotype.Component;

/** Orchestrates showing/hiding the application root while preserving selection and expansion. */
@Component
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

  public void syncApplicationRootVisibility(Context context) {
    Context in = Objects.requireNonNull(context, "context");
    Set<TreePath> expandedBefore = in.snapshotExpandedTreePaths();
    boolean structureChanged = false;

    if (in.showApplicationRoot()) {
      if (!in.isApplicationRootAttached()) {
        in.attachApplicationRoot(Math.min(1, in.rootChildCount()));
        in.rootStructureChanged();
        structureChanged = true;
      }
      if (structureChanged) in.restoreExpandedTreePaths(expandedBefore);
      in.expandApplicationRootPath();
      return;
    }

    TargetRef selected = in.selectedTargetRef();
    if (selected != null && selected.isApplicationUi()) {
      TargetRef first = in.firstServerStatusRef();
      if (first != null) {
        in.selectTarget(first);
      } else {
        in.selectDefaultPath();
      }
    }

    if (in.isApplicationRootAttached()) {
      in.detachApplicationRoot();
      in.rootStructureChanged();
      structureChanged = true;
    }
    if (structureChanged) in.restoreExpandedTreePaths(expandedBefore);
  }
}
