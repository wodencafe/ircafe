package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.logging.model.LogLine;
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

  private static Clock fixedClock(long epochMs) {
    return Clock.fixed(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
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
