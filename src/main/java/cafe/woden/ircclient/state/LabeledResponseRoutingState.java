package cafe.woden.ircclient.state;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Adapts feature-owned labeled-response tracking to application chat-buffer routing. */
@Component
@ApplicationLayer
public class LabeledResponseRoutingState implements LabeledResponseRoutingPort {
  private final LabeledResponseRequestStore<RoutingContext> requests =
      new LabeledResponseRequestStore<>();

  @Override
  public void remember(
      String serverId,
      String label,
      TargetRef originTarget,
      String requestPreview,
      Instant startedAt) {
    String sid = LabeledResponseRoutingPort.normalizeServer(serverId);
    if (sid.isEmpty() && originTarget != null) {
      sid = LabeledResponseRoutingPort.normalizeServer(originTarget.serverId());
    }
    if (sid.isEmpty() || originTarget == null) return;
    TargetRef target =
        sid.equals(originTarget.serverId())
            ? originTarget
            : new TargetRef(sid, originTarget.target());
    requests.remember(
        sid,
        label,
        new RoutingContext(target, LabeledResponseRoutingPort.normalizePreview(requestPreview)),
        startedAt);
  }

  @Override
  public PendingLabeledRequest findIfFresh(String serverId, String label, Duration maxAge) {
    return toRoutingRequest(requests.findIfFresh(serverId, label, maxAge));
  }

  @Override
  public PendingLabeledRequest markOutcomeIfPending(
      String serverId, String label, Outcome outcome, Instant at) {
    LabeledResponseRequestStore.Outcome next =
        switch (outcome == null ? Outcome.PENDING : outcome) {
          case PENDING -> LabeledResponseRequestStore.Outcome.PENDING;
          case SUCCESS -> LabeledResponseRequestStore.Outcome.SUCCESS;
          case FAILURE -> LabeledResponseRequestStore.Outcome.FAILURE;
          case TIMEOUT -> LabeledResponseRequestStore.Outcome.TIMEOUT;
        };
    return toRoutingRequest(requests.markOutcomeIfPending(serverId, label, next, at));
  }

  @Override
  public List<TimedOutLabeledRequest> collectTimedOut(Duration timeout, int maxCount) {
    return requests.collectTimedOut(timeout, maxCount).stream()
        .map(
            request ->
                new TimedOutLabeledRequest(
                    request.serverId(),
                    request.label(),
                    toRoutingRequest(request.request()),
                    request.timedOutAt()))
        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
  }

  @Override
  public void clearServer(String serverId) {
    requests.clearServer(serverId);
  }

  private static PendingLabeledRequest toRoutingRequest(
      LabeledResponseRequestStore.Request<RoutingContext> request) {
    if (request == null) return null;
    Outcome outcome =
        switch (request.outcome()) {
          case PENDING -> Outcome.PENDING;
          case SUCCESS -> Outcome.SUCCESS;
          case FAILURE -> Outcome.FAILURE;
          case TIMEOUT -> Outcome.TIMEOUT;
        };
    return new PendingLabeledRequest(
        request.context().target(),
        request.context().preview(),
        request.startedAt(),
        outcome,
        request.outcomeAt());
  }

  private record RoutingContext(TargetRef target, String preview) {}
}
