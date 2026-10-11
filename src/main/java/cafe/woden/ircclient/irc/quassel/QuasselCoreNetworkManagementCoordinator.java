package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreLogSummary.summarizeNetworkInfoForLog;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkIdentityId;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkSummary;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates network management against live session catalogs and existing command workflows. The
 * service validates the session and schedules calls on IO workers.
 */
final class QuasselCoreNetworkManagementCoordinator {
  private static final Logger log =
      LoggerFactory.getLogger(QuasselCoreNetworkManagementCoordinator.class);
  private static final String SYNC_CONNECT_NETWORK_SLOT = "requestConnect";
  private static final String SYNC_DISCONNECT_NETWORK_SLOT = "requestDisconnect";

  private final QuasselCoreObservationMediator observations;
  private final QuasselCoreStateObservationCoordinator stateObservations;

  QuasselCoreNetworkManagementCoordinator(
      QuasselCoreObservationMediator observations,
      QuasselCoreStateObservationCoordinator stateObservations) {
    this.observations = Objects.requireNonNull(observations, "observations");
    this.stateObservations = Objects.requireNonNull(stateObservations, "stateObservations");
  }

  void connect(QuasselCoreSession session, String networkIdOrName) throws Exception {
    String sid = session.serverId;
    int networkId =
        resolveQuasselNetworkId(session, sid, networkIdOrName, "quassel connect network");
    log.debug(
        "Quassel connect network request: serverId={}, networkToken='{}', resolvedNetworkId={}, slot={}",
        sid,
        Objects.toString(networkIdOrName, ""),
        networkId,
        SYNC_CONNECT_NETWORK_SLOT);
    QuasselCoreNetworkSummary targetSummary =
        findNetworkSummaryById(stateObservations.snapshot(session), networkId);
    if (targetSummary == null) {
      log.debug(
          "Quassel connect target summary missing from current snapshot: serverId={}, networkId={}, knownNetworkIds={}, displayNames={}, stateKeys={}",
          sid,
          networkId,
          collectKnownNetworkIds(session),
          session.networks.displayNames(),
          session.networks.stateIds());
    } else {
      log.debug(
          "Quassel connect target summary: serverId={}, networkId={}, networkName={}, connected={}, enabled={}, identityId={}, host={}, port={}, tls={}, rawState={}",
          sid,
          networkId,
          targetSummary.networkName(),
          targetSummary.connected(),
          targetSummary.enabled(),
          targetSummary.identityId(),
          targetSummary.serverHost(),
          targetSummary.serverPort(),
          targetSummary.useTls(),
          summarizeNetworkInfoForLog(targetSummary.rawState()));
    }
    boolean repairConfirmed = maybeRepairNetworkIdentityBeforeConnect(session, networkId);
    if (!repairConfirmed) {
      log.debug(
          "Skipping Quassel connect because identity repair is not confirmed yet: serverId={}, networkId={}",
          sid,
          networkId);
      return;
    }
    session.networkCommands.syncNetwork(networkId, SYNC_CONNECT_NETWORK_SLOT);
  }

  void disconnect(QuasselCoreSession session, String networkIdOrName) throws Exception {
    String sid = session.serverId;
    int networkId =
        resolveQuasselNetworkId(session, sid, networkIdOrName, "quassel disconnect network");
    log.debug(
        "Quassel disconnect network request: serverId={}, networkToken='{}', resolvedNetworkId={}, slot={}",
        sid,
        Objects.toString(networkIdOrName, ""),
        networkId,
        SYNC_DISCONNECT_NETWORK_SLOT);
    session.networkCommands.syncNetwork(networkId, SYNC_DISCONNECT_NETWORK_SLOT);
  }

  void create(QuasselCoreSession session, QuasselCoreNetworkCreateRequest request)
      throws Exception {
    String sid = session.serverId;
    QuasselCoreNetworkCreateRequest req =
        QuasselCoreNetworkRequests.normalizeCreateRequest(
            Objects.requireNonNull(request, "request"));
    log.debug(
        "Quassel create network preflight: serverId={}, requestedIdentityId={}, knownIdentityIds={}, identityStateKeys={}, identityNames={}, authNetworkIds={}",
        sid,
        req.identityId(),
        session.identities.knownIds(),
        session.identities.stateIds(),
        session.identities.names(),
        Optional.ofNullable(session.authResult.get())
            .map(QuasselCoreAuthHandshake.AuthResult::networkIds)
            .orElse(List.of()));
    coordinateNetworkCreation(session, req);
  }

  void update(
      QuasselCoreSession session, String networkIdOrName, QuasselCoreNetworkUpdateRequest request)
      throws Exception {
    String sid = session.serverId;
    int networkId =
        resolveQuasselNetworkId(session, sid, networkIdOrName, "quassel update network");
    QuasselCoreNetworkUpdateRequest req =
        QuasselCoreNetworkRequests.normalizeUpdateRequest(
            Objects.requireNonNull(request, "request"));
    networkCommandMediator(session).updateNetwork(networkId, req);
  }

  void remove(QuasselCoreSession session, String networkIdOrName) throws Exception {
    String sid = session.serverId;
    int networkId =
        resolveQuasselNetworkId(session, sid, networkIdOrName, "quassel remove network");
    session.networkCommands.removeNetwork(networkId);
    stateObservations.forgetNetwork(session, networkId);
  }

  private static int firstKnownIdentityId(QuasselCoreSession session) {
    return session == null ? -1 : session.identities.firstKnownId();
  }

  private static LinkedHashSet<Integer> collectKnownNetworkIds(QuasselCoreSession session) {
    return session == null
        ? new LinkedHashSet<>()
        : session.networks.knownIds(session.authResult.get(), session.buffers.values());
  }

  private int resolveQuasselNetworkId(
      QuasselCoreSession session, String serverId, String networkIdOrName, String operation) {
    if (session == null) throw new IllegalStateException("Quassel session is missing");
    int resolved = session.networks.resolve(networkIdOrName);
    if (resolved >= 0) return resolved;
    String raw = Objects.toString(networkIdOrName, "").trim();
    throw new BackendNotAvailableException(
        IrcProperties.Server.Backend.QUASSEL_CORE,
        operation,
        serverId,
        "unknown Quassel network '" + raw + "' (run /quasselnet list)");
  }

  private static int resolveQuasselIdentityId(
      QuasselCoreSession session, Integer requestedIdentityId) {
    if (requestedIdentityId != null && requestedIdentityId.intValue() > 0) {
      return requestedIdentityId.intValue();
    }
    if (session != null) {
      int observedIdentity = firstKnownIdentityId(session);
      if (observedIdentity >= 0) return observedIdentity;
      int primary = primaryNetworkId(session);
      if (primary >= 0) {
        Map<String, Object> state = session.networks.state(primary);
        int fromPrimary = parseNetworkIdentityId(state);
        if (fromPrimary >= 0) return fromPrimary;
      }
      for (Map<String, Object> state : session.networks.states()) {
        int parsed = parseNetworkIdentityId(state);
        if (parsed >= 0) return parsed;
      }
      log.debug(
          "Falling back to default Quassel identity id=1: serverId={}, requestedIdentityId={}, knownIdentityIds={}, identityStateKeys={}, networkStateKeys={}",
          session.serverId,
          requestedIdentityId,
          session.identities.knownIds(),
          session.identities.stateIds(),
          session.networks.stateIds());
    }
    return 1;
  }

  private boolean maybeRepairNetworkIdentityBeforeConnect(QuasselCoreSession session, int networkId)
      throws Exception {
    if (session == null || networkId < 0) return true;
    return new QuasselCoreNetworkConnectPreflight(
            session.serverId,
            session.networks,
            session.identities,
            observations,
            () -> resolveQuasselIdentityId(session, null),
            () -> collectKnownNetworkIds(session),
            networkCommandMediator(session))
        .prepare(networkId);
  }

  void requestNetworkInitState(QuasselCoreSession session, int networkId) throws Exception {
    if (session == null || networkId < 0) return;
    networkCommandMediator(session).requestNetworkInitState(networkId);
  }

  private static QuasselCoreNetworkSummary findNetworkSummaryById(
      List<QuasselCoreNetworkSummary> networks, int networkId) {
    if (networks == null || networks.isEmpty() || networkId < 0) return null;
    for (QuasselCoreNetworkSummary summary : networks) {
      if (summary == null || summary.networkId() < 0) continue;
      if (summary.networkId() == networkId) return summary;
    }
    return null;
  }

  private void coordinateNetworkCreation(
      QuasselCoreSession session, QuasselCoreNetworkCreateRequest request) throws Exception {
    new QuasselCoreNetworkCreationCoordinator(
            session.serverId,
            session.initialNick,
            session.identities,
            session.networks,
            observations,
            requested -> resolveQuasselIdentityId(session, requested),
            () -> collectKnownNetworkIds(session),
            networkCommandMediator(session))
        .create(request);
  }

  private QuasselCoreNetworkCommandMediator networkCommandMediator(QuasselCoreSession session) {
    if (session == null) throw new IllegalStateException("Quassel session is missing");
    return new QuasselCoreNetworkCommandMediator(
        session,
        () -> resolveQuasselIdentityId(session, null),
        (networkId, name) -> stateObservations.observeNetwork(session, networkId, name));
  }

  private static int primaryNetworkId(QuasselCoreSession session) {
    return session == null
        ? -1
        : session.networks.primaryNetworkId(session.authResult.get(), session.buffers.values());
  }
}
