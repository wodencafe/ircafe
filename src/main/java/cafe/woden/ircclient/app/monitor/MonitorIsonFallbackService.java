package cafe.woden.ircclient.app.monitor;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.PircbotxIsonParsers;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.core.Flowable;
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
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** ISON-based monitor fallback when IRC MONITOR is unavailable on a connected server. */
@Component
public class MonitorIsonFallbackService {
  private static final Logger log = LoggerFactory.getLogger(MonitorIsonFallbackService.class);

  static final int DEFAULT_POLL_INTERVAL_SECONDS = 30;
  static final int MIN_POLL_INTERVAL_SECONDS = 5;
  static final int MAX_POLL_INTERVAL_SECONDS = 600;

  private static final int MAX_NICKS_PER_ISON = 25;
  private static final long POLL_TIMEOUT_MS = 10_000L;

  private final IrcClientService irc;
  private final MonitorListService monitorListService;
  private final UiPort ui;
  private final UiSettingsBus uiSettingsBus;

  private final CompositeDisposable disposables = new CompositeDisposable();
  private final ConcurrentHashMap<String, Boolean> connectedByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> readyByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> nextPollAtMsByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, PollCycle> pollByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Map<String, Boolean>> knownOnlineByServer =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> fallbackNoticeShownByServer =
      new ConcurrentHashMap<>();

  public MonitorIsonFallbackService(
      IrcClientService irc,
      MonitorListService monitorListService,
      UiPort ui,
      UiSettingsBus uiSettingsBus) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.monitorListService = Objects.requireNonNull(monitorListService, "monitorListService");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.uiSettingsBus = Objects.requireNonNull(uiSettingsBus, "uiSettingsBus");

    disposables.add(irc.events().subscribe(this::onEvent, this::onEventError));
    disposables.add(
        monitorListService.changes().subscribe(this::onListChanged, this::onListChangeError));
    disposables.add(
        Flowable.interval(1, TimeUnit.SECONDS).subscribe(this::onTick, this::onTickError));
  }

  @jakarta.annotation.PreDestroy
  void shutdown() {
    disposables.dispose();
    connectedByServer.clear();
    readyByServer.clear();
    nextPollAtMsByServer.clear();
    pollByServer.clear();
    knownOnlineByServer.clear();
    fallbackNoticeShownByServer.clear();
  }

  /** Whether fallback mode is currently active for this server connection. */
  public boolean isFallbackActive(String serverId) {
    return isFallbackEligible(normalizeServerId(serverId));
  }

  /**
   * Whether status-pane rendering should suppress ISON numerics for this server.
   *
   * <p>When fallback is active, periodic ISON replies would otherwise flood Status.
   */
  public boolean shouldSuppressIsonServerResponse(String serverId) {
    return isFallbackEligible(normalizeServerId(serverId));
  }

  /** Request an immediate ISON poll cycle (best effort). */
  public void requestImmediateRefresh(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    nextPollAtMsByServer.put(sid, 0L);
    tryStartPoll(sid, true);
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
      }
      return;
    }

    // 005 can arrive in multiple lines; MONITOR capability availability can flip mid-connection.
    if (code == 5 && isFallbackEligible(sid)) {
      maybeShowFallbackNotice(sid);
      requestImmediateRefresh(sid);
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

  private void onTick(Long tick) {
    long now = System.currentTimeMillis();
    for (String sid : connectedByServer.keySet()) {
      if (sid == null || sid.isBlank()) continue;
      if (!isFallbackEligible(sid)) {
        // MONITOR is available (or server not ready): stop any in-flight fallback cycle.
        pollByServer.remove(sid);
        if (Boolean.TRUE.equals(connectedByServer.get(sid))
            && Boolean.TRUE.equals(readyByServer.get(sid))) {
          fallbackNoticeShownByServer.remove(sid);
        }
        continue;
      }

      maybeShowFallbackNotice(sid);

      PollCycle cycle = pollByServer.get(sid);
      if (cycle != null) {
        if (now - cycle.startedAtMs >= POLL_TIMEOUT_MS) {
          finalizePoll(sid, cycle, Instant.now());
        }
        continue;
      }

      long dueAt = nextPollAtMsByServer.getOrDefault(sid, 0L);
      if (now >= dueAt) {
        tryStartPoll(sid, false);
      }
    }
  }

  private void tryStartPoll(String serverId, boolean immediate) {
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

    PollCycle cycle = new PollCycle(expectedByLower, chunks.size(), System.currentTimeMillis());
    PollCycle prev = pollByServer.putIfAbsent(sid, cycle);
    if (prev != null) return;
    scheduleNextPoll(sid);

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

    if (immediate) {
      // Keep deterministic cadence from "now + interval" even when manually refreshed.
      scheduleNextPoll(sid);
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
    nextPollAtMsByServer.put(sid, next);
  }

  private int pollIntervalSeconds() {
    int seconds = DEFAULT_POLL_INTERVAL_SECONDS;
    try {
      if (uiSettingsBus.get() != null) {
        seconds = uiSettingsBus.get().monitorIsonFallbackPollIntervalSeconds();
      }
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
    nextPollAtMsByServer.remove(sid);
    pollByServer.remove(sid);
    knownOnlineByServer.remove(sid);
    fallbackNoticeShownByServer.remove(sid);
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

  private void onTickError(Throwable err) {
    log.warn("MonitorIsonFallbackService poll ticker failed", err);
  }

  private static final class PollCycle {
    final Object lock = new Object();
    final LinkedHashMap<String, String> expectedByLower;
    final LinkedHashSet<String> onlineLower = new LinkedHashSet<>();
    final int expectedResponses;
    int receivedResponses;
    final long startedAtMs;

    PollCycle(Map<String, String> expectedByLower, int expectedResponses, long startedAtMs) {
      this.expectedByLower = new LinkedHashMap<>(expectedByLower);
      this.expectedResponses = Math.max(1, expectedResponses);
      this.startedAtMs = startedAtMs;
      this.receivedResponses = 0;
    }
  }
}
