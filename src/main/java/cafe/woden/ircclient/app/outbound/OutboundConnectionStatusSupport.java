package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared connection-status feedback for outbound command flows. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class OutboundConnectionStatusSupport {

  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;

  public boolean ensureConnected(TargetRef target) {
    return ensureConnected(target, true);
  }

  public boolean ensureConnectedStatusOnly(TargetRef target) {
    return ensureConnected(target, false);
  }

  public boolean ensureConnectedStatusOnly(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      return false;
    }
    return ensureConnected(new TargetRef(sid, "status"), false);
  }

  private boolean ensureConnected(TargetRef target, boolean mirrorToTarget) {
    if (target == null) {
      return false;
    }

    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) {
      return false;
    }

    if (connectionCoordinator.isConnected(sid)) {
      return true;
    }

    TargetRef status = new TargetRef(sid, "status");
    ui.appendStatus(status, "(conn)", "Not connected");
    if (mirrorToTarget && !target.isStatus()) {
      ui.appendStatus(target, "(conn)", "Not connected");
    }
    return false;
  }
}
