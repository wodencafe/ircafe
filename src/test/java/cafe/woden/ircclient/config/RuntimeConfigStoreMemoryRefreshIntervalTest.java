package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreMemoryRefreshIntervalTest {

  @TempDir Path tempDir;

  @Test
  void memoryRefreshIntervalRoundTripsAndClamps() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertEquals(1000, store.readMemoryUsageRefreshIntervalMs(1000));

    store.rememberMemoryUsageRefreshIntervalMs(1800);
    assertEquals(1800, store.readMemoryUsageRefreshIntervalMs(1000));

    store.rememberMemoryUsageRefreshIntervalMs(50);
    assertEquals(250, store.readMemoryUsageRefreshIntervalMs(1000));

    store.rememberMemoryUsageRefreshIntervalMs(120_000);
    assertEquals(60_000, store.readMemoryUsageRefreshIntervalMs(1000));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("memoryUsageRefreshIntervalMs"));
  }
}

