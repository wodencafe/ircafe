package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Owns one Core session's bounded observations and background task handles. */
final class QuasselCoreSession {
  private static final int MAX_NETWORK_IDENTITIES_PER_SESSION = 512;
  final String serverId;
  final String initialNick;
  final String connectedHost;
  final int connectedPort;

  final AtomicReference<Socket> socketRef = new AtomicReference<>();
  final AtomicReference<Disposable> readLoopTask = new AtomicReference<>();
  final AtomicReference<QuasselCoreProtocolProbe.ProbeSelection> probeSelection =
      new AtomicReference<>();
  final AtomicReference<QuasselCoreAuthHandshake.AuthResult> authResult = new AtomicReference<>();
  final QuasselCoreNickState nicks;
  final QuasselCoreNetworkCatalog networks;
  final QuasselCoreIdentityState identities;
  final QuasselCoreFeatureState features;
  final QuasselCoreBufferCatalog buffers = new QuasselCoreBufferCatalog();
  final QuasselCoreHistorySupport history = new QuasselCoreHistorySupport();
  final QuasselCorePendingReadMarkers pendingReadMarkers = new QuasselCorePendingReadMarkers();
  final QuasselCoreTargetNetworkHints targetNetworkHints = new QuasselCoreTargetNetworkHints();
  final QuasselCoreChannelMembership membership;
  final QuasselCoreLagTracker lag = new QuasselCoreLagTracker();
  final AtomicBoolean nativeReadMarkerSupportObserved = new AtomicBoolean(false);
  final QuasselCoreReadinessCoordinator readiness;
  final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
  final QuasselCoreBacklogTranslator backlog = new QuasselCoreBacklogTranslator();
  final AtomicReference<QuasselSessionPhase> phase =
      new AtomicReference<>(QuasselSessionPhase.TRANSPORT_CONNECTING);
  final AtomicReference<String> closeReason = new AtomicReference<>("Client requested disconnect");
  final QuasselCoreSignalProxySender outbound;
  final QuasselCoreNetworkCommandSender networkCommands;
  final QuasselCoreBufferCommandSender bufferCommands;
  final AtomicBoolean closeRequested = new AtomicBoolean(false);
  final AtomicBoolean disconnectedEmitted = new AtomicBoolean(false);

  QuasselCoreSession(
      String serverId,
      String nick,
      String connectedHost,
      int connectedPort,
      QuasselCoreSignalProxySender sender,
      Consumer<String> identityObserved,
      Consumer<IrcEvent> eventObserved) {
    this.outbound = new QuasselCoreSerializedSignalProxySender(sender);
    this.bufferCommands = new QuasselCoreBufferCommandSender(socketRef::get, outbound);
    this.networkCommands =
        new QuasselCoreNetworkCommandSender(serverId, nick, socketRef::get, outbound);
    this.membership = new QuasselCoreChannelMembership(eventObserved);
    this.readiness =
        new QuasselCoreReadinessCoordinator(
            () ->
                phase.get() == QuasselSessionPhase.SESSION_ESTABLISHED
                    && !closeRequested.get()
                    && socketRef.get() != null,
            eventObserved,
            RxVirtualSchedulers::io);
    this.features = new QuasselCoreFeatureState(MAX_NETWORK_IDENTITIES_PER_SESSION, eventObserved);
    this.identities =
        new QuasselCoreIdentityState(
            serverId, MAX_NETWORK_IDENTITIES_PER_SESSION, identityObserved);
    this.networks =
        new QuasselCoreNetworkCatalog(
            MAX_NETWORK_IDENTITIES_PER_SESSION, identityId -> identities.observe(identityId, ""));
    this.serverId = serverId;
    this.initialNick = nick;
    this.nicks =
        new QuasselCoreNickState(
            nick,
            () -> networks.primaryNetworkId(authResult.get(), buffers.values()),
            eventObserved);
    this.connectedHost = Objects.toString(connectedHost, "").trim();
    this.connectedPort = connectedPort;
  }

  /** Initialize observations before the service publishes SESSION_ESTABLISHED. */
  void initialize(
      QuasselCoreAuthHandshake.AuthResult auth,
      IntConsumer observeNetwork,
      Consumer<QuasselCoreDatastreamCodec.BufferInfoValue> seedBufferHint) {
    authResult.set(auth);
    buffers.clear();
    pendingReadMarkers.clear();
    nativeReadMarkerSupportObserved.set(false);
    buffers.loadInitial(auth.initialBuffers());
    targetNetworkHints.clear();
    networks.reset();
    identities.clear();
    nicks.clear();
    features.clear();
    if (auth.networkIds() != null) {
      for (Integer id : auth.networkIds()) {
        if (id != null) observeNetwork.accept(id);
      }
    }
    identities.initialize(auth.initialIdentities());
    int primaryNetworkId = networks.primaryNetworkId(auth, buffers.values());
    nicks.seedPrimaryNetwork(primaryNetworkId);
    for (QuasselCoreDatastreamCodec.BufferInfoValue initial : buffers.values()) {
      if (initial == null) continue;
      observeNetwork.accept(initial.networkId());
      seedBufferHint.accept(initial);
    }
    lag.clear();
    readiness.resetObservations();
    reconnectScheduled.set(false);
  }

  void clearObservedState() {
    lag.clear();
    buffers.clear();
    pendingReadMarkers.clear();
    nativeReadMarkerSupportObserved.set(false);
    history.clear();
    targetNetworkHints.clear();
    membership.clear();
    networks.clearMetadata();
    identities.clear();
    nicks.clear();
    features.clear();
  }

  void disposeReadLoopTask() {
    disposeTask(readLoopTask);
  }

  private static void disposeTask(AtomicReference<Disposable> taskRef) {
    Disposable task = taskRef.getAndSet(null);
    if (task == null || task.isDisposed()) return;
    try {
      task.dispose();
    } catch (Exception ignored) {
    }
  }

  enum QuasselSessionPhase {
    TRANSPORT_CONNECTING,
    TRANSPORT_CONNECTED,
    PROTOCOL_NEGOTIATED,
    AUTHENTICATING,
    SESSION_ESTABLISHED
  }
}
