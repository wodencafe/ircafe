package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionSetupCoordinator;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestStreams;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Objects;
import java.util.function.Function;

/** Public-facing request stream API composed from tree request and interaction collaborators. */
public final class ServerTreeRequestApi {
  private final ServerTreeSelectionBroadcastCoordinator selectionBroadcastCoordinator;
  private final ServerTreeRequestStreams requestStreams;
  private final ServerTreeInteractionSetupCoordinator interactionSetupCoordinator;

  public ServerTreeRequestApi(
      ServerTreeSelectionBroadcastCoordinator selectionBroadcastCoordinator,
      ServerTreeRequestStreams requestStreams,
      ServerTreeInteractionSetupCoordinator interactionSetupCoordinator) {
    this.selectionBroadcastCoordinator =
        Objects.requireNonNull(selectionBroadcastCoordinator, "selectionBroadcastCoordinator");
    this.requestStreams = Objects.requireNonNull(requestStreams, "requestStreams");
    this.interactionSetupCoordinator =
        Objects.requireNonNull(interactionSetupCoordinator, "interactionSetupCoordinator");
  }

  public Flowable<TargetRef> selectionStream() {
    return selectionBroadcastCoordinator.selectionStream();
  }

  public Flowable<String> connectServerRequests() {
    return requestStreams.connectServerRequests();
  }

  public Flowable<String> disconnectServerRequests() {
    return requestStreams.disconnectServerRequests();
  }

  public Flowable<TargetRef> closeTargetRequests() {
    return requestStreams.closeTargetRequests();
  }

  public Flowable<TargetRef> joinChannelRequests() {
    return requestStreams.joinChannelRequests();
  }

  public Flowable<TargetRef> disconnectChannelRequests() {
    return requestStreams.disconnectChannelRequests();
  }

  public Flowable<TargetRef> bouncerDetachChannelRequests() {
    return requestStreams.bouncerDetachChannelRequests();
  }

  public Flowable<TargetRef> closeChannelRequests() {
    return requestStreams.closeChannelRequests();
  }

  public Flowable<String> managedChannelsChangedByServer() {
    return requestStreams.managedChannelsChangedByServer();
  }

  public Flowable<TargetRef> clearLogRequests() {
    return requestStreams.clearLogRequests();
  }

  public Flowable<TargetRef> openPinnedChatRequests() {
    return requestStreams.openPinnedChatRequests();
  }

  public Flowable<String> quasselSetupRequests() {
    return requestStreams.openQuasselSetupRequests();
  }

  public Flowable<String> quasselNetworkManagerRequests() {
    return requestStreams.openQuasselNetworkManagerRequests();
  }

  public void setPinnedDockableProvider(Function<TargetRef, Dockable> provider) {
    interactionSetupCoordinator.setPinnedDockableProvider(provider);
  }

  public Flowable<TargetRef> channelModeDetailsRequests() {
    return requestStreams.channelModeDetailsRequests();
  }

  public Flowable<TargetRef> channelModeRefreshRequests() {
    return requestStreams.channelModeRefreshRequests();
  }

  public Flowable<ServerTreeDockable.ChannelModeSetRequest> channelModeSetRequests() {
    return requestStreams.channelModeSetRequests();
  }

  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return requestStreams.ircv3CapabilityToggleRequests();
  }
}
