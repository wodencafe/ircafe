package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerTreeChannelInteractionApiTest {

  @Test
  void delegatesChannelQueriesRequestsAndStateOperations() {
    ServerTreeChannelQueryService queryService = mock(ServerTreeChannelQueryService.class);
    ServerTreeChannelQueryService.Context queryServiceContext =
        mock(ServerTreeChannelQueryService.Context.class);
    ServerTreeChannelTargetOperations targetOperations =
        mock(ServerTreeChannelTargetOperations.class);
    ServerTreeChannelTargetOperations.Context targetOperationsContext =
        mock(ServerTreeChannelTargetOperations.Context.class);
    ServerTreeChannelDisconnectStateManager disconnectStateManager =
        mock(ServerTreeChannelDisconnectStateManager.class);
    ServerTreeUnreadStateCoordinator unreadStateCoordinator =
        mock(ServerTreeUnreadStateCoordinator.class);
    ServerTreeTypingActivityManager typingActivityManager =
        mock(ServerTreeTypingActivityManager.class);
    ServerTreeChannelInteractionApi api =
        new ServerTreeChannelInteractionApi(
            queryService,
            queryServiceContext,
            targetOperations,
            targetOperationsContext,
            disconnectStateManager,
            unreadStateCoordinator,
            typingActivityManager);

    TargetRef channelRef = new TargetRef("libera", "#ircafe");
    when(queryService.openChannelsForServer(queryServiceContext, "libera"))
        .thenReturn(List.of("#ircafe"));
    when(queryService.managedChannelsForServer(queryServiceContext, "libera"))
        .thenReturn(List.of(new ServerTreeDockable.ManagedChannelEntry("#ircafe", false, true, 1)));
    when(queryService.channelSortModeForServer(queryServiceContext, "libera"))
        .thenReturn(ServerTreeDockable.ChannelSortMode.ALPHABETICAL);
    when(disconnectStateManager.isChannelDisconnected(channelRef)).thenReturn(true);
    when(targetOperations.isChannelAutoReattach(targetOperationsContext, channelRef))
        .thenReturn(true);
    when(targetOperations.isChannelPinned(targetOperationsContext, channelRef)).thenReturn(false);
    when(targetOperations.isChannelMuted(targetOperationsContext, channelRef)).thenReturn(false);

    assertEquals(List.of("#ircafe"), api.openChannelsForServer("libera"));
    assertEquals(1, api.managedChannelsForServer("libera").size());
    assertEquals(
        ServerTreeDockable.ChannelSortMode.ALPHABETICAL, api.channelSortModeForServer("libera"));
    assertTrue(api.isChannelDisconnected(channelRef));
    assertTrue(api.isChannelAutoReattach(channelRef));
    assertFalse(api.isChannelPinned(channelRef));
    assertFalse(api.isChannelMuted(channelRef));

    api.setChannelSortModeForServer("libera", ServerTreeDockable.ChannelSortMode.CUSTOM);
    api.setChannelCustomOrderForServer("libera", List.of("#a", "#b"));
    api.requestJoinChannel(channelRef);
    api.requestDisconnectChannel(channelRef);
    api.requestCloseChannel(channelRef);
    api.setChannelDisconnected(channelRef, true, "test");
    api.clearChannelDisconnectedWarning(channelRef);
    api.setChannelAutoReattach(channelRef, true);
    api.setChannelPinned(channelRef, true);
    api.setChannelMuted(channelRef, true);
    api.markUnread(channelRef);
    api.markHighlight(channelRef);
    api.clearUnread(channelRef);
    api.markTypingActivity(channelRef, "active");

    verify(queryService)
        .setChannelSortModeForServer(
            queryServiceContext, "libera", ServerTreeDockable.ChannelSortMode.CUSTOM);
    verify(queryService)
        .setChannelCustomOrderForServer(queryServiceContext, "libera", List.of("#a", "#b"));
    verify(targetOperations).requestJoinChannel(targetOperationsContext, channelRef);
    verify(targetOperations).requestDisconnectChannel(targetOperationsContext, channelRef);
    verify(targetOperations).requestCloseChannel(targetOperationsContext, channelRef);
    verify(disconnectStateManager).setChannelDisconnected(channelRef, true, "test");
    verify(disconnectStateManager).clearChannelDisconnectedWarning(channelRef);
    verify(targetOperations).setChannelAutoReattach(targetOperationsContext, channelRef, true);
    verify(targetOperations).setChannelPinned(targetOperationsContext, channelRef, true);
    verify(targetOperations).setChannelMuted(targetOperationsContext, channelRef, true);
    verify(unreadStateCoordinator).onChannelMutedStateChanged(channelRef, true);
    verify(unreadStateCoordinator).markUnread(channelRef);
    verify(unreadStateCoordinator).markHighlight(channelRef);
    verify(unreadStateCoordinator).clearUnread(channelRef);
    verify(typingActivityManager).markTypingActivity(channelRef, "active");
  }
}
