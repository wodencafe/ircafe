package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ServerTreeSelectionBroadcastCoordinatorTest {

  @Test
  void publishSelectionBroadcastsAndTracksLastSelection() {
    ServerTreeSelectionBroadcastCoordinator coordinator =
        new ServerTreeSelectionBroadcastCoordinator();
    TestSubscriber<TargetRef> stream = coordinator.selectionStream().test();
    TargetRef statusRef = new TargetRef("libera", "status");

    coordinator.publishSelection(statusRef);

    stream.assertValue(statusRef);
    assertSame(statusRef, coordinator.lastBroadcastSelectionRef());
  }

  @Test
  void withSuppressedSelectionBroadcastWrapsTaskAndResetsFlag() {
    ServerTreeSelectionBroadcastCoordinator coordinator =
        new ServerTreeSelectionBroadcastCoordinator();
    AtomicBoolean suppressInsideTask = new AtomicBoolean(false);

    assertFalse(coordinator.suppressSelectionBroadcast());
    coordinator.withSuppressedSelectionBroadcast(
        () -> suppressInsideTask.set(coordinator.suppressSelectionBroadcast()));

    assertTrue(suppressInsideTask.get());
    assertFalse(coordinator.suppressSelectionBroadcast());
  }
}
