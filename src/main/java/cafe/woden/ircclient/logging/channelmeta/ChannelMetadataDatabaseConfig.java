package cafe.woden.ircclient.logging.channelmeta;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@InfrastructureLayer
public class ChannelMetadataDatabaseConfig {

  private static final Logger log = LoggerFactory.getLogger(ChannelMetadataDatabaseConfig.class);
  private static final String DEFAULT_DB_BASE_NAME = "ircafe-channelmeta";

  @Bean(name = "channelMetadataDataSource", destroyMethod = "close")
  public DataSource channelMetadataDataSource(RuntimeConfigStore runtimeConfigStore) {
    Path basePath = resolveDbBasePath(runtimeConfigStore);
    Path lockPath = lockFilePath(basePath);
    String url = "jdbc:hsqldb:file:" + basePath.toAbsolutePath() + ";hsqldb.tx=mvcc";
    try {
      return new HikariDataSource(buildDataSourceConfig(url));
    } catch (RuntimeException e) {
      if (!isRecoverableLockFailure(e) || !tryRecoverStaleLockFile(lockPath)) {
        log.error("[ircafe] channel metadata DB lock could not be recovered at '{}'", lockPath, e);
        throw e;
      }
      log.warn(
          "[ircafe] removed stale channel metadata lock file '{}' and retrying once", lockPath);
      return new HikariDataSource(buildDataSourceConfig(url));
    }
  }

  @Bean(initMethod = "migrate", name = "channelMetadataFlyway")
  public Flyway channelMetadataFlyway(
      @Qualifier("channelMetadataDataSource") DataSource channelMetadataDataSource) {
    return Flyway.configure()
        .dataSource(channelMetadataDataSource)
        .locations("classpath:db/migration/channelmeta")
        .load();
  }

  @Bean(name = "channelMetadataJdbcTemplate")
  public JdbcTemplate channelMetadataJdbcTemplate(
      @Qualifier("channelMetadataDataSource") DataSource ds,
      @Qualifier("channelMetadataFlyway") Flyway flyway) {
    if (flyway == null) {
      throw new IllegalStateException(
          "channelMetadataFlyway bean missing (migrations must run before metadata store)");
    }
    return new JdbcTemplate(ds);
  }

  @Bean
  public ChannelMetadataRepository channelMetadataRepository(
      @Qualifier("channelMetadataJdbcTemplate") JdbcTemplate jdbc) {
    return new ChannelMetadataRepository(jdbc);
  }

  private static Path resolveDbBasePath(RuntimeConfigStore runtimeConfigStore) {
    Path runtimeCfg = runtimeConfigStore.runtimeConfigPath();
    Path parent = runtimeCfg != null ? runtimeCfg.getParent() : null;
    Path dir = (parent != null) ? parent : defaultIrcafeDir();
    try {
      Files.createDirectories(dir);
    } catch (Exception e) {
      log.warn(
          "[ircafe] could not create channel metadata DB dir '{}' (using current directory)",
          dir,
          e);
      dir = Paths.get(".").toAbsolutePath();
    }
    Path basePath = dir.resolve(DEFAULT_DB_BASE_NAME);
    log.info("[ircafe] channel metadata DB enabled (HSQLDB file: {})", basePath.toAbsolutePath());
    return basePath;
  }

  private static Path defaultIrcafeDir() {
    return Paths.get(System.getProperty("user.home"), ".config", "ircafe");
  }

  private static Path lockFilePath(Path basePath) {
    return Paths.get(basePath.toAbsolutePath() + ".lck");
  }

  private static HikariConfig buildDataSourceConfig(String url) {
    HikariConfig cfg = new HikariConfig();
    cfg.setPoolName("ircafe-channelmeta");
    cfg.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
    cfg.setJdbcUrl(url);
    cfg.setUsername("SA");
    cfg.setPassword("");
    cfg.setMaximumPoolSize(2);
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
      if (lock == null) return false;
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
}
