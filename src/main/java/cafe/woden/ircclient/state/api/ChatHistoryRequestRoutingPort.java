package cafe.woden.ircclient.state.api;

import cafe.woden.ircclient.model.TargetRef;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for explicit CHATHISTORY request routing. */
@ApplicationLayer
public interface ChatHistoryRequestRoutingPort {

  enum QueryMode {
    BEFORE,
    LATEST,
    BETWEEN,
    AROUND
  }

  record PendingRequest(
      TargetRef originTarget,
      Instant requestedAt,
      int limit,
      String selector,
      QueryMode queryMode) {
    public PendingRequest {
      requestedAt = (requestedAt == null) ? Instant.now() : requestedAt;
      selector = Objects.toString(selector, "").trim();
      if (limit < 0) limit = 0;
      queryMode = (queryMode == null) ? QueryMode.BEFORE : queryMode;
    }
  }

  void remember(
      String serverId,
      String target,
      TargetRef originTarget,
      int limit,
      String selector,
      Instant requestedAt);

  void remember(
      String serverId,
      String target,
      TargetRef originTarget,
      int limit,
      String selector,
      Instant requestedAt,
      QueryMode queryMode);

  PendingRequest consumeIfFresh(String serverId, String target, Duration maxAge);

  void clearServer(String serverId);
}
