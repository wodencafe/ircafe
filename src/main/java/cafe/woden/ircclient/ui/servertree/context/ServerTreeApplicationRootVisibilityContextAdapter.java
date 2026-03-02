package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeApplicationRootVisibilityCoordinator;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.tree.TreePath;

/** Adapter for {@link ServerTreeApplicationRootVisibilityCoordinator.Context}. */
public final class ServerTreeApplicationRootVisibilityContextAdapter
    implements ServerTreeApplicationRootVisibilityCoordinator.Context {

  private final Supplier<Set<TreePath>> snapshotExpandedTreePaths;
  private final Consumer<Set<TreePath>> restoreExpandedTreePaths;
  private final BooleanSupplier showApplicationRoot;
  private final BooleanSupplier isApplicationRootAttached;
  private final IntSupplier rootChildCount;
  private final java.util.function.IntConsumer attachApplicationRoot;
  private final Runnable detachApplicationRoot;
  private final Runnable rootStructureChanged;
  private final Runnable expandApplicationRootPath;
  private final Supplier<TargetRef> selectedTargetRef;
  private final Supplier<TargetRef> firstServerStatusRef;
  private final Consumer<TargetRef> selectTarget;
  private final Runnable selectDefaultPath;

  public ServerTreeApplicationRootVisibilityContextAdapter(
      Supplier<Set<TreePath>> snapshotExpandedTreePaths,
      Consumer<Set<TreePath>> restoreExpandedTreePaths,
      BooleanSupplier showApplicationRoot,
      BooleanSupplier isApplicationRootAttached,
      IntSupplier rootChildCount,
      java.util.function.IntConsumer attachApplicationRoot,
      Runnable detachApplicationRoot,
      Runnable rootStructureChanged,
      Runnable expandApplicationRootPath,
      Supplier<TargetRef> selectedTargetRef,
      Supplier<TargetRef> firstServerStatusRef,
      Consumer<TargetRef> selectTarget,
      Runnable selectDefaultPath) {
    this.snapshotExpandedTreePaths =
        Objects.requireNonNull(snapshotExpandedTreePaths, "snapshotExpandedTreePaths");
    this.restoreExpandedTreePaths =
        Objects.requireNonNull(restoreExpandedTreePaths, "restoreExpandedTreePaths");
    this.showApplicationRoot = Objects.requireNonNull(showApplicationRoot, "showApplicationRoot");
    this.isApplicationRootAttached =
        Objects.requireNonNull(isApplicationRootAttached, "isApplicationRootAttached");
    this.rootChildCount = Objects.requireNonNull(rootChildCount, "rootChildCount");
    this.attachApplicationRoot =
        Objects.requireNonNull(attachApplicationRoot, "attachApplicationRoot");
    this.detachApplicationRoot =
        Objects.requireNonNull(detachApplicationRoot, "detachApplicationRoot");
    this.rootStructureChanged =
        Objects.requireNonNull(rootStructureChanged, "rootStructureChanged");
    this.expandApplicationRootPath =
        Objects.requireNonNull(expandApplicationRootPath, "expandApplicationRootPath");
    this.selectedTargetRef = Objects.requireNonNull(selectedTargetRef, "selectedTargetRef");
    this.firstServerStatusRef =
        Objects.requireNonNull(firstServerStatusRef, "firstServerStatusRef");
    this.selectTarget = Objects.requireNonNull(selectTarget, "selectTarget");
    this.selectDefaultPath = Objects.requireNonNull(selectDefaultPath, "selectDefaultPath");
  }

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
}
