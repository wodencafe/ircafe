package cafe.woden.ircclient.ignore.api;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Read/query operations for ignore list state. */
@ApplicationLayer
public interface IgnoreListQueryPort {

  List<String> listMasks(String serverId);

  List<String> listSoftMasks(String serverId);

  List<String> levelsForHardMask(String serverId, String mask);

  List<String> channelsForHardMask(String serverId, String mask);

  long expiresAtEpochMsForHardMask(String serverId, String mask);

  String patternForHardMask(String serverId, String mask);

  IgnoreTextPatternMode patternModeForHardMask(String serverId, String mask);

  boolean repliesForHardMask(String serverId, String mask);
}
