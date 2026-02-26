package cafe.woden.ircclient.dcc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DccTransferStoreTest {

  @Test
  void upsertNormalizesValuesAndListsNewestFirst() throws Exception {
    DccTransferStore store = new DccTransferStore();

    store.upsert(" libera ", " id-1 ", " alice ", " Chat ", " Open ", " detail ", -5, null);
    Thread.sleep(2L);
    store.upsert(
        "libera",
        "id-2",
        "bob",
        "File",
        "Sending",
        "50%",
        120,
        DccTransferStore.ActionHint.GET_FILE);

    List<DccTransferStore.Entry> entries = store.listAll("libera");
    assertEquals(2, entries.size());

    DccTransferStore.Entry newest = entries.get(0);
    assertEquals("id-2", newest.entryId());
    assertEquals(Integer.valueOf(100), newest.progressPercent());
    assertEquals(DccTransferStore.ActionHint.GET_FILE, newest.actionHint());

    DccTransferStore.Entry older = entries.get(1);
    assertEquals("id-1", older.entryId());
    assertEquals("libera", older.serverId());
    assertEquals(Integer.valueOf(0), older.progressPercent());
    assertEquals(DccTransferStore.ActionHint.NONE, older.actionHint());
  }

  @Test
  void removeAndClearEmitChangesOnlyWhenStateMutates() {
    DccTransferStore store = new DccTransferStore();
    List<DccTransferStore.Change> changes = new ArrayList<>();
    var sub = store.changes().subscribe(changes::add);
    try {
      store.remove("libera", "missing");
      store.clearServer("libera");

      store.upsert("libera", "id-1", "alice", "Chat", "Open", "", 10, null);
      store.remove("libera", "id-1");
      store.upsert("libera", "id-2", "bob", "File", "Done", "", 100, null);
      store.clearServer("libera");
    } finally {
      sub.dispose();
    }

    assertEquals(
        List.of(
            new DccTransferStore.Change("libera"),
            new DccTransferStore.Change("libera"),
            new DccTransferStore.Change("libera"),
            new DccTransferStore.Change("libera")),
        changes);
  }

  @Test
  void maxEntriesPerServerTrimsOldestEntries() {
    DccTransferStore store = new DccTransferStore(50);
    for (int i = 0; i < 55; i++) {
      store.upsert(
          "libera",
          "id-" + i,
          "nick-" + i,
          "File",
          "Sending",
          "entry-" + i,
          i % 101,
          DccTransferStore.ActionHint.NONE);
    }

    List<DccTransferStore.Entry> entries = store.listAll("libera");
    assertEquals(50, entries.size());

    Set<String> ids =
        entries.stream().map(DccTransferStore.Entry::entryId).collect(Collectors.toSet());
    for (int i = 0; i < 5; i++) {
      assertTrue(!ids.contains("id-" + i), "oldest entries should be trimmed first");
    }
  }
}
