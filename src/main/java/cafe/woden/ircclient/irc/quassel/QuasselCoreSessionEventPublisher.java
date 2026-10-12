package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Publishes connection notifications, phase diagnostics, and one disconnect event per session,
 * closing readiness before disconnect publication.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreSessionEventPublisher {
  private static final String DEFAULT_DISCONNECT_REASON = "Client requested disconnect";
  private static final String FEATURE_PHASE_PREFIX = "quassel-phase=";
  private static final String FEATURE_DETAIL_PREFIX = ";detail=";

  @NonNull private final Consumer<ServerIrcEvent> events;

  void emitConnecting(QuasselCoreSession session) {
    if (session == null) return;
    events.accept(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.Connecting(
                Instant.now(), session.connectedHost, session.connectedPort, session.initialNick)));
  }

  void emitConnected(QuasselCoreSession session) {
    if (session == null) return;
    events.accept(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.Connected(
                Instant.now(),
                session.connectedHost,
                session.connectedPort,
                session.nicks.lastObservedNick())));
  }

  void emitDisconnectedOnce(QuasselCoreSession session, String reason) {
    if (session == null) return;
    session.readiness.close();
    if (!session.disconnectedEmitted.compareAndSet(false, true)) return;
    events.accept(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.Disconnected(Instant.now(), normalizeDisconnectReason(reason))));
  }

  static String normalizeDisconnectReason(String reason) {
    String value = Objects.toString(reason, "").trim();
    if (value.isEmpty()) return DEFAULT_DISCONNECT_REASON;
    return value;
  }

  static String renderProbeSource(QuasselCoreProtocolProbe.ProbeSelection probe) {
    return "quassel-probe protocol="
        + QuasselCoreProtocolProbe.protocolLabel(probe.protocolType())
        + " proto-features="
        + QuasselCoreProtocolProbe.hex16(probe.protocolFeatures())
        + " conn-features="
        + QuasselCoreProtocolProbe.hex8(probe.connectionFeatures());
  }

  void emitConnectionPhase(QuasselCoreSession session, String phase, String detail) {
    if (session == null) return;
    String phaseToken = Objects.toString(phase, "").trim();
    if (phaseToken.isEmpty()) return;
    String source = FEATURE_PHASE_PREFIX + phaseToken;
    String detailToken = Objects.toString(detail, "").trim();
    if (!detailToken.isEmpty()) {
      source = source + FEATURE_DETAIL_PREFIX + detailToken.replace('\n', ' ').replace('\r', ' ');
    }
    events.accept(
        new ServerIrcEvent(
            session.serverId, new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), source)));
  }
}
