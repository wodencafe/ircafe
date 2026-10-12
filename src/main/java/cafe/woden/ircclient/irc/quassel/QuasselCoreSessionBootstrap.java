package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Completes authenticated session initialization before starting reads. The service owns
 * availability and reconnect bookkeeping; this collaborator owns no additional tasks or session
 * state.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreSessionBootstrap {
  private static final String BUFFER_SYNCER_CLASS = "BufferSyncer";
  private static final String BUFFER_SYNCER_OBJECT = "";

  @NonNull private final QuasselCoreStateObservationCoordinator stateObservations;
  @NonNull private final QuasselCoreNetworkManagementCoordinator networkManagement;
  @NonNull private final QuasselCoreSessionReader sessionReader;
  @NonNull private final Consumer<String> onEstablished;

  void complete(QuasselCoreSession session, QuasselCoreAuthHandshake.AuthResult auth)
      throws Exception {
    session.initialize(
        auth,
        id -> stateObservations.observeNetwork(session, id, ""),
        info ->
            stateObservations.observeTargetNetwork(
                session, info.bufferName(), info.networkId(), false));
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    onEstablished.accept(session.serverId);

    session.networkCommands.requestInit(BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT);
    for (Integer networkId :
        session.networks.knownIds(session.authResult.get(), session.buffers.values())) {
      networkManagement.requestNetworkInitState(session, networkId);
    }
    sessionReader.start(session);
  }
}
