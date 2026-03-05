package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionSetupCoordinator;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestStreams;
import io.github.andrewauclair.moderndocking.Dockable;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ServerTreeRequestApiTest {

  @Test
  void delegatesSelectionRequestsModeStreamsAndPinnedDockProvider() {
    ServerTreeSelectionBroadcastCoordinator selectionBroadcastCoordinator =
        mock(ServerTreeSelectionBroadcastCoordinator.class);
    ServerTreeRequestStreams requestStreams = mock(ServerTreeRequestStreams.class);
    ServerTreeInteractionSetupCoordinator interactionSetupCoordinator =
        mock(ServerTreeInteractionSetupCoordinator.class);
    ServerTreeRequestApi api =
        new ServerTreeRequestApi(
            selectionBroadcastCoordinator, requestStreams, interactionSetupCoordinator);

    Function<TargetRef, Dockable> provider = __ -> null;

    api.selectionStream();
    api.connectServerRequests();
    api.disconnectServerRequests();
    api.closeTargetRequests();
    api.joinChannelRequests();
    api.disconnectChannelRequests();
    api.bouncerDetachChannelRequests();
    api.closeChannelRequests();
    api.managedChannelsChangedByServer();
    api.clearLogRequests();
    api.openPinnedChatRequests();
    api.quasselSetupRequests();
    api.quasselNetworkManagerRequests();
    api.channelModeDetailsRequests();
    api.channelModeRefreshRequests();
    api.channelModeSetRequests();
    api.ircv3CapabilityToggleRequests();
    api.setPinnedDockableProvider(provider);

    verify(selectionBroadcastCoordinator).selectionStream();
    verify(requestStreams).connectServerRequests();
    verify(requestStreams).disconnectServerRequests();
    verify(requestStreams).closeTargetRequests();
    verify(requestStreams).joinChannelRequests();
    verify(requestStreams).disconnectChannelRequests();
    verify(requestStreams).bouncerDetachChannelRequests();
    verify(requestStreams).closeChannelRequests();
    verify(requestStreams).managedChannelsChangedByServer();
    verify(requestStreams).clearLogRequests();
    verify(requestStreams).openPinnedChatRequests();
    verify(requestStreams).openQuasselSetupRequests();
    verify(requestStreams).openQuasselNetworkManagerRequests();
    verify(requestStreams).channelModeDetailsRequests();
    verify(requestStreams).channelModeRefreshRequests();
    verify(requestStreams).channelModeSetRequests();
    verify(requestStreams).ircv3CapabilityToggleRequests();
    verify(interactionSetupCoordinator).setPinnedDockableProvider(provider);
  }
}
