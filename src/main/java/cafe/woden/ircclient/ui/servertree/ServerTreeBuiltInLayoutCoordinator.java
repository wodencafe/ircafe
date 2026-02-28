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

/** Coordinates per-server built-in node layout state and runtime-config persistence. */
final class ServerTreeBuiltInLayoutCoordinator {

  private final RuntimeConfigStore runtimeConfig;
  private final Map<String, RuntimeConfigStore.ServerTreeBuiltInLayout> byServer = new HashMap<>();

  ServerTreeBuiltInLayoutCoordinator(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
    loadPersisted();
  }

  RuntimeConfigStore.ServerTreeBuiltInLayout layoutForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return RuntimeConfigStore.ServerTreeBuiltInLayout.defaults();
    return byServer.getOrDefault(sid, RuntimeConfigStore.ServerTreeBuiltInLayout.defaults());
  }

  void rememberLayout(String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    RuntimeConfigStore.ServerTreeBuiltInLayout normalized =
        normalizeLayout(
            layout == null ? RuntimeConfigStore.ServerTreeBuiltInLayout.defaults() : layout);
    RuntimeConfigStore.ServerTreeBuiltInLayout defaults =
        RuntimeConfigStore.ServerTreeBuiltInLayout.defaults();
    if (normalized.equals(defaults)) {
      byServer.remove(sid);
    } else {
      byServer.put(sid, normalized);
    }
    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeBuiltInLayout(sid, normalized);
    }
  }

  RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKindForNode(
      DefaultMutableTreeNode node,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Function<DefaultMutableTreeNode, TargetRef> nodeRefExtractor) {
    if (node == null) return null;
    if (isMonitorGroupNode != null && isMonitorGroupNode.test(node)) {
      return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.MONITOR;
    }
    if (isInterceptorsGroupNode != null && isInterceptorsGroupNode.test(node)) {
      return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.INTERCEPTORS;
    }
    if (nodeRefExtractor == null) return null;
    return nodeKindForRef(nodeRefExtractor.apply(node));
  }

  static RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKindForRef(TargetRef ref) {
    if (ref == null) return null;
    if (ref.isStatus()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER;
    if (ref.isNotifications()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.NOTIFICATIONS;
    if (ref.isLogViewer()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.LOG_VIEWER;
    if (ref.isWeechatFilters()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.FILTERS;
    if (ref.isIgnores()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.IGNORES;
    return null;
  }

  static RuntimeConfigStore.ServerTreeBuiltInLayout normalizeLayout(
      RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    List<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> defaultOther =
        RuntimeConfigStore.ServerTreeBuiltInLayout.defaults().otherOrder();

    List<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> rawRoot =
        layout == null || layout.rootOrder() == null ? List.of() : layout.rootOrder();
    List<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> rawOther =
        layout == null || layout.otherOrder() == null ? List.of() : layout.otherOrder();

    java.util.EnumSet<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> seen =
        java.util.EnumSet.noneOf(RuntimeConfigStore.ServerTreeBuiltInLayoutNode.class);
    ArrayList<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> root = new ArrayList<>();
    for (RuntimeConfigStore.ServerTreeBuiltInLayoutNode node : rawRoot) {
      if (node == null || seen.contains(node)) continue;
      root.add(node);
      seen.add(node);
    }

    ArrayList<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> other = new ArrayList<>();
    for (RuntimeConfigStore.ServerTreeBuiltInLayoutNode node : rawOther) {
      if (node == null || seen.contains(node)) continue;
      other.add(node);
      seen.add(node);
    }

    for (RuntimeConfigStore.ServerTreeBuiltInLayoutNode node : defaultOther) {
      if (node == null || seen.contains(node)) continue;
      other.add(node);
      seen.add(node);
    }

    return new RuntimeConfigStore.ServerTreeBuiltInLayout(List.copyOf(root), List.copyOf(other));
  }

  private void loadPersisted() {
    if (runtimeConfig == null) return;
    try {
      Map<String, RuntimeConfigStore.ServerTreeBuiltInLayout> persisted =
          runtimeConfig.readServerTreeBuiltInLayoutByServer();
      if (persisted == null || persisted.isEmpty()) return;
      for (Map.Entry<String, RuntimeConfigStore.ServerTreeBuiltInLayout> entry :
          persisted.entrySet()) {
        String sid = normalizeServerId(entry.getKey());
        if (sid.isEmpty()) continue;
        RuntimeConfigStore.ServerTreeBuiltInLayout normalized = normalizeLayout(entry.getValue());
        if (normalized.equals(RuntimeConfigStore.ServerTreeBuiltInLayout.defaults())) {
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
