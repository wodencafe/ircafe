package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles outbound /markread command flow and read-marker help messaging. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundReadMarkerCommandService implements OutboundHelpContributor {

  @NonNull private final IrcReadMarkerPort readMarkerPort;
  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  @NonNull private final OutboundCommandAvailabilitySupport outboundCommandAvailabilitySupport;
  @NonNull private final OutboundConnectionStatusSupport outboundConnectionStatusSupport;
  @NonNull private final UiPort ui;
  @NonNull private final TargetCoordinator targetCoordinator;

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
    if (!outboundConnectionStatusSupport.ensureConnectedStatusOnly(at)) {
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
            + outboundCommandAvailabilitySupport.helpAvailabilitySuffix(
                sid, available, "requires negotiated read-marker or draft/read-marker"));
  }

  private boolean isReadMarkerSupportedForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return backendCapabilityPolicy.supportsReadMarker(sid);
  }

  private String featureUnavailableMessage(String serverId, String fallback) {
    return outboundCommandAvailabilitySupport.featureUnavailableMessage(serverId, fallback);
  }
}
