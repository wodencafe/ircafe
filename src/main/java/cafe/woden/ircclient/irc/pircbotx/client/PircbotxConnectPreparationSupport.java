package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.irc.pircbotx.*;
import cafe.woden.ircclient.irc.znc.ZncLoginParts;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.util.Objects;

/** Prepares connection state for a new outbound connect attempt. */
final class PircbotxConnectPreparationSupport {

  private final ServerCatalog serverCatalog;
  private final Ircv3StsPolicyService stsPolicies;
  private final ServerIsupportStatePort serverIsupportState;
  private final PircbotxConnectionTimersRx timers;

  PircbotxConnectPreparationSupport(
      ServerCatalog serverCatalog,
      Ircv3StsPolicyService stsPolicies,
      ServerIsupportStatePort serverIsupportState,
      PircbotxConnectionTimersRx timers) {
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    this.stsPolicies = Objects.requireNonNull(stsPolicies, "stsPolicies");
    this.serverIsupportState = Objects.requireNonNull(serverIsupportState, "serverIsupportState");
    this.timers = Objects.requireNonNull(timers, "timers");
  }

  PreparedConnect prepare(String serverId, PircbotxConnectionState connection) {
    serverIsupportState.clearServer(serverId);
    connection.resetNegotiatedCaps();

    // soju discovery state is per-session; reset before starting a new connection.
    try {
      connection.clearSojuDiscoveredNetworks();
      connection.clearGenericBouncerDiscoveredNetworks();
      connection.clearSojuListNetworksRequest();
      connection.clearSojuBouncerNetId();
    } catch (Exception ignored) {
    }

    timers.cancelReconnect(connection);
    connection.clearManualDisconnect();
    connection.resetReconnectAttempts();

    IrcProperties.Server configured = serverCatalog.require(serverId);
    IrcProperties.Server server = stsPolicies.applyPolicy(configured);
    connection.setConnectedEndpoint(server.host(), server.tls());
    connection.setSelfNickHint(Objects.toString(server.nick(), "").trim());

    // ZNC detection uses CAP/004/*status heuristics, but we can still parse the configured login
    // now so logs and discovery logic have context once ZNC is detected.
    try {
      connection.clearZncDetection();
      connection.clearZncLoginContext();

      connection.clearZncPlaybackRequest();
      connection.clearZncListNetworksRequest();
      connection.cancelZncPlaybackCapture("reconnect");

      ZncLoginParts loginParts = ZncLoginParts.parse(server.login());
      ZncLoginParts saslParts =
          (server.sasl() != null && server.sasl().enabled())
              ? ZncLoginParts.parse(server.sasl().username())
              : new ZncLoginParts("", "", "");

      ZncLoginParts merged = loginParts.mergePreferThis(saslParts);
      connection.setZncLoginContext(merged.baseUser(), merged.clientId(), merged.network());
    } catch (Exception ignored) {
    }

    boolean disconnectOnSaslFailure =
        server.sasl() != null
            && server.sasl().enabled()
            && Boolean.TRUE.equals(server.sasl().disconnectOnFailure());
    return new PreparedConnect(server, disconnectOnSaslFailure);
  }

  record PreparedConnect(IrcProperties.Server server, boolean disconnectOnSaslFailure) {}
}
