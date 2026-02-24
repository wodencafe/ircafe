package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreMonitorNicksTest {

  @TempDir Path tempDir;

  @Test
  void monitorNickListCanBePersistedUpdatedAndCleared() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(),
            new IrcProperties(null, List.of(server("libera"))));

    store.rememberMonitorNick("libera", "Alice");
    store.rememberMonitorNick("libera", "alice");
    store.rememberMonitorNick("libera", "bob");
    assertEquals(List.of("Alice", "bob"), store.readMonitorNicks("libera"));

    store.forgetMonitorNick("libera", "ALICE");
    assertEquals(List.of("bob"), store.readMonitorNicks("libera"));

    store.replaceMonitorNicks("libera", List.of("charlie", "bob!ident@host", "charlie"));
    assertEquals(List.of("charlie", "bob"), store.readMonitorNicks("libera"));

    store.replaceMonitorNicks("libera", List.of());
    assertEquals(List.of(), store.readMonitorNicks("libera"));
  }

  private static IrcProperties.Server server(String id) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        List.of(),
        List.of(),
        null);
  }
}
