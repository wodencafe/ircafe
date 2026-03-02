package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeNetworkInfoDialogBuilder;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Adapter for {@link ServerTreeNetworkInfoDialogBuilder.Context}. */
public final class ServerTreeNetworkInfoDialogContextAdapter
    implements ServerTreeNetworkInfoDialogBuilder.Context {

  @FunctionalInterface
  public interface CapabilityToggleRequester {
    void request(String serverId, String capability, boolean enable);
  }

  private final Function<String, ConnectionState> connectionStateForServer;
  private final Predicate<String> desiredOnlineForServer;
  private final Function<String, String> prettyServerLabel;
  private final Function<String, String> connectionDiagnosticsTipForServer;
  private final CapabilityToggleRequester capabilityToggleRequester;

  public ServerTreeNetworkInfoDialogContextAdapter(
      Function<String, ConnectionState> connectionStateForServer,
      Predicate<String> desiredOnlineForServer,
      Function<String, String> prettyServerLabel,
      Function<String, String> connectionDiagnosticsTipForServer,
      CapabilityToggleRequester capabilityToggleRequester) {
    this.connectionStateForServer =
        Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    this.desiredOnlineForServer =
        Objects.requireNonNull(desiredOnlineForServer, "desiredOnlineForServer");
    this.prettyServerLabel = Objects.requireNonNull(prettyServerLabel, "prettyServerLabel");
    this.connectionDiagnosticsTipForServer =
        Objects.requireNonNull(
            connectionDiagnosticsTipForServer, "connectionDiagnosticsTipForServer");
    this.capabilityToggleRequester =
        Objects.requireNonNull(capabilityToggleRequester, "capabilityToggleRequester");
  }

  @Override
  public ConnectionState connectionStateForServer(String serverId) {
    return connectionStateForServer.apply(serverId);
  }

  @Override
  public boolean desiredOnlineForServer(String serverId) {
    return desiredOnlineForServer.test(serverId);
  }

  @Override
  public String prettyServerLabel(String serverId) {
    return prettyServerLabel.apply(serverId);
  }

  @Override
  public String connectionDiagnosticsTipForServer(String serverId) {
    return connectionDiagnosticsTipForServer.apply(serverId);
  }

  @Override
  public void requestCapabilityToggle(String serverId, String capability, boolean enable) {
    capabilityToggleRequester.request(serverId, capability, enable);
  }
}
