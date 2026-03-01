package cafe.woden.ircclient.ui.servertree.request;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.util.Objects;

/** Request emitter backed by RxJava processors exposed by {@code ServerTreeDockable}. */
public final class ServerTreeProcessorRequestEmitter implements ServerTreeRequestEmitter {

  private final FlowableProcessor<String> connectServerRequests;
  private final FlowableProcessor<String> disconnectServerRequests;
  private final FlowableProcessor<TargetRef> closeTargetRequests;
  private final FlowableProcessor<TargetRef> joinChannelRequests;
  private final FlowableProcessor<TargetRef> disconnectChannelRequests;
  private final FlowableProcessor<TargetRef> bouncerDetachChannelRequests;
  private final FlowableProcessor<TargetRef> closeChannelRequests;
  private final FlowableProcessor<String> managedChannelsChangedByServer;
  private final FlowableProcessor<TargetRef> clearLogRequests;
  private final FlowableProcessor<TargetRef> openPinnedChatRequests;
  private final FlowableProcessor<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests;

  public ServerTreeProcessorRequestEmitter(
      FlowableProcessor<String> connectServerRequests,
      FlowableProcessor<String> disconnectServerRequests,
      FlowableProcessor<TargetRef> closeTargetRequests,
      FlowableProcessor<TargetRef> joinChannelRequests,
      FlowableProcessor<TargetRef> disconnectChannelRequests,
      FlowableProcessor<TargetRef> bouncerDetachChannelRequests,
      FlowableProcessor<TargetRef> closeChannelRequests,
      FlowableProcessor<String> managedChannelsChangedByServer,
      FlowableProcessor<TargetRef> clearLogRequests,
      FlowableProcessor<TargetRef> openPinnedChatRequests,
      FlowableProcessor<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests) {
    this.connectServerRequests =
        Objects.requireNonNull(connectServerRequests, "connectServerRequests");
    this.disconnectServerRequests =
        Objects.requireNonNull(disconnectServerRequests, "disconnectServerRequests");
    this.closeTargetRequests = Objects.requireNonNull(closeTargetRequests, "closeTargetRequests");
    this.joinChannelRequests = Objects.requireNonNull(joinChannelRequests, "joinChannelRequests");
    this.disconnectChannelRequests =
        Objects.requireNonNull(disconnectChannelRequests, "disconnectChannelRequests");
    this.bouncerDetachChannelRequests =
        Objects.requireNonNull(bouncerDetachChannelRequests, "bouncerDetachChannelRequests");
    this.closeChannelRequests =
        Objects.requireNonNull(closeChannelRequests, "closeChannelRequests");
    this.managedChannelsChangedByServer =
        Objects.requireNonNull(managedChannelsChangedByServer, "managedChannelsChangedByServer");
    this.clearLogRequests = Objects.requireNonNull(clearLogRequests, "clearLogRequests");
    this.openPinnedChatRequests =
        Objects.requireNonNull(openPinnedChatRequests, "openPinnedChatRequests");
    this.ircv3CapabilityToggleRequests =
        Objects.requireNonNull(ircv3CapabilityToggleRequests, "ircv3CapabilityToggleRequests");
  }

  @Override
  public void emitConnectServer(String serverId) {
    connectServerRequests.onNext(serverId);
  }

  @Override
  public void emitDisconnectServer(String serverId) {
    disconnectServerRequests.onNext(serverId);
  }

  @Override
  public void emitCloseTarget(TargetRef ref) {
    closeTargetRequests.onNext(ref);
  }

  @Override
  public void emitJoinChannel(TargetRef ref) {
    joinChannelRequests.onNext(ref);
  }

  @Override
  public void emitDisconnectChannel(TargetRef ref) {
    disconnectChannelRequests.onNext(ref);
  }

  @Override
  public void emitBouncerDetachChannel(TargetRef ref) {
    bouncerDetachChannelRequests.onNext(ref);
  }

  @Override
  public void emitCloseChannel(TargetRef ref) {
    closeChannelRequests.onNext(ref);
  }

  @Override
  public void emitManagedChannelsChanged(String serverId) {
    managedChannelsChangedByServer.onNext(serverId);
  }

  @Override
  public void emitClearLog(TargetRef target) {
    clearLogRequests.onNext(target);
  }

  @Override
  public void emitOpenPinnedChat(TargetRef ref) {
    openPinnedChatRequests.onNext(ref);
  }

  @Override
  public void emitIrcv3CapabilityToggle(Ircv3CapabilityToggleRequest request) {
    ircv3CapabilityToggleRequests.onNext(request);
  }
}
