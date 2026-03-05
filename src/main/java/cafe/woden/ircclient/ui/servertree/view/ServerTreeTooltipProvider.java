package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.awt.event.MouseEvent;
import java.util.Objects;
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

  public interface Context {
    String serverIdAt(int x, int y);

    TreePath serverPathForId(String serverId);

    boolean isIrcRootNode(DefaultMutableTreeNode node);

    boolean isApplicationRootNode(DefaultMutableTreeNode node);

    boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node);

    boolean isZncNetworksGroupNode(DefaultMutableTreeNode node);

    boolean isGenericNetworksGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isOtherGroupNode(DefaultMutableTreeNode node);

    boolean isServerNode(DefaultMutableTreeNode node);

    ConnectionState connectionStateForServer(String serverId);

    boolean desiredOnlineForServer(String serverId);

    String connectionDiagnosticsTipForServer(String serverId);

    boolean isSojuEphemeralServer(String serverId);

    boolean isZncEphemeralServer(String serverId);

    boolean isGenericEphemeralServer(String serverId);

    String sojuOriginByServerId(String serverId);

    String zncOriginByServerId(String serverId);

    String genericOriginByServerId(String serverId);

    String serverDisplayName(String serverId);

    boolean isSojuAutoConnectEnabled(String originId, String networkKey);

    boolean isZncAutoConnectEnabled(String originId, String networkKey);

    boolean isGenericAutoConnectEnabled(String originId, String networkKey);

    boolean isApplicationJfrActive();

    boolean isBouncerControlStatusNode(ServerTreeNodeData nodeData);
  }

  public static Context context(
      IntPairFunction<String> serverIdAt,
      Function<String, TreePath> serverPathForId,
      Predicate<DefaultMutableTreeNode> isIrcRootNode,
      Predicate<DefaultMutableTreeNode> isApplicationRootNode,
      Predicate<DefaultMutableTreeNode> isSojuNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isZncNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isGenericNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Predicate<DefaultMutableTreeNode> isServerNode,
      Function<String, ConnectionState> connectionStateForServer,
      Function<String, Boolean> desiredOnlineForServer,
      Function<String, String> connectionDiagnosticsTipForServer,
      Predicate<String> isSojuEphemeralServer,
      Predicate<String> isZncEphemeralServer,
      Predicate<String> isGenericEphemeralServer,
      Function<String, String> sojuOriginByServerId,
      Function<String, String> zncOriginByServerId,
      Function<String, String> genericOriginByServerId,
      Function<String, String> serverDisplayName,
      java.util.function.BiPredicate<String, String> isSojuAutoConnectEnabled,
      java.util.function.BiPredicate<String, String> isZncAutoConnectEnabled,
      java.util.function.BiPredicate<String, String> isGenericAutoConnectEnabled,
      Supplier<Boolean> isApplicationJfrActive,
      Predicate<ServerTreeNodeData> isBouncerControlStatusNode) {
    Objects.requireNonNull(serverIdAt, "serverIdAt");
    Objects.requireNonNull(serverPathForId, "serverPathForId");
    Objects.requireNonNull(isIrcRootNode, "isIrcRootNode");
    Objects.requireNonNull(isApplicationRootNode, "isApplicationRootNode");
    Objects.requireNonNull(isSojuNetworksGroupNode, "isSojuNetworksGroupNode");
    Objects.requireNonNull(isZncNetworksGroupNode, "isZncNetworksGroupNode");
    Objects.requireNonNull(isGenericNetworksGroupNode, "isGenericNetworksGroupNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    Objects.requireNonNull(isOtherGroupNode, "isOtherGroupNode");
    Objects.requireNonNull(isServerNode, "isServerNode");
    Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    Objects.requireNonNull(desiredOnlineForServer, "desiredOnlineForServer");
    Objects.requireNonNull(connectionDiagnosticsTipForServer, "connectionDiagnosticsTipForServer");
    Objects.requireNonNull(isSojuEphemeralServer, "isSojuEphemeralServer");
    Objects.requireNonNull(isZncEphemeralServer, "isZncEphemeralServer");
    Objects.requireNonNull(isGenericEphemeralServer, "isGenericEphemeralServer");
    Objects.requireNonNull(sojuOriginByServerId, "sojuOriginByServerId");
    Objects.requireNonNull(zncOriginByServerId, "zncOriginByServerId");
    Objects.requireNonNull(genericOriginByServerId, "genericOriginByServerId");
    Objects.requireNonNull(serverDisplayName, "serverDisplayName");
    Objects.requireNonNull(isSojuAutoConnectEnabled, "isSojuAutoConnectEnabled");
    Objects.requireNonNull(isZncAutoConnectEnabled, "isZncAutoConnectEnabled");
    Objects.requireNonNull(isGenericAutoConnectEnabled, "isGenericAutoConnectEnabled");
    Objects.requireNonNull(isApplicationJfrActive, "isApplicationJfrActive");
    Objects.requireNonNull(isBouncerControlStatusNode, "isBouncerControlStatusNode");
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
      public boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node) {
        return isSojuNetworksGroupNode.test(node);
      }

      @Override
      public boolean isZncNetworksGroupNode(DefaultMutableTreeNode node) {
        return isZncNetworksGroupNode.test(node);
      }

      @Override
      public boolean isGenericNetworksGroupNode(DefaultMutableTreeNode node) {
        return isGenericNetworksGroupNode.test(node);
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
      public boolean isSojuEphemeralServer(String serverId) {
        return isSojuEphemeralServer.test(serverId);
      }

      @Override
      public boolean isZncEphemeralServer(String serverId) {
        return isZncEphemeralServer.test(serverId);
      }

      @Override
      public boolean isGenericEphemeralServer(String serverId) {
        return isGenericEphemeralServer.test(serverId);
      }

      @Override
      public String sojuOriginByServerId(String serverId) {
        return sojuOriginByServerId.apply(serverId);
      }

      @Override
      public String zncOriginByServerId(String serverId) {
        return zncOriginByServerId.apply(serverId);
      }

      @Override
      public String genericOriginByServerId(String serverId) {
        return genericOriginByServerId.apply(serverId);
      }

      @Override
      public String serverDisplayName(String serverId) {
        return serverDisplayName.apply(serverId);
      }

      @Override
      public boolean isSojuAutoConnectEnabled(String originId, String networkKey) {
        return isSojuAutoConnectEnabled.test(originId, networkKey);
      }

      @Override
      public boolean isZncAutoConnectEnabled(String originId, String networkKey) {
        return isZncAutoConnectEnabled.test(originId, networkKey);
      }

      @Override
      public boolean isGenericAutoConnectEnabled(String originId, String networkKey) {
        return isGenericAutoConnectEnabled.test(originId, networkKey);
      }

      @Override
      public boolean isApplicationJfrActive() {
        return isApplicationJfrActive.get();
      }

      @Override
      public boolean isBouncerControlStatusNode(ServerTreeNodeData nodeData) {
        return isBouncerControlStatusNode.test(nodeData);
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

    if (context.isSojuNetworksGroupNode(node)) {
      return "Soju networks discovered from the bouncer (not saved).";
    }

    if (context.isZncNetworksGroupNode(node)) {
      return "ZNC networks discovered from the bouncer (not saved).";
    }

    if (context.isGenericNetworksGroupNode(node)) {
      return "Bouncer networks discovered from generic protocol lines (not saved).";
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
      return ephemeralServerTooltip(serverId, "soju");
    }

    if (uo instanceof String serverId
        && context.isServerNode(node)
        && context.isZncEphemeralServer(serverId)) {
      return ephemeralServerTooltip(serverId, "znc");
    }

    if (uo instanceof String serverId
        && context.isServerNode(node)
        && context.isGenericEphemeralServer(serverId)) {
      return ephemeralServerTooltip(serverId, "generic");
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

  private String ephemeralServerTooltip(String serverId, String backendId) {
    String backend = Objects.toString(backendId, "").trim().toLowerCase(java.util.Locale.ROOT);
    ConnectionState state = context.connectionStateForServer(serverId);
    boolean desired = context.desiredOnlineForServer(serverId);
    String stateTip = "State: " + ServerTreeConnectionStateViewModel.stateLabel(state) + ".";
    String intentTip =
        " Intent: " + ServerTreeConnectionStateViewModel.desiredIntentLabel(desired) + ".";
    String queueTip = ServerTreeConnectionStateViewModel.intentQueueTip(state, desired);
    String diagnostics = context.connectionDiagnosticsTipForServer(serverId);

    String origin =
        Objects.toString(
                "soju".equals(backend)
                    ? context.sojuOriginByServerId(serverId)
                    : "znc".equals(backend)
                        ? context.zncOriginByServerId(serverId)
                        : context.genericOriginByServerId(serverId),
                "")
            .trim();
    String display = context.serverDisplayName(serverId);
    boolean auto =
        !origin.isEmpty()
            && ("soju".equals(backend)
                ? context.isSojuAutoConnectEnabled(origin, display)
                : "znc".equals(backend)
                    ? context.isZncAutoConnectEnabled(origin, display)
                    : context.isGenericAutoConnectEnabled(origin, display));

    String tip = stateTip + intentTip;
    if (!queueTip.isBlank()) tip += " " + queueTip;
    if (!diagnostics.isBlank()) tip += diagnostics;
    if ("soju".equals(backend)) {
      tip += " Discovered from soju; not saved.";
    } else if ("znc".equals(backend)) {
      tip += " Discovered from ZNC; not saved.";
    } else {
      tip += " Discovered from generic bouncer protocol; not saved.";
    }
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
