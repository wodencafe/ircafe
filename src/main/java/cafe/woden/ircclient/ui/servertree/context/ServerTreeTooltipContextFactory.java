package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.ui.servertree.ServerTreeUiHooks;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Factory for composing {@link ServerTreeTooltipProvider.Context} from dockable collaborators. */
public final class ServerTreeTooltipContextFactory {

  private ServerTreeTooltipContextFactory() {}

  public record Inputs(
      ServerTreeTooltipProviderContextAdapter.IntPairFunction<String> serverIdAt,
      ServerTreeUiHooks uiHooks,
      Predicate<DefaultMutableTreeNode> isIrcRootNode,
      Predicate<DefaultMutableTreeNode> isApplicationRootNode,
      Predicate<DefaultMutableTreeNode> isSojuNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isZncNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Function<String, Boolean> desiredOnlineForServer,
      Function<String, String> connectionDiagnosticsTipForServer,
      Predicate<String> isSojuEphemeralServer,
      Predicate<String> isZncEphemeralServer,
      Function<String, String> sojuOriginByServerId,
      Function<String, String> zncOriginByServerId,
      Function<String, String> serverDisplayName,
      ServerTreeTooltipProviderContextAdapter.BiStringPredicate isSojuAutoConnectEnabled,
      ServerTreeTooltipProviderContextAdapter.BiStringPredicate isZncAutoConnectEnabled,
      Supplier<Boolean> isApplicationJfrActive,
      Predicate<ServerTreeNodeData> isBouncerControlStatusNode) {}

  public static ServerTreeTooltipProvider.Context create(Inputs input) {
    Inputs in = Objects.requireNonNull(input, "input");
    ServerTreeUiHooks uiHooks = Objects.requireNonNull(in.uiHooks(), "uiHooks");

    return new ServerTreeTooltipProviderContextAdapter(
        Objects.requireNonNull(in.serverIdAt(), "serverIdAt"),
        uiHooks::serverPathForId,
        Objects.requireNonNull(in.isIrcRootNode(), "isIrcRootNode"),
        Objects.requireNonNull(in.isApplicationRootNode(), "isApplicationRootNode"),
        Objects.requireNonNull(in.isSojuNetworksGroupNode(), "isSojuNetworksGroupNode"),
        Objects.requireNonNull(in.isZncNetworksGroupNode(), "isZncNetworksGroupNode"),
        Objects.requireNonNull(in.isInterceptorsGroupNode(), "isInterceptorsGroupNode"),
        Objects.requireNonNull(in.isMonitorGroupNode(), "isMonitorGroupNode"),
        Objects.requireNonNull(in.isOtherGroupNode(), "isOtherGroupNode"),
        uiHooks::isServerNode,
        uiHooks::connectionStateForServer,
        Objects.requireNonNull(in.desiredOnlineForServer(), "desiredOnlineForServer"),
        Objects.requireNonNull(
            in.connectionDiagnosticsTipForServer(), "connectionDiagnosticsTipForServer"),
        Objects.requireNonNull(in.isSojuEphemeralServer(), "isSojuEphemeralServer"),
        Objects.requireNonNull(in.isZncEphemeralServer(), "isZncEphemeralServer"),
        Objects.requireNonNull(in.sojuOriginByServerId(), "sojuOriginByServerId"),
        Objects.requireNonNull(in.zncOriginByServerId(), "zncOriginByServerId"),
        Objects.requireNonNull(in.serverDisplayName(), "serverDisplayName"),
        Objects.requireNonNull(in.isSojuAutoConnectEnabled(), "isSojuAutoConnectEnabled"),
        Objects.requireNonNull(in.isZncAutoConnectEnabled(), "isZncAutoConnectEnabled"),
        Objects.requireNonNull(in.isApplicationJfrActive(), "isApplicationJfrActive"),
        Objects.requireNonNull(in.isBouncerControlStatusNode(), "isBouncerControlStatusNode"));
  }
}
