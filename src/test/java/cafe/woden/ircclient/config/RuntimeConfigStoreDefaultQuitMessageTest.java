package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreDefaultQuitMessageTest {

  @TempDir Path tempDir;

  @Test
  void defaultQuitMessageFallsBackWhenUnset() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    assertEquals(RuntimeConfigStore.DEFAULT_QUIT_MESSAGE, store.readDefaultQuitMessage());
  }

  @Test
  void rememberDefaultQuitMessagePersistsCustomValue() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberDefaultQuitMessage("bye from ircafe");

    assertEquals("bye from ircafe", store.readDefaultQuitMessage());

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("defaultQuitMessage: bye from ircafe"));
  }

  @Test
  void blankDefaultQuitMessageResetsToBuiltInDefault() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberDefaultQuitMessage("custom");
    store.rememberDefaultQuitMessage("   ");

    assertEquals(RuntimeConfigStore.DEFAULT_QUIT_MESSAGE, store.readDefaultQuitMessage());

    String yaml = Files.readString(cfg);
    assertFalse(yaml.contains("defaultQuitMessage:"));
  }
}
