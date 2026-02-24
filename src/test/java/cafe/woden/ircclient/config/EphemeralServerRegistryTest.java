package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EphemeralServerRegistryTest {

  @Test
  void upsertStoresServerOriginAndEmitsUpdate() {
    EphemeralServerRegistry registry = new EphemeralServerRegistry();
    var observer = registry.updates().test();
    IrcProperties.Server soju = server("soju-net");

    registry.upsert(soju, "soju");

    observer.assertValueCount(2);
    observer.assertValueAt(0, List::isEmpty);
    observer.assertValueAt(1, v -> v.equals(List.of(ServerEntry.ephemeral(soju, "soju"))));
    assertEquals(List.of(soju), registry.servers());
    assertEquals(List.of("soju-net"), registry.serverIds().stream().toList());
    assertTrue(registry.find(" soju-net ").isPresent());
    assertEquals("soju", registry.originOf("soju-net").orElseThrow());
    observer.cancel();
  }

  @Test
  void upsertWithBlankOriginClearsStoredOrigin() {
    EphemeralServerRegistry registry = new EphemeralServerRegistry();
    IrcProperties.Server soju = server("soju-net");

    registry.upsert(soju, "soju");
    registry.upsert(soju, "   ");

    assertTrue(registry.originOf("soju-net").isEmpty());
    assertEquals(List.of(ServerEntry.ephemeral(soju, null)), registry.entries());
  }

  @Test
  void removeByOriginRemovesOnlyMatchingServers() {
    EphemeralServerRegistry registry = new EphemeralServerRegistry();
    IrcProperties.Server sojuA = server("soju-a");
    IrcProperties.Server znc = server("znc-net");
    IrcProperties.Server sojuB = server("soju-b");

    registry.upsert(sojuA, "soju");
    registry.upsert(znc, "znc");
    registry.upsert(sojuB, "soju");
    registry.removeByOrigin("soju");

    assertEquals(List.of(ServerEntry.ephemeral(znc, "znc")), registry.entries());
    assertFalse(registry.containsId("soju-a"));
    assertFalse(registry.containsId("soju-b"));
    assertTrue(registry.containsId("znc-net"));
  }

  @Test
  void removeAndClearDoNotEmitExtraUpdatesOnNoOp() {
    EphemeralServerRegistry registry = new EphemeralServerRegistry();
    var observer = registry.updates().test();

    registry.remove("missing");
    registry.removeByOrigin("missing");
    registry.clear();
    observer.assertValueCount(1);

    IrcProperties.Server soju = server("soju-net");
    registry.upsert(soju, null);
    registry.clear();
    registry.clear();

    observer.assertValueCount(3);
    observer.assertValueAt(0, List::isEmpty);
    observer.assertValueAt(1, v -> v.equals(List.of(ServerEntry.ephemeral(soju, null))));
    observer.assertValueAt(2, List::isEmpty);
    observer.cancel();
  }

  @Test
  void requireThrowsForUnknownServer() {
    EphemeralServerRegistry registry = new EphemeralServerRegistry();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> registry.require("missing"));

    assertEquals("Unknown server id: missing", ex.getMessage());
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
