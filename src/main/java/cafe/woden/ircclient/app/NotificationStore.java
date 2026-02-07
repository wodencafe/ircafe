package cafe.woden.ircclient.app;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory store of per-server "highlight" notifications.
 *
 */
@Component
public class NotificationStore {

  /**
   * A single highlight/mention event.
   *
   * @param serverId server identifier
   * @param channel channel name (e.g. #libera)
   * @param fromNick nick that triggered the highlight
   * @param at timestamp (Instant)
   */
  public record HighlightEvent(String serverId, String channel, String fromNick, Instant at) {}

  /** Notification store update signal (used by the UI to refresh). */
  public record Change(String serverId) {}

  /**
   * Hard cap to prevent unbounded memory growth.
   *
   */
  public static final int DEFAULT_MAX_EVENTS_PER_SERVER = 2000;

  private final int maxEventsPerServer;

  private final ConcurrentHashMap<String, List<HighlightEvent>> eventsByServer = new ConcurrentHashMap<>();

  private final FlowableProcessor<Change> changes =
      PublishProcessor.<Change>create().toSerialized();

  public NotificationStore() {
    this(DEFAULT_MAX_EVENTS_PER_SERVER);
  }

  public NotificationStore(int maxEventsPerServer) {
    this.maxEventsPerServer = Math.max(50, maxEventsPerServer);
  }

  /** Emits a signal whenever notifications change for a server. */
  public Flowable<Change> changes() {
    return changes.onBackpressureBuffer();
  }

  /**
   * Record a new highlight event.
   *
   */
  public void recordHighlight(TargetRef channelTarget, String fromNick) {
    if (channelTarget == null) return;
    if (channelTarget.isUiOnly()) return;
    if (!channelTarget.isChannel()) return;

    String sid = normalizeServerId(channelTarget.serverId());
    if (sid.isEmpty()) return;

    String channel = normalizeChannel(channelTarget.target());
    if (channel.isEmpty()) return;

    String nick = normalizeNick(fromNick);
    Instant now = Instant.now();

    List<HighlightEvent> list = eventsByServer.computeIfAbsent(
        sid, k -> Collections.synchronizedList(new ArrayList<>()));

    synchronized (list) {
      list.add(new HighlightEvent(sid, channel, nick, now));
      // Enforce cap (drop oldest first).
      int overflow = list.size() - maxEventsPerServer;
      if (overflow > 0) {
        list.subList(0, overflow).clear();
      }
    }

    changes.onNext(new Change(sid));
  }

  /** Returns a defensive copy of all highlight events for a server, oldest to newest. */
  public List<HighlightEvent> listAll(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  /** Returns up to {@code max} most recent highlight events for a server (newest last). */
  public List<HighlightEvent> listRecent(String serverId, int max) {
    if (max <= 0) return List.of();
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      int n = list.size();
      int from = Math.max(0, n - max);
      return List.copyOf(list.subList(from, n));
    }
  }

  public int count(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return 0;
    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list == null) return 0;
    synchronized (list) {
      return list.size();
    }
  }

  /** Clears all highlight events for a specific channel on a server. */
  public void clearChannel(TargetRef channelTarget) {
    if (channelTarget == null) return;
    if (channelTarget.isUiOnly()) return;
    if (!channelTarget.isChannel()) return;

    String sid = normalizeServerId(channelTarget.serverId());
    if (sid.isEmpty()) return;
    String channel = normalizeChannel(channelTarget.target());
    if (channel.isEmpty()) return;

    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list == null) return;
    boolean changed = false;
    synchronized (list) {
      changed = list.removeIf(ev -> ev != null && channel.equalsIgnoreCase(ev.channel()));
    }
    if (changed) {
      changes.onNext(new Change(sid));
    }
  }

  /** Clears all highlight events for a server. */
  public void clearServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    List<HighlightEvent> list = eventsByServer.get(sid);
    if (list == null) return;
    synchronized (list) {
      list.clear();
    }
    changes.onNext(new Change(sid));
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeChannel(String channel) {
    return Objects.toString(channel, "").trim();
  }

  private static String normalizeNick(String nick) {
    String s = Objects.toString(nick, "").trim();
    return s.isEmpty() ? "?" : s;
  }
}
