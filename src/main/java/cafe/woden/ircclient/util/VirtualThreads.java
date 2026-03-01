package cafe.woden.ircclient.util;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Shared helpers for creating app-owned executors/threads on virtual threads. */
public final class VirtualThreads {
  private static final java.util.Set<ExecutorService> TRACKED_EXECUTORS =
      ConcurrentHashMap.newKeySet();
  private static final long TRACKED_SHUTDOWN_GRACE_MS = 1500L;

  private VirtualThreads() {}

  public static ThreadFactory namedFactory(String baseName) {
    String base = normalize(baseName);
    return Thread.ofVirtual().name(base + "-", 1).factory();
  }

  public static ExecutorService newSingleThreadExecutor(String baseName) {
    return track(Executors.newSingleThreadExecutor(namedFactory(baseName)));
  }

  public static ScheduledExecutorService newSingleThreadScheduledExecutor(String baseName) {
    return track(Executors.newSingleThreadScheduledExecutor(namedFactory(baseName)));
  }

  public static ExecutorService newThreadPerTaskExecutor(String baseName) {
    return track(Executors.newThreadPerTaskExecutor(namedFactory(baseName)));
  }

  public static ScheduledExecutorService newScheduledThreadPool(int poolSize, String baseName) {
    int size = Math.max(1, poolSize);
    return track(Executors.newScheduledThreadPool(size, namedFactory(baseName)));
  }

  public static Thread start(String name, Runnable task) {
    return Thread.ofVirtual().name(normalize(name)).start(task);
  }

  public static Thread unstarted(String name, Runnable task) {
    return Thread.ofVirtual().name(normalize(name)).unstarted(task);
  }

  public static int shutdownTrackedExecutorsNow() {
    long deadlineNanos =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TRACKED_SHUTDOWN_GRACE_MS);
    int count = 0;
    for (ExecutorService exec : java.util.List.copyOf(TRACKED_EXECUTORS)) {
      if (exec == null) continue;
      if (exec.isShutdown() || exec.isTerminated()) continue;
      try {
        exec.shutdown();
        count++;
      } catch (Exception ignored) {
      }
    }
    for (ExecutorService exec : java.util.List.copyOf(TRACKED_EXECUTORS)) {
      if (exec == null) continue;
      if (exec.isTerminated()) continue;
      if (awaitTermination(exec, deadlineNanos)) continue;
      try {
        exec.shutdownNow();
      } catch (Exception ignored) {
      }
      awaitTermination(exec, deadlineNanos);
    }
    TRACKED_EXECUTORS.clear();
    return count;
  }

  private static boolean awaitTermination(ExecutorService exec, long deadlineNanos) {
    if (exec == null) return true;
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

  private static <E extends ExecutorService> E track(E exec) {
    pruneTrackedExecutors();
    TRACKED_EXECUTORS.add(exec);
    return exec;
  }

  private static void pruneTrackedExecutors() {
    TRACKED_EXECUTORS.removeIf(exec -> exec == null || exec.isShutdown() || exec.isTerminated());
  }

  private static String normalize(String name) {
    String s = Objects.toString(name, "").trim();
    return s.isEmpty() ? "ircafe-vthread" : s;
  }
}
