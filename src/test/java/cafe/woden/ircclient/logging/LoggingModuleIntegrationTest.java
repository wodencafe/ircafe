package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(properties = {"ircafe.logging.enabled=true"})
class LoggingModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  private final ChatLogWriter writer;
  private final ChatLogRepository repo;
  private final ChatLogMaintenance maintenance;
  private final LogLineFactory lineFactory;
  private final DataSource dataSource;

  LoggingModuleIntegrationTest(
      ChatLogWriter writer,
      ChatLogRepository repo,
      ChatLogMaintenance maintenance,
      LogLineFactory lineFactory,
      @Qualifier("chatLogDataSource") DataSource dataSource) {
    this.writer = writer;
    this.repo = repo;
    this.maintenance = maintenance;
    this.lineFactory = lineFactory;
    this.dataSource = dataSource;
  }

  @Test
  void wiresLoggingBeansWithEmbeddedHsqldb() {
    assertInstanceOf(ChatLogService.class, writer);
    assertInstanceOf(HikariDataSource.class, dataSource);

    String jdbcUrl = ((HikariDataSource) dataSource).getJdbcUrl();
    assertTrue(
        jdbcUrl.contains("build/tmp/modulith-tests"),
        "logging DB should be created under build/tmp for tests");
  }

  @Test
  void persistsAndClearsTargetRowsViaModuleBeans() {
    TargetRef target = new TargetRef("modulith", "#logging");

    repo.insert(
        lineFactory.chatAt(
            target,
            "alice",
            "hello from modulith",
            false,
            System.currentTimeMillis(),
            "module-msg-1",
            Map.of("msgid", "module-msg-1")));

    assertEquals(1, repo.fetchRecent("modulith", "#logging", 10).size());
    assertTrue(maintenance.enabled());

    maintenance.clearTarget(target);

    assertEquals(0, repo.fetchRecent("modulith", "#logging", 10).size());
  }
}
