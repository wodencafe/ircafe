package cafe.woden.ircclient.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationStoreTest {

  @Test
  void recordsEventsAcrossCategoriesAndCountsPerServer() {
    NotificationStore store = new NotificationStore();
    TargetRef chan = new TargetRef("libera", "#ircafe");

    store.recordHighlight(chan, "alice");
    store.recordRuleMatch(chan, "bob", "Rule A", "match snippet");
    store.recordIrcEvent("libera", "#ircafe", "carol", "Topic changed", "new topic body");

    assertEquals(1, store.listAll("libera").size());
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
}
