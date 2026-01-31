package cafe.woden.ircclient.ignore;

import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks ignore masks per server.
 *
 * <p>This service ONLY tracks and persists ignore masks. It does not apply ignoring
 * to incoming/outgoing messages (that comes later).
 */
@Component
public class IgnoreListService {

  private final RuntimeConfigStore runtimeConfig;

  /** In-memory copy of ignore masks (serverId -> ordered list of masks). */
  private final ConcurrentHashMap<String, List<String>> masksByServer = new ConcurrentHashMap<>();

  /** In-memory copy of soft-ignore masks (serverId -> ordered list of masks). */
  private final ConcurrentHashMap<String, List<String>> softMasksByServer = new ConcurrentHashMap<>();

  /** Emits when an ignore list changes (for UI repainting, etc.). */
  public enum ListKind { IGNORE, SOFT_IGNORE }

  public record Change(String serverId, ListKind kind) {}

  private final FlowableProcessor<Change> changes =
      PublishProcessor.<Change>create().toSerialized();

  public Flowable<Change> changes() {
    return changes.onBackpressureBuffer();
  }


  public IgnoreListService(IgnoreProperties props, RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;

    // Seed from configuration (including runtime YAML import).
    if (props != null && props.servers() != null) {
      for (Map.Entry<String, IgnoreProperties.ServerIgnore> e : props.servers().entrySet()) {
        String sid = normalizeServerId(e.getKey());
        if (sid.isEmpty()) continue;
        IgnoreProperties.ServerIgnore si = e.getValue();
        if (si == null) continue;

        // Hard ignores
        List<String> hard = cleanList(si.masks());
        if (!hard.isEmpty()) {
          masksByServer.put(sid, Collections.synchronizedList(hard));
        }

        // Soft ignores (reserved for a future feature; tracked + persisted but not applied yet)
        List<String> soft = cleanList(si.softMasks());
        if (!soft.isEmpty()) {
          softMasksByServer.put(sid, Collections.synchronizedList(soft));
        }
      }
    }
  }

  private static List<String> cleanList(List<String> masks) {
    if (masks == null || masks.isEmpty()) return List.of();
    List<String> cleaned = new ArrayList<>();
    for (String m : masks) {
      String mm = normalizeMask(m);
      if (!mm.isEmpty() && cleaned.stream().noneMatch(x -> x.equalsIgnoreCase(mm))) {
        cleaned.add(mm);
      }
    }
    return cleaned;
  }

  /** List current ignore masks for a server (stable order). */
  public List<String> listMasks(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<String> list = masksByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  /** List current soft-ignore masks for a server (stable order). */
  public List<String> listSoftMasks(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<String> list = softMasksByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  /** Add an ignore mask (case-insensitive uniqueness). Returns true if newly added. */
  public boolean addMask(String serverId, String rawMaskOrNick) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    String mask = normalizeMaskOrNickToHostmask(rawMaskOrNick);
    if (mask.isEmpty()) return false;

    List<String> list = masksByServer.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (list) {
      if (list.stream().anyMatch(m -> m.equalsIgnoreCase(mask))) {
        return false;
      }
      list.add(mask);
    }

    runtimeConfig.rememberIgnoreMask(sid, mask);
    changes.onNext(new Change(sid, ListKind.IGNORE));
    return true;
  }

  /** Add a soft-ignore mask (case-insensitive uniqueness). Returns true if newly added. */
  public boolean addSoftMask(String serverId, String rawMaskOrNick) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    String mask = normalizeMaskOrNickToHostmask(rawMaskOrNick);
    if (mask.isEmpty()) return false;

    List<String> list = softMasksByServer.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (list) {
      if (list.stream().anyMatch(m -> m.equalsIgnoreCase(mask))) {
        return false;
      }
      list.add(mask);
    }

    runtimeConfig.rememberSoftIgnoreMask(sid, mask);
    changes.onNext(new Change(sid, ListKind.SOFT_IGNORE));
    return true;
  }

  /** Remove an ignore mask (case-insensitive). Returns true if removed. */
  public boolean removeMask(String serverId, String rawMaskOrNick) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    String mask = normalizeMaskOrNickToHostmask(rawMaskOrNick);
    if (mask.isEmpty()) return false;

    List<String> list = masksByServer.get(sid);
    if (list == null) return false;

    boolean removed;
    synchronized (list) {
      removed = list.removeIf(m -> m != null && m.equalsIgnoreCase(mask));
    }

    if (removed) {
      runtimeConfig.forgetIgnoreMask(sid, mask);
      changes.onNext(new Change(sid, ListKind.IGNORE));
    }

    return removed;
  }

  /** Remove a soft-ignore mask (case-insensitive). Returns true if removed. */
  public boolean removeSoftMask(String serverId, String rawMaskOrNick) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    String mask = normalizeMaskOrNickToHostmask(rawMaskOrNick);
    if (mask.isEmpty()) return false;

    List<String> list = softMasksByServer.get(sid);
    if (list == null) return false;

    boolean removed;
    synchronized (list) {
      removed = list.removeIf(m -> m != null && m.equalsIgnoreCase(mask));
    }

    if (removed) {
      runtimeConfig.forgetSoftIgnoreMask(sid, mask);
      changes.onNext(new Change(sid, ListKind.SOFT_IGNORE));
    }

    return removed;
  }

  // ----------------- normalization helpers -----------------

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  /** Trim + collapse whitespace. */
  private static String normalizeMask(String raw) {
    String s = Objects.toString(raw, "").trim();
    // no internal whitespace in masks
    s = s.replaceAll("\\s+", "");
    return s;
  }

  /**
   * Convert user input to a hostmask pattern.
   *
   * <p>We do NOT resolve nicks to actual hostmasks yet. For now:
   * <ul>
   *   <li>If input contains '!' and '@', we treat it as a full hostmask/pattern and store it.</li>
   *   <li>If input contains '@' but not '!', we treat it as "*!user@host" (or "*!*@host").</li>
   *   <li>If input contains neither, we treat it as a nick pattern: "nick!*@*".</li>
   * </ul>
   */
  public static String normalizeMaskOrNickToHostmask(String rawMaskOrNick) {
    String s = normalizeMask(rawMaskOrNick);
    if (s.isEmpty()) return "";

    // Full hostmask/pattern already.
    if (s.indexOf('!') >= 0 && s.indexOf('@') >= 0) {
      return s;
    }

    // Something@host (maybe ident@host or *@host).
    if (s.indexOf('@') >= 0) {
      // If it already has a leading "*!" prefix, keep it.
      if (s.startsWith("*!")) return s;
      // If it starts with "!" (rare), prefix nick wildcard.
      if (s.startsWith("!")) return "*" + s;
      return "*!" + s;
    }

    // Host-only
    if (looksLikeHost(s)) {
      return "*!*@" + s;
    }

    // Otherwise treat as nick.
    return s + "!*@*";
  }

  private static boolean looksLikeHost(String s) {
    // Heuristic: contains a dot or colon (v6), or ends with known-ish TLD length.
    String lower = s.toLowerCase(Locale.ROOT);
    return lower.contains(".") || lower.contains(":") || lower.endsWith("/");
  }
}
