package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerRegistryTest {

  @Test
  void constructorLoadsServersAndPublishesInitialSnapshot() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    IrcProperties.Server initialLibera = server("libera", "old.libera.example");
    IrcProperties.Server oftc = server("oftc", "irc.oftc.net");
    IrcProperties.Server overrideLibera = server("libera", "irc.libera.chat");

    ServerRegistry registry =
        new ServerRegistry(new IrcProperties(null, List.of(initialLibera, oftc, overrideLibera)), runtimeConfig);

    var observer = registry.updates().test();
    observer.assertValue(List.of(overrideLibera, oftc));

    assertEquals(List.of(overrideLibera, oftc), registry.servers());
    assertSame(overrideLibera, registry.require("libera"));
    assertTrue(registry.containsId(" oftc "));
    assertFalse(registry.find(" ").isPresent());
    verifyNoInteractions(runtimeConfig);
    observer.cancel();
  }

  @Test
  void upsertWritesRuntimeConfigAndEmitsUpdatedSnapshot() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    ServerRegistry registry = new ServerRegistry(new IrcProperties(null, List.of()), runtimeConfig);
    var observer = registry.updates().test();

    IrcProperties.Server libera = server("libera", "irc.libera.chat");
    IrcProperties.Server oftc = server("oftc", "irc.oftc.net");
    registry.upsert(libera);
    registry.upsert(oftc);

    observer.assertValueCount(3);
    observer.assertValueAt(0, List::isEmpty);
    observer.assertValueAt(1, v -> v.equals(List.of(libera)));
    observer.assertValueAt(2, v -> v.equals(List.of(libera, oftc)));
    verify(runtimeConfig).writeServers(List.of(libera));
    verify(runtimeConfig).writeServers(List.of(libera, oftc));
    observer.cancel();
  }

  @Test
  void setAllReplacesStateAndSkipsNullRows() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    IrcProperties.Server libera = server("libera", "irc.libera.chat");
    IrcProperties.Server oftc = server("oftc", "irc.oftc.net");
    ServerRegistry registry = new ServerRegistry(new IrcProperties(null, List.of(libera)), runtimeConfig);

    List<IrcProperties.Server> replacement = new ArrayList<>();
    replacement.add(oftc);
    replacement.add(null);
    replacement.add(libera);
    registry.setAll(replacement);

    assertEquals(List.of(oftc, libera), registry.servers());
    verify(runtimeConfig).writeServers(List.of(oftc, libera));
  }

  @Test
  void removeIgnoresBlankIdsAndPersistsWhenPresentIdRemoved() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    IrcProperties.Server libera = server("libera", "irc.libera.chat");
    IrcProperties.Server oftc = server("oftc", "irc.oftc.net");
    ServerRegistry registry = new ServerRegistry(new IrcProperties(null, List.of(libera, oftc)), runtimeConfig);
    var observer = registry.updates().test();

    registry.remove(" ");
    registry.remove(null);
    registry.remove("libera");

    observer.assertValueCount(2);
    observer.assertValueAt(0, v -> v.equals(List.of(libera, oftc)));
    observer.assertValueAt(1, v -> v.equals(List.of(oftc)));
    verify(runtimeConfig, never()).writeServers(List.of(libera, oftc));
    verify(runtimeConfig).writeServers(List.of(oftc));
    observer.cancel();
  }

  @Test
  void requireThrowsForUnknownServer() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    ServerRegistry registry = new ServerRegistry(new IrcProperties(null, List.of()), runtimeConfig);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> registry.require("missing"));

    assertEquals("Unknown server id: missing", ex.getMessage());
  }

  private static IrcProperties.Server server(String id, String host) {
    return new IrcProperties.Server(
        id,
        host,
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
