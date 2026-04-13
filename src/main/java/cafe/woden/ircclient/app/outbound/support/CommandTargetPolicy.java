package cafe.woden.ircclient.app.outbound.support;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendEditorProfileCatalog;
import cafe.woden.ircclient.app.api.BackendUiMode;
import cafe.woden.ircclient.app.outbound.backend.BackendExtensionCatalog;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Backend-aware target classification for outbound slash-command handling.
 *
 * <p>Matrix room IDs ({@code !roomId:server}) are only treated as channel-like on backends whose
 * profile opts into Matrix-style UI mode.
 */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class CommandTargetPolicy {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  @NonNull private final ServerCatalog serverCatalog;
  @NonNull private final BackendExtensionCatalog backendExtensionCatalog;
  @NonNull private final AvailableBackendIdsPort backendMetadata;

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

  public boolean persistsJoinedChannelsLocally(String serverId) {
    String backendId = backendIdForServer(serverId);
    return backendExtensionCatalog.featureAdapterFor(backendId).persistsJoinedChannelsLocally();
  }

  public boolean isChannelLikeTargetForServer(String serverId, String target) {
    String raw = normalize(target);
    if (raw.isEmpty()) return false;
    if (raw.startsWith("#") || raw.startsWith("&")) return true;
    return backendUiModeForServer(serverId) == BackendUiMode.MATRIX && looksLikeMatrixRoomId(raw);
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
    return BackendEditorProfileCatalog.from(backendMetadata)
        .uiModeForBackendId(backendIdForServer(serverId));
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
