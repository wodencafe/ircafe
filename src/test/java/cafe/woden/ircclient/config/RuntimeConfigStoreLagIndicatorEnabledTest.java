package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreLagIndicatorEnabledTest {

  @TempDir Path tempDir;

  @Test
  void lagIndicatorEnabledDefaultsWhenUnset() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    assertTrue(store.readLagIndicatorEnabled(true));
    assertFalse(store.readLagIndicatorEnabled(false));
  }

  @Test
  void lagIndicatorEnabledPersistsAndReadsBack() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberLagIndicatorEnabled(false);
    assertFalse(store.readLagIndicatorEnabled(true));

    store.rememberLagIndicatorEnabled(true);
    assertTrue(store.readLagIndicatorEnabled(false));
  }
}
