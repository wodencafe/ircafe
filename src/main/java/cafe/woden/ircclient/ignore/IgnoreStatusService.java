package cafe.woden.ircclient.ignore;

import cafe.woden.ircclient.model.UserListStore;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Centralized ignore matching service.
 *
 * <p>This exists so that UI indicators (context menus, user list badges/tooltips) and
 * inbound message filtering agree on the same ignore matching rules.
 */
@Component
public class IgnoreStatusService {

  /**
   * @param hard whether sender is hard-ignored
   * @param soft whether sender is soft-ignored
   * @param usedHostmask whether matching was done against a useful hostmask
   * @param hostmaskUsed the useful hostmask used for matching (may be blank when usedHostmask=false)
   */
  public record Status(boolean hard, boolean soft, boolean usedHostmask, String hostmaskUsed) {}

  private record Masks(List<String> hardMasks, List<String> softMasks) {}

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
          ignoreListService.changes().subscribe(
              ch -> {
                if (ch == null) return;
                String sid = Objects.toString(ch.serverId(), "").trim();
                if (sid.isEmpty()) return;
                cacheByServer.remove(sid);
              },
              err -> {
                // ignore
              }
          )
      );
    }
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
  }

  private Masks masksFor(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || ignoreListService == null) {
      return new Masks(List.of(), List.of());
    }

    return cacheByServer.computeIfAbsent(sid, s -> new Masks(
        ignoreListService.listMasks(s),
        ignoreListService.listSoftMasks(s)
    ));
  }

  /**
   * Compute ignore status for a sender.
   *
   * <p>If a useful hostmask is available (either passed in, or learned from the user store),
   * we prefer hostmask matching. Otherwise we fall back to nick-glob matching.
   */
  public Status status(String serverId, String nick, String hostmask) {
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
      hard = IgnoreMaskMatcher.hostmaskTargetedByAny(masks.hardMasks, hm);
      soft = IgnoreMaskMatcher.hostmaskTargetedByAny(masks.softMasks, hm);
    } else {
      hard = IgnoreMaskMatcher.nickTargetedByAny(masks.hardMasks, n);
      soft = IgnoreMaskMatcher.nickTargetedByAny(masks.softMasks, n);
    }

    return new Status(hard, soft, useHostmask, useHostmask ? hm : "");
  }

  /**
   * Convenience for ignore dialogs: choose the best identifier (hostmask if useful, else nick).
   */
  public String bestSeedForMask(String serverId, String nick, String hostmask) {
    Status st = status(serverId, nick, hostmask);
    if (st.usedHostmask() && st.hostmaskUsed() != null && !st.hostmaskUsed().isBlank()) {
      return st.hostmaskUsed();
    }
    return Objects.toString(nick, "").trim();
  }
}
