package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.util.List;
import java.util.function.Consumer;

/**
 * Persists collected remote history (IRCv3 CHATHISTORY / bouncer playback) into the local chat log DB.
 *
 * <p>When chat logging is disabled, a no-op implementation is used.
 */
public interface ChatHistoryIngestor {

  /**
   * Persist the given history entries asynchronously.
   *
   * @param serverId server/network id
   * @param targetHint best-effort target name associated with the batch
   * @param batchId raw IRCv3 BATCH id (if known)
   * @param entries collected history entries
   * @param callback invoked once ingestion completes (may be called on a background thread)
   */
  void ingestAsync(
      String serverId,
      String targetHint,
      String batchId,
      List<ChatHistoryEntry> entries,
      Consumer<ChatHistoryIngestResult> callback
  );
}
