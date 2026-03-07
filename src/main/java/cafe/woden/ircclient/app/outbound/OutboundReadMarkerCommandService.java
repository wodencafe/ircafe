package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcReadMarkerPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Handles outbound /markread command flow and read-marker help messaging. */
@Component
final class OutboundReadMarkerCommandService implements OutboundHelpContributor {

  private final IrcReadMarkerPort readMarkerPort;
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;

  OutboundReadMarkerCommandService(
      IrcReadMarkerPort readMarkerPort,
      OutboundBackendCapabilityPolicy backendCapabilityPolicy,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator) {
    this.readMarkerPort = Objects.requireNonNull(readMarkerPort, "readMarkerPort");
    this.backendCapabilityPolicy =
        Objects.requireNonNull(backendCapabilityPolicy, "backendCapabilityPolicy");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
  }

  @Override
  public void appendGeneralHelp(TargetRef out) {
    appendMarkReadHelp(out);
  }

  @Override
  public Map<String, Consumer<TargetRef>> topicHelpHandlers() {
    return Map.of("markread", this::appendMarkReadHelp);
  }

  void handleMarkRead(CompositeDisposable disposables) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(markread)", "Select a target first.");
      return;
    }
    if (at.isStatus()) {
      ui.appendStatus(at, "(markread)", "Select a channel or query first.");
      return;
    }
    if (at.isUiOnly()) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(markread)",
          "This view does not support /markread.");
      return;
    }
    TargetRef status = new TargetRef(at.serverId(), "status");
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (!backendCapabilityPolicy.supportsReadMarker(at.serverId())) {
      ui.appendStatus(
          status,
          "(markread)",
          featureUnavailableMessage(
              at.serverId(), "read-marker is not negotiated on this server."));
      return;
    }

    Instant now = Instant.now();
    long nowMs = now.toEpochMilli();
    ui.setReadMarker(at, nowMs);
    ui.clearUnread(at);

    disposables.add(
        readMarkerPort
            .sendReadMarker(at.serverId(), at.target(), now)
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(markread-error)", String.valueOf(err))));
  }

  private void appendMarkReadHelp(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    String sid = target.serverId();
    boolean available = isReadMarkerSupportedForServer(sid);
    ui.appendStatus(
        target,
        "(help)",
        "/markread"
            + availabilitySuffix(
                available,
                unavailableReasonForHelp(
                    sid, "requires negotiated read-marker or draft/read-marker")));
  }

  private boolean isReadMarkerSupportedForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return backendCapabilityPolicy.supportsReadMarker(sid);
  }

  private static String availabilitySuffix(boolean available, String unavailableReason) {
    if (available) return "";
    String reason = unavailableReason == null ? "" : unavailableReason.trim();
    if (reason.isEmpty()) return "";
    return " (unavailable: " + reason + ")";
  }

  private String featureUnavailableMessage(String serverId, String fallback) {
    return backendCapabilityPolicy.featureUnavailableMessage(serverId, fallback);
  }

  private String unavailableReasonForHelp(String serverId, String fallback) {
    return backendCapabilityPolicy.unavailableReasonForHelp(serverId, fallback);
  }
}
