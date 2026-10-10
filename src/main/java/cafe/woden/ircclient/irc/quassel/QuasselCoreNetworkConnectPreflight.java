package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreLogSummary.summarizeNetworkInfoForLog;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.networkStateLooksUsableForConnect;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkEnabled;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkIdentityId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parsePrimaryNetworkServer;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;

import cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.NetworkServerEndpoint;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates snapshot refresh and identity repair before a network connect request. */
final class QuasselCoreNetworkConnectPreflight {
  private static final Logger log =
      LoggerFactory.getLogger(QuasselCoreNetworkConnectPreflight.class);

  interface Commands {
    void requestNetworkInitState(int networkId) throws Exception;

    void updateNetwork(int networkId, QuasselCoreNetworkUpdateRequest request) throws Exception;

    void createNetwork(int identityId, QuasselCoreNetworkCreateRequest request) throws Exception;
  }

  private final String serverId;
  private final QuasselCoreNetworkCatalog networks;
  private final QuasselCoreIdentityState identities;
  private final QuasselCoreObservationMediator observations;
  private final IntSupplier resolveReplacementIdentity;
  private final Supplier<Set<Integer>> knownNetworkIds;
  private final Commands commands;

  QuasselCoreNetworkConnectPreflight(
      String serverId,
      QuasselCoreNetworkCatalog networks,
      QuasselCoreIdentityState identities,
      QuasselCoreObservationMediator observations,
      IntSupplier resolveReplacementIdentity,
      Supplier<Set<Integer>> knownNetworkIds,
      Commands commands) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.networks = Objects.requireNonNull(networks, "networks");
    this.identities = Objects.requireNonNull(identities, "identities");
    this.observations = Objects.requireNonNull(observations, "observations");
    this.resolveReplacementIdentity =
        Objects.requireNonNull(resolveReplacementIdentity, "resolveReplacementIdentity");
    this.knownNetworkIds = Objects.requireNonNull(knownNetworkIds, "knownNetworkIds");
    this.commands = Objects.requireNonNull(commands, "commands");
  }

  boolean prepare(int networkId) throws Exception {
    if (networkId < 0) return true;
    Map<String, Object> existing = refreshNetworkStateForConnectPreflight(networkId);
    int configuredIdentityId = parseNetworkIdentityId(existing);
    Map<String, Object> identityState =
        configuredIdentityId > 0 ? identities.state(configuredIdentityId) : Map.of();
    boolean identityStateUsable = QuasselCoreIdentityState.looksUsable(identityState);
    boolean identityKnown = configuredIdentityId > 0 && identities.isKnown(configuredIdentityId);
    NetworkServerEndpoint endpoint = parsePrimaryNetworkServer(existing);
    String networkName =
        firstNonBlank(
            mapValueIgnoreCase(existing, "networkName"),
            mapValueIgnoreCase(existing, "networkname"),
            mapValueIgnoreCase(existing, "name"),
            networks.displayNames().get(networkId),
            "network-" + networkId);
    boolean enabled = parseNetworkEnabled(existing);
    log.debug(
        "Quassel connect preflight state: serverId={}, networkId={}, configuredIdentityId={}, identityKnown={}, identityStateUsable={}, knownIdentityIds={}, identityNames={}, identityStateKeys={}, networkName={}, endpointHost={}, endpointPort={}, endpointTls={}, enabled={}, knownNetworkIds={}, networkStateKeys={}, rawState={}",
        serverId,
        networkId,
        configuredIdentityId,
        identityKnown,
        identityStateUsable,
        identities.knownIds(),
        identities.names(),
        identities.stateIds(),
        networkName,
        endpoint.host(),
        endpoint.port(),
        endpoint.useTls(),
        enabled,
        knownNetworkIds.get(),
        networks.stateIds(),
        summarizeNetworkInfoForLog(existing));
    if (identityKnown && identityStateUsable) {
      log.debug(
          "Skipping network identity repair before connect because identity is already known and state looks usable: serverId={}, networkId={}, identityId={}",
          serverId,
          networkId,
          configuredIdentityId);
      return true;
    }

    int replacementIdentityId = resolveReplacementIdentity.getAsInt();
    if (replacementIdentityId < 0) {
      log.debug(
          "Skipping network identity repair before connect because no replacement identity id is available: serverId={}, networkId={}",
          serverId,
          networkId);
      return true;
    }

    log.debug(
        "Quassel connect preflight found invalid/unknown identity or unusable identity state: serverId={}, networkId={}, configuredIdentityId={}, identityKnown={}, identityStateUsable={}, knownIdentityIds={}, replacementIdentityId={}, networkName={}, endpointHost={}, endpointPort={}, endpointTls={}, rawState={}",
        serverId,
        networkId,
        configuredIdentityId,
        identityKnown,
        identityStateUsable,
        identities.knownIds(),
        replacementIdentityId,
        networkName,
        endpoint.host(),
        endpoint.port(),
        endpoint.useTls(),
        summarizeNetworkInfoForLog(existing));
    if (endpoint.host().isBlank()) {
      log.debug(
          "Skipping network identity repair before connect due to missing host in network state: serverId={}, networkId={}",
          serverId,
          networkId);
      return true;
    }

    QuasselCoreNetworkUpdateRequest repairRequest =
        new QuasselCoreNetworkUpdateRequest(
            networkName,
            endpoint.host(),
            endpoint.port(),
            endpoint.useTls(),
            "",
            true,
            replacementIdentityId,
            enabled);
    commands.updateNetwork(
        networkId, QuasselCoreNetworkRequests.normalizeUpdateRequest(repairRequest));
    log.debug(
        "Submitted pre-connect network identity repair: serverId={}, networkId={}, identityId={}, host={}, port={}, tls={}, enabled={}",
        serverId,
        networkId,
        replacementIdentityId,
        endpoint.host(),
        endpoint.port(),
        endpoint.useTls(),
        enabled);
    // Network updates are async on core side; request a fresh snapshot before connect.
    commands.requestNetworkInitState(networkId);
    Map<String, Object> repaired =
        awaitNetworkStateSnapshotForIdentity(networkId, replacementIdentityId, 2_500L);
    int confirmedIdentityId = parseNetworkIdentityId(repaired);
    if (confirmedIdentityId != replacementIdentityId) {
      log.debug(
          "Quassel connect preflight identity repair not yet confirmed by core: serverId={}, networkId={}, expectedIdentityId={}, confirmedIdentityId={}, state={}",
          serverId,
          networkId,
          replacementIdentityId,
          confirmedIdentityId,
          summarizeNetworkInfoForLog(repaired));
      QuasselCoreNetworkCreateRequest fallbackRequest =
          new QuasselCoreNetworkCreateRequest(
              networkName,
              endpoint.host(),
              endpoint.port(),
              endpoint.useTls(),
              "",
              true,
              replacementIdentityId,
              List.of());
      log.debug(
          "Attempting createNetwork fallback to force identity repair: serverId={}, networkId={}, networkName={}, identityId={}",
          serverId,
          networkId,
          networkName,
          replacementIdentityId);
      commands.createNetwork(replacementIdentityId, fallbackRequest);
      commands.requestNetworkInitState(networkId);
      Map<String, Object> fallbackSnapshot =
          awaitNetworkStateSnapshotForIdentity(networkId, replacementIdentityId, 2_000L);
      int fallbackConfirmedIdentityId = parseNetworkIdentityId(fallbackSnapshot);
      if (fallbackConfirmedIdentityId != replacementIdentityId) {
        log.debug(
            "Quassel createNetwork fallback did not confirm identity repair: serverId={}, networkId={}, expectedIdentityId={}, confirmedIdentityId={}, state={}",
            serverId,
            networkId,
            replacementIdentityId,
            fallbackConfirmedIdentityId,
            summarizeNetworkInfoForLog(fallbackSnapshot));
        return false;
      }
      log.debug(
          "Quassel createNetwork fallback confirmed identity repair: serverId={}, networkId={}, confirmedIdentityId={}",
          serverId,
          networkId,
          fallbackConfirmedIdentityId);
      return true;
    }
    log.debug(
        "Post-repair network state snapshot: serverId={}, networkId={}, configuredIdentityId={}, state={}",
        serverId,
        networkId,
        confirmedIdentityId,
        summarizeNetworkInfoForLog(repaired));
    return true;
  }

  private Map<String, Object> refreshNetworkStateForConnectPreflight(int networkId)
      throws Exception {
    if (networkId < 0) return Map.of();
    Map<String, Object> existing = networks.state(networkId);
    if (networkStateLooksUsableForConnect(existing)) {
      return existing;
    }

    log.debug(
        "Quassel connect preflight requesting init-data refresh for network state: serverId={}, networkId={}, knownNetworkIds={}, networkStateKeys={}",
        serverId,
        networkId,
        knownNetworkIds.get(),
        networks.stateIds());
    commands.requestNetworkInitState(networkId);
    Map<String, Object> refreshed = awaitNetworkStateSnapshot(networkId, 800L);
    log.debug(
        "Quassel connect preflight init-data refresh result: serverId={}, networkId={}, refreshedStateLooksUsable={}, refreshedState={}",
        serverId,
        networkId,
        networkStateLooksUsableForConnect(refreshed),
        summarizeNetworkInfoForLog(refreshed));
    return refreshed;
  }

  private Map<String, Object> awaitNetworkStateSnapshot(int networkId, long timeoutMs) {
    if (networkId < 0) return Map.of();
    Map<String, Object> current = networks.state(networkId);
    if (networkStateLooksUsableForConnect(current)) {
      return current;
    }
    if (timeoutMs <= 0L) {
      return current == null ? Map.of() : current;
    }
    awaitQuasselNetworkCondition(
        timeoutMs,
        () -> {
          Map<String, Object> state = networks.state(networkId);
          return networkStateLooksUsableForConnect(state);
        });
    current = networks.state(networkId);
    return current == null ? Map.of() : current;
  }

  private Map<String, Object> awaitNetworkStateSnapshotForIdentity(
      int networkId, int expectedIdentityId, long timeoutMs) {
    if (networkId < 0) return Map.of();
    if (expectedIdentityId <= 0) {
      return awaitNetworkStateSnapshot(networkId, timeoutMs);
    }
    Map<String, Object> current = networks.state(networkId);
    if (parseNetworkIdentityId(current) == expectedIdentityId) {
      return current;
    }
    if (timeoutMs <= 0L) {
      return current == null ? Map.of() : current;
    }
    awaitQuasselNetworkCondition(
        timeoutMs,
        () -> {
          Map<String, Object> state = networks.state(networkId);
          return parseNetworkIdentityId(state) == expectedIdentityId;
        });
    current = networks.state(networkId);
    return current == null ? Map.of() : current;
  }

  private void awaitQuasselNetworkCondition(long timeoutMs, BooleanSupplier condition) {
    try {
      observations.whenNetwork(serverId, timeoutMs, condition).blockingAwait();
    } catch (Exception ignored) {
    }
  }
}
