package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.model.TargetRef;
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
    ServerTreeRequestApi api = new ServerTreeRequestApi();
    ServerTreeRequestApi.Context context =
        ServerTreeRequestApi.context(
            selectionBroadcastCoordinator, requestStreams, interactionSetupCoordinator);

    Function<TargetRef, Dockable> provider = __ -> null;

    api.selectionStream(context);
    api.connectServerRequests(context);
    api.disconnectServerRequests(context);
    api.closeTargetRequests(context);
    api.joinChannelRequests(context);
    api.disconnectChannelRequests(context);
    api.bouncerDetachChannelRequests(context);
    api.closeChannelRequests(context);
    api.managedChannelsChangedByServer(context);
    api.clearLogRequests(context);
    api.openPinnedChatRequests(context);
    api.quasselSetupRequests(context);
    api.quasselNetworkManagerRequests(context);
    api.channelModeDetailsRequests(context);
    api.channelModeRefreshRequests(context);
    api.channelModeSetRequests(context);
    api.ircv3CapabilityToggleRequests(context);
    api.setPinnedDockableProvider(context, provider);

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
