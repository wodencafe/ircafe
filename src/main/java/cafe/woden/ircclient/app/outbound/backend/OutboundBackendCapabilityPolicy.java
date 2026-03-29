package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.NonNull;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Backend capability facade used by outbound command services. */
@Component
@ApplicationLayer
public final class OutboundBackendCapabilityPolicy {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry;

  @NonNull private final IrcNegotiatedFeaturePort irc;

  @NonNull private final IrcBackendAvailabilityPort backendAvailability;

  @NonNull private final AvailableBackendIdsPort backendMetadata;

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
            backendMetadata, OutboundBackendCapabilityPolicy::defaultBackendMetadata);
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
        defaultBackendMetadata());
  }

  @Deprecated(forRemoval = false)
  public IrcProperties.Server.Backend backendForServer(String serverId) {
    return commandTargetPolicy.backendForServer(serverId);
  }

  public String backendIdForServer(String serverId) {
    return commandTargetPolicy.backendIdForServer(serverId);
  }

  public boolean supportsSemanticUpload(String serverId) {
    return supportsSemanticUploadByBackendId(backendIdForServer(serverId));
  }

  @Deprecated(forRemoval = false)
  public boolean supportsSemanticUpload(IrcProperties.Server.Backend backend) {
    return supportsSemanticUploadByBackendId(
        backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public boolean supportsQuasselCoreCommands(String serverId) {
    return supportsQuasselCoreCommandsByBackendId(backendIdForServer(serverId));
  }

  @Deprecated(forRemoval = false)
  public boolean supportsQuasselCoreCommands(IrcProperties.Server.Backend backend) {
    return supportsQuasselCoreCommandsByBackendId(
        backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public boolean persistsJoinedChannelsLocally(String serverId) {
    return persistsJoinedChannelsLocallyByBackendId(backendIdForServer(serverId));
  }

  @Deprecated(forRemoval = false)
  public boolean persistsJoinedChannelsLocally(IrcProperties.Server.Backend backend) {
    return persistsJoinedChannelsLocallyByBackendId(
        backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
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
      return decorateBackendReason(
          sid, Objects.toString(backendAvailability.backendAvailabilityReason(sid), "").trim());
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

  private String decorateBackendReason(String serverId, String reason) {
    String text = Objects.toString(reason, "").trim();
    if (text.isEmpty()) return "";
    String backendId = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendIdForServer(serverId));
    String backendLabel = backendDisplayLabel(backendId);
    if (backendLabel.isEmpty() || mentionsBackend(text, backendLabel, backendId)) {
      return text;
    }
    return backendLabel + ": " + text;
  }

  private String backendDisplayLabel(String backendId) {
    String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
    String displayName =
        Objects.toString(backendMetadata.backendDisplayName(normalized), "").trim();
    String label = displayName.isEmpty() ? normalized : displayName;
    if (label.isEmpty()) return "";
    return label.toLowerCase(Locale.ROOT).endsWith("backend") ? label : label + " backend";
  }

  private static boolean mentionsBackend(String text, String backendLabel, String backendId) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains(" backend")) {
      return true;
    }
    if (!Objects.toString(backendLabel, "").isBlank()
        && lower.contains(backendLabel.toLowerCase(Locale.ROOT))) {
      return true;
    }
    return !Objects.toString(backendId, "").isBlank()
        && lower.contains(backendId.toLowerCase(Locale.ROOT));
  }

  private static AvailableBackendIdsPort defaultBackendMetadata() {
    return new AvailableBackendIdsPort() {
      @Override
      public List<String> availableBackendIds() {
        return List.of();
      }

      @Override
      public String backendDisplayName(String backendId) {
        String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
        return BACKEND_DESCRIPTORS
            .descriptorForId(normalized)
            .map(descriptor -> Objects.toString(descriptor.displayName(), "").trim())
            .orElse(normalized);
      }
    };
  }
}
