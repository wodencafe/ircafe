package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Shared message-redaction availability support for outbound and transcript actions. */
@Component
@ApplicationLayer
public final class Ircv3MessageRedactionFeatureSupport implements Ircv3FeatureAvailabilitySupport {

  private static final String FEATURE_ID = "message-redaction";
  private static final String REQUIREMENT_HINT =
      "requires negotiated draft/message-redaction or message-redaction";
  private static final String NEGOTIATION_UNAVAILABLE_MESSAGE =
      "message-redaction is not negotiated on this server.";

  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  private final IrcNegotiatedFeaturePort ircNegotiatedFeaturePort;

  public Ircv3MessageRedactionFeatureSupport(IrcNegotiatedFeaturePort ircNegotiatedFeaturePort) {
    this(null, ircNegotiatedFeaturePort);
  }

  @Autowired
  public Ircv3MessageRedactionFeatureSupport(
      OutboundBackendCapabilityPolicy backendCapabilityPolicy,
      IrcNegotiatedFeaturePort ircNegotiatedFeaturePort) {
    this.backendCapabilityPolicy = backendCapabilityPolicy;
    this.ircNegotiatedFeaturePort = ircNegotiatedFeaturePort;
  }

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
    if (backendCapabilityPolicy != null) {
      return backendCapabilityPolicy.supportsMessageRedaction(sid);
    }
    return ircNegotiatedFeaturePort != null
        && ircNegotiatedFeaturePort.isMessageRedactionAvailable(sid);
  }

  @Override
  public String requirementHint() {
    return REQUIREMENT_HINT;
  }

  public String negotiationUnavailableMessage() {
    return NEGOTIATION_UNAVAILABLE_MESSAGE;
  }
}
