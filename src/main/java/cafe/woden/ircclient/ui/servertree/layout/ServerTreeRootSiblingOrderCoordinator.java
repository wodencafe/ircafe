package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.model.TargetRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates per-server root sibling order state and runtime-config persistence. */
public final class ServerTreeRootSiblingOrderCoordinator {

  private final ServerTreePerServerNormalizedStore<ServerTreeRootSiblingOrder> byServer;

  public ServerTreeRootSiblingOrderCoordinator(ServerTreeLayoutConfigPort runtimeConfig) {
    byServer =
        new ServerTreePerServerNormalizedStore<>(
            runtimeConfig,
            ServerTreeRootSiblingOrder.defaults(),
            ServerTreeRootSiblingOrderCoordinator::normalizeOrder,
            new ServerTreePerServerNormalizedStore.Persistence<>() {
              @Override
              public Map<String, ServerTreeRootSiblingOrder> read(
                  ServerTreeLayoutConfigPort config) {
                return config.readServerTreeRootSiblingOrderByServer();
              }

              @Override
              public void write(
                  ServerTreeLayoutConfigPort config,
                  String serverId,
                  ServerTreeRootSiblingOrder value) {
                config.rememberServerTreeRootSiblingOrder(serverId, value);
              }
            });
  }

  public ServerTreeRootSiblingOrder orderForServer(String serverId) {
    return byServer.valueForServer(serverId);
  }

  public void rememberOrder(String serverId, ServerTreeRootSiblingOrder order) {
    byServer.remember(serverId, order);
  }

  public ServerTreeRootSiblingNode nodeKindForNode(
      DefaultMutableTreeNode node,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Predicate<DefaultMutableTreeNode> isPrivateMessagesGroupNode,
      Function<DefaultMutableTreeNode, TargetRef> nodeRefExtractor) {
    if (node == null) return null;
    if (isOtherGroupNode != null && isOtherGroupNode.test(node)) {
      return ServerTreeRootSiblingNode.OTHER;
    }
    if (isPrivateMessagesGroupNode != null && isPrivateMessagesGroupNode.test(node)) {
      return ServerTreeRootSiblingNode.PRIVATE_MESSAGES;
    }
    if (nodeRefExtractor == null) return null;
    return nodeKindForRef(nodeRefExtractor.apply(node));
  }

  public static ServerTreeRootSiblingNode nodeKindForRef(TargetRef ref) {
    if (ref == null) return null;
    if (ref.isChannelList()) return ServerTreeRootSiblingNode.CHANNEL_LIST;
    if (ref.isNotifications()) return ServerTreeRootSiblingNode.NOTIFICATIONS;
    return null;
  }

  public static ServerTreeRootSiblingOrder normalizeOrder(ServerTreeRootSiblingOrder order) {
    List<ServerTreeRootSiblingNode> defaults = ServerTreeRootSiblingOrder.defaults().order();
    List<ServerTreeRootSiblingNode> raw =
        order == null || order.order() == null ? List.of() : order.order();

    ArrayList<ServerTreeRootSiblingNode> normalized = new ArrayList<>();
    for (ServerTreeRootSiblingNode node : raw) {
      if (node == null || normalized.contains(node)) continue;
      normalized.add(node);
    }
    for (ServerTreeRootSiblingNode node : defaults) {
      if (node == null || normalized.contains(node)) continue;
      normalized.add(node);
    }
    return new ServerTreeRootSiblingOrder(List.copyOf(normalized));
  }
}
