package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
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
      connection.sojuNetworksByNetId.clear();
      connection.genericBouncerNetworksById.clear();
      connection.sojuListNetworksRequestedThisSession.set(false);
      connection.sojuBouncerNetId.set("");
    } catch (Exception ignored) {
    }

    timers.cancelReconnect(connection);
    connection.clearManualDisconnect();
    connection.resetReconnectAttempts();

    IrcProperties.Server configured = serverCatalog.require(serverId);
    IrcProperties.Server server = stsPolicies.applyPolicy(configured);
    connection.connectedHost.set(Objects.toString(server.host(), "").trim());
    connection.connectedWithTls.set(server.tls());
    connection.selfNickHint.set(Objects.toString(server.nick(), "").trim());

    // ZNC detection uses CAP/004/*status heuristics, but we can still parse the configured login
    // now so logs and discovery logic have context once ZNC is detected.
    try {
      connection.zncDetected.set(false);
      connection.zncDetectedLogged.set(false);
      connection.zncBaseUser.set("");
      connection.zncClientId.set("");
      connection.zncNetwork.set("");

      connection.zncPlaybackRequestedThisSession.set(false);
      connection.zncListNetworksRequestedThisSession.set(false);
      connection.zncPlaybackCapture.cancelActive("reconnect");

      ZncLoginParts loginParts = ZncLoginParts.parse(server.login());
      ZncLoginParts saslParts =
          (server.sasl() != null && server.sasl().enabled())
              ? ZncLoginParts.parse(server.sasl().username())
              : new ZncLoginParts("", "", "");

      ZncLoginParts merged = loginParts.mergePreferThis(saslParts);
      connection.zncBaseUser.set(merged.baseUser());
      connection.zncClientId.set(merged.clientId());
      connection.zncNetwork.set(merged.network());
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
