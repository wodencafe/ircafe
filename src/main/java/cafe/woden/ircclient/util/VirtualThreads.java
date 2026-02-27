package cafe.woden.ircclient.util;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/** Shared helpers for creating app-owned executors/threads on virtual threads. */
public final class VirtualThreads {
  private static final java.util.Set<ExecutorService> TRACKED_EXECUTORS =
      ConcurrentHashMap.newKeySet();

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
    int count = 0;
    for (ExecutorService exec : java.util.List.copyOf(TRACKED_EXECUTORS)) {
      if (exec == null) continue;
      if (exec.isShutdown() || exec.isTerminated()) continue;
      try {
        exec.shutdownNow();
        count++;
      } catch (Exception ignored) {
      }
    }
    TRACKED_EXECUTORS.clear();
    return count;
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
