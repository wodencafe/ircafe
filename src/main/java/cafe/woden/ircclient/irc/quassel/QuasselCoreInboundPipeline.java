package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SignalProxyMessage;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Assembles one session's inbound translators and dispatches frames on the caller's thread. The
 * service owns the read task and session teardown; this pipeline creates no subscriptions.
 */
final class QuasselCoreInboundPipeline {
  private final QuasselCoreSession session;
  private final QuasselCoreTargetResolver targets;
  private final QuasselCoreStateObservationCoordinator stateObservations;
  private final QuasselIrcv3RuntimeSupport ircv3RuntimeSupport;
  private final Consumer<ServerIrcEvent> events;
  private final QuasselCoreSignalProxyDispatcher dispatcher;

  QuasselCoreInboundPipeline(
      QuasselCoreSession session,
      QuasselCoreTargetResolver targets,
      QuasselCoreStateObservationCoordinator stateObservations,
      QuasselIrcv3RuntimeSupport ircv3RuntimeSupport,
      Consumer<ServerIrcEvent> events) {
    this.session = Objects.requireNonNull(session, "session");
    this.targets = Objects.requireNonNull(targets, "targets");
    this.stateObservations = Objects.requireNonNull(stateObservations, "stateObservations");
    this.ircv3RuntimeSupport = Objects.requireNonNull(ircv3RuntimeSupport, "ircv3RuntimeSupport");
    this.events = Objects.requireNonNull(events, "events");
    QuasselCoreReadMarkerCoordinator markers = readMarkerCoordinator();
    QuasselCoreSyncDispatcher sync = syncDispatcher(markers);
    QuasselCoreRpcDispatcher rpc = rpcDispatcher(inboundCoordinator(markers));
    this.dispatcher = new QuasselCoreSignalProxyDispatcher(session, rpc::dispatch, sync::dispatch);
  }

  void dispatch(SignalProxyMessage message) throws IOException {
    dispatcher.dispatch(message);
  }

  private void emit(IrcEvent event) {
    events.accept(new ServerIrcEvent(session.serverId, event));
  }

  private QuasselCoreInboundMessageCoordinator inboundCoordinator(
      QuasselCoreReadMarkerCoordinator markers) {
    return new QuasselCoreInboundMessageCoordinator(
        session,
        markers,
        targets,
        ircv3RuntimeSupport,
        new QuasselCoreInboundMessageCoordinator.SessionPort() {
          @Override
          public QuasselCoreDatastreamCodec.BufferInfoValue resolveBuffer(
              QuasselCoreDatastreamCodec.BufferInfoValue incoming) {
            return stateObservations.resolveBuffer(session, incoming);
          }

          @Override
          public String currentNick(int networkId) {
            return session.nicks.forNetwork(networkId);
          }

          @Override
          public boolean isSelfNick(String nick, int networkId) {
            return session.nicks.isSelf(nick, networkId);
          }

          @Override
          public void observeTargetNetwork(String target, int networkId) {
            stateObservations.observeTargetNetwork(session, target, networkId, true);
          }

          @Override
          public void observeNick(int networkId, Instant at, String nick) {
            session.nicks.observe(networkId, nick, at);
          }

          @Override
          public void emit(IrcEvent event) {
            QuasselCoreInboundPipeline.this.emit(event);
          }
        });
  }

  private QuasselCoreRpcDispatcher rpcDispatcher(QuasselCoreInboundMessageCoordinator inbound) {
    QuasselCoreNetworkLifecycleTranslator networks =
        new QuasselCoreNetworkLifecycleTranslator(
            session.serverId,
            session.networks::claimCreatedName,
            (networkId, name) -> stateObservations.observeNetwork(session, networkId, name),
            networkId -> stateObservations.forgetNetwork(session, networkId),
            (networkId, state) -> stateObservations.observeFullState(session, networkId, state));
    return new QuasselCoreRpcDispatcher(
        session,
        networks,
        new QuasselCoreRpcDispatcher.SessionPort() {
          @Override
          public void displayMessage(QuasselCoreDatastreamCodec.MessageValue message) {
            inbound.handle(message);
          }

          @Override
          public void observeBuffer(QuasselCoreDatastreamCodec.BufferInfoValue merged) {
            stateObservations.observeBuffer(session, merged);
          }

          @Override
          public void emit(IrcEvent event) {
            QuasselCoreInboundPipeline.this.emit(event);
          }
        });
  }

  private QuasselCoreSyncDispatcher syncDispatcher(QuasselCoreReadMarkerCoordinator markers) {
    return new QuasselCoreSyncDispatcher(
        session,
        networkSyncTranslator(),
        new QuasselCoreSyncDispatcher.SessionPort() {
          @Override
          public void applyBufferInfoSnapshot(List<Object> values) {
            stateObservations.applyBufferSnapshot(session, values);
          }

          @Override
          public void observeReadMarkers(String slotName, List<Object> values) {
            markers.observeSync(slotName, values);
          }

          @Override
          public void receiveBacklog(List<Object> values) {
            handleBacklogSync(markers, values);
          }

          @Override
          public void observeNetwork(int networkId, String name) {
            stateObservations.observeNetwork(session, networkId, name);
          }

          @Override
          public String qualifyTarget(String target, int networkId) {
            return targets.qualifyTarget(session, target, networkId);
          }

          @Override
          public void emit(IrcEvent event) {
            QuasselCoreInboundPipeline.this.emit(event);
          }
        });
  }

  private QuasselCoreNetworkSyncTranslator networkSyncTranslator() {
    return new QuasselCoreNetworkSyncTranslator(
        session.serverId,
        (networkId, name) -> stateObservations.observeNetwork(session, networkId, name),
        (networkId, state) -> stateObservations.observeState(session, networkId, state),
        (networkId, state) -> stateObservations.observeFullState(session, networkId, state),
        (networkId, nick) -> session.nicks.observe(networkId, nick, Instant.now()));
  }

  private QuasselCoreReadMarkerCoordinator readMarkerCoordinator() {
    return new QuasselCoreReadMarkerCoordinator(
        session,
        new QuasselCoreReadMarkerCoordinator.SessionPort() {
          @Override
          public String currentNick(int networkId) {
            return session.nicks.forNetwork(networkId);
          }

          @Override
          public String historyTarget(
              QuasselCoreDatastreamCodec.BufferInfoValue buffer, String from) {
            return targets.targetForBuffer(session, buffer, from);
          }

          @Override
          public String qualifyTarget(String target, int networkId) {
            return targets.qualifyTarget(session, target, networkId);
          }

          @Override
          public void observeTargetNetwork(String target, int networkId) {
            stateObservations.observeTargetNetwork(session, target, networkId, true);
          }

          @Override
          public void emit(IrcEvent.ReadMarkerObserved event) {
            QuasselCoreInboundPipeline.this.emit(event);
          }
        });
  }

  private void handleBacklogSync(QuasselCoreReadMarkerCoordinator markers, List<Object> values) {
    IrcEvent.ChatHistoryBatchReceived batch =
        session.backlog.sync(
            values,
            session.buffers::get,
            info -> stateObservations.resolveBuffer(session, info),
            (info, from) -> targets.targetForBuffer(session, info, from),
            (target, networkId) ->
                stateObservations.observeTargetNetwork(session, target, networkId, true),
            (message, entry) ->
                markers.observeHistory(entry.target(), message.messageId(), entry.at()));
    if (batch != null) emit(batch);
  }
}
