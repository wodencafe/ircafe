package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionPersistencePolicy;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeSelectionPersistencePolicy.Context}. */
public final class ServerTreeSelectionPersistenceContextAdapter
    implements ServerTreeSelectionPersistencePolicy.Context {

  private final Supplier<TargetRef> lastBroadcastSelection;
  private final Supplier<TargetRef> selectedTargetRef;
  private final Supplier<DefaultMutableTreeNode> selectedTreeNode;
  private final Function<DefaultMutableTreeNode, String> owningServerIdForNode;
  private final Predicate<DefaultMutableTreeNode> isMonitorGroupNode;
  private final Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode;

  public ServerTreeSelectionPersistenceContextAdapter(
      Supplier<TargetRef> lastBroadcastSelection,
      Supplier<TargetRef> selectedTargetRef,
      Supplier<DefaultMutableTreeNode> selectedTreeNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode) {
    this.lastBroadcastSelection =
        Objects.requireNonNull(lastBroadcastSelection, "lastBroadcastSelection");
    this.selectedTargetRef = Objects.requireNonNull(selectedTargetRef, "selectedTargetRef");
    this.selectedTreeNode = Objects.requireNonNull(selectedTreeNode, "selectedTreeNode");
    this.owningServerIdForNode =
        Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
    this.isMonitorGroupNode = Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    this.isInterceptorsGroupNode =
        Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
  }

  @Override
  public TargetRef lastBroadcastSelection() {
    return lastBroadcastSelection.get();
  }

  @Override
  public TargetRef selectedTargetRef() {
    return selectedTargetRef.get();
  }

  @Override
  public DefaultMutableTreeNode selectedTreeNode() {
    return selectedTreeNode.get();
  }

  @Override
  public String owningServerIdForNode(DefaultMutableTreeNode node) {
    return owningServerIdForNode.apply(node);
  }

  @Override
  public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
    return isMonitorGroupNode.test(node);
  }

  @Override
  public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
    return isInterceptorsGroupNode.test(node);
  }
}
