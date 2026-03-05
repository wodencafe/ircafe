package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Public-facing channel interaction API composed from query/state coordinators. */
public final class ServerTreeChannelInteractionApi {
  private final ServerTreeChannelQueryService channelQueryService;
  private final ServerTreeChannelTargetOperations channelTargetOperations;
  private final ServerTreeChannelDisconnectStateManager channelDisconnectStateManager;
  private final ServerTreeUnreadStateCoordinator unreadStateCoordinator;
  private final ServerTreeTypingActivityManager typingActivityManager;

  public ServerTreeChannelInteractionApi(
      ServerTreeChannelQueryService channelQueryService,
      ServerTreeChannelTargetOperations channelTargetOperations,
      ServerTreeChannelDisconnectStateManager channelDisconnectStateManager,
      ServerTreeUnreadStateCoordinator unreadStateCoordinator,
      ServerTreeTypingActivityManager typingActivityManager) {
    this.channelQueryService = Objects.requireNonNull(channelQueryService, "channelQueryService");
    this.channelTargetOperations =
        Objects.requireNonNull(channelTargetOperations, "channelTargetOperations");
    this.channelDisconnectStateManager =
        Objects.requireNonNull(channelDisconnectStateManager, "channelDisconnectStateManager");
    this.unreadStateCoordinator =
        Objects.requireNonNull(unreadStateCoordinator, "unreadStateCoordinator");
    this.typingActivityManager =
        Objects.requireNonNull(typingActivityManager, "typingActivityManager");
  }

  public List<String> openChannelsForServer(String serverId) {
    return channelQueryService.openChannelsForServer(serverId);
  }

  public List<ServerTreeDockable.ManagedChannelEntry> managedChannelsForServer(String serverId) {
    return channelQueryService.managedChannelsForServer(serverId);
  }

  public ServerTreeDockable.ChannelSortMode channelSortModeForServer(String serverId) {
    return channelQueryService.channelSortModeForServer(serverId);
  }

  public void setChannelSortModeForServer(
      String serverId, ServerTreeDockable.ChannelSortMode mode) {
    channelQueryService.setChannelSortModeForServer(serverId, mode);
  }

  public void setChannelCustomOrderForServer(String serverId, List<String> channels) {
    channelQueryService.setChannelCustomOrderForServer(serverId, channels);
  }

  public void setCanEditChannelModes(BiPredicate<String, String> canEditChannelModes) {
    channelTargetOperations.setCanEditChannelModes(canEditChannelModes);
  }

  public void requestJoinChannel(TargetRef target) {
    channelTargetOperations.requestJoinChannel(target);
  }

  public void requestDisconnectChannel(TargetRef target) {
    channelTargetOperations.requestDisconnectChannel(target);
  }

  public void requestCloseChannel(TargetRef target) {
    channelTargetOperations.requestCloseChannel(target);
  }

  public void setChannelDisconnected(TargetRef ref, boolean disconnected, String warningReason) {
    channelDisconnectStateManager.setChannelDisconnected(ref, disconnected, warningReason);
  }

  public void clearChannelDisconnectedWarning(TargetRef ref) {
    channelDisconnectStateManager.clearChannelDisconnectedWarning(ref);
  }

  public boolean isChannelDisconnected(TargetRef ref) {
    return channelDisconnectStateManager.isChannelDisconnected(ref);
  }

  public boolean isChannelAutoReattach(TargetRef ref) {
    return channelTargetOperations.isChannelAutoReattach(ref);
  }

  public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
    channelTargetOperations.setChannelAutoReattach(ref, autoReattach);
  }

  public boolean isChannelPinned(TargetRef ref) {
    return channelTargetOperations.isChannelPinned(ref);
  }

  public void setChannelPinned(TargetRef ref, boolean pinned) {
    channelTargetOperations.setChannelPinned(ref, pinned);
  }

  public boolean isChannelMuted(TargetRef ref) {
    return channelTargetOperations.isChannelMuted(ref);
  }

  public void setChannelMuted(TargetRef ref, boolean muted) {
    channelTargetOperations.setChannelMuted(ref, muted);
    unreadStateCoordinator.onChannelMutedStateChanged(ref, muted);
  }

  public void markUnread(TargetRef ref) {
    unreadStateCoordinator.markUnread(ref);
  }

  public void markHighlight(TargetRef ref) {
    unreadStateCoordinator.markHighlight(ref);
  }

  public void clearUnread(TargetRef ref) {
    unreadStateCoordinator.clearUnread(ref);
  }

  public void markTypingActivity(TargetRef ref, String state) {
    typingActivityManager.markTypingActivity(ref, state);
  }
}
