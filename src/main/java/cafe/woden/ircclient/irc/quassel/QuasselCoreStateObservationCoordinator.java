package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkConnected;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkSnapshotEvent;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Coordinates network/buffer observations, membership, and removal across live session catalogs.
 * Does not retain sessions or own background tasks.
 */
final class QuasselCoreStateObservationCoordinator {
  private static final int BUFFER_STATUS = 0x01;

  private final QuasselCoreTargetResolver targets;
  private final Consumer<QuasselCoreNetworkSnapshotEvent> networkObserved;

  QuasselCoreStateObservationCoordinator(
      QuasselCoreTargetResolver targets,
      Consumer<QuasselCoreNetworkSnapshotEvent> networkObserved) {
    this.targets = Objects.requireNonNull(targets, "targets");
    this.networkObserved = Objects.requireNonNull(networkObserved, "networkObserved");
  }

  void observeTargetNetwork(
      QuasselCoreSession session, String target, int networkId, boolean preferObservedNetwork) {
    if (session == null || networkId < 0) return;
    observeNetwork(session, networkId, "");
    if (preferObservedNetwork) {
      session.targetNetworkHints.observe(target, networkId);
    } else {
      session.targetNetworkHints.seed(target, networkId, () -> firstKnownNetworkId(session));
    }
  }

  void observeFullState(QuasselCoreSession session, int networkId, Map<?, ?> state) {
    observeState(session, networkId, state);
    observeCapabilities(session, networkId, state);
    observeMonitorSupport(session, networkId, state);
  }

  void applyBufferSnapshot(QuasselCoreSession session, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    ArrayList<BufferInfoValue> found = new ArrayList<>();
    for (Object value : values) {
      collectBufferInfos(value, found);
    }
    if (found.isEmpty()) return;
    for (BufferInfoValue info : found) {
      if (info == null || info.bufferId() < 0) continue;
      BufferInfoValue merged = session.buffers.merge(info);
      observeBuffer(session, merged);
    }
  }

  private static void collectBufferInfos(Object raw, List<BufferInfoValue> out) {
    if (raw == null || out == null) return;
    if (raw instanceof BufferInfoValue info) {
      out.add(info);
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectBufferInfos(value, out);
      }
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      for (Object value : map.values()) {
        collectBufferInfos(value, out);
      }
    }
  }

  void observeBuffer(QuasselCoreSession session, BufferInfoValue merged) {
    observeNetwork(session, merged.networkId(), "");
    observeTargetNetwork(session, merged.bufferName(), merged.networkId(), false);
    observeConnectedBufferMembership(session, merged);
  }

  private void observeCapabilities(QuasselCoreSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    session.features.observeCapabilities(resolvedNetworkId, stateMap);
  }

  private void observeMonitorSupport(
      QuasselCoreSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    session.features.observeMonitor(resolvedNetworkId, stateMap);
  }

  BufferInfoValue resolveBuffer(QuasselCoreSession session, BufferInfoValue incoming) {
    if (incoming == null) {
      int networkId = primaryNetworkId(session);
      return new BufferInfoValue(-1, networkId, BUFFER_STATUS, -1, "");
    }

    if (incoming.bufferId() < 0) {
      return incoming;
    }

    BufferInfoValue merged = session.buffers.resolve(incoming);
    observeNetwork(session, merged.networkId(), "");
    return merged;
  }

  void observeNetwork(QuasselCoreSession session, int networkId, String networkName) {
    if (session == null || networkId < 0) return;
    session.networks.observe(networkId, networkName);
    emitSnapshot(session, "observe-known-network");
  }

  void forgetNetwork(QuasselCoreSession session, int networkId) {
    if (session == null || networkId < 0) return;
    session.networks.forget(networkId);
    session.nicks.forgetNetwork(networkId);
    session.features.removeNetwork(networkId);
    session.buffers.forgetNetwork(networkId, session.pendingReadMarkers::forgetBuffer);
    session.targetNetworkHints.forgetNetwork(networkId);
    session.membership.forgetNetwork(networkId);
    emitSnapshot(session, "forget-known-network");
  }

  void observeState(QuasselCoreSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null) return;
    Map<String, Object> merged = session.networks.observeState(networkId, stateMap);
    if (merged == null) return;
    session.membership.reconcileNetwork(
        networkId,
        parseNetworkConnected(merged),
        session.buffers.values(),
        (channel, id) -> targets.qualifyTarget(session, channel, id),
        (target, id) -> observeTargetNetwork(session, target, id, true));
    emitSnapshot(session, "observe-network-state");
  }

  private void observeConnectedBufferMembership(
      QuasselCoreSession session, BufferInfoValue bufferInfo) {
    if (session == null || bufferInfo == null) return;
    int networkId = bufferInfo.networkId();
    if (networkId < 0) return;
    Map<String, Object> state = session.networks.state(networkId);
    if (!parseNetworkConnected(state)) return;
    session.membership.observeBuffer(
        bufferInfo,
        Instant.now(),
        (channel, id) -> targets.qualifyTarget(session, channel, id),
        (target, id) -> observeTargetNetwork(session, target, id, true));
  }

  List<QuasselCoreNetworkSummary> snapshot(QuasselCoreSession session) {
    return session == null
        ? List.of()
        : session.networks.snapshot(session.authResult.get(), session.buffers.values());
  }

  private void emitSnapshot(QuasselCoreSession session, String source) {
    if (session == null) return;
    String sid = Objects.toString(session.serverId, "").trim();
    if (sid.isEmpty()) return;
    List<QuasselCoreNetworkSummary> snapshot = snapshot(session);
    networkObserved.accept(new QuasselCoreNetworkSnapshotEvent(sid, snapshot, source));
  }

  private static int primaryNetworkId(QuasselCoreSession session) {
    return session == null
        ? -1
        : session.networks.primaryNetworkId(session.authResult.get(), session.buffers.values());
  }

  private static int firstKnownNetworkId(QuasselCoreSession session) {
    return session == null
        ? -1
        : session.networks.firstKnownNetworkId(session.authResult.get(), session.buffers.values());
  }
}
