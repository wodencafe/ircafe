package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound /markread command flow and read-marker help messaging. */
@Component
final class OutboundReadMarkerCommandService implements OutboundHelpContributor {

  private final IrcClientService irc;
  private final IrcBackendAvailabilityPort backendAvailability;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;

  OutboundReadMarkerCommandService(
      IrcClientService irc,
      @Qualifier("ircClientService") IrcBackendAvailabilityPort backendAvailability,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.backendAvailability = Objects.requireNonNull(backendAvailability, "backendAvailability");
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
    if (!irc.isReadMarkerAvailable(at.serverId())) {
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
        irc.sendReadMarker(at.serverId(), at.target(), now)
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
    try {
      return irc.isReadMarkerAvailable(sid);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String availabilitySuffix(boolean available, String unavailableReason) {
    if (available) return "";
    String reason = unavailableReason == null ? "" : unavailableReason.trim();
    if (reason.isEmpty()) return "";
    return " (unavailable: " + reason + ")";
  }

  private String featureUnavailableMessage(String serverId, String fallback) {
    String backendReason = normalizedBackendAvailabilityReason(serverId);
    if (!backendReason.isEmpty()) return ensureTerminalPunctuation(backendReason);
    return fallback;
  }

  private String unavailableReasonForHelp(String serverId, String fallback) {
    String backendReason = normalizedBackendAvailabilityReason(serverId);
    if (!backendReason.isEmpty()) return backendReason;
    return fallback;
  }

  private String normalizedBackendAvailabilityReason(String serverId) {
    try {
      return Objects.toString(backendAvailability.backendAvailabilityReason(serverId), "").trim();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String ensureTerminalPunctuation(String message) {
    String text = Objects.toString(message, "").trim();
    if (text.isEmpty()) return "";
    char last = text.charAt(text.length() - 1);
    if (last == '.' || last == '!' || last == '?') return text;
    return text + ".";
  }
}
