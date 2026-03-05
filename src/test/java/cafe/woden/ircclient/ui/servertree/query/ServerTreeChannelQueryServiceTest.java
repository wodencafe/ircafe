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
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        mock(ServerTreeChannelStateCoordinator.class);
    ServerTreeChannelQueryService service =
        new ServerTreeChannelQueryService(
            new ServerTreeEdtExecutor(),
            targetSnapshotProvider,
            channelStateCoordinator,
            id -> Objects.toString(id, "").trim());

    when(targetSnapshotProvider.snapshotOpenChannelsForServer("libera"))
        .thenReturn(List.of("#ircafe"));

    assertEquals(List.of("#ircafe"), service.openChannelsForServer("libera"));
    assertTrue(service.openChannelsForServer(" ").isEmpty());

    verify(targetSnapshotProvider).snapshotOpenChannelsForServer("libera");
  }

  @Test
  void managedChannelQueriesAndMutationsUseNormalizedServerIds() throws Exception {
    ServerTreeTargetSnapshotProvider targetSnapshotProvider =
        mock(ServerTreeTargetSnapshotProvider.class);
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        mock(ServerTreeChannelStateCoordinator.class);
    ServerTreeChannelQueryService service =
        new ServerTreeChannelQueryService(
            new ServerTreeEdtExecutor(),
            targetSnapshotProvider,
            channelStateCoordinator,
            id -> Objects.toString(id, "").trim());

    when(channelStateCoordinator.snapshotManagedChannelsForServer("libera"))
        .thenReturn(List.of(new ServerTreeDockable.ManagedChannelEntry("#ircafe", false, true, 1)));
    when(channelStateCoordinator.channelSortModeForServer("libera"))
        .thenReturn(ServerTreeDockable.ChannelSortMode.ALPHABETICAL);

    assertEquals(1, service.managedChannelsForServer(" libera ").size());
    assertEquals(
        ServerTreeDockable.ChannelSortMode.ALPHABETICAL,
        service.channelSortModeForServer(" libera "));

    service.setChannelSortModeForServer(" libera ", null);
    service.setChannelCustomOrderForServer(" libera ", List.of("#a", "#b"));
    flushEdt();

    verify(channelStateCoordinator)
        .setChannelSortModeForServer("libera", ServerTreeDockable.ChannelSortMode.CUSTOM);
    verify(channelStateCoordinator).setChannelCustomOrderForServer("libera", List.of("#a", "#b"));

    service.setChannelSortModeForServer(" ", ServerTreeDockable.ChannelSortMode.CUSTOM);
    service.setChannelCustomOrderForServer(" ", List.of("#ignored"));
    flushEdt();

    verify(channelStateCoordinator, never())
        .setChannelCustomOrderForServer("", List.of("#ignored"));
  }

  private static void flushEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }
}
