package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;

/**
 * Low-traffic hostmask resolution using the IRC USERHOST command.
 *
 * <p>Batches and rate-limits requests (with per-nick cooldown) to avoid flooding.
 * Responses are parsed by the IRC event layer (RPL 302) and used to enrich cached user info.
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

  private final IrcClientService irc;
  private final ObjectProvider<UiSettingsBus> settingsBusProvider;
  private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "userhost-query");
    t.setDaemon(true);
    return t;
  });

  private final ConcurrentHashMap<String, ServerState> stateByServer = new ConcurrentHashMap<>();

  public UserhostQueryService(IrcClientService irc, ObjectProvider<UiSettingsBus> settingsBusProvider) {
    this.irc = irc;
    this.settingsBusProvider = settingsBusProvider;
    // One global tick; each server has its own rate-limited state.
    exec.scheduleWithFixedDelay(this::tickAll, 0, 1, TimeUnit.SECONDS);
  }

  @PreDestroy
  void shutdown() {
    exec.shutdownNow();
  }

  /** Clear all queued state for a server (e.g., on disconnect). */
  public void clearServer(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    stateByServer.remove(sid);
  }

  /**
   * Enqueue nicks for hostmask resolution via USERHOST.
   *
   * <p>Nicks are de-duplicated and applied with per-nick cooldown.
   */
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
  }

  private void tickAll() {
    try {
      UserhostConfig cfg = config();
      if (!cfg.enabled) {
        stateByServer.clear();
        return;
      }
      Instant now = Instant.now();
      for (Map.Entry<String, ServerState> e : stateByServer.entrySet()) {
        tickServer(e.getKey(), e.getValue(), now, cfg);
      }
    } catch (Exception ex) {
      // Never allow the scheduler to die.
      log.debug("UserhostQueryService tick failed", ex);
    }
  }

  private void tickServer(String serverId, ServerState st, Instant now, UserhostConfig cfg) {
    java.util.List<String> batch;
    synchronized (st) {
      // Cleanup command history (last minute).
      while (!st.cmdTimes.isEmpty() && Duration.between(st.cmdTimes.peekFirst(), now).toSeconds() >= 60) {
        st.cmdTimes.removeFirst();
      }

      // Respect max cmds/min and min interval.
      if (st.cmdTimes.size() >= cfg.maxCommandsPerMinute) return;
      if (st.lastCmdAt != null && Duration.between(st.lastCmdAt, now).compareTo(cfg.minCmdInterval) < 0) return;

      if (st.queue.isEmpty()) return;

      batch = new ArrayList<>(cfg.maxNicksPerCommand);
      // Pull up to max nicks per command, skipping those still in cooldown.
      java.util.Iterator<String> it = st.queue.iterator();
      while (it.hasNext() && batch.size() < cfg.maxNicksPerCommand) {
        String nick = it.next();
        String key = nick.toLowerCase(Locale.ROOT);
        Instant last = st.lastNickRequestAt.get(key);
        if (last != null && Duration.between(last, now).compareTo(cfg.nickCooldown) < 0) {
          // Still cooling down; leave it in the queue.
          continue;
        }
        it.remove();
        st.lastNickRequestAt.put(key, now);
        batch.add(nick);
      }

      if (batch.isEmpty()) return;
      st.lastCmdAt = now;
      st.cmdTimes.addLast(now);
    }

    // Send outside synchronized block.
    String line = "USERHOST " + String.join(" ", batch);

    // Requested: emit an info log whenever we perform a USERHOST lookup.
    // (We log per-batch to avoid excessive spam while still being traceable.)
    log.info("[{}] USERHOST lookup: {}", serverId, String.join(", ", batch));

    Disposable d = irc.sendRaw(serverId, line).subscribe(
        () -> {},
        err -> log.debug("USERHOST failed for {}: {}", serverId, err.toString())
    );
    // Disposable is intentionally not retained; the Completable completes quickly.
    if (d.isDisposed()) {
      // no-op
    }
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
      int maxNicksPerCommand
  ) {}

  private static final class ServerState {
    final Set<String> queue = new LinkedHashSet<>();
    final Map<String, Instant> lastNickRequestAt = new ConcurrentHashMap<>();
    final Deque<Instant> cmdTimes = new ArrayDeque<>();
    Instant lastCmdAt;
  }
}
