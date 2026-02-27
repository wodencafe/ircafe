package cafe.woden.ircclient.ignore.api;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Command/mutation operations for ignore list state. */
@ApplicationLayer
public interface IgnoreListCommandPort {

  boolean addMask(String serverId, String rawMaskOrNick);

  IgnoreAddMaskResult addMaskWithLevels(
      String serverId,
      String rawMaskOrNick,
      List<String> levels,
      List<String> channels,
      Long expiresAtEpochMs,
      String textPattern,
      IgnoreTextPatternMode textPatternMode,
      boolean includeReplies);

  boolean addSoftMask(String serverId, String rawMaskOrNick);

  boolean removeMask(String serverId, String rawMaskOrNick);

  boolean removeSoftMask(String serverId, String rawMaskOrNick);

  int pruneExpiredHardMasks(String serverId, long nowEpochMs);
}
