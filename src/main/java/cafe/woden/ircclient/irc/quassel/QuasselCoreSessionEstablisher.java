package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreSessionEventPublisher.renderProbeSource;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import java.net.Socket;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Coordinates socket opening, protocol phases, authentication, and bootstrap. The service schedules
 * calls on IO; lifecycle and setup collaborators retain cleanup and reporting ownership.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreSessionEstablisher {
  private static final String PHASE_PROTOCOL_NEGOTIATED = "protocol-negotiated";

  @NonNull private final QuasselCoreSocketConnector socketConnector;
  @NonNull private final QuasselCoreHandshakeCoordinator handshakes;
  @NonNull private final QuasselCoreAuthHandshake authHandshake;
  @NonNull private final QuasselCoreSessionBootstrap sessionBootstrap;
  @NonNull private final QuasselCoreSessionLifecycle sessionLifecycle;
  @NonNull private final QuasselCoreSetupCoordinator setups;
  @NonNull private final QuasselCoreSessionEventPublisher sessionEvents;

  void establish(IrcProperties.Server server, QuasselCoreSession session) {
    Socket openedSocket = null;
    try {
      if (sessionLifecycle.abortEstablishmentIfRequested(session, null)) return;

      openedSocket = socketConnector.connect(server);
      if (sessionLifecycle.abortEstablishmentIfRequested(session, openedSocket)) return;

      QuasselCoreProtocolProbe.ProbeSelection probe = handshakes.negotiateDatastream(openedSocket);

      session.socketRef.set(openedSocket);
      session.phase.set(QuasselSessionPhase.TRANSPORT_CONNECTED);
      sessionEvents.emitConnected(session);

      session.probeSelection.set(probe);
      session.phase.set(QuasselSessionPhase.PROTOCOL_NEGOTIATED);
      sessionEvents.emitConnectionPhase(
          session, PHASE_PROTOCOL_NEGOTIATED, renderProbeSource(probe));

      session.phase.set(QuasselSessionPhase.AUTHENTICATING);
      QuasselCoreAuthHandshake.AuthResult auth = authHandshake.authenticate(openedSocket, server);
      sessionBootstrap.complete(session, auth);
    } catch (QuasselCoreAuthHandshake.CoreSetupRequiredException e) {
      sessionLifecycle.discardFailedConnection(session, openedSocket);
      if (session.closeRequested.get()) return;

      setups.reportRequired(session, e);
    } catch (Exception e) {
      sessionLifecycle.handleConnectFailure(session, openedSocket, e);
    }
  }
}
