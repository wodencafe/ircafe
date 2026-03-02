package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreChatSmoothWheelScrollingTest {

  @TempDir Path tempDir;

  @Test
  void smoothWheelScrollingDefaultsWhenUnset() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    assertTrue(store.readChatSmoothWheelScrollingEnabled(true));
    assertFalse(store.readChatSmoothWheelScrollingEnabled(false));
  }

  @Test
  void smoothWheelScrollingCanBePersistedAndReadBack() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberChatSmoothWheelScrollingEnabled(false);
    assertFalse(store.readChatSmoothWheelScrollingEnabled(true));

    store.rememberChatSmoothWheelScrollingEnabled(true);
    assertTrue(store.readChatSmoothWheelScrollingEnabled(false));
  }
}
