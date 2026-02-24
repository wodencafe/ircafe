package cafe.woden.ircclient.app.monitor;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Re-applies persisted IRC MONITOR nick lists after connect-ready numerics. */
@Component
public class MonitorSyncService {
  private static final Logger log = LoggerFactory.getLogger(MonitorSyncService.class);

  private static final int DEFAULT_MONITOR_CHUNK = 100;

  private final IrcClientService irc;
  private final MonitorListService monitorListService;
  private final CompositeDisposable sendDisposables = new CompositeDisposable();
  private final ConcurrentHashMap<String, Boolean> readyByServer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> syncedByServer = new ConcurrentHashMap<>();
  private final Disposable eventsSub;

  public MonitorSyncService(IrcClientService irc, MonitorListService monitorListService) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.monitorListService = Objects.requireNonNull(monitorListService, "monitorListService");
    this.eventsSub = irc.events().subscribe(this::onEvent, this::onEventError);
  }

  @jakarta.annotation.PreDestroy
  void shutdown() {
    try {
      if (eventsSub != null && !eventsSub.isDisposed()) eventsSub.dispose();
    } catch (Exception ignored) {
    }
    sendDisposables.dispose();
    readyByServer.clear();
    syncedByServer.clear();
  }

  private void onEvent(ServerIrcEvent sev) {
    if (sev == null) return;
    String sid = normalizeServerId(sev.serverId());
    if (sid.isEmpty()) return;

    IrcEvent event = sev.event();
    if (event instanceof IrcEvent.Disconnected) {
      readyByServer.remove(sid);
      syncedByServer.remove(sid);
      return;
    }

    if (event instanceof IrcEvent.Connected) {
      readyByServer.put(sid, Boolean.FALSE);
      syncedByServer.put(sid, Boolean.FALSE);
      return;
    }

    if (!(event instanceof IrcEvent.ServerResponseLine sr)) return;
    int code = sr.code();
    if (code == 376 || code == 422) {
      readyByServer.put(sid, Boolean.TRUE);
      maybeSyncNow(sid);
      return;
    }

    // 005 can arrive in multiple lines; once MONITOR is observed after MOTD, sync immediately.
    if (code == 5) {
      maybeSyncNow(sid);
    }
  }

  private void maybeSyncNow(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    if (!Boolean.TRUE.equals(readyByServer.get(sid))) return;
    if (Boolean.TRUE.equals(syncedByServer.get(sid))) return;
    if (!irc.isMonitorAvailable(sid)) return;

    List<String> nicks = monitorListService.listNicks(sid);
    int monitorLimit = irc.negotiatedMonitorLimit(sid);
    int chunkSize = monitorLimit > 0 ? monitorLimit : DEFAULT_MONITOR_CHUNK;
    if (chunkSize <= 0) chunkSize = DEFAULT_MONITOR_CHUNK;
    final int chunkSizeFinal = chunkSize;

    ArrayList<String> lines = new ArrayList<>();
    lines.add("MONITOR C");
    for (int i = 0; i < nicks.size(); i += chunkSizeFinal) {
      int end = Math.min(i + chunkSizeFinal, nicks.size());
      List<String> chunk = nicks.subList(i, end);
      if (!chunk.isEmpty()) {
        lines.add("MONITOR +" + String.join(",", chunk));
      }
    }

    Completable sequence = Completable.complete();
    for (String line : lines) {
      sequence = sequence.andThen(irc.sendRaw(sid, line));
    }

    syncedByServer.put(sid, Boolean.TRUE);
    sendDisposables.add(
        sequence.subscribe(
            () ->
                log.info(
                    "[{}] monitor sync applied ({} nick{}; chunk-size={})",
                    sid,
                    nicks.size(),
                    nicks.size() == 1 ? "" : "s",
                    chunkSizeFinal),
            err -> {
              syncedByServer.put(sid, Boolean.FALSE);
              log.warn("[{}] monitor sync failed", sid, err);
            }));
  }

  private void onEventError(Throwable err) {
    log.warn("MonitorSyncService event stream failed", err);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
