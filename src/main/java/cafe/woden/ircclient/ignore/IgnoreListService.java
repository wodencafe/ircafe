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

@Component
public class IgnoreListService {

  private final RuntimeConfigStore runtimeConfig;

  private volatile boolean hardIgnoreIncludesCtcp;

  private volatile boolean softIgnoreIncludesCtcp;

  private final ConcurrentHashMap<String, List<String>> masksByServer = new ConcurrentHashMap<>();

  /**
   * Optional per-mask level metadata for hard ignores.
   *
   * <p>Key shape: serverId -> lowercased mask -> normalized levels (non-empty; default is ALL).
   */
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<String>>>
      hardMaskLevelsByServer = new ConcurrentHashMap<>();

  /**
   * Optional per-mask channel scope metadata for hard ignores.
   *
   * <p>Key shape: serverId -> lowercased mask -> normalized channel patterns. Empty/absent means
   * all channels (and private messages).
   */
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<String>>>
      hardMaskChannelsByServer = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, List<String>> softMasksByServer =
      new ConcurrentHashMap<>();

  public enum AddMaskResult {
    ADDED,
    UPDATED,
    UNCHANGED
  }

  public enum ListKind {
    IGNORE,
    SOFT_IGNORE
  }

  public record Change(String serverId, ListKind kind) {}

  private final FlowableProcessor<Change> changes =
      PublishProcessor.<Change>create().toSerialized();

  public Flowable<Change> changes() {
    return changes.onBackpressureBuffer();
  }

  public boolean hardIgnoreIncludesCtcp() {
    return hardIgnoreIncludesCtcp;
  }

  public boolean softIgnoreIncludesCtcp() {
    return softIgnoreIncludesCtcp;
  }

  public void setHardIgnoreIncludesCtcp(boolean enabled) {
    this.hardIgnoreIncludesCtcp = enabled;
    runtimeConfig.rememberHardIgnoreIncludesCtcp(enabled);
  }

  public void setSoftIgnoreIncludesCtcp(boolean enabled) {
    this.softIgnoreIncludesCtcp = enabled;
    runtimeConfig.rememberSoftIgnoreIncludesCtcp(enabled);
  }

  public IgnoreListService(IgnoreProperties props, RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
    this.hardIgnoreIncludesCtcp =
        (props == null) ? true : Boolean.TRUE.equals(props.hardIgnoreIncludesCtcp());
    this.softIgnoreIncludesCtcp =
        (props != null) && Boolean.TRUE.equals(props.softIgnoreIncludesCtcp());
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
          ConcurrentHashMap<String, List<String>> byMaskLevels = hardMaskLevelsForServer(sid);
          ConcurrentHashMap<String, List<String>> byMaskChannels = hardMaskChannelsForServer(sid);
          for (String mask : hard) {
            List<String> levels =
                IgnoreLevels.normalizeConfigured(
                    lookupConfiguredList(si.maskLevels(), mask).orElse(List.of()));
            byMaskLevels.put(maskKey(mask), levels);

            List<String> channels =
                normalizeChannels(lookupConfiguredList(si.maskChannels(), mask).orElse(List.of()));
            if (!channels.isEmpty()) {
              byMaskChannels.put(maskKey(mask), channels);
            }
          }
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

  private static java.util.Optional<List<String>> lookupConfiguredList(
      Map<String, List<String>> valuesByMask, String mask) {
    if (valuesByMask == null || valuesByMask.isEmpty()) return java.util.Optional.empty();
    String m = Objects.toString(mask, "");
    for (Map.Entry<String, List<String>> ent : valuesByMask.entrySet()) {
      if (Objects.toString(ent.getKey(), "").equalsIgnoreCase(m)) {
        return java.util.Optional.ofNullable(ent.getValue());
      }
    }
    return java.util.Optional.empty();
  }

  public List<String> listMasks(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<String> list = masksByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  public List<String> listSoftMasks(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    List<String> list = softMasksByServer.get(sid);
    if (list == null) return List.of();
    synchronized (list) {
      return List.copyOf(list);
    }
  }

  public boolean addMask(String serverId, String rawMaskOrNick) {
    return addMaskWithLevels(serverId, rawMaskOrNick, List.of()) == AddMaskResult.ADDED;
  }

  public AddMaskResult addMaskWithLevels(
      String serverId, String rawMaskOrNick, List<String> levels) {
    return addMaskWithLevels(serverId, rawMaskOrNick, levels, List.of());
  }

  public AddMaskResult addMaskWithLevels(
      String serverId, String rawMaskOrNick, List<String> levels, List<String> channels) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return AddMaskResult.UNCHANGED;

    String mask = normalizeMaskOrNickToHostmask(rawMaskOrNick);
    if (mask.isEmpty()) return AddMaskResult.UNCHANGED;

    List<String> normalizedLevels = IgnoreLevels.normalizeConfigured(levels);
    List<String> normalizedChannels = normalizeChannels(channels);
    String key = maskKey(mask);

    boolean added = false;
    String storedMask = mask;
    List<String> list =
        masksByServer.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (list) {
      String existing =
          list.stream()
              .filter(m -> Objects.toString(m, "").equalsIgnoreCase(mask))
              .findFirst()
              .orElse(null);
      if (existing == null) {
        list.add(mask);
        storedMask = mask;
        added = true;
      } else {
        storedMask = existing;
      }
    }

    ConcurrentHashMap<String, List<String>> byMask = hardMaskLevelsForServer(sid);
    List<String> prevLevels = byMask.put(key, normalizedLevels);
    boolean levelsChanged = !Objects.equals(prevLevels, normalizedLevels);

    ConcurrentHashMap<String, List<String>> byMaskChannels = hardMaskChannelsForServer(sid);
    List<String> prevChannels =
        normalizedChannels.isEmpty()
            ? byMaskChannels.remove(key)
            : byMaskChannels.put(key, normalizedChannels);
    List<String> prevNormalizedChannels = normalizeChannels(prevChannels);
    boolean channelsChanged = !Objects.equals(prevNormalizedChannels, normalizedChannels);

    if (!added && !levelsChanged && !channelsChanged) {
      return AddMaskResult.UNCHANGED;
    }

    if (added) {
      runtimeConfig.rememberIgnoreMask(sid, storedMask);
    }
    runtimeConfig.rememberIgnoreMaskLevels(sid, storedMask, normalizedLevels);
    runtimeConfig.rememberIgnoreMaskChannels(sid, storedMask, normalizedChannels);
    changes.onNext(new Change(sid, ListKind.IGNORE));
    return added ? AddMaskResult.ADDED : AddMaskResult.UPDATED;
  }

  public boolean addSoftMask(String serverId, String rawMaskOrNick) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    String mask = normalizeMaskOrNickToHostmask(rawMaskOrNick);
    if (mask.isEmpty()) return false;

    List<String> list =
        softMasksByServer.computeIfAbsent(
            sid, k -> Collections.synchronizedList(new ArrayList<>()));
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
      ConcurrentHashMap<String, List<String>> byMask = hardMaskLevelsByServer.get(sid);
      if (byMask != null) {
        byMask
            .entrySet()
            .removeIf(e -> Objects.toString(e.getKey(), "").equalsIgnoreCase(maskKey(mask)));
      }
      ConcurrentHashMap<String, List<String>> byMaskChannels = hardMaskChannelsByServer.get(sid);
      if (byMaskChannels != null) {
        byMaskChannels
            .entrySet()
            .removeIf(e -> Objects.toString(e.getKey(), "").equalsIgnoreCase(maskKey(mask)));
      }
      runtimeConfig.forgetIgnoreMask(sid, mask);
      changes.onNext(new Change(sid, ListKind.IGNORE));
    }

    return removed;
  }

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

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeMask(String raw) {
    String s = Objects.toString(raw, "").trim();
    // no internal whitespace in masks
    s = s.replaceAll("\\s+", "");
    return s;
  }

  /**
   * Convert user input to a hostmask pattern (full mask, user@host, host-only, or nick pattern).
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

  public boolean isHardIgnored(String serverId, String fromHostmask) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    String hm = Objects.toString(fromHostmask, "").trim();
    if (hm.isEmpty()) return false;

    List<String> masks = listMasks(sid);
    if (masks.isEmpty()) return false;

    return IgnoreMaskMatcher.hostmaskTargetedByAny(masks, hm);
  }

  public boolean isSoftIgnored(String serverId, String fromHostmask) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    String hm = Objects.toString(fromHostmask, "").trim();
    if (hm.isEmpty()) return false;

    List<String> masks = listSoftMasks(sid);
    if (masks.isEmpty()) return false;

    return IgnoreMaskMatcher.hostmaskTargetedByAny(masks, hm);
  }

  /** Returns configured hard-ignore levels for a specific mask; defaults to {@code ALL}. */
  public List<String> levelsForHardMask(String serverId, String mask) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of("ALL");
    String key = maskKey(mask);
    if (key.isEmpty()) return List.of("ALL");
    ConcurrentHashMap<String, List<String>> byMask = hardMaskLevelsByServer.get(sid);
    if (byMask == null) return List.of("ALL");
    List<String> levels = byMask.get(key);
    return IgnoreLevels.normalizeConfigured(levels);
  }

  /** Snapshot of hard-ignore levels by lowercased mask key. */
  public Map<String, List<String>> hardMaskLevels(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Map.of();
    ConcurrentHashMap<String, List<String>> byMask = hardMaskLevelsByServer.get(sid);
    if (byMask == null || byMask.isEmpty()) return Map.of();
    return Map.copyOf(byMask);
  }

  /**
   * Returns configured hard-ignore channel scopes for a specific mask; empty means unrestricted.
   */
  public List<String> channelsForHardMask(String serverId, String mask) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    String key = maskKey(mask);
    if (key.isEmpty()) return List.of();
    ConcurrentHashMap<String, List<String>> byMask = hardMaskChannelsByServer.get(sid);
    if (byMask == null) return List.of();
    return normalizeChannels(byMask.get(key));
  }

  /** Snapshot of hard-ignore channel scopes by lowercased mask key. */
  public Map<String, List<String>> hardMaskChannels(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Map.of();
    ConcurrentHashMap<String, List<String>> byMask = hardMaskChannelsByServer.get(sid);
    if (byMask == null || byMask.isEmpty()) return Map.of();
    return Map.copyOf(byMask);
  }

  private ConcurrentHashMap<String, List<String>> hardMaskLevelsForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    return hardMaskLevelsByServer.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
  }

  private ConcurrentHashMap<String, List<String>> hardMaskChannelsForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    return hardMaskChannelsByServer.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
  }

  private static String maskKey(String mask) {
    return normalizeMask(mask).toLowerCase(Locale.ROOT);
  }

  private static List<String> normalizeChannels(List<String> channels) {
    if (channels == null || channels.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    for (String raw : channels) {
      String c = Objects.toString(raw, "").trim();
      if (!(c.startsWith("#") || c.startsWith("&"))) continue;
      if (out.stream().noneMatch(existing -> existing.equalsIgnoreCase(c))) {
        out.add(c);
      }
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }
}
