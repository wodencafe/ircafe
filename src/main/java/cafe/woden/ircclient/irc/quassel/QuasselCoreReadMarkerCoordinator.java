package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistorySelectors;
import cafe.woden.ircclient.irc.ircv3.Ircv3ReadMarkerCommandBuilder;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Coordinates native marker observations with session buffers and exact history timestamps. */
final class QuasselCoreReadMarkerCoordinator {
  interface SessionPort {
    String currentNick(int networkId);

    String historyTarget(BufferInfoValue buffer, String from);

    String qualifyTarget(String target, int networkId);

    void observeTargetNetwork(String target, int networkId);

    void emit(IrcEvent.ReadMarkerObserved event);
  }

  private final QuasselCoreSession session;
  private final SessionPort port;

  QuasselCoreReadMarkerCoordinator(QuasselCoreSession session, SessionPort port) {
    this.session = Objects.requireNonNull(session, "session");
    this.port = Objects.requireNonNull(port, "port");
  }

  void observeSync(String slotName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    Instant now = Instant.now();
    for (var update : QuasselCoreBufferSyncerParser.parseReadMarkers(slotName, values)) {
      observeMarker(update.bufferId(), update.msgId(), now);
    }
  }

  void observeHistory(String target, long messageId, Instant at) {
    session.history.observe(target, messageId, at);
    Integer bufferId = session.pendingReadMarkers.takeBufferForMessage(messageId);
    if (bufferId != null) {
      observeMarker(bufferId, messageId, at);
    }
  }

  private void observeMarker(int bufferId, long markerMsgId, Instant at) {
    if (bufferId < 0 || markerMsgId <= 0L) return;
    // Core init-data arrives before backlog. Keep the native ID until its timestamp is known.
    session.pendingReadMarkers.forgetBuffer(bufferId);
    BufferInfoValue bufferInfo = session.buffers.get(bufferId);
    if (bufferInfo == null) {
      session.pendingReadMarkers.defer(bufferId, markerMsgId);
      return;
    }

    int networkId = bufferInfo.networkId();
    String from = port.currentNick(networkId);
    if (from.isBlank()) {
      from = "server";
    }
    String target = port.historyTarget(bufferInfo, from);
    if (target.isBlank()) {
      target = port.qualifyTarget(Objects.toString(bufferInfo.bufferName(), "").trim(), networkId);
    }
    if (target.isBlank()) return;

    port.observeTargetNetwork(target, networkId);
    Instant observedAt = at == null ? Instant.now() : at;
    long resolvedEpochMs = session.history.exactTimestampForMsgId(target, markerMsgId);
    if (resolvedEpochMs <= 0L) {
      session.pendingReadMarkers.defer(bufferId, markerMsgId);
      return;
    }
    String marker =
        Ircv3ChatHistorySelectors.TIMESTAMP_PREFIX
            + Ircv3ReadMarkerCommandBuilder.formatTimestamp(Instant.ofEpochMilli(resolvedEpochMs));
    port.emit(new IrcEvent.ReadMarkerObserved(observedAt, from, target, marker));
  }
}
