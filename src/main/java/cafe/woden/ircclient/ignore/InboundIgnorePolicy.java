package cafe.woden.ircclient.ignore;

import java.util.List;
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
    return decide(serverId, fromNick, hostmask, isCtcp, List.of());
  }

  /**
   * Decide how a message from a sender should be handled in a specific inbound level context.
   *
   * <p>Level values follow irssi naming (for example: MSGS, PUBLIC, NOTICES, CTCPS, ACTIONS).
   */
  public Decision decide(
      String serverId,
      String fromNick,
      String hostmask,
      boolean isCtcp,
      List<String> inboundLevels) {
    return decide(serverId, fromNick, hostmask, isCtcp, inboundLevels, "", "");
  }

  /**
   * Decide how a message from a sender should be handled in a specific inbound level and channel
   * context.
   */
  public Decision decide(
      String serverId,
      String fromNick,
      String hostmask,
      boolean isCtcp,
      List<String> inboundLevels,
      String inboundChannel) {
    return decide(serverId, fromNick, hostmask, isCtcp, inboundLevels, inboundChannel, "");
  }

  /**
   * Decide how a message from a sender should be handled in a specific inbound level/channel/text
   * context.
   */
  public Decision decide(
      String serverId,
      String fromNick,
      String hostmask,
      boolean isCtcp,
      List<String> inboundLevels,
      String inboundChannel,
      String inboundText) {
    if (ignoreListService == null) return Decision.ALLOW;

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return Decision.ALLOW;

    String nick = Objects.toString(fromNick, "").trim();
    if (nick.isEmpty()) return Decision.ALLOW;

    // Delegate the matching rules to IgnoreStatusService so UI + inbound agree.
    IgnoreStatusService.Status st =
        (ignoreStatusService == null)
            ? new IgnoreStatusService.Status(false, false, false, "")
            : ignoreStatusService.status(
                sid, nick, hostmask, inboundLevels, inboundChannel, inboundText);

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
