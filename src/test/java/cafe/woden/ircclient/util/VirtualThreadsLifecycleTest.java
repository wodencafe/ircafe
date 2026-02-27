package cafe.woden.ircclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VirtualThreadsLifecycleTest {

  @AfterEach
  void cleanup() {
    VirtualThreads.shutdownTrackedExecutorsNow();
  }

  @Test
  void shutdownClosesTrackedExecutors() {
    ExecutorService exec = VirtualThreads.newSingleThreadExecutor("test-vt-lifecycle");
    assertFalse(exec.isShutdown());

    new VirtualThreadsLifecycle().shutdown();

    assertTrue(exec.isShutdown());
  }

  @Test
  void shutdownIsSafeWhenNoTrackedExecutorsRemain() {
    VirtualThreads.shutdownTrackedExecutorsNow();

    new VirtualThreadsLifecycle().shutdown();

    // If no exception is thrown and tracker stays drained, lifecycle shutdown is safe to repeat.
    assertEquals(0, VirtualThreads.shutdownTrackedExecutorsNow());
  }
}
