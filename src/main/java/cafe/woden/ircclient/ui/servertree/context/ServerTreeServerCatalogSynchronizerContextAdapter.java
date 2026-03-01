package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerCatalogSynchronizer;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Adapter for {@link ServerTreeServerCatalogSynchronizer.Context}. */
public final class ServerTreeServerCatalogSynchronizerContextAdapter
    implements ServerTreeServerCatalogSynchronizer.Context {

  private final JTree tree;
  private final Map<String, ServerNodes> servers;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final DefaultTreeModel model;
  private final DefaultMutableTreeNode root;
  private final BooleanSupplier startupSelectionCompleted;
  private final Runnable markStartupSelectionCompleted;
  private final Supplier<TargetRef> selectedTargetRef;
  private final Consumer<String> addServerRoot;
  private final Consumer<String> removeServerRoot;
  private final BiSetConsumer updateBouncerControlLabels;
  private final Supplier<Set<TreePath>> snapshotExpandedTreePaths;
  private final Consumer<Set<TreePath>> restoreExpandedTreePaths;
  private final BooleanSupplier hasValidTreeSelection;
  private final Consumer<TargetRef> selectTarget;
  private final Supplier<String> firstServerId;
  private final Consumer<String> selectStartupDefaultForServer;
  private final Supplier<TreePath> defaultSelectionPath;

  @FunctionalInterface
  public interface BiSetConsumer {
    void accept(Set<String> first, Set<String> second);
  }

  public ServerTreeServerCatalogSynchronizerContextAdapter(
      JTree tree,
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      BooleanSupplier startupSelectionCompleted,
      Runnable markStartupSelectionCompleted,
      Supplier<TargetRef> selectedTargetRef,
      Consumer<String> addServerRoot,
      Consumer<String> removeServerRoot,
      BiSetConsumer updateBouncerControlLabels,
      Supplier<Set<TreePath>> snapshotExpandedTreePaths,
      Consumer<Set<TreePath>> restoreExpandedTreePaths,
      BooleanSupplier hasValidTreeSelection,
      Consumer<TargetRef> selectTarget,
      Supplier<String> firstServerId,
      Consumer<String> selectStartupDefaultForServer,
      Supplier<TreePath> defaultSelectionPath) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.servers = Objects.requireNonNull(servers, "servers");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.model = Objects.requireNonNull(model, "model");
    this.root = Objects.requireNonNull(root, "root");
    this.startupSelectionCompleted =
        Objects.requireNonNull(startupSelectionCompleted, "startupSelectionCompleted");
    this.markStartupSelectionCompleted =
        Objects.requireNonNull(markStartupSelectionCompleted, "markStartupSelectionCompleted");
    this.selectedTargetRef = Objects.requireNonNull(selectedTargetRef, "selectedTargetRef");
    this.addServerRoot = Objects.requireNonNull(addServerRoot, "addServerRoot");
    this.removeServerRoot = Objects.requireNonNull(removeServerRoot, "removeServerRoot");
    this.updateBouncerControlLabels =
        Objects.requireNonNull(updateBouncerControlLabels, "updateBouncerControlLabels");
    this.snapshotExpandedTreePaths =
        Objects.requireNonNull(snapshotExpandedTreePaths, "snapshotExpandedTreePaths");
    this.restoreExpandedTreePaths =
        Objects.requireNonNull(restoreExpandedTreePaths, "restoreExpandedTreePaths");
    this.hasValidTreeSelection =
        Objects.requireNonNull(hasValidTreeSelection, "hasValidTreeSelection");
    this.selectTarget = Objects.requireNonNull(selectTarget, "selectTarget");
    this.firstServerId = Objects.requireNonNull(firstServerId, "firstServerId");
    this.selectStartupDefaultForServer =
        Objects.requireNonNull(selectStartupDefaultForServer, "selectStartupDefaultForServer");
    this.defaultSelectionPath =
        Objects.requireNonNull(defaultSelectionPath, "defaultSelectionPath");
  }

  @Override
  public boolean treeHasSelectionPath() {
    return tree.getSelectionPath() != null;
  }

  @Override
  public void markStartupSelectionCompleted() {
    markStartupSelectionCompleted.run();
  }

  @Override
  public boolean startupSelectionCompleted() {
    return startupSelectionCompleted.getAsBoolean();
  }

  @Override
  public TargetRef selectedTargetRef() {
    return selectedTargetRef.get();
  }

  @Override
  public boolean hasServer(String serverId) {
    return servers.containsKey(serverId);
  }

  @Override
  public Set<String> currentServerIds() {
    return servers.keySet();
  }

  @Override
  public void addServerRoot(String serverId) {
    addServerRoot.accept(serverId);
  }

  @Override
  public void removeServerRoot(String serverId) {
    removeServerRoot.accept(serverId);
  }

  @Override
  public void updateBouncerControlLabels(
      Set<String> nextSojuBouncerControl, Set<String> nextZncBouncerControl) {
    updateBouncerControlLabels.accept(nextSojuBouncerControl, nextZncBouncerControl);
  }

  @Override
  public void nodeChangedForServer(String serverId) {
    ServerNodes serverNodes = servers.get(serverId);
    if (serverNodes != null) {
      model.nodeChanged(serverNodes.serverNode);
    }
  }

  @Override
  public Set<TreePath> snapshotExpandedTreePaths() {
    return snapshotExpandedTreePaths.get();
  }

  @Override
  public void reloadTreeModel() {
    model.reload(root);
  }

  @Override
  public void restoreExpandedTreePaths(Set<TreePath> expanded) {
    restoreExpandedTreePaths.accept(expanded);
  }

  @Override
  public void runLater(Runnable task) {
    SwingUtilities.invokeLater(task);
  }

  @Override
  public boolean hasValidTreeSelection() {
    return hasValidTreeSelection.getAsBoolean();
  }

  @Override
  public boolean hasLeaf(TargetRef ref) {
    return leaves.containsKey(ref);
  }

  @Override
  public void selectTarget(TargetRef ref) {
    selectTarget.accept(ref);
  }

  @Override
  public String firstServerId() {
    return firstServerId.get();
  }

  @Override
  public void selectStartupDefaultForServer(String serverId) {
    selectStartupDefaultForServer.accept(serverId);
  }

  @Override
  public void selectDefaultPath() {
    tree.setSelectionPath(defaultSelectionPath.get());
  }
}
