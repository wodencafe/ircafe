package cafe.woden.ircclient.irc.soju;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Creates/updates ephemeral server entries for Soju-discovered networks.
 *
 * <p>These servers are not persisted to the runtime YAML; they exist only for the duration of the
 * bouncer control session.
 */
@Component
public class SojuEphemeralNetworkImporter {

  private static final Logger log = LoggerFactory.getLogger(SojuEphemeralNetworkImporter.class);

  private final ServerRegistry serverRegistry;
  private final EphemeralServerRegistry ephemeralServers;
  private final SojuAutoConnectStore autoConnect;
  private final IrcClientService irc;

  /** Guard against repeated connect() calls when the bouncer repeats NETWORK lines. */
  private final Set<String> autoConnectQueued = ConcurrentHashMap.newKeySet();

  public SojuEphemeralNetworkImporter(
      ServerRegistry serverRegistry,
      EphemeralServerRegistry ephemeralServers,
      SojuAutoConnectStore autoConnect,
      @Lazy IrcClientService irc) {
    this.serverRegistry = Objects.requireNonNull(serverRegistry, "serverRegistry");
    this.ephemeralServers = Objects.requireNonNull(ephemeralServers, "ephemeralServers");
    this.autoConnect = Objects.requireNonNull(autoConnect, "autoConnect");
    this.irc = Objects.requireNonNull(irc, "irc");
  }

  /**
   * Consume a discovered Soju network and upsert a corresponding ephemeral server entry.
   *
   * <p>Safe to call multiple times; entries are de-duplicated by deterministic server id.
   */
  public void onNetworkDiscovered(SojuNetwork network) {
    if (network == null) return;

    String bouncerId = network.bouncerServerId();
    Optional<IrcProperties.Server> bouncerOpt = serverRegistry.find(bouncerId);
    if (bouncerOpt.isEmpty()) {
      log.debug(
          "[soju] Ignoring discovered network '{}' (netId={}) for unknown bouncer id '{}'",
          network.name(),
          network.netId(),
          bouncerId);
      return;
    }

    IrcProperties.Server bouncer = bouncerOpt.get();
    SojuEphemeralNaming.Derived d = SojuEphemeralNaming.derive(bouncer, network);

    IrcProperties.Server server = buildEphemeralServer(bouncer, d);

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
    log.info(
        "[soju] Discovered network '{}' (netId={}) -> ephemeral server '{}'",
        d.networkName(),
        network.netId(),
        server.id());

    maybeAutoConnect(bouncerId, d.networkName(), server.id());
  }

  /**
   * Remove all ephemeral servers that were discovered from the given origin (typically the bouncer
   * control connection).
   */
  public void onOriginDisconnected(String originServerId) {
    String origin = java.util.Objects.toString(originServerId, "").trim();
    if (origin.isEmpty()) return;

    long count =
        ephemeralServers.entries().stream().filter(e -> origin.equals(e.originId())).count();
    if (count == 0) return;

    ephemeralServers.removeByOrigin(origin);
    log.info("[soju] Cleared {} ephemeral networks for origin '{}'", count, origin);

    // Drop queued-connect guards for this origin so reconnecting the bouncer can re-trigger.
    String prefix = "soju:" + origin + ":";
    autoConnectQueued.removeIf(id -> id != null && id.startsWith(prefix));
  }

  private void maybeAutoConnect(String bouncerId, String networkName, String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    if (!autoConnect.isEnabled(bouncerId, networkName)) return;
    if (!autoConnectQueued.add(sid)) return;

    try {
      irc.connect(sid)
          .subscribe(
              () -> {},
              err ->
                  log.warn(
                      "[soju] Auto-connect failed for '{}' ({}): {}",
                      networkName,
                      sid,
                      String.valueOf(err)));
      log.info(
          "[soju] Auto-connect enabled for '{}' on '{}' -> connecting {}",
          networkName,
          bouncerId,
          sid);
    } catch (Exception e) {
      log.warn("[soju] Auto-connect threw for '{}' ({}): {}", networkName, sid, String.valueOf(e));
    }
  }

  private static IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer, SojuEphemeralNaming.Derived d) {
    IrcProperties.Server.Sasl sasl = bouncer.sasl();

    // Always set the username variant (even if SASL is disabled) so later toggles don't require
    // re-importing.
    IrcProperties.Server.Sasl updatedSasl =
        new IrcProperties.Server.Sasl(
            sasl.enabled(),
            d.loginUser(),
            sasl.password(),
            sasl.mechanism(),
            sasl.disconnectOnFailure());

    return new IrcProperties.Server(
        d.serverId(),
        bouncer.host(),
        bouncer.port(),
        bouncer.tls(),
        bouncer.serverPassword(),
        bouncer.nick(),
        d.loginUser(),
        bouncer.realName(),
        updatedSasl,
        List.of(),
        List.of(),
        bouncer.proxy());
  }
}
