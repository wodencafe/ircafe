package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned publishing contract for raw CHATHISTORY batches. */
@ApplicationLayer
public interface ChatHistoryBatchEventsPort {

  void publish(BatchEvent event);

  record BatchEvent(
      String serverId,
      String target,
      String batchId,
      List<ChatHistoryEntry> entries,
      long earliestTsEpochMs,
      long latestTsEpochMs) {

    public BatchEvent {
      entries = entries == null ? List.of() : List.copyOf(entries);
    }
  }
}
