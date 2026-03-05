package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;

/** Cleans per-server transient and persisted tree state when a server root is removed. */
public final class ServerTreeServerStateCleaner {

  public interface Context {
    void clearPrivateMessageOnlineStates(String serverId);
  }

  private final InterceptorStore interceptorStore;
  private final ServerTreeServerActionOverlay serverActionOverlay;
  private final ServerTreeRuntimeState runtimeState;
  private final ServerTreeChannelStateStore channelStateStore;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Set<DefaultMutableTreeNode> typingActivityNodes;
  private final Context context;

  public ServerTreeServerStateCleaner(
      InterceptorStore interceptorStore,
      ServerTreeServerActionOverlay serverActionOverlay,
      ServerTreeRuntimeState runtimeState,
      ServerTreeChannelStateStore channelStateStore,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Set<DefaultMutableTreeNode> typingActivityNodes,
      Context context) {
    this.interceptorStore = interceptorStore;
    this.serverActionOverlay = Objects.requireNonNull(serverActionOverlay, "serverActionOverlay");
    this.runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
    this.channelStateStore = Objects.requireNonNull(channelStateStore, "channelStateStore");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.typingActivityNodes = Objects.requireNonNull(typingActivityNodes, "typingActivityNodes");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void cleanupServerState(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    if (interceptorStore != null) {
      try {
        interceptorStore.clearServerHits(sid);
      } catch (Exception ignored) {
      }
    }

    serverActionOverlay.clearHoveredServer(sid);
    runtimeState.removeServer(sid);
    channelStateStore.clearServer(sid);
    context.clearPrivateMessageOnlineStates(sid);
    leaves.entrySet().removeIf(entry -> Objects.equals(entry.getKey().serverId(), sid));
    typingActivityNodes.removeIf(
        node -> {
          if (node == null || node.getParent() == null) return true;
          Object userObject = node.getUserObject();
          if (!(userObject instanceof ServerTreeNodeData nodeData) || nodeData.ref == null) {
            return false;
          }
          return Objects.equals(nodeData.ref.serverId(), sid);
        });
  }
}
