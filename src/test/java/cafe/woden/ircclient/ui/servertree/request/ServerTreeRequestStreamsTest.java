package cafe.woden.ircclient.ui.servertree.request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable.ChannelModeSetRequest;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;

class ServerTreeRequestStreamsTest {

  @Test
  void emitterPublishesAllRequestsToMatchingStreams() {
    ServerTreeRequestStreams streams = new ServerTreeRequestStreams();
    ServerTreeRequestEmitter emitter = streams.requestEmitter();

    TestSubscriber<String> connect = streams.connectServerRequests().test();
    TestSubscriber<String> disconnect = streams.disconnectServerRequests().test();
    TestSubscriber<TargetRef> closeTarget = streams.closeTargetRequests().test();
    TestSubscriber<TargetRef> joinChannel = streams.joinChannelRequests().test();
    TestSubscriber<TargetRef> disconnectChannel = streams.disconnectChannelRequests().test();
    TestSubscriber<TargetRef> bouncerDetach = streams.bouncerDetachChannelRequests().test();
    TestSubscriber<TargetRef> closeChannel = streams.closeChannelRequests().test();
    TestSubscriber<String> managedChanged = streams.managedChannelsChangedByServer().test();
    TestSubscriber<TargetRef> clearLog = streams.clearLogRequests().test();
    TestSubscriber<TargetRef> openPinned = streams.openPinnedChatRequests().test();
    TestSubscriber<String> openSetup = streams.openQuasselSetupRequests().test();
    TestSubscriber<String> openNetworkManager = streams.openQuasselNetworkManagerRequests().test();
    TestSubscriber<Ircv3CapabilityToggleRequest> toggleCaps =
        streams.ircv3CapabilityToggleRequests().test();

    TargetRef channel = new TargetRef("libera", "#ircafe");
    Ircv3CapabilityToggleRequest toggle =
        new Ircv3CapabilityToggleRequest("libera", "draft/example", true);

    emitter.emitConnectServer("libera");
    emitter.emitDisconnectServer("libera");
    emitter.emitCloseTarget(channel);
    emitter.emitJoinChannel(channel);
    emitter.emitDisconnectChannel(channel);
    emitter.emitBouncerDetachChannel(channel);
    emitter.emitCloseChannel(channel);
    emitter.emitManagedChannelsChanged("libera");
    emitter.emitClearLog(channel);
    emitter.emitOpenPinnedChat(channel);
    emitter.emitOpenQuasselSetup("libera");
    emitter.emitOpenQuasselNetworkManager("libera");
    emitter.emitIrcv3CapabilityToggle(toggle);

    connect.assertValue("libera");
    disconnect.assertValue("libera");
    closeTarget.assertValue(channel);
    joinChannel.assertValue(channel);
    disconnectChannel.assertValue(channel);
    bouncerDetach.assertValue(channel);
    closeChannel.assertValue(channel);
    managedChanged.assertValue("libera");
    clearLog.assertValue(channel);
    openPinned.assertValue(channel);
    openSetup.assertValue("libera");
    openNetworkManager.assertValue("libera");
    toggleCaps.assertValue(toggle);
    assertEquals(1, connect.values().size());
  }

  @Test
  void emitsChannelModeDetailsOnlyForChannelTargets() {
    ServerTreeRequestStreams streams = new ServerTreeRequestStreams();
    TestSubscriber<TargetRef> details = streams.channelModeDetailsRequests().test();

    TargetRef channel = new TargetRef("libera", "#ircafe");
    streams.emitChannelModeDetailsRequest(TargetRef.notifications("libera"));
    details.assertNoValues();

    streams.emitChannelModeDetailsRequest(channel);
    details.assertValue(channel);
  }

  @Test
  void emitsChannelModeSetRequestsWithTrimmedSpecs() {
    ServerTreeRequestStreams streams = new ServerTreeRequestStreams();
    TestSubscriber<ChannelModeSetRequest> setRequests = streams.channelModeSetRequests().test();

    TargetRef channel = new TargetRef("libera", "#ircafe");
    streams.emitChannelModeSetRequest(channel, "   ");
    setRequests.assertNoValues();

    streams.emitChannelModeSetRequest(channel, "  +m  ");
    assertEquals(1, setRequests.values().size());
    ChannelModeSetRequest emitted = setRequests.values().getFirst();
    assertEquals(channel, emitted.target());
    assertEquals("+m", emitted.modeSpec());
  }
}
