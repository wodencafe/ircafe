package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreLagIndicatorEnabledTest {

  @TempDir Path tempDir;

  @Test
  void lagIndicatorEnabledDefaultsWhenUnset() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    assertTrue(store.readLagIndicatorEnabled(true));
    assertFalse(store.readLagIndicatorEnabled(false));
  }

  @Test
  void lagIndicatorEnabledPersistsAndReadsBack() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    store.rememberLagIndicatorEnabled(false);
    assertFalse(store.readLagIndicatorEnabled(true));

    store.rememberLagIndicatorEnabled(true);
    assertTrue(store.readLagIndicatorEnabled(false));
  }
}
