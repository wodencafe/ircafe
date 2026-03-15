package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned publishing contract for CHATHISTORY ingest completions. */
@SecondaryPort
@ApplicationLayer
public interface ChatHistoryIngestEventsPort {

  void publish(IngestEvent event);

  record IngestEvent(
      String serverId,
      String target,
      String batchId,
      int total,
      int inserted,
      long earliestTsEpochMs,
      long latestTsEpochMs) {}
}
