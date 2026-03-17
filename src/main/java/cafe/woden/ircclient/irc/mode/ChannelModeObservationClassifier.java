package cafe.woden.ircclient.irc.mode;

import cafe.woden.ircclient.irc.IrcEvent;
import java.util.Objects;

/**
 * Classifies inbound live MODE details into either a delta or a snapshot-like observation.
 *
 * <p>Some networks emit unsolicited MODE snapshots (for example {@code +nrf <arg>}) as regular MODE
 * events with no clear actor. We classify those separately so downstream policy can avoid
 * transcript spam.
 */
final class ChannelModeObservationClassifier {
  private ChannelModeObservationClassifier() {}

  static IrcEvent.ChannelModeKind classifyLiveModeKind(String by, String details) {
    String actor = Objects.toString(by, "").trim();
    if (!actor.isEmpty()) return IrcEvent.ChannelModeKind.DELTA;
    return looksLikeSnapshotModeDetails(details)
        ? IrcEvent.ChannelModeKind.SNAPSHOT
        : IrcEvent.ChannelModeKind.DELTA;
  }

  static boolean looksLikeSnapshotModeDetails(String details) {
    if (details == null) return false;
    String d = details.trim();
    if (d.isEmpty()) return false;

    int sp = d.indexOf(' ');
    String modeToken = (sp < 0) ? d : d.substring(0, sp);
    if (modeToken.isEmpty()) return false;

    boolean sawSign = false;
    boolean sawMode = false;
    boolean adding = true;
    for (int i = 0; i < modeToken.length(); i++) {
      char c = modeToken.charAt(i);
      if (c == '+') {
        sawSign = true;
        adding = true;
        continue;
      }
      if (c == '-') return false;
      if (!Character.isLetterOrDigit(c)) return false;
      if (isStatusOrListMode(c, adding)) return false;
      sawMode = true;
    }
    return sawSign && sawMode;
  }

  private static boolean isStatusOrListMode(char mode, boolean adding) {
    if (!adding) return true;
    return switch (mode) {
      case 'a', 'o', 'h', 'v', 'y', 'q', 'b', 'e', 'I' -> true;
      default -> false;
    };
  }
}
