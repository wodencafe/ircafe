package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.logging.history.ChatHistoryTranscriptPort;
import cafe.woden.ircclient.logging.history.LoggingAppHistoryPortsAdapter;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.OptionalLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(properties = {"ircafe.logging.enabled=true"})
class LoggingModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final ChatLogWriter writer;
  private final ChatLogRepository repo;
  private final ChatLogMaintenance maintenance;
  private final LogLineFactory lineFactory;
  private final DataSource dataSource;
  private final TargetChatHistoryPort targetChatHistoryPort;
  private final TargetLogMaintenancePort targetLogMaintenancePort;
  private final ChatHistoryIngestionPort chatHistoryIngestionPort;
  private final ChatHistoryIngestEventsPort chatHistoryIngestEventsPort;
  private final ChatHistoryBatchEventsPort chatHistoryBatchEventsPort;
  private final ZncPlaybackEventsPort zncPlaybackEventsPort;
  private final ChatHistoryTranscriptPort chatHistoryTranscriptPort;

  LoggingModuleIntegrationTest(
      ApplicationContext applicationContext,
      ChatLogWriter writer,
      ChatLogRepository repo,
      ChatLogMaintenance maintenance,
      LogLineFactory lineFactory,
      @Qualifier("chatLogDataSource") DataSource dataSource,
      TargetChatHistoryPort targetChatHistoryPort,
      TargetLogMaintenancePort targetLogMaintenancePort,
      ChatHistoryIngestionPort chatHistoryIngestionPort,
      ChatHistoryIngestEventsPort chatHistoryIngestEventsPort,
      ChatHistoryBatchEventsPort chatHistoryBatchEventsPort,
      ZncPlaybackEventsPort zncPlaybackEventsPort,
      ChatHistoryTranscriptPort chatHistoryTranscriptPort) {
    this.applicationContext = applicationContext;
    this.writer = writer;
    this.repo = repo;
    this.maintenance = maintenance;
    this.lineFactory = lineFactory;
    this.dataSource = dataSource;
    this.targetChatHistoryPort = targetChatHistoryPort;
    this.targetLogMaintenancePort = targetLogMaintenancePort;
    this.chatHistoryIngestionPort = chatHistoryIngestionPort;
    this.chatHistoryIngestEventsPort = chatHistoryIngestEventsPort;
    this.chatHistoryBatchEventsPort = chatHistoryBatchEventsPort;
    this.zncPlaybackEventsPort = zncPlaybackEventsPort;
    this.chatHistoryTranscriptPort = chatHistoryTranscriptPort;
  }

  @BeforeEach
  void clearHistoryTranscriptInteractions() {
    clearInvocations(chatHistoryTranscriptPort);
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
  void exposesLoggingAppPortsThroughAdapters() {
    assertEquals(1, applicationContext.getBeansOfType(TargetLogMaintenancePort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(TargetChatHistoryPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(ChatHistoryIngestionPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(ChatHistoryIngestEventsPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(ChatHistoryBatchEventsPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(ZncPlaybackEventsPort.class).size());

    assertNotNull(targetLogMaintenancePort);
    assertNotNull(targetChatHistoryPort);
    assertEquals(
        LoggingTargetLogMaintenancePortAdapter.class,
        AopUtils.getTargetClass(targetLogMaintenancePort));
    assertEquals(
        LoggingAppHistoryPortsAdapter.class, AopUtils.getTargetClass(targetChatHistoryPort));
    assertSame(targetChatHistoryPort, chatHistoryIngestionPort);
    assertSame(targetChatHistoryPort, chatHistoryIngestEventsPort);
    assertSame(targetChatHistoryPort, chatHistoryBatchEventsPort);
    assertSame(targetChatHistoryPort, zncPlaybackEventsPort);
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

  @Test
  void targetLogMaintenancePortAdapterClearsPersistedRows() {
    TargetRef target = new TargetRef("modulith", "#logging-port-clear");

    repo.insert(
        lineFactory.chatAt(
            target,
            "alice",
            "adapter clear check",
            false,
            System.currentTimeMillis(),
            "module-msg-port-clear",
            Map.of("msgid", "module-msg-port-clear")));

    assertEquals(1, repo.fetchRecent("modulith", "#logging-port-clear", 10).size());
    targetLogMaintenancePort.clearTarget(target);
    assertEquals(0, repo.fetchRecent("modulith", "#logging-port-clear", 10).size());
  }

  @Test
  void mixedCaseHistoryCanReplayAgainAfterResetViaTargetHistoryPort() {
    TargetRef storedTarget = new TargetRef("modulith", "#ChanCase");
    TargetRef selectedTarget = new TargetRef("modulith", "#chancase");
    long tsEpochMs = 1_770_000_000_000L;

    repo.insert(
        lineFactory.chatAt(
            storedTarget,
            "alice",
            "history replay check",
            false,
            tsEpochMs,
            "history-replay-1",
            Map.of("msgid", "history-replay-1")));

    when(chatHistoryTranscriptPort.chatHistoryInitialLoadLines()).thenReturn(100);
    when(chatHistoryTranscriptPort.earliestTimestampEpochMs(selectedTarget))
        .thenReturn(OptionalLong.empty());

    targetChatHistoryPort.onTargetSelected(selectedTarget);

    verify(chatHistoryTranscriptPort, timeout(3_000).atLeastOnce())
        .insertChatFromHistoryAt(
            eq(selectedTarget),
            anyInt(),
            eq("alice"),
            eq("history replay check"),
            eq(false),
            eq(tsEpochMs));
    verify(chatHistoryTranscriptPort, timeout(3_000).atLeastOnce())
        .endHistoryInsertBatch(selectedTarget);

    clearInvocations(chatHistoryTranscriptPort);
    when(chatHistoryTranscriptPort.chatHistoryInitialLoadLines()).thenReturn(100);
    when(chatHistoryTranscriptPort.earliestTimestampEpochMs(selectedTarget))
        .thenReturn(OptionalLong.empty());

    targetChatHistoryPort.reset(selectedTarget);
    targetChatHistoryPort.onTargetSelected(selectedTarget);

    verify(chatHistoryTranscriptPort, timeout(3_000).atLeastOnce())
        .insertChatFromHistoryAt(
            eq(selectedTarget),
            anyInt(),
            eq("alice"),
            eq("history replay check"),
            eq(false),
            eq(tsEpochMs));
  }

  @Test
  void mixedCasePrivateHistoryCanReplayAgainAfterResetViaTargetHistoryPort() {
    TargetRef storedTarget = new TargetRef("modulith", "Alice");
    TargetRef selectedTarget = new TargetRef("modulith", "alice");
    long tsEpochMs = 1_770_000_000_100L;

    repo.insert(
        lineFactory.chatAt(
            storedTarget,
            "Alice",
            "pm history replay check",
            false,
            tsEpochMs,
            "pm-history-replay-1",
            Map.of("msgid", "pm-history-replay-1")));

    when(chatHistoryTranscriptPort.chatHistoryInitialLoadLines()).thenReturn(100);
    when(chatHistoryTranscriptPort.earliestTimestampEpochMs(selectedTarget))
        .thenReturn(OptionalLong.empty());

    targetChatHistoryPort.onTargetSelected(selectedTarget);

    verify(chatHistoryTranscriptPort, timeout(3_000).atLeastOnce())
        .insertChatFromHistoryAt(
            eq(selectedTarget),
            anyInt(),
            eq("Alice"),
            eq("pm history replay check"),
            eq(false),
            eq(tsEpochMs));
    verify(chatHistoryTranscriptPort, timeout(3_000).atLeastOnce())
        .endHistoryInsertBatch(selectedTarget);

    clearInvocations(chatHistoryTranscriptPort);
    when(chatHistoryTranscriptPort.chatHistoryInitialLoadLines()).thenReturn(100);
    when(chatHistoryTranscriptPort.earliestTimestampEpochMs(selectedTarget))
        .thenReturn(OptionalLong.empty());

    targetChatHistoryPort.reset(selectedTarget);
    targetChatHistoryPort.onTargetSelected(selectedTarget);

    verify(chatHistoryTranscriptPort, timeout(3_000).atLeastOnce())
        .insertChatFromHistoryAt(
            eq(selectedTarget),
            anyInt(),
            eq("Alice"),
            eq("pm history replay check"),
            eq(false),
            eq(tsEpochMs));
  }
}
