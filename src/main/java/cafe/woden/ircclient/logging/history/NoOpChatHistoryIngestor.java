package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.util.List;
import java.util.function.Consumer;

final class NoOpChatHistoryIngestor implements ChatHistoryIngestor {
  @Override
  public void ingestAsync(String serverId, String targetHint, String batchId, List<ChatHistoryEntry> entries,
                          Consumer<ChatHistoryIngestResult> callback) {
    int total = entries == null ? 0 : entries.size();
    if (callback == null) return;
    callback.accept(new ChatHistoryIngestResult(
        false,
        total,
        0,
        total,
        0L,
        0L,
        "Chat logging is disabled; history batch not persisted."
    ));
  }
}
