package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned publishing contract for ZNC playback ranges. */
@ApplicationLayer
public interface ZncPlaybackEventsPort {

  void publish(PlaybackEvent event);

  record PlaybackEvent(
      String serverId,
      String target,
      Instant fromInclusive,
      Instant toInclusive,
      List<ChatHistoryEntry> entries,
      long earliestTsEpochMs,
      long latestTsEpochMs) {

    public PlaybackEvent {
      entries = entries == null ? List.of() : List.copyOf(entries);
      fromInclusive = fromInclusive == null ? Instant.EPOCH : fromInclusive;
      toInclusive = toInclusive == null ? Instant.EPOCH : toInclusive;
    }
  }
}
