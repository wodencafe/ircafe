package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.TargetChatHistoryPort;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.ZncPlaybackEventsPort;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Logging-side adapter that implements app-owned history ports and delegates to existing logging
 * services/buses.
 */
@Component
public class LoggingAppHistoryPortsAdapter
    implements ChatHistoryIngestionPort,
        ChatHistoryIngestEventsPort,
        ChatHistoryBatchEventsPort,
        ZncPlaybackEventsPort,
        TargetChatHistoryPort {

  private final ChatHistoryIngestor ingestor;
  private final ChatHistoryIngestBus ingestBus;
  private final ChatHistoryBatchBus batchBus;
  private final ZncPlaybackBus playbackBus;
  private final ChatHistoryService historyService;

  public LoggingAppHistoryPortsAdapter(
      ChatHistoryIngestor ingestor,
      ChatHistoryIngestBus ingestBus,
      ChatHistoryBatchBus batchBus,
      ZncPlaybackBus playbackBus,
      ChatHistoryService historyService) {
    this.ingestor = Objects.requireNonNull(ingestor, "ingestor");
    this.ingestBus = Objects.requireNonNull(ingestBus, "ingestBus");
    this.batchBus = Objects.requireNonNull(batchBus, "batchBus");
    this.playbackBus = Objects.requireNonNull(playbackBus, "playbackBus");
    this.historyService = Objects.requireNonNull(historyService, "historyService");
  }

  @Override
  public void ingestAsync(
      String serverId,
      String targetHint,
      String batchId,
      java.util.List<ChatHistoryEntry> entries,
      Consumer<IngestResult> callback) {
    ingestor.ingestAsync(
        serverId,
        targetHint,
        batchId,
        entries,
        result -> {
          if (callback == null) {
            return;
          }
          if (result == null) {
            callback.accept(null);
            return;
          }
          callback.accept(
              new IngestResult(
                  result.enabled(),
                  result.message(),
                  result.total(),
                  result.inserted(),
                  result.earliestInsertedEpochMs(),
                  result.latestInsertedEpochMs()));
        });
  }

  @Override
  public void publish(ChatHistoryIngestEventsPort.IngestEvent event) {
    if (event == null) return;
    ingestBus.publish(
        new ChatHistoryIngestBus.IngestEvent(
            event.serverId(),
            event.target(),
            event.batchId(),
            event.total(),
            event.inserted(),
            event.earliestTsEpochMs(),
            event.latestTsEpochMs()));
  }

  @Override
  public void publish(ChatHistoryBatchEventsPort.BatchEvent event) {
    if (event == null) return;
    batchBus.publish(
        new ChatHistoryBatchBus.BatchEvent(
            event.serverId(),
            event.target(),
            event.batchId(),
            event.entries(),
            event.earliestTsEpochMs(),
            event.latestTsEpochMs()));
  }

  @Override
  public void publish(ZncPlaybackEventsPort.PlaybackEvent event) {
    if (event == null) return;
    playbackBus.publish(
        new ZncPlaybackBus.PlaybackEvent(
            event.serverId(),
            event.target(),
            event.fromInclusive(),
            event.toInclusive(),
            event.entries(),
            event.earliestTsEpochMs(),
            event.latestTsEpochMs()));
  }

  @Override
  public void onTargetSelected(TargetRef target) {
    historyService.onTargetSelected(target);
  }

  @Override
  public void reset(TargetRef target) {
    historyService.reset(target);
  }
}
