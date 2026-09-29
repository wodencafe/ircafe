package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreChatLoggingEnabledTest {

  @TempDir Path tempDir;

  @Test
  void chatLoggingEnabledDefaultsWhenUnset() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    assertTrue(store.readChatLoggingEnabled(true));
    assertFalse(store.readChatLoggingEnabled(false));
  }

  @Test
  void chatLoggingEnabledPersistsAndReadsBack() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    store.rememberChatLoggingEnabled(true);
    assertTrue(store.readChatLoggingEnabled(false));

    store.rememberChatLoggingEnabled(false);
    assertFalse(store.readChatLoggingEnabled(true));
  }
}
