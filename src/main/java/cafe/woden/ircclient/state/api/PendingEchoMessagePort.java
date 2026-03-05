package cafe.woden.ircclient.state.api;

import cafe.woden.ircclient.model.TargetRef;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for pending echo-message reconciliation state. */
@ApplicationLayer
public interface PendingEchoMessagePort {

  record PendingOutboundChat(
      String pendingId, TargetRef target, String fromNick, String text, Instant createdAt) {}

  PendingOutboundChat register(TargetRef target, String fromNick, String text, Instant createdAt);

  Optional<PendingOutboundChat> removeById(String pendingId);

  Optional<PendingOutboundChat> consumeByTargetAndText(
      TargetRef target, String fromNick, String text);

  Optional<PendingOutboundChat> consumeOldestByTarget(TargetRef target);

  Optional<PendingOutboundChat> consumePrivateFallback(
      String serverId, String fromNick, String text);

  List<PendingOutboundChat> drainServer(String serverId);

  List<PendingOutboundChat> collectTimedOut(Duration timeout, int maxCount, Instant now);
}
