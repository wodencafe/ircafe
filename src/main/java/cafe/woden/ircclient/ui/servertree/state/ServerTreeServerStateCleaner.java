package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.tree.DefaultMutableTreeNode;

/** Cleans per-server transient and persisted tree state when a server root is removed. */
@org.springframework.stereotype.Component
public final class ServerTreeServerStateCleaner {

  public interface Context {
    InterceptorStore interceptorStore();

    void clearHoveredServer(String serverId);

    void removeServerRuntime(String serverId);

    void clearChannelState(String serverId);

    void clearPrivateMessageOnlineStates(String serverId);

    Map<TargetRef, DefaultMutableTreeNode> leaves();

    Set<DefaultMutableTreeNode> typingActivityNodes();
  }

  public static Context context(
      InterceptorStore interceptorStore,
      Consumer<String> clearHoveredServer,
      Consumer<String> removeServerRuntime,
      Consumer<String> clearChannelState,
      Consumer<String> clearPrivateMessageOnlineStates,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Set<DefaultMutableTreeNode> typingActivityNodes) {
    Objects.requireNonNull(clearHoveredServer, "clearHoveredServer");
    Objects.requireNonNull(removeServerRuntime, "removeServerRuntime");
    Objects.requireNonNull(clearChannelState, "clearChannelState");
    Objects.requireNonNull(clearPrivateMessageOnlineStates, "clearPrivateMessageOnlineStates");
    Objects.requireNonNull(leaves, "leaves");
    Objects.requireNonNull(typingActivityNodes, "typingActivityNodes");
    return new Context() {
      @Override
      public InterceptorStore interceptorStore() {
        return interceptorStore;
      }

      @Override
      public void clearHoveredServer(String serverId) {
        clearHoveredServer.accept(serverId);
      }

      @Override
      public void removeServerRuntime(String serverId) {
        removeServerRuntime.accept(serverId);
      }

      @Override
      public void clearChannelState(String serverId) {
        clearChannelState.accept(serverId);
      }

      @Override
      public void clearPrivateMessageOnlineStates(String serverId) {
        clearPrivateMessageOnlineStates.accept(serverId);
      }

      @Override
      public Map<TargetRef, DefaultMutableTreeNode> leaves() {
        return leaves;
      }

      @Override
      public Set<DefaultMutableTreeNode> typingActivityNodes() {
        return typingActivityNodes;
      }
    };
  }

  public void cleanupServerState(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    InterceptorStore interceptorStore = in.interceptorStore();
    if (interceptorStore != null) {
      try {
        interceptorStore.clearServerHits(sid);
      } catch (Exception ignored) {
      }
    }

    in.clearHoveredServer(sid);
    in.removeServerRuntime(sid);
    in.clearChannelState(sid);
    in.clearPrivateMessageOnlineStates(sid);
    in.leaves().entrySet().removeIf(entry -> Objects.equals(entry.getKey().serverId(), sid));
    in.typingActivityNodes()
        .removeIf(
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
