package cafe.woden.ircclient.app;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Centralized formatting for IRC MODE output.
 *
 * <p>This is intentionally a Spring component so callers (like {@link IrcMediator})
 * can depend on an injectable formatter rather than static helpers.
 */
@Component
public final class ModeFormattingService {

  private static final Logger log = LoggerFactory.getLogger(ModeFormattingService.class);

  public List<String> prettyModeChange(String actor, String channel, String details) {
    List<String> out = ModePrettyPrinter.pretty(actor, channel, details);
    if (log.isDebugEnabled()) {
      log.debug("MODEDBG formatting prettyModeChange actor={} channel={} details={} -> lines={}",
          clip(actor), clip(channel), clip(details), out.size());
    }
    return out;
  }

  
  public String describeCurrentChannelModes(String details) {
    String s = ModeSummary.describeCurrentChannelModes(details);
    if (log.isDebugEnabled()) {
      log.debug("MODEDBG formatting describeCurrentChannelModes details={} -> {}", clip(details), clip(s));
    }
    return s;
  }

  
  public String describeBufferedJoinModes(Set<Character> plus, Set<Character> minus) {
    String s = ModeSummary.describeBufferedJoinModes(plus, minus);
    if (log.isDebugEnabled()) {
      log.debug("MODEDBG formatting describeBufferedJoinModes plus={} minus={} -> {}", plus, minus, clip(s));
    }
    return s;
  }

  private static String clip(Object v) {
    if (v == null) return "<null>";
    String s = String.valueOf(v);
    if (s == null) return "<null>";
    s = s.replace('\n', ' ').replace('\r', ' ');
    if (s.length() > 200) return s.substring(0, 197) + "...";
    return s;
  }
}
