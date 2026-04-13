package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Resolves tree tooltips for server tree nodes. */
public final class ServerTreeTooltipProvider {

  @FunctionalInterface
  public interface IntPairFunction<T> {
    T apply(int first, int second);
  }

  @FunctionalInterface
  public interface TriPredicate<A, B, C> {
    boolean test(A first, B second, C third);
  }

  public interface Context {
    String serverIdAt(int x, int y);

    TreePath serverPathForId(String serverId);

    boolean isIrcRootNode(DefaultMutableTreeNode node);

    boolean isApplicationRootNode(DefaultMutableTreeNode node);

    String backendIdForNetworksGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isOtherGroupNode(DefaultMutableTreeNode node);

    boolean isServerNode(DefaultMutableTreeNode node);

    boolean isQuasselNetworkNode(DefaultMutableTreeNode node);

    boolean isQuasselEmptyStateNode(DefaultMutableTreeNode node);

    ConnectionState connectionStateForServer(String serverId);

    boolean desiredOnlineForServer(String serverId);

    String connectionDiagnosticsTipForServer(String serverId);

    String backendDisplayNameForServer(String serverId);

    String backendIdForEphemeralServer(String serverId);

    String originByServerId(String backendId, String serverId);

    String serverDisplayName(String serverId);

    boolean isAutoConnectEnabled(String backendId, String originId, String networkKey);

    boolean isApplicationJfrActive();

    boolean isBouncerControlStatusNode(ServerTreeNodeData nodeData);

    String quasselNetworkTooltip(String serverId, String networkToken);
  }

  public static Context context(
      IntPairFunction<String> serverIdAt,
      Function<String, TreePath> serverPathForId,
      Predicate<DefaultMutableTreeNode> isIrcRootNode,
      Predicate<DefaultMutableTreeNode> isApplicationRootNode,
      Function<DefaultMutableTreeNode, String> backendIdForNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isQuasselNetworkNode,
      Predicate<DefaultMutableTreeNode> isQuasselEmptyStateNode,
      Function<String, ConnectionState> connectionStateForServer,
      Function<String, Boolean> desiredOnlineForServer,
      Function<String, String> connectionDiagnosticsTipForServer,
      Function<String, String> backendDisplayNameForServer,
      Function<String, String> backendIdForEphemeralServer,
      BiFunction<String, String, String> originByServerId,
      Function<String, String> serverDisplayName,
      TriPredicate<String, String, String> isAutoConnectEnabled,
      Supplier<Boolean> isApplicationJfrActive,
      Predicate<ServerTreeNodeData> isBouncerControlStatusNode,
      BiFunction<String, String, String> quasselNetworkTooltip) {
    Objects.requireNonNull(serverIdAt, "serverIdAt");
    Objects.requireNonNull(serverPathForId, "serverPathForId");
    Objects.requireNonNull(isIrcRootNode, "isIrcRootNode");
    Objects.requireNonNull(isApplicationRootNode, "isApplicationRootNode");
    Objects.requireNonNull(backendIdForNetworksGroupNode, "backendIdForNetworksGroupNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    Objects.requireNonNull(isOtherGroupNode, "isOtherGroupNode");
    Objects.requireNonNull(isServerNode, "isServerNode");
    Objects.requireNonNull(isQuasselNetworkNode, "isQuasselNetworkNode");
    Objects.requireNonNull(isQuasselEmptyStateNode, "isQuasselEmptyStateNode");
    Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    Objects.requireNonNull(desiredOnlineForServer, "desiredOnlineForServer");
    Objects.requireNonNull(connectionDiagnosticsTipForServer, "connectionDiagnosticsTipForServer");
    Objects.requireNonNull(backendDisplayNameForServer, "backendDisplayNameForServer");
    Objects.requireNonNull(backendIdForEphemeralServer, "backendIdForEphemeralServer");
    Objects.requireNonNull(originByServerId, "originByServerId");
    Objects.requireNonNull(serverDisplayName, "serverDisplayName");
    Objects.requireNonNull(isAutoConnectEnabled, "isAutoConnectEnabled");
    Objects.requireNonNull(isApplicationJfrActive, "isApplicationJfrActive");
    Objects.requireNonNull(isBouncerControlStatusNode, "isBouncerControlStatusNode");
    Objects.requireNonNull(quasselNetworkTooltip, "quasselNetworkTooltip");
    return new Context() {
      @Override
      public String serverIdAt(int x, int y) {
        return serverIdAt.apply(x, y);
      }

      @Override
      public TreePath serverPathForId(String serverId) {
        return serverPathForId.apply(serverId);
      }

      @Override
      public boolean isIrcRootNode(DefaultMutableTreeNode node) {
        return isIrcRootNode.test(node);
      }

      @Override
      public boolean isApplicationRootNode(DefaultMutableTreeNode node) {
        return isApplicationRootNode.test(node);
      }

      @Override
      public String backendIdForNetworksGroupNode(DefaultMutableTreeNode node) {
        return backendIdForNetworksGroupNode.apply(node);
      }

      @Override
      public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
        return isInterceptorsGroupNode.test(node);
      }

      @Override
      public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
        return isMonitorGroupNode.test(node);
      }

      @Override
      public boolean isOtherGroupNode(DefaultMutableTreeNode node) {
        return isOtherGroupNode.test(node);
      }

      @Override
      public boolean isServerNode(DefaultMutableTreeNode node) {
        return isServerNode.test(node);
      }

      @Override
      public boolean isQuasselNetworkNode(DefaultMutableTreeNode node) {
        return isQuasselNetworkNode.test(node);
      }

      @Override
      public boolean isQuasselEmptyStateNode(DefaultMutableTreeNode node) {
        return isQuasselEmptyStateNode.test(node);
      }

      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return connectionStateForServer.apply(serverId);
      }

      @Override
      public boolean desiredOnlineForServer(String serverId) {
        return desiredOnlineForServer.apply(serverId);
      }

      @Override
      public String connectionDiagnosticsTipForServer(String serverId) {
        return connectionDiagnosticsTipForServer.apply(serverId);
      }

      @Override
      public String backendDisplayNameForServer(String serverId) {
        return backendDisplayNameForServer.apply(serverId);
      }

      @Override
      public String backendIdForEphemeralServer(String serverId) {
        return backendIdForEphemeralServer.apply(serverId);
      }

      @Override
      public String originByServerId(String backendId, String serverId) {
        return originByServerId.apply(backendId, serverId);
      }

      @Override
      public String serverDisplayName(String serverId) {
        return serverDisplayName.apply(serverId);
      }

      @Override
      public boolean isAutoConnectEnabled(String backendId, String originId, String networkKey) {
        return isAutoConnectEnabled.test(backendId, originId, networkKey);
      }

      @Override
      public boolean isApplicationJfrActive() {
        return isApplicationJfrActive.get();
      }

      @Override
      public boolean isBouncerControlStatusNode(ServerTreeNodeData nodeData) {
        return isBouncerControlStatusNode.test(nodeData);
      }

      @Override
      public String quasselNetworkTooltip(String serverId, String networkToken) {
        return quasselNetworkTooltip.apply(serverId, networkToken);
      }
    };
  }

  private final JTree tree;
  private final Context context;
  private final ServerTreeTooltipTextPolicy tooltipTextPolicy;

  public ServerTreeTooltipProvider(
      JTree tree, Context context, ServerTreeTooltipTextPolicy tooltipTextPolicy) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.context = Objects.requireNonNull(context, "context");
    this.tooltipTextPolicy = Objects.requireNonNull(tooltipTextPolicy, "tooltipTextPolicy");
  }

  public String toolTipForEvent(MouseEvent event) {
    if (event == null) return null;

    TreePath path = tree.getPathForLocation(event.getX(), event.getY());
    if (path == null) {
      String sid = context.serverIdAt(event.getX(), event.getY());
      if (!sid.isEmpty()) {
        path = context.serverPathForId(sid);
      }
    }
    if (path == null) return null;

    Object comp = path.getLastPathComponent();
    if (!(comp instanceof DefaultMutableTreeNode node)) return null;

    return tooltipTextPolicy.toolTipForNode(context, node);
  }
}
