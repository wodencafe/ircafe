package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates per-server root sibling order state and runtime-config persistence. */
final class ServerTreeRootSiblingOrderCoordinator {

  private final RuntimeConfigStore runtimeConfig;
  private final Map<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> byServer =
      new HashMap<>();

  ServerTreeRootSiblingOrderCoordinator(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
    loadPersisted();
  }

  RuntimeConfigStore.ServerTreeRootSiblingOrder orderForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults();
    return byServer.getOrDefault(sid, RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults());
  }

  void rememberOrder(String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    RuntimeConfigStore.ServerTreeRootSiblingOrder normalized =
        normalizeOrder(
            order == null ? RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults() : order);
    RuntimeConfigStore.ServerTreeRootSiblingOrder defaults =
        RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults();
    if (normalized.equals(defaults)) {
      byServer.remove(sid);
    } else {
      byServer.put(sid, normalized);
    }
    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeRootSiblingOrder(sid, normalized);
    }
  }

  RuntimeConfigStore.ServerTreeRootSiblingNode nodeKindForNode(
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

  static RuntimeConfigStore.ServerTreeRootSiblingNode nodeKindForRef(TargetRef ref) {
    if (ref == null) return null;
    if (ref.isChannelList()) return RuntimeConfigStore.ServerTreeRootSiblingNode.CHANNEL_LIST;
    if (ref.isNotifications()) return RuntimeConfigStore.ServerTreeRootSiblingNode.NOTIFICATIONS;
    return null;
  }

  static RuntimeConfigStore.ServerTreeRootSiblingOrder normalizeOrder(
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

  private void loadPersisted() {
    if (runtimeConfig == null) return;
    try {
      Map<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> persisted =
          runtimeConfig.readServerTreeRootSiblingOrderByServer();
      if (persisted == null || persisted.isEmpty()) return;
      for (Map.Entry<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> entry :
          persisted.entrySet()) {
        String sid = normalizeServerId(entry.getKey());
        if (sid.isEmpty()) continue;
        RuntimeConfigStore.ServerTreeRootSiblingOrder normalized = normalizeOrder(entry.getValue());
        if (normalized.equals(RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults())) {
          byServer.remove(sid);
        } else {
          byServer.put(sid, normalized);
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
