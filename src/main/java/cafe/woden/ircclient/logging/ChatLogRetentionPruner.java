package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.config.LogProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Periodically prunes old chat log rows based on {@code ircafe.logging.retentionDays}.
 *
 * <p>Retention is disabled when:
 *
 * <ul>
 *   <li>logging is disabled
 *   <li>{@code keepForever} is true
 *   <li>{@code retentionDays <= 0}
 * </ul>
 *
 * <p>This is intentionally conservative:
 *
 * <ul>
 *   <li>runs once shortly after startup
 *   <li>then runs on a fixed schedule (every 12 hours)
 * </ul>
 */
public final class ChatLogRetentionPruner implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(ChatLogRetentionPruner.class);

  private static final long RUN_EVERY_HOURS = 12;

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private final ChatLogRepository repo;
  private final TransactionTemplate tx;
  private final LogProperties props;
  private final Flyway flyway;

  private final ScheduledFuture<?> startupTask;
  private final ScheduledFuture<?> recurringTask;

  public ChatLogRetentionPruner(
      ChatLogRepository repo,
      TransactionTemplate tx,
      LogProperties props,
      Flyway flyway,
      ScheduledExecutorService exec) {
    this.repo = Objects.requireNonNull(repo, "repo");
    this.tx = Objects.requireNonNull(tx, "tx");
    this.props = Objects.requireNonNull(props, "props");
    this.flyway = Objects.requireNonNull(flyway, "flyway");

    // Run once shortly after startup, then periodically.
    this.startupTask = exec.schedule(this::pruneSafely, 10, TimeUnit.SECONDS);
    this.recurringTask =
        exec.scheduleWithFixedDelay(
            this::pruneSafely, RUN_EVERY_HOURS, RUN_EVERY_HOURS, TimeUnit.HOURS);
  }

  private boolean retentionEnabled() {
    if (!Boolean.TRUE.equals(props.enabled())) return false;
    if (Boolean.TRUE.equals(props.keepForever())) return false;
    Integer days = props.retentionDays();
    return days != null && days > 0;
  }

  private void pruneSafely() {
    try {
      pruneOnce();
    } catch (Throwable t) {
      log.warn("[ircafe] Chat log retention prune failed", t);
    }
  }

  private void pruneOnce() {
    if (!retentionEnabled()) return;

    // Ensure migrations have run before we touch the DB.
    flyway.info();

    int days = Math.max(0, Objects.requireNonNullElse(props.retentionDays(), 0));
    long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);

    Integer deleted = tx.execute(status -> repo.deleteOlderThan(cutoff));
    int n = deleted == null ? 0 : deleted;

    if (n > 0) {
      log.info(
          "[ircafe] Pruned {} chat log rows older than {} (retentionDays={})",
          n,
          TS_FMT.format(Instant.ofEpochMilli(cutoff)),
          days);
    } else {
      log.debug(
          "[ircafe] Chat log retention prune: nothing to delete (retentionDays={}, cutoff={})",
          days,
          TS_FMT.format(Instant.ofEpochMilli(cutoff)));
    }
  }

  @Override
  public void close() {
    try {
      startupTask.cancel(false);
    } catch (Exception ignored) {
    }
    try {
      recurringTask.cancel(false);
    } catch (Exception ignored) {
    }
  }
}
