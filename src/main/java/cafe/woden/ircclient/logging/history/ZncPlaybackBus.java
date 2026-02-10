package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.time.Duration;
import java.time.Instant;
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

/** In-memory bus that carries ZNC Playback module ranges. */
@Component
public final class ZncPlaybackBus {

  private static final Logger log = LoggerFactory.getLogger(ZncPlaybackBus.class);

  public record PlaybackEvent(
      String serverId,
      String target,
      Instant fromInclusive,
      Instant toInclusive,
      List<ChatHistoryEntry> entries,
      long earliestTsEpochMs,
      long latestTsEpochMs
  ) {
    public PlaybackEvent {
      entries = entries == null ? List.of() : List.copyOf(entries);
      fromInclusive = fromInclusive == null ? Instant.EPOCH : fromInclusive;
      toInclusive = toInclusive == null ? Instant.EPOCH : toInclusive;
    }
  }

  private record Key(String serverId, String target) {}

  private final ConcurrentHashMap<Key, CopyOnWriteArrayList<CompletableFuture<PlaybackEvent>>> waiters =
      new ConcurrentHashMap<>();

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "ircafe-znc-playback-bus");
    t.setDaemon(true);
    return t;
  });

  public CompletableFuture<PlaybackEvent> awaitNext(String serverId, String target, Duration timeout) {
    String sid = serverId == null ? "" : serverId;
    String tgt = target == null ? "" : target;
    if (timeout == null || timeout.isNegative() || timeout.isZero()) timeout = Duration.ofSeconds(5);

    Key key = new Key(sid, tgt);
    CompletableFuture<PlaybackEvent> f = new CompletableFuture<>();
    waiters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(f);

    final ScheduledFuture<?> timer = scheduler.schedule(
        () -> f.completeExceptionally(
            new TimeoutException("Timed out waiting for ZNC playback: " + sid + "/" + tgt)
        ),
        timeout.toMillis(),
        TimeUnit.MILLISECONDS
    );

    f.whenComplete((ok, err) -> {
      timer.cancel(false);
      CopyOnWriteArrayList<CompletableFuture<PlaybackEvent>> list = waiters.get(key);
      if (list != null) {
        list.remove(f);
        if (list.isEmpty()) waiters.remove(key, list);
      }
    });

    return f;
  }

  public void publish(PlaybackEvent event) {
    if (event == null) return;
    Key key = new Key(Objects.toString(event.serverId(), ""), Objects.toString(event.target(), ""));
    CopyOnWriteArrayList<CompletableFuture<PlaybackEvent>> list = waiters.remove(key);
    if (list == null || list.isEmpty()) return;

    for (CompletableFuture<PlaybackEvent> f : list) {
      try {
        f.complete(event);
      } catch (Exception e) {
        log.debug("ZNC playback bus completion failed", e);
      }
    }
  }
}
