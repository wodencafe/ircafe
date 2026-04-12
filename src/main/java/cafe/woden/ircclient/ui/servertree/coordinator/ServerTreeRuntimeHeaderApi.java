package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Public-facing runtime metadata and header controls API used by the dockable. */
@Component
public final class ServerTreeRuntimeHeaderApi {
  public interface Context {
    ServerTreeServerRuntimeUiUpdater.Context serverRuntimeUiUpdaterContext();

    void setStatusText(String text);

    void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled);
  }

  public static Context context(
      ServerTreeServerRuntimeUiUpdater.Context serverRuntimeUiUpdaterContext,
      Consumer<String> setStatusText,
      BiConsumer<Boolean, Boolean> setConnectionControlsEnabled) {
    Objects.requireNonNull(serverRuntimeUiUpdaterContext, "serverRuntimeUiUpdaterContext");
    Objects.requireNonNull(setStatusText, "setStatusText");
    Objects.requireNonNull(setConnectionControlsEnabled, "setConnectionControlsEnabled");
    return new Context() {
      @Override
      public ServerTreeServerRuntimeUiUpdater.Context serverRuntimeUiUpdaterContext() {
        return serverRuntimeUiUpdaterContext;
      }

      @Override
      public void setStatusText(String text) {
        setStatusText.accept(text);
      }

      @Override
      public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
        setConnectionControlsEnabled.accept(connectEnabled, disconnectEnabled);
      }
    };
  }

  private final ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater;

  public ServerTreeRuntimeHeaderApi(ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater) {
    this.serverRuntimeUiUpdater =
        Objects.requireNonNull(serverRuntimeUiUpdater, "serverRuntimeUiUpdater");
  }

  public void setServerConnectionState(Context context, String serverId, ConnectionState state) {
    Context in = Objects.requireNonNull(context, "context");
    serverRuntimeUiUpdater.setServerConnectionState(
        in.serverRuntimeUiUpdaterContext(), serverId, state);
  }

  public void setServerDesiredOnline(Context context, String serverId, boolean desiredOnline) {
    Context in = Objects.requireNonNull(context, "context");
    serverRuntimeUiUpdater.setServerDesiredOnline(
        in.serverRuntimeUiUpdaterContext(), serverId, desiredOnline);
  }

  public void setServerConnectionDiagnostics(
      Context context, String serverId, String lastError, Long nextRetryEpochMs) {
    Context in = Objects.requireNonNull(context, "context");
    serverRuntimeUiUpdater.setServerConnectionDiagnostics(
        in.serverRuntimeUiUpdaterContext(), serverId, lastError, nextRetryEpochMs);
  }

  public void setServerConnectedIdentity(
      Context context,
      String serverId,
      String connectedHost,
      int connectedPort,
      String nick,
      Instant at) {
    Context in = Objects.requireNonNull(context, "context");
    serverRuntimeUiUpdater.setServerConnectedIdentity(
        in.serverRuntimeUiUpdaterContext(), serverId, connectedHost, connectedPort, nick, at);
  }

  public void setServerIrcv3Capability(
      Context context, String serverId, String capability, String subcommand, boolean enabled) {
    Context in = Objects.requireNonNull(context, "context");
    serverRuntimeUiUpdater.setServerIrcv3Capability(
        in.serverRuntimeUiUpdaterContext(), serverId, capability, subcommand, enabled);
  }

  public void setServerIsupportToken(
      Context context, String serverId, String tokenName, String tokenValue) {
    Context in = Objects.requireNonNull(context, "context");
    serverRuntimeUiUpdater.setServerIsupportToken(
        in.serverRuntimeUiUpdaterContext(), serverId, tokenName, tokenValue);
  }

  public void setServerVersionDetails(
      Context context,
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    Context in = Objects.requireNonNull(context, "context");
    serverRuntimeUiUpdater.setServerVersionDetails(
        in.serverRuntimeUiUpdaterContext(),
        serverId,
        serverName,
        serverVersion,
        userModes,
        channelModes);
  }

  public void setStatusText(Context context, String text) {
    Objects.requireNonNull(context, "context").setStatusText(text);
  }

  public void setConnectionControlsEnabled(
      Context context, boolean connectEnabled, boolean disconnectEnabled) {
    Objects.requireNonNull(context, "context")
        .setConnectionControlsEnabled(connectEnabled, disconnectEnabled);
  }

  public void setConnectedUi(Context context, boolean connected) {
    setConnectionControlsEnabled(context, !connected, connected);
  }
}
