package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.model.LogLine;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ChatLogRepositoryTest {

  @TempDir Path tempDir;

  @Test
  void duplicateMessageIdIsSuppressedAcrossRepositoryReopen() {
    Path base = tempDir.resolve("chatlog-reopen");

    try (Fixture first = openFixture(base)) {
      TargetRef target = new TargetRef("srv", "#chan");
      LogLineFactory factory = new LogLineFactory(fixedClock(1_700_000_000_000L));

      first.repo.insert(
          factory.chatAt(
              target,
              "alice",
              "first",
              false,
              1_700_000_000_000L,
              "dup-1",
              Map.of("msgid", "dup-1")));
      assertEquals(1, first.repo.fetchRecent("srv", "#chan", 10).size());
    }

    try (Fixture second = openFixture(base)) {
      TargetRef target = new TargetRef("srv", "#chan");
      LogLineFactory factory = new LogLineFactory(fixedClock(1_700_000_100_000L));

      second.repo.insert(
          factory.chatAt(
              target,
              "alice",
              "second",
              false,
              1_700_000_100_000L,
              "dup-1",
              Map.of("msgid", "dup-1")));
      List<LogLine> rows = second.repo.fetchRecent("srv", "#chan", 10);
      assertEquals(1, rows.size());
      assertEquals("first", rows.getFirst().text());
    }
  }

  @Test
  void batchInsertSkipsDuplicateMessageIdButKeepsUniqueRows() {
    try (Fixture fixture = openFixture(tempDir.resolve("chatlog-batch"))) {
      TargetRef target = new TargetRef("srv", "#chan");
      LogLineFactory factory = new LogLineFactory(fixedClock(1_700_001_000_000L));

      LogLine a =
          factory.chatAt(
              target, "alice", "a", false, 1_700_001_000_000L, "dup-2", Map.of("msgid", "dup-2"));
      LogLine b =
          factory.chatAt(
              target, "alice", "b", false, 1_700_001_001_000L, "dup-2", Map.of("msgid", "dup-2"));
      LogLine c =
          factory.chatAt(
              target,
              "alice",
              "c",
              false,
              1_700_001_002_000L,
              "unique-2",
              Map.of("msgid", "unique-2"));

      fixture.repo.insertBatch(List.of(a, b, c));

      List<LogLine> rows = fixture.repo.fetchRecent("srv", "#chan", 10);
      assertEquals(2, rows.size());
      assertEquals("c", rows.getFirst().text());
      assertEquals("a", rows.get(1).text());
    }
  }

  @Test
  void rowsWithoutMessageIdAreNotDeduplicatedByPersistentKey() {
    try (Fixture fixture = openFixture(tempDir.resolve("chatlog-null-msgid"))) {
      TargetRef target = new TargetRef("srv", "status");
      LogLineFactory factory = new LogLineFactory(fixedClock(1_700_002_000_000L));

      LogLine n1 = factory.noticeAt(target, "server", "maintenance", 1_700_002_000_000L);
      LogLine n2 = factory.noticeAt(target, "server", "maintenance", 1_700_002_000_000L);
      fixture.repo.insertBatch(List.of(n1, n2));

      assertEquals(2, fixture.repo.fetchRecent("srv", "status", 10).size());
    }
  }

  @Test
  void legacyRowsCanBeBackfilledAndDeduplicatedByMessageId() {
    try (Fixture fixture = openFixture(tempDir.resolve("chatlog-legacy-message-id-repair"))) {
      insertLegacyRowWithNullMessageId(
          fixture, "srv", "#chan", 1_700_010_000_000L, "first", "legacy-dup-1");
      insertLegacyRowWithNullMessageId(
          fixture, "srv", "#chan", 1_700_010_001_000L, "second", "legacy-dup-1");
      insertLegacyRowWithNullMessageId(
          fixture, "srv", "#chan", 1_700_010_002_000L, "third", "legacy-unique-1");

      runLegacyMessageIdRepair(fixture.repo);

      Integer dupCount =
          fixture.jdbc.queryForObject(
              "SELECT COUNT(*) FROM chat_log WHERE message_id = ?", Integer.class, "legacy-dup-1");
      Integer uniqueCount =
          fixture.jdbc.queryForObject(
              "SELECT COUNT(*) FROM chat_log WHERE message_id = ?",
              Integer.class,
              "legacy-unique-1");

      assertEquals(1, dupCount == null ? 0 : dupCount);
      assertEquals(1, uniqueCount == null ? 0 : uniqueCount);

      List<LogLine> rows = fixture.repo.fetchRecent("srv", "#chan", 10);
      List<Map<String, Object>> rawRows =
          fixture.jdbc.queryForList("SELECT id, text, message_id, meta FROM chat_log ORDER BY id");
      assertEquals(2, rows.size(), rawRows.toString());
      assertEquals("third", rows.getFirst().text());
      assertEquals("first", rows.get(1).text());
    }
  }

  @Test
  void targetLookupsAreCaseInsensitive() {
    try (Fixture fixture = openFixture(tempDir.resolve("chatlog-case-insensitive-target"))) {
      TargetRef target = new TargetRef("srv", "#ChanCase");
      LogLineFactory factory = new LogLineFactory(fixedClock(1_700_003_000_000L));

      fixture.repo.insert(
          factory.chatAt(
              target,
              "alice",
              "case-check",
              false,
              1_700_003_000_000L,
              "case-target-1",
              Map.of("msgid", "case-target-1")));

      assertEquals(1, fixture.repo.fetchRecent("srv", "#chancase", 10).size());
      assertEquals(1, fixture.repo.fetchRecent("srv", "#CHANCASE", 10).size());
      assertEquals(1, fixture.repo.fetchRecentRows("srv", "#chancase", 10).size());
      assertEquals(
          1,
          fixture
              .repo
              .fetchOlderRows("srv", "#chancase", Long.MAX_VALUE, Long.MAX_VALUE, 10)
              .size());
      assertEquals(1, fixture.repo.deleteTarget("srv", "#chancase"));
      assertEquals(0, fixture.repo.fetchRecent("srv", "#ChanCase", 10).size());
    }
  }

  private static Clock fixedClock(long epochMs) {
    return Clock.fixed(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
  }

  private static void runLegacyMessageIdRepair(ChatLogRepository repo) {
    long afterId = -1L;
    while (true) {
      List<ChatLogRepository.LegacyMessageIdRow> batch =
          repo.fetchLegacyRowsWithoutMessageIdAfter(afterId, 2);
      if (batch == null || batch.isEmpty()) return;
      afterId = batch.get(batch.size() - 1).id();

      for (ChatLogRepository.LegacyMessageIdRow row : batch) {
        String messageId = ChatLogRepository.extractMessageId(row.metaJson());
        if (messageId.isBlank()) continue;
        repo.backfillMessageIdOrDeleteDuplicate(row, messageId);
      }
    }
  }

  private static void insertLegacyRowWithNullMessageId(
      Fixture fixture,
      String serverId,
      String target,
      long tsEpochMs,
      String text,
      String messageId) {
    String metaJson = "{\"messageId\":\"" + messageId + "\"}";
    fixture.jdbc.update(
        """
        INSERT INTO chat_log(
          server_id,
          target,
          ts_epoch_ms,
          direction,
          kind,
          from_nick,
          text,
          outgoing_local_echo,
          soft_ignored,
          meta,
          message_id
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
        """,
        serverId,
        target,
        tsEpochMs,
        "IN",
        "CHAT",
        "alice",
        text,
        false,
        false,
        metaJson,
        null);
  }

  private static Fixture openFixture(Path basePath) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
    ds.setUrl("jdbc:hsqldb:file:" + basePath.toAbsolutePath() + ";hsqldb.tx=mvcc");
    ds.setUsername("SA");
    ds.setPassword("");

    Flyway.configure().dataSource(ds).locations("classpath:db/migration/chatlog").load().migrate();

    JdbcTemplate jdbc = new JdbcTemplate(ds);
    return new Fixture(jdbc, new ChatLogRepository(jdbc));
  }

  private record Fixture(JdbcTemplate jdbc, ChatLogRepository repo) implements AutoCloseable {
    @Override
    public void close() {
      try {
        jdbc.execute("SHUTDOWN");
      } catch (Exception ignored) {
      }
    }
  }
}
