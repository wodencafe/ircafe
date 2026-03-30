package cafe.woden.ircclient.app.outbound.support;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.api.BackendUiMode;
import cafe.woden.ircclient.app.api.BuiltInBackendEditorProfiles;
import cafe.woden.ircclient.app.outbound.backend.BackendExtensionCatalog;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.model.TargetRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Backend-aware target classification for outbound slash-command handling.
 *
 * <p>Matrix room IDs ({@code !roomId:server}) are only treated as channel-like on backends whose
 * profile opts into Matrix-style UI mode.
 */
@Component
@ApplicationLayer
public class CommandTargetPolicy {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  @NonNull private final ServerCatalog serverCatalog;
  private final BackendExtensionCatalog backendExtensionCatalog;
  private final Map<String, BackendUiMode> backendUiModesById;

  public CommandTargetPolicy(ServerCatalog serverCatalog) {
    this(serverCatalog, null, AvailableBackendIdsPort.builtInsOnly());
  }

  @Deprecated(forRemoval = false)
  public CommandTargetPolicy(
      ServerCatalog serverCatalog, BackendExtensionCatalog backendExtensionCatalog) {
    this(serverCatalog, backendExtensionCatalog, AvailableBackendIdsPort.builtInsOnly());
  }

  @Autowired
  public CommandTargetPolicy(
      ServerCatalog serverCatalog,
      BackendExtensionCatalog backendExtensionCatalog,
      AvailableBackendIdsPort backendMetadata) {
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    this.backendExtensionCatalog = backendExtensionCatalog;
    this.backendUiModesById = indexBackendUiModes(backendMetadata);
  }

  @Deprecated(forRemoval = false)
  public IrcProperties.Server.Backend backendForServer(String serverId) {
    return BACKEND_DESCRIPTORS.backendForId(backendIdForServer(serverId)).orElse(null);
  }

  public String backendIdForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) {
      return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
    }
    Optional<IrcProperties.Server> configuredServer =
        Optional.ofNullable(serverCatalog.find(sid)).orElse(Optional.empty());
    return configuredServer
        .map(IrcProperties.Server::backendId)
        .orElse(BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC));
  }

  public boolean isMatrixBackendServer(String serverId) {
    return backendUiModeForServer(serverId) == BackendUiMode.MATRIX;
  }

  public boolean persistsJoinedChannelsLocally(String serverId) {
    String backendId = backendIdForServer(serverId);
    if (backendExtensionCatalog == null) {
      return !BACKEND_DESCRIPTORS
          .idFor(IrcProperties.Server.Backend.QUASSEL_CORE)
          .equals(backendId);
    }
    return backendExtensionCatalog.featureAdapterFor(backendId).persistsJoinedChannelsLocally();
  }

  public boolean isChannelLikeTargetForServer(String serverId, String target) {
    String raw = normalize(target);
    if (raw.isEmpty()) return false;
    if (raw.startsWith("#") || raw.startsWith("&")) return true;
    return isMatrixBackendServer(serverId) && looksLikeMatrixRoomId(raw);
  }

  public boolean isChannelLikeTarget(TargetRef target) {
    if (target == null) return false;
    if (target.isChannel()) return true;
    return isChannelLikeTargetForServer(target.serverId(), target.target());
  }

  public String resolveChannelOrNull(TargetRef active, String explicitChannel) {
    String channel = normalize(explicitChannel);
    if (!channel.isEmpty()) {
      String sid = active == null ? "" : active.serverId();
      return isChannelLikeTargetForServer(sid, channel) ? channel : null;
    }
    return isChannelLikeTarget(active) ? active.target() : null;
  }

  public static boolean looksLikeMatrixRoomId(String token) {
    String value = normalize(token);
    if (!value.startsWith("!")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private BackendUiMode backendUiModeForServer(String serverId) {
    String backendId = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendIdForServer(serverId));
    return backendUiModesById.getOrDefault(backendId, BackendUiMode.IRC);
  }

  private static Map<String, BackendUiMode> indexBackendUiModes(
      AvailableBackendIdsPort backendMetadata) {
    AvailableBackendIdsPort metadata =
        Objects.requireNonNullElseGet(backendMetadata, AvailableBackendIdsPort::builtInsOnly);
    LinkedHashMap<String, BackendUiMode> indexed = new LinkedHashMap<>();
    for (BackendEditorProfileSpec profile : BuiltInBackendEditorProfiles.all()) {
      indexed.put(profile.backendId(), profile.uiMode());
    }
    for (BackendEditorProfileSpec profile :
        Objects.requireNonNullElse(
            metadata.availableBackendEditorProfiles(), List.<BackendEditorProfileSpec>of())) {
      if (profile == null) continue;
      indexed.put(profile.backendId(), profile.uiMode());
    }
    return Map.copyOf(indexed);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
