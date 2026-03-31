package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.model.TargetRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates per-server built-in node layout state and runtime-config persistence. */
public final class ServerTreeBuiltInLayoutCoordinator {

  private final ServerTreePerServerNormalizedStore<ServerTreeBuiltInLayout> byServer;

  public ServerTreeBuiltInLayoutCoordinator(ServerTreeLayoutConfigPort runtimeConfig) {
    byServer =
        new ServerTreePerServerNormalizedStore<>(
            runtimeConfig,
            ServerTreeBuiltInLayout.defaults(),
            ServerTreeBuiltInLayoutCoordinator::normalizeLayout,
            new ServerTreePerServerNormalizedStore.Persistence<>() {
              @Override
              public Map<String, ServerTreeBuiltInLayout> read(ServerTreeLayoutConfigPort config) {
                return config.readServerTreeBuiltInLayoutByServer();
              }

              @Override
              public void write(
                  ServerTreeLayoutConfigPort config,
                  String serverId,
                  ServerTreeBuiltInLayout value) {
                config.rememberServerTreeBuiltInLayout(serverId, value);
              }
            });
  }

  public ServerTreeBuiltInLayout layoutForServer(String serverId) {
    return byServer.valueForServer(serverId);
  }

  public void rememberLayout(String serverId, ServerTreeBuiltInLayout layout) {
    byServer.remember(serverId, layout);
  }

  public ServerTreeBuiltInLayoutNode nodeKindForNode(
      DefaultMutableTreeNode node,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Function<DefaultMutableTreeNode, TargetRef> nodeRefExtractor) {
    if (node == null) return null;
    if (isMonitorGroupNode != null && isMonitorGroupNode.test(node)) {
      return ServerTreeBuiltInLayoutNode.MONITOR;
    }
    if (isInterceptorsGroupNode != null && isInterceptorsGroupNode.test(node)) {
      return ServerTreeBuiltInLayoutNode.INTERCEPTORS;
    }
    if (nodeRefExtractor == null) return null;
    return nodeKindForRef(nodeRefExtractor.apply(node));
  }

  public static ServerTreeBuiltInLayoutNode nodeKindForRef(TargetRef ref) {
    if (ref == null) return null;
    if (ref.isStatus()) return ServerTreeBuiltInLayoutNode.SERVER;
    if (ref.isNotifications()) return ServerTreeBuiltInLayoutNode.NOTIFICATIONS;
    if (ref.isLogViewer()) return ServerTreeBuiltInLayoutNode.LOG_VIEWER;
    if (ref.isWeechatFilters()) return ServerTreeBuiltInLayoutNode.FILTERS;
    if (ref.isIgnores()) return ServerTreeBuiltInLayoutNode.IGNORES;
    return null;
  }

  public static ServerTreeBuiltInLayout normalizeLayout(ServerTreeBuiltInLayout layout) {
    List<ServerTreeBuiltInLayoutNode> defaultOther =
        ServerTreeBuiltInLayout.defaults().otherOrder();

    List<ServerTreeBuiltInLayoutNode> rawRoot =
        layout == null || layout.rootOrder() == null ? List.of() : layout.rootOrder();
    List<ServerTreeBuiltInLayoutNode> rawOther =
        layout == null || layout.otherOrder() == null ? List.of() : layout.otherOrder();

    java.util.EnumSet<ServerTreeBuiltInLayoutNode> seen =
        java.util.EnumSet.noneOf(ServerTreeBuiltInLayoutNode.class);
    ArrayList<ServerTreeBuiltInLayoutNode> root = new ArrayList<>();
    for (ServerTreeBuiltInLayoutNode node : rawRoot) {
      if (node == null || seen.contains(node)) continue;
      root.add(node);
      seen.add(node);
    }

    ArrayList<ServerTreeBuiltInLayoutNode> other = new ArrayList<>();
    for (ServerTreeBuiltInLayoutNode node : rawOther) {
      if (node == null || seen.contains(node)) continue;
      other.add(node);
      seen.add(node);
    }

    for (ServerTreeBuiltInLayoutNode node : defaultOther) {
      if (node == null || seen.contains(node)) continue;
      other.add(node);
      seen.add(node);
    }

    return new ServerTreeBuiltInLayout(List.copyOf(root), List.copyOf(other));
  }
}
