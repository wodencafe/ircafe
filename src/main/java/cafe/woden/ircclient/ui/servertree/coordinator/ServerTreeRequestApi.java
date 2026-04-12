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
import org.springframework.stereotype.Component;

/** Public-facing request stream API composed from tree request and interaction collaborators. */
@Component
public final class ServerTreeRequestApi {

  public interface Context {
    Flowable<TargetRef> selectionStream();

    Flowable<String> connectServerRequests();

    Flowable<String> disconnectServerRequests();

    Flowable<TargetRef> closeTargetRequests();

    Flowable<TargetRef> joinChannelRequests();

    Flowable<TargetRef> disconnectChannelRequests();

    Flowable<TargetRef> bouncerDetachChannelRequests();

    Flowable<TargetRef> closeChannelRequests();

    Flowable<String> managedChannelsChangedByServer();

    Flowable<TargetRef> clearLogRequests();

    Flowable<TargetRef> openPinnedChatRequests();

    Flowable<String> quasselSetupRequests();

    Flowable<String> quasselNetworkManagerRequests();

    void setPinnedDockableProvider(Function<TargetRef, Dockable> provider);

    Flowable<TargetRef> channelModeDetailsRequests();

    Flowable<TargetRef> channelModeRefreshRequests();

    Flowable<ServerTreeDockable.ChannelModeSetRequest> channelModeSetRequests();

    Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests();
  }

  public static Context context(
      ServerTreeSelectionBroadcastCoordinator selectionBroadcastCoordinator,
      ServerTreeRequestStreams requestStreams,
      ServerTreeInteractionSetupCoordinator interactionSetupCoordinator) {
    Objects.requireNonNull(selectionBroadcastCoordinator, "selectionBroadcastCoordinator");
    Objects.requireNonNull(requestStreams, "requestStreams");
    Objects.requireNonNull(interactionSetupCoordinator, "interactionSetupCoordinator");
    return new Context() {
      @Override
      public Flowable<TargetRef> selectionStream() {
        return selectionBroadcastCoordinator.selectionStream();
      }

      @Override
      public Flowable<String> connectServerRequests() {
        return requestStreams.connectServerRequests();
      }

      @Override
      public Flowable<String> disconnectServerRequests() {
        return requestStreams.disconnectServerRequests();
      }

      @Override
      public Flowable<TargetRef> closeTargetRequests() {
        return requestStreams.closeTargetRequests();
      }

      @Override
      public Flowable<TargetRef> joinChannelRequests() {
        return requestStreams.joinChannelRequests();
      }

      @Override
      public Flowable<TargetRef> disconnectChannelRequests() {
        return requestStreams.disconnectChannelRequests();
      }

      @Override
      public Flowable<TargetRef> bouncerDetachChannelRequests() {
        return requestStreams.bouncerDetachChannelRequests();
      }

      @Override
      public Flowable<TargetRef> closeChannelRequests() {
        return requestStreams.closeChannelRequests();
      }

      @Override
      public Flowable<String> managedChannelsChangedByServer() {
        return requestStreams.managedChannelsChangedByServer();
      }

      @Override
      public Flowable<TargetRef> clearLogRequests() {
        return requestStreams.clearLogRequests();
      }

      @Override
      public Flowable<TargetRef> openPinnedChatRequests() {
        return requestStreams.openPinnedChatRequests();
      }

      @Override
      public Flowable<String> quasselSetupRequests() {
        return requestStreams.openQuasselSetupRequests();
      }

      @Override
      public Flowable<String> quasselNetworkManagerRequests() {
        return requestStreams.openQuasselNetworkManagerRequests();
      }

      @Override
      public void setPinnedDockableProvider(Function<TargetRef, Dockable> provider) {
        interactionSetupCoordinator.setPinnedDockableProvider(provider);
      }

      @Override
      public Flowable<TargetRef> channelModeDetailsRequests() {
        return requestStreams.channelModeDetailsRequests();
      }

      @Override
      public Flowable<TargetRef> channelModeRefreshRequests() {
        return requestStreams.channelModeRefreshRequests();
      }

      @Override
      public Flowable<ServerTreeDockable.ChannelModeSetRequest> channelModeSetRequests() {
        return requestStreams.channelModeSetRequests();
      }

      @Override
      public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
        return requestStreams.ircv3CapabilityToggleRequests();
      }
    };
  }

  public Flowable<TargetRef> selectionStream(Context context) {
    return Objects.requireNonNull(context, "context").selectionStream();
  }

  public Flowable<String> connectServerRequests(Context context) {
    return Objects.requireNonNull(context, "context").connectServerRequests();
  }

  public Flowable<String> disconnectServerRequests(Context context) {
    return Objects.requireNonNull(context, "context").disconnectServerRequests();
  }

  public Flowable<TargetRef> closeTargetRequests(Context context) {
    return Objects.requireNonNull(context, "context").closeTargetRequests();
  }

  public Flowable<TargetRef> joinChannelRequests(Context context) {
    return Objects.requireNonNull(context, "context").joinChannelRequests();
  }

  public Flowable<TargetRef> disconnectChannelRequests(Context context) {
    return Objects.requireNonNull(context, "context").disconnectChannelRequests();
  }

  public Flowable<TargetRef> bouncerDetachChannelRequests(Context context) {
    return Objects.requireNonNull(context, "context").bouncerDetachChannelRequests();
  }

  public Flowable<TargetRef> closeChannelRequests(Context context) {
    return Objects.requireNonNull(context, "context").closeChannelRequests();
  }

  public Flowable<String> managedChannelsChangedByServer(Context context) {
    return Objects.requireNonNull(context, "context").managedChannelsChangedByServer();
  }

  public Flowable<TargetRef> clearLogRequests(Context context) {
    return Objects.requireNonNull(context, "context").clearLogRequests();
  }

  public Flowable<TargetRef> openPinnedChatRequests(Context context) {
    return Objects.requireNonNull(context, "context").openPinnedChatRequests();
  }

  public Flowable<String> quasselSetupRequests(Context context) {
    return Objects.requireNonNull(context, "context").quasselSetupRequests();
  }

  public Flowable<String> quasselNetworkManagerRequests(Context context) {
    return Objects.requireNonNull(context, "context").quasselNetworkManagerRequests();
  }

  public void setPinnedDockableProvider(Context context, Function<TargetRef, Dockable> provider) {
    Objects.requireNonNull(context, "context").setPinnedDockableProvider(provider);
  }

  public Flowable<TargetRef> channelModeDetailsRequests(Context context) {
    return Objects.requireNonNull(context, "context").channelModeDetailsRequests();
  }

  public Flowable<TargetRef> channelModeRefreshRequests(Context context) {
    return Objects.requireNonNull(context, "context").channelModeRefreshRequests();
  }

  public Flowable<ServerTreeDockable.ChannelModeSetRequest> channelModeSetRequests(
      Context context) {
    return Objects.requireNonNull(context, "context").channelModeSetRequests();
  }

  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests(Context context) {
    return Objects.requireNonNull(context, "context").ircv3CapabilityToggleRequests();
  }
}
