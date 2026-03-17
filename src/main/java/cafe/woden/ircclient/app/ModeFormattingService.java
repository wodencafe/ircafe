package cafe.woden.ircclient.app;

import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Centralized formatting for IRC MODE output.
 *
 * <p>This is intentionally a Spring component so callers (like {@link IrcMediator}) can depend on
 * an injectable formatter rather than static helpers.
 */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class ModeFormattingService {
  private final ServerIsupportStatePort serverIsupportState;

  public List<String> prettyModeChange(
      String serverId, String actor, String channel, String details) {
    ModeVocabulary vocabulary = serverIsupportState.vocabularyForServer(serverId);
    return ModePrettyPrinter.pretty(vocabulary, actor, channel, details);
  }

  public String describeCurrentChannelModes(String serverId, String details) {
    ModeVocabulary vocabulary = serverIsupportState.vocabularyForServer(serverId);
    return ModeSummary.describeCurrentChannelModes(vocabulary, details);
  }

  public String describeBufferedJoinModes(Set<Character> plus, Set<Character> minus) {
    return ModeSummary.describeBufferedJoinModes(plus, minus);
  }
}
