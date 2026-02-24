package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Low-traffic hostmask resolution using the IRC USERHOST command.
 *
 * <p>Batches and rate-limits requests (with per-nick cooldown) to avoid flooding. Responses are
 * parsed by the IRC event layer (RPL 302) and used to enrich cached user info.
 */
@Component
public class UserhostQueryService {
  private static final Logger log = LoggerFactory.getLogger(UserhostQueryService.class);

  // USERHOST typically allows up to 5 nick arguments.
  private static final int ABSOLUTE_MAX_NICKS_PER_CMD = 5;

  // Conservative defaults (can be overridden via preferences/config):
  private static final Duration DEFAULT_MIN_CMD_INTERVAL = Duration.ofSeconds(7);
  private static final int DEFAULT_MAX_CMDS_PER_MINUTE = 6;
  private static final Duration DEFAULT_NICK_COOLDOWN = Duration.ofMinutes(30);
  private static final int DEFAULT_MAX_NICKS_PER_CMD = 5;
  private static final long NO_WAKE_NEEDED_MS = Long.MAX_VALUE;

  private final IrcClientService irc;
  private final ObjectProvider<UiSettingsBus> settingsBusProvider;
  private final ScheduledExecutorService exec;
  private final Object scheduleLock = new Object();
  private ScheduledFuture<?> scheduledTick;
  private long scheduledAtEpochMs = Long.MAX_VALUE;

  private final ConcurrentHashMap<String, ServerState> stateByServer = new ConcurrentHashMap<>();

  public UserhostQueryService(
      IrcClientService irc,
      ObjectProvider<UiSettingsBus> settingsBusProvider,
      @Qualifier(ExecutorConfig.USERHOST_QUERY_SCHEDULER) ScheduledExecutorService exec) {
    this.irc = irc;
    this.settingsBusProvider = settingsBusProvider;
    this.exec = exec;
  }

  @PreDestroy
  void shutdown() {
    synchronized (scheduleLock) {
      if (scheduledTick != null) {
        scheduledTick.cancel(false);
        scheduledTick = null;
      }
      scheduledAtEpochMs = Long.MAX_VALUE;
    }
  }

  public void clearServer(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    stateByServer.remove(sid);
  }

  public void enqueue(String serverId, Collection<String> nicks) {
    if (!config().enabled) return;
    String sid = norm(serverId);
    if (sid.isEmpty() || nicks == null || nicks.isEmpty()) return;

    ServerState st = stateByServer.computeIfAbsent(sid, k -> new ServerState());
    synchronized (st) {
      for (String n : nicks) {
        String nick = Objects.toString(n, "").trim();
        if (nick.isEmpty()) continue;
        st.queue.add(nick);
      }
    }

    requestTick(0);
  }

  private void tickAll() {
    try {
      UserhostConfig cfg = config();
      if (!cfg.enabled) {
        stateByServer.clear();
        return;
      }
      Instant now = Instant.now();
      long nextDelayMs = NO_WAKE_NEEDED_MS;
      for (Map.Entry<String, ServerState> e : stateByServer.entrySet()) {
        long serverDelayMs = tickServer(e.getKey(), e.getValue(), now, cfg);
        if (serverDelayMs < nextDelayMs) {
          nextDelayMs = serverDelayMs;
        }
      }
      if (nextDelayMs != NO_WAKE_NEEDED_MS) {
        requestTick(nextDelayMs);
      }
    } catch (Exception ex) {
      // Never allow the scheduler to die.
      log.debug("UserhostQueryService tick failed", ex);
    }
  }

  private long tickServer(String serverId, ServerState st, Instant now, UserhostConfig cfg) {
    List<String> batch = List.of();
    long nextDelayMs;

    synchronized (st) {
      cleanupCommandHistory(st, now);
      if (st.queue.isEmpty()) {
        return NO_WAKE_NEEDED_MS;
      }

      if (canSendNow(st, now, cfg)) {
        List<String> candidate = new ArrayList<>(cfg.maxNicksPerCommand);
        // Pull up to max nicks per command, skipping those still in cooldown.
        java.util.Iterator<String> it = st.queue.iterator();
        while (it.hasNext() && candidate.size() < cfg.maxNicksPerCommand) {
          String nick = it.next();
          String key = nick.toLowerCase(Locale.ROOT);
          Instant last = st.lastNickRequestAt.get(key);
          if (last != null && Duration.between(last, now).compareTo(cfg.nickCooldown) < 0) {
            // Still cooling down; leave it in the queue.
            continue;
          }
          it.remove();
          st.lastNickRequestAt.put(key, now);
          candidate.add(nick);
        }
        if (!candidate.isEmpty()) {
          st.lastCmdAt = now;
          st.cmdTimes.addLast(now);
          batch = candidate;
        }
      }

      nextDelayMs = computeNextDelayMs(st, now, cfg);
    }

    if (batch.isEmpty()) {
      return nextDelayMs;
    }

    // Send outside synchronized block.
    String line = "USERHOST " + String.join(" ", batch);

    // Requested: emit an info log whenever we perform a USERHOST lookup.
    // (We log per-batch to avoid excessive spam while still being traceable.)
    log.info("[{}] USERHOST lookup: {}", serverId, String.join(", ", batch));

    Disposable d =
        irc.sendRaw(serverId, line)
            .subscribe(
                () -> {}, err -> log.debug("USERHOST failed for {}: {}", serverId, err.toString()));
    // Disposable is intentionally not retained; the Completable completes quickly.
    if (d.isDisposed()) {
      // no-op
    }

    return nextDelayMs;
  }

  private void cleanupCommandHistory(ServerState st, Instant now) {
    while (!st.cmdTimes.isEmpty()
        && Duration.between(st.cmdTimes.peekFirst(), now).toSeconds() >= 60) {
      st.cmdTimes.removeFirst();
    }
  }

  private boolean canSendNow(ServerState st, Instant now, UserhostConfig cfg) {
    if (st.cmdTimes.size() >= cfg.maxCommandsPerMinute) return false;
    return st.lastCmdAt == null
        || Duration.between(st.lastCmdAt, now).compareTo(cfg.minCmdInterval) >= 0;
  }

  private long computeNextDelayMs(ServerState st, Instant now, UserhostConfig cfg) {
    if (st.queue.isEmpty()) {
      return NO_WAKE_NEEDED_MS;
    }

    Instant nextRateAllowedAt = now;
    if (st.lastCmdAt != null) {
      Instant afterMinInterval = st.lastCmdAt.plus(cfg.minCmdInterval);
      if (afterMinInterval.isAfter(nextRateAllowedAt)) {
        nextRateAllowedAt = afterMinInterval;
      }
    }
    if (st.cmdTimes.size() >= cfg.maxCommandsPerMinute && st.cmdTimes.peekFirst() != null) {
      Instant afterRateWindow = st.cmdTimes.peekFirst().plusSeconds(60);
      if (afterRateWindow.isAfter(nextRateAllowedAt)) {
        nextRateAllowedAt = afterRateWindow;
      }
    }

    Instant earliestNickReadyAt = null;
    for (String nick : st.queue) {
      Instant readyAt;
      Instant last = st.lastNickRequestAt.get(nick.toLowerCase(Locale.ROOT));
      if (last == null) {
        readyAt = now;
      } else {
        readyAt = last.plus(cfg.nickCooldown);
      }
      if (earliestNickReadyAt == null || readyAt.isBefore(earliestNickReadyAt)) {
        earliestNickReadyAt = readyAt;
      }
      if (now.equals(earliestNickReadyAt)) {
        break;
      }
    }

    if (earliestNickReadyAt == null) {
      return NO_WAKE_NEEDED_MS;
    }

    Instant wakeAt =
        nextRateAllowedAt.isAfter(earliestNickReadyAt) ? nextRateAllowedAt : earliestNickReadyAt;
    long delayMs = Duration.between(now, wakeAt).toMillis();
    return Math.max(0L, delayMs);
  }

  private void requestTick(long delayMs) {
    long safeDelayMs = Math.max(0L, delayMs);
    long targetEpochMs = System.currentTimeMillis() + safeDelayMs;
    synchronized (scheduleLock) {
      if (exec.isShutdown()) return;

      if (scheduledTick != null && !scheduledTick.isDone()) {
        if (targetEpochMs >= scheduledAtEpochMs) {
          return;
        }
        scheduledTick.cancel(false);
      }

      scheduledAtEpochMs = targetEpochMs;
      scheduledTick = exec.schedule(this::runScheduledTick, safeDelayMs, TimeUnit.MILLISECONDS);
    }
  }

  private void runScheduledTick() {
    synchronized (scheduleLock) {
      scheduledTick = null;
      scheduledAtEpochMs = Long.MAX_VALUE;
    }
    tickAll();
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }

  private UserhostConfig config() {
    UiSettingsBus bus = settingsBusProvider.getIfAvailable();
    UiSettings s = bus != null ? bus.get() : null;

    boolean enabled = s == null || s.userhostDiscoveryEnabled();

    Duration minInterval = DEFAULT_MIN_CMD_INTERVAL;
    int maxPerMinute = DEFAULT_MAX_CMDS_PER_MINUTE;
    Duration cooldown = DEFAULT_NICK_COOLDOWN;
    int maxNicks = DEFAULT_MAX_NICKS_PER_CMD;

    if (s != null) {
      minInterval = Duration.ofSeconds(Math.max(1, s.userhostMinIntervalSeconds()));
      maxPerMinute = Math.max(1, s.userhostMaxCommandsPerMinute());
      cooldown = Duration.ofMinutes(Math.max(1, s.userhostNickCooldownMinutes()));
      maxNicks = Math.max(1, Math.min(ABSOLUTE_MAX_NICKS_PER_CMD, s.userhostMaxNicksPerCommand()));
    }

    return new UserhostConfig(enabled, minInterval, maxPerMinute, cooldown, maxNicks);
  }

  private record UserhostConfig(
      boolean enabled,
      Duration minCmdInterval,
      int maxCommandsPerMinute,
      Duration nickCooldown,
      int maxNicksPerCommand) {}

  private static final class ServerState {
    final Set<String> queue = new LinkedHashSet<>();
    final Map<String, Instant> lastNickRequestAt = new ConcurrentHashMap<>();
    final Deque<Instant> cmdTimes = new ArrayDeque<>();
    Instant lastCmdAt;
  }
}
