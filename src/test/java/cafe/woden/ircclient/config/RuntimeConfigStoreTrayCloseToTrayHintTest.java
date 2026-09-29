package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreTrayCloseToTrayHintTest {

  @TempDir Path tempDir;

  @Test
  void closeToTrayHintDefaultsToFalseWhenUnset() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    assertFalse(store.readTrayCloseToTrayHintShown(false));
  }

  @Test
  void closeToTrayHintCanBePersistedAndReadBack() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    store.rememberTrayCloseToTrayHintShown(true);
    assertTrue(store.readTrayCloseToTrayHintShown(false));
  }
}
