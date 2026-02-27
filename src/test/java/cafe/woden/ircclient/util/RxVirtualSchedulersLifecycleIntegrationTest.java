package cafe.woden.ircclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.reactivex.rxjava3.core.Scheduler;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class RxVirtualSchedulersLifecycleIntegrationTest {

  @AfterEach
  void cleanup() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void springContextCloseInvokesLifecyclePreDestroy() throws Exception {
    Scheduler ioScheduler = RxVirtualSchedulers.io();
    Scheduler computationScheduler = RxVirtualSchedulers.computation();
    assertNotNull(ioScheduler);
    assertNotNull(computationScheduler);

    ExecutorService ioExec = readIoExec();
    ScheduledExecutorService computationExec = readComputationExec();
    assertNotNull(ioExec);
    assertNotNull(computationExec);
    assertFalse(ioExec.isShutdown());
    assertFalse(computationExec.isShutdown());

    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    try {
      ctx.register(RxVirtualSchedulersLifecycle.class);
      ctx.refresh();
    } finally {
      ctx.close();
    }

    assertNull(readIoExec());
    assertNull(readComputationExec());
  }

  private static ExecutorService readIoExec() throws Exception {
    Field f = RxVirtualSchedulers.class.getDeclaredField("ioExec");
    f.setAccessible(true);
    return (ExecutorService) f.get(null);
  }

  private static ScheduledExecutorService readComputationExec() throws Exception {
    Field f = RxVirtualSchedulers.class.getDeclaredField("computationExec");
    f.setAccessible(true);
    return (ScheduledExecutorService) f.get(null);
  }
}

