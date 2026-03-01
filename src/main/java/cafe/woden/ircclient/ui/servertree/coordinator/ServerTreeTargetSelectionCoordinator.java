package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;

/** Orchestrates selecting target/group nodes after ensuring they exist in the tree model. */
public final class ServerTreeTargetSelectionCoordinator {

  public interface Context {
    void ensureNode(TargetRef ref);

    DefaultMutableTreeNode monitorGroupNode(String serverId);

    DefaultMutableTreeNode interceptorsGroupNode(String serverId);

    boolean isGroupNodeSelectable(String serverId, DefaultMutableTreeNode node);

    DefaultMutableTreeNode leafNode(TargetRef ref);

    void selectNode(DefaultMutableTreeNode node);
  }

  private final Context context;

  public ServerTreeTargetSelectionCoordinator(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public void selectTarget(TargetRef ref) {
    if (ref == null) return;
    if (ref.isMonitorGroup()) {
      selectGroupTarget(ref, true);
      return;
    }
    if (ref.isInterceptorsGroup()) {
      selectGroupTarget(ref, false);
      return;
    }
    context.ensureNode(ref);
    DefaultMutableTreeNode node = context.leafNode(ref);
    if (node == null) return;
    context.selectNode(node);
  }

  private void selectGroupTarget(TargetRef ref, boolean monitor) {
    context.ensureNode(ref);
    String serverId = Objects.toString(ref.serverId(), "").trim();
    DefaultMutableTreeNode node =
        monitor ? context.monitorGroupNode(serverId) : context.interceptorsGroupNode(serverId);
    if (node == null || !context.isGroupNodeSelectable(serverId, node)) return;
    context.selectNode(node);
  }
}
