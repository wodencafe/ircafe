package cafe.woden.ircclient.app;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.logging.history.ChatHistoryBatchBus;
import cafe.woden.ircclient.logging.history.ChatHistoryIngestBus;
import cafe.woden.ircclient.logging.history.ChatHistoryIngestor;
import cafe.woden.ircclient.logging.history.ZncPlaybackBus;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Orchestrates history batch persistence side effects extracted from {@link IrcMediator}.
 */
@Component
public class MediatorHistoryIngestOrchestrator {

  private final UiPort ui;
  private final ChatHistoryIngestor chatHistoryIngestor;
  private final ChatHistoryIngestBus chatHistoryIngestBus;
  private final ChatHistoryBatchBus chatHistoryBatchBus;
  private final ZncPlaybackBus zncPlaybackBus;

  public MediatorHistoryIngestOrchestrator(
      UiPort ui,
      ChatHistoryIngestor chatHistoryIngestor,
      ChatHistoryIngestBus chatHistoryIngestBus,
      ChatHistoryBatchBus chatHistoryBatchBus,
      ZncPlaybackBus zncPlaybackBus
  ) {
    this.ui = ui;
    this.chatHistoryIngestor = chatHistoryIngestor;
    this.chatHistoryIngestBus = chatHistoryIngestBus;
    this.chatHistoryBatchBus = chatHistoryBatchBus;
    this.zncPlaybackBus = zncPlaybackBus;
  }

  public void onChatHistoryBatchReceived(String sid, IrcEvent.ChatHistoryBatchReceived ev) {
    String target = normalizeTarget(ev.target());
    final TargetRef dest = new TargetRef(sid, target);
    ensureTargetExists(dest);

    try {
      long earliest = earliestEpochMs(ev.entries());
      long latest = latestEpochMs(ev.entries());
      if (chatHistoryBatchBus != null) {
        chatHistoryBatchBus.publish(new ChatHistoryBatchBus.BatchEvent(
            sid,
            target,
            ev.batchId(),
            ev.entries(),
            earliest,
            latest
        ));
      }
    } catch (Exception ignored) {
    }

    int n = (ev.entries() == null) ? 0 : ev.entries().size();
    ui.appendStatus(dest, "(chathistory)", "Received " + n + " history lines (batch " + ev.batchId() + "). Persisting… (still not displayed)");

    chatHistoryIngestor.ingestAsync(sid, target, ev.batchId(), ev.entries(), result -> {
      if (result == null) {
        appendStatus(dest, "(chathistory)", "Persist finished (no details).");
        return;
      }

      String msg;
      if (!result.enabled()) {
        msg = "History batch not persisted: chat logging is disabled.";
      } else if (result.message() != null) {
        msg = result.message();
      } else {
        msg = "Persisted " + result.inserted() + "/" + result.total() + " history lines.";
      }
      appendStatus(dest, "(chathistory)", msg);

      try {
        if (chatHistoryIngestBus != null) {
          chatHistoryIngestBus.publish(new ChatHistoryIngestBus.IngestEvent(
              sid,
              target,
              ev.batchId(),
              result.total(),
              result.inserted(),
              result.earliestInsertedEpochMs(),
              result.latestInsertedEpochMs()
          ));
        }
      } catch (Exception ignored) {
      }
    });
  }

  public void onZncPlaybackBatchReceived(String sid, IrcEvent.ZncPlaybackBatchReceived ev) {
    String target = normalizeTarget(ev.target());
    final TargetRef dest = new TargetRef(sid, target);
    ensureTargetExists(dest);

    // Deterministic batch id so the DB-backed history service can wait for ingest.
    final String batchId = "znc-playback:"
        + (ev.fromInclusive() == null ? 0L : ev.fromInclusive().toEpochMilli())
        + "-"
        + (ev.toInclusive() == null ? 0L : ev.toInclusive().toEpochMilli());

    try {
      long earliest = earliestEpochMs(ev.entries());
      long latest = latestEpochMs(ev.entries());
      if (zncPlaybackBus != null) {
        zncPlaybackBus.publish(new ZncPlaybackBus.PlaybackEvent(
            sid,
            target,
            ev.fromInclusive(),
            ev.toInclusive(),
            ev.entries(),
            earliest,
            latest
        ));
      }
    } catch (Exception ignored) {
    }

    int n = (ev.entries() == null) ? 0 : ev.entries().size();
    ui.appendStatus(dest, "(znc-playback)", "Captured " + n + " playback lines for scrollback. Persisting… (still not displayed)");

    chatHistoryIngestor.ingestAsync(sid, target, batchId, ev.entries(), result -> {
      if (result == null) {
        appendStatus(dest, "(znc-playback)", "Persist finished (no details).");
        return;
      }

      String msg;
      if (!result.enabled()) {
        msg = "Playback batch not persisted: chat logging is disabled.";
      } else if (result.message() != null) {
        msg = result.message();
      } else {
        msg = "Persisted " + result.inserted() + "/" + result.total() + " playback lines.";
      }
      appendStatus(dest, "(znc-playback)", msg);

      try {
        if (chatHistoryIngestBus != null) {
          chatHistoryIngestBus.publish(new ChatHistoryIngestBus.IngestEvent(
              sid,
              target,
              batchId,
              result.total(),
              result.inserted(),
              result.earliestInsertedEpochMs(),
              result.latestInsertedEpochMs()
          ));
        }
      } catch (Exception ignored) {
      }
    });
  }

  private static String normalizeTarget(String target) {
    if (target == null || target.isBlank()) return "status";
    return target;
  }

  private void appendStatus(TargetRef dest, String tag, String msg) {
    ensureTargetExists(dest);
    ui.appendStatus(dest, tag, msg);
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private static long earliestEpochMs(List<ChatHistoryEntry> entries) {
    if (entries == null || entries.isEmpty()) return 0L;
    return entries.stream().mapToLong(entry -> entry.at().toEpochMilli()).min().orElse(0L);
  }

  private static long latestEpochMs(List<ChatHistoryEntry> entries) {
    if (entries == null || entries.isEmpty()) return 0L;
    return entries.stream().mapToLong(entry -> entry.at().toEpochMilli()).max().orElse(0L);
  }
}
