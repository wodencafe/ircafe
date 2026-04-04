package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
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

  public ServerTreeTooltipProvider(JTree tree, Context context) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.context = Objects.requireNonNull(context, "context");
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

    if (context.isIrcRootNode(node)) {
      return "Configured IRC servers and discovered bouncer networks.";
    }

    if (context.isApplicationRootNode(node)) {
      return "Application diagnostics buffers.";
    }

    String networksGroupBackendId = normalizeBackendId(context.backendIdForNetworksGroupNode(node));
    if (!networksGroupBackendId.isEmpty()) {
      return ServerTreeBouncerBackends.networksGroupTooltip(networksGroupBackendId);
    }

    if (context.isInterceptorsGroupNode(node)) {
      return "Interceptors for this server. Individual interceptor nodes show their own captured hit counts.";
    }
    if (context.isMonitorGroupNode(node)) {
      return "Monitored nick presence for this server (IRC MONITOR, with ISON fallback when unavailable).";
    }
    if (context.isOtherGroupNode(node)) {
      return "Built-in server utility nodes. Drag listed nodes in/out of this group to customize layout.";
    }

    Object uo = node.getUserObject();
    if (uo instanceof ServerTreeQuasselNetworkNodeData networkNodeData) {
      if (context.isQuasselEmptyStateNode(node) || networkNodeData.emptyState()) {
        return "No Quassel networks are configured yet. Right-click and choose Add Quassel Network…";
      }
      if (context.isQuasselNetworkNode(node)) {
        String sid = Objects.toString(networkNodeData.serverId(), "").trim();
        String token = Objects.toString(networkNodeData.networkToken(), "").trim();
        String tip = Objects.toString(context.quasselNetworkTooltip(sid, token), "").trim();
        if (!tip.isEmpty()) return tip;
        String state =
            Boolean.FALSE.equals(networkNodeData.enabled())
                ? "disabled"
                : Boolean.TRUE.equals(networkNodeData.connected())
                    ? "connected"
                    : Boolean.FALSE.equals(networkNodeData.connected())
                        ? "disconnected"
                        : "status unknown";
        return "Quassel network \""
            + networkNodeData.label()
            + "\" ("
            + state
            + ", token: "
            + token
            + ").";
      }
    }

    if (uo instanceof ServerTreeNodeData nd && nd.ref != null) {
      String nodeTip = tooltipForNodeData(nd);
      if (nodeTip != null) {
        return nodeTip;
      }
    }

    if (uo instanceof String serverId && context.isServerNode(node)) {
      String backendId = normalizeBackendId(context.backendIdForEphemeralServer(serverId));
      if (!backendId.isEmpty()) {
        return ephemeralServerTooltip(serverId, backendId);
      }
      return standardServerTooltip(serverId);
    }

    return null;
  }

  private String tooltipForNodeData(ServerTreeNodeData nodeData) {
    if (nodeData.ref.isChannel() && nodeData.hasDetachedWarning()) {
      return "Disconnected: " + nodeData.detachedWarning + " (click warning icon to clear).";
    }
    if (nodeData.ref.isApplicationUnhandledErrors()) {
      return "Uncaught JVM exceptions captured by IRCafe.";
    }
    if (nodeData.ref.isApplicationAssertjSwing()) {
      return "Diagnostic buffer for AssertJ Swing/watchdog output.";
    }
    if (nodeData.ref.isApplicationJhiccup()) {
      return "Diagnostic buffer for jHiccup latency output.";
    }
    if (nodeData.ref.isApplicationInboundDedup()) {
      return "Inbound duplicate message suppression diagnostics (msgid replay / resend telemetry).";
    }
    if (nodeData.ref.isApplicationPlugins()) {
      return "Declared external plugin jars discovered from the plugin directory.";
    }
    if (nodeData.ref.isApplicationJfr()) {
      return context.isApplicationJfrActive()
          ? "Runtime JFR diagnostics are active (status gauges + JFR event stream)."
          : "Runtime JFR diagnostics are disabled. Open the JFR view to enable.";
    }
    if (nodeData.ref.isApplicationSpring()) {
      return "Spring framework lifecycle and availability event feed.";
    }
    if (nodeData.ref.isApplicationTerminal()) {
      return "In-app terminal output mirrored from System.out/System.err.";
    }
    if (context.isBouncerControlStatusNode(nodeData)) {
      return "Bouncer Control connection (used to discover bouncer networks).";
    }
    if (nodeData.ref.isInterceptor()) {
      return "Custom interceptor rules, actions, and captured matches. Badge count shows stored hits for this interceptor.";
    }
    if (nodeData.ref.isWeechatFilters()) {
      return "WeeChat-style local filters for this server (rules, placeholders, and scope overrides).";
    }
    if (nodeData.ref.isIgnores()) {
      return "Manage hard and soft ignore rules for this server.";
    }
    return null;
  }

  private String ephemeralServerTooltip(String serverId, String backendId) {
    String backend = normalizeBackendId(backendId);
    ConnectionState state = context.connectionStateForServer(serverId);
    boolean desired = context.desiredOnlineForServer(serverId);
    String stateTip = "State: " + ServerTreeConnectionStateViewModel.stateLabel(state) + ".";
    String intentTip =
        " Intent: " + ServerTreeConnectionStateViewModel.desiredIntentLabel(desired) + ".";
    String queueTip = ServerTreeConnectionStateViewModel.intentQueueTip(state, desired);
    String diagnostics = context.connectionDiagnosticsTipForServer(serverId);

    String origin = Objects.toString(context.originByServerId(backend, serverId), "").trim();
    String display = context.serverDisplayName(serverId);
    boolean auto = !origin.isEmpty() && context.isAutoConnectEnabled(backend, origin, display);

    String tip = stateTip + intentTip;
    if (!queueTip.isBlank()) tip += " " + queueTip;
    if (!diagnostics.isBlank()) tip += diagnostics;
    tip += " " + ServerTreeBouncerBackends.ephemeralDiscoveryTooltip(backend);
    if (auto) tip += " Auto-connect enabled.";
    if (!origin.isEmpty()) tip += " Origin: " + origin + ".";
    if (display != null && !display.isBlank()) tip += " Network: " + display + ".";
    return tip;
  }

  private String standardServerTooltip(String serverId) {
    ConnectionState state = context.connectionStateForServer(serverId);
    boolean desired = context.desiredOnlineForServer(serverId);
    String queueTip = ServerTreeConnectionStateViewModel.intentQueueTip(state, desired);
    String diagnostics = context.connectionDiagnosticsTipForServer(serverId);
    String action = ServerTreeConnectionStateViewModel.actionHint(state);
    String backendDisplayName =
        Objects.toString(context.backendDisplayNameForServer(serverId), "").trim();
    String base =
        "State: "
            + ServerTreeConnectionStateViewModel.stateLabel(state)
            + ". Intent: "
            + ServerTreeConnectionStateViewModel.desiredIntentLabel(desired)
            + ".";
    if (!backendDisplayName.isEmpty()) {
      base += " Backend: " + backendDisplayName + ".";
    }
    if (!queueTip.isBlank() && !diagnostics.isBlank())
      return base + " " + queueTip + diagnostics + " " + action;
    if (!queueTip.isBlank()) return base + " " + queueTip + " " + action;
    if (!diagnostics.isBlank()) return base + diagnostics + " " + action;
    return base + " " + action;
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(java.util.Locale.ROOT);
  }
}
