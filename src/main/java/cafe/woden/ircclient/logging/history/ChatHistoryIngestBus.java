package cafe.woden.ircclient.logging.history;

import java.time.Duration;
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

/**
 * Small in-memory coordination bus used to bridge "request history" -> "history ingested".
 *
 * <p>Step 4E uses this to wait for the next CHATHISTORY ingest completion for a given
 * (serverId,target) before re-querying the local DB.
 */
@Component
public final class ChatHistoryIngestBus {

  private static final Logger log = LoggerFactory.getLogger(ChatHistoryIngestBus.class);

  public record IngestEvent(
      String serverId,
      String target,
      String batchId,
      int total,
      int inserted,
      long earliestTsEpochMs,
      long latestTsEpochMs
  ) {}

  private record Key(String serverId, String target) {}

  private final ConcurrentHashMap<Key, CopyOnWriteArrayList<CompletableFuture<IngestEvent>>> waiters =
      new ConcurrentHashMap<>();

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "ircafe-chathistory-bus");
    t.setDaemon(true);
    return t;
  });

  /** Await the next ingest event for this (serverId,target). */
  public CompletableFuture<IngestEvent> awaitNext(String serverId, String target, Duration timeout) {
    String sid = serverId == null ? "" : serverId;
    String tgt = target == null ? "" : target;
    if (timeout == null || timeout.isNegative() || timeout.isZero()) timeout = Duration.ofSeconds(5);

    Key key = new Key(sid, tgt);
    CompletableFuture<IngestEvent> f = new CompletableFuture<>();
    waiters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(f);

    final ScheduledFuture<?> timer = scheduler.schedule(
        () -> f.completeExceptionally(new TimeoutException("Timed out waiting for CHATHISTORY ingest: " + sid + "/" + tgt)),
        timeout.toMillis(),
        TimeUnit.MILLISECONDS
    );

    f.whenComplete((ok, err) -> {
      timer.cancel(false);
      CopyOnWriteArrayList<CompletableFuture<IngestEvent>> list = waiters.get(key);
      if (list != null) {
        list.remove(f);
        if (list.isEmpty()) waiters.remove(key, list);
      }
    });

    return f;
  }

  /** Publish an ingest event, completing any awaiting futures for this (serverId,target). */
  public void publish(IngestEvent event) {
    if (event == null) return;
    Key key = new Key(Objects.toString(event.serverId(), ""), Objects.toString(event.target(), ""));
    CopyOnWriteArrayList<CompletableFuture<IngestEvent>> list = waiters.remove(key);
    if (list == null || list.isEmpty()) return;

    for (CompletableFuture<IngestEvent> f : list) {
      try {
        f.complete(event);
      } catch (Exception e) {
        log.debug("CHATHISTORY ingest bus completion failed", e);
      }
    }
  }
}
