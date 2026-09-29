package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreChatSmoothWheelScrollingTest {

  @TempDir Path tempDir;

  @Test
  void smoothWheelScrollingDefaultsWhenUnset() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    assertTrue(store.readChatSmoothWheelScrollingEnabled(true));
    assertFalse(store.readChatSmoothWheelScrollingEnabled(false));
  }

  @Test
  void smoothWheelScrollingCanBePersistedAndReadBack() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    store.rememberChatSmoothWheelScrollingEnabled(false);
    assertFalse(store.readChatSmoothWheelScrollingEnabled(true));

    store.rememberChatSmoothWheelScrollingEnabled(true);
    assertTrue(store.readChatSmoothWheelScrollingEnabled(false));
  }
}
