package cafe.woden.ircclient.app.outbound.identity;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared away-command decision, routing, and rollback support. */
@ApplicationLayer
@RequiredArgsConstructor
final class AwayCommandSupport {

  @NonNull private final IrcClientService irc;
  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final AwayRoutingPort awayRoutingState;

  void handleAway(CompositeDisposable disposables, String message) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(away)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(active.serverId(), "status");
    AwayCommandRequest request = resolveRequest(active.serverId(), message);
    if (!connectionCoordinator.isConnected(active.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    awayRoutingState.rememberOrigin(active.serverId(), active);

    String previousReason = awayRoutingState.getLastReason(active.serverId());
    awayRoutingState.setLastReason(active.serverId(), request.clear() ? null : request.toSend());

    disposables.add(
        irc.setAway(active.serverId(), request.toSend())
            .subscribe(
                () -> {
                  awayRoutingState.setAway(active.serverId(), !request.clear());
                  ui.appendStatus(
                      status,
                      "(away)",
                      request.clear() ? "Away cleared" : ("Away set: " + request.toSend()));
                },
                err -> {
                  awayRoutingState.setLastReason(active.serverId(), previousReason);
                  ui.appendError(status, "(away-error)", String.valueOf(err));
                }));
  }

  private AwayCommandRequest resolveRequest(String serverId, String message) {
    String trimmed = Objects.toString(message, "").trim();
    boolean explicitClear =
        "-".equals(trimmed) || "off".equalsIgnoreCase(trimmed) || "clear".equalsIgnoreCase(trimmed);

    if (trimmed.isEmpty()) {
      if (awayRoutingState.isAway(serverId)) {
        return new AwayCommandRequest(true, "");
      }
      return new AwayCommandRequest(false, "Gone for now.");
    }

    if (explicitClear) {
      return new AwayCommandRequest(true, "");
    }

    return new AwayCommandRequest(false, trimmed);
  }

  private record AwayCommandRequest(boolean clear, String toSend) {}
}
