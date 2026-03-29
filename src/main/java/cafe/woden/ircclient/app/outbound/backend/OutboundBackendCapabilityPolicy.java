package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
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
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

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

  public String backendIdForServer(String serverId) {
    return commandTargetPolicy.backendIdForServer(serverId);
  }

  public boolean supportsSemanticUpload(String serverId) {
    return supportsSemanticUploadById(backendIdForServer(serverId));
  }

  public boolean supportsSemanticUpload(IrcProperties.Server.Backend backend) {
    return supportsSemanticUploadById(backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public boolean supportsQuasselCoreCommands(String serverId) {
    return supportsQuasselCoreCommandsById(backendIdForServer(serverId));
  }

  public boolean supportsQuasselCoreCommands(IrcProperties.Server.Backend backend) {
    return supportsQuasselCoreCommandsById(
        backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public boolean persistsJoinedChannelsLocally(String serverId) {
    return persistsJoinedChannelsLocallyById(backendIdForServer(serverId));
  }

  public boolean persistsJoinedChannelsLocally(IrcProperties.Server.Backend backend) {
    return persistsJoinedChannelsLocallyById(
        backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public boolean supportsReadMarker(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
        .supportsReadMarker(irc, sid);
  }

  public boolean supportsMonitor(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
        .supportsMonitor(irc, sid);
  }

  public boolean supportsLabeledResponse(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
        .supportsLabeledResponse(irc, sid);
  }

  public boolean supportsMultiline(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
        .supportsMultiline(irc, sid);
  }

  public boolean supportsDraftReply(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
        .supportsDraftReply(irc, sid);
  }

  public boolean supportsDraftReact(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
        .supportsDraftReact(irc, sid);
  }

  public boolean supportsDraftUnreact(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
        .supportsDraftUnreact(irc, sid);
  }

  public boolean supportsMessageEdit(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
        .supportsMessageEdit(irc, sid);
  }

  public boolean supportsMessageRedaction(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return outboundBackendFeatureRegistry
        .adapterFor(backendIdForServer(sid))
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

  private boolean supportsSemanticUploadById(String backendId) {
    return outboundBackendFeatureRegistry.adapterFor(backendId).supportsSemanticUpload();
  }

  private boolean supportsQuasselCoreCommandsById(String backendId) {
    return outboundBackendFeatureRegistry.adapterFor(backendId).supportsQuasselCoreCommands();
  }

  private boolean persistsJoinedChannelsLocallyById(String backendId) {
    return outboundBackendFeatureRegistry.adapterFor(backendId).persistsJoinedChannelsLocally();
  }
}
