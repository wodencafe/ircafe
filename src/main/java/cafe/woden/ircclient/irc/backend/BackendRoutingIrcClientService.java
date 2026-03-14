package cafe.woden.ircclient.irc.backend;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Routes {@link IrcClientService} calls to the configured server backend.
 *
 * <p>Per-server backend selection comes from {@link IrcProperties.Server#backend()}.
 */
@Service("ircClientService")
@Primary
@InfrastructureLayer
public class BackendRoutingIrcClientService
    implements IrcClientService,
        IrcHeartbeatMaintenanceService,
        IrcBackendAvailabilityPort,
        IrcBackendModePort,
        QuasselCoreControlPort,
        IrcBouncerPlaybackPort {
  private static final Logger log = LoggerFactory.getLogger(BackendRoutingIrcClientService.class);

  private final ServerCatalog serverCatalog;
  private final Map<IrcProperties.Server.Backend, IrcBackendClientService> backendsByType;
  private final List<IrcBackendClientService> backends;
  private final Map<String, IrcProperties.Server.Backend> activeBackendByServer =
      new ConcurrentHashMap<>();
  private final Flowable<ServerIrcEvent> mergedEvents;
  private final Flowable<QuasselCoreNetworkSnapshotEvent> mergedQuasselNetworkEvents;

  public BackendRoutingIrcClientService(
      ServerCatalog serverCatalog, List<IrcBackendClientService> backendServices) {
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    Objects.requireNonNull(backendServices, "backendServices");

    LinkedHashMap<IrcProperties.Server.Backend, IrcBackendClientService> map =
        new LinkedHashMap<>();
    for (IrcBackendClientService backendService : backendServices) {
      if (backendService == null) continue;
      IrcProperties.Server.Backend backend = backendService.backend();
      if (backend == null) {
        throw new IllegalArgumentException(
            "Irc backend service reported null backend: " + backendService.getClass().getName());
      }
      IrcBackendClientService previous = map.putIfAbsent(backend, backendService);
      if (previous != null) {
        throw new IllegalStateException(
            "Multiple Irc backend services registered for "
                + backend
                + ": "
                + previous.getClass().getName()
                + ", "
                + backendService.getClass().getName());
      }
    }

    if (map.isEmpty()) {
      throw new IllegalStateException("No Irc backend services were registered");
    }

    this.backendsByType = Map.copyOf(map);
    this.backends = List.copyOf(map.values());

    ArrayList<Flowable<ServerIrcEvent>> streams = new ArrayList<>(backends.size());
    ArrayList<Flowable<QuasselCoreNetworkSnapshotEvent>> quasselNetworkStreams =
        new ArrayList<>(backends.size());
    for (IrcBackendClientService backend : backends) {
      IrcProperties.Server.Backend backendType = backend.backend();
      streams.add(backend.events().doOnNext(event -> noteBackendOwnership(backendType, event)));
      quasselNetworkStreams.add(backend.quasselCoreNetworkEvents());
    }
    this.mergedEvents = Flowable.merge(streams).onBackpressureBuffer();
    this.mergedQuasselNetworkEvents = Flowable.merge(quasselNetworkStreams).onBackpressureBuffer();
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
                activeBackendByServer.put(sid, delegate.backend());
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
  public IrcProperties.Server.Backend backendForServer(String serverId) {
    return effectiveBackendForServer(serverId);
  }

  @Override
  public boolean isMatrixBackendServer(String serverId) {
    return effectiveBackendForServer(serverId) == IrcProperties.Server.Backend.MATRIX;
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
    IrcProperties.Server.Backend backend = configuredBackend(configuredServer);
    IrcBackendClientService delegate = backendsByType.get(backend);
    if (delegate != null) return delegate;

    if (configuredServer.isPresent()) {
      throw new IllegalStateException(
          "[" + sid + "] no backend service registered for configured backend " + backend.token());
    }

    IrcBackendClientService fallback = backendsByType.get(IrcProperties.Server.Backend.IRC);
    if (fallback != null) {
      if (sid.isEmpty()) {
        log.debug("Falling back to IRC backend for blank server id");
      } else {
        log.warn(
            "[{}] no backend registered for {}; falling back to IRC backend", sid, backend.token());
      }
      return fallback;
    }

    IrcBackendClientService first = backends.get(0);
    if (sid.isEmpty()) {
      log.debug("Falling back to backend {} for blank server id", first.backend().token());
    } else {
      log.warn(
          "[{}] no backend registered for {}; falling back to {}",
          sid,
          backend.token(),
          first.backend().token());
    }
    return first;
  }

  private IrcProperties.Server.Backend effectiveBackendForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    IrcBackendClientService active = resolveActiveOwner(sid);
    if (active != null && active.backend() != null) {
      return active.backend();
    }
    Optional<IrcProperties.Server> configuredServer =
        sid.isEmpty() ? Optional.empty() : serverCatalog.find(sid);
    return configuredBackend(configuredServer);
  }

  private static IrcProperties.Server.Backend configuredBackend(
      Optional<IrcProperties.Server> configuredServer) {
    return configuredServer
        .map(IrcProperties.Server::backend)
        .orElse(IrcProperties.Server.Backend.IRC);
  }

  private IrcBackendClientService resolveActiveOwner(String sid) {
    if (sid.isEmpty()) return null;
    IrcProperties.Server.Backend activeBackend = activeBackendByServer.get(sid);
    if (activeBackend == null) return null;
    IrcBackendClientService activeDelegate = backendsByType.get(activeBackend);
    if (activeDelegate == null) {
      activeBackendByServer.remove(sid, activeBackend);
      return null;
    }
    return activeDelegate;
  }

  private void noteBackendOwnership(
      IrcProperties.Server.Backend backend, ServerIrcEvent serverEvent) {
    if (backend == null || serverEvent == null) return;
    String sid = normalizeServerId(serverEvent.serverId());
    if (sid.isEmpty()) return;
    IrcEvent event = serverEvent.event();
    if (event instanceof IrcEvent.Disconnected) {
      activeBackendByServer.remove(sid, backend);
      return;
    }
    if (event instanceof IrcEvent.Connecting
        || event instanceof IrcEvent.Connected
        || event instanceof IrcEvent.Reconnecting
        || event instanceof IrcEvent.ConnectionReady) {
      activeBackendByServer.put(sid, backend);
    }
  }

  private void clearActiveOwnership(String sid) {
    if (sid.isEmpty()) return;
    activeBackendByServer.remove(sid);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
