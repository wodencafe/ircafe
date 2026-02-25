package cafe.woden.ircclient.app;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.util.List;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned contract for persisting remote history batches. */
@ApplicationLayer
public interface ChatHistoryIngestionPort {

  void ingestAsync(
      String serverId,
      String targetHint,
      String batchId,
      List<ChatHistoryEntry> entries,
      Consumer<IngestResult> callback);

  record IngestResult(
      boolean enabled,
      String message,
      int total,
      int inserted,
      long earliestInsertedEpochMs,
      long latestInsertedEpochMs) {}
}
