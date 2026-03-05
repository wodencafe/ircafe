package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.Test;

class ChatTopicCoordinatorTest {

  @Test
  void setTopicUpdatesActiveChannelStateAndEmitsUpdates() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    AtomicInteger uiRefreshCalls = new AtomicInteger();
    ChatTopicCoordinator coordinator =
        newCoordinator(channelListPanel, uiRefreshCalls::incrementAndGet);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    TestSubscriber<ChatDockable.TopicUpdate> subscriber = coordinator.topicUpdates().test();

    coordinator.setTopic(channel, "\u0002new topic\u000f", channel);
    coordinator.clearTopic(channel, channel);

    assertEquals("", coordinator.topicFor(channel));
    subscriber.assertValues(
        new ChatDockable.TopicUpdate(channel, "new topic"),
        new ChatDockable.TopicUpdate(channel, ""));

    verify(channelListPanel, times(2)).refreshOpenChannelDetails("libera", "#ircafe");
    JSplitPane split = (JSplitPane) coordinator.topicSplit();
    assertEquals(0, split.getDividerSize());
    assertTrue(uiRefreshCalls.get() > 0);
  }

  @Test
  void setTopicIgnoresNonChannelTargets() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatTopicCoordinator coordinator = newCoordinator(channelListPanel, () -> {});
    TargetRef queryTarget = new TargetRef("libera", "alice");
    TestSubscriber<ChatDockable.TopicUpdate> subscriber = coordinator.topicUpdates().test();

    coordinator.setTopic(queryTarget, "ignored", queryTarget);

    assertEquals("", coordinator.topicFor(queryTarget));
    subscriber.assertNoValues();
    verifyNoInteractions(channelListPanel);
  }

  @Test
  void updateTopicPanelForActiveChannelShowsTopicPanel() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatTopicCoordinator coordinator = newCoordinator(channelListPanel, () -> {});
    TargetRef channel = new TargetRef("libera", "#ircafe");

    coordinator.setTopic(channel, "visible topic", channel);
    coordinator.updateTopicPanelForActiveTarget(channel);

    JSplitPane split = (JSplitPane) coordinator.topicSplit();
    assertTrue(split.getDividerSize() > 0);
  }

  @Test
  void updateTopicPanelForNonChannelHidesPanel() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatTopicCoordinator coordinator = newCoordinator(channelListPanel, () -> {});
    TargetRef channel = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");

    coordinator.setTopic(channel, "visible topic", channel);
    coordinator.updateTopicPanelForActiveTarget(channel);
    coordinator.updateTopicPanelForActiveTarget(status);

    JSplitPane split = (JSplitPane) coordinator.topicSplit();
    assertEquals(0, split.getDividerSize());
  }

  @Test
  void clearTopicWithoutExistingValueDoesNotEmitUpdate() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatTopicCoordinator coordinator = newCoordinator(channelListPanel, () -> {});
    TargetRef channel = new TargetRef("libera", "#ircafe");
    TestSubscriber<ChatDockable.TopicUpdate> subscriber = coordinator.topicUpdates().test();

    coordinator.clearTopic(channel, channel);

    subscriber.assertNoValues();
    verifyNoInteractions(channelListPanel);
  }

  @Test
  void updateTopicPanelFallsBackToPersistedTopicWhenRuntimeCacheIsEmpty() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    AtomicReference<TargetRef> lookupTarget = new AtomicReference<>();
    Function<TargetRef, String> lookup =
        target -> {
          lookupTarget.set(target);
          return "persisted topic";
        };
    ChatTopicCoordinator coordinator =
        newCoordinator(channelListPanel, () -> {}, lookup, (t, p) -> {});
    TargetRef channel = new TargetRef("libera", "#ircafe");

    coordinator.updateTopicPanelForActiveTarget(channel);

    assertEquals(channel, lookupTarget.get());
    assertEquals("persisted topic", coordinator.topicFor(channel));
    JSplitPane split = (JSplitPane) coordinator.topicSplit();
    assertTrue(split.getDividerSize() > 0);
  }

  @Test
  void topicPanelHeightSetterClampsToExpectedBounds() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatTopicCoordinator coordinator = newCoordinator(channelListPanel, () -> {});

    coordinator.setTopicPanelHeightPx(1);
    assertEquals(40, coordinator.topicPanelHeightPx());

    coordinator.setTopicPanelHeightPx(500);
    assertEquals(200, coordinator.topicPanelHeightPx());
  }

  @Test
  void topicPanelHeightIsTrackedPerChannel() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    TargetRef first = new TargetRef("libera", "#first");
    TargetRef second = new TargetRef("libera", "#second");
    AtomicReference<TargetRef> persistedTarget = new AtomicReference<>();
    AtomicInteger persistedHeight = new AtomicInteger(-1);
    ChatTopicCoordinator coordinator =
        newCoordinator(
            channelListPanel,
            () -> {},
            target -> "",
            (target, topic) -> {},
            target -> target.equals(first) ? 111 : 66,
            (target, heightPx) -> {
              persistedTarget.set(target);
              persistedHeight.set(heightPx);
            });

    coordinator.updateTopicPanelForActiveTarget(first);
    assertEquals(111, coordinator.topicPanelHeightPx());

    coordinator.updateTopicPanelForActiveTarget(second);
    assertEquals(66, coordinator.topicPanelHeightPx());

    coordinator.setTopicPanelHeightPxFor(first, 150);
    coordinator.updateTopicPanelForActiveTarget(first);
    assertEquals(150, coordinator.topicPanelHeightPx());
    assertEquals(first, persistedTarget.get());
    assertEquals(150, persistedHeight.get());
  }

  private static ChatTopicCoordinator newCoordinator(
      ChannelListPanel channelListPanel, Runnable refresh) {
    return newCoordinator(
        channelListPanel,
        refresh,
        target -> "",
        (target, topic) -> {},
        target -> 58,
        (target, heightPx) -> {});
  }

  private static ChatTopicCoordinator newCoordinator(
      ChannelListPanel channelListPanel,
      Runnable refresh,
      Function<TargetRef, String> persistedLookup,
      BiConsumer<TargetRef, String> persistedSink) {
    return newCoordinator(
        channelListPanel,
        refresh,
        persistedLookup,
        persistedSink,
        target -> 58,
        (target, heightPx) -> {});
  }

  private static ChatTopicCoordinator newCoordinator(
      ChannelListPanel channelListPanel,
      Runnable refresh,
      Function<TargetRef, String> persistedLookup,
      BiConsumer<TargetRef, String> persistedSink,
      Function<TargetRef, Integer> persistedHeightLookup,
      BiConsumer<TargetRef, Integer> persistedHeightSink) {
    NotificationStore notificationStore = mock(NotificationStore.class);
    when(notificationStore.listAll("libera")).thenReturn(List.of());
    when(notificationStore.listAllRuleMatches("libera")).thenReturn(List.of());
    when(notificationStore.listAllIrcEventRules("libera")).thenReturn(List.of());
    return new ChatTopicCoordinator(
        new JScrollPane(),
        channelListPanel,
        notificationStore,
        target -> {},
        refresh,
        persistedLookup,
        persistedSink,
        persistedHeightLookup,
        persistedHeightSink);
  }
}
