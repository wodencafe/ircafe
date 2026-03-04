package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates per-server root sibling order state and runtime-config persistence. */
public final class ServerTreeRootSiblingOrderCoordinator {

  private final ServerTreePerServerNormalizedStore<RuntimeConfigStore.ServerTreeRootSiblingOrder>
      byServer;

  public ServerTreeRootSiblingOrderCoordinator(RuntimeConfigStore runtimeConfig) {
    byServer =
        new ServerTreePerServerNormalizedStore<>(
            runtimeConfig,
            RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults(),
            ServerTreeRootSiblingOrderCoordinator::normalizeOrder,
            new ServerTreePerServerNormalizedStore.Persistence<>() {
              @Override
              public Map<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> read(
                  RuntimeConfigStore config) {
                return config.readServerTreeRootSiblingOrderByServer();
              }

              @Override
              public void write(
                  RuntimeConfigStore config,
                  String serverId,
                  RuntimeConfigStore.ServerTreeRootSiblingOrder value) {
                config.rememberServerTreeRootSiblingOrder(serverId, value);
              }
            });
  }

  public RuntimeConfigStore.ServerTreeRootSiblingOrder orderForServer(String serverId) {
    return byServer.valueForServer(serverId);
  }

  public void rememberOrder(String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
    byServer.remember(serverId, order);
  }

  public RuntimeConfigStore.ServerTreeRootSiblingNode nodeKindForNode(
      DefaultMutableTreeNode node,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Predicate<DefaultMutableTreeNode> isPrivateMessagesGroupNode,
      Function<DefaultMutableTreeNode, TargetRef> nodeRefExtractor) {
    if (node == null) return null;
    if (isOtherGroupNode != null && isOtherGroupNode.test(node)) {
      return RuntimeConfigStore.ServerTreeRootSiblingNode.OTHER;
    }
    if (isPrivateMessagesGroupNode != null && isPrivateMessagesGroupNode.test(node)) {
      return RuntimeConfigStore.ServerTreeRootSiblingNode.PRIVATE_MESSAGES;
    }
    if (nodeRefExtractor == null) return null;
    return nodeKindForRef(nodeRefExtractor.apply(node));
  }

  public static RuntimeConfigStore.ServerTreeRootSiblingNode nodeKindForRef(TargetRef ref) {
    if (ref == null) return null;
    if (ref.isChannelList()) return RuntimeConfigStore.ServerTreeRootSiblingNode.CHANNEL_LIST;
    if (ref.isNotifications()) return RuntimeConfigStore.ServerTreeRootSiblingNode.NOTIFICATIONS;
    return null;
  }

  public static RuntimeConfigStore.ServerTreeRootSiblingOrder normalizeOrder(
      RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
    List<RuntimeConfigStore.ServerTreeRootSiblingNode> defaults =
        RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults().order();
    List<RuntimeConfigStore.ServerTreeRootSiblingNode> raw =
        order == null || order.order() == null ? List.of() : order.order();

    ArrayList<RuntimeConfigStore.ServerTreeRootSiblingNode> normalized = new ArrayList<>();
    for (RuntimeConfigStore.ServerTreeRootSiblingNode node : raw) {
      if (node == null || normalized.contains(node)) continue;
      normalized.add(node);
    }
    for (RuntimeConfigStore.ServerTreeRootSiblingNode node : defaults) {
      if (node == null || normalized.contains(node)) continue;
      normalized.add(node);
    }
    return new RuntimeConfigStore.ServerTreeRootSiblingOrder(List.copyOf(normalized));
  }

}
