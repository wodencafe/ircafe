package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.Test;

class ChatTopicCoordinatorTest {

  @Test
  void setTopicUpdatesActiveChannelStateAndEmitsUpdates() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    AtomicInteger uiRefreshCalls = new AtomicInteger();
    ChatTopicCoordinator coordinator =
        new ChatTopicCoordinator(
            new JScrollPane(), channelListPanel, uiRefreshCalls::incrementAndGet);
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
    ChatTopicCoordinator coordinator =
        new ChatTopicCoordinator(new JScrollPane(), channelListPanel, () -> {});
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
    ChatTopicCoordinator coordinator =
        new ChatTopicCoordinator(new JScrollPane(), channelListPanel, () -> {});
    TargetRef channel = new TargetRef("libera", "#ircafe");

    coordinator.setTopic(channel, "visible topic", channel);
    coordinator.updateTopicPanelForActiveTarget(channel);

    JSplitPane split = (JSplitPane) coordinator.topicSplit();
    assertTrue(split.getDividerSize() > 0);
  }

  @Test
  void updateTopicPanelForNonChannelHidesPanel() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatTopicCoordinator coordinator =
        new ChatTopicCoordinator(new JScrollPane(), channelListPanel, () -> {});
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
    ChatTopicCoordinator coordinator =
        new ChatTopicCoordinator(new JScrollPane(), channelListPanel, () -> {});
    TargetRef channel = new TargetRef("libera", "#ircafe");
    TestSubscriber<ChatDockable.TopicUpdate> subscriber = coordinator.topicUpdates().test();

    coordinator.clearTopic(channel, channel);

    subscriber.assertNoValues();
    verifyNoInteractions(channelListPanel);
  }
}
