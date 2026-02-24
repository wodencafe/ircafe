package cafe.woden.ircclient.util;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/** Shared helpers for creating app-owned executors/threads on virtual threads. */
public final class VirtualThreads {
  private VirtualThreads() {}

  public static ThreadFactory namedFactory(String baseName) {
    String base = normalize(baseName);
    return Thread.ofVirtual().name(base + "-", 1).factory();
  }

  public static ExecutorService newSingleThreadExecutor(String baseName) {
    return Executors.newSingleThreadExecutor(namedFactory(baseName));
  }

  public static ScheduledExecutorService newSingleThreadScheduledExecutor(String baseName) {
    return Executors.newSingleThreadScheduledExecutor(namedFactory(baseName));
  }

  public static ExecutorService newThreadPerTaskExecutor(String baseName) {
    return Executors.newThreadPerTaskExecutor(namedFactory(baseName));
  }

  public static Thread start(String name, Runnable task) {
    return Thread.ofVirtual().name(normalize(name)).start(task);
  }

  public static Thread unstarted(String name, Runnable task) {
    return Thread.ofVirtual().name(normalize(name)).unstarted(task);
  }

  private static String normalize(String name) {
    String s = Objects.toString(name, "").trim();
    return s.isEmpty() ? "ircafe-vthread" : s;
  }
}
