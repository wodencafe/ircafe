package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreChatLoggingEnabledTest {

  @TempDir Path tempDir;

  @Test
  void chatLoggingEnabledDefaultsWhenUnset() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    assertTrue(store.readChatLoggingEnabled(true));
    assertFalse(store.readChatLoggingEnabled(false));
  }

  @Test
  void chatLoggingEnabledPersistsAndReadsBack() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberChatLoggingEnabled(true);
    assertTrue(store.readChatLoggingEnabled(false));

    store.rememberChatLoggingEnabled(false);
    assertFalse(store.readChatLoggingEnabled(true));
  }
}
