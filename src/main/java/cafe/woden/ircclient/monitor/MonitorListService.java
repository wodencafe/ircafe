package cafe.woden.ircclient.monitor;

import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Server-scoped monitor nick list persistence/cache. */
@Component
@ApplicationLayer
public class MonitorListService implements MonitorRosterPort {

  private final RuntimeConfigStore runtimeConfig;
  private final ConcurrentHashMap<String, List<String>> nicksByServer = new ConcurrentHashMap<>();
  private final FlowableProcessor<Change> changes =
      PublishProcessor.<Change>create().toSerialized();

  public enum ChangeKind {
    ADDED,
    REMOVED,
    REPLACED,
    CLEARED
  }

  public record Change(String serverId, ChangeKind kind) {}

  public MonitorListService(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
  }

  public Flowable<Change> changes() {
    return changes.onBackpressureBuffer();
  }

  @Override
  public List<String> listNicks(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<String> list = nicksByServer.computeIfAbsent(sid, this::loadServerList);
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  @Override
  public int addNicks(String serverId, List<String> rawNicks) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return 0;
    List<String> normalized = normalizeNicks(rawNicks);
    if (normalized.isEmpty()) return 0;

    List<String> list = nicksByServer.computeIfAbsent(sid, this::loadServerList);
    int added = 0;
    synchronized (list) {
      for (String nick : normalized) {
        if (containsIgnoreCase(list, nick)) continue;
        list.add(nick);
        added++;
      }
      if (added <= 0) return 0;
    }
    runtimeConfig.replaceMonitorNicks(sid, listNicks(sid));
    changes.onNext(new Change(sid, ChangeKind.ADDED));
    return added;
  }

  @Override
  public int removeNicks(String serverId, List<String> rawNicks) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return 0;
    List<String> normalized = normalizeNicks(rawNicks);
    if (normalized.isEmpty()) return 0;

    List<String> list = nicksByServer.computeIfAbsent(sid, this::loadServerList);
    int removed = 0;
    synchronized (list) {
      for (String nick : normalized) {
        boolean changed =
            list.removeIf(existing -> existing != null && existing.equalsIgnoreCase(nick));
        if (changed) removed++;
      }
      if (removed <= 0) return 0;
    }
    runtimeConfig.replaceMonitorNicks(sid, listNicks(sid));
    changes.onNext(new Change(sid, ChangeKind.REMOVED));
    return removed;
  }

  public int replaceNicks(String serverId, List<String> rawNicks) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return 0;
    List<String> normalized = normalizeNicks(rawNicks);

    List<String> list = nicksByServer.computeIfAbsent(sid, this::loadServerList);
    synchronized (list) {
      if (list.equals(normalized)) return normalized.size();
      list.clear();
      list.addAll(normalized);
    }
    runtimeConfig.replaceMonitorNicks(sid, normalized);
    changes.onNext(
        new Change(sid, normalized.isEmpty() ? ChangeKind.CLEARED : ChangeKind.REPLACED));
    return normalized.size();
  }

  @Override
  public int clearNicks(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return 0;
    List<String> list = nicksByServer.computeIfAbsent(sid, this::loadServerList);
    int removed;
    synchronized (list) {
      removed = list.size();
      if (removed <= 0) return 0;
      list.clear();
    }
    runtimeConfig.replaceMonitorNicks(sid, List.of());
    changes.onNext(new Change(sid, ChangeKind.CLEARED));
    return removed;
  }

  /** Tokenizes nicks from user input where values may be comma and/or space separated. */
  public static List<String> tokenizeNickInput(String rawInput) {
    String raw = Objects.toString(rawInput, "").trim();
    if (raw.isEmpty()) return List.of();

    String normalizedSeparators = raw.replace(',', ' ');
    String[] tokens = normalizedSeparators.split("\\s+");
    if (tokens.length == 0) return List.of();

    ArrayList<String> out = new ArrayList<>(tokens.length);
    for (String token : tokens) {
      String nick = normalizeNick(token);
      if (nick.isEmpty()) continue;
      out.add(nick);
    }
    if (out.isEmpty()) return List.of();
    return out;
  }

  @Override
  public List<String> parseNickInput(String rawInput) {
    return MonitorListService.tokenizeNickInput(rawInput);
  }

  private List<String> loadServerList(String serverId) {
    List<String> stored = runtimeConfig.readMonitorNicks(serverId);
    return Collections.synchronizedList(new ArrayList<>(normalizeNicks(stored)));
  }

  private static List<String> normalizeNicks(List<String> rawNicks) {
    if (rawNicks == null || rawNicks.isEmpty()) return List.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (String raw : rawNicks) {
      String nick = normalizeNick(raw);
      if (nick.isEmpty()) continue;
      out.putIfAbsent(nick.toLowerCase(Locale.ROOT), nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out.values());
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeNick(String rawNick) {
    String nick = Objects.toString(rawNick, "").trim();
    if (nick.isEmpty()) return "";
    if (nick.startsWith(":")) nick = nick.substring(1).trim();
    int comma = nick.indexOf(',');
    if (comma >= 0) nick = nick.substring(0, comma).trim();
    int bang = nick.indexOf('!');
    if (bang > 0) nick = nick.substring(0, bang).trim();
    if (nick.isEmpty()) return "";
    if (nick.indexOf(' ') >= 0 || nick.indexOf('\t') >= 0) return "";
    if (nick.startsWith("#") || nick.startsWith("&")) return "";
    return nick;
  }

  private static boolean containsIgnoreCase(List<String> list, String value) {
    if (list == null || list.isEmpty()) return false;
    String needle = Objects.toString(value, "").trim();
    if (needle.isEmpty()) return false;
    for (String item : list) {
      if (item != null && item.equalsIgnoreCase(needle)) return true;
    }
    return false;
  }
}
