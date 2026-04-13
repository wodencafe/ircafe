package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared message-redaction availability support for outbound and transcript actions. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class Ircv3MessageRedactionFeatureSupport implements Ircv3FeatureAvailabilitySupport {

  private static final String FEATURE_ID = "message-redaction";
  private static final String REQUIREMENT_HINT =
      "requires negotiated draft/message-redaction or message-redaction";
  private static final String NEGOTIATION_UNAVAILABLE_MESSAGE =
      "message-redaction is not negotiated on this server.";

  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;

  @Override
  public String featureId() {
    return FEATURE_ID;
  }

  @Override
  public boolean isAvailable(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      return false;
    }
    return backendCapabilityPolicy.supportsMessageRedaction(sid);
  }

  @Override
  public String requirementHint() {
    return REQUIREMENT_HINT;
  }

  public String negotiationUnavailableMessage() {
    return NEGOTIATION_UNAVAILABLE_MESSAGE;
  }
}
