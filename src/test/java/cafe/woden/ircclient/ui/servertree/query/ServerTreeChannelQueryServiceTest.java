package cafe.woden.ircclient.ui.servertree.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.ServerTreeEdtExecutor;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import java.util.List;
import java.util.Objects;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ServerTreeChannelQueryServiceTest {

  @Test
  void openChannelsForServerReturnsSnapshotOrEmptyForBlankServerId() {
    ServerTreeTargetSnapshotProvider targetSnapshotProvider =
        mock(ServerTreeTargetSnapshotProvider.class);
    ServerTreeTargetSnapshotProvider.Context targetSnapshotProviderContext =
        mock(ServerTreeTargetSnapshotProvider.Context.class);
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        mock(ServerTreeChannelStateCoordinator.class);
    ServerTreeChannelQueryService service =
        new ServerTreeChannelQueryService(new ServerTreeEdtExecutor());
    ServerTreeChannelQueryService.Context context =
        ServerTreeChannelQueryService.context(
            targetSnapshotProvider,
            targetSnapshotProviderContext,
            channelStateCoordinator,
            id -> Objects.toString(id, "").trim());

    when(targetSnapshotProvider.snapshotOpenChannelsForServer(
            targetSnapshotProviderContext, "libera"))
        .thenReturn(List.of("#ircafe"));

    assertEquals(List.of("#ircafe"), service.openChannelsForServer(context, "libera"));
    assertTrue(service.openChannelsForServer(context, " ").isEmpty());

    verify(targetSnapshotProvider)
        .snapshotOpenChannelsForServer(targetSnapshotProviderContext, "libera");
  }

  @Test
  void managedChannelQueriesAndMutationsUseNormalizedServerIds() throws Exception {
    ServerTreeTargetSnapshotProvider targetSnapshotProvider =
        mock(ServerTreeTargetSnapshotProvider.class);
    ServerTreeTargetSnapshotProvider.Context targetSnapshotProviderContext =
        mock(ServerTreeTargetSnapshotProvider.Context.class);
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        mock(ServerTreeChannelStateCoordinator.class);
    ServerTreeChannelQueryService service =
        new ServerTreeChannelQueryService(new ServerTreeEdtExecutor());
    ServerTreeChannelQueryService.Context context =
        ServerTreeChannelQueryService.context(
            targetSnapshotProvider,
            targetSnapshotProviderContext,
            channelStateCoordinator,
            id -> Objects.toString(id, "").trim());

    when(channelStateCoordinator.snapshotManagedChannelsForServer("libera"))
        .thenReturn(List.of(new ServerTreeDockable.ManagedChannelEntry("#ircafe", false, true, 1)));
    when(channelStateCoordinator.channelSortModeForServer("libera"))
        .thenReturn(ServerTreeDockable.ChannelSortMode.ALPHABETICAL);

    assertEquals(1, service.managedChannelsForServer(context, " libera ").size());
    assertEquals(
        ServerTreeDockable.ChannelSortMode.ALPHABETICAL,
        service.channelSortModeForServer(context, " libera "));

    service.setChannelSortModeForServer(context, " libera ", null);
    service.setChannelCustomOrderForServer(context, " libera ", List.of("#a", "#b"));
    flushEdt();

    verify(channelStateCoordinator)
        .setChannelSortModeForServer("libera", ServerTreeDockable.ChannelSortMode.CUSTOM);
    verify(channelStateCoordinator).setChannelCustomOrderForServer("libera", List.of("#a", "#b"));

    service.setChannelSortModeForServer(context, " ", ServerTreeDockable.ChannelSortMode.CUSTOM);
    service.setChannelCustomOrderForServer(context, " ", List.of("#ignored"));
    flushEdt();

    verify(channelStateCoordinator, never())
        .setChannelCustomOrderForServer("", List.of("#ignored"));
  }

  private static void flushEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }
}
