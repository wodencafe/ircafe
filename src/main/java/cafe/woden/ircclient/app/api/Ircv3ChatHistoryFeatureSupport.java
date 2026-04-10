package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Shared chathistory availability support for outbound commands and history loading. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class Ircv3ChatHistoryFeatureSupport implements Ircv3FeatureAvailabilitySupport {

  private static final String FEATURE_ID = "chathistory";
  private static final String REQUIREMENT_HINT =
      "requires negotiated draft/chathistory or chathistory";
  private static final String NEGOTIATION_UNAVAILABLE_MESSAGE =
      "chathistory is not negotiated on this server.";
  private static final String REMOTE_HISTORY_UNAVAILABLE_MESSAGE =
      "remote history is not available on this server.";

  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;

  @Qualifier("ircNegotiatedFeaturePort")
  @NonNull
  private final IrcNegotiatedFeaturePort ircNegotiatedFeaturePort;

  @Qualifier("ircClientService")
  @NonNull
  private final IrcBouncerPlaybackPort bouncerPlaybackPort;

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
    return ircNegotiatedFeaturePort.isChatHistoryAvailable(sid);
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
    return bouncerPlaybackPort.isZncPlaybackAvailable(sid);
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
    return backendCapabilityPolicy.featureUnavailableMessage(
        serverId, negotiationUnavailableMessage());
  }

  public String unavailableReasonForHelp(String serverId) {
    return backendCapabilityPolicy.unavailableReasonForHelp(serverId, requirementHint());
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
