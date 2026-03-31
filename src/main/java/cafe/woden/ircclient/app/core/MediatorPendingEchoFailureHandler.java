package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import java.time.Instant;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Fails pending echoed messages when connectivity is lost. */
@Component
@ApplicationLayer
public class MediatorPendingEchoFailureHandler {

  private final UiPort ui;
  private final PendingEchoMessagePort pendingEchoMessageState;

  public MediatorPendingEchoFailureHandler(
      UiPort ui, PendingEchoMessagePort pendingEchoMessageState) {
    this.ui = ui;
    this.pendingEchoMessageState = pendingEchoMessageState;
  }

  public void failPendingEchoesForServer(String serverId, String reason) {
    if (serverId == null || serverId.isBlank()) {
      return;
    }
    Instant now = Instant.now();
    for (PendingEchoMessagePort.PendingOutboundChat pending :
        pendingEchoMessageState.drainServer(serverId)) {
      TargetRef target = pending.target();
      if (target == null) {
        continue;
      }
      ui.failPendingOutgoingChat(
          target, pending.pendingId(), now, pending.fromNick(), pending.text(), reason);
    }
  }
}
