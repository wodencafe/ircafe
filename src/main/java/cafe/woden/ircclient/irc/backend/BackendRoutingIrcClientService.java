package cafe.woden.ircclient.irc.backend;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.InstalledPluginServices;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.BackendMetadataPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.irc.DisconnectRequestSource;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcDisconnectWithSourcePort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.util.PluginServiceLoaderSupport;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.annotation.PreDestroy;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Routes {@link IrcClientService} calls to the configured server backend.
 *
 * <p>Per-server backend selection comes from {@link IrcProperties.Server#backendId()}.
 */
@Service("ircClientService")
@Primary
@SecondaryAdapter
@InfrastructureLayer
public class BackendRoutingIrcClientService
    implements IrcClientService,
        IrcDisconnectWithSourcePort,
        IrcHeartbeatMaintenanceService,
        IrcBackendAvailabilityPort,
        IrcBackendModePort,
        QuasselCoreControlPort,
        IrcBouncerPlaybackPort {
  private static final Logger log = LoggerFactory.getLogger(BackendRoutingIrcClientService.class);
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private final ServerCatalog serverCatalog;
  private final BackendMetadataPort backendMetadata;
  private final Map<String, IrcBackendClientService> backendsById;
  private final List<IrcBackendClientService> backends;
  private final List<URLClassLoader> pluginClassLoaders;
  private final Map<String, String> activeBackendByServer = new ConcurrentHashMap<>();
  private final Flowable<ServerIrcEvent> mergedEvents;
  private final Flowable<QuasselCoreNetworkSnapshotEvent> mergedQuasselNetworkEvents;

  @Autowired
  public BackendRoutingIrcClientService(
      ServerCatalog serverCatalog,
      InstalledPluginServices installedPluginServices,
      ObjectProvider<BackendMetadataPort> backendMetadataProvider,
      List<IrcBackendClientService> backendServices) {
    this(
        serverCatalog,
        backendMetadataProvider.getIfAvailable(),
        loadInstalledBackendServices(
            List.copyOf(Objects.requireNonNullElse(backendServices, List.of())),
            installedPluginServices));
  }

  public BackendRoutingIrcClientService(
      ServerCatalog serverCatalog,
      RuntimeConfigPathPort runtimeConfigPathPort,
      ObjectProvider<BackendMetadataPort> backendMetadataProvider,
      List<IrcBackendClientService> backendServices) {
    this(
        serverCatalog,
        backendMetadataProvider.getIfAvailable(),
        loadInstalledBackendServices(
            List.copyOf(Objects.requireNonNullElse(backendServices, List.of())),
            PluginServiceLoaderSupport.resolvePluginDirectory(
                runtimeConfigPathPort == null ? null : runtimeConfigPathPort::runtimeConfigPath,
                log),
            PluginServiceLoaderSupport.defaultApplicationClassLoader(
                BackendRoutingIrcClientService.class)));
  }

  public BackendRoutingIrcClientService(
      ServerCatalog serverCatalog,
      BackendMetadataPort backendMetadata,
      List<IrcBackendClientService> backendServices) {
    this(serverCatalog, backendMetadata, backendServices, List.of());
  }

  private BackendRoutingIrcClientService(
      ServerCatalog serverCatalog,
      BackendMetadataPort backendMetadata,
      LoadedBackendServices loadedBackendServices) {
    this(
        serverCatalog,
        backendMetadata,
        Objects.requireNonNull(loadedBackendServices, "loadedBackendServices").services(),
        loadedBackendServices.pluginClassLoaders());
  }

  private BackendRoutingIrcClientService(
      ServerCatalog serverCatalog,
      BackendMetadataPort backendMetadata,
      List<IrcBackendClientService> backendServices,
      List<URLClassLoader> pluginClassLoaders) {
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    this.backendMetadata =
        Objects.requireNonNullElseGet(backendMetadata, BackendMetadataPort::builtInsOnly);
    Objects.requireNonNull(backendServices, "backendServices");
    this.pluginClassLoaders =
        List.copyOf(Objects.requireNonNull(pluginClassLoaders, "pluginClassLoaders"));

    LinkedHashMap<String, IrcBackendClientService> map = new LinkedHashMap<>();
    for (IrcBackendClientService backendService : backendServices) {
      if (backendService == null) continue;
      String backendId = backendIdOf(backendService);
      if (backendId.isEmpty()) {
        throw new IllegalArgumentException(
            "Irc backend service reported blank backend id: "
                + backendService.getClass().getName());
      }
      IrcBackendClientService previous = map.putIfAbsent(backendId, backendService);
      if (previous != null) {
        throw new IllegalStateException(
            "Multiple Irc backend services registered for "
                + backendId
                + ": "
                + previous.getClass().getName()
                + ", "
                + backendService.getClass().getName());
      }
    }

    if (map.isEmpty()) {
      throw new IllegalStateException("No Irc backend services were registered");
    }

    this.backendsById = Map.copyOf(map);
    this.backends = List.copyOf(map.values());

    ArrayList<Flowable<ServerIrcEvent>> streams = new ArrayList<>(backends.size());
    ArrayList<Flowable<QuasselCoreNetworkSnapshotEvent>> quasselNetworkStreams =
        new ArrayList<>(backends.size());
    for (IrcBackendClientService backend : backends) {
      String backendId = backendIdOf(backend);
      streams.add(backend.events().doOnNext(event -> noteBackendOwnership(backendId, event)));
      quasselNetworkStreams.add(backend.quasselCoreNetworkEvents());
    }
    this.mergedEvents = Flowable.merge(streams).onBackpressureBuffer();
    this.mergedQuasselNetworkEvents = Flowable.merge(quasselNetworkStreams).onBackpressureBuffer();
  }

  @Deprecated(forRemoval = false)
  public BackendRoutingIrcClientService(
      ServerCatalog serverCatalog, List<IrcBackendClientService> backendServices) {
    this(serverCatalog, BackendMetadataPort.builtInsOnly(), backendServices);
  }

  static BackendRoutingIrcClientService installed(
      ServerCatalog serverCatalog,
      BackendMetadataPort backendMetadata,
      Path pluginDirectory,
      ClassLoader applicationClassLoader,
      List<IrcBackendClientService> builtInBackendServices) {
    return new BackendRoutingIrcClientService(
        serverCatalog,
        backendMetadata,
        loadInstalledBackendServices(
            List.copyOf(Objects.requireNonNullElse(builtInBackendServices, List.of())),
            pluginDirectory,
            applicationClassLoader));
  }

  static BackendRoutingIrcClientService installed(
      ServerCatalog serverCatalog,
      BackendMetadataPort backendMetadata,
      RuntimeConfigPathPort runtimeConfigPathPort,
      ClassLoader applicationClassLoader,
      List<IrcBackendClientService> builtInBackendServices) {
    return installed(
        serverCatalog,
        backendMetadata,
        PluginServiceLoaderSupport.resolvePluginDirectory(
            runtimeConfigPathPort == null ? null : runtimeConfigPathPort::runtimeConfigPath, log),
        applicationClassLoader,
        builtInBackendServices);
  }

  @PreDestroy
  void closePluginClassLoaders() {
    PluginServiceLoaderSupport.closePluginClassLoaders(
        pluginClassLoaders, log, "[ircafe] failed to close backend transport plugin classloader");
  }

  @Override
  public void shutdownNow() {
    activeBackendByServer.clear();
    for (IrcBackendClientService backend : backends) {
      try {
        backend.shutdownNow();
      } catch (Exception e) {
        log.warn("Failed shutting down backend {}", backend.getClass().getSimpleName(), e);
      }
    }
  }

  @Override
  public Flowable<ServerIrcEvent> events() {
    return mergedEvents;
  }

  @Override
  public void rescheduleActiveHeartbeats() {
    for (IrcBackendClientService backend : backends) {
      try {
        backend.rescheduleActiveHeartbeats();
      } catch (Exception e) {
        log.debug(
            "Failed rescheduling heartbeats for backend {}", backend.getClass().getSimpleName(), e);
      }
    }
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    return routeActiveOrConfigured(serverId).currentNick(serverId);
  }

  @Override
  public Completable connect(String serverId) {
    IrcBackendClientService delegate = routeConfigured(serverId);
    String sid = normalizeServerId(serverId);
    return delegate
        .connect(serverId)
        .doOnComplete(
            () -> {
              if (!sid.isEmpty()) {
                activeBackendByServer.put(sid, backendIdOf(delegate));
              }
            });
  }

  @Override
  public Completable disconnect(String serverId) {
    String sid = normalizeServerId(serverId);
    return routeActiveOrConfigured(serverId)
        .disconnect(serverId)
        .doOnComplete(() -> clearActiveOwnership(sid));
  }

  @Override
  public Completable disconnect(String serverId, String reason) {
    String sid = normalizeServerId(serverId);
    return routeActiveOrConfigured(serverId)
        .disconnect(serverId, reason)
        .doOnComplete(() -> clearActiveOwnership(sid));
  }

  @Override
  public Completable disconnect(String serverId, String reason, DisconnectRequestSource source) {
    String sid = normalizeServerId(serverId);
    IrcBackendClientService delegate = routeActiveOrConfigured(serverId);
    Completable disconnect;
    if (delegate instanceof IrcDisconnectWithSourcePort sourceAware) {
      disconnect =
          sourceAware.disconnect(
              serverId, reason, source == null ? DisconnectRequestSource.UNKNOWN : source);
    } else {
      disconnect = delegate.disconnect(serverId, reason);
    }
    return disconnect.doOnComplete(() -> clearActiveOwnership(sid));
  }

  @Override
  public Completable changeNick(String serverId, String newNick) {
    return routeActiveOrConfigured(serverId).changeNick(serverId, newNick);
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    return routeActiveOrConfigured(serverId).setAway(serverId, awayMessage);
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return routeActiveOrConfigured(serverId).requestNames(serverId, channel);
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return routeActiveOrConfigured(serverId).joinChannel(serverId, channel);
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return routeActiveOrConfigured(serverId).whois(serverId, nick);
  }

  @Override
  public Completable whowas(String serverId, String nick, int count) {
    return routeActiveOrConfigured(serverId).whowas(serverId, nick, count);
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return routeActiveOrConfigured(serverId).partChannel(serverId, channel, reason);
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    return routeActiveOrConfigured(serverId).sendToChannel(serverId, channel, message);
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    return routeActiveOrConfigured(serverId).sendPrivateMessage(serverId, nick, message);
  }

  @Override
  public Completable sendNoticeToChannel(String serverId, String channel, String message) {
    return routeActiveOrConfigured(serverId).sendNoticeToChannel(serverId, channel, message);
  }

  @Override
  public Completable sendNoticePrivate(String serverId, String nick, String message) {
    return routeActiveOrConfigured(serverId).sendNoticePrivate(serverId, nick, message);
  }

  @Override
  public Completable sendRaw(String serverId, String rawLine) {
    return routeActiveOrConfigured(serverId).sendRaw(serverId, rawLine);
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, Instant beforeExclusive, int limit) {
    return routeActiveOrConfigured(serverId)
        .requestChatHistoryBefore(serverId, target, beforeExclusive, limit);
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, String selector, int limit) {
    return routeActiveOrConfigured(serverId)
        .requestChatHistoryBefore(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryLatest(
      String serverId, String target, String selector, int limit) {
    return routeActiveOrConfigured(serverId)
        .requestChatHistoryLatest(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    return routeActiveOrConfigured(serverId)
        .requestChatHistoryBetween(serverId, target, startSelector, endSelector, limit);
  }

  @Override
  public Completable requestChatHistoryAround(
      String serverId, String target, String selector, int limit) {
    return routeActiveOrConfigured(serverId)
        .requestChatHistoryAround(serverId, target, selector, limit);
  }

  @Override
  public String backendAvailabilityReason(String serverId) {
    return routeActiveOrConfigured(serverId).backendAvailabilityReason(serverId);
  }

  @Override
  public String backendIdForServer(String serverId) {
    return effectiveBackendIdForServer(serverId);
  }

  @Override
  public boolean isQuasselCoreSetupPending(String serverId) {
    return routeActiveOrConfigured(serverId).isQuasselCoreSetupPending(serverId);
  }

  @Override
  public boolean hasEstablishedQuasselCoreSession(String serverId) {
    return routeActiveOrConfigured(serverId).hasEstablishedQuasselCoreSession(serverId);
  }

  @Override
  public Optional<QuasselCoreSetupPrompt> quasselCoreSetupPrompt(String serverId) {
    return routeActiveOrConfigured(serverId).quasselCoreSetupPrompt(serverId);
  }

  @Override
  public Completable submitQuasselCoreSetup(String serverId, QuasselCoreSetupRequest request) {
    return routeActiveOrConfigured(serverId).submitQuasselCoreSetup(serverId, request);
  }

  @Override
  public List<QuasselCoreNetworkSummary> quasselCoreNetworks(String serverId) {
    return routeActiveOrConfigured(serverId).quasselCoreNetworks(serverId);
  }

  @Override
  public Flowable<QuasselCoreNetworkSnapshotEvent> quasselCoreNetworkEvents() {
    return mergedQuasselNetworkEvents;
  }

  @Override
  public Completable quasselCoreConnectNetwork(String serverId, String networkIdOrName) {
    return routeActiveOrConfigured(serverId).quasselCoreConnectNetwork(serverId, networkIdOrName);
  }

  @Override
  public Completable quasselCoreDisconnectNetwork(String serverId, String networkIdOrName) {
    return routeActiveOrConfigured(serverId)
        .quasselCoreDisconnectNetwork(serverId, networkIdOrName);
  }

  @Override
  public Completable quasselCoreCreateNetwork(
      String serverId, QuasselCoreNetworkCreateRequest request) {
    return routeActiveOrConfigured(serverId).quasselCoreCreateNetwork(serverId, request);
  }

  @Override
  public Completable quasselCoreUpdateNetwork(
      String serverId, String networkIdOrName, QuasselCoreNetworkUpdateRequest request) {
    return routeActiveOrConfigured(serverId)
        .quasselCoreUpdateNetwork(serverId, networkIdOrName, request);
  }

  @Override
  public Completable quasselCoreRemoveNetwork(String serverId, String networkIdOrName) {
    return routeActiveOrConfigured(serverId).quasselCoreRemoveNetwork(serverId, networkIdOrName);
  }

  @Override
  public boolean isChatHistoryAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isChatHistoryAvailable(serverId);
  }

  @Override
  public boolean isEchoMessageAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isEchoMessageAvailable(serverId);
  }

  @Override
  public boolean isMessageTagsAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isMessageTagsAvailable(serverId);
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isDraftReplyAvailable(serverId);
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isDraftReactAvailable(serverId);
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isDraftUnreactAvailable(serverId);
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isMultilineAvailable(serverId);
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    return routeActiveOrConfigured(serverId).negotiatedMultilineMaxBytes(serverId);
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    return routeActiveOrConfigured(serverId).negotiatedMultilineMaxLines(serverId);
  }

  @Override
  public boolean isMessageEditAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isMessageEditAvailable(serverId);
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isMessageRedactionAvailable(serverId);
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isTypingAvailable(serverId);
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    return routeActiveOrConfigured(serverId).typingAvailabilityReason(serverId);
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isReadMarkerAvailable(serverId);
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isLabeledResponseAvailable(serverId);
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isStandardRepliesAvailable(serverId);
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isMonitorAvailable(serverId);
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    return routeActiveOrConfigured(serverId).negotiatedMonitorLimit(serverId);
  }

  @Override
  public Completable requestLagProbe(String serverId) {
    return routeActiveOrConfigured(serverId).requestLagProbe(serverId);
  }

  @Override
  public boolean shouldRequestLagProbe(String serverId) {
    return routeActiveOrConfigured(serverId).shouldRequestLagProbe(serverId);
  }

  @Override
  public boolean isLagProbeReady(String serverId) {
    return routeActiveOrConfigured(serverId).isLagProbeReady(serverId);
  }

  @Override
  public OptionalLong lastMeasuredLagMs(String serverId) {
    return routeActiveOrConfigured(serverId).lastMeasuredLagMs(serverId);
  }

  @Override
  public Completable sendTyping(String serverId, String target, String state) {
    return routeActiveOrConfigured(serverId).sendTyping(serverId, target, state);
  }

  @Override
  public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return routeActiveOrConfigured(serverId).sendReadMarker(serverId, target, markerAt);
  }

  @Override
  public boolean isZncPlaybackAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isZncPlaybackAvailable(serverId);
  }

  @Override
  public boolean isZncBouncerDetected(String serverId) {
    return routeActiveOrConfigured(serverId).isZncBouncerDetected(serverId);
  }

  @Override
  public boolean isSojuBouncerAvailable(String serverId) {
    return routeActiveOrConfigured(serverId).isSojuBouncerAvailable(serverId);
  }

  @Override
  public Completable requestZncPlaybackRange(
      String serverId, String target, Instant fromInclusive, Instant toInclusive) {
    return routeActiveOrConfigured(serverId)
        .requestZncPlaybackRange(serverId, target, fromInclusive, toInclusive);
  }

  @Override
  public Completable sendAction(String serverId, String target, String action) {
    return routeActiveOrConfigured(serverId).sendAction(serverId, target, action);
  }

  private IrcBackendClientService routeActiveOrConfigured(String serverId) {
    String sid = normalizeServerId(serverId);
    IrcBackendClientService active = resolveActiveOwner(sid);
    if (active != null) return active;
    return routeConfigured(serverId);
  }

  private IrcBackendClientService routeConfigured(String serverId) {
    String sid = normalizeServerId(serverId);
    Optional<IrcProperties.Server> configuredServer =
        sid.isEmpty() ? Optional.empty() : serverCatalog.find(sid);
    String backendId = configuredBackendId(configuredServer);
    IrcBackendClientService delegate = backendsById.get(backendId);
    if (delegate != null) return delegate;

    if (configuredServer.isPresent()) {
      throw new IllegalStateException(
          "["
              + sid
              + "] no backend service registered for configured backend "
              + renderBackend(backendId));
    }

    IrcBackendClientService fallback =
        backendsById.get(BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC));
    if (fallback != null) {
      if (sid.isEmpty()) {
        log.debug("Falling back to IRC backend for blank server id");
      } else {
        log.warn(
            "[{}] no backend registered for {}; falling back to IRC backend",
            sid,
            renderBackend(backendId));
      }
      return fallback;
    }

    IrcBackendClientService first = backends.get(0);
    if (sid.isEmpty()) {
      log.debug("Falling back to backend {} for blank server id", first.backendId());
    } else {
      log.warn(
          "[{}] no backend registered for {}; falling back to {}",
          sid,
          renderBackend(backendId),
          renderBackend(first.backendId()));
    }
    return first;
  }

  private String effectiveBackendIdForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    IrcBackendClientService active = resolveActiveOwner(sid);
    if (active != null) {
      return normalizeBackendId(active.backendId());
    }
    Optional<IrcProperties.Server> configuredServer =
        sid.isEmpty() ? Optional.empty() : serverCatalog.find(sid);
    return configuredBackendId(configuredServer);
  }

  private static String configuredBackendId(Optional<IrcProperties.Server> configuredServer) {
    return configuredServer
        .map(IrcProperties.Server::backendId)
        .map(BackendRoutingIrcClientService::normalizeBackendId)
        .filter(id -> !id.isEmpty())
        .orElseGet(() -> BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC));
  }

  private String renderBackend(String backendId) {
    String normalized = normalizeBackendId(backendId);
    if (normalized.isEmpty()) return "backend";
    String displayName =
        Objects.toString(backendMetadata.backendDisplayName(normalized), "").trim();
    if (displayName.isEmpty() || displayName.equalsIgnoreCase(normalized)) {
      return normalized;
    }
    return displayName + " (" + normalized + ")";
  }

  private IrcBackendClientService resolveActiveOwner(String sid) {
    if (sid.isEmpty()) return null;
    String activeBackendId = normalizeBackendId(activeBackendByServer.get(sid));
    if (activeBackendId.isEmpty()) return null;
    IrcBackendClientService activeDelegate = backendsById.get(activeBackendId);
    if (activeDelegate == null) {
      activeBackendByServer.remove(sid, activeBackendId);
      return null;
    }
    return activeDelegate;
  }

  private void noteBackendOwnership(String backendId, ServerIrcEvent serverEvent) {
    String resolvedBackendId = normalizeBackendId(backendId);
    if (resolvedBackendId.isEmpty() || serverEvent == null) return;
    String sid = normalizeServerId(serverEvent.serverId());
    if (sid.isEmpty()) return;
    IrcEvent event = serverEvent.event();
    if (event instanceof IrcEvent.Disconnected) {
      activeBackendByServer.remove(sid, resolvedBackendId);
      return;
    }
    if (event instanceof IrcEvent.Connecting
        || event instanceof IrcEvent.Connected
        || event instanceof IrcEvent.Reconnecting
        || event instanceof IrcEvent.ConnectionReady) {
      activeBackendByServer.put(sid, resolvedBackendId);
    }
  }

  private void clearActiveOwnership(String sid) {
    if (sid.isEmpty()) return;
    activeBackendByServer.remove(sid);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
  }

  private static String backendIdOf(IrcBackendClientService backendService) {
    if (backendService == null) return "";
    String backendId = normalizeBackendId(backendService.backendId());
    if (!backendId.isEmpty()) return backendId;
    IrcProperties.Server.Backend backend = backendService.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }

  private static LoadedBackendServices loadInstalledBackendServices(
      List<IrcBackendClientService> builtInBackendServices,
      Path pluginDirectory,
      ClassLoader applicationClassLoader) {
    PluginServiceLoaderSupport.LoadedServices<IrcBackendClientService> loadedServices =
        PluginServiceLoaderSupport.loadInstalledServices(
            IrcBackendClientService.class,
            builtInBackendServices,
            pluginDirectory,
            applicationClassLoader,
            log);
    return new LoadedBackendServices(
        loadedServices.services(), loadedServices.pluginClassLoaders());
  }

  private static LoadedBackendServices loadInstalledBackendServices(
      List<IrcBackendClientService> builtInBackendServices,
      InstalledPluginServices installedPluginServices) {
    InstalledPluginServices pluginServices =
        Objects.requireNonNull(installedPluginServices, "installedPluginServices");
    return new LoadedBackendServices(
        pluginServices.loadInstalledServices(IrcBackendClientService.class, builtInBackendServices),
        List.of());
  }

  private record LoadedBackendServices(
      List<IrcBackendClientService> services, List<URLClassLoader> pluginClassLoaders) {}
}
