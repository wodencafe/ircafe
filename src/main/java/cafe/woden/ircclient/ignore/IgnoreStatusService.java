package cafe.woden.ircclient.ignore;

import cafe.woden.ircclient.irc.UserListStore;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
      Map<String, List<String>> hardMaskChannels) {}

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
      return new Masks(List.of(), List.of(), Map.of(), Map.of());
    }

    return cacheByServer.computeIfAbsent(
        sid,
        s ->
            new Masks(
                ignoreListService.listMasks(s),
                ignoreListService.listSoftMasks(s),
                ignoreListService.hardMaskLevels(s),
                ignoreListService.hardMaskChannels(s)));
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
    return status(serverId, nick, hostmask, inboundLevels, "");
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
    if (ignoreListService == null) return new Status(false, false, false, "");

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return new Status(false, false, false, "");

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
              hm,
              inboundLevels,
              inboundChannel);
      soft = IgnoreMaskMatcher.hostmaskTargetedByAny(masks.softMasks, hm);
    } else {
      hard =
          nickTargetedByAnyWithMetadata(
              masks.hardMasks,
              masks.hardMaskLevels,
              masks.hardMaskChannels,
              n,
              inboundLevels,
              inboundChannel);
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
      String hostmask,
      List<String> inboundLevels,
      String inboundChannel) {
    if (masks == null || masks.isEmpty()) return false;
    String hm = Objects.toString(hostmask, "").trim();
    if (hm.isEmpty()) return false;
    for (String mask : masks) {
      String m = Objects.toString(mask, "").trim();
      if (m.isEmpty()) continue;
      if (!IgnoreMaskMatcher.globMatchIgnoreMask(m, hm)) continue;
      List<String> configuredChannels =
          (channelsByLowerMask == null)
              ? List.of()
              : channelsByLowerMask.getOrDefault(m.toLowerCase(java.util.Locale.ROOT), List.of());
      if (!channelMatches(configuredChannels, inboundChannel)) continue;
      List<String> configured =
          (levelsByLowerMask == null)
              ? List.of("ALL")
              : levelsByLowerMask.getOrDefault(
                  m.toLowerCase(java.util.Locale.ROOT), List.of("ALL"));
      if (IgnoreLevels.matches(configured, inboundLevels)) {
        return true;
      }
    }
    return false;
  }

  private static boolean nickTargetedByAnyWithMetadata(
      List<String> masks,
      Map<String, List<String>> levelsByLowerMask,
      Map<String, List<String>> channelsByLowerMask,
      String nick,
      List<String> inboundLevels,
      String inboundChannel) {
    if (masks == null || masks.isEmpty()) return false;
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return false;

    for (String mask : masks) {
      String m = Objects.toString(mask, "").trim();
      if (m.isEmpty()) continue;
      int bang = m.indexOf('!');
      if (bang <= 0) continue;
      String nickGlob = m.substring(0, bang).trim();
      if (nickGlob.isEmpty()) continue;
      if (nickGlob.chars().allMatch(ch -> ch == '*' || ch == '?')) continue;
      if (!IgnoreMaskMatcher.globMatches(nickGlob, n)) continue;

      List<String> configuredChannels =
          (channelsByLowerMask == null)
              ? List.of()
              : channelsByLowerMask.getOrDefault(m.toLowerCase(java.util.Locale.ROOT), List.of());
      if (!channelMatches(configuredChannels, inboundChannel)) continue;

      List<String> configured =
          (levelsByLowerMask == null)
              ? List.of("ALL")
              : levelsByLowerMask.getOrDefault(
                  m.toLowerCase(java.util.Locale.ROOT), List.of("ALL"));
      if (IgnoreLevels.matches(configured, inboundLevels)) {
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
}
