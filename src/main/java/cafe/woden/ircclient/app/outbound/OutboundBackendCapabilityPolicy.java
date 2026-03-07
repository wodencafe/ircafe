package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.IrcBackendAvailabilityPort;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Backend capability facade used by outbound command services. */
@Component
final class OutboundBackendCapabilityPolicy {

  private final CommandTargetPolicy commandTargetPolicy;
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry;
  private final IrcBackendAvailabilityPort backendAvailability;

  OutboundBackendCapabilityPolicy(
      CommandTargetPolicy commandTargetPolicy,
      OutboundBackendFeatureRegistry outboundBackendFeatureRegistry,
      @Qualifier("ircClientService") IrcBackendAvailabilityPort backendAvailability) {
    this.commandTargetPolicy = Objects.requireNonNull(commandTargetPolicy, "commandTargetPolicy");
    this.outboundBackendFeatureRegistry =
        Objects.requireNonNull(outboundBackendFeatureRegistry, "outboundBackendFeatureRegistry");
    this.backendAvailability = Objects.requireNonNull(backendAvailability, "backendAvailability");
  }

  IrcProperties.Server.Backend backendForServer(String serverId) {
    return commandTargetPolicy.backendForServer(serverId);
  }

  boolean supportsSemanticUpload(String serverId) {
    return supportsSemanticUpload(backendForServer(serverId));
  }

  boolean supportsSemanticUpload(IrcProperties.Server.Backend backend) {
    return outboundBackendFeatureRegistry.adapterFor(backend).supportsSemanticUpload();
  }

  boolean supportsQuasselCoreCommands(String serverId) {
    return supportsQuasselCoreCommands(backendForServer(serverId));
  }

  boolean supportsQuasselCoreCommands(IrcProperties.Server.Backend backend) {
    return outboundBackendFeatureRegistry.adapterFor(backend).supportsQuasselCoreCommands();
  }

  String backendAvailabilityReason(String serverId) {
    try {
      return Objects.toString(backendAvailability.backendAvailabilityReason(serverId), "").trim();
    } catch (Exception ignored) {
      return "";
    }
  }

  String unavailableReasonForHelp(String serverId, String fallbackReason) {
    String backendReason = backendAvailabilityReason(serverId);
    if (!backendReason.isEmpty()) return backendReason;
    return Objects.toString(fallbackReason, "").trim();
  }

  String featureUnavailableMessage(String serverId, String fallbackMessage) {
    String backendReason = backendAvailabilityReason(serverId);
    if (!backendReason.isEmpty()) return ensureTerminalPunctuation(backendReason);
    return Objects.toString(fallbackMessage, "").trim();
  }

  private static String ensureTerminalPunctuation(String message) {
    String text = Objects.toString(message, "").trim();
    if (text.isEmpty()) return "";
    char last = text.charAt(text.length() - 1);
    if (last == '.' || last == '!' || last == '?') return text;
    return text + ".";
  }
}
