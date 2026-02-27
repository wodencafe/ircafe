package cafe.woden.ircclient.irc.enrichment;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Pure planner for rate-limited user info enrichment.
 *
 * <p>This class does not send IRC commands. It only:
 *
 * <ul>
 *   <li>tracks per-server queues (USERHOST / WHOIS)
 *   <li>applies per-kind rate limits (min interval + cmds/min) and per-nick cooldown
 *   <li>returns the next command that would be safe to run
 * </ul>
 *
 * <p>Execution (sending IRC commands and handling replies) is handled elsewhere.
 */
@Component
public final class UserInfoEnrichmentPlanner {
  // USERHOST typically allows up to 5 nick arguments.
  public static final int ABSOLUTE_MAX_USERHOST_NICKS_PER_CMD = 5;

  // WHO (channel scan) defaults: conservative to avoid flooding.
  private static final Duration WHO_CHANNEL_MIN_CMD_INTERVAL = Duration.ofSeconds(30);
  private static final int WHO_CHANNEL_MAX_COMMANDS_PER_MINUTE = 2;
  private static final Duration WHO_CHANNEL_TARGET_COOLDOWN = Duration.ofMinutes(10);
  private static final Duration PROBE_STATE_TTL = Duration.ofHours(24);
  private static final int MAX_TRACKED_TARGET_STATE_PER_KIND = 8_192;
  private static final long NO_WAKE_NEEDED_MS = Long.MAX_VALUE;
  private static final long MISSING_EPOCH_MILLIS = Long.MIN_VALUE;
  private static final int MISSING_COUNT = Integer.MIN_VALUE;

  public enum ProbeKind {
    WHO_CHANNEL,
    USERHOST,
    WHOIS
  }

  /** Settings snapshot (typically derived from {@code UiSettings}). */
  public record Settings(
      boolean enabled,
      Duration userhostMinCmdInterval,
      int userhostMaxCommandsPerMinute,
      Duration userhostNickCooldown,
      int userhostMaxNicksPerCommand,
      boolean whoisFallbackEnabled,
      Duration whoisMinCmdInterval,
      Duration whoisNickCooldown,
      boolean periodicRefreshEnabled,
      Duration periodicRefreshInterval,
      int periodicRefreshNicksPerTick) {
    public Settings {
      Objects.requireNonNull(userhostMinCmdInterval, "userhostMinCmdInterval");
      Objects.requireNonNull(userhostNickCooldown, "userhostNickCooldown");
      Objects.requireNonNull(whoisMinCmdInterval, "whoisMinCmdInterval");
      Objects.requireNonNull(whoisNickCooldown, "whoisNickCooldown");
      Objects.requireNonNull(periodicRefreshInterval, "periodicRefreshInterval");

      if (userhostMaxCommandsPerMinute < 1) userhostMaxCommandsPerMinute = 1;
      if (userhostMaxNicksPerCommand < 1) userhostMaxNicksPerCommand = 1;
      if (userhostMaxNicksPerCommand > ABSOLUTE_MAX_USERHOST_NICKS_PER_CMD) {
        userhostMaxNicksPerCommand = ABSOLUTE_MAX_USERHOST_NICKS_PER_CMD;
      }

      if (periodicRefreshNicksPerTick < 1) periodicRefreshNicksPerTick = 1;
      if (periodicRefreshNicksPerTick > 10) periodicRefreshNicksPerTick = 10;
    }

    /**
     * For WHOIS, we currently only have a min-interval knob; this provides a derived hint.
     *
     * <p>Example: 45s interval ~= 1.33/min, clamped to at least 1/min.
     */
    public int whoisMaxCommandsPerMinuteHint() {
      long sec = Math.max(1, whoisMinCmdInterval.getSeconds());
      return (int) Math.max(1, 60 / sec);
    }
  }

  /** A planned command to execute. */
  public record PlannedCommand(ProbeKind kind, String serverId, List<String> nicks) {
    public PlannedCommand {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(serverId, "serverId");
      Objects.requireNonNull(nicks, "nicks");
    }

    /** A best-effort raw IRC line for this command. */
    public String rawLine() {
      return switch (kind) {
        case WHO_CHANNEL -> "WHO " + (nicks.isEmpty() ? "" : nicks.getFirst());
        case USERHOST -> "USERHOST " + String.join(" ", nicks);
        case WHOIS -> "WHOIS " + (nicks.isEmpty() ? "" : nicks.getFirst());
      };
    }
  }

  private final ConcurrentHashMap<String, ServerState> stateByServer = new ConcurrentHashMap<>();

  public void clearServer(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    stateByServer.remove(sid);
  }

  /**
   * Update the roster snapshot used for periodic refresh (if enabled).
   *
   * <p>This does not enqueue any probes by itself; it only gives the planner a list of candidates.
   */
  public void setRosterSnapshot(String serverId, Collection<String> nicks) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;

    ServerState st = stateByServer.computeIfAbsent(sid, k -> new ServerState());
    synchronized (st) {
      st.roster.replaceAll(nicks);
    }
  }

  public void enqueueUserhost(String serverId, Collection<String> nicks) {
    enqueue(serverId, nicks, ProbeKind.USERHOST);
  }

  public void enqueueWhois(String serverId, Collection<String> nicks) {
    enqueue(serverId, nicks, ProbeKind.WHOIS);
  }

  public void enqueueWhoChannel(String serverId, String channel) {
    String sid = norm(serverId);
    String ch = norm(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;
    enqueue(sid, java.util.List.of(ch), ProbeKind.WHO_CHANNEL);
  }

  public void enqueueWhoChannelPrioritized(String serverId, String channel) {
    String sid = norm(serverId);
    String ch = norm(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;
    enqueuePrioritized(sid, java.util.List.of(ch), ProbeKind.WHO_CHANNEL);
  }

  /**
   * Enqueue probes, treating the provided nick order as high priority.
   *
   * <p>This will promote the given nicks to the front of the queue (stable order), so recently
   * active users can be enriched sooner without increasing overall traffic.
   */
  public void enqueueUserhostPrioritized(String serverId, List<String> nicks) {
    enqueuePrioritized(serverId, nicks, ProbeKind.USERHOST);
  }

  /** Enqueue WHOIS probes as high priority (see {@link #enqueueUserhostPrioritized}). */
  public void enqueueWhoisPrioritized(String serverId, List<String> nicks) {
    enqueuePrioritized(serverId, nicks, ProbeKind.WHOIS);
  }

  public void enqueuePrioritized(String serverId, List<String> nicks, ProbeKind kind) {
    String sid = norm(serverId);
    if (sid.isEmpty() || nicks == null || nicks.isEmpty() || kind == null) return;

    ServerState st = stateByServer.computeIfAbsent(sid, k -> new ServerState());
    synchronized (st) {
      ProbeState ps = st.state(kind);

      // Build a new ordered set: preferred nicks first, then the existing queue.
      LinkedHashSet<String> newQueue = new LinkedHashSet<>();
      for (String n : nicks) {
        String nick = Objects.toString(n, "").trim();
        if (nick.isEmpty()) continue;
        newQueue.add(nick);
      }
      for (String existing : ps.queue) {
        if (existing == null || existing.isBlank()) continue;
        newQueue.add(existing);
      }

      ps.queue.clear();
      ps.queue.addAll(newQueue);
    }
  }

  public void enqueue(String serverId, Collection<String> nicks, ProbeKind kind) {
    String sid = norm(serverId);
    if (sid.isEmpty() || nicks == null || nicks.isEmpty() || kind == null) return;

    ServerState st = stateByServer.computeIfAbsent(sid, k -> new ServerState());
    synchronized (st) {
      ProbeState ps = st.state(kind);
      for (String n : nicks) {
        String nick = Objects.toString(n, "").trim();
        if (nick.isEmpty()) continue;
        ps.queue.add(nick);
      }
    }
  }

  /**
   * Records completion of a WHOIS probe, allowing the planner to apply staleness/backoff.
   *
   * <p>This is used to avoid repeatedly WHOIS'ing the same nick forever when account details appear
   * unavailable (e.g. no IRCv3 and no reliable WHOIS account numeric).
   */
  public void noteWhoisProbeCompleted(
      String serverId,
      String nick,
      Instant at,
      boolean sawAccount,
      boolean accountNumericSupported,
      Settings cfg) {
    String sid = norm(serverId);
    String nk = norm(nick);
    if (sid.isEmpty() || nk.isEmpty() || at == null || cfg == null) return;
    if (!cfg.enabled || !cfg.whoisFallbackEnabled) return;

    ServerState st = stateByServer.get(sid);
    if (st == null) return;

    String key = nk.toLowerCase(Locale.ROOT);
    synchronized (st) {
      ProbeState ps = st.whois;

      // If the connection has proven it supports 330 (or we actually saw an account),
      // we don't need special backoff for this nick.
      if (accountNumericSupported || sawAccount) {
        ps.snoozeUntilByNickLower.remove(key);
        ps.ambiguousWhoisMissCountByNickLower.remove(key);
        return;
      }

      // Ambiguous result: we initiated WHOIS, it completed, but we didn't learn an account
      // and we still haven't seen any 330 on this connection. Back off aggressively.
      int miss = ps.ambiguousWhoisMissCountByNickLower.getOrZero(key) + 1;
      ps.ambiguousWhoisMissCountByNickLower.put(key, miss);

      Duration base = cfg.whoisNickCooldown;
      if (base.compareTo(Duration.ofHours(6)) < 0) base = Duration.ofHours(6);

      // Exponential backoff, capped.
      Duration max = Duration.ofHours(24);
      long factor = 1L;
      for (int i = 1; i < miss && i < 6; i++) factor = factor * 2L;
      Duration backoff;
      try {
        backoff = base.multipliedBy(factor);
      } catch (ArithmeticException ex) {
        backoff = max;
      }
      if (backoff.compareTo(max) > 0) backoff = max;

      ps.snoozeUntilByNickLower.putInstant(key, at.plus(backoff));
    }
  }

  /**
   * Returns the next safe command to run for a specific server.
   *
   * <p>Priority order: USERHOST first (lighter), then WHOIS (heavier) when enabled.
   */
  public Optional<PlannedCommand> pollNext(String serverId, Instant now, Settings cfg) {
    String sid = norm(serverId);
    if (sid.isEmpty() || cfg == null || now == null || !cfg.enabled) return Optional.empty();
    ServerState st = stateByServer.get(sid);
    if (st == null) return Optional.empty();

    Optional<PlannedCommand> w = pollWhoChannel(sid, st, now);
    if (w.isPresent()) return w;

    Optional<PlannedCommand> u = pollUserhost(sid, st, now, cfg);
    if (u.isPresent()) return u;
    if (!cfg.whoisFallbackEnabled) return Optional.empty();
    return pollWhois(sid, st, now, cfg);
  }

  /**
   * Returns the next safe command for any server.
   *
   * <p>This is useful for a single-threaded executor loop.
   */
  public Optional<PlannedCommand> pollAnyNext(Instant now, Settings cfg) {
    if (cfg == null || now == null || !cfg.enabled) return Optional.empty();
    for (String sid : stateByServer.keySet()) {
      Optional<PlannedCommand> cmd = pollNext(sid, now, cfg);
      if (cmd.isPresent()) return cmd;
    }
    return Optional.empty();
  }

  /**
   * When periodic refresh is enabled, this will enqueue a small round-robin slice of the roster.
   *
   * <p>This only prepares queues; sending still happens through {@link #pollNext}.
   */
  public void maybeEnqueuePeriodicRefresh(String serverId, Instant now, Settings cfg) {
    String sid = norm(serverId);
    if (sid.isEmpty() || cfg == null || now == null) return;
    if (!cfg.enabled || !cfg.periodicRefreshEnabled) return;

    ServerState st = stateByServer.computeIfAbsent(sid, k -> new ServerState());
    synchronized (st) {
      if (st.nextPeriodicAt != null && now.isBefore(st.nextPeriodicAt)) return;
      st.nextPeriodicAt = now.plus(cfg.periodicRefreshInterval);

      List<String> slice = st.roster.pickNext(cfg.periodicRefreshNicksPerTick);
      if (!slice.isEmpty()) {
        st.userhost.queue.addAll(slice);
      }
    }
  }

  /**
   * Returns the earliest delay until any queued probe for this server could become sendable.
   *
   * <p>This is a non-mutating scheduling hint for event-driven executors.
   */
  public long nextReadyDelayMs(String serverId, Instant now, Settings cfg) {
    String sid = norm(serverId);
    if (sid.isEmpty() || cfg == null || now == null || !cfg.enabled) return NO_WAKE_NEEDED_MS;
    ServerState st = stateByServer.get(sid);
    if (st == null) return NO_WAKE_NEEDED_MS;

    synchronized (st) {
      cleanupWindow(st.whoChannel, now);
      cleanupWindow(st.userhost, now);
      cleanupWindow(st.whois, now);

      long nextDelayMs = NO_WAKE_NEEDED_MS;
      nextDelayMs =
          Math.min(
              nextDelayMs,
              computeProbeNextDelayMs(
                  st.whoChannel,
                  now,
                  WHO_CHANNEL_MIN_CMD_INTERVAL,
                  WHO_CHANNEL_MAX_COMMANDS_PER_MINUTE,
                  WHO_CHANNEL_TARGET_COOLDOWN,
                  null));
      nextDelayMs =
          Math.min(
              nextDelayMs,
              computeProbeNextDelayMs(
                  st.userhost,
                  now,
                  cfg.userhostMinCmdInterval,
                  cfg.userhostMaxCommandsPerMinute,
                  cfg.userhostNickCooldown,
                  null));
      if (cfg.whoisFallbackEnabled) {
        nextDelayMs =
            Math.min(
                nextDelayMs,
                computeProbeNextDelayMs(
                    st.whois,
                    now,
                    cfg.whoisMinCmdInterval,
                    cfg.whoisMaxCommandsPerMinuteHint(),
                    cfg.whoisNickCooldown,
                    st.whois.snoozeUntilByNickLower));
      }
      return nextDelayMs;
    }
  }

  /**
   * Returns when periodic refresh should run next for this server, if periodic refresh is active.
   */
  public long nextPeriodicRefreshDelayMs(String serverId, Instant now, Settings cfg) {
    String sid = norm(serverId);
    if (sid.isEmpty()
        || cfg == null
        || now == null
        || !cfg.enabled
        || !cfg.periodicRefreshEnabled) {
      return NO_WAKE_NEEDED_MS;
    }
    ServerState st = stateByServer.get(sid);
    if (st == null) return NO_WAKE_NEEDED_MS;

    synchronized (st) {
      if (st.roster.snapshot.isEmpty()) return NO_WAKE_NEEDED_MS;
      if (st.nextPeriodicAt == null || !now.isBefore(st.nextPeriodicAt)) return 0L;
      long delayMs = Duration.between(now, st.nextPeriodicAt).toMillis();
      return Math.max(0L, delayMs);
    }
  }

  private Optional<PlannedCommand> pollWhoChannel(String serverId, ServerState st, Instant now) {
    synchronized (st) {
      ProbeState ps = st.whoChannel;
      cleanupWindow(ps, now);
      if (!canSend(ps, now, WHO_CHANNEL_MIN_CMD_INTERVAL, WHO_CHANNEL_MAX_COMMANDS_PER_MINUTE))
        return Optional.empty();
      if (ps.queue.isEmpty()) return Optional.empty();

      long nowEpochMs = now.toEpochMilli();
      var it = ps.queue.iterator();
      while (it.hasNext()) {
        String channel = it.next();
        String key = channel.toLowerCase(Locale.ROOT);
        long lastEpochMs = ps.lastNickRequestAt.getEpochMillis(key);
        if (isStillCoolingDown(nowEpochMs, lastEpochMs, WHO_CHANNEL_TARGET_COOLDOWN)) {
          continue;
        }
        it.remove();
        ps.lastNickRequestAt.putEpochMillis(key, nowEpochMs);
        noteSent(ps, now);
        return Optional.of(
            new PlannedCommand(ProbeKind.WHO_CHANNEL, serverId, java.util.List.of(channel)));
      }

      return Optional.empty();
    }
  }

  private Optional<PlannedCommand> pollUserhost(
      String serverId, ServerState st, Instant now, Settings cfg) {
    List<String> batch;
    synchronized (st) {
      ProbeState ps = st.userhost;
      cleanupWindow(ps, now);
      if (!canSend(ps, now, cfg.userhostMinCmdInterval, cfg.userhostMaxCommandsPerMinute))
        return Optional.empty();
      if (ps.queue.isEmpty()) return Optional.empty();

      long nowEpochMs = now.toEpochMilli();
      batch = new ArrayList<>(cfg.userhostMaxNicksPerCommand);
      var it = ps.queue.iterator();
      while (it.hasNext() && batch.size() < cfg.userhostMaxNicksPerCommand) {
        String nick = it.next();
        String key = nick.toLowerCase(Locale.ROOT);
        long lastEpochMs = ps.lastNickRequestAt.getEpochMillis(key);
        if (isStillCoolingDown(nowEpochMs, lastEpochMs, cfg.userhostNickCooldown)) {
          continue;
        }
        it.remove();
        ps.lastNickRequestAt.putEpochMillis(key, nowEpochMs);
        batch.add(nick);
      }

      if (batch.isEmpty()) return Optional.empty();
      noteSent(ps, now);
    }
    return Optional.of(new PlannedCommand(ProbeKind.USERHOST, serverId, List.copyOf(batch)));
  }

  private Optional<PlannedCommand> pollWhois(
      String serverId, ServerState st, Instant now, Settings cfg) {
    List<String> chosen;
    synchronized (st) {
      ProbeState ps = st.whois;
      cleanupWindow(ps, now);
      // WHOIS is intentionally constrained: min interval + derived cmds/min hint.
      if (!canSend(ps, now, cfg.whoisMinCmdInterval, cfg.whoisMaxCommandsPerMinuteHint()))
        return Optional.empty();
      if (ps.queue.isEmpty()) return Optional.empty();

      long nowEpochMs = now.toEpochMilli();
      chosen = new ArrayList<>(1);
      var it = ps.queue.iterator();
      while (it.hasNext() && chosen.isEmpty()) {
        String nick = it.next();
        String key = nick.toLowerCase(Locale.ROOT);
        long snoozeUntilEpochMs = ps.snoozeUntilByNickLower.getEpochMillis(key);
        if (snoozeUntilEpochMs != MISSING_EPOCH_MILLIS && nowEpochMs < snoozeUntilEpochMs) {
          continue;
        }
        long lastEpochMs = ps.lastNickRequestAt.getEpochMillis(key);
        if (isStillCoolingDown(nowEpochMs, lastEpochMs, cfg.whoisNickCooldown)) {
          continue;
        }
        it.remove();
        ps.lastNickRequestAt.putEpochMillis(key, nowEpochMs);
        chosen.add(nick);
      }

      if (chosen.isEmpty()) return Optional.empty();
      noteSent(ps, now);
    }
    return Optional.of(new PlannedCommand(ProbeKind.WHOIS, serverId, List.copyOf(chosen)));
  }

  private static void cleanupWindow(ProbeState ps, Instant now) {
    while (!ps.cmdTimes.isEmpty()
        && Duration.between(ps.cmdTimes.peekFirst(), now).toSeconds() >= 60) {
      ps.cmdTimes.removeFirst();
    }
    ps.lastNickRequestAt.removeIfBefore(now.minus(PROBE_STATE_TTL));
    ps.snoozeUntilByNickLower.removeIfNotAfter(now);
    ps.ambiguousWhoisMissCountByNickLower.removeKeysNotPresentIn(ps.snoozeUntilByNickLower);
  }

  private static boolean canSend(
      ProbeState ps, Instant now, Duration minInterval, int maxPerMinute) {
    if (ps.cmdTimes.size() >= maxPerMinute) return false;
    return ps.lastCmdAt == null || Duration.between(ps.lastCmdAt, now).compareTo(minInterval) >= 0;
  }

  private static long computeProbeNextDelayMs(
      ProbeState ps,
      Instant now,
      Duration minInterval,
      int maxPerMinute,
      Duration targetCooldown,
      BoundedNickInstantStore notBeforeByNickLower) {
    if (ps.queue.isEmpty()) return NO_WAKE_NEEDED_MS;
    long nowEpochMs = now.toEpochMilli();

    long nextRateAllowedAtEpochMs = nowEpochMs;
    if (ps.lastCmdAt != null) {
      long afterMinIntervalEpochMs =
          safeAddEpochMillis(ps.lastCmdAt.toEpochMilli(), Math.max(0L, minInterval.toMillis()));
      if (afterMinIntervalEpochMs > nextRateAllowedAtEpochMs) {
        nextRateAllowedAtEpochMs = afterMinIntervalEpochMs;
      }
    }
    if (ps.cmdTimes.size() >= maxPerMinute && ps.cmdTimes.peekFirst() != null) {
      long afterRateWindowEpochMs =
          safeAddEpochMillis(ps.cmdTimes.peekFirst().toEpochMilli(), 60_000L);
      if (afterRateWindowEpochMs > nextRateAllowedAtEpochMs) {
        nextRateAllowedAtEpochMs = afterRateWindowEpochMs;
      }
    }

    long earliestTargetReadyAtEpochMs = Long.MAX_VALUE;
    boolean foundReadyAt = false;
    long targetCooldownMs = Math.max(0L, targetCooldown.toMillis());
    for (String target : ps.queue) {
      String key = Objects.toString(target, "").trim().toLowerCase(Locale.ROOT);
      if (key.isEmpty()) continue;

      long readyAtEpochMs = nowEpochMs;
      long lastEpochMs = ps.lastNickRequestAt.getEpochMillis(key);
      if (lastEpochMs != MISSING_EPOCH_MILLIS) {
        long afterCooldownEpochMs = safeAddEpochMillis(lastEpochMs, targetCooldownMs);
        if (afterCooldownEpochMs > readyAtEpochMs) {
          readyAtEpochMs = afterCooldownEpochMs;
        }
      }
      if (notBeforeByNickLower != null) {
        long notBeforeEpochMs = notBeforeByNickLower.getEpochMillis(key);
        if (notBeforeEpochMs != MISSING_EPOCH_MILLIS && notBeforeEpochMs > readyAtEpochMs) {
          readyAtEpochMs = notBeforeEpochMs;
        }
      }

      if (!foundReadyAt || readyAtEpochMs < earliestTargetReadyAtEpochMs) {
        earliestTargetReadyAtEpochMs = readyAtEpochMs;
        foundReadyAt = true;
        if (readyAtEpochMs <= nowEpochMs) {
          break;
        }
      }
    }

    if (!foundReadyAt) return NO_WAKE_NEEDED_MS;

    long wakeAtEpochMs = Math.max(nextRateAllowedAtEpochMs, earliestTargetReadyAtEpochMs);
    return Math.max(0L, wakeAtEpochMs - nowEpochMs);
  }

  private static void noteSent(ProbeState ps, Instant at) {
    ps.lastCmdAt = at;
    ps.cmdTimes.addLast(at);
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }

  private static boolean isStillCoolingDown(long nowEpochMs, long lastEpochMs, Duration cooldown) {
    if (lastEpochMs == MISSING_EPOCH_MILLIS) return false;
    long cooldownMs = Math.max(0L, cooldown.toMillis());
    return nowEpochMs < safeAddEpochMillis(lastEpochMs, cooldownMs);
  }

  private static long safeAddEpochMillis(long baseEpochMs, long deltaMs) {
    if (deltaMs <= 0L) return baseEpochMs;
    if (Long.MAX_VALUE - baseEpochMs < deltaMs) return Long.MAX_VALUE;
    return baseEpochMs + deltaMs;
  }

  private static final class ServerState {
    final ProbeState whoChannel = new ProbeState();
    final ProbeState userhost = new ProbeState();
    final ProbeState whois = new ProbeState();
    final RosterCursor roster = new RosterCursor();
    Instant nextPeriodicAt;

    ProbeState state(ProbeKind kind) {
      return switch (kind) {
        case WHO_CHANNEL -> whoChannel;
        case WHOIS -> whois;
        case USERHOST -> userhost;
      };
    }
  }

  private static final class ProbeState {
    final Set<String> queue = new LinkedHashSet<>();
    final BoundedNickInstantStore lastNickRequestAt =
        new BoundedNickInstantStore(MAX_TRACKED_TARGET_STATE_PER_KIND);
    // When WHOIS account information is ambiguous (e.g., no 330 ever seen), apply a soft snooze so
    // we
    // do not hammer WHOIS forever for the same nick. Keys are lowercase nick.
    final BoundedNickInstantStore snoozeUntilByNickLower =
        new BoundedNickInstantStore(MAX_TRACKED_TARGET_STATE_PER_KIND);
    final BoundedNickIntStore ambiguousWhoisMissCountByNickLower =
        new BoundedNickIntStore(MAX_TRACKED_TARGET_STATE_PER_KIND);
    final Deque<Instant> cmdTimes = new ArrayDeque<>();
    Instant lastCmdAt;
  }

  private static final class BoundedNickInstantStore {
    private final int maxSize;
    private final Object2LongLinkedOpenHashMap<String> byNickLower;

    BoundedNickInstantStore(int maxSize) {
      this.maxSize = Math.max(1, maxSize);
      this.byNickLower = new Object2LongLinkedOpenHashMap<>(256, 0.75f);
      this.byNickLower.defaultReturnValue(MISSING_EPOCH_MILLIS);
    }

    long getEpochMillis(String nickLower) {
      if (nickLower == null || nickLower.isBlank()) return MISSING_EPOCH_MILLIS;
      return byNickLower.getAndMoveToLast(nickLower);
    }

    void putInstant(String nickLower, Instant at) {
      if (at == null) return;
      putEpochMillis(nickLower, at.toEpochMilli());
    }

    void putEpochMillis(String nickLower, long atEpochMs) {
      if (nickLower == null || nickLower.isBlank()) return;
      byNickLower.putAndMoveToLast(nickLower, atEpochMs);
      trimToMax();
    }

    void remove(String nickLower) {
      if (nickLower == null || nickLower.isBlank()) return;
      byNickLower.removeLong(nickLower);
    }

    boolean containsKey(String nickLower) {
      if (nickLower == null || nickLower.isBlank()) return false;
      return byNickLower.containsKey(nickLower);
    }

    void removeIfBefore(Instant cutoff) {
      if (cutoff == null) return;
      long cutoffEpochMs = cutoff.toEpochMilli();
      var it = byNickLower.object2LongEntrySet().fastIterator();
      while (it.hasNext()) {
        Object2LongMap.Entry<String> entry = it.next();
        if (entry.getLongValue() < cutoffEpochMs) it.remove();
      }
    }

    void removeIfNotAfter(Instant threshold) {
      if (threshold == null) return;
      long thresholdEpochMs = threshold.toEpochMilli();
      var it = byNickLower.object2LongEntrySet().fastIterator();
      while (it.hasNext()) {
        Object2LongMap.Entry<String> entry = it.next();
        if (entry.getLongValue() <= thresholdEpochMs) it.remove();
      }
    }

    private void trimToMax() {
      while (byNickLower.size() > maxSize) {
        byNickLower.removeFirstLong();
      }
    }
  }

  private static final class BoundedNickIntStore {
    private final int maxSize;
    private final Object2IntLinkedOpenHashMap<String> byNickLower;

    BoundedNickIntStore(int maxSize) {
      this.maxSize = Math.max(1, maxSize);
      this.byNickLower = new Object2IntLinkedOpenHashMap<>(256, 0.75f);
      this.byNickLower.defaultReturnValue(MISSING_COUNT);
    }

    int getOrZero(String nickLower) {
      if (nickLower == null || nickLower.isBlank()) return 0;
      int value = byNickLower.getAndMoveToLast(nickLower);
      return value == MISSING_COUNT ? 0 : value;
    }

    void put(String nickLower, int value) {
      if (nickLower == null || nickLower.isBlank()) return;
      byNickLower.putAndMoveToLast(nickLower, value);
      trimToMax();
    }

    void remove(String nickLower) {
      if (nickLower == null || nickLower.isBlank()) return;
      byNickLower.removeInt(nickLower);
    }

    void removeKeysNotPresentIn(BoundedNickInstantStore allowedKeys) {
      if (allowedKeys == null) {
        byNickLower.clear();
        return;
      }
      var it = byNickLower.object2IntEntrySet().fastIterator();
      while (it.hasNext()) {
        Object2IntMap.Entry<String> entry = it.next();
        if (!allowedKeys.containsKey(entry.getKey())) it.remove();
      }
    }

    private void trimToMax() {
      while (byNickLower.size() > maxSize) {
        byNickLower.removeFirstInt();
      }
    }
  }

  /**
   * Round-robin selector for periodic refresh.
   *
   * <p>Stores a deduped snapshot in insertion order.
   */
  private static final class RosterCursor {
    final List<String> snapshot = new ArrayList<>();
    int idx = 0;

    void replaceAll(Collection<String> nicks) {
      snapshot.clear();
      idx = 0;
      if (nicks == null || nicks.isEmpty()) return;
      LinkedHashSet<String> dedup = new LinkedHashSet<>();
      for (String n : nicks) {
        String nick = Objects.toString(n, "").trim();
        if (!nick.isEmpty()) dedup.add(nick);
      }
      snapshot.addAll(dedup);
    }

    List<String> pickNext(int count) {
      if (count <= 0 || snapshot.isEmpty()) return List.of();
      int n = Math.min(count, snapshot.size());
      List<String> out = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        if (idx >= snapshot.size()) idx = 0;
        out.add(snapshot.get(idx++));
      }
      return out;
    }
  }
}
