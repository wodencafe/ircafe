package cafe.woden.ircclient.ui.servertree.query;

import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.ServerTreeEdtExecutor;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides channel-related tree queries and mutations with EDT-safe execution. */
public final class ServerTreeChannelQueryService {
  private static final Logger log = LoggerFactory.getLogger(ServerTreeChannelQueryService.class);

  private final ServerTreeEdtExecutor edtExecutor;
  private final ServerTreeTargetSnapshotProvider targetSnapshotProvider;
  private final ServerTreeChannelStateCoordinator channelStateCoordinator;
  private final Function<String, String> normalizeServerId;

  public ServerTreeChannelQueryService(
      ServerTreeEdtExecutor edtExecutor,
      ServerTreeTargetSnapshotProvider targetSnapshotProvider,
      ServerTreeChannelStateCoordinator channelStateCoordinator,
      Function<String, String> normalizeServerId) {
    this.edtExecutor = Objects.requireNonNull(edtExecutor, "edtExecutor");
    this.targetSnapshotProvider =
        Objects.requireNonNull(targetSnapshotProvider, "targetSnapshotProvider");
    this.channelStateCoordinator =
        Objects.requireNonNull(channelStateCoordinator, "channelStateCoordinator");
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
  }

  public List<String> openChannelsForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return List.of();
    return edtExecutor.read(
        () -> targetSnapshotProvider.snapshotOpenChannelsForServer(sid),
        List.of(),
        ex -> log.debug("[ircafe] open channel snapshot failed for server={}", sid, ex));
  }

  public List<ServerTreeDockable.ManagedChannelEntry> managedChannelsForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return List.of();
    return edtExecutor.read(
        () -> channelStateCoordinator.snapshotManagedChannelsForServer(sid),
        List.of(),
        ex -> log.debug("[ircafe] managed channel snapshot failed for server={}", sid, ex));
  }

  public ServerTreeDockable.ChannelSortMode channelSortModeForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return ServerTreeDockable.ChannelSortMode.CUSTOM;
    return edtExecutor.read(
        () -> channelStateCoordinator.channelSortModeForServer(sid),
        ServerTreeDockable.ChannelSortMode.CUSTOM,
        ex -> log.debug("[ircafe] channel sort mode snapshot failed for server={}", sid, ex));
  }

  public void setChannelSortModeForServer(
      String serverId, ServerTreeDockable.ChannelSortMode channelSortMode) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;
    ServerTreeDockable.ChannelSortMode next =
        channelSortMode == null ? ServerTreeDockable.ChannelSortMode.CUSTOM : channelSortMode;
    edtExecutor.write(() -> channelStateCoordinator.setChannelSortModeForServer(sid, next));
  }

  public void setChannelCustomOrderForServer(String serverId, List<String> channels) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;
    List<String> requested = channels == null ? List.of() : List.copyOf(channels);
    edtExecutor.write(() -> channelStateCoordinator.setChannelCustomOrderForServer(sid, requested));
  }

  private String normalize(String serverId) {
    return Objects.toString(normalizeServerId.apply(serverId), "").trim();
  }
}
