package cafe.woden.ircclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserListStoreTest {

  @Test
  void updateRealNameAcrossChannelsRefreshesRosterWithoutLosingOtherIdentityFields() {
    UserListStore store = new UserListStore();
    String serverId = "libera";

    store.put(
        serverId,
        "#ircafe",
        List.of(
            new NickInfo(
                "Alice",
                "@",
                "Alice!user@old.host",
                AwayState.AWAY,
                "brb",
                AccountState.LOGGED_IN,
                "alice-account"),
            new NickInfo("Bob", "+", "Bob!user@host")));
    store.put(
        serverId,
        "#java",
        List.of(
            new NickInfo("alice", "", "", AwayState.UNKNOWN, null, AccountState.UNKNOWN, null)));

    Set<String> changed = store.updateRealNameAcrossChannels(serverId, "ALICE", "Alice Liddell");
    assertEquals(Set.of("#ircafe", "#java"), changed);
    assertTrue(store.isNickPresentOnServer(serverId, "alice"));

    NickInfo ircafeAlice = store.get(serverId, "#ircafe").get(0);
    assertEquals("Alice Liddell", ircafeAlice.realName());
    assertEquals("Alice!user@old.host", ircafeAlice.hostmask());
    assertEquals(AwayState.AWAY, ircafeAlice.awayState());
    assertEquals("alice-account", ircafeAlice.accountName());

    // Host updates should preserve learned real-name metadata.
    store.updateHostmaskAcrossChannels(serverId, "alice", "Alice!~user@new.host");
    NickInfo updatedAlice = store.get(serverId, "#ircafe").get(0);
    assertEquals("Alice Liddell", updatedAlice.realName());
    assertEquals("Alice!~user@new.host", updatedAlice.hostmask());
  }

  @Test
  void putMergesPreviouslyLearnedRealNameIntoFreshRosterSnapshot() {
    UserListStore store = new UserListStore();
    String serverId = "libera";

    store.put(serverId, "#ircafe", List.of(new NickInfo("Alice", "", "Alice!user@host")));
    store.updateRealNameAcrossChannels(serverId, "alice", "Alice Liddell");

    // Simulate a fresh NAMES snapshot that lacks real-name/metadata.
    store.put(serverId, "#ircafe", List.of(new NickInfo("Alice", "", "")));

    NickInfo merged = store.get(serverId, "#ircafe").get(0);
    assertEquals("Alice Liddell", merged.realName());
  }

  @Test
  void channelsContainingNickReturnsOnlyChannelsThatContainThatNick() {
    UserListStore store = new UserListStore();
    String serverId = "libera";

    store.put(serverId, "##chat", List.of(new NickInfo("quinsmith", "", "quinsmith!u@h")));
    store.put(serverId, "##Llamas", List.of(new NickInfo("alpaca", "", "alpaca!u@h")));
    store.put(serverId, "##Chat-Overflow", List.of(new NickInfo("QuinSmith", "", "QuinSmith!u@h")));

    assertEquals(
        Set.of("##chat", "##chat-overflow"), store.channelsContainingNick(serverId, "QUINSMITH"));
    assertTrue(store.channelsContainingNick(serverId, "nobody").isEmpty());
  }
}
