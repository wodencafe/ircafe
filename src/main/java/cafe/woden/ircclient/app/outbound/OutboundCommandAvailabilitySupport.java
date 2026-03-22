package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared availability and help messaging support for outbound command capabilities. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class OutboundCommandAvailabilitySupport {

  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;

  public String featureUnavailableMessage(String serverId, String fallbackMessage) {
    return backendCapabilityPolicy.featureUnavailableMessage(serverId, fallbackMessage);
  }

  public String helpAvailabilitySuffix(String serverId, boolean available, String fallbackReason) {
    if (available) {
      return "";
    }

    String reason = backendCapabilityPolicy.unavailableReasonForHelp(serverId, fallbackReason);
    if (reason == null || reason.isBlank()) {
      return "";
    }
    return " (unavailable: " + reason + ")";
  }
}
