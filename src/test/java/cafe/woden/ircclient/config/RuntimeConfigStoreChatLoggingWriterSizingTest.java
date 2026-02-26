package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreChatLoggingWriterSizingTest {

  @TempDir Path tempDir;

  @Test
  void writerQueueAndBatchArePersistedUnderLoggingSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberChatLoggingWriterQueueMax(123_456);
    store.rememberChatLoggingWriterBatchSize(777);

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("logging:"));
    assertTrue(yaml.contains("writerQueueMax: 123456"));
    assertTrue(yaml.contains("writerBatchSize: 777"));
  }

  @Test
  void writerQueueAndBatchAreClampedToSafeBounds() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberChatLoggingWriterQueueMax(5);
    store.rememberChatLoggingWriterBatchSize(0);
    store.rememberChatLoggingWriterQueueMax(2_000_000);
    store.rememberChatLoggingWriterBatchSize(20_000);

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("writerQueueMax: 1000000"));
    assertTrue(yaml.contains("writerBatchSize: 10000"));
  }
}
