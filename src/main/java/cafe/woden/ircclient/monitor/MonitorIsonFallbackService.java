package cafe.woden.ircclient.monitor;

import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.PircbotxIsonParsers;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** ISON-based monitor fallback when IRC MONITOR is unavailable on a connected server. */
@Component
@ApplicationLayer
public class MonitorIsonFallbackService implements MonitorFallbackPort {
  private static final Logger log = LoggerFactory.getLogger(MonitorIsonFallbackService.class);

  static final int DEFAULT_POLL_INTERVAL_SECONDS = 30;
  static final int MIN_POLL_INTERVAL_SECONDS = 5;
  static final int MAX_POLL_INTERVAL_SECONDS = 600;

  private static final int MAX_NICKS_PER_ISON = 25;
  private static final long POLL_TIMEOUT_MS = 10_000L;

  private final IrcClientService irc;
  private final MonitorListService monitorListService;
  private final UiPort ui;
  private final UiSettingsPort uiSettingsPort;
  private final ScheduledExecutorService pollScheduler;
  private final Object scheduleLock = new Object();

  private final CompositeDisposable disposables = new CompositeDisposable();
  private final ConcurrentHashMap<String, Boolean> connectedByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> readyByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> nextPollAtMsByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledPollByServer =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, PollTimeoutHandle> timeoutByServer =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, PollCycle> pollByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Map<String, Boolean>> knownOnlineByServer =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> fallbackNoticeShownByServer =
      new ConcurrentHashMap<>();

  public MonitorIsonFallbackService(
      IrcClientService irc,
      MonitorListService monitorListService,
      UiPort ui,
      UiSettingsPort uiSettingsPort,
      @Qualifier(ExecutorConfig.MONITOR_ISON_FALLBACK_SCHEDULER)
          ScheduledExecutorService pollScheduler) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.monitorListService = Objects.requireNonNull(monitorListService, "monitorListService");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.uiSettingsPort = Objects.requireNonNull(uiSettingsPort, "uiSettingsPort");
    this.pollScheduler = Objects.requireNonNull(pollScheduler, "pollScheduler");

    disposables.add(irc.events().subscribe(this::onEvent, this::onEventError));
    disposables.add(
        monitorListService.changes().subscribe(this::onListChanged, this::onListChangeError));
  }

  @jakarta.annotation.PreDestroy
  void shutdown() {
    disposables.dispose();
    cancelAllSchedules();
    connectedByServer.clear();
    readyByServer.clear();
    nextPollAtMsByServer.clear();
    pollByServer.clear();
    knownOnlineByServer.clear();
    fallbackNoticeShownByServer.clear();
  }

  /** Whether fallback mode is currently active for this server connection. */
  @Override
  public boolean isFallbackActive(String serverId) {
    return isFallbackEligible(normalizeServerId(serverId));
  }

  /**
   * Whether status-pane rendering should suppress ISON numerics for this server.
   *
   * <p>When fallback is active, periodic ISON replies would otherwise flood Status.
   */
  @Override
  public boolean shouldSuppressIsonServerResponse(String serverId) {
    return isFallbackEligible(normalizeServerId(serverId));
  }

  /** Request an immediate ISON poll cycle (best effort). */
  @Override
  public void requestImmediateRefresh(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    schedulePollAt(sid, System.currentTimeMillis());
    maybeStartDuePoll(sid);
  }

  private void onEvent(ServerIrcEvent sev) {
    if (sev == null) return;
    String sid = normalizeServerId(sev.serverId());
    if (sid.isEmpty()) return;

    IrcEvent ev = sev.event();
    if (ev instanceof IrcEvent.Connected) {
      connectedByServer.put(sid, Boolean.TRUE);
      readyByServer.put(sid, Boolean.FALSE);
      fallbackNoticeShownByServer.remove(sid);
      return;
    }
    if (ev instanceof IrcEvent.Disconnected) {
      clearServerState(sid);
      return;
    }

    if (!(ev instanceof IrcEvent.ServerResponseLine sr)) return;
    int code = sr.code();
    if (code == 376 || code == 422) {
      readyByServer.put(sid, Boolean.TRUE);
      if (isFallbackEligible(sid)) {
        maybeShowFallbackNotice(sid);
        requestImmediateRefresh(sid);
      } else {
        onFallbackNotEligible(sid);
      }
      return;
    }

    // 005 can arrive in multiple lines; MONITOR capability availability can flip mid-connection.
    if (code == 5) {
      if (isFallbackEligible(sid)) {
        maybeShowFallbackNotice(sid);
        requestImmediateRefresh(sid);
      } else {
        onFallbackNotEligible(sid);
      }
      return;
    }

    if (code == 303) {
      handleIsonReply(sid, sr);
    }
  }

  private void onListChanged(MonitorListService.Change change) {
    if (change == null) return;
    String sid = normalizeServerId(change.serverId());
    if (sid.isEmpty()) return;
    if (!isFallbackEligible(sid)) return;
    requestImmediateRefresh(sid);
  }

  private void maybeStartDuePoll(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    if (!isFallbackEligible(sid)) {
      onFallbackNotEligible(sid);
      return;
    }

    maybeShowFallbackNotice(sid);

    if (pollByServer.containsKey(sid)) {
      return;
    }

    long now = System.currentTimeMillis();
    long dueAt = nextPollAtMsByServer.getOrDefault(sid, 0L);
    if (now < dueAt) {
      schedulePollAt(sid, dueAt);
      return;
    }

    tryStartPoll(sid);
  }

  private void tryStartPoll(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    if (!isFallbackEligible(sid)) return;
    if (pollByServer.containsKey(sid)) return;

    LinkedHashMap<String, String> expectedByLower = expectedNickMap(sid);
    if (expectedByLower.isEmpty()) {
      knownOnlineByServer.remove(sid);
      scheduleNextPoll(sid);
      return;
    }

    List<List<String>> chunks =
        chunkNicks(new ArrayList<>(expectedByLower.values()), MAX_NICKS_PER_ISON);
    if (chunks.isEmpty()) {
      knownOnlineByServer.remove(sid);
      scheduleNextPoll(sid);
      return;
    }

    PollCycle cycle = new PollCycle(expectedByLower, chunks.size());
    PollCycle prev = pollByServer.putIfAbsent(sid, cycle);
    if (prev != null) return;

    scheduleNextPoll(sid);
    schedulePollTimeout(sid, cycle);

    for (List<String> chunk : chunks) {
      if (chunk == null || chunk.isEmpty()) {
        onChunkCompleted(sid, cycle, Instant.now());
        continue;
      }
      String line = "ISON " + String.join(" ", chunk);
      disposables.add(
          irc.sendRaw(sid, line)
              .subscribe(
                  () -> {},
                  err -> {
                    log.debug("[{}] ISON fallback send failed: {}", sid, String.valueOf(err));
                    onChunkCompleted(sid, cycle, Instant.now());
                  }));
    }
  }

  private void handleIsonReply(String serverId, IrcEvent.ServerResponseLine sr) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    if (!isFallbackEligible(sid)) return;

    PollCycle cycle = pollByServer.get(sid);
    if (cycle == null) return;

    List<String> online = PircbotxIsonParsers.parseRpl303IsonOnlineNicks(sr.rawLine());
    if (online == null) return;
    synchronized (cycle.lock) {
      for (String nick : online) {
        String lower = normalizeNickLower(nick);
        if (lower.isEmpty()) continue;
        if (cycle.expectedByLower.containsKey(lower)) {
          cycle.onlineLower.add(lower);
        }
      }
    }
    onChunkCompleted(sid, cycle, sr.at() == null ? Instant.now() : sr.at());
  }

  private void onChunkCompleted(String serverId, PollCycle cycle, Instant at) {
    if (cycle == null) return;
    boolean done;
    synchronized (cycle.lock) {
      cycle.receivedResponses++;
      done = cycle.receivedResponses >= cycle.expectedResponses;
    }
    if (done) {
      finalizePoll(serverId, cycle, at == null ? Instant.now() : at);
    }
  }

  private void finalizePoll(String serverId, PollCycle cycle, Instant at) {
    if (cycle == null) return;
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    if (!pollByServer.remove(sid, cycle)) return;
    cancelPollTimeout(sid, cycle);

    TargetRef monitorTarget = TargetRef.monitorGroup(sid);
    List<String> wentOnline = new ArrayList<>();
    List<String> wentOffline = new ArrayList<>();
    Map<String, Boolean> known =
        knownOnlineByServer.computeIfAbsent(sid, __ -> new LinkedHashMap<>());

    synchronized (cycle.lock) {
      known.keySet().removeIf(k -> !cycle.expectedByLower.containsKey(k));
      for (Map.Entry<String, String> e : cycle.expectedByLower.entrySet()) {
        String lower = e.getKey();
        String displayNick = e.getValue();
        boolean nowOnline = cycle.onlineLower.contains(lower);
        Boolean prevOnline = known.put(lower, nowOnline);
        if (prevOnline == null || prevOnline.booleanValue() != nowOnline) {
          if (nowOnline) {
            wentOnline.add(displayNick);
          } else {
            wentOffline.add(displayNick);
          }
        }
      }
    }

    if (!wentOnline.isEmpty() || !wentOffline.isEmpty()) {
      ui.ensureTargetExists(monitorTarget);
    }

    if (!wentOnline.isEmpty()) {
      for (String nick : wentOnline) {
        ui.setPrivateMessageOnlineState(sid, nick, true);
      }
      ui.appendStatusAt(
          monitorTarget,
          at == null ? Instant.now() : at,
          "(monitor)",
          "Online: " + String.join(", ", wentOnline));
    }

    if (!wentOffline.isEmpty()) {
      for (String nick : wentOffline) {
        ui.setPrivateMessageOnlineState(sid, nick, false);
      }
      ui.appendStatusAt(
          monitorTarget,
          at == null ? Instant.now() : at,
          "(monitor)",
          "Offline: " + String.join(", ", wentOffline));
    }

    maybeStartDuePoll(sid);
  }

  private void maybeShowFallbackNotice(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    if (fallbackNoticeShownByServer.putIfAbsent(sid, Boolean.TRUE) != null) return;

    TargetRef monitorTarget = TargetRef.monitorGroup(sid);
    ui.ensureTargetExists(monitorTarget);
    ui.appendStatus(
        monitorTarget,
        "(monitor)",
        "MONITOR unavailable; using ISON fallback polling (" + pollIntervalSeconds() + "s).");
  }

  private void scheduleNextPoll(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    long next = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(pollIntervalSeconds());
    schedulePollAt(sid, next);
  }

  private void schedulePollAt(String serverId, long targetEpochMs) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    long now = System.currentTimeMillis();
    long dueAt = Math.max(now, targetEpochMs);
    long delayMs = Math.max(0L, dueAt - now);

    synchronized (scheduleLock) {
      if (pollScheduler.isShutdown()) return;

      ScheduledFuture<?> existing = scheduledPollByServer.get(sid);
      long existingDueAt = nextPollAtMsByServer.getOrDefault(sid, Long.MAX_VALUE);
      if (existing != null && !existing.isDone() && dueAt >= existingDueAt) {
        return;
      }
      if (existing != null) {
        existing.cancel(false);
      }

      nextPollAtMsByServer.put(sid, dueAt);
      try {
        ScheduledFuture<?> scheduled =
            pollScheduler.schedule(() -> runScheduledPoll(sid), delayMs, TimeUnit.MILLISECONDS);
        scheduledPollByServer.put(sid, scheduled);
      } catch (RejectedExecutionException ex) {
        log.debug("[{}] monitor fallback poll schedule rejected", sid, ex);
      }
    }
  }

  private void runScheduledPoll(String serverId) {
    maybeStartDuePoll(serverId);
  }

  private void schedulePollTimeout(String serverId, PollCycle cycle) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || cycle == null) return;

    synchronized (scheduleLock) {
      if (pollScheduler.isShutdown()) return;

      PollTimeoutHandle existing = timeoutByServer.remove(sid);
      if (existing != null) {
        existing.future.cancel(false);
      }

      try {
        ScheduledFuture<?> timeout =
            pollScheduler.schedule(
                () -> onPollTimeout(sid, cycle), POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        timeoutByServer.put(sid, new PollTimeoutHandle(cycle, timeout));
      } catch (RejectedExecutionException ex) {
        log.debug("[{}] monitor fallback timeout schedule rejected", sid, ex);
      }
    }
  }

  private void onPollTimeout(String serverId, PollCycle cycle) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || cycle == null) return;
    cancelPollTimeout(sid, cycle);

    PollCycle active = pollByServer.get(sid);
    if (active != cycle) return;
    finalizePoll(sid, cycle, Instant.now());
  }

  private void cancelPollTimeout(String serverId, PollCycle cycle) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    synchronized (scheduleLock) {
      PollTimeoutHandle handle = timeoutByServer.get(sid);
      if (handle == null) return;
      if (cycle != null && handle.cycle != cycle) return;
      if (timeoutByServer.remove(sid, handle)) {
        handle.future.cancel(false);
      }
    }
  }

  private void onFallbackNotEligible(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    pollByServer.remove(sid);
    cancelPollTimeout(sid, null);
    cancelScheduledPoll(sid, false);
    if (Boolean.TRUE.equals(connectedByServer.get(sid))
        && Boolean.TRUE.equals(readyByServer.get(sid))) {
      fallbackNoticeShownByServer.remove(sid);
    }
  }

  private int pollIntervalSeconds() {
    int seconds = DEFAULT_POLL_INTERVAL_SECONDS;
    try {
      seconds = uiSettingsPort.get().monitorIsonFallbackPollIntervalSeconds();
    } catch (Exception ignored) {
    }
    if (seconds < MIN_POLL_INTERVAL_SECONDS) return MIN_POLL_INTERVAL_SECONDS;
    if (seconds > MAX_POLL_INTERVAL_SECONDS) return MAX_POLL_INTERVAL_SECONDS;
    return seconds;
  }

  private boolean isFallbackEligible(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    if (!Boolean.TRUE.equals(connectedByServer.get(sid))) return false;
    if (!Boolean.TRUE.equals(readyByServer.get(sid))) return false;
    return !irc.isMonitorAvailable(sid);
  }

  private void clearServerState(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    connectedByServer.remove(sid);
    readyByServer.remove(sid);
    pollByServer.remove(sid);
    cancelPollTimeout(sid, null);
    cancelScheduledPoll(sid, true);
    knownOnlineByServer.remove(sid);
    fallbackNoticeShownByServer.remove(sid);
  }

  private void cancelScheduledPoll(String serverId, boolean clearDueAt) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    synchronized (scheduleLock) {
      ScheduledFuture<?> scheduled = scheduledPollByServer.remove(sid);
      if (scheduled != null) {
        scheduled.cancel(false);
      }
      if (clearDueAt) {
        nextPollAtMsByServer.remove(sid);
      }
    }
  }

  private void cancelAllSchedules() {
    synchronized (scheduleLock) {
      for (ScheduledFuture<?> scheduled : scheduledPollByServer.values()) {
        if (scheduled != null) {
          scheduled.cancel(false);
        }
      }
      scheduledPollByServer.clear();
      for (PollTimeoutHandle handle : timeoutByServer.values()) {
        if (handle != null) {
          handle.future.cancel(false);
        }
      }
      timeoutByServer.clear();
    }
  }

  private LinkedHashMap<String, String> expectedNickMap(String serverId) {
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    List<String> nicks = monitorListService.listNicks(serverId);
    for (String nick : nicks) {
      String normalized = Objects.toString(nick, "").trim();
      if (normalized.isEmpty()) continue;
      String lower = normalized.toLowerCase(Locale.ROOT);
      out.putIfAbsent(lower, normalized);
    }
    return out;
  }

  private static List<List<String>> chunkNicks(List<String> nicks, int chunkSize) {
    if (nicks == null || nicks.isEmpty()) return List.of();
    int size = Math.max(1, chunkSize);
    ArrayList<List<String>> out = new ArrayList<>((nicks.size() + size - 1) / size);
    for (int i = 0; i < nicks.size(); i += size) {
      int end = Math.min(i + size, nicks.size());
      List<String> chunk = nicks.subList(i, end);
      if (!chunk.isEmpty()) out.add(List.copyOf(chunk));
    }
    return out;
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeNickLower(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return "";
    return n.toLowerCase(Locale.ROOT);
  }

  private void onEventError(Throwable err) {
    log.warn("MonitorIsonFallbackService event stream failed", err);
  }

  private void onListChangeError(Throwable err) {
    log.warn("MonitorIsonFallbackService monitor list stream failed", err);
  }

  private static final class PollCycle {
    final Object lock = new Object();
    final LinkedHashMap<String, String> expectedByLower;
    final LinkedHashSet<String> onlineLower = new LinkedHashSet<>();
    final int expectedResponses;
    int receivedResponses;

    PollCycle(Map<String, String> expectedByLower, int expectedResponses) {
      this.expectedByLower = new LinkedHashMap<>(expectedByLower);
      this.expectedResponses = Math.max(1, expectedResponses);
      this.receivedResponses = 0;
    }
  }

  private static final class PollTimeoutHandle {
    final PollCycle cycle;
    final ScheduledFuture<?> future;

    PollTimeoutHandle(PollCycle cycle, ScheduledFuture<?> future) {
      this.cycle = cycle;
      this.future = future;
    }
  }
}
