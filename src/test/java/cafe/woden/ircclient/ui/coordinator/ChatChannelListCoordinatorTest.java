package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.ui.UserListDockable;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatChannelListCoordinatorTest {

  @Test
  void refreshManagedChannelsCardBuildsRowsFromServerTreeAndUserCounts() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    UserListStore userListStore = mock(UserListStore.class);
    UserListDockable usersDock = mock(UserListDockable.class);

    ChatChannelListCoordinator coordinator =
        new ChatChannelListCoordinator(
            channelListPanel,
            serverTree,
            new OutboundLineBus(),
            userListStore,
            usersDock,
            () -> TargetRef.channelList("libera"),
            (sid, channel) -> "",
            (sid, channel) -> List.of());

    when(serverTree.managedChannelsForServer("libera"))
        .thenReturn(List.of(new ServerTreeDockable.ManagedChannelEntry("#ircafe", false, true, 3)));
    when(serverTree.channelSortModeForServer("libera"))
        .thenReturn(ServerTreeDockable.ChannelSortMode.ALPHABETICAL);
    when(userListStore.get("libera", "#ircafe"))
        .thenReturn(List.of(mock(NickInfo.class), mock(NickInfo.class)));

    coordinator.refreshManagedChannelsCard("libera");

    verify(channelListPanel)
        .setManagedChannels(
            eq("libera"),
            argThat(
                rows -> {
                  assertEquals(1, rows.size());
                  ChannelListPanel.ManagedChannelRow row = rows.getFirst();
                  assertEquals("#ircafe", row.channel());
                  assertEquals(false, row.detached());
                  assertEquals(true, row.autoReattach());
                  assertEquals(2, row.users());
                  assertEquals(3, row.notifications());
                  assertEquals("(Unknown)", row.modes());
                  return true;
                }),
            eq(ChannelListPanel.ManagedSortMode.ALPHABETICAL));
  }

  @Test
  void refreshManagedChannelsCardMapsMostRecentActivityMode() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    UserListStore userListStore = mock(UserListStore.class);
    UserListDockable usersDock = mock(UserListDockable.class);

    ChatChannelListCoordinator coordinator =
        new ChatChannelListCoordinator(
            channelListPanel,
            serverTree,
            new OutboundLineBus(),
            userListStore,
            usersDock,
            () -> TargetRef.channelList("libera"),
            (sid, channel) -> "",
            (sid, channel) -> List.of());

    when(serverTree.managedChannelsForServer("libera")).thenReturn(List.of());
    when(serverTree.channelSortModeForServer("libera"))
        .thenReturn(ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY);

    coordinator.refreshManagedChannelsCard("libera");

    verify(channelListPanel)
        .setManagedChannels(
            eq("libera"), anyList(), eq(ChannelListPanel.ManagedSortMode.MOST_RECENT_ACTIVITY));
  }

  @Test
  void refreshManagedChannelsCardUsesCustomModeForBlankServerId() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    UserListStore userListStore = mock(UserListStore.class);
    UserListDockable usersDock = mock(UserListDockable.class);
    ChatChannelListCoordinator coordinator =
        new ChatChannelListCoordinator(
            channelListPanel,
            null,
            new OutboundLineBus(),
            userListStore,
            usersDock,
            () -> TargetRef.channelList("libera"),
            (sid, channel) -> "",
            (sid, channel) -> List.of());

    coordinator.refreshManagedChannelsCard("  ");

    verify(channelListPanel)
        .setManagedChannels(eq(""), anyList(), eq(ChannelListPanel.ManagedSortMode.CUSTOM));
  }

  @Test
  void bindJoinChannelCallbackUsesActiveTargetServerWhenPanelServerIsBlank() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    UserListStore userListStore = mock(UserListStore.class);
    UserListDockable usersDock = mock(UserListDockable.class);
    OutboundLineBus outboundLineBus = mock(OutboundLineBus.class);
    FlowableProcessor<String> changes = PublishProcessor.<String>create().toSerialized();
    when(serverTree.managedChannelsChangedByServer()).thenReturn(changes.onBackpressureBuffer());
    when(channelListPanel.currentServerId()).thenReturn("");
    when(serverTree.managedChannelsForServer("libera")).thenReturn(List.of());
    when(serverTree.channelSortModeForServer("libera"))
        .thenReturn(ServerTreeDockable.ChannelSortMode.CUSTOM);

    ChatChannelListCoordinator coordinator =
        new ChatChannelListCoordinator(
            channelListPanel,
            serverTree,
            outboundLineBus,
            userListStore,
            usersDock,
            () -> new TargetRef("libera", "status"),
            (sid, channel) -> "",
            (sid, channel) -> List.of());

    CompositeDisposable disposables = new CompositeDisposable();
    coordinator.bind(disposables);
    Consumer<String> joinChannel = captureJoinChannelCallback(channelListPanel);
    joinChannel.accept("#ircafe");

    verify(serverTree).requestJoinChannel(new TargetRef("libera", "#ircafe"));
    verify(channelListPanel)
        .setManagedChannels(eq("libera"), anyList(), eq(ChannelListPanel.ManagedSortMode.CUSTOM));
    disposables.dispose();
  }

  @Test
  void bindRunListCallbackSelectsChannelListAndEmitsListCommand() throws Exception {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    UserListStore userListStore = mock(UserListStore.class);
    UserListDockable usersDock = mock(UserListDockable.class);
    OutboundLineBus outboundLineBus = new OutboundLineBus();
    TestSubscriber<String> outbound = outboundLineBus.stream().test();
    FlowableProcessor<String> changes = PublishProcessor.<String>create().toSerialized();
    when(serverTree.managedChannelsChangedByServer()).thenReturn(changes.onBackpressureBuffer());
    when(channelListPanel.currentServerId()).thenReturn("libera");

    ChatChannelListCoordinator coordinator =
        new ChatChannelListCoordinator(
            channelListPanel,
            serverTree,
            outboundLineBus,
            userListStore,
            usersDock,
            () -> TargetRef.channelList("libera"),
            (sid, channel) -> "",
            (sid, channel) -> List.of());

    CompositeDisposable disposables = new CompositeDisposable();
    coordinator.bind(disposables);
    Runnable runList = captureRunListCallback(channelListPanel);
    runList.run();

    verify(serverTree).selectTarget(TargetRef.channelList("libera"));
    Thread.sleep(300);
    outbound.assertValue("/list");
    disposables.dispose();
  }

  @Test
  void bindBanListRefreshCallbackSelectsListTargetAndEmitsModeCommand() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    UserListStore userListStore = mock(UserListStore.class);
    UserListDockable usersDock = mock(UserListDockable.class);
    OutboundLineBus outboundLineBus = mock(OutboundLineBus.class);
    FlowableProcessor<String> changes = PublishProcessor.<String>create().toSerialized();
    when(serverTree.managedChannelsChangedByServer()).thenReturn(changes.onBackpressureBuffer());
    when(channelListPanel.currentServerId()).thenReturn("libera");

    ChatChannelListCoordinator coordinator =
        new ChatChannelListCoordinator(
            channelListPanel,
            serverTree,
            outboundLineBus,
            userListStore,
            usersDock,
            () -> TargetRef.channelList("libera"),
            (sid, channel) -> "",
            (sid, channel) -> List.of());

    CompositeDisposable disposables = new CompositeDisposable();
    coordinator.bind(disposables);
    BiConsumer<String, String> banRefresh = captureBanListRefreshCallback(channelListPanel);
    banRefresh.accept("", "#ircafe");

    verify(serverTree).selectTarget(TargetRef.channelList("libera"));
    verify(outboundLineBus).emit("/mode #ircafe +b");
    disposables.dispose();
  }

  @Test
  void bindManagedChannelSelectedCallbackUpdatesUsersDock() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    UserListStore userListStore = mock(UserListStore.class);
    UserListDockable usersDock = mock(UserListDockable.class);
    OutboundLineBus outboundLineBus = mock(OutboundLineBus.class);
    FlowableProcessor<String> changes = PublishProcessor.<String>create().toSerialized();
    when(serverTree.managedChannelsChangedByServer()).thenReturn(changes.onBackpressureBuffer());
    when(channelListPanel.currentServerId()).thenReturn("libera");
    when(userListStore.get("libera", "#ircafe")).thenReturn(List.of());

    ChatChannelListCoordinator coordinator =
        new ChatChannelListCoordinator(
            channelListPanel,
            serverTree,
            outboundLineBus,
            userListStore,
            usersDock,
            () -> TargetRef.channelList("libera"),
            (sid, channel) -> "",
            (sid, channel) -> List.of());

    CompositeDisposable disposables = new CompositeDisposable();
    coordinator.bind(disposables);
    Consumer<String> selectedCallback = captureManagedChannelSelectedCallback(channelListPanel);
    selectedCallback.accept("#ircafe");

    verify(usersDock).setChannel(new TargetRef("libera", "#ircafe"));
    verify(usersDock).setNicks(List.of());
    disposables.dispose();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Consumer<String> captureJoinChannelCallback(ChannelListPanel channelListPanel) {
    ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(channelListPanel).setOnJoinChannel(captor.capture());
    return captor.getValue();
  }

  private static Runnable captureRunListCallback(ChannelListPanel channelListPanel) {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(channelListPanel).setOnRunListRequest(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static BiConsumer<String, String> captureBanListRefreshCallback(
      ChannelListPanel channelListPanel) {
    ArgumentCaptor<BiConsumer> captor = ArgumentCaptor.forClass(BiConsumer.class);
    verify(channelListPanel).setOnChannelBanListRefreshRequest(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Consumer<String> captureManagedChannelSelectedCallback(
      ChannelListPanel channelListPanel) {
    ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(channelListPanel).setOnManagedChannelSelected(captor.capture());
    return captor.getValue();
  }
}
