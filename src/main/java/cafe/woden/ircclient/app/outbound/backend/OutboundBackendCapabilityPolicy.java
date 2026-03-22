package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Backend capability facade used by outbound command services. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class OutboundBackendCapabilityPolicy {

  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry;

  @NonNull
  @Qualifier("ircNegotiatedFeaturePort")
  private final IrcNegotiatedFeaturePort irc;

  @NonNull
  @Qualifier("ircClientService")
  private final IrcBackendAvailabilityPort backendAvailability;

  public IrcProperties.Server.Backend backendForServer(String serverId) {
    return commandTargetPolicy.backendForServer(serverId);
  }

  public boolean supportsSemanticUpload(String serverId) {
    return supportsSemanticUpload(backendForServer(serverId));
  }

  public boolean supportsSemanticUpload(IrcProperties.Server.Backend backend) {
    return outboundBackendFeatureRegistry.adapterFor(backend).supportsSemanticUpload();
  }

  public boolean supportsQuasselCoreCommands(String serverId) {
    return supportsQuasselCoreCommands(backendForServer(serverId));
  }

  public boolean supportsQuasselCoreCommands(IrcProperties.Server.Backend backend) {
    return outboundBackendFeatureRegistry.adapterFor(backend).supportsQuasselCoreCommands();
  }

  public boolean supportsReadMarker(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsReadMarker(irc, sid);
  }

  public boolean supportsMonitor(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsMonitor(irc, sid);
  }

  public boolean supportsLabeledResponse(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsLabeledResponse(irc, sid);
  }

  public boolean supportsMultiline(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsMultiline(irc, sid);
  }

  public boolean supportsDraftReply(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsDraftReply(irc, sid);
  }

  public boolean supportsDraftReact(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsDraftReact(irc, sid);
  }

  public boolean supportsDraftUnreact(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsDraftUnreact(irc, sid);
  }

  public boolean supportsMessageEdit(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsMessageEdit(irc, sid);
  }

  public boolean supportsMessageRedaction(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendForServer(sid))
        .supportsMessageRedaction(irc, sid);
  }

  public String backendAvailabilityReason(String serverId) {
    try {
      return Objects.toString(backendAvailability.backendAvailabilityReason(serverId), "").trim();
    } catch (Exception ignored) {
      return "";
    }
  }

  public String unavailableReasonForHelp(String serverId, String fallbackReason) {
    String backendReason = backendAvailabilityReason(serverId);
    if (!backendReason.isEmpty()) return backendReason;
    return Objects.toString(fallbackReason, "").trim();
  }

  public String featureUnavailableMessage(String serverId, String fallbackMessage) {
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
