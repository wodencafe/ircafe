package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** In-memory bus that carries the raw CHATHISTORY batch entries. */
@Component
public final class ChatHistoryBatchBus extends AbstractTargetWaiterBus<ChatHistoryBatchBus.BatchEvent> {

  private static final Logger log = LoggerFactory.getLogger(ChatHistoryBatchBus.class);

  public record BatchEvent(
      String serverId,
      String target,
      String batchId,
      List<ChatHistoryEntry> entries,
      long earliestTsEpochMs,
      long latestTsEpochMs
  ) {
    public BatchEvent {
      entries = entries == null ? List.of() : List.copyOf(entries);
    }
  }

  public ChatHistoryBatchBus(
      @Qualifier(ExecutorConfig.CHATHISTORY_BATCH_BUS_SCHEDULER) ScheduledExecutorService scheduler
  ) {
    super(
        scheduler,
        "CHATHISTORY batch",
        "CHATHISTORY batch bus completion failed",
        log
    );
  }

  @Override
  protected String serverIdOf(BatchEvent event) {
    return event.serverId();
  }

  @Override
  protected String targetOf(BatchEvent event) {
    return event.target();
  }
}
