package cafe.woden.ircclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class VirtualThreadsLifecycleIntegrationTest {

  @AfterEach
  void cleanup() {
    VirtualThreads.shutdownTrackedExecutorsNow();
  }

  @Test
  void springContextCloseInvokesLifecyclePreDestroy() {
    ExecutorService exec = null;
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    try {
      ctx.register(VirtualThreadsLifecycle.class);
      ctx.refresh();
      exec = VirtualThreads.newSingleThreadExecutor("test-vt-lifecycle-integration");
      assertFalse(exec.isShutdown());
    } finally {
      ctx.close();
    }

    // @PreDestroy on VirtualThreadsLifecycle should drain tracked executors on context close.
    assertNotNull(exec);
    assertTrue(exec.isShutdown());
  }
}
