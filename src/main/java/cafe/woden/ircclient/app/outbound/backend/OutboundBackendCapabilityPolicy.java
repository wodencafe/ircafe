package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendAvailabilityReasonFormatter;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.Objects;
import lombok.NonNull;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Backend capability facade used by outbound command services. */
@Component
@ApplicationLayer
public final class OutboundBackendCapabilityPolicy {
  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry;

  @NonNull private final IrcNegotiatedFeaturePort irc;

  @NonNull private final IrcBackendAvailabilityPort backendAvailability;

  @NonNull private final AvailableBackendIdsPort backendMetadata;

  @Autowired
  public OutboundBackendCapabilityPolicy(
      CommandTargetPolicy commandTargetPolicy,
      OutboundBackendFeatureRegistry outboundBackendFeatureRegistry,
      @Qualifier("ircNegotiatedFeaturePort") IrcNegotiatedFeaturePort irc,
      @Qualifier("ircClientService") IrcBackendAvailabilityPort backendAvailability,
      AvailableBackendIdsPort backendMetadata) {
    this.commandTargetPolicy = Objects.requireNonNull(commandTargetPolicy, "commandTargetPolicy");
    this.outboundBackendFeatureRegistry =
        Objects.requireNonNull(outboundBackendFeatureRegistry, "outboundBackendFeatureRegistry");
    this.irc = Objects.requireNonNull(irc, "irc");
    this.backendAvailability = Objects.requireNonNull(backendAvailability, "backendAvailability");
    this.backendMetadata =
        Objects.requireNonNullElseGet(
            backendMetadata, BackendAvailabilityReasonFormatter::builtInsBackendMetadata);
  }

  @Deprecated(forRemoval = false)
  public OutboundBackendCapabilityPolicy(
      CommandTargetPolicy commandTargetPolicy,
      OutboundBackendFeatureRegistry outboundBackendFeatureRegistry,
      IrcNegotiatedFeaturePort irc,
      IrcBackendAvailabilityPort backendAvailability) {
    this(
        commandTargetPolicy,
        outboundBackendFeatureRegistry,
        irc,
        backendAvailability,
        BackendAvailabilityReasonFormatter.builtInsBackendMetadata());
  }

  public String backendIdForServer(String serverId) {
    return commandTargetPolicy.backendIdForServer(serverId);
  }

  public boolean supportsSemanticUpload(String serverId) {
    return supportsSemanticUploadByBackendId(backendIdForServer(serverId));
  }

  public boolean supportsQuasselCoreCommands(String serverId) {
    return supportsQuasselCoreCommandsByBackendId(backendIdForServer(serverId));
  }

  public boolean persistsJoinedChannelsLocally(String serverId) {
    return persistsJoinedChannelsLocallyByBackendId(backendIdForServer(serverId));
  }

  public boolean supportsSemanticUploadByBackendId(String backendId) {
    return outboundBackendFeatureRegistry.adapterFor(backendId).supportsSemanticUpload();
  }

  public boolean supportsQuasselCoreCommandsByBackendId(String backendId) {
    return outboundBackendFeatureRegistry.adapterFor(backendId).supportsQuasselCoreCommands();
  }

  public boolean persistsJoinedChannelsLocallyByBackendId(String backendId) {
    return outboundBackendFeatureRegistry.adapterFor(backendId).persistsJoinedChannelsLocally();
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
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return "";
    try {
      return BackendAvailabilityReasonFormatter.decorate(
          backendIdForServer(sid),
          Objects.toString(backendAvailability.backendAvailabilityReason(sid), "").trim(),
          backendMetadata);
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
