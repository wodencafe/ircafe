package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory bus that carries the raw CHATHISTORY batch entries. */
@Component
public final class ChatHistoryBatchBus {

  private static final Logger log = LoggerFactory.getLogger(ChatHistoryBatchBus.class);

  public record BatchEvent(
      String serverId,
      String target,
      String batchId,
      List<ChatHistoryEntry> entries,
      long earliestTsEpochMs,
      long latestTsEpochMs
  ) {
    public BatchEvent {
      entries = entries == null ? List.of() : List.copyOf(entries);
    }
  }

  private record Key(String serverId, String target) {}

  private static String foldTarget(String target) {
    return (target == null ? "" : target).toLowerCase(Locale.ROOT);
  }

  private final ConcurrentHashMap<Key, CopyOnWriteArrayList<CompletableFuture<BatchEvent>>> waiters =
      new ConcurrentHashMap<>();

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "ircafe-chathistory-batch-bus");
    t.setDaemon(true);
    return t;
  });

  public CompletableFuture<BatchEvent> awaitNext(String serverId, String target, Duration timeout) {
    String sid = serverId == null ? "" : serverId;
    String tgt = foldTarget(target);
    if (timeout == null || timeout.isNegative() || timeout.isZero()) timeout = Duration.ofSeconds(5);

    Key key = new Key(sid, tgt);
    CompletableFuture<BatchEvent> f = new CompletableFuture<>();
    waiters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(f);

    final ScheduledFuture<?> timer = scheduler.schedule(
        () -> f.completeExceptionally(
            new TimeoutException("Timed out waiting for CHATHISTORY batch: " + sid + "/" + tgt)
        ),
        timeout.toMillis(),
        TimeUnit.MILLISECONDS
    );

    f.whenComplete((ok, err) -> {
      timer.cancel(false);
      CopyOnWriteArrayList<CompletableFuture<BatchEvent>> list = waiters.get(key);
      if (list != null) {
        list.remove(f);
        if (list.isEmpty()) waiters.remove(key, list);
      }
    });

    return f;
  }

  public void publish(BatchEvent event) {
    if (event == null) return;
    Key key = new Key(
        Objects.toString(event.serverId(), ""),
        foldTarget(Objects.toString(event.target(), ""))
    );
    CopyOnWriteArrayList<CompletableFuture<BatchEvent>> list = waiters.remove(key);
    if (list == null || list.isEmpty()) return;

    for (CompletableFuture<BatchEvent> f : list) {
      try {
        f.complete(event);
      } catch (Exception e) {
        log.debug("CHATHISTORY batch bus completion failed", e);
      }
    }
  }
}
