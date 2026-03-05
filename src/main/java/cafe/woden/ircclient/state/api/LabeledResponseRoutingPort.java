package cafe.woden.ircclient.state.api;

import cafe.woden.ircclient.model.TargetRef;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for IRCv3 labeled-response correlation. */
@ApplicationLayer
public interface LabeledResponseRoutingPort {

  enum Outcome {
    PENDING,
    SUCCESS,
    FAILURE,
    TIMEOUT
  }

  record PreparedRawLine(String line, String label, boolean injected) {
    public PreparedRawLine {
      line = Objects.toString(line, "").trim();
      label = LabeledResponseRoutingPort.normalizeLabel(label);
    }
  }

  record PendingLabeledRequest(
      TargetRef originTarget,
      String requestPreview,
      Instant startedAt,
      Outcome outcome,
      Instant outcomeAt) {
    public PendingLabeledRequest {
      requestPreview = LabeledResponseRoutingPort.normalizePreview(requestPreview);
      startedAt = (startedAt == null) ? Instant.now() : startedAt;
      outcome = (outcome == null) ? Outcome.PENDING : outcome;
      if (outcome == Outcome.PENDING) outcomeAt = null;
      else outcomeAt = (outcomeAt == null) ? Instant.now() : outcomeAt;
    }

    public PendingLabeledRequest(TargetRef originTarget, String requestPreview, Instant startedAt) {
      this(originTarget, requestPreview, startedAt, Outcome.PENDING, null);
    }

    public boolean terminal() {
      return outcome != Outcome.PENDING;
    }

    public PendingLabeledRequest withOutcome(Outcome nextOutcome, Instant at) {
      Outcome normalized = (nextOutcome == null) ? Outcome.PENDING : nextOutcome;
      if (normalized == Outcome.PENDING) return this;
      Instant ts = (at == null) ? Instant.now() : at;
      return new PendingLabeledRequest(originTarget, requestPreview, startedAt, normalized, ts);
    }
  }

  record TimedOutLabeledRequest(
      String serverId, String label, PendingLabeledRequest request, Instant timedOutAt) {
    public TimedOutLabeledRequest {
      serverId = LabeledResponseRoutingPort.normalizeServer(serverId);
      label = LabeledResponseRoutingPort.normalizeLabel(label);
      timedOutAt = (timedOutAt == null) ? Instant.now() : timedOutAt;
    }
  }

  LabeledResponseRoutingPort.PreparedRawLine prepareOutgoingRaw(String serverId, String rawLine);

  String nextClientLabel(String serverId);

  void remember(
      String serverId,
      String label,
      TargetRef originTarget,
      String requestPreview,
      Instant startedAt);

  LabeledResponseRoutingPort.PendingLabeledRequest findIfFresh(
      String serverId, String label, Duration maxAge);

  LabeledResponseRoutingPort.PendingLabeledRequest markOutcomeIfPending(
      String serverId, String label, LabeledResponseRoutingPort.Outcome outcome, Instant at);

  List<LabeledResponseRoutingPort.TimedOutLabeledRequest> collectTimedOut(
      Duration timeout, int maxCount);

  void clearServer(String serverId);

  static String normalizeServer(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  static String normalizeLabel(String label) {
    return Objects.toString(label, "").trim();
  }

  static String normalizePreview(String preview) {
    String p = Objects.toString(preview, "").trim();
    if (p.isEmpty()) return "";
    p = p.replaceAll("\\s+", " ");
    int max = 220;
    if (p.length() > max) {
      p = p.substring(0, max - 1) + "...";
    }
    return p;
  }
}
