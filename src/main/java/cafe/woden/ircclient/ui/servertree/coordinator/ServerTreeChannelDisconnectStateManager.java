package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Objects;
import java.util.Set;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;

/** Manages detached/disconnected channel node state and warning lifecycle. */
public final class ServerTreeChannelDisconnectStateManager {

  public interface Context {
    void ensureNode(TargetRef ref);

    DefaultMutableTreeNode leafNode(TargetRef ref);

    void nodeChanged(DefaultMutableTreeNode node);

    void emitManagedChannelsChanged(String serverId);
  }

  private final Set<DefaultMutableTreeNode> typingActivityNodes;
  private final Timer typingActivityTimer;
  private final Context context;

  public ServerTreeChannelDisconnectStateManager(
      Set<DefaultMutableTreeNode> typingActivityNodes, Timer typingActivityTimer, Context context) {
    this.typingActivityNodes = Objects.requireNonNull(typingActivityNodes, "typingActivityNodes");
    this.typingActivityTimer = Objects.requireNonNull(typingActivityTimer, "typingActivityTimer");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void setChannelDisconnected(TargetRef ref, boolean detached, String warningReason) {
    if (ref == null || !ref.isChannel()) return;
    context.ensureNode(ref);
    DefaultMutableTreeNode node = context.leafNode(ref);
    if (node == null) return;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return;

    String normalizedReason = warningReason == null ? null : warningReason.trim();
    String nextWarning;
    if (!detached) {
      nextWarning = "";
    } else if (normalizedReason != null) {
      nextWarning = normalizedReason;
    } else if (!nodeData.detached) {
      nextWarning = "";
    } else {
      nextWarning = Objects.toString(nodeData.detachedWarning, "");
    }

    boolean detachedChanged = nodeData.detached != detached;
    boolean warningChanged =
        !Objects.equals(Objects.toString(nodeData.detachedWarning, ""), nextWarning);
    if (!detachedChanged && !warningChanged) return;

    nodeData.detached = detached;
    nodeData.detachedWarning = nextWarning;
    if (detached) {
      nodeData.clearTypingActivityNow();
      typingActivityNodes.remove(node);
      if (typingActivityNodes.isEmpty()) {
        typingActivityTimer.stop();
      }
    }
    context.nodeChanged(node);
    context.emitManagedChannelsChanged(ref.serverId());
  }

  public void clearChannelDisconnectedWarning(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return;
    DefaultMutableTreeNode node = context.leafNode(ref);
    if (node == null) return;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData) || !nodeData.hasDetachedWarning()) {
      return;
    }
    setChannelDisconnected(ref, true, "");
  }

  public boolean isChannelDisconnected(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return false;
    DefaultMutableTreeNode node = context.leafNode(ref);
    if (node == null) return false;
    Object userObject = node.getUserObject();
    return userObject instanceof ServerTreeNodeData nodeData && nodeData.detached;
  }
}
