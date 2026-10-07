package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreLogSummary.summarizeNetworkInfoForLog;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates identity bootstrap and Core confirmation for modern and legacy network creation. */
final class QuasselCoreNetworkCreationCoordinator {
  static final String RPC_CREATE_NETWORK_SLOT = "2createNetwork(NetworkInfo,QStringList)";
  static final String RPC_CREATE_NETWORK_SLOT_LEGACY = "2createNetwork(NetworkInfo)";
  private static final Logger log =
      LoggerFactory.getLogger(QuasselCoreNetworkCreationCoordinator.class);

  interface Commands {
    void createIdentity(Map<String, Object> payload) throws Exception;

    void createNetwork(int identityId, QuasselCoreNetworkCreateRequest request, boolean legacy)
        throws Exception;
  }

  private final String serverId;
  private final String initialNick;
  private final QuasselCoreIdentityState identities;
  private final QuasselCoreNetworkCatalog networks;
  private final QuasselCoreObservationMediator observations;
  private final ToIntFunction<Integer> resolveIdentity;
  private final Supplier<Set<Integer>> knownNetworkIds;
  private final Commands commands;

  QuasselCoreNetworkCreationCoordinator(
      String serverId,
      String initialNick,
      QuasselCoreIdentityState identities,
      QuasselCoreNetworkCatalog networks,
      QuasselCoreObservationMediator observations,
      ToIntFunction<Integer> resolveIdentity,
      Supplier<Set<Integer>> knownNetworkIds,
      Commands commands) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.initialNick = initialNick;
    this.identities = Objects.requireNonNull(identities, "identities");
    this.networks = Objects.requireNonNull(networks, "networks");
    this.observations = Objects.requireNonNull(observations, "observations");
    this.resolveIdentity = Objects.requireNonNull(resolveIdentity, "resolveIdentity");
    this.knownNetworkIds = Objects.requireNonNull(knownNetworkIds, "knownNetworkIds");
    this.commands = Objects.requireNonNull(commands, "commands");
  }

  // The service normalizes the request and retains its IO scheduler boundary.
  void create(QuasselCoreNetworkCreateRequest request) throws Exception {
    if (request.identityId() == null && !identities.hasKnown()) {
      maybeCreateDefaultIdentityForNetwork(request);
    }
    int identityId = resolveIdentity.applyAsInt(request.identityId());
    log.debug(
        "Quassel create network request: serverId={}, networkName={}, host={}, port={}, tls={}, verifyTls={}, autoJoinCount={}, requestedIdentityId={}, resolvedIdentityId={}",
        serverId,
        request.networkName(),
        request.serverHost(),
        request.serverPort(),
        request.useTls(),
        request.verifyTls(),
        request.autoJoinChannels() == null ? 0 : request.autoJoinChannels().size(),
        request.identityId(),
        identityId);
    Set<Integer> baseline = Set.copyOf(knownNetworkIds.get());
    commands.createNetwork(identityId, request, false);
    maybeRetryLegacyCreateNetworkSlot(identityId, request, baseline);
  }

  private void maybeCreateDefaultIdentityForNetwork(QuasselCoreNetworkCreateRequest request)
      throws Exception {
    if (identities.hasKnown()) {
      log.debug(
          "Skipping create identity bootstrap because identities are already known: serverId={}, knownIdentityIds={}, identityNames={}",
          serverId,
          identities.knownIds(),
          identities.names());
      return;
    }
    int objectIdentityId = identities.firstKnownId();
    if (objectIdentityId >= 0) {
      log.debug(
          "Skipping create identity bootstrap because firstKnownIdentityId returned {}: serverId={}",
          objectIdentityId,
          serverId);
      return;
    }

    Map<String, Object> identityPayload =
        QuasselCoreIdentityRequests.defaultPayload(initialNick, request);
    log.debug(
        "No known Quassel identity observed before network create. Sending create identity RPC: serverId={}, payload={}",
        serverId,
        summarizeNetworkInfoForLog(identityPayload));
    commands.createIdentity(identityPayload);
    int observedIdentity = awaitObservedIdentityId(1_500L);
    if (observedIdentity >= 0) {
      log.debug(
          "Observed identity id {} after create identity RPC on serverId={}.",
          observedIdentity,
          serverId);
      return;
    }
    log.debug(
        "No identity observed after create identity RPC on serverId={}. Continuing with fallback identity resolution.",
        serverId);
  }

  private int awaitObservedIdentityId(long timeoutMs) {
    if (timeoutMs <= 0L) return -1;
    int current = identities.firstKnownId();
    if (current >= 0) return current;
    try {
      observations
          .whenIdentity(serverId, timeoutMs, () -> identities.firstKnownId() >= 0)
          .blockingAwait();
    } catch (Exception ignored) {
    }
    return identities.firstKnownId();
  }

  private void maybeRetryLegacyCreateNetworkSlot(
      int identityId, QuasselCoreNetworkCreateRequest request, Set<Integer> baselineNetworkIds)
      throws Exception {
    if (request == null) return;
    List<String> autoJoin = request.autoJoinChannels();
    if (autoJoin != null && !autoJoin.isEmpty()) {
      return;
    }

    if (awaitObservedNetworkAfterCreate(request.networkName(), baselineNetworkIds, 1_000L)) {
      return;
    }

    log.debug(
        "No network observed after create RPC {} for serverId={}, networkName={}. Retrying once with legacy slot {}.",
        RPC_CREATE_NETWORK_SLOT,
        serverId,
        request.networkName(),
        RPC_CREATE_NETWORK_SLOT_LEGACY);
    commands.createNetwork(identityId, request, true);
    if (awaitObservedNetworkAfterCreate(request.networkName(), baselineNetworkIds, 1_000L)) {
      log.debug(
          "Network '{}' observed after legacy create retry on serverId={}.",
          request.networkName(),
          serverId);
      return;
    }
    log.debug(
        "Network '{}' still not observed after legacy create retry on serverId={}.",
        request.networkName(),
        serverId);
  }

  private boolean awaitObservedNetworkAfterCreate(
      String expectedNetworkName, Set<Integer> baselineNetworkIds, long timeoutMs) {
    String wanted = Objects.toString(expectedNetworkName, "").trim();
    Set<Integer> baseline = baselineNetworkIds == null ? Set.of() : Set.copyOf(baselineNetworkIds);
    if (timeoutMs <= 0L) return false;

    if ((!wanted.isEmpty() && networks.isObservedName(wanted))
        || hasObservedNewNetworkId(baseline)) {
      return true;
    }
    try {
      observations
          .whenNetwork(
              serverId,
              timeoutMs,
              () ->
                  (!wanted.isEmpty() && networks.isObservedName(wanted))
                      || hasObservedNewNetworkId(baseline))
          .blockingAwait();
    } catch (Exception ignored) {
    }
    return (!wanted.isEmpty() && networks.isObservedName(wanted))
        || hasObservedNewNetworkId(baseline);
  }

  private boolean hasObservedNewNetworkId(Set<Integer> baselineIds) {
    Set<Integer> baseline = baselineIds == null ? Set.of() : baselineIds;
    for (Integer id : knownNetworkIds.get()) {
      if (id == null || id.intValue() < 0) continue;
      if (!baseline.contains(id)) return true;
    }
    return false;
  }
}
