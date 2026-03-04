package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeApplicationRootVisibilityCoordinatorContextTest {

  @Test
  void contextDelegatesApplicationRootOperations() {
    TreePath path = new TreePath(new Object[] {"root"});
    AtomicReference<Set<TreePath>> restoredPaths = new AtomicReference<>(Set.of());
    AtomicBoolean showApplicationRoot = new AtomicBoolean(true);
    AtomicBoolean attached = new AtomicBoolean(false);
    AtomicInteger childCount = new AtomicInteger(2);
    AtomicInteger attachedIndex = new AtomicInteger(-1);
    AtomicBoolean detached = new AtomicBoolean(false);
    AtomicBoolean structureChanged = new AtomicBoolean(false);
    AtomicBoolean expanded = new AtomicBoolean(false);
    TargetRef selected = new TargetRef("libera", "app:settings");
    TargetRef firstStatus = new TargetRef("libera", "status");
    AtomicReference<TargetRef> selectedTarget = new AtomicReference<>();
    AtomicBoolean defaultPathSelected = new AtomicBoolean(false);

    ServerTreeApplicationRootVisibilityCoordinator.Context context =
        ServerTreeApplicationRootVisibilityCoordinator.context(
            () -> Set.of(path),
            restoredPaths::set,
            showApplicationRoot::get,
            attached::get,
            childCount::get,
            attachedIndex::set,
            () -> detached.set(true),
            () -> structureChanged.set(true),
            () -> expanded.set(true),
            () -> selected,
            () -> firstStatus,
            selectedTarget::set,
            () -> defaultPathSelected.set(true));

    assertEquals(Set.of(path), context.snapshotExpandedTreePaths());
    context.restoreExpandedTreePaths(Set.of(path));
    assertEquals(Set.of(path), restoredPaths.get());

    assertTrue(context.showApplicationRoot());
    assertFalse(context.isApplicationRootAttached());
    assertEquals(2, context.rootChildCount());
    context.attachApplicationRoot(1);
    assertEquals(1, attachedIndex.get());

    context.detachApplicationRoot();
    context.rootStructureChanged();
    context.expandApplicationRootPath();
    assertTrue(detached.get());
    assertTrue(structureChanged.get());
    assertTrue(expanded.get());

    assertSame(selected, context.selectedTargetRef());
    assertSame(firstStatus, context.firstServerStatusRef());
    context.selectTarget(firstStatus);
    assertSame(firstStatus, selectedTarget.get());
    context.selectDefaultPath();
    assertTrue(defaultPathSelected.get());
  }
}
