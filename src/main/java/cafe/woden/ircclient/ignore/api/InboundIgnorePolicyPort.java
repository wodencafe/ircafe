package cafe.woden.ircclient.ignore.api;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-facing policy port for inbound ignore decisions. */
@ApplicationLayer
public interface InboundIgnorePolicyPort {

  enum Decision {
    ALLOW,

    SOFT_SPOILER,

    HARD_DROP
  }

  /**
   * Decide how a message from a sender should be handled.
   *
   * @param serverId server id
   * @param fromNick sender nick
   * @param hostmask optional full hostmask (nick!ident@host) if already known; may be null
   * @param isCtcp whether this is a CTCP request/reply (hard ignore can optionally exclude CTCP)
   */
  default Decision decide(String serverId, String fromNick, String hostmask, boolean isCtcp) {
    return decide(serverId, fromNick, hostmask, isCtcp, List.of());
  }

  /**
   * Decide how a message from a sender should be handled in a specific inbound level context.
   *
   * <p>Level values follow irssi naming (for example: MSGS, PUBLIC, NOTICES, CTCPS, ACTIONS).
   */
  default Decision decide(
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
  default Decision decide(
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
  Decision decide(
      String serverId,
      String fromNick,
      String hostmask,
      boolean isCtcp,
      List<String> inboundLevels,
      String inboundChannel,
      String inboundText);
}
