package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.config.ExecutorConfig;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** In-memory bus for coordinating CHATHISTORY requests with DB ingest. */
@Component
public final class ChatHistoryIngestBus extends AbstractTargetWaiterBus<ChatHistoryIngestBus.IngestEvent> {

  private static final Logger log = LoggerFactory.getLogger(ChatHistoryIngestBus.class);

  public record IngestEvent(
      String serverId,
      String target,
      String batchId,
      int total,
      int inserted,
      long earliestTsEpochMs,
      long latestTsEpochMs
  ) {}

  public ChatHistoryIngestBus(
      @Qualifier(ExecutorConfig.CHATHISTORY_INGEST_BUS_SCHEDULER) ScheduledExecutorService scheduler
  ) {
    super(
        scheduler,
        "CHATHISTORY ingest",
        "CHATHISTORY ingest bus completion failed",
        log
    );
  }

  @Override
  protected String serverIdOf(IngestEvent event) {
    return event.serverId();
  }

  @Override
  protected String targetOf(IngestEvent event) {
    return event.target();
  }
}
