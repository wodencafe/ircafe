package cafe.woden.ircclient.util;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/** Shared RxJava schedulers backed by virtual-thread executors. */
public final class RxVirtualSchedulers {
  private static final int COMPUTATION_THREADS =
      Math.max(2, Runtime.getRuntime().availableProcessors());

  private static final Object LOCK = new Object();
  private static ExecutorService ioExec;
  private static ScheduledExecutorService computationExec;
  private static Scheduler ioScheduler;
  private static Scheduler computationScheduler;

  private RxVirtualSchedulers() {}

  public static Scheduler io() {
    synchronized (LOCK) {
      ensureInitializedLocked();
      return ioScheduler;
    }
  }

  public static Scheduler computation() {
    synchronized (LOCK) {
      ensureInitializedLocked();
      return computationScheduler;
    }
  }

  public static void shutdown() {
    synchronized (LOCK) {
      if (ioExec != null) {
        try {
          ioExec.shutdownNow();
        } catch (Exception ignored) {
        }
      }
      if (computationExec != null) {
        try {
          computationExec.shutdownNow();
        } catch (Exception ignored) {
        }
      }
      ioExec = null;
      computationExec = null;
      ioScheduler = null;
      computationScheduler = null;
    }
  }

  private static void ensureInitializedLocked() {
    if (ioExec == null || ioExec.isShutdown() || ioExec.isTerminated()) {
      ioExec = VirtualThreads.newThreadPerTaskExecutor("ircafe-rx-io");
      ioScheduler = Schedulers.from(ioExec);
    }
    if (computationExec == null || computationExec.isShutdown() || computationExec.isTerminated()) {
      computationExec =
          VirtualThreads.newScheduledThreadPool(COMPUTATION_THREADS, "ircafe-rx-computation");
      computationScheduler = Schedulers.from(computationExec);
    }
  }
}
