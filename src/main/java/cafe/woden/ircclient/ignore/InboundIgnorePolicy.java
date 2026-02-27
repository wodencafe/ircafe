package cafe.woden.ircclient.ignore;

import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Centralized decision point for how inbound messages should be handled with respect to ignore
 * lists.
 */
@Component
@ApplicationLayer
public class InboundIgnorePolicy implements InboundIgnorePolicyPort {

  private final IgnoreListService ignoreListService;
  private final IgnoreStatusService ignoreStatusService;

  public InboundIgnorePolicy(
      IgnoreListService ignoreListService, IgnoreStatusService ignoreStatusService) {
    this.ignoreListService = ignoreListService;
    this.ignoreStatusService = ignoreStatusService;
  }

  @Override
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
