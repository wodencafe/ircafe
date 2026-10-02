package cafe.woden.ircclient.bouncer;

import cafe.woden.ircclient.bouncer.spi.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.spi.BouncerEphemeralServerSpec;
import cafe.woden.ircclient.bouncer.spi.BouncerNetworkMappingContext;
import cafe.woden.ircclient.bouncer.spi.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.bouncer.spi.BouncerServerProfile;
import cafe.woden.ircclient.bouncer.spi.ResolvedBouncerNetwork;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.BouncerDiscoveryConfigPort;
import cafe.woden.ircclient.config.servers.EphemeralServerRegistry;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;

/**
 * Generic orchestrator for discovered bouncer networks.
 *
 * <p>Backend-specific parsing and naming policy are delegated to a mapping strategy.
 */
@ApplicationLayer
@RequiredArgsConstructor
public final class BouncerNetworkDiscoveryOrchestrator {

  @NonNull private final Logger log;
  @NonNull private final BouncerNetworkMappingStrategy mappingStrategy;
  @NonNull private final ServerRegistry serverRegistry;
  @NonNull private final EphemeralServerRegistry ephemeralServers;
  @NonNull private final BouncerAutoConnectStore autoConnect;
  @NonNull private final BouncerDiscoveryConfigPort runtimeConfig;
  @NonNull private final BouncerConnectionPort connectionPort;

  private final BouncerDiscoveryRuntimeRules runtimeRules = new BouncerDiscoveryRuntimeRules();
  private final BouncerNetworkMappingContextFactory mappingContextFactory =
      new BouncerNetworkMappingContextFactory();
  private final BouncerServerProfileFactory serverProfileFactory =
      new BouncerServerProfileFactory();
  private final BouncerEphemeralServerConfigFactory ephemeralServerConfigFactory =
      new BouncerEphemeralServerConfigFactory();
  private final BouncerConfiguredServerTemplateFactory configuredServerTemplateFactory =
      new BouncerConfiguredServerTemplateFactory();
  private final BouncerDiscoveredNetworkPreflightPlanner preflightPlanner =
      new BouncerDiscoveredNetworkPreflightPlanner();
  private final BouncerOriginDisconnectPlanner originDisconnectPlanner =
      new BouncerOriginDisconnectPlanner();
  private final BouncerDiscoveredNetworkResolutionService resolutionService =
      new BouncerDiscoveredNetworkResolutionService();
  private final BouncerDiscoveredNetworkApplicationPlanner applicationPlanner =
      new BouncerDiscoveredNetworkApplicationPlanner();
  private final BouncerAutoConnectQueueGate autoConnectQueueGate =
      new BouncerAutoConnectQueueGate();
  private final BouncerAutoConnectExecutionPlanner autoConnectExecutionPlanner =
      new BouncerAutoConnectExecutionPlanner(autoConnectQueueGate);
  private final BouncerNetworkDebugLabelFormatter debugLabelFormatter =
      new BouncerNetworkDebugLabelFormatter();

  public String backendId() {
    return mappingStrategy.backendId();
  }

  /**
   * Consume a discovered bouncer network and upsert a corresponding ephemeral server entry.
   *
   * <p>Safe to call multiple times; entries are de-duplicated by deterministic server id.
   */
  public void onNetworkDiscovered(BouncerDiscoveredNetwork network) {
    BouncerDiscoveredNetworkPreflightPlan preflight = preflightPlanner.plan(backendId(), network);
    if (!preflight.accepts()) return;

    String bouncerId = preflight.originServerId();

    Optional<IrcProperties.Server> bouncerOpt = serverRegistry.find(bouncerId);
    if (bouncerOpt.isEmpty()) {
      logUnknownBouncer(network, bouncerId);
      return;
    }

    IrcProperties.Server bouncer = bouncerOpt.get();
    BouncerDiscoveredNetworkResolution resolution =
        resolutionService.resolve(
            mappingStrategy,
            bouncerProfile(bouncer),
            network,
            mappingContext(),
            this::autoJoinChannelsFor);
    IrcProperties.Server server = buildEphemeralServer(bouncer, resolution.serverSpec());

    BouncerDiscoveredNetworkApplicationPlan plan =
        applicationPlan(server, bouncerId, resolution.resolvedNetwork());
    if (plan.removesEphemeralDuplicate()) {
      ephemeralServers.remove(plan.serverId());
      return;
    }
    if (plan.keepsExisting()) return;

    ephemeralServers.upsert(server, bouncerId);
    logDiscoveredNetwork(network, resolution.resolvedNetwork(), plan.serverId());

    maybeAutoConnect(bouncerId, plan.autoConnectName(), plan.serverId());
  }

  /**
   * Remove all ephemeral servers that were discovered from the given origin (typically the
   * bouncer-control connection).
   */
  public void onOriginDisconnected(String originServerId) {
    BouncerOriginDisconnectPlan plan =
        originDisconnectPlanner.plan(
            originServerId, ephemeralServers.entries().stream().map(e -> e.originId()).toList());
    if (!plan.clearsOrigin()) return;

    ephemeralServers.removeByOrigin(plan.originServerId());
    log.info(
        "[{}] Cleared {} ephemeral networks for origin '{}'",
        backendId(),
        plan.ephemeralCount(),
        plan.originServerId());

    // Drop queued-connect guards for this origin so reconnecting the bouncer can re-trigger.
    autoConnectQueueGate.clearOrigin(plan.originServerId());
  }

  private BouncerDiscoveredNetworkApplicationPlan applicationPlan(
      IrcProperties.Server server, String bouncerId, ResolvedBouncerNetwork resolved) {
    Optional<IrcProperties.Server> existingOpt = ephemeralServers.find(server.id());
    boolean same = existingOpt.isPresent() && existingOpt.get().equals(server);
    boolean sameOrigin =
        ephemeralServers.originOf(server.id()).map(o -> o.equals(bouncerId)).orElse(false);

    return applicationPlanner.plan(
        server.id(),
        resolved.autoConnectName(),
        serverRegistry.containsId(server.id()),
        same,
        sameOrigin);
  }

  private BouncerNetworkMappingContext mappingContext() {
    return mappingContextFactory.fromRuntimeSettings(
        runtimeConfig.readGenericBouncerLoginTemplate(
            mappingContextFactory.defaultGenericLoginTemplate()),
        runtimeConfig.readGenericBouncerPreferLoginHint(
            mappingContextFactory.defaultPreferLoginHint()));
  }

  private void maybeAutoConnect(String bouncerId, String networkName, String serverId) {
    BouncerAutoConnectExecutionPlan plan =
        autoConnectExecutionPlanner.plan(
            bouncerId, networkName, serverId, () -> autoConnect.isEnabled(bouncerId, networkName));
    if (!plan.connects()) return;

    try {
      var unused =
          connectionPort
              .connect(plan.serverId())
              .subscribe(
                  () -> {},
                  err ->
                      log.warn(
                          "[{}] Auto-connect failed for '{}' ({}): {}",
                          backendId(),
                          plan.networkName(),
                          plan.serverId(),
                          String.valueOf(err)));
      log.info(
          "[{}] Auto-connect enabled for '{}' on '{}' -> connecting {}",
          backendId(),
          plan.networkName(),
          plan.bouncerId(),
          plan.serverId());
    } catch (Exception e) {
      log.warn(
          "[{}] Auto-connect threw for '{}' ({}): {}",
          backendId(),
          plan.networkName(),
          plan.serverId(),
          String.valueOf(e));
    }
  }

  private BouncerServerProfile bouncerProfile(IrcProperties.Server bouncer) {
    IrcProperties.Server.Sasl sasl = bouncer.sasl();
    return serverProfileFactory.fromConfiguredServer(
        bouncer.id(), bouncer.login(), sasl == null ? null : sasl.username());
  }

  private IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer, BouncerEphemeralServerSpec spec) {
    BouncerEphemeralServerConfig config =
        ephemeralServerConfigFactory.fromConfiguredServer(configuredTemplate(bouncer), spec);
    BouncerEphemeralServerConfig.Sasl sasl = config.sasl();

    IrcProperties.Server.Sasl updatedSasl =
        new IrcProperties.Server.Sasl(
            sasl.enabled(),
            sasl.username(),
            sasl.password(),
            sasl.mechanism(),
            sasl.disconnectOnFailure());

    return new IrcProperties.Server(
        config.serverId(),
        config.host(),
        config.port(),
        config.tls(),
        config.serverPassword(),
        config.nick(),
        config.login(),
        config.realName(),
        updatedSasl,
        bouncer.nickserv(),
        config.autoJoinChannels(),
        List.of(),
        bouncer.proxy(),
        bouncer.backendId(),
        bouncer.trustAllCertificates());
  }

  private BouncerConfiguredServerTemplate configuredTemplate(IrcProperties.Server bouncer) {
    IrcProperties.Server.Sasl sasl = bouncer.sasl();
    return configuredServerTemplateFactory.fromConfiguredServerFields(
        bouncer.host(),
        bouncer.port(),
        bouncer.tls(),
        bouncer.serverPassword(),
        bouncer.nick(),
        bouncer.login(),
        bouncer.realName(),
        sasl == null ? null : sasl.enabled(),
        sasl == null ? null : sasl.username(),
        sasl == null ? null : sasl.password(),
        sasl == null ? null : sasl.mechanism(),
        sasl == null ? null : sasl.disconnectOnFailure());
  }

  private List<String> autoJoinChannelsFor(String serverId) {
    return runtimeRules.autoJoinChannels(
        runtimeConfig.readKnownChannels(serverId),
        channel -> runtimeConfig.readServerTreeChannelAutoReattach(serverId, channel, true));
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
    return debugLabelFormatter.suffixFor(mappingStrategy, network);
  }
}
