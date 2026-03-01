package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

/** Applies server runtime metadata updates and keeps tree node UI in sync. */
public final class ServerTreeServerRuntimeUiUpdater {

  private final ServerTreeRuntimeState runtimeState;
  private final Map<String, ServerNodes> servers;
  private final DefaultTreeModel model;
  private final ServerTreeServerActionOverlay serverActionOverlay;
  private final JTree tree;

  public ServerTreeServerRuntimeUiUpdater(
      ServerTreeRuntimeState runtimeState,
      Map<String, ServerNodes> servers,
      DefaultTreeModel model,
      ServerTreeServerActionOverlay serverActionOverlay,
      JTree tree) {
    this.runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
    this.servers = Objects.requireNonNull(servers, "servers");
    this.model = Objects.requireNonNull(model, "model");
    this.serverActionOverlay = Objects.requireNonNull(serverActionOverlay, "serverActionOverlay");
    this.tree = Objects.requireNonNull(tree, "tree");
  }

  public void setServerConnectionState(String serverId, ConnectionState state) {
    if (!runtimeState.setServerConnectionState(serverId, state)) return;
    refreshServerNode(serverId);
    repaintHoveredServer(serverId);
  }

  public void setServerDesiredOnline(String serverId, boolean desiredOnline) {
    String sid = normalizeServerId(serverId);
    if (!runtimeState.setServerDesiredOnline(sid, desiredOnline)) return;
    refreshServerNode(sid);
    repaintHoveredServer(sid);
  }

  public void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    String sid = normalizeServerId(serverId);
    if (!runtimeState.setServerConnectionDiagnostics(sid, lastError, nextRetryEpochMs)) return;
    refreshServerNode(sid);
    tree.repaint();
  }

  public void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    String sid = normalizeServerId(serverId);
    if (!runtimeState.setServerConnectedIdentity(sid, connectedHost, connectedPort, nick, at)) {
      return;
    }
    refreshServerNode(sid);
  }

  public void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    String sid = normalizeServerId(serverId);
    if (!runtimeState.setServerIrcv3Capability(sid, capability, subcommand, enabled)) return;
    refreshServerNode(sid);
  }

  public void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    String sid = normalizeServerId(serverId);
    if (!runtimeState.setServerIsupportToken(sid, tokenName, tokenValue)) return;
    refreshServerNode(sid);
  }

  public void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    String sid = normalizeServerId(serverId);
    if (!runtimeState.setServerVersionDetails(
        sid, serverName, serverVersion, userModes, channelModes)) {
      return;
    }
    refreshServerNode(sid);
  }

  private void refreshServerNode(String serverId) {
    ServerNodes serverNodes = servers.get(serverId);
    if (serverNodes == null || serverNodes.serverNode == null) return;
    model.nodeChanged(serverNodes.serverNode);
  }

  private void repaintHoveredServer(String serverId) {
    if (!serverActionOverlay.isHoveredServer(serverId)) return;
    tree.repaint();
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
