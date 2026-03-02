package cafe.woden.ircclient.irc.bouncer;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Shared importer behavior for bouncer-discovered ephemeral network entries.
 *
 * @param <N> network model parsed from a backend-specific discovery protocol
 */
public abstract class AbstractBouncerEphemeralNetworkImporter<N> {

  private final Logger log;
  private final String backendId;
  private final String ephemeralIdPrefix;
  private final ServerRegistry serverRegistry;
  private final EphemeralServerRegistry ephemeralServers;
  private final BouncerAutoConnectStore autoConnect;
  private final RuntimeConfigStore runtimeConfig;
  private final IrcClientService irc;

  /** Guard against repeated connect() calls when a bouncer repeats network discovery lines. */
  private final Set<String> autoConnectQueued = ConcurrentHashMap.newKeySet();

  protected AbstractBouncerEphemeralNetworkImporter(
      Logger log,
      String backendId,
      String ephemeralIdPrefix,
      ServerRegistry serverRegistry,
      EphemeralServerRegistry ephemeralServers,
      BouncerAutoConnectStore autoConnect,
      RuntimeConfigStore runtimeConfig,
      IrcClientService irc) {
    this.log = Objects.requireNonNull(log, "log");
    this.backendId = requireNonBlank(backendId, "backendId");
    this.ephemeralIdPrefix = requireNonBlank(ephemeralIdPrefix, "ephemeralIdPrefix");
    this.serverRegistry = Objects.requireNonNull(serverRegistry, "serverRegistry");
    this.ephemeralServers = Objects.requireNonNull(ephemeralServers, "ephemeralServers");
    this.autoConnect = Objects.requireNonNull(autoConnect, "autoConnect");
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.irc = Objects.requireNonNull(irc, "irc");
  }

  /**
   * Consume a discovered bouncer network and upsert a corresponding ephemeral server entry.
   *
   * <p>Safe to call multiple times; entries are de-duplicated by deterministic server id.
   */
  public final void onNetworkDiscovered(N network) {
    if (network == null) return;

    String bouncerId = Objects.toString(bouncerServerId(network), "").trim();
    if (bouncerId.isEmpty()) return;

    Optional<IrcProperties.Server> bouncerOpt = serverRegistry.find(bouncerId);
    if (bouncerOpt.isEmpty()) {
      logUnknownBouncer(network, bouncerId);
      return;
    }

    IrcProperties.Server bouncer = bouncerOpt.get();
    ResolvedBouncerNetwork resolved = resolveNetwork(bouncer, network);
    IrcProperties.Server server =
        buildEphemeralServer(bouncer, resolved, autoJoinChannelsFor(resolved.serverId()));

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
    logDiscoveredNetwork(resolved, network, server.id());

    maybeAutoConnect(bouncerId, resolved.autoConnectName(), server.id());
  }

  /**
   * Remove all ephemeral servers that were discovered from the given origin (typically the
   * bouncer-control connection).
   */
  public final void onOriginDisconnected(String originServerId) {
    String origin = Objects.toString(originServerId, "").trim();
    if (origin.isEmpty()) return;

    long count =
        ephemeralServers.entries().stream().filter(e -> origin.equals(e.originId())).count();
    if (count == 0) return;

    ephemeralServers.removeByOrigin(origin);
    log.info("[{}] Cleared {} ephemeral networks for origin '{}'", backendId, count, origin);

    // Drop queued-connect guards for this origin so reconnecting the bouncer can re-trigger.
    String prefix = ephemeralIdPrefix + origin + ":";
    autoConnectQueued.removeIf(id -> id != null && id.startsWith(prefix));
  }

  protected abstract String bouncerServerId(N network);

  protected abstract String networkDisplayName(N network);

  protected String networkDebugId(N network) {
    return "";
  }

  protected abstract ResolvedBouncerNetwork resolveNetwork(IrcProperties.Server bouncer, N network);

  protected abstract IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer, ResolvedBouncerNetwork resolved, List<String> autoJoinChannels);

  private void maybeAutoConnect(String bouncerId, String networkName, String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    if (!autoConnect.isEnabled(bouncerId, networkName)) return;
    if (!autoConnectQueued.add(sid)) return;

    try {
      var unused =
          irc.connect(sid)
              .subscribe(
                  () -> {},
                  err ->
                      log.warn(
                          "[{}] Auto-connect failed for '{}' ({}): {}",
                          backendId,
                          networkName,
                          sid,
                          String.valueOf(err)));
      log.info(
          "[{}] Auto-connect enabled for '{}' on '{}' -> connecting {}",
          backendId,
          networkName,
          bouncerId,
          sid);
    } catch (Exception e) {
      log.warn(
          "[{}] Auto-connect threw for '{}' ({}): {}",
          backendId,
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
      String ch = Objects.toString(channel, "").trim();
      if (ch.isEmpty()) continue;
      if (!runtimeConfig.readServerTreeChannelAutoReattach(serverId, ch, true)) continue;
      if (containsIgnoreCase(out, ch)) continue;
      out.add(ch);
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private void logUnknownBouncer(N network, String bouncerId) {
    String debug = debugSuffix(network);
    log.debug(
        "[{}] Ignoring discovered network '{}'{} for unknown bouncer id '{}'",
        backendId,
        networkDisplayName(network),
        debug,
        bouncerId);
  }

  private void logDiscoveredNetwork(ResolvedBouncerNetwork resolved, N network, String serverId) {
    String debug = debugSuffix(network);
    log.info(
        "[{}] Discovered network '{}'{} -> ephemeral server '{}'",
        backendId,
        resolved.displayName(),
        debug,
        serverId);
  }

  private String debugSuffix(N network) {
    String debug = Objects.toString(networkDebugId(network), "").trim();
    return debug.isEmpty() ? "" : " (" + debug + ")";
  }

  private static boolean containsIgnoreCase(List<String> values, String needle) {
    if (values == null || values.isEmpty()) return false;
    String n = Objects.toString(needle, "").trim();
    if (n.isEmpty()) return false;
    for (String value : values) {
      if (value != null && value.equalsIgnoreCase(n)) return true;
    }
    return false;
  }

  private static String requireNonBlank(String value, String field) {
    String v = Objects.toString(value, "").trim();
    if (v.isEmpty()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return v;
  }
}
