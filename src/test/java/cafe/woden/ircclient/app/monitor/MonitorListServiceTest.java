package cafe.woden.ircclient.app.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MonitorListServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void addRemoveAndPersistMonitorNicks() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(),
            new IrcProperties(null, List.of(server("libera"))));
    MonitorListService service = new MonitorListService(store);

    assertEquals(2, service.addNicks("libera", List.of("Alice", "bob", "alice")));
    assertEquals(List.of("Alice", "bob"), service.listNicks("libera"));

    assertEquals(1, service.removeNicks("libera", List.of("ALICE", "charlie")));
    assertEquals(List.of("bob"), service.listNicks("libera"));

    MonitorListService reloaded = new MonitorListService(store);
    assertEquals(List.of("bob"), reloaded.listNicks("libera"));
  }

  @Test
  void tokenizesNickInputFromCommaOrSpaceSeparatedValues() {
    assertEquals(
        List.of("alice", "bob", "charlie"),
        MonitorListService.tokenizeNickInput("alice,bob charlie"));
  }

  @Test
  void emitsChangesOnlyForMutationsAndClearReturnsRemovedCount() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(),
            new IrcProperties(null, List.of(server("libera"))));
    MonitorListService service = new MonitorListService(store);

    ArrayList<MonitorListService.Change> changes = new ArrayList<>();
    io.reactivex.rxjava3.disposables.Disposable sub = service.changes().subscribe(changes::add);
    try {
      assertEquals(2, service.addNicks("libera", List.of("alice", "bob")));
      assertEquals(0, service.addNicks("libera", List.of("Alice")));
      assertEquals(1, service.removeNicks("libera", List.of("BOB")));
      assertEquals(1, service.replaceNicks("libera", List.of("carol")));
      assertEquals(1, service.replaceNicks("libera", List.of("carol")));
      assertEquals(1, service.clearNicks("libera"));
      assertEquals(0, service.clearNicks("libera"));
    } finally {
      sub.dispose();
    }

    assertEquals(
        List.of(
            new MonitorListService.Change("libera", MonitorListService.ChangeKind.ADDED),
            new MonitorListService.Change("libera", MonitorListService.ChangeKind.REMOVED),
            new MonitorListService.Change("libera", MonitorListService.ChangeKind.REPLACED),
            new MonitorListService.Change("libera", MonitorListService.ChangeKind.CLEARED)),
        changes);
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
