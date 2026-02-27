package cafe.woden.ircclient.ignore;

import cafe.woden.ircclient.irc.UserListStore;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Centralized ignore matching service. */
@Component
public class IgnoreStatusService {

  /**
   * @param hard whether sender is hard-ignored
   * @param soft whether sender is soft-ignored
   * @param usedHostmask whether matching was done against a useful hostmask
   * @param hostmaskUsed the useful hostmask used for matching (may be blank when
   *     usedHostmask=false)
   */
  public record Status(boolean hard, boolean soft, boolean usedHostmask, String hostmaskUsed) {}

  private record Masks(
      List<String> hardMasks,
      List<String> softMasks,
      Map<String, List<String>> hardMaskLevels,
      Map<String, List<String>> hardMaskChannels,
      Map<String, Long> hardMaskExpiries,
      Map<String, String> hardMaskPatterns,
      Map<String, IgnoreTextPatternMode> hardMaskPatternModes,
      Map<String, Boolean> hardMaskReplies) {}

  private final IgnoreListService ignoreListService;
  private final UserListStore userListStore;

  private final ConcurrentHashMap<String, Masks> cacheByServer = new ConcurrentHashMap<>();
  private final CompositeDisposable disposables = new CompositeDisposable();

  public IgnoreStatusService(IgnoreListService ignoreListService, UserListStore userListStore) {
    this.ignoreListService = ignoreListService;
    this.userListStore = userListStore;

    // Invalidate caches whenever a server's ignore list changes.
    if (ignoreListService != null) {
      disposables.add(
          ignoreListService
              .changes()
              .subscribe(
                  ch -> {
                    if (ch == null) return;
                    String sid = Objects.toString(ch.serverId(), "").trim();
                    if (sid.isEmpty()) return;
                    cacheByServer.remove(sid);
                  },
                  err -> {}));
    }
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
  }

  private Masks masksFor(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || ignoreListService == null) {
      return new Masks(
          List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    return cacheByServer.computeIfAbsent(
        sid,
        s ->
            new Masks(
                ignoreListService.listMasks(s),
                ignoreListService.listSoftMasks(s),
                ignoreListService.hardMaskLevels(s),
                ignoreListService.hardMaskChannels(s),
                ignoreListService.hardMaskExpiries(s),
                ignoreListService.hardMaskPatterns(s),
                ignoreListService.hardMaskPatternModes(s),
                ignoreListService.hardMaskReplies(s)));
  }

  /** Compute ignore status for a sender. */
  public Status status(String serverId, String nick, String hostmask) {
    return status(serverId, nick, hostmask, List.of());
  }

  /**
   * Compute ignore status for a sender in a specific inbound level context.
   *
   * <p>If {@code inboundLevels} is empty, level filtering is skipped (legacy behavior).
   */
  public Status status(String serverId, String nick, String hostmask, List<String> inboundLevels) {
    return status(serverId, nick, hostmask, inboundLevels, "", "");
  }

  /**
   * Compute ignore status for a sender in a specific inbound level and channel context.
   *
   * <p>If {@code inboundChannel} is blank, channel-scoped masks do not match.
   */
  public Status status(
      String serverId,
      String nick,
      String hostmask,
      List<String> inboundLevels,
      String inboundChannel) {
    return status(serverId, nick, hostmask, inboundLevels, inboundChannel, "");
  }

  /**
   * Compute ignore status for a sender in a specific inbound level/channel/text context.
   *
   * <p>When a mask has an optional text pattern, it must match {@code inboundText} to apply.
   */
  public Status status(
      String serverId,
      String nick,
      String hostmask,
      List<String> inboundLevels,
      String inboundChannel,
      String inboundText) {
    if (ignoreListService == null) return new Status(false, false, false, "");

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return new Status(false, false, false, "");

    long nowEpochMs = System.currentTimeMillis();
    if (ignoreListService != null) {
      ignoreListService.pruneExpiredHardMasks(sid, nowEpochMs);
    }

    String n = Objects.toString(nick, "").trim();
    String hm = Objects.toString(hostmask, "").trim();

    if (n.isEmpty() && hm.isEmpty()) return new Status(false, false, false, "");

    // Prefer a useful hostmask: explicit hostmask wins, otherwise try learned hostmask.
    if (!IgnoreMaskMatcher.isUsefulHostmask(hm) && userListStore != null && !n.isEmpty()) {
      try {
        String learned = userListStore.getLearnedHostmask(sid, n);
        String lhm = Objects.toString(learned, "").trim();
        if (IgnoreMaskMatcher.isUsefulHostmask(lhm)) hm = lhm;
      } catch (Exception ignored) {
        // Defensive: ignore status should never crash UI or inbound processing.
      }
    }

    Masks masks = masksFor(sid);

    boolean useHostmask = IgnoreMaskMatcher.isUsefulHostmask(hm);

    boolean hard;
    boolean soft;

    if (useHostmask) {
      hard =
          hostmaskTargetedByAnyWithMetadata(
              masks.hardMasks,
              masks.hardMaskLevels,
              masks.hardMaskChannels,
              masks.hardMaskExpiries,
              masks.hardMaskPatterns,
              masks.hardMaskPatternModes,
              masks.hardMaskReplies,
              hm,
              inboundLevels,
              inboundChannel,
              inboundText,
              nowEpochMs);
      soft = IgnoreMaskMatcher.hostmaskTargetedByAny(masks.softMasks, hm);
    } else {
      hard =
          nickTargetedByAnyWithMetadata(
              masks.hardMasks,
              masks.hardMaskLevels,
              masks.hardMaskChannels,
              masks.hardMaskExpiries,
              masks.hardMaskPatterns,
              masks.hardMaskPatternModes,
              masks.hardMaskReplies,
              n,
              inboundLevels,
              inboundChannel,
              inboundText,
              nowEpochMs);
      soft = IgnoreMaskMatcher.nickTargetedByAny(masks.softMasks, n);
    }

    return new Status(hard, soft, useHostmask, useHostmask ? hm : "");
  }

  public String bestSeedForMask(String serverId, String nick, String hostmask) {
    Status st = status(serverId, nick, hostmask);
    if (st.usedHostmask() && st.hostmaskUsed() != null && !st.hostmaskUsed().isBlank()) {
      return st.hostmaskUsed();
    }
    return Objects.toString(nick, "").trim();
  }

  private static boolean hostmaskTargetedByAnyWithMetadata(
      List<String> masks,
      Map<String, List<String>> levelsByLowerMask,
      Map<String, List<String>> channelsByLowerMask,
      Map<String, Long> expiryByLowerMask,
      Map<String, String> patternByLowerMask,
      Map<String, IgnoreTextPatternMode> patternModesByLowerMask,
      Map<String, Boolean> repliesByLowerMask,
      String hostmask,
      List<String> inboundLevels,
      String inboundChannel,
      String inboundText,
      long nowEpochMs) {
    if (masks == null || masks.isEmpty()) return false;
    String hm = Objects.toString(hostmask, "").trim();
    if (hm.isEmpty()) return false;
    for (String mask : masks) {
      String m = Objects.toString(mask, "").trim();
      if (m.isEmpty()) continue;
      if (maskExpired(expiryByLowerMask, m, nowEpochMs)) continue;
      boolean senderMatches = IgnoreMaskMatcher.globMatchIgnoreMask(m, hm);
      List<String> configuredChannels =
          (channelsByLowerMask == null)
              ? List.of()
              : channelsByLowerMask.getOrDefault(m.toLowerCase(java.util.Locale.ROOT), List.of());
      if (!channelMatches(configuredChannels, inboundChannel)) continue;
      if (!textPatternMatches(patternByLowerMask, patternModesByLowerMask, m, inboundText))
        continue;
      List<String> configured =
          (levelsByLowerMask == null)
              ? List.of("ALL")
              : levelsByLowerMask.getOrDefault(
                  m.toLowerCase(java.util.Locale.ROOT), List.of("ALL"));
      if (!IgnoreLevels.matches(configured, inboundLevels)) continue;

      if (senderMatches) {
        return true;
      }

      if (replyMatches(repliesByLowerMask, m, inboundChannel, inboundText)) {
        return true;
      }
    }
    return false;
  }

  private static boolean nickTargetedByAnyWithMetadata(
      List<String> masks,
      Map<String, List<String>> levelsByLowerMask,
      Map<String, List<String>> channelsByLowerMask,
      Map<String, Long> expiryByLowerMask,
      Map<String, String> patternByLowerMask,
      Map<String, IgnoreTextPatternMode> patternModesByLowerMask,
      Map<String, Boolean> repliesByLowerMask,
      String nick,
      List<String> inboundLevels,
      String inboundChannel,
      String inboundText,
      long nowEpochMs) {
    if (masks == null || masks.isEmpty()) return false;
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return false;

    for (String mask : masks) {
      String m = Objects.toString(mask, "").trim();
      if (m.isEmpty()) continue;
      if (maskExpired(expiryByLowerMask, m, nowEpochMs)) continue;
      boolean senderMatches = maskTargetsNick(m, n);

      List<String> configuredChannels =
          (channelsByLowerMask == null)
              ? List.of()
              : channelsByLowerMask.getOrDefault(m.toLowerCase(java.util.Locale.ROOT), List.of());
      if (!channelMatches(configuredChannels, inboundChannel)) continue;
      if (!textPatternMatches(patternByLowerMask, patternModesByLowerMask, m, inboundText))
        continue;

      List<String> configured =
          (levelsByLowerMask == null)
              ? List.of("ALL")
              : levelsByLowerMask.getOrDefault(
                  m.toLowerCase(java.util.Locale.ROOT), List.of("ALL"));
      if (!IgnoreLevels.matches(configured, inboundLevels)) continue;

      if (senderMatches) {
        return true;
      }

      if (replyMatches(repliesByLowerMask, m, inboundChannel, inboundText)) {
        return true;
      }
    }
    return false;
  }

  private static boolean channelMatches(List<String> configuredChannels, String inboundChannel) {
    if (configuredChannels == null || configuredChannels.isEmpty()) return true;
    String ch = Objects.toString(inboundChannel, "").trim();
    if (ch.isEmpty()) return false;
    for (String configured : configuredChannels) {
      String c = Objects.toString(configured, "").trim();
      if (c.isEmpty()) continue;
      if (IgnoreMaskMatcher.globMatches(c, ch)) {
        return true;
      }
    }
    return false;
  }

  private static boolean maskExpired(
      Map<String, Long> expiryByLowerMask, String mask, long nowEpochMs) {
    if (expiryByLowerMask == null || expiryByLowerMask.isEmpty()) return false;
    long expiresAt = expiryByLowerMask.getOrDefault(mask.toLowerCase(java.util.Locale.ROOT), 0L);
    return expiresAt > 0L && expiresAt <= nowEpochMs;
  }

  private static boolean textPatternMatches(
      Map<String, String> patternByLowerMask,
      Map<String, IgnoreTextPatternMode> patternModesByLowerMask,
      String mask,
      String inboundText) {
    String key = Objects.toString(mask, "").toLowerCase(java.util.Locale.ROOT);
    String pattern =
        Objects.toString(
                (patternByLowerMask == null) ? "" : patternByLowerMask.getOrDefault(key, ""), "")
            .trim();
    if (pattern.isEmpty()) return true;

    String text = Objects.toString(inboundText, "");
    if (text.isBlank()) return false;

    IgnoreTextPatternMode mode =
        (patternModesByLowerMask == null)
            ? IgnoreTextPatternMode.GLOB
            : patternModesByLowerMask.getOrDefault(key, IgnoreTextPatternMode.GLOB);
    return textMatchesPattern(text, pattern, mode);
  }

  private static boolean textMatchesPattern(
      String inboundText, String pattern, IgnoreTextPatternMode mode) {
    String text = Objects.toString(inboundText, "");
    String p = Objects.toString(pattern, "").trim();
    IgnoreTextPatternMode m = (mode == null) ? IgnoreTextPatternMode.GLOB : mode;
    if (p.isEmpty() || text.isBlank()) return false;

    return switch (m) {
      case REGEXP -> matchesRegex(text, p);
      case FULL -> matchesFullWord(text, p);
      case GLOB -> matchesGlobPattern(text, p);
    };
  }

  private static boolean matchesRegex(String text, String pattern) {
    try {
      return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
          .matcher(text)
          .find();
    } catch (Exception ex) {
      return false;
    }
  }

  private static boolean matchesFullWord(String text, String pattern) {
    try {
      return Pattern.compile("(?iu)(^|\\b)" + Pattern.quote(pattern) + "(\\b|$)")
          .matcher(text)
          .find();
    } catch (Exception ex) {
      return false;
    }
  }

  private static boolean matchesGlobPattern(String text, String pattern) {
    String p = Objects.toString(pattern, "");
    if (p.indexOf('*') >= 0 || p.indexOf('?') >= 0) {
      return IgnoreMaskMatcher.globMatches(p, text);
    }
    return text.toLowerCase(java.util.Locale.ROOT).contains(p.toLowerCase(java.util.Locale.ROOT));
  }

  private static boolean replyMatches(
      Map<String, Boolean> repliesByLowerMask,
      String mask,
      String inboundChannel,
      String inboundText) {
    if (repliesByLowerMask == null || repliesByLowerMask.isEmpty()) return false;
    String key = Objects.toString(mask, "").toLowerCase(java.util.Locale.ROOT);
    if (!Boolean.TRUE.equals(repliesByLowerMask.get(key))) return false;
    String ch = Objects.toString(inboundChannel, "").trim();
    if (ch.isEmpty()) return false;

    String replyNick = extractReplyTargetNick(inboundText);
    if (replyNick.isEmpty()) return false;
    return maskTargetsNick(mask, replyNick);
  }

  private static String extractReplyTargetNick(String inboundText) {
    String text = Objects.toString(inboundText, "").trim();
    if (text.isEmpty()) return "";
    int sp = text.indexOf(' ');
    String first = (sp >= 0) ? text.substring(0, sp).trim() : text;
    if (first.isEmpty()) return "";

    if (first.endsWith(":") || first.endsWith(",")) {
      String nick = first.substring(0, first.length() - 1).trim();
      if (nick.startsWith("@")) nick = nick.substring(1).trim();
      return nick;
    }
    return "";
  }

  private static boolean maskTargetsNick(String mask, String nick) {
    String m = Objects.toString(mask, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (m.isEmpty() || n.isEmpty()) return false;
    int bang = m.indexOf('!');
    if (bang <= 0) return false;
    String nickGlob = m.substring(0, bang).trim();
    if (nickGlob.isEmpty()) return false;
    if (nickGlob.chars().allMatch(ch -> ch == '*' || ch == '?')) return false;
    return IgnoreMaskMatcher.globMatches(nickGlob, n);
  }
}
