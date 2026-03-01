package cafe.woden.ircclient.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-shot startup maintenance for legacy rows created before persisted message-id dedup existed.
 *
 * <p>Repairs old rows by backfilling {@code message_id} from metadata and deleting duplicate rows
 * that collide on the persisted unique key.
 */
public final class ChatLogLegacyMessageIdRepair implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(ChatLogLegacyMessageIdRepair.class);

  private static final int BATCH_SIZE = 1000;
  private static final long STARTUP_DELAY_SECONDS = 3L;

  private final ChatLogRepository repo;
  private final TransactionTemplate tx;
  private final Flyway flyway;
  private final ScheduledFuture<?> startupTask;

  public ChatLogLegacyMessageIdRepair(
      ChatLogRepository repo,
      TransactionTemplate tx,
      Flyway flyway,
      ScheduledExecutorService scheduler) {
    this.repo = Objects.requireNonNull(repo, "repo");
    this.tx = Objects.requireNonNull(tx, "tx");
    this.flyway = Objects.requireNonNull(flyway, "flyway");
    Objects.requireNonNull(scheduler, "scheduler");
    this.startupTask =
        scheduler.schedule(this::repairSafely, STARTUP_DELAY_SECONDS, TimeUnit.SECONDS);
  }

  private void repairSafely() {
    try {
      repairOnce();
    } catch (Throwable t) {
      log.warn("[ircafe] Legacy chat-log message-id repair failed", t);
    }
  }

  private void repairOnce() {
    // Ensure migrations are visible before touching message_id columns.
    flyway.info();

    OptionalLong maxRowIdOpt = repo.maxRowId();
    long maxRowId = maxRowIdOpt.isPresent() ? maxRowIdOpt.getAsLong() : -1L;
    if (maxRowId < 0L) return;

    long startedNanos = System.nanoTime();
    long afterId = -1L;
    int scanned = 0;
    int skippedMissingMessageId = 0;
    int updated = 0;
    int deletedDuplicates = 0;

    int batches = 0;

    while (afterId < maxRowId) {
      List<ChatLogRepository.LegacyMessageIdRow> batch =
          repo.fetchLegacyRowsWithoutMessageIdAfter(afterId, BATCH_SIZE);
      if (batch == null || batch.isEmpty()) break;

      ArrayList<ChatLogRepository.LegacyMessageIdRow> bounded = new ArrayList<>(batch.size());
      for (ChatLogRepository.LegacyMessageIdRow row : batch) {
        if (row == null) continue;
        if (row.id() > maxRowId) break;
        bounded.add(row);
      }
      if (bounded.isEmpty()) break;

      batches++;
      scanned += bounded.size();
      afterId = Math.max(afterId, bounded.get(bounded.size() - 1).id());

      BatchStats stats = tx.execute(status -> processBatch(bounded));
      if (stats != null) {
        skippedMissingMessageId += stats.skippedMissingMessageId();
        updated += stats.updated();
        deletedDuplicates += stats.deletedDuplicates();
      }
    }

    Integer deletedExact =
        tx.execute(status -> repo.deleteExactDuplicatesWithoutMessageIdUpTo(maxRowId));
    int deletedExactNullMessageIdDuplicates = deletedExact == null ? 0 : Math.max(0, deletedExact);

    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    if (updated > 0 || deletedDuplicates > 0 || deletedExactNullMessageIdDuplicates > 0) {
      log.info(
          "[ircafe] Legacy message-id repair completed (scanned={}, updated={}, deletedDuplicates={}, deletedExactNullMessageIdDuplicates={}, skippedMissingMessageId={}, batches={}, tookMs={})",
          scanned,
          updated,
          deletedDuplicates,
          deletedExactNullMessageIdDuplicates,
          skippedMissingMessageId,
          batches,
          durationMs);
    } else {
      log.debug(
          "[ircafe] Legacy message-id repair found nothing to change (scanned={}, skippedMissingMessageId={}, batches={}, tookMs={})",
          scanned,
          skippedMissingMessageId,
          batches,
          durationMs);
    }
  }

  private BatchStats processBatch(List<ChatLogRepository.LegacyMessageIdRow> batch) {
    int skippedMissingMessageId = 0;
    int updated = 0;
    int deletedDuplicates = 0;

    for (ChatLogRepository.LegacyMessageIdRow row : batch) {
      if (row == null) continue;
      String messageId = ChatLogRepository.extractMessageId(row.metaJson());
      if (messageId.isBlank()) {
        skippedMissingMessageId++;
        continue;
      }

      ChatLogRepository.LegacyMessageIdRepairOutcome outcome =
          repo.backfillMessageIdOrDeleteDuplicate(row, messageId);
      if (outcome == ChatLogRepository.LegacyMessageIdRepairOutcome.UPDATED) {
        updated++;
      } else if (outcome == ChatLogRepository.LegacyMessageIdRepairOutcome.DELETED_DUPLICATE) {
        deletedDuplicates++;
      }
    }

    return new BatchStats(skippedMissingMessageId, updated, deletedDuplicates);
  }

  @Override
  public void close() {
    try {
      startupTask.cancel(false);
    } catch (Exception ignored) {
    }
  }

  private record BatchStats(int skippedMissingMessageId, int updated, int deletedDuplicates) {}
}
