package cafe.woden.ircclient.ui.servertree.query;

import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.ServerTreeEdtExecutor;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Provides channel-related tree queries and mutations with EDT-safe execution. */
@Component
@RequiredArgsConstructor
public final class ServerTreeChannelQueryService {
  private static final Logger log = LoggerFactory.getLogger(ServerTreeChannelQueryService.class);

  @NonNull private final ServerTreeEdtExecutor edtExecutor;

  public interface Context {
    String normalizeServerId(String serverId);

    List<String> snapshotOpenChannelsForServer(String serverId);

    List<ServerTreeDockable.ManagedChannelEntry> snapshotManagedChannelsForServer(String serverId);

    ServerTreeDockable.ChannelSortMode channelSortModeForServer(String serverId);

    void setChannelSortModeForServer(
        String serverId, ServerTreeDockable.ChannelSortMode channelSortMode);

    void setChannelCustomOrderForServer(String serverId, List<String> channels);
  }

  public static Context context(
      ServerTreeTargetSnapshotProvider targetSnapshotProvider,
      ServerTreeTargetSnapshotProvider.Context targetSnapshotProviderContext,
      ServerTreeChannelStateCoordinator channelStateCoordinator,
      Function<String, String> normalizeServerId) {
    Objects.requireNonNull(targetSnapshotProvider, "targetSnapshotProvider");
    Objects.requireNonNull(targetSnapshotProviderContext, "targetSnapshotProviderContext");
    Objects.requireNonNull(channelStateCoordinator, "channelStateCoordinator");
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    return new Context() {
      @Override
      public String normalizeServerId(String serverId) {
        return normalizeServerId.apply(serverId);
      }

      @Override
      public List<String> snapshotOpenChannelsForServer(String serverId) {
        return targetSnapshotProvider.snapshotOpenChannelsForServer(
            targetSnapshotProviderContext, serverId);
      }

      @Override
      public List<ServerTreeDockable.ManagedChannelEntry> snapshotManagedChannelsForServer(
          String serverId) {
        return channelStateCoordinator.snapshotManagedChannelsForServer(serverId);
      }

      @Override
      public ServerTreeDockable.ChannelSortMode channelSortModeForServer(String serverId) {
        return channelStateCoordinator.channelSortModeForServer(serverId);
      }

      @Override
      public void setChannelSortModeForServer(
          String serverId, ServerTreeDockable.ChannelSortMode channelSortMode) {
        channelStateCoordinator.setChannelSortModeForServer(serverId, channelSortMode);
      }

      @Override
      public void setChannelCustomOrderForServer(String serverId, List<String> channels) {
        channelStateCoordinator.setChannelCustomOrderForServer(serverId, channels);
      }
    };
  }

  public List<String> openChannelsForServer(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalize(in, serverId);
    if (sid.isEmpty()) return List.of();
    return edtExecutor.read(
        () -> in.snapshotOpenChannelsForServer(sid),
        List.of(),
        ex -> log.debug("[ircafe] open channel snapshot failed for server={}", sid, ex));
  }

  public List<ServerTreeDockable.ManagedChannelEntry> managedChannelsForServer(
      Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalize(in, serverId);
    if (sid.isEmpty()) return List.of();
    return edtExecutor.read(
        () -> in.snapshotManagedChannelsForServer(sid),
        List.of(),
        ex -> log.debug("[ircafe] managed channel snapshot failed for server={}", sid, ex));
  }

  public ServerTreeDockable.ChannelSortMode channelSortModeForServer(
      Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalize(in, serverId);
    if (sid.isEmpty()) return ServerTreeDockable.ChannelSortMode.CUSTOM;
    return edtExecutor.read(
        () -> in.channelSortModeForServer(sid),
        ServerTreeDockable.ChannelSortMode.CUSTOM,
        ex -> log.debug("[ircafe] channel sort mode snapshot failed for server={}", sid, ex));
  }

  public void setChannelSortModeForServer(
      Context context, String serverId, ServerTreeDockable.ChannelSortMode channelSortMode) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalize(in, serverId);
    if (sid.isEmpty()) return;
    ServerTreeDockable.ChannelSortMode next =
        channelSortMode == null ? ServerTreeDockable.ChannelSortMode.CUSTOM : channelSortMode;
    edtExecutor.write(() -> in.setChannelSortModeForServer(sid, next));
  }

  public void setChannelCustomOrderForServer(
      Context context, String serverId, List<String> channels) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalize(in, serverId);
    if (sid.isEmpty()) return;
    List<String> requested = channels == null ? List.of() : List.copyOf(channels);
    edtExecutor.write(() -> in.setChannelCustomOrderForServer(sid, requested));
  }

  private static String normalize(Context context, String serverId) {
    return Objects.toString(context.normalizeServerId(serverId), "").trim();
  }
}
