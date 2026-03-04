package cafe.woden.ircclient.util;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Shared RxJava schedulers backed by virtual-thread executors. */
public final class RxVirtualSchedulers {
  private static final int COMPUTATION_THREADS =
      Math.max(2, Runtime.getRuntime().availableProcessors());
  private static final long DEFAULT_SHUTDOWN_GRACE_MS = 1500L;
  private static final String SHUTDOWN_GRACE_PROPERTY = "ircafe.rx.shutdown.grace.ms";

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
      long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownGraceMillis());
      shutdownExecutor(ioExec, deadlineNanos);
      shutdownExecutor(computationExec, deadlineNanos);
      ioExec = null;
      computationExec = null;
      ioScheduler = null;
      computationScheduler = null;
    }
  }

  private static void shutdownExecutor(ExecutorService exec, long deadlineNanos) {
    if (exec == null) return;
    if (exec.isShutdown() || exec.isTerminated()) return;
    try {
      exec.shutdown();
    } catch (Exception ignored) {
    }
    if (awaitTermination(exec, deadlineNanos)) return;
    try {
      exec.shutdownNow();
    } catch (Exception ignored) {
    }
    awaitTermination(exec, deadlineNanos);
  }

  private static boolean awaitTermination(ExecutorService exec, long deadlineNanos) {
    long remaining = deadlineNanos - System.nanoTime();
    if (remaining <= 0L) return exec.isTerminated();
    try {
      return exec.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return exec.isTerminated();
    } catch (Exception ignored) {
      return exec.isTerminated();
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

  private static long shutdownGraceMillis() {
    String raw = System.getProperty(SHUTDOWN_GRACE_PROPERTY);
    if (raw == null || raw.isBlank()) return DEFAULT_SHUTDOWN_GRACE_MS;
    try {
      return Math.max(0L, Long.parseLong(raw.trim()));
    } catch (NumberFormatException ignored) {
      return DEFAULT_SHUTDOWN_GRACE_MS;
    }
  }
}
