package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.TargetRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates per-server built-in node layout state and runtime-config persistence. */
public final class ServerTreeBuiltInLayoutCoordinator {

  private final ServerTreePerServerNormalizedStore<RuntimeConfigStore.ServerTreeBuiltInLayout>
      byServer;

  public ServerTreeBuiltInLayoutCoordinator(RuntimeConfigStore runtimeConfig) {
    byServer =
        new ServerTreePerServerNormalizedStore<>(
            runtimeConfig,
            RuntimeConfigStore.ServerTreeBuiltInLayout.defaults(),
            ServerTreeBuiltInLayoutCoordinator::normalizeLayout,
            new ServerTreePerServerNormalizedStore.Persistence<>() {
              @Override
              public Map<String, RuntimeConfigStore.ServerTreeBuiltInLayout> read(
                  RuntimeConfigStore config) {
                return config.readServerTreeBuiltInLayoutByServer();
              }

              @Override
              public void write(
                  RuntimeConfigStore config,
                  String serverId,
                  RuntimeConfigStore.ServerTreeBuiltInLayout value) {
                config.rememberServerTreeBuiltInLayout(serverId, value);
              }
            });
  }

  public RuntimeConfigStore.ServerTreeBuiltInLayout layoutForServer(String serverId) {
    return byServer.valueForServer(serverId);
  }

  public void rememberLayout(String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    byServer.remember(serverId, layout);
  }

  public RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKindForNode(
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

  public static RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKindForRef(TargetRef ref) {
    if (ref == null) return null;
    if (ref.isStatus()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER;
    if (ref.isNotifications()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.NOTIFICATIONS;
    if (ref.isLogViewer()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.LOG_VIEWER;
    if (ref.isWeechatFilters()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.FILTERS;
    if (ref.isIgnores()) return RuntimeConfigStore.ServerTreeBuiltInLayoutNode.IGNORES;
    return null;
  }

  public static RuntimeConfigStore.ServerTreeBuiltInLayout normalizeLayout(
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
}
