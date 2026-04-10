package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Shared multiline availability and negotiated-limit reasoning for outbound planning. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class Ircv3MultilineFeatureSupport implements Ircv3FeatureAvailabilitySupport {

  private static final String FEATURE_ID = "multiline";
  private static final String REQUIREMENT_HINT = "requires negotiated draft/multiline or multiline";
  private static final String NEGOTIATION_UNAVAILABLE_MESSAGE =
      "IRCv3 multiline is not negotiated on this server.";

  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;

  @Qualifier("ircNegotiatedFeaturePort")
  @NonNull
  private final IrcNegotiatedFeaturePort ircNegotiatedFeaturePort;

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
    if (backendCapabilityPolicy != null) {
      return backendCapabilityPolicy.supportsMultiline(sid);
    }
    return ircNegotiatedFeaturePort != null && ircNegotiatedFeaturePort.isMultilineAvailable(sid);
  }

  @Override
  public String requirementHint() {
    return REQUIREMENT_HINT;
  }

  public String negotiationUnavailableMessage() {
    return NEGOTIATION_UNAVAILABLE_MESSAGE;
  }

  public long negotiatedMaxBytes(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || ircNegotiatedFeaturePort == null) {
      return 0L;
    }
    return Math.max(0L, ircNegotiatedFeaturePort.negotiatedMultilineMaxBytes(sid));
  }

  public int negotiatedMaxLines(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || ircNegotiatedFeaturePort == null) {
      return 0;
    }
    return Math.max(0, ircNegotiatedFeaturePort.negotiatedMultilineMaxLines(sid));
  }

  public String unavailableOrLimitReason(String serverId, int lineCount, long payloadUtf8Bytes) {
    String sid = normalizeServerId(serverId);
    String backendUnavailableReason =
        backendCapabilityPolicy == null
            ? ""
            : backendCapabilityPolicy.featureUnavailableMessage(sid, "");
    if (!backendUnavailableReason.isBlank()) {
      return backendUnavailableReason;
    }

    if (!isAvailable(sid)) {
      return negotiationUnavailableMessage();
    }

    int maxLines = negotiatedMaxLines(sid);
    if (maxLines > 0 && lineCount > maxLines) {
      return "Message has "
          + lineCount
          + " lines; negotiated multiline max-lines is "
          + maxLines
          + ".";
    }

    long maxBytes = negotiatedMaxBytes(sid);
    if (maxBytes > 0L && payloadUtf8Bytes > maxBytes) {
      return "Message is "
          + payloadUtf8Bytes
          + " UTF-8 bytes; negotiated multiline max-bytes is "
          + maxBytes
          + ".";
    }

    return "";
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
