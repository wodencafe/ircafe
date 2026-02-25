package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreChatMessageColorsTest {

  @TempDir Path tempDir;

  @Test
  void chatMessageColorOverridesPersistAndCanBeCleared() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberChatMessageColor("#112233");
    store.rememberChatNoticeColor("#223344");
    store.rememberChatActionColor("#334455");
    store.rememberChatErrorColor("#445566");
    store.rememberChatPresenceColor("#556677");

    String persisted = Files.readString(cfg);
    assertTrue(persisted.contains("chatMessageColor"));
    assertTrue(persisted.contains("chatNoticeColor"));
    assertTrue(persisted.contains("chatActionColor"));
    assertTrue(persisted.contains("chatErrorColor"));
    assertTrue(persisted.contains("chatPresenceColor"));
    assertTrue(persisted.contains("#112233"));
    assertTrue(persisted.contains("#223344"));
    assertTrue(persisted.contains("#334455"));
    assertTrue(persisted.contains("#445566"));
    assertTrue(persisted.contains("#556677"));

    store.rememberChatMessageColor(" ");
    store.rememberChatNoticeColor(" ");
    store.rememberChatActionColor(" ");
    store.rememberChatErrorColor(" ");
    store.rememberChatPresenceColor(" ");

    String cleared = Files.readString(cfg);
    assertFalse(cleared.contains("chatMessageColor"));
    assertFalse(cleared.contains("chatNoticeColor"));
    assertFalse(cleared.contains("chatActionColor"));
    assertFalse(cleared.contains("chatErrorColor"));
    assertFalse(cleared.contains("chatPresenceColor"));
  }
}
