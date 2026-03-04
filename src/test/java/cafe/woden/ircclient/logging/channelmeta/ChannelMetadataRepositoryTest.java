package cafe.woden.ircclient.logging.channelmeta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ChannelMetadataRepositoryTest {

  @TempDir Path tempDir;

  @Test
  void upsertInsertsAndUpdatesRowUsingHsqldb() {
    try (Fixture fixture = openFixture(tempDir.resolve("channelmeta"))) {
      ChannelMetadataRepository repo = fixture.repo();

      repo.upsert(
          new ChannelMetadataRepository.ChannelMetadataRow(
              "libera", "#ircafe", "#IRCafe", "Topic one", "alice", 10L, 100L));
      repo.upsert(
          new ChannelMetadataRepository.ChannelMetadataRow(
              "libera", "#ircafe", "#IRCafe", "Topic two", "bob", 20L, 200L));

      var stored = repo.find("libera", "#ircafe");
      assertTrue(stored.isPresent());
      assertEquals("Topic two", stored.orElseThrow().topic());
      assertEquals("bob", stored.orElseThrow().topicSetBy());
      assertEquals(20L, stored.orElseThrow().topicSetAtEpochMs());
      assertEquals(200L, stored.orElseThrow().updatedAtEpochMs());
    }
  }

  @Test
  void deleteRemovesRow() {
    try (Fixture fixture = openFixture(tempDir.resolve("channelmeta-delete"))) {
      ChannelMetadataRepository repo = fixture.repo();

      repo.upsert(
          new ChannelMetadataRepository.ChannelMetadataRow(
              "libera", "#ircafe", "#ircafe", "Topic one", null, null, 1L));
      repo.delete("libera", "#ircafe");

      assertFalse(repo.find("libera", "#ircafe").isPresent());
    }
  }

  private static Fixture openFixture(Path basePath) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
    ds.setUrl("jdbc:hsqldb:file:" + basePath.toAbsolutePath() + ";hsqldb.tx=mvcc");
    ds.setUsername("SA");
    ds.setPassword("");

    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration/channelmeta")
        .load()
        .migrate();
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    return new Fixture(jdbc, new ChannelMetadataRepository(jdbc));
  }

  private record Fixture(JdbcTemplate jdbc, ChannelMetadataRepository repo)
      implements AutoCloseable {
    @Override
    public void close() {
      try {
        jdbc.execute("SHUTDOWN");
      } catch (Exception ignored) {
      }
    }
  }
}
