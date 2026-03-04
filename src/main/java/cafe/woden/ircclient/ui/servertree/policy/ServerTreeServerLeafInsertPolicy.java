package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Shared insert-index policy for fixed-position built-in leaves under a server root. */
public final class ServerTreeServerLeafInsertPolicy {

  private ServerTreeServerLeafInsertPolicy() {}

  public static int fixedServerLeafInsertIndexFor(
      ServerNodes serverNodes,
      TargetRef ref,
      Function<TargetRef, DefaultMutableTreeNode> leafForTarget) {
    if (serverNodes == null || serverNodes.serverNode == null || ref == null) return 0;

    if (ref.equals(serverNodes.channelListRef)) {
      return 0;
    }

    if (ref.equals(serverNodes.dccTransfersRef)) {
      DefaultMutableTreeNode channelListNode =
          leafForTarget == null ? null : leafForTarget.apply(serverNodes.channelListRef);
      int idx = 0;
      if (channelListNode != null && channelListNode.getParent() == serverNodes.serverNode) {
        int channelIdx = serverNodes.serverNode.getIndex(channelListNode);
        if (channelIdx >= 0) idx = channelIdx + 1;
      }
      return Math.max(0, Math.min(idx, serverNodes.serverNode.getChildCount()));
    }

    return serverNodes.serverNode.getChildCount();
  }
}
