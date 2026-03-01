package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Adapter for {@link ServerTreeTooltipProvider.Context}. */
public final class ServerTreeTooltipProviderContextAdapter
    implements ServerTreeTooltipProvider.Context {

  @FunctionalInterface
  public interface IntPairFunction<T> {
    T apply(int first, int second);
  }

  @FunctionalInterface
  public interface BiStringPredicate {
    boolean test(String first, String second);
  }

  private final IntPairFunction<String> serverIdAt;
  private final Function<String, TreePath> serverPathForId;
  private final Predicate<DefaultMutableTreeNode> isIrcRootNode;
  private final Predicate<DefaultMutableTreeNode> isApplicationRootNode;
  private final Predicate<DefaultMutableTreeNode> isSojuNetworksGroupNode;
  private final Predicate<DefaultMutableTreeNode> isZncNetworksGroupNode;
  private final Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode;
  private final Predicate<DefaultMutableTreeNode> isMonitorGroupNode;
  private final Predicate<DefaultMutableTreeNode> isOtherGroupNode;
  private final Predicate<DefaultMutableTreeNode> isServerNode;
  private final Function<String, ConnectionState> connectionStateForServer;
  private final Function<String, Boolean> desiredOnlineForServer;
  private final Function<String, String> connectionDiagnosticsTipForServer;
  private final Predicate<String> isSojuEphemeralServer;
  private final Predicate<String> isZncEphemeralServer;
  private final Function<String, String> sojuOriginByServerId;
  private final Function<String, String> zncOriginByServerId;
  private final Function<String, String> serverDisplayName;
  private final BiStringPredicate isSojuAutoConnectEnabled;
  private final BiStringPredicate isZncAutoConnectEnabled;
  private final Supplier<Boolean> isApplicationJfrActive;
  private final Predicate<ServerTreeNodeData> isBouncerControlStatusNode;

  public ServerTreeTooltipProviderContextAdapter(
      IntPairFunction<String> serverIdAt,
      Function<String, TreePath> serverPathForId,
      Predicate<DefaultMutableTreeNode> isIrcRootNode,
      Predicate<DefaultMutableTreeNode> isApplicationRootNode,
      Predicate<DefaultMutableTreeNode> isSojuNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isZncNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Predicate<DefaultMutableTreeNode> isServerNode,
      Function<String, ConnectionState> connectionStateForServer,
      Function<String, Boolean> desiredOnlineForServer,
      Function<String, String> connectionDiagnosticsTipForServer,
      Predicate<String> isSojuEphemeralServer,
      Predicate<String> isZncEphemeralServer,
      Function<String, String> sojuOriginByServerId,
      Function<String, String> zncOriginByServerId,
      Function<String, String> serverDisplayName,
      BiStringPredicate isSojuAutoConnectEnabled,
      BiStringPredicate isZncAutoConnectEnabled,
      Supplier<Boolean> isApplicationJfrActive,
      Predicate<ServerTreeNodeData> isBouncerControlStatusNode) {
    this.serverIdAt = Objects.requireNonNull(serverIdAt, "serverIdAt");
    this.serverPathForId = Objects.requireNonNull(serverPathForId, "serverPathForId");
    this.isIrcRootNode = Objects.requireNonNull(isIrcRootNode, "isIrcRootNode");
    this.isApplicationRootNode =
        Objects.requireNonNull(isApplicationRootNode, "isApplicationRootNode");
    this.isSojuNetworksGroupNode =
        Objects.requireNonNull(isSojuNetworksGroupNode, "isSojuNetworksGroupNode");
    this.isZncNetworksGroupNode =
        Objects.requireNonNull(isZncNetworksGroupNode, "isZncNetworksGroupNode");
    this.isInterceptorsGroupNode =
        Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    this.isMonitorGroupNode = Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    this.isOtherGroupNode = Objects.requireNonNull(isOtherGroupNode, "isOtherGroupNode");
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
    this.connectionStateForServer =
        Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    this.desiredOnlineForServer =
        Objects.requireNonNull(desiredOnlineForServer, "desiredOnlineForServer");
    this.connectionDiagnosticsTipForServer =
        Objects.requireNonNull(
            connectionDiagnosticsTipForServer, "connectionDiagnosticsTipForServer");
    this.isSojuEphemeralServer =
        Objects.requireNonNull(isSojuEphemeralServer, "isSojuEphemeralServer");
    this.isZncEphemeralServer =
        Objects.requireNonNull(isZncEphemeralServer, "isZncEphemeralServer");
    this.sojuOriginByServerId =
        Objects.requireNonNull(sojuOriginByServerId, "sojuOriginByServerId");
    this.zncOriginByServerId = Objects.requireNonNull(zncOriginByServerId, "zncOriginByServerId");
    this.serverDisplayName = Objects.requireNonNull(serverDisplayName, "serverDisplayName");
    this.isSojuAutoConnectEnabled =
        Objects.requireNonNull(isSojuAutoConnectEnabled, "isSojuAutoConnectEnabled");
    this.isZncAutoConnectEnabled =
        Objects.requireNonNull(isZncAutoConnectEnabled, "isZncAutoConnectEnabled");
    this.isApplicationJfrActive =
        Objects.requireNonNull(isApplicationJfrActive, "isApplicationJfrActive");
    this.isBouncerControlStatusNode =
        Objects.requireNonNull(isBouncerControlStatusNode, "isBouncerControlStatusNode");
  }

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
  public String sojuOriginByServerId(String serverId) {
    return sojuOriginByServerId.apply(serverId);
  }

  @Override
  public String zncOriginByServerId(String serverId) {
    return zncOriginByServerId.apply(serverId);
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
  public boolean isApplicationJfrActive() {
    return isApplicationJfrActive.get();
  }

  @Override
  public boolean isBouncerControlStatusNode(ServerTreeNodeData nodeData) {
    return isBouncerControlStatusNode.test(nodeData);
  }
}
