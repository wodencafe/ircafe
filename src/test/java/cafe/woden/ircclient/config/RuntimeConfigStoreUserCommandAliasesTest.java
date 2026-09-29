package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreUserCommandAliasesTest {

  @TempDir Path tempDir;

  @Test
  void unknownCommandAsRawDefaultsWhenUnset() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    assertTrue(store.readUnknownCommandAsRawEnabled(true));
    assertFalse(store.readUnknownCommandAsRawEnabled(false));
  }

  @Test
  void unknownCommandAsRawPersistsAndReadsBack() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    store.rememberUnknownCommandAsRawEnabled(true);
    assertTrue(store.readUnknownCommandAsRawEnabled(false));

    store.rememberUnknownCommandAsRawEnabled(false);
    assertFalse(store.readUnknownCommandAsRawEnabled(true));
  }
}
