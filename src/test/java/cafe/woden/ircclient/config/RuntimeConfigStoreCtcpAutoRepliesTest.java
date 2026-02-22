package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreCtcpAutoRepliesTest {

  @TempDir
  Path tempDir;

  @Test
  void ctcpAutoReplySettingsDefaultToEnabledWhenUnset() {
    RuntimeConfigStore store = new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(),
        new IrcProperties(null, List.of()));

    assertTrue(store.readCtcpAutoRepliesEnabled(true));
    assertTrue(store.readCtcpAutoReplyVersionEnabled(true));
    assertTrue(store.readCtcpAutoReplyPingEnabled(true));
    assertTrue(store.readCtcpAutoReplyTimeEnabled(true));
  }

  @Test
  void ctcpAutoReplySettingsPersistAndReadBack() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = new RuntimeConfigStore(
        cfg.toString(),
        new IrcProperties(null, List.of()));

    store.rememberCtcpAutoRepliesEnabled(false);
    store.rememberCtcpAutoReplyVersionEnabled(false);
    store.rememberCtcpAutoReplyPingEnabled(true);
    store.rememberCtcpAutoReplyTimeEnabled(false);

    assertFalse(store.readCtcpAutoRepliesEnabled(true));
    assertFalse(store.readCtcpAutoReplyVersionEnabled(true));
    assertTrue(store.readCtcpAutoReplyPingEnabled(false));
    assertFalse(store.readCtcpAutoReplyTimeEnabled(true));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("ctcpReplies"));
    assertTrue(yaml.contains("enabled: false"));
    assertTrue(yaml.contains("version: false"));
    assertTrue(yaml.contains("ping: true"));
    assertTrue(yaml.contains("time: false"));
  }
}
