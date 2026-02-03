package cafe.woden.ircclient.app;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Centralized formatting for IRC MODE output.
 *
 * <p>This is intentionally a Spring component so callers (like {@link IrcMediator})
 * can depend on an injectable formatter rather than static helpers.
 */
@Component
public final class ModeFormattingService {

  /** Pretty-prints a MODE change (e.g. "+b mask") into human-friendly sentences. */
  public List<String> prettyModeChange(String actor, String channel, String details) {
    return ModePrettyPrinter.pretty(actor, channel, details);
  }

  /** Summarizes current channel modes from the 324 numeric details string. */
  public String describeCurrentChannelModes(String details) {
    return ModeSummary.describeCurrentChannelModes(details);
  }

  /** Summarizes buffered join-burst modes into a single human-friendly line. */
  public String describeBufferedJoinModes(Set<Character> plus, Set<Character> minus) {
    return ModeSummary.describeBufferedJoinModes(plus, minus);
  }
}
