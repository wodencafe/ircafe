package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Stateless tooltip text policy for server-tree nodes once a concrete tree node is known. */
@Component
public final class ServerTreeTooltipTextPolicy {

  public String toolTipForNode(
      ServerTreeTooltipProvider.Context context, DefaultMutableTreeNode node) {
    Objects.requireNonNull(context, "context");
    if (node == null) return null;

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

    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeQuasselNetworkNodeData networkNodeData) {
      String networkTip = tooltipForQuasselNetwork(context, node, networkNodeData);
      if (networkTip != null) {
        return networkTip;
      }
    }

    if (userObject instanceof ServerTreeNodeData nodeData && nodeData.ref != null) {
      String nodeTip = tooltipForNodeData(context, nodeData);
      if (nodeTip != null) {
        return nodeTip;
      }
    }

    if (userObject instanceof String serverId && context.isServerNode(node)) {
      String backendId = normalizeBackendId(context.backendIdForEphemeralServer(serverId));
      if (!backendId.isEmpty()) {
        return ephemeralServerTooltip(context, serverId, backendId);
      }
      return standardServerTooltip(context, serverId);
    }

    return null;
  }

  private String tooltipForQuasselNetwork(
      ServerTreeTooltipProvider.Context context,
      DefaultMutableTreeNode node,
      ServerTreeQuasselNetworkNodeData networkNodeData) {
    if (context.isQuasselEmptyStateNode(node) || networkNodeData.emptyState()) {
      return "No Quassel networks are configured yet. Right-click and choose Add Quassel Network…";
    }
    if (!context.isQuasselNetworkNode(node)) {
      return null;
    }

    String serverId = Objects.toString(networkNodeData.serverId(), "").trim();
    String token = Objects.toString(networkNodeData.networkToken(), "").trim();
    String tip = Objects.toString(context.quasselNetworkTooltip(serverId, token), "").trim();
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

  private String tooltipForNodeData(
      ServerTreeTooltipProvider.Context context, ServerTreeNodeData nodeData) {
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

  private String ephemeralServerTooltip(
      ServerTreeTooltipProvider.Context context, String serverId, String backendId) {
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

  private String standardServerTooltip(ServerTreeTooltipProvider.Context context, String serverId) {
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
    if (!queueTip.isBlank() && !diagnostics.isBlank()) {
      return base + " " + queueTip + diagnostics + " " + action;
    }
    if (!queueTip.isBlank()) return base + " " + queueTip + " " + action;
    if (!diagnostics.isBlank()) return base + diagnostics + " " + action;
    return base + " " + action;
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(java.util.Locale.ROOT);
  }
}
