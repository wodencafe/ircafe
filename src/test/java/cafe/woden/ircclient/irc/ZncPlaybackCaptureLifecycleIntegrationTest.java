package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ZncPlaybackCaptureLifecycleIntegrationTest {

  @AfterEach
  void cleanup() {
    ZncPlaybackCaptureCoordinator.shutdownSharedScheduler();
  }

  @Test
  void springContextCloseInvokesLifecyclePreDestroy() throws Exception {
    ZncPlaybackCaptureCoordinator coordinator = new ZncPlaybackCaptureCoordinator();
    coordinator.start("libera", "#ircafe", null, null, event -> {});
    coordinator.cancelActive("test");
    ScheduledExecutorService schedulerBefore = readScheduler();
    assertNotNull(schedulerBefore);

    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    try {
      ctx.register(ZncPlaybackCaptureLifecycle.class);
      ctx.refresh();
    } finally {
      ctx.close();
    }

    assertNull(readScheduler());
  }

  private static ScheduledExecutorService readScheduler() throws Exception {
    Field f = ZncPlaybackCaptureCoordinator.class.getDeclaredField("scheduler");
    f.setAccessible(true);
    return (ScheduledExecutorService) f.get(null);
  }
}
