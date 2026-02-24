package cafe.woden.ircclient.logging.history;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

/** Shared in-memory waiter bus keyed by {@code serverId + target}. */
abstract class AbstractTargetWaiterBus<E> {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  private record Key(String serverId, String target) {}

  private final ConcurrentHashMap<Key, CopyOnWriteArrayList<CompletableFuture<E>>> waiters =
      new ConcurrentHashMap<>();

  private final ScheduledExecutorService scheduler;
  private final String timeoutLabel;
  private final String completionFailureMessage;
  private final Logger log;

  protected AbstractTargetWaiterBus(
      ScheduledExecutorService scheduler,
      String timeoutLabel,
      String completionFailureMessage,
      Logger log) {
    this.timeoutLabel = Objects.toString(timeoutLabel, "").trim();
    this.completionFailureMessage = Objects.toString(completionFailureMessage, "").trim();
    this.log = Objects.requireNonNull(log, "log");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
  }

  public final CompletableFuture<E> awaitNext(String serverId, String target, Duration timeout) {
    String sid = Objects.toString(serverId, "");
    String tgt = foldTarget(target);
    Duration to = normalizeTimeout(timeout);

    Key key = new Key(sid, tgt);
    CompletableFuture<E> f = new CompletableFuture<>();
    waiters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(f);

    final ScheduledFuture<?> timer =
        scheduler.schedule(
            () ->
                f.completeExceptionally(
                    new TimeoutException(
                        "Timed out waiting for " + timeoutLabel + ": " + sid + "/" + tgt)),
            to.toMillis(),
            TimeUnit.MILLISECONDS);

    f.whenComplete(
        (ok, err) -> {
          timer.cancel(false);
          CopyOnWriteArrayList<CompletableFuture<E>> list = waiters.get(key);
          if (list != null) {
            list.remove(f);
            if (list.isEmpty()) waiters.remove(key, list);
          }
        });

    return f;
  }

  public final void publish(E event) {
    if (event == null) return;
    Key key =
        new Key(
            Objects.toString(serverIdOf(event), ""),
            foldTarget(Objects.toString(targetOf(event), "")));
    CopyOnWriteArrayList<CompletableFuture<E>> list = waiters.remove(key);
    if (list == null || list.isEmpty()) return;

    for (CompletableFuture<E> f : list) {
      try {
        f.complete(event);
      } catch (Exception e) {
        log.debug(completionFailureMessage, e);
      }
    }
  }

  protected abstract String serverIdOf(E event);

  protected abstract String targetOf(E event);

  private static Duration normalizeTimeout(Duration timeout) {
    if (timeout == null || timeout.isNegative() || timeout.isZero()) return DEFAULT_TIMEOUT;
    return timeout;
  }

  private static String foldTarget(String target) {
    return (target == null ? "" : target).toLowerCase(Locale.ROOT);
  }
}
