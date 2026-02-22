package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreTrayCloseToTrayHintTest {

  @TempDir
  Path tempDir;

  @Test
  void closeToTrayHintDefaultsToFalseWhenUnset() {
    RuntimeConfigStore store = new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(),
        new IrcProperties(null, List.of()));

    assertFalse(store.readTrayCloseToTrayHintShown(false));
  }

  @Test
  void closeToTrayHintCanBePersistedAndReadBack() {
    RuntimeConfigStore store = new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(),
        new IrcProperties(null, List.of()));

    store.rememberTrayCloseToTrayHintShown(true);
    assertTrue(store.readTrayCloseToTrayHintShown(false));
  }
}
