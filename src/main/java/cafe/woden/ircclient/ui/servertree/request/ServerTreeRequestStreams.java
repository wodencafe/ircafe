package cafe.woden.ircclient.ui.servertree.request;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;

/** Owns outbound request processors and exposes both stream and emitter views. */
public final class ServerTreeRequestStreams {
  private final FlowableProcessor<String> connectServerRequests =
      PublishProcessor.<String>create().toSerialized();
  private final FlowableProcessor<String> disconnectServerRequests =
      PublishProcessor.<String>create().toSerialized();

  private final FlowableProcessor<TargetRef> closeTargetRequests =
      PublishProcessor.<TargetRef>create().toSerialized();
  private final FlowableProcessor<TargetRef> joinChannelRequests =
      PublishProcessor.<TargetRef>create().toSerialized();
  private final FlowableProcessor<TargetRef> disconnectChannelRequests =
      PublishProcessor.<TargetRef>create().toSerialized();
  private final FlowableProcessor<TargetRef> bouncerDetachChannelRequests =
      PublishProcessor.<TargetRef>create().toSerialized();
  private final FlowableProcessor<TargetRef> closeChannelRequests =
      PublishProcessor.<TargetRef>create().toSerialized();
  private final FlowableProcessor<String> managedChannelsChangedByServer =
      PublishProcessor.<String>create().toSerialized();

  private final FlowableProcessor<TargetRef> clearLogRequests =
      PublishProcessor.<TargetRef>create().toSerialized();
  private final FlowableProcessor<TargetRef> openPinnedChatRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<String> openQuasselSetupRequests =
      PublishProcessor.<String>create().toSerialized();
  private final FlowableProcessor<String> openQuasselNetworkManagerRequests =
      PublishProcessor.<String>create().toSerialized();
  private final FlowableProcessor<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests =
      PublishProcessor.<Ircv3CapabilityToggleRequest>create().toSerialized();

  private final ServerTreeRequestEmitter requestEmitter =
      new ServerTreeRequestLoggingDecorator(
          new ServerTreeProcessorRequestEmitter(
              connectServerRequests,
              disconnectServerRequests,
              closeTargetRequests,
              joinChannelRequests,
              disconnectChannelRequests,
              bouncerDetachChannelRequests,
              closeChannelRequests,
              managedChannelsChangedByServer,
              clearLogRequests,
              openPinnedChatRequests,
              openQuasselSetupRequests,
              openQuasselNetworkManagerRequests,
              ircv3CapabilityToggleRequests));

  public ServerTreeRequestEmitter requestEmitter() {
    return requestEmitter;
  }

  public Flowable<String> connectServerRequests() {
    return connectServerRequests.onBackpressureLatest();
  }

  public Flowable<String> disconnectServerRequests() {
    return disconnectServerRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> closeTargetRequests() {
    return closeTargetRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> joinChannelRequests() {
    return joinChannelRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> disconnectChannelRequests() {
    return disconnectChannelRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> bouncerDetachChannelRequests() {
    return bouncerDetachChannelRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> closeChannelRequests() {
    return closeChannelRequests.onBackpressureLatest();
  }

  public Flowable<String> managedChannelsChangedByServer() {
    return managedChannelsChangedByServer.onBackpressureLatest();
  }

  public Flowable<TargetRef> clearLogRequests() {
    return clearLogRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> openPinnedChatRequests() {
    return openPinnedChatRequests.onBackpressureLatest();
  }

  public Flowable<String> openQuasselSetupRequests() {
    return openQuasselSetupRequests.onBackpressureLatest();
  }

  public Flowable<String> openQuasselNetworkManagerRequests() {
    return openQuasselNetworkManagerRequests.onBackpressureLatest();
  }

  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return ircv3CapabilityToggleRequests.onBackpressureLatest();
  }
}
