package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerTreeUnreadBadgeScaleTest {

  @TempDir Path tempDir;

  @Test
  void serverTreeUnreadBadgeScaleRoundTripsAndClamps() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertEquals(100, store.readServerTreeUnreadBadgeScalePercent(100));

    store.rememberServerTreeUnreadBadgeScalePercent(80);
    assertEquals(80, store.readServerTreeUnreadBadgeScalePercent(100));

    store.rememberServerTreeUnreadBadgeScalePercent(5);
    assertEquals(50, store.readServerTreeUnreadBadgeScalePercent(100));

    store.rememberServerTreeUnreadBadgeScalePercent(999);
    assertEquals(150, store.readServerTreeUnreadBadgeScalePercent(100));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("serverTreeUnreadBadgeScalePercent"));
  }

  @Test
  void mutationBatchWritesCombinedUiUpdates() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.runMutationBatch(
        () -> {
          store.rememberTypingIndicatorsEnabled(false);
          store.rememberServerTreeUnreadBadgeScalePercent(70);
        });

    assertEquals(70, store.readServerTreeUnreadBadgeScalePercent(100));
    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("typingIndicatorsEnabled: false"));
    assertTrue(yaml.contains("serverTreeUnreadBadgeScalePercent: 70"));
  }
}
