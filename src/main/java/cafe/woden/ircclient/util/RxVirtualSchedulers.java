package cafe.woden.ircclient.util;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Shared RxJava schedulers backed by virtual-thread executors.
 */
public final class RxVirtualSchedulers {
  private static final int COMPUTATION_THREADS =
      Math.max(2, Runtime.getRuntime().availableProcessors());

  private static final ExecutorService IO_EXEC =
      VirtualThreads.newThreadPerTaskExecutor("ircafe-rx-io");

  private static final ScheduledExecutorService COMPUTATION_EXEC =
      Executors.newScheduledThreadPool(
          COMPUTATION_THREADS,
          VirtualThreads.namedFactory("ircafe-rx-computation")
      );

  private static final Scheduler IO = Schedulers.from(IO_EXEC);
  private static final Scheduler COMPUTATION = Schedulers.from(COMPUTATION_EXEC);

  private RxVirtualSchedulers() {
  }

  public static Scheduler io() {
    return IO;
  }

  public static Scheduler computation() {
    return COMPUTATION;
  }
}
