package cafe.woden.ircclient.app.outbound;

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
 * <p>Matrix room IDs ({@code !roomId:server}) are only treated as channel-like on Matrix-backed
 * servers.
 */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class CommandTargetPolicy {

  @NonNull private final ServerCatalog serverCatalog;

  public IrcProperties.Server.Backend backendForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) {
      return IrcProperties.Server.Backend.IRC;
    }
    Optional<IrcProperties.Server> configuredServer =
        Optional.ofNullable(serverCatalog.find(sid)).orElse(Optional.empty());
    return configuredServer
        .map(IrcProperties.Server::backend)
        .orElse(IrcProperties.Server.Backend.IRC);
  }

  public boolean isMatrixBackendServer(String serverId) {
    return backendForServer(serverId) == IrcProperties.Server.Backend.MATRIX;
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

  public static boolean looksLikeMatrixRoomId(String token) {
    String value = normalize(token);
    if (!value.startsWith("!")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
