package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeHeaderControls;
import java.time.Instant;
import java.util.Objects;

/** Public-facing runtime metadata and header controls API used by the dockable. */
public final class ServerTreeRuntimeHeaderApi {
  private final ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater;
  private final ServerTreeHeaderControls headerControls;

  public ServerTreeRuntimeHeaderApi(
      ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater,
      ServerTreeHeaderControls headerControls) {
    this.serverRuntimeUiUpdater =
        Objects.requireNonNull(serverRuntimeUiUpdater, "serverRuntimeUiUpdater");
    this.headerControls = Objects.requireNonNull(headerControls, "headerControls");
  }

  public void setServerConnectionState(String serverId, ConnectionState state) {
    serverRuntimeUiUpdater.setServerConnectionState(serverId, state);
  }

  public void setServerDesiredOnline(String serverId, boolean desiredOnline) {
    serverRuntimeUiUpdater.setServerDesiredOnline(serverId, desiredOnline);
  }

  public void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    serverRuntimeUiUpdater.setServerConnectionDiagnostics(serverId, lastError, nextRetryEpochMs);
  }

  public void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    serverRuntimeUiUpdater.setServerConnectedIdentity(
        serverId, connectedHost, connectedPort, nick, at);
  }

  public void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    serverRuntimeUiUpdater.setServerIrcv3Capability(serverId, capability, subcommand, enabled);
  }

  public void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    serverRuntimeUiUpdater.setServerIsupportToken(serverId, tokenName, tokenValue);
  }

  public void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    serverRuntimeUiUpdater.setServerVersionDetails(
        serverId, serverName, serverVersion, userModes, channelModes);
  }

  public void setStatusText(String text) {
    headerControls.setStatusText(text);
  }

  public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
    headerControls.setConnectionControlsEnabled(connectEnabled, disconnectEnabled);
  }

  public void setConnectedUi(boolean connected) {
    setConnectionControlsEnabled(!connected, connected);
  }
}
