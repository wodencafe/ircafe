package cafe.woden.ircclient.app;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory store of per-server DCC transfer/chat state for UI rendering. */
@Component
public class DccTransferStore {

  public enum ActionHint {
    NONE,
    ACCEPT_CHAT,
    GET_FILE,
    CLOSE_CHAT
  }

  public record Entry(
      String entryId,
      String serverId,
      String nick,
      String kind,
      String status,
      String detail,
      String localPath,
      Integer progressPercent,
      ActionHint actionHint,
      Instant updatedAt) {}

  public record Change(String serverId) {}

  public static final int DEFAULT_MAX_ENTRIES_PER_SERVER = 400;

  private final int maxEntriesPerServer;
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>> entriesByServer =
      new ConcurrentHashMap<>();
  private final FlowableProcessor<Change> changes =
      PublishProcessor.<Change>create().toSerialized();

  public DccTransferStore() {
    this(DEFAULT_MAX_ENTRIES_PER_SERVER);
  }

  public DccTransferStore(int maxEntriesPerServer) {
    this.maxEntriesPerServer = Math.max(50, maxEntriesPerServer);
  }

  public Flowable<Change> changes() {
    return changes.onBackpressureBuffer();
  }

  public List<Entry> listAll(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    ConcurrentHashMap<String, Entry> map = entriesByServer.get(sid);
    if (map == null || map.isEmpty()) return List.of();

    ArrayList<Entry> out = new ArrayList<>(map.values());
    out.sort(
        Comparator.comparing(
                DccTransferStore.Entry::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(e -> Objects.toString(e.kind(), ""))
            .thenComparing(e -> Objects.toString(e.nick(), ""), String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(out);
  }

  public void upsert(
      String serverId,
      String entryId,
      String nick,
      String kind,
      String status,
      String detail,
      Integer progressPercent,
      ActionHint actionHint) {
    upsert(serverId, entryId, nick, kind, status, detail, "", progressPercent, actionHint);
  }

  public void upsert(
      String serverId,
      String entryId,
      String nick,
      String kind,
      String status,
      String detail,
      String localPath,
      Integer progressPercent,
      ActionHint actionHint) {
    String sid = normalizeServerId(serverId);
    String id = normalizeEntryId(entryId);
    if (sid.isEmpty() || id.isEmpty()) return;

    String n = normalizeNick(nick);
    String k = normalizeText(kind);
    String st = normalizeText(status);
    String d = normalizeText(detail);
    String path = normalizePath(localPath);
    Integer pct = normalizeProgress(progressPercent);
    ActionHint hint = (actionHint == null) ? ActionHint.NONE : actionHint;

    Entry next = new Entry(id, sid, n, k, st, d, path, pct, hint, Instant.now());
    ConcurrentHashMap<String, Entry> map =
        entriesByServer.computeIfAbsent(sid, __ -> new ConcurrentHashMap<>());
    map.put(id, next);
    trimIfNeeded(map);
    changes.onNext(new Change(sid));
  }

  public void remove(String serverId, String entryId) {
    String sid = normalizeServerId(serverId);
    String id = normalizeEntryId(entryId);
    if (sid.isEmpty() || id.isEmpty()) return;

    ConcurrentHashMap<String, Entry> map = entriesByServer.get(sid);
    if (map == null) return;
    if (map.remove(id) != null) {
      changes.onNext(new Change(sid));
    }
  }

  public void clearServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ConcurrentHashMap<String, Entry> removed = entriesByServer.remove(sid);
    if (removed != null && !removed.isEmpty()) {
      changes.onNext(new Change(sid));
    }
  }

  private void trimIfNeeded(ConcurrentHashMap<String, Entry> map) {
    if (map == null) return;
    while (map.size() > maxEntriesPerServer) {
      Entry oldest = null;
      for (Entry entry : map.values()) {
        if (entry == null) continue;
        if (oldest == null) {
          oldest = entry;
          continue;
        }
        Instant at = entry.updatedAt();
        Instant oldestAt = oldest.updatedAt();
        if (oldestAt == null || (at != null && at.isBefore(oldestAt))) {
          oldest = entry;
        }
      }
      if (oldest == null || oldest.entryId() == null) return;
      map.remove(oldest.entryId(), oldest);
    }
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeEntryId(String entryId) {
    return Objects.toString(entryId, "").trim();
  }

  private static String normalizeNick(String nick) {
    return Objects.toString(nick, "").trim();
  }

  private static String normalizeText(String text) {
    return Objects.toString(text, "").trim();
  }

  private static String normalizePath(String localPath) {
    return Objects.toString(localPath, "").trim();
  }

  private static Integer normalizeProgress(Integer progressPercent) {
    if (progressPercent == null) return null;
    int p = progressPercent;
    if (p < 0) p = 0;
    if (p > 100) p = 100;
    return p;
  }
}
