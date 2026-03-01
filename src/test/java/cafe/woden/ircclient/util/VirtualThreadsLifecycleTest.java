package cafe.woden.ircclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

  @Test
  void shutdownLetsShortRunningTasksFinishBeforeForcedInterrupt() throws Exception {
    ExecutorService exec = VirtualThreads.newSingleThreadExecutor("test-vt-graceful-shutdown");
    AtomicBoolean interrupted = new AtomicBoolean(false);
    CountDownLatch started = new CountDownLatch(1);
    Future<?> task =
        exec.submit(
            () -> {
              started.countDown();
              try {
                Thread.sleep(120);
              } catch (InterruptedException ie) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
              }
            });

    assertTrue(started.await(1, TimeUnit.SECONDS));

    VirtualThreads.shutdownTrackedExecutorsNow();

    task.get(1, TimeUnit.SECONDS);
    assertFalse(interrupted.get());
  }
}
