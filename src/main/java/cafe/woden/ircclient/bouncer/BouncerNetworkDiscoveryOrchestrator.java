package cafe.woden.ircclient.bouncer;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;

/**
 * Generic orchestrator for discovered bouncer networks.
 *
 * <p>Backend-specific parsing and naming policy are delegated to a mapping strategy.
 */
@ApplicationLayer
public final class BouncerNetworkDiscoveryOrchestrator {

  private final Logger log;
  private final BouncerNetworkMappingStrategy mappingStrategy;
  private final ServerRegistry serverRegistry;
  private final EphemeralServerRegistry ephemeralServers;
  private final BouncerAutoConnectStore autoConnect;
  private final RuntimeConfigStore runtimeConfig;
  private final BouncerConnectionPort connectionPort;

  /** Guard against repeated connect() calls when a bouncer repeats network discovery lines. */
  private final Set<String> autoConnectQueued = ConcurrentHashMap.newKeySet();

  public BouncerNetworkDiscoveryOrchestrator(
      Logger log,
      BouncerNetworkMappingStrategy mappingStrategy,
      ServerRegistry serverRegistry,
      EphemeralServerRegistry ephemeralServers,
      BouncerAutoConnectStore autoConnect,
      RuntimeConfigStore runtimeConfig,
      BouncerConnectionPort connectionPort) {
    this.log = Objects.requireNonNull(log, "log");
    this.mappingStrategy = Objects.requireNonNull(mappingStrategy, "mappingStrategy");
    this.serverRegistry = Objects.requireNonNull(serverRegistry, "serverRegistry");
    this.ephemeralServers = Objects.requireNonNull(ephemeralServers, "ephemeralServers");
    this.autoConnect = Objects.requireNonNull(autoConnect, "autoConnect");
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.connectionPort = Objects.requireNonNull(connectionPort, "connectionPort");
  }

  public String backendId() {
    return mappingStrategy.backendId();
  }

  /**
   * Consume a discovered bouncer network and upsert a corresponding ephemeral server entry.
   *
   * <p>Safe to call multiple times; entries are de-duplicated by deterministic server id.
   */
  public void onNetworkDiscovered(BouncerDiscoveredNetwork network) {
    if (network == null) return;
    if (!isSameBackend(network.backendId())) return;

    String bouncerId = normalize(network.originServerId());
    if (bouncerId == null) return;

    Optional<IrcProperties.Server> bouncerOpt = serverRegistry.find(bouncerId);
    if (bouncerOpt.isEmpty()) {
      logUnknownBouncer(network, bouncerId);
      return;
    }

    IrcProperties.Server bouncer = bouncerOpt.get();
    ResolvedBouncerNetwork resolved = mappingStrategy.resolveNetwork(bouncer, network);
    IrcProperties.Server server =
        mappingStrategy.buildEphemeralServer(
            bouncer, resolved, autoJoinChannelsFor(resolved.serverId()));

    // If the user has chosen to persist this network entry, don't keep an ephemeral duplicate.
    if (serverRegistry.containsId(server.id())) {
      ephemeralServers.remove(server.id());
      return;
    }

    Optional<IrcProperties.Server> existingOpt = ephemeralServers.find(server.id());
    boolean same = existingOpt.isPresent() && existingOpt.get().equals(server);
    boolean sameOrigin =
        ephemeralServers.originOf(server.id()).map(o -> o.equals(bouncerId)).orElse(false);
    if (same && sameOrigin) return;

    ephemeralServers.upsert(server, bouncerId);
    logDiscoveredNetwork(network, resolved, server.id());

    maybeAutoConnect(bouncerId, resolved.autoConnectName(), server.id());
  }

  /**
   * Remove all ephemeral servers that were discovered from the given origin (typically the
   * bouncer-control connection).
   */
  public void onOriginDisconnected(String originServerId) {
    String origin = normalize(originServerId);
    if (origin == null) return;

    long count =
        ephemeralServers.entries().stream().filter(e -> origin.equals(e.originId())).count();
    if (count == 0) return;

    ephemeralServers.removeByOrigin(origin);
    log.info("[{}] Cleared {} ephemeral networks for origin '{}'", backendId(), count, origin);

    // Drop queued-connect guards for this origin so reconnecting the bouncer can re-trigger.
    autoConnectQueued.removeIf(id -> originMatchesServerId(id, origin));
  }

  private void maybeAutoConnect(String bouncerId, String networkName, String serverId) {
    String sid = normalize(serverId);
    if (sid == null) return;

    if (!autoConnect.isEnabled(bouncerId, networkName)) return;
    if (!autoConnectQueued.add(sid)) return;

    try {
      var unused =
          connectionPort
              .connect(sid)
              .subscribe(
                  () -> {},
                  err ->
                      log.warn(
                          "[{}] Auto-connect failed for '{}' ({}): {}",
                          backendId(),
                          networkName,
                          sid,
                          String.valueOf(err)));
      log.info(
          "[{}] Auto-connect enabled for '{}' on '{}' -> connecting {}",
          backendId(),
          networkName,
          bouncerId,
          sid);
    } catch (Exception e) {
      log.warn(
          "[{}] Auto-connect threw for '{}' ({}): {}",
          backendId(),
          networkName,
          sid,
          String.valueOf(e));
    }
  }

  private List<String> autoJoinChannelsFor(String serverId) {
    List<String> channels = runtimeConfig.readKnownChannels(serverId);
    if (channels == null || channels.isEmpty()) return List.of();

    ArrayList<String> out = new ArrayList<>();
    for (String channel : channels) {
      String ch = normalize(channel);
      if (ch == null) continue;
      if (!runtimeConfig.readServerTreeChannelAutoReattach(serverId, ch, true)) continue;
      if (containsIgnoreCase(out, ch)) continue;
      out.add(ch);
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private void logUnknownBouncer(BouncerDiscoveredNetwork network, String bouncerId) {
    String debug = debugSuffix(network);
    log.debug(
        "[{}] Ignoring discovered network '{}'{} for unknown bouncer id '{}'",
        backendId(),
        network.displayName(),
        debug,
        bouncerId);
  }

  private void logDiscoveredNetwork(
      BouncerDiscoveredNetwork network, ResolvedBouncerNetwork resolved, String serverId) {
    String debug = debugSuffix(network);
    log.info(
        "[{}] Discovered network '{}'{} -> ephemeral server '{}'",
        backendId(),
        resolved.displayName(),
        debug,
        serverId);
  }

  private String debugSuffix(BouncerDiscoveredNetwork network) {
    String debug = normalize(mappingStrategy.networkDebugId(network));
    return debug == null ? "" : " (" + debug + ")";
  }

  private boolean isSameBackend(String discoveredBackendId) {
    String expected = normalize(backendId());
    String actual = normalize(discoveredBackendId);
    if (expected == null || actual == null) return false;
    return expected.equalsIgnoreCase(actual);
  }

  private static boolean originMatchesServerId(String serverId, String originServerId) {
    String server = normalize(serverId);
    String origin = normalize(originServerId);
    if (server == null || origin == null) return false;

    int firstColon = server.indexOf(':');
    if (firstColon <= 0 || firstColon + 1 >= server.length()) return false;
    int secondColon = server.indexOf(':', firstColon + 1);
    if (secondColon <= firstColon + 1) return false;

    String parsedOrigin = server.substring(firstColon + 1, secondColon).trim();
    return origin.equals(parsedOrigin);
  }

  private static boolean containsIgnoreCase(List<String> values, String needle) {
    if (values == null || values.isEmpty()) return false;
    String n = normalize(needle);
    if (n == null) return false;
    for (String value : values) {
      if (value != null && value.equalsIgnoreCase(n)) return true;
    }
    return false;
  }

  private static String normalize(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v;
  }
}
