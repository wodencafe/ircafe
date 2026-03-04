package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Resolves the current best-effort selection used for persistence. */
public final class ServerTreeSelectionPersistencePolicy {

  public interface Context {
    TargetRef lastBroadcastSelection();

    TargetRef selectedTargetRef();

    DefaultMutableTreeNode selectedTreeNode();

    String owningServerIdForNode(DefaultMutableTreeNode node);

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);
  }

  public static Context context(
      Supplier<TargetRef> lastBroadcastSelection,
      Supplier<TargetRef> selectedTargetRef,
      Supplier<DefaultMutableTreeNode> selectedTreeNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode) {
    Objects.requireNonNull(lastBroadcastSelection, "lastBroadcastSelection");
    Objects.requireNonNull(selectedTargetRef, "selectedTargetRef");
    Objects.requireNonNull(selectedTreeNode, "selectedTreeNode");
    Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
    Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    return new Context() {
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
    };
  }

  private final Context context;

  public ServerTreeSelectionPersistencePolicy(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public TargetRef selectedTargetForPersistence() {
    TargetRef emitted = context.lastBroadcastSelection();
    if (emitted != null) return emitted;

    TargetRef selected = context.selectedTargetRef();
    if (selected != null) return selected;

    DefaultMutableTreeNode node = context.selectedTreeNode();
    if (node == null) return null;

    String serverId = Objects.toString(context.owningServerIdForNode(node), "").trim();
    if (serverId.isBlank()) return null;

    if (context.isMonitorGroupNode(node)) {
      return TargetRef.monitorGroup(serverId);
    }
    if (context.isInterceptorsGroupNode(node)) {
      return TargetRef.interceptorsGroup(serverId);
    }
    return null;
  }
}
