package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.logging.history.ChatHistoryIngestBus;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.history.ChatHistoryTranscriptPort;
import cafe.woden.ircclient.logging.history.DbChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditService;
import cafe.woden.ircclient.logging.viewer.DbChatLogViewerService;
import cafe.woden.ircclient.model.TargetRef;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Embedded chat log DB wiring.
 *
 * <p>Only activates when {@code ircafe.logging.enabled=true}. Uses a file-based HSQLDB stored next
 * to the runtime config YAML by default. Flyway migrations are run on startup.
 */
@Configuration
@ConditionalOnProperty(prefix = "ircafe.logging", name = "enabled", havingValue = "true")
@InfrastructureLayer
public class ChatLogDatabaseConfig {

  private static final Logger log = LoggerFactory.getLogger(ChatLogDatabaseConfig.class);

  @Bean(name = "chatLogDataSource", destroyMethod = "close")
  public DataSource chatLogDataSource(
      LogProperties logProps, RuntimeConfigPathPort runtimeConfigPathPort) {
    Path basePath = resolveDbBasePath(logProps, runtimeConfigPathPort);
    Path lockPath = lockFilePath(basePath);

    // Keep the DB open for the life of the app by reusing pooled connections.
    // (Do NOT use ;shutdown=true here; it can race when connections close.)
    String url = "jdbc:hsqldb:file:" + basePath.toAbsolutePath() + ";hsqldb.tx=mvcc";

    log.info("[ircafe] Chat logging DB enabled (HSQLDB file: {})", basePath.toAbsolutePath());
    try {
      return new HikariDataSource(buildDataSourceConfig(url));
    } catch (RuntimeException e) {
      if (!isRecoverableLockFailure(e) || !tryRecoverStaleLockFile(lockPath)) {
        log.error(
            "[ircafe] Chat logging DB lock could not be recovered at '{}'. "
                + "If no IRCafe process is running, delete this file and restart.",
            lockPath,
            e);
        throw e;
      }
      log.warn(
          "[ircafe] Removed stale chat log DB lock file '{}' and retrying startup once", lockPath);
      return new HikariDataSource(buildDataSourceConfig(url));
    }
  }

  @Bean(initMethod = "migrate", name = "chatLogFlyway")
  public Flyway chatLogFlyway(@Qualifier("chatLogDataSource") DataSource chatLogDataSource) {
    return Flyway.configure()
        .dataSource(chatLogDataSource)
        .locations("classpath:db/migration/chatlog")
        .load();
  }

  @Bean(name = "chatLogJdbcTemplate")
  public JdbcTemplate chatLogJdbcTemplate(@Qualifier("chatLogDataSource") DataSource ds) {
    return new JdbcTemplate(ds);
  }

  @Bean(name = "chatLogTxManager")
  public PlatformTransactionManager chatLogTxManager(
      @Qualifier("chatLogDataSource") DataSource ds) {
    return new DataSourceTransactionManager(ds);
  }

  @Bean(name = "chatLogTx")
  public TransactionTemplate chatLogTx(
      @Qualifier("chatLogTxManager") PlatformTransactionManager tm) {
    return new TransactionTemplate(tm);
  }

  @Bean
  public ChatLogRepository chatLogRepository(@Qualifier("chatLogJdbcTemplate") JdbcTemplate jdbc) {
    return new ChatLogRepository(jdbc);
  }

  @Bean
  public ChatRedactionAuditRepository chatRedactionAuditRepository(
      @Qualifier("chatLogJdbcTemplate") JdbcTemplate jdbc) {
    return new ChatRedactionAuditRepository(jdbc);
  }

  @Bean
  public ChatRedactionAuditService chatRedactionAuditService(
      ChatRedactionAuditRepository repo, LogProperties props) {
    return new DbChatRedactionAuditService(repo, props);
  }

  @Bean
  public ChatLogViewerService chatLogViewerService(ChatLogRepository repo) {
    return new DbChatLogViewerService(repo);
  }

  @Bean(destroyMethod = "close")
  public ChatLogService chatLogService(
      ChatLogRepository repo,
      @Qualifier("chatLogTx") TransactionTemplate tx,
      LogProperties props,
      @Qualifier("chatLogFlyway") Flyway flyway) {
    // touch flyway to ensure init/migrate ran
    if (flyway == null) {
      throw new IllegalStateException(
          "chatLogFlyway bean missing (migrations must run before logging)");
    }
    return new ChatLogService(repo, tx, props);
  }

  @Bean(destroyMethod = "close")
  public ChatLogRetentionPruner chatLogRetentionPruner(
      ChatLogRepository repo,
      ChatRedactionAuditRepository redactionAuditRepository,
      @Qualifier("chatLogTx") TransactionTemplate tx,
      LogProperties props,
      @Qualifier("chatLogFlyway") Flyway flyway,
      @Qualifier(ExecutorConfig.CHAT_LOG_RETENTION_SCHEDULER)
          ScheduledExecutorService retentionScheduler) {
    return new ChatLogRetentionPruner(
        repo, redactionAuditRepository, tx, props, flyway, retentionScheduler);
  }

  @Bean(destroyMethod = "close")
  public ChatLogLegacyMessageIdRepair chatLogLegacyMessageIdRepair(
      ChatLogRepository repo,
      @Qualifier("chatLogTx") TransactionTemplate tx,
      @Qualifier("chatLogFlyway") Flyway flyway,
      @Qualifier(ExecutorConfig.CHAT_LOG_RETENTION_SCHEDULER)
          ScheduledExecutorService retentionScheduler) {
    return new ChatLogLegacyMessageIdRepair(repo, tx, flyway, retentionScheduler);
  }

  private static Path resolveDbBasePath(
      LogProperties props, RuntimeConfigPathPort runtimeConfigPathPort) {
    String baseName = props.hsqldb() != null ? props.hsqldb().fileBaseName() : "ircafe-chatlog";

    Path dir;
    if (props.hsqldb() != null && Boolean.TRUE.equals(props.hsqldb().nextToRuntimeConfig())) {
      Path runtimeCfg = runtimeConfigPathPort.runtimeConfigPath();
      Path parent = runtimeCfg != null ? runtimeCfg.getParent() : null;
      dir = parent != null ? parent : defaultIrcafeDir();
    } else {
      dir = defaultIrcafeDir();
    }

    try {
      Files.createDirectories(dir);
    } catch (Exception e) {
      log.warn(
          "[ircafe] Could not create chat log DB dir '{}' (falling back to current directory)",
          dir,
          e);
      dir = Paths.get(".").toAbsolutePath();
    }

    return dir.resolve(baseName);
  }

  private static Path defaultIrcafeDir() {
    return Paths.get(System.getProperty("user.home"), ".config", "ircafe");
  }

  private static HikariConfig buildDataSourceConfig(String url) {
    HikariConfig cfg = new HikariConfig();
    cfg.setPoolName("ircafe-chatlog");
    cfg.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
    cfg.setJdbcUrl(url);
    cfg.setUsername("SA");
    cfg.setPassword("");

    // Tiny pool is enough (writer + history reads), but prevents open/close thrash.
    cfg.setMaximumPoolSize(4);
    cfg.setMinimumIdle(1);
    cfg.setConnectionTimeout(5_000);
    cfg.setValidationTimeout(5_000);
    cfg.setIdleTimeout(60_000);
    return cfg;
  }

  static boolean isRecoverableLockFailure(Throwable throwable) {
    boolean sawLockFailure = false;
    boolean sawLockHeld = false;
    Throwable current = throwable;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        sawLockFailure |= message.contains("Database lock acquisition failure");
        sawLockHeld |= message.contains("LockHeldExternallyException");
      }
      sawLockHeld |= current.getClass().getName().contains("LockHeldExternallyException");
      current = current.getCause();
    }
    return sawLockFailure && sawLockHeld;
  }

  static boolean tryRecoverStaleLockFile(Path lockPath) {
    if (lockPath == null || !Files.exists(lockPath)) {
      return false;
    }

    try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE)) {
      FileLock lock;
      try {
        lock = channel.tryLock();
      } catch (OverlappingFileLockException ignored) {
        return false;
      }
      if (lock == null) {
        return false;
      }
      lock.close();
    } catch (Exception e) {
      return false;
    }

    try {
      return Files.deleteIfExists(lockPath);
    } catch (Exception e) {
      return false;
    }
  }

  private static Path lockFilePath(Path basePath) {
    return Paths.get(basePath.toAbsolutePath() + ".lck");
  }

  @Bean
  public ChatHistoryService chatHistoryService(
      ChatLogRepository repo,
      LogProperties props,
      ChatHistoryTranscriptPort transcripts,
      IrcClientService irc,
      @Qualifier("ircClientService") IrcBouncerPlaybackPort bouncerPlayback,
      ChatHistoryIngestBus ingestBus,
      @Qualifier(ExecutorConfig.DB_CHAT_HISTORY_EXECUTOR) ExecutorService chatHistoryExecutor) {
    return new DbChatHistoryService(
        repo, props, transcripts, irc, bouncerPlayback, ingestBus, chatHistoryExecutor);
  }

  @Bean
  public ChatLogMaintenance chatLogMaintenance(
      ChatLogRepository repo,
      @Qualifier("chatLogTx") TransactionTemplate tx,
      ChatLogService writer) {
    return new ChatLogMaintenance() {
      @Override
      public boolean enabled() {
        return true;
      }

      @Override
      public void clearTarget(TargetRef target) {
        if (target == null) return;
        try {
          // Best effort: flush queued writes so they can't reappear after the delete.
          if (writer != null) writer.flushNow();
        } catch (Exception ignored) {
        }

        tx.executeWithoutResult(
            status -> {
              repo.deleteTarget(target.serverId(), target.target());
            });
      }
    };
  }
}
