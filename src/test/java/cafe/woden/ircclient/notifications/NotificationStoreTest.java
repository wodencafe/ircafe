package cafe.woden.ircclient.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationStoreTest {

  @Test
  void recordsEventsAcrossCategoriesAndCountsPerServer() {
    NotificationStore store = new NotificationStore();
    TargetRef chan = new TargetRef("libera", "#ircafe");

    store.recordHighlight(chan, "alice", "alice: ping");
    store.recordRuleMatch(chan, "bob", "Rule A", "match snippet");
    store.recordIrcEvent("libera", "#ircafe", "carol", "Topic changed", "new topic body");

    assertEquals(1, store.listAll("libera").size());
    assertEquals("alice: ping", store.listAll("libera").getFirst().snippet());
    assertEquals(1, store.listAllRuleMatches("libera").size());
    assertEquals(1, store.listAllIrcEventRules("libera").size());
    assertEquals(3, store.count("libera"));
  }

  @Test
  void ignoresUiOnlyAndNonChannelTargets() {
    NotificationStore store = new NotificationStore();

    store.recordHighlight(TargetRef.notifications("libera"), "alice");
    store.recordHighlight(new TargetRef("libera", "status"), "alice");
    store.recordRuleMatch(TargetRef.dccTransfers("libera"), "alice", "Rule A", "snippet");
    store.recordRuleMatch(new TargetRef("libera", "status"), "alice", "Rule A", "snippet");

    assertEquals(0, store.count("libera"));
    assertTrue(store.listAll("libera").isEmpty());
    assertTrue(store.listAllRuleMatches("libera").isEmpty());
  }

  @Test
  void defaultRuleMatchCooldownSuppressesDuplicatesUntilChannelCleared() {
    NotificationStore store = new NotificationStore();
    TargetRef chan = new TargetRef("libera", "#ircafe");

    store.recordRuleMatch(chan, "alice", "Rule A", "first");
    store.recordRuleMatch(chan, "alice", "Rule A", "second");

    assertEquals(1, store.listAllRuleMatches("libera").size());

    store.clearChannel(chan);
    assertEquals(0, store.listAllRuleMatches("libera").size());

    store.recordRuleMatch(chan, "alice", "Rule A", "third");
    assertEquals(1, store.listAllRuleMatches("libera").size());
  }

  @Test
  void cooldownCanBeDisabledViaUiSettingsPort() {
    UiSettingsPort settings = mock(UiSettingsPort.class);
    when(settings.get()).thenReturn(new UiSettingsSnapshot(List.of(), 0, 30, true, true));

    NotificationStore store = new NotificationStore(settings, 2000);
    TargetRef chan = new TargetRef("libera", "#ircafe");

    store.recordRuleMatch(chan, "alice", "Rule A", "first");
    store.recordRuleMatch(chan, "alice", "Rule A", "second");

    assertEquals(2, store.listAllRuleMatches("libera").size());
  }

  @Test
  void staleRuleMatchCooldownKeysArePrunedBeforeCooldownCheck() throws Exception {
    NotificationStore store = new NotificationStore();
    for (int i = 0; i < 32; i++) {
      store.recordRuleMatch(
          new TargetRef("libera", "#chan" + i), "nick" + i, "Rule " + i, "seed " + i);
    }

    Field field = NotificationStore.class.getDeclaredField("lastRuleMatchAt");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<Object, Instant> cooldownMap = (Map<Object, Instant>) field.get(store);
    cooldownMap.replaceAll((k, v) -> Instant.now().minus(Duration.ofDays(2)));
    int before = cooldownMap.size();
    assertTrue(before >= 30);

    TargetRef fresh = new TargetRef("libera", "#fresh");
    int beforeEvents = store.listAllRuleMatches("libera").size();
    store.recordRuleMatch(fresh, "alice", "Rule Fresh", "after-prune");
    store.recordRuleMatch(fresh, "alice", "Rule Fresh", "blocked-by-cooldown");

    assertEquals(beforeEvents + 1, store.listAllRuleMatches("libera").size());
    assertTrue(cooldownMap.size() <= 2);
  }
}
