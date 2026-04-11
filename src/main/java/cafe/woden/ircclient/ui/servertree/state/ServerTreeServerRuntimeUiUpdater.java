package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Applies server runtime metadata updates and keeps tree node UI in sync. */
@org.springframework.stereotype.Component
public final class ServerTreeServerRuntimeUiUpdater {

  public interface Context {
    ServerTreeRuntimeState runtimeState();

    Map<String, ServerNodes> servers();

    void nodeChanged(DefaultMutableTreeNode node);

    boolean isHoveredServer(String serverId);

    void repaintTree();
  }

  public static Context context(
      ServerTreeRuntimeState runtimeState,
      Map<String, ServerNodes> servers,
      Consumer<DefaultMutableTreeNode> nodeChanged,
      Predicate<String> isHoveredServer,
      Runnable repaintTree) {
    Objects.requireNonNull(runtimeState, "runtimeState");
    Objects.requireNonNull(servers, "servers");
    Objects.requireNonNull(nodeChanged, "nodeChanged");
    Objects.requireNonNull(isHoveredServer, "isHoveredServer");
    Objects.requireNonNull(repaintTree, "repaintTree");
    return new Context() {
      @Override
      public ServerTreeRuntimeState runtimeState() {
        return runtimeState;
      }

      @Override
      public Map<String, ServerNodes> servers() {
        return servers;
      }

      @Override
      public void nodeChanged(DefaultMutableTreeNode node) {
        nodeChanged.accept(node);
      }

      @Override
      public boolean isHoveredServer(String serverId) {
        return isHoveredServer.test(serverId);
      }

      @Override
      public void repaintTree() {
        repaintTree.run();
      }
    };
  }

  public void setServerConnectionState(Context context, String serverId, ConnectionState state) {
    Context in = Objects.requireNonNull(context, "context");
    if (!in.runtimeState().setServerConnectionState(serverId, state)) return;
    refreshServerNode(in, serverId);
    repaintHoveredServer(in, serverId);
  }

  public void setServerDesiredOnline(Context context, String serverId, boolean desiredOnline) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalizeServerId(serverId);
    if (!in.runtimeState().setServerDesiredOnline(sid, desiredOnline)) return;
    refreshServerNode(in, sid);
    repaintHoveredServer(in, sid);
  }

  public void setServerConnectionDiagnostics(
      Context context, String serverId, String lastError, Long nextRetryEpochMs) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalizeServerId(serverId);
    if (!in.runtimeState().setServerConnectionDiagnostics(sid, lastError, nextRetryEpochMs)) return;
    refreshServerNode(in, sid);
    in.repaintTree();
  }

  public void setServerConnectedIdentity(
      Context context,
      String serverId,
      String connectedHost,
      int connectedPort,
      String nick,
      Instant at) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalizeServerId(serverId);
    if (!in.runtimeState()
        .setServerConnectedIdentity(sid, connectedHost, connectedPort, nick, at)) {
      return;
    }
    refreshServerNode(in, sid);
  }

  public void setServerIrcv3Capability(
      Context context, String serverId, String capability, String subcommand, boolean enabled) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalizeServerId(serverId);
    if (!in.runtimeState().setServerIrcv3Capability(sid, capability, subcommand, enabled)) return;
    refreshServerNode(in, sid);
  }

  public void setServerIsupportToken(
      Context context, String serverId, String tokenName, String tokenValue) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalizeServerId(serverId);
    if (!in.runtimeState().setServerIsupportToken(sid, tokenName, tokenValue)) return;
    refreshServerNode(in, sid);
  }

  public void setServerVersionDetails(
      Context context,
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalizeServerId(serverId);
    if (!in.runtimeState()
        .setServerVersionDetails(sid, serverName, serverVersion, userModes, channelModes)) {
      return;
    }
    refreshServerNode(in, sid);
  }

  private void refreshServerNode(Context context, String serverId) {
    ServerNodes serverNodes = context.servers().get(serverId);
    if (serverNodes == null || serverNodes.serverNode == null) return;
    context.nodeChanged(serverNodes.serverNode);
  }

  private void repaintHoveredServer(Context context, String serverId) {
    if (!context.isHoveredServer(serverId)) return;
    context.repaintTree();
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
