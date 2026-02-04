package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.app.TargetRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
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
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.history.DbChatHistoryService;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;

/**
 * Embedded chat log DB wiring.
 *
 * <p>Only activates when {@code ircafe.logging.enabled=true}.
 * Uses a file-based HSQLDB stored next to the runtime config YAML by default.
 * Flyway migrations are run on startup.
 */
@Configuration
@ConditionalOnProperty(prefix = "ircafe.logging", name = "enabled", havingValue = "true")
public class ChatLogDatabaseConfig {

  private static final Logger log = LoggerFactory.getLogger(ChatLogDatabaseConfig.class);

  
  @Bean(name = "chatLogDataSource", destroyMethod = "close")
  public DataSource chatLogDataSource(LogProperties logProps, RuntimeConfigStore runtimeConfigStore) {
    Path basePath = resolveDbBasePath(logProps, runtimeConfigStore);

    // Keep the DB open for the life of the app by reusing pooled connections.
    // (Do NOT use ;shutdown=true here; it can race when connections close.)
    String url = "jdbc:hsqldb:file:" + basePath.toAbsolutePath() + ";hsqldb.tx=mvcc";

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

    log.info("[ircafe] Chat logging DB enabled (HSQLDB file: {})", basePath.toAbsolutePath());
    return new HikariDataSource(cfg);
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
  public PlatformTransactionManager chatLogTxManager(@Qualifier("chatLogDataSource") DataSource ds) {
    return new DataSourceTransactionManager(ds);
  }

  @Bean(name = "chatLogTx")
  public TransactionTemplate chatLogTx(@Qualifier("chatLogTxManager") PlatformTransactionManager tm) {
    return new TransactionTemplate(tm);
  }

  @Bean
  public ChatLogRepository chatLogRepository(@Qualifier("chatLogJdbcTemplate") JdbcTemplate jdbc) {
    return new ChatLogRepository(jdbc);
  }

  @Bean(destroyMethod = "close")
  public ChatLogService chatLogService(
      ChatLogRepository repo,
      @Qualifier("chatLogTx") TransactionTemplate tx,
      LogProperties props,
      @Qualifier("chatLogFlyway") Flyway flyway
  ) {
    // touch flyway to ensure init/migrate ran
    if (flyway == null) {
      throw new IllegalStateException("chatLogFlyway bean missing (migrations must run before logging)");
    }
    return new ChatLogService(repo, tx, props);
  }

  private static Path resolveDbBasePath(LogProperties props, RuntimeConfigStore runtimeConfigStore) {
    String baseName = props.hsqldb() != null ? props.hsqldb().fileBaseName() : "ircafe-chatlog";

    Path dir;
    if (props.hsqldb() != null && Boolean.TRUE.equals(props.hsqldb().nextToRuntimeConfig())) {
      Path runtimeCfg = runtimeConfigStore.runtimeConfigPath();
      Path parent = runtimeCfg != null ? runtimeCfg.getParent() : null;
      dir = parent != null ? parent : defaultIrcafeDir();
    } else {
      dir = defaultIrcafeDir();
    }

    try {
      Files.createDirectories(dir);
    } catch (Exception e) {
      log.warn("[ircafe] Could not create chat log DB dir '{}' (falling back to current directory)", dir, e);
      dir = Paths.get(".").toAbsolutePath();
    }

    return dir.resolve(baseName);
  }

  private static Path defaultIrcafeDir() {
    return Paths.get(System.getProperty("user.home"), ".config", "ircafe");
  }


  @Bean
  public ChatHistoryService chatHistoryService(ChatLogRepository repo,
                                              LogProperties props,
                                              ChatTranscriptStore transcripts,
                                              UiSettingsBus settingsBus) {
    return new DbChatHistoryService(repo, props, transcripts, settingsBus);
  }

  @Bean
  public ChatLogMaintenance chatLogMaintenance(ChatLogRepository repo,
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

        tx.executeWithoutResult(status -> {
          repo.deleteTarget(target.serverId(), target.target());
        });
      }
    };
  }

}