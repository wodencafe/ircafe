package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeUnreadStateCoordinatorTest {

  @Test
  void markUnreadIncrementsCounterAndEmitsSignals() {
    TargetRef ref = new TargetRef("libera", "#ircafe");
    Fixture fixture = Fixture.withChannel(ref);

    fixture.coordinator.markUnread(ref);

    assertEquals(1, fixture.nodeData.unread);
    assertEquals(0, fixture.nodeData.highlightUnread);
    assertEquals(1, fixture.noteActivityCount.get());
    assertEquals(ref, fixture.lastNotedActivity.get());
    assertEquals(1, fixture.unreadChangedCount.get());
    assertEquals(ref, fixture.lastUnreadChanged.get());
    assertEquals(1, fixture.managedChangedCount.get());
    assertEquals("libera", fixture.lastManagedServerId.get());
  }

  @Test
  void markUnreadSkipsMutedChannels() {
    TargetRef ref = new TargetRef("libera", "#ircafe");
    Fixture fixture = Fixture.withChannel(ref);
    fixture.muted = true;

    fixture.coordinator.markUnread(ref);

    assertEquals(0, fixture.nodeData.unread);
    assertEquals(0, fixture.nodeData.highlightUnread);
    assertEquals(0, fixture.noteActivityCount.get());
    assertEquals(0, fixture.unreadChangedCount.get());
    assertEquals(0, fixture.managedChangedCount.get());
  }

  @Test
  void mutingChannelClearsUnreadCountersAndEmitsSignals() {
    TargetRef ref = new TargetRef("libera", "#ircafe");
    Fixture fixture = Fixture.withChannel(ref);
    fixture.nodeData.unread = 2;
    fixture.nodeData.highlightUnread = 1;

    fixture.coordinator.onChannelMutedStateChanged(ref, true);

    assertEquals(0, fixture.nodeData.unread);
    assertEquals(0, fixture.nodeData.highlightUnread);
    assertEquals(0, fixture.noteActivityCount.get());
    assertEquals(1, fixture.unreadChangedCount.get());
    assertEquals(ref, fixture.lastUnreadChanged.get());
    assertEquals(1, fixture.managedChangedCount.get());
    assertEquals("libera", fixture.lastManagedServerId.get());
  }

  private static final class Fixture {
    private final AtomicInteger noteActivityCount = new AtomicInteger();
    private final AtomicReference<TargetRef> lastNotedActivity = new AtomicReference<>();
    private final AtomicInteger unreadChangedCount = new AtomicInteger();
    private final AtomicReference<TargetRef> lastUnreadChanged = new AtomicReference<>();
    private final AtomicInteger managedChangedCount = new AtomicInteger();
    private final AtomicReference<String> lastManagedServerId = new AtomicReference<>();
    private volatile boolean muted = false;

    private ServerTreeUnreadStateCoordinator coordinator;
    private ServerTreeNodeData nodeData;

    private static Fixture withChannel(TargetRef ref) {
      Fixture fixture = new Fixture();
      DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
      fixture.nodeData = new ServerTreeNodeData(ref, ref.target());
      DefaultMutableTreeNode channelNode = new DefaultMutableTreeNode(fixture.nodeData);
      root.add(channelNode);
      DefaultTreeModel model = new DefaultTreeModel(root);

      Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
      leaves.put(ref, channelNode);

      fixture.coordinator =
          new ServerTreeUnreadStateCoordinator(
              leaves,
              model,
              ignored -> fixture.muted,
              target -> {
                fixture.noteActivityCount.incrementAndGet();
                fixture.lastNotedActivity.set(target);
              },
              target -> {
                fixture.unreadChangedCount.incrementAndGet();
                fixture.lastUnreadChanged.set(target);
              },
              serverId -> {
                fixture.managedChangedCount.incrementAndGet();
                fixture.lastManagedServerId.set(serverId);
              });
      return fixture;
    }
  }
}
