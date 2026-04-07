package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Shared chathistory availability support for outbound commands and history loading. */
@Component
@ApplicationLayer
public final class Ircv3ChatHistoryFeatureSupport implements Ircv3FeatureAvailabilitySupport {

  private static final String FEATURE_ID = "chathistory";
  private static final String REQUIREMENT_HINT =
      "requires negotiated draft/chathistory or chathistory";
  private static final String NEGOTIATION_UNAVAILABLE_MESSAGE =
      "chathistory is not negotiated on this server.";
  private static final String REMOTE_HISTORY_UNAVAILABLE_MESSAGE =
      "remote history is not available on this server.";

  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  private final IrcNegotiatedFeaturePort ircNegotiatedFeaturePort;
  private final IrcBouncerPlaybackPort bouncerPlaybackPort;

  public Ircv3ChatHistoryFeatureSupport(IrcNegotiatedFeaturePort ircNegotiatedFeaturePort) {
    this(null, ircNegotiatedFeaturePort, null);
  }

  public Ircv3ChatHistoryFeatureSupport(
      IrcNegotiatedFeaturePort ircNegotiatedFeaturePort,
      IrcBouncerPlaybackPort bouncerPlaybackPort) {
    this(null, ircNegotiatedFeaturePort, bouncerPlaybackPort);
  }

  @Autowired
  public Ircv3ChatHistoryFeatureSupport(
      OutboundBackendCapabilityPolicy backendCapabilityPolicy,
      @Qualifier("ircNegotiatedFeaturePort") IrcNegotiatedFeaturePort ircNegotiatedFeaturePort,
      @Qualifier("ircClientService") IrcBouncerPlaybackPort bouncerPlaybackPort) {
    this.backendCapabilityPolicy = backendCapabilityPolicy;
    this.ircNegotiatedFeaturePort = ircNegotiatedFeaturePort;
    this.bouncerPlaybackPort = bouncerPlaybackPort;
  }

  @Override
  public String featureId() {
    return FEATURE_ID;
  }

  @Override
  public boolean isAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      return false;
    }
    return ircNegotiatedFeaturePort != null && ircNegotiatedFeaturePort.isChatHistoryAvailable(sid);
  }

  public boolean isRemoteHistoryAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      return false;
    }
    if (isAvailable(sid)) {
      return true;
    }
    return isZncPlaybackAvailable(sid);
  }

  public boolean isZncPlaybackAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      return false;
    }
    return bouncerPlaybackPort != null && bouncerPlaybackPort.isZncPlaybackAvailable(sid);
  }

  @Override
  public String requirementHint() {
    return REQUIREMENT_HINT;
  }

  public String negotiationUnavailableMessage() {
    return NEGOTIATION_UNAVAILABLE_MESSAGE;
  }

  public String remoteHistoryUnavailableMessage() {
    return REMOTE_HISTORY_UNAVAILABLE_MESSAGE;
  }

  public String unavailableMessage(String serverId) {
    if (backendCapabilityPolicy != null) {
      return backendCapabilityPolicy.featureUnavailableMessage(
          serverId, negotiationUnavailableMessage());
    }
    return negotiationUnavailableMessage();
  }

  public String unavailableReasonForHelp(String serverId) {
    if (backendCapabilityPolicy != null) {
      return backendCapabilityPolicy.unavailableReasonForHelp(serverId, requirementHint());
    }
    return requirementHint();
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
