package cafe.woden.ircclient.ignore;

import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Centralized decision point for how inbound messages should be handled with respect to ignore
 * lists.
 */
@Component
public class InboundIgnorePolicy {

  public enum Decision {
    ALLOW,

    SOFT_SPOILER,

    HARD_DROP
  }

  private final IgnoreListService ignoreListService;
  private final IgnoreStatusService ignoreStatusService;

  public InboundIgnorePolicy(
      IgnoreListService ignoreListService, IgnoreStatusService ignoreStatusService) {
    this.ignoreListService = ignoreListService;
    this.ignoreStatusService = ignoreStatusService;
  }

  /**
   * Decide how a message from a sender should be handled.
   *
   * @param serverId server id
   * @param fromNick sender nick
   * @param hostmask optional full hostmask (nick!ident@host) if already known; may be null
   * @param isCtcp whether this is a CTCP request/reply (hard ignore can optionally exclude CTCP)
   */
  public Decision decide(String serverId, String fromNick, String hostmask, boolean isCtcp) {
    if (ignoreListService == null) return Decision.ALLOW;

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return Decision.ALLOW;

    String nick = Objects.toString(fromNick, "").trim();
    if (nick.isEmpty()) return Decision.ALLOW;

    // Delegate the matching rules to IgnoreStatusService so UI + inbound agree.
    IgnoreStatusService.Status st =
        (ignoreStatusService == null)
            ? new IgnoreStatusService.Status(false, false, false, "")
            : ignoreStatusService.status(sid, nick, hostmask);

    if (st.hard()) {
      if (!isCtcp || ignoreListService.hardIgnoreIncludesCtcp()) {
        return Decision.HARD_DROP;
      }
    }
    if (st.soft()) {
      if (isCtcp && ignoreListService.softIgnoreIncludesCtcp()) {
        return Decision.HARD_DROP;
      }
      return Decision.SOFT_SPOILER;
    }
    return Decision.ALLOW;
  }
}
