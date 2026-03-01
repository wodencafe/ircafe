package cafe.woden.ircclient.ui.servertree.resolver;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Resolves a parent node for ensure-node operations based on target and current layout. */
public final class ServerTreeEnsureNodeParentResolver {

  public record ParentNodes(
      DefaultMutableTreeNode serverNode,
      DefaultMutableTreeNode privateMessagesNode,
      DefaultMutableTreeNode otherNode,
      DefaultMutableTreeNode monitorNode,
      DefaultMutableTreeNode interceptorsNode) {}

  public DefaultMutableTreeNode resolveParent(
      TargetRef ref,
      ParentNodes nodes,
      RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInKind,
      RuntimeConfigStore.ServerTreeBuiltInLayout layout,
      Supplier<DefaultMutableTreeNode> ensureChannelListNode) {
    if (ref == null || nodes == null) return null;
    DefaultMutableTreeNode serverNode = nodes.serverNode();
    DefaultMutableTreeNode otherNode = nodes.otherNode();
    RuntimeConfigStore.ServerTreeBuiltInLayout safeLayout =
        layout == null ? RuntimeConfigStore.ServerTreeBuiltInLayout.defaults() : layout;

    if (ref.isStatus()
        || ref.isNotifications()
        || ref.isWeechatFilters()
        || ref.isIgnores()
        || ref.isLogViewer()) {
      return rootOrOther(serverNode, otherNode, builtInKind, safeLayout);
    }

    if (ref.isMonitorGroup()) {
      return nodes.monitorNode() != null ? nodes.monitorNode() : serverNode;
    }
    if (ref.isInterceptor()) {
      return nodes.interceptorsNode() != null ? nodes.interceptorsNode() : serverNode;
    }
    if (ref.isChannelList() || ref.isDccTransfers()) {
      return serverNode;
    }
    if (ref.isChannel()) {
      if (ensureChannelListNode != null) {
        DefaultMutableTreeNode channelListNode = ensureChannelListNode.get();
        if (channelListNode != null) return channelListNode;
      }
      return serverNode;
    }
    return nodes.privateMessagesNode();
  }

  private static DefaultMutableTreeNode rootOrOther(
      DefaultMutableTreeNode serverNode,
      DefaultMutableTreeNode otherNode,
      RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInKind,
      RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    if (serverNode == null) return null;
    if (builtInKind != null
        && otherNode != null
        && !Objects.requireNonNullElse(
                layout, RuntimeConfigStore.ServerTreeBuiltInLayout.defaults())
            .rootOrder()
            .contains(builtInKind)) {
      return otherNode;
    }
    return serverNode;
  }
}
