package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Backend capability facade used by outbound command services. */
@Component
final class OutboundBackendCapabilityPolicy {

  private final CommandTargetPolicy commandTargetPolicy;
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry;
  private final IrcNegotiatedFeaturePort irc;
  private final IrcBackendAvailabilityPort backendAvailability;

  OutboundBackendCapabilityPolicy(
      CommandTargetPolicy commandTargetPolicy,
      OutboundBackendFeatureRegistry outboundBackendFeatureRegistry,
      @Qualifier("ircClientService") IrcNegotiatedFeaturePort irc,
      @Qualifier("ircClientService") IrcBackendAvailabilityPort backendAvailability) {
    this.commandTargetPolicy = Objects.requireNonNull(commandTargetPolicy, "commandTargetPolicy");
    this.outboundBackendFeatureRegistry =
        Objects.requireNonNull(outboundBackendFeatureRegistry, "outboundBackendFeatureRegistry");
    this.irc = Objects.requireNonNull(irc, "irc");
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

  boolean supportsReadMarker(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsReadMarker(irc, sid);
  }

  boolean supportsMonitor(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsMonitor(irc, sid);
  }

  boolean supportsLabeledResponse(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsLabeledResponse(irc, sid);
  }

  boolean supportsMultiline(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsMultiline(irc, sid);
  }

  boolean supportsDraftReply(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsDraftReply(irc, sid);
  }

  boolean supportsDraftReact(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsDraftReact(irc, sid);
  }

  boolean supportsDraftUnreact(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsDraftUnreact(irc, sid);
  }

  boolean supportsMessageEdit(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsMessageEdit(irc, sid);
  }

  boolean supportsMessageRedaction(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsMessageRedaction(irc, sid);
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

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
