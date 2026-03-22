package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared monitor command target resolution, transport, and fallback support. */
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundMonitorCommandSupport {

  @NonNull private final IrcClientService irc;
  @NonNull private final UiPort ui;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final MonitorFallbackPort monitorFallbackPort;
  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;

  MonitorCommandContext resolveContextOrNull() {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef fallback = (active != null) ? active : targetCoordinator.safeStatusTarget();
    String serverId = Objects.toString(fallback.serverId(), "").trim();
    if (serverId.isEmpty()) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(monitor)", "Select a server first.");
      return null;
    }

    TargetRef statusTarget = new TargetRef(serverId, "status");
    TargetRef monitorTarget = TargetRef.monitorGroup(serverId);
    ui.ensureTargetExists(statusTarget);
    ui.ensureTargetExists(monitorTarget);
    return new MonitorCommandContext(serverId, statusTarget, monitorTarget);
  }

  boolean isFallbackActive(String serverId) {
    return monitorFallbackPort.isFallbackActive(serverId);
  }

  int negotiatedChunkSize(String serverId, int defaultChunkSize) {
    int chunkSize = irc.negotiatedMonitorLimit(serverId);
    return chunkSize > 0 ? chunkSize : defaultChunkSize;
  }

  void appendStatus(TargetRef target, String text) {
    ui.appendStatus(target, "(monitor)", text);
  }

  void sendMonitorRaw(
      CompositeDisposable disposables,
      MonitorCommandContext context,
      String line,
      boolean requireConnection) {
    String serverId = Objects.toString(context.serverId(), "").trim();
    String rawLine = Objects.toString(line, "").trim();
    if (serverId.isEmpty() || rawLine.isEmpty()) return;
    if (containsCrlf(rawLine)) {
      appendStatus(context.statusTarget(), "Refusing to send multi-line /monitor input.");
      return;
    }

    if (!ensureConnected(serverId, context.statusTarget(), requireConnection)) {
      return;
    }

    if (!backendCapabilityPolicy.supportsMonitor(serverId)) {
      if (isFallbackActive(serverId)) {
        requestFallbackRefresh(serverId, context.statusTarget(), false);
      } else {
        appendStatus(
            context.statusTarget(),
            backendCapabilityPolicy.featureUnavailableMessage(
                serverId, "MONITOR capability is unavailable on this server."));
      }
      return;
    }

    appendStatus(context.monitorTarget(), "→ " + rawLine);
    disposables.add(
        irc.sendRaw(serverId, rawLine)
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        context.statusTarget(), "(monitor-error)", String.valueOf(err))));
  }

  void requestFallbackRefresh(String serverId, TargetRef statusTarget, boolean requireConnection) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    if (!ensureConnected(sid, statusTarget, requireConnection)) {
      return;
    }
    monitorFallbackPort.requestImmediateRefresh(sid);
  }

  private boolean ensureConnected(
      String serverId, TargetRef statusTarget, boolean requireConnection) {
    if (connectionCoordinator.isConnected(serverId)) {
      return true;
    }
    if (requireConnection) {
      ui.appendStatus(statusTarget, "(conn)", "Not connected");
    }
    return false;
  }

  private static boolean containsCrlf(String s) {
    if (s == null || s.isEmpty()) return false;
    return s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
  }
}

record MonitorCommandContext(String serverId, TargetRef statusTarget, TargetRef monitorTarget) {}
