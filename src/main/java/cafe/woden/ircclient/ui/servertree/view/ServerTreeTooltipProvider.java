package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Resolves tree tooltips for server tree nodes. */
public final class ServerTreeTooltipProvider {

  public interface Context {
    String serverIdAt(int x, int y);

    TreePath serverPathForId(String serverId);

    boolean isIrcRootNode(DefaultMutableTreeNode node);

    boolean isApplicationRootNode(DefaultMutableTreeNode node);

    boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node);

    boolean isZncNetworksGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isOtherGroupNode(DefaultMutableTreeNode node);

    boolean isServerNode(DefaultMutableTreeNode node);

    ConnectionState connectionStateForServer(String serverId);

    boolean desiredOnlineForServer(String serverId);

    String connectionDiagnosticsTipForServer(String serverId);

    boolean isSojuEphemeralServer(String serverId);

    boolean isZncEphemeralServer(String serverId);

    String sojuOriginByServerId(String serverId);

    String zncOriginByServerId(String serverId);

    String serverDisplayName(String serverId);

    boolean isSojuAutoConnectEnabled(String originId, String networkKey);

    boolean isZncAutoConnectEnabled(String originId, String networkKey);

    boolean isApplicationJfrActive();

    boolean isBouncerControlStatusNode(ServerTreeNodeData nodeData);
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

    if (context.isSojuNetworksGroupNode(node)) {
      return "Soju networks discovered from the bouncer (not saved).";
    }

    if (context.isZncNetworksGroupNode(node)) {
      return "ZNC networks discovered from the bouncer (not saved).";
    }

    if (context.isInterceptorsGroupNode(node)) {
      return "Interceptors for this server. Count shows total captured hits.";
    }
    if (context.isMonitorGroupNode(node)) {
      return "Monitored nick presence for this server (IRC MONITOR, with ISON fallback when unavailable).";
    }
    if (context.isOtherGroupNode(node)) {
      return "Built-in server utility nodes. Drag listed nodes in/out of this group to customize layout.";
    }

    Object uo = node.getUserObject();
    if (uo instanceof ServerTreeNodeData nd && nd.ref != null) {
      String nodeTip = tooltipForNodeData(nd);
      if (nodeTip != null) {
        return nodeTip;
      }
    }

    if (uo instanceof String serverId
        && context.isServerNode(node)
        && context.isSojuEphemeralServer(serverId)) {
      return ephemeralServerTooltip(serverId, true);
    }

    if (uo instanceof String serverId
        && context.isServerNode(node)
        && context.isZncEphemeralServer(serverId)) {
      return ephemeralServerTooltip(serverId, false);
    }

    if (uo instanceof String serverId && context.isServerNode(node)) {
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
      return "Custom interceptor rules, actions, and captured matches. Scope can be this server or any server.";
    }
    if (nodeData.ref.isWeechatFilters()) {
      return "WeeChat-style local filters for this server (rules, placeholders, and scope overrides).";
    }
    if (nodeData.ref.isIgnores()) {
      return "Manage hard and soft ignore rules for this server.";
    }
    return null;
  }

  private String ephemeralServerTooltip(String serverId, boolean soju) {
    ConnectionState state = context.connectionStateForServer(serverId);
    boolean desired = context.desiredOnlineForServer(serverId);
    String stateTip = "State: " + ServerTreeConnectionStateViewModel.stateLabel(state) + ".";
    String intentTip =
        " Intent: " + ServerTreeConnectionStateViewModel.desiredIntentLabel(desired) + ".";
    String queueTip = ServerTreeConnectionStateViewModel.intentQueueTip(state, desired);
    String diagnostics = context.connectionDiagnosticsTipForServer(serverId);

    String origin =
        Objects.toString(
                soju
                    ? context.sojuOriginByServerId(serverId)
                    : context.zncOriginByServerId(serverId),
                "")
            .trim();
    String display = context.serverDisplayName(serverId);
    boolean auto =
        !origin.isEmpty()
            && (soju
                ? context.isSojuAutoConnectEnabled(origin, display)
                : context.isZncAutoConnectEnabled(origin, display));

    String tip = stateTip + intentTip;
    if (!queueTip.isBlank()) tip += " " + queueTip;
    if (!diagnostics.isBlank()) tip += diagnostics;
    tip += soju ? " Discovered from soju; not saved." : " Discovered from ZNC; not saved.";
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
    String base =
        "State: "
            + ServerTreeConnectionStateViewModel.stateLabel(state)
            + ". Intent: "
            + ServerTreeConnectionStateViewModel.desiredIntentLabel(desired)
            + ".";
    if (!queueTip.isBlank() && !diagnostics.isBlank())
      return base + " " + queueTip + diagnostics + " " + action;
    if (!queueTip.isBlank()) return base + " " + queueTip + " " + action;
    if (!diagnostics.isBlank()) return base + diagnostics + " " + action;
    return base + " " + action;
  }
}
