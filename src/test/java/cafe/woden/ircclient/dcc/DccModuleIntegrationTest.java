package cafe.woden.ircclient.dcc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class DccModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final DccTransferStore dccTransferStore;

  DccModuleIntegrationTest(
      ApplicationContext applicationContext, DccTransferStore dccTransferStore) {
    this.applicationContext = applicationContext;
    this.dccTransferStore = dccTransferStore;
  }

  @Test
  void exposesSingleDccTransferStoreBean() {
    assertEquals(1, applicationContext.getBeansOfType(DccTransferStore.class).size());
    assertNotNull(dccTransferStore);
  }

  @Test
  void upsertRemoveAndClearServerMutateStoredEntries() {
    String serverId = "libera";
    dccTransferStore.clearServer(serverId);

    dccTransferStore.upsert(
        serverId,
        "chat:alice",
        "alice",
        "Chat",
        "Connected",
        "Open",
        100,
        DccTransferStore.ActionHint.CLOSE_CHAT);
    dccTransferStore.upsert(
        serverId,
        "send:bob",
        "bob",
        "File",
        "Sending",
        "48%",
        48,
        DccTransferStore.ActionHint.NONE);

    Set<String> entryIds =
        dccTransferStore.listAll(serverId).stream()
            .map(DccTransferStore.Entry::entryId)
            .collect(Collectors.toSet());
    assertEquals(Set.of("chat:alice", "send:bob"), entryIds);

    dccTransferStore.remove(serverId, "chat:alice");
    assertEquals(1, dccTransferStore.listAll(serverId).size());

    dccTransferStore.clearServer(serverId);
    assertTrue(dccTransferStore.listAll(serverId).isEmpty());
  }
}
