package cafe.woden.ircclient.ignore;

import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.api.IgnoreAddMaskResult;
import cafe.woden.ircclient.ignore.api.IgnoreLevels;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.IgnoreMaskNormalizer;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
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
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

@Component
@ApplicationLayer
public class IgnoreListService implements IgnoreListQueryPort, IgnoreListCommandPort {

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

  /**
   * Optional per-mask absolute expiry metadata for hard ignores.
   *
   * <p>Key shape: serverId -> lowercased mask -> expiry epoch millis UTC (>0).
   */
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> hardMaskExpiryByServer =
      new ConcurrentHashMap<>();

  /**
   * Optional per-mask message text pattern metadata for hard ignores.
   *
   * <p>Key shape: serverId -> lowercased mask -> non-blank pattern.
   */
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>>
      hardMaskPatternByServer = new ConcurrentHashMap<>();

  /**
   * Optional per-mask text pattern mode metadata for hard ignores.
   *
   * <p>Only meaningful when a pattern exists for the same mask key.
   */
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, IgnoreTextPatternMode>>
      hardMaskPatternModeByServer = new ConcurrentHashMap<>();

  /**
   * Optional per-mask replies flag metadata for hard ignores.
   *
   * <p>When true, messages that reply to an ignored nick can also be ignored.
   */
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>
      hardMaskRepliesByServer = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, List<String>> softMasksByServer =
      new ConcurrentHashMap<>();

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
          ConcurrentHashMap<String, Long> byMaskExpiry = hardMaskExpiryForServer(sid);
          ConcurrentHashMap<String, String> byMaskPattern = hardMaskPatternForServer(sid);
          ConcurrentHashMap<String, IgnoreTextPatternMode> byMaskPatternMode =
              hardMaskPatternModeForServer(sid);
          ConcurrentHashMap<String, Boolean> byMaskReplies = hardMaskRepliesForServer(sid);
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

            long expiresAt = lookupConfiguredLong(si.maskExpiresAt(), mask).orElse(0L);
            if (expiresAt > 0L) {
              byMaskExpiry.put(maskKey(mask), expiresAt);
            }

            String textPattern =
                normalizePattern(lookupConfiguredString(si.maskPatterns(), mask).orElse(""));
            if (!textPattern.isEmpty()) {
              IgnoreTextPatternMode mode =
                  normalizePatternMode(
                      IgnoreTextPatternMode.fromToken(
                          lookupConfiguredString(si.maskPatternModes(), mask).orElse("glob")));
              String key = maskKey(mask);
              byMaskPattern.put(key, textPattern);
              byMaskPatternMode.put(key, mode);
            }

            boolean replies = lookupConfiguredBoolean(si.maskReplies(), mask).orElse(Boolean.FALSE);
            if (replies) {
              byMaskReplies.put(maskKey(mask), Boolean.TRUE);
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

  private static java.util.Optional<Long> lookupConfiguredLong(
      Map<String, Long> valuesByMask, String mask) {
    if (valuesByMask == null || valuesByMask.isEmpty()) return java.util.Optional.empty();
    String m = Objects.toString(mask, "");
    for (Map.Entry<String, Long> ent : valuesByMask.entrySet()) {
      if (Objects.toString(ent.getKey(), "").equalsIgnoreCase(m)) {
        Long value = ent.getValue();
        if (value != null && value > 0L) {
          return java.util.Optional.of(value);
        }
        return java.util.Optional.empty();
      }
    }
    return java.util.Optional.empty();
  }

  private static java.util.Optional<String> lookupConfiguredString(
      Map<String, String> valuesByMask, String mask) {
    if (valuesByMask == null || valuesByMask.isEmpty()) return java.util.Optional.empty();
    String m = Objects.toString(mask, "");
    for (Map.Entry<String, String> ent : valuesByMask.entrySet()) {
      if (Objects.toString(ent.getKey(), "").equalsIgnoreCase(m)) {
        return java.util.Optional.ofNullable(ent.getValue());
      }
    }
    return java.util.Optional.empty();
  }

  private static java.util.Optional<Boolean> lookupConfiguredBoolean(
      Map<String, Boolean> valuesByMask, String mask) {
    if (valuesByMask == null || valuesByMask.isEmpty()) return java.util.Optional.empty();
    String m = Objects.toString(mask, "");
    for (Map.Entry<String, Boolean> ent : valuesByMask.entrySet()) {
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
    return addMaskWithLevels(serverId, rawMaskOrNick, List.of()) == IgnoreAddMaskResult.ADDED;
  }

  public IgnoreAddMaskResult addMaskWithLevels(
      String serverId, String rawMaskOrNick, List<String> levels) {
    return addMaskWithLevels(serverId, rawMaskOrNick, levels, List.of(), null);
  }

  public IgnoreAddMaskResult addMaskWithLevels(
      String serverId, String rawMaskOrNick, List<String> levels, List<String> channels) {
    return addMaskWithLevels(
        serverId, rawMaskOrNick, levels, channels, null, "", IgnoreTextPatternMode.GLOB, false);
  }

  public IgnoreAddMaskResult addMaskWithLevels(
      String serverId,
      String rawMaskOrNick,
      List<String> levels,
      List<String> channels,
      Long expiresAtEpochMs) {
    return addMaskWithLevels(
        serverId,
        rawMaskOrNick,
        levels,
        channels,
        expiresAtEpochMs,
        "",
        IgnoreTextPatternMode.GLOB,
        false);
  }

  public IgnoreAddMaskResult addMaskWithLevels(
      String serverId,
      String rawMaskOrNick,
      List<String> levels,
      List<String> channels,
      Long expiresAtEpochMs,
      String textPattern,
      IgnoreTextPatternMode textPatternMode) {
    return addMaskWithLevels(
        serverId,
        rawMaskOrNick,
        levels,
        channels,
        expiresAtEpochMs,
        textPattern,
        textPatternMode,
        false);
  }

  public IgnoreAddMaskResult addMaskWithLevels(
      String serverId,
      String rawMaskOrNick,
      List<String> levels,
      List<String> channels,
      Long expiresAtEpochMs,
      String textPattern,
      IgnoreTextPatternMode textPatternMode,
      boolean repliesEnabled) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return IgnoreAddMaskResult.UNCHANGED;

    String mask = normalizeMaskOrNickToHostmask(rawMaskOrNick);
    if (mask.isEmpty()) return IgnoreAddMaskResult.UNCHANGED;

    List<String> normalizedLevels = IgnoreLevels.normalizeConfigured(levels);
    List<String> normalizedChannels = normalizeChannels(channels);
    long normalizedExpiry = normalizeExpiryEpochMs(expiresAtEpochMs);
    String normalizedPattern = normalizePattern(textPattern);
    IgnoreTextPatternMode normalizedPatternMode = normalizePatternMode(textPatternMode);
    boolean normalizedReplies = normalizeReplies(repliesEnabled);
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

    ConcurrentHashMap<String, Long> byMaskExpiry = hardMaskExpiryForServer(sid);
    Long prevExpiry =
        (normalizedExpiry <= 0L)
            ? byMaskExpiry.remove(key)
            : byMaskExpiry.put(key, normalizedExpiry);
    long prevNormalizedExpiry = normalizeExpiryEpochMs(prevExpiry);
    boolean expiryChanged = prevNormalizedExpiry != normalizedExpiry;

    ConcurrentHashMap<String, String> byMaskPattern = hardMaskPatternForServer(sid);
    String prevPattern =
        normalizedPattern.isEmpty()
            ? byMaskPattern.remove(key)
            : byMaskPattern.put(key, normalizedPattern);
    String prevNormalizedPattern = normalizePattern(prevPattern);
    boolean patternChanged = !Objects.equals(prevNormalizedPattern, normalizedPattern);

    ConcurrentHashMap<String, IgnoreTextPatternMode> byMaskPatternMode =
        hardMaskPatternModeForServer(sid);
    IgnoreTextPatternMode prevPatternMode;
    if (normalizedPattern.isEmpty()) {
      prevPatternMode = byMaskPatternMode.remove(key);
    } else {
      prevPatternMode = byMaskPatternMode.put(key, normalizedPatternMode);
    }
    IgnoreTextPatternMode prevNormalizedMode = normalizePatternMode(prevPatternMode);
    boolean patternModeChanged = prevNormalizedMode != normalizedPatternMode;

    ConcurrentHashMap<String, Boolean> byMaskReplies = hardMaskRepliesForServer(sid);
    Boolean prevReplies =
        normalizedReplies ? byMaskReplies.put(key, Boolean.TRUE) : byMaskReplies.remove(key);
    boolean prevNormalizedReplies = normalizeReplies(prevReplies);
    boolean repliesChanged = prevNormalizedReplies != normalizedReplies;

    if (!added
        && !levelsChanged
        && !channelsChanged
        && !expiryChanged
        && !patternChanged
        && !patternModeChanged
        && !repliesChanged) {
      return IgnoreAddMaskResult.UNCHANGED;
    }

    if (added) {
      runtimeConfig.rememberIgnoreMask(sid, storedMask);
    }
    runtimeConfig.rememberIgnoreMaskLevels(sid, storedMask, normalizedLevels);
    runtimeConfig.rememberIgnoreMaskChannels(sid, storedMask, normalizedChannels);
    runtimeConfig.rememberIgnoreMaskExpiresAt(sid, storedMask, normalizedExpiry);
    runtimeConfig.rememberIgnoreMaskPattern(
        sid, storedMask, normalizedPattern, normalizedPatternMode.token());
    runtimeConfig.rememberIgnoreMaskReplies(sid, storedMask, normalizedReplies);
    changes.onNext(new Change(sid, ListKind.IGNORE));
    return added ? IgnoreAddMaskResult.ADDED : IgnoreAddMaskResult.UPDATED;
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
      ConcurrentHashMap<String, Long> byMaskExpiry = hardMaskExpiryByServer.get(sid);
      if (byMaskExpiry != null) {
        byMaskExpiry
            .entrySet()
            .removeIf(e -> Objects.toString(e.getKey(), "").equalsIgnoreCase(maskKey(mask)));
      }
      ConcurrentHashMap<String, String> byMaskPattern = hardMaskPatternByServer.get(sid);
      if (byMaskPattern != null) {
        byMaskPattern
            .entrySet()
            .removeIf(e -> Objects.toString(e.getKey(), "").equalsIgnoreCase(maskKey(mask)));
      }
      ConcurrentHashMap<String, IgnoreTextPatternMode> byMaskPatternMode =
          hardMaskPatternModeByServer.get(sid);
      if (byMaskPatternMode != null) {
        byMaskPatternMode
            .entrySet()
            .removeIf(e -> Objects.toString(e.getKey(), "").equalsIgnoreCase(maskKey(mask)));
      }
      ConcurrentHashMap<String, Boolean> byMaskReplies = hardMaskRepliesByServer.get(sid);
      if (byMaskReplies != null) {
        byMaskReplies
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
    return IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(rawMaskOrNick);
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

  /** Returns configured hard-ignore expiry epoch millis for a specific mask, or {@code 0}. */
  public long expiresAtEpochMsForHardMask(String serverId, String mask) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return 0L;
    String key = maskKey(mask);
    if (key.isEmpty()) return 0L;
    ConcurrentHashMap<String, Long> byMask = hardMaskExpiryByServer.get(sid);
    if (byMask == null) return 0L;
    return normalizeExpiryEpochMs(byMask.get(key));
  }

  /** Snapshot of hard-ignore absolute expiries by lowercased mask key. */
  public Map<String, Long> hardMaskExpiries(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Map.of();
    ConcurrentHashMap<String, Long> byMask = hardMaskExpiryByServer.get(sid);
    if (byMask == null || byMask.isEmpty()) return Map.of();
    return Map.copyOf(byMask);
  }

  /** Returns configured hard-ignore message pattern for a specific mask, or empty. */
  public String patternForHardMask(String serverId, String mask) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return "";
    String key = maskKey(mask);
    if (key.isEmpty()) return "";
    ConcurrentHashMap<String, String> byMask = hardMaskPatternByServer.get(sid);
    if (byMask == null) return "";
    return normalizePattern(byMask.get(key));
  }

  /** Returns configured hard-ignore text pattern mode for a specific mask; defaults to glob. */
  public IgnoreTextPatternMode patternModeForHardMask(String serverId, String mask) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return IgnoreTextPatternMode.GLOB;
    String key = maskKey(mask);
    if (key.isEmpty()) return IgnoreTextPatternMode.GLOB;
    ConcurrentHashMap<String, IgnoreTextPatternMode> byMask = hardMaskPatternModeByServer.get(sid);
    if (byMask == null) return IgnoreTextPatternMode.GLOB;
    return normalizePatternMode(byMask.get(key));
  }

  /** Snapshot of hard-ignore message patterns by lowercased mask key. */
  public Map<String, String> hardMaskPatterns(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Map.of();
    ConcurrentHashMap<String, String> byMask = hardMaskPatternByServer.get(sid);
    if (byMask == null || byMask.isEmpty()) return Map.of();
    return Map.copyOf(byMask);
  }

  /** Snapshot of hard-ignore text pattern modes by lowercased mask key. */
  public Map<String, IgnoreTextPatternMode> hardMaskPatternModes(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Map.of();
    ConcurrentHashMap<String, IgnoreTextPatternMode> byMask = hardMaskPatternModeByServer.get(sid);
    if (byMask == null || byMask.isEmpty()) return Map.of();
    return Map.copyOf(byMask);
  }

  /** Returns whether a hard-ignore mask should also ignore replies. */
  public boolean repliesForHardMask(String serverId, String mask) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    String key = maskKey(mask);
    if (key.isEmpty()) return false;
    ConcurrentHashMap<String, Boolean> byMask = hardMaskRepliesByServer.get(sid);
    if (byMask == null) return false;
    return normalizeReplies(byMask.get(key));
  }

  /** Snapshot of hard-ignore replies flags by lowercased mask key. */
  public Map<String, Boolean> hardMaskReplies(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Map.of();
    ConcurrentHashMap<String, Boolean> byMask = hardMaskRepliesByServer.get(sid);
    if (byMask == null || byMask.isEmpty()) return Map.of();
    return Map.copyOf(byMask);
  }

  /**
   * Removes expired hard ignore masks for a server and persists the cleanup.
   *
   * @return number of removed masks
   */
  public int pruneExpiredHardMasks(String serverId, long nowEpochMs) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return 0;

    ConcurrentHashMap<String, Long> expiryByMask = hardMaskExpiryByServer.get(sid);
    if (expiryByMask == null || expiryByMask.isEmpty()) return 0;

    List<String> masks = masksByServer.get(sid);
    if (masks == null || masks.isEmpty()) return 0;

    List<String> toRemove = new ArrayList<>();
    synchronized (masks) {
      for (String mask : masks) {
        String key = maskKey(mask);
        long expiresAt = normalizeExpiryEpochMs(expiryByMask.get(key));
        if (expiresAt > 0L && expiresAt <= nowEpochMs) {
          toRemove.add(mask);
        }
      }
    }
    if (toRemove.isEmpty()) return 0;

    int removed = 0;
    for (String mask : toRemove) {
      if (removeMask(sid, mask)) {
        removed++;
      }
    }
    return removed;
  }

  private ConcurrentHashMap<String, List<String>> hardMaskLevelsForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    return hardMaskLevelsByServer.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
  }

  private ConcurrentHashMap<String, List<String>> hardMaskChannelsForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    return hardMaskChannelsByServer.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
  }

  private ConcurrentHashMap<String, Long> hardMaskExpiryForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    return hardMaskExpiryByServer.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
  }

  private ConcurrentHashMap<String, String> hardMaskPatternForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    return hardMaskPatternByServer.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
  }

  private ConcurrentHashMap<String, IgnoreTextPatternMode> hardMaskPatternModeForServer(
      String serverId) {
    String sid = normalizeServerId(serverId);
    return hardMaskPatternModeByServer.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
  }

  private ConcurrentHashMap<String, Boolean> hardMaskRepliesForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    return hardMaskRepliesByServer.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
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

  private static long normalizeExpiryEpochMs(Long raw) {
    if (raw == null || raw <= 0L) return 0L;
    return raw;
  }

  private static String normalizePattern(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static IgnoreTextPatternMode normalizePatternMode(IgnoreTextPatternMode raw) {
    return (raw == null) ? IgnoreTextPatternMode.GLOB : raw;
  }

  private static boolean normalizeReplies(Boolean raw) {
    return Boolean.TRUE.equals(raw);
  }

  private static boolean normalizeReplies(boolean raw) {
    return raw;
  }
}
