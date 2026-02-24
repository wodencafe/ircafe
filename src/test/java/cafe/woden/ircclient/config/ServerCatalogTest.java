package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.processors.BehaviorProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServerCatalogTest {

  @Test
  void findPrefersPersistedServerOverEphemeralServer() {
    IrcProperties.Server persisted = server("libera");
    IrcProperties.Server ephemeral = server("libera");

    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    EphemeralServerRegistry ephemeralServers = mock(EphemeralServerRegistry.class);
    when(serverRegistry.find("libera")).thenReturn(Optional.of(persisted));
    when(ephemeralServers.find("libera")).thenReturn(Optional.of(ephemeral));

    ServerCatalog catalog = new ServerCatalog(serverRegistry, ephemeralServers);

    assertSame(persisted, catalog.find("libera").orElseThrow());
    verify(serverRegistry).find("libera");
    verify(ephemeralServers, never()).find("libera");
  }

  @Test
  void findEntryReturnsEphemeralWithOriginWhenNotPersisted() {
    IrcProperties.Server ephemeral = server("soju-net");

    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    EphemeralServerRegistry ephemeralServers = mock(EphemeralServerRegistry.class);
    when(serverRegistry.find("soju-net")).thenReturn(Optional.empty());
    when(ephemeralServers.find("soju-net")).thenReturn(Optional.of(ephemeral));
    when(ephemeralServers.originOf("soju-net")).thenReturn(Optional.of("soju"));

    ServerCatalog catalog = new ServerCatalog(serverRegistry, ephemeralServers);
    ServerEntry entry = catalog.findEntry("soju-net").orElseThrow();

    assertSame(ephemeral, entry.server());
    assertTrue(entry.ephemeral());
    assertEquals("soju", entry.originId());
  }

  @Test
  void containsIdTrimsInputAndChecksBothRegistries() {
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    EphemeralServerRegistry ephemeralServers = mock(EphemeralServerRegistry.class);
    when(serverRegistry.containsId("libera")).thenReturn(false);
    when(ephemeralServers.containsId("libera")).thenReturn(true);

    ServerCatalog catalog = new ServerCatalog(serverRegistry, ephemeralServers);

    assertTrue(catalog.containsId(" libera "));
    assertFalse(catalog.containsId(" "));
    assertFalse(catalog.containsId(null));
    verify(serverRegistry).containsId("libera");
    verify(ephemeralServers).containsId("libera");
  }

  @Test
  void entriesReturnsPersistedThenEphemeralAndSkipsNullPersistedRows() {
    IrcProperties.Server persistedA = server("libera");
    IrcProperties.Server persistedB = server("oftc");
    IrcProperties.Server ephemeral = server("soju-net");

    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    EphemeralServerRegistry ephemeralServers = mock(EphemeralServerRegistry.class);
    List<IrcProperties.Server> persistedRows = new ArrayList<>();
    persistedRows.add(persistedA);
    persistedRows.add(null);
    persistedRows.add(persistedB);
    when(serverRegistry.servers()).thenReturn(persistedRows);
    when(ephemeralServers.entries())
        .thenReturn(List.of(ServerEntry.ephemeral(ephemeral, "soju")));

    ServerCatalog catalog = new ServerCatalog(serverRegistry, ephemeralServers);
    List<ServerEntry> entries = catalog.entries();

    assertEquals(3, entries.size());
    assertEquals(ServerEntry.persistent(persistedA), entries.get(0));
    assertEquals(ServerEntry.persistent(persistedB), entries.get(1));
    assertEquals(ServerEntry.ephemeral(ephemeral, "soju"), entries.get(2));
  }

  @Test
  void updatesCombinesPersistedAndEphemeralAndSuppressesDuplicateStates() {
    IrcProperties.Server persistedA = server("libera");
    IrcProperties.Server persistedB = server("oftc");
    IrcProperties.Server ephemeral = server("soju-net");

    BehaviorProcessor<List<IrcProperties.Server>> persistedUpdates =
        BehaviorProcessor.createDefault(List.of(persistedA));
    BehaviorProcessor<List<ServerEntry>> ephemeralUpdates =
        BehaviorProcessor.createDefault(List.of());

    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    EphemeralServerRegistry ephemeralServers = mock(EphemeralServerRegistry.class);
    when(serverRegistry.updates()).thenReturn(persistedUpdates.onBackpressureLatest());
    when(ephemeralServers.updates()).thenReturn(ephemeralUpdates.onBackpressureLatest());

    ServerCatalog catalog = new ServerCatalog(serverRegistry, ephemeralServers);
    var observer = catalog.updates().test();

    persistedUpdates.onNext(List.of(persistedA)); // duplicate state
    ephemeralUpdates.onNext(List.of(ServerEntry.ephemeral(ephemeral, "soju")));
    ephemeralUpdates.onNext(List.of(ServerEntry.ephemeral(ephemeral, "soju"))); // duplicate state
    persistedUpdates.onNext(List.of(persistedA, persistedB));

    observer.assertValueCount(3);
    observer.assertValueAt(0, v -> v.equals(List.of(ServerEntry.persistent(persistedA))));
    observer.assertValueAt(
        1,
        v ->
            v.equals(
                List.of(
                    ServerEntry.persistent(persistedA),
                    ServerEntry.ephemeral(ephemeral, "soju"))));
    observer.assertValueAt(
        2,
        v ->
            v.equals(
                List.of(
                    ServerEntry.persistent(persistedA),
                    ServerEntry.persistent(persistedB),
                    ServerEntry.ephemeral(ephemeral, "soju"))));
    observer.cancel();
  }

  @Test
  void requireAndRequireEntryThrowForUnknownIds() {
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    EphemeralServerRegistry ephemeralServers = mock(EphemeralServerRegistry.class);
    when(serverRegistry.find("missing")).thenReturn(Optional.empty());
    when(ephemeralServers.find("missing")).thenReturn(Optional.empty());

    ServerCatalog catalog = new ServerCatalog(serverRegistry, ephemeralServers);

    IllegalArgumentException requireEx =
        assertThrows(IllegalArgumentException.class, () -> catalog.require("missing"));
    IllegalArgumentException requireEntryEx =
        assertThrows(IllegalArgumentException.class, () -> catalog.requireEntry("missing"));

    assertEquals("Unknown server id: missing", requireEx.getMessage());
    assertEquals("Unknown server id: missing", requireEntryEx.getMessage());
    assertTrue(catalog.findEntry(" ").isEmpty());
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
