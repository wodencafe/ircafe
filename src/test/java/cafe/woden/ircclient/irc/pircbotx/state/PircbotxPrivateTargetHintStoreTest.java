package cafe.woden.ircclient.irc.pircbotx.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PircbotxPrivateTargetHintStoreTest {

  @Test
  void findsHintByMessageId() {
    PircbotxPrivateTargetHintStore store = new PircbotxPrivateTargetHintStore();
    long now = 1_000_000L;

    store.remember("WodenCafe", "alice", "PRIVMSG", "hello there", "msg-1", now);

    String target = store.find("wodencafe", "PRIVMSG", "different text", "msg-1", now + 1_000);
    assertEquals("alice", target);
  }

  @Test
  void fallsBackToPayloadFingerprintWhenMessageIdMissing() {
    PircbotxPrivateTargetHintStore store = new PircbotxPrivateTargetHintStore();
    long now = 2_000_000L;

    store.remember("wodencafe", "bob", "ACTION", "waves", "", now);

    String target = store.find("WODENCAFE", "action", "waves", "", now + 500);
    assertEquals("bob", target);
  }

  @Test
  void ignoresExpiredHints() {
    PircbotxPrivateTargetHintStore store = new PircbotxPrivateTargetHintStore();
    long now = 3_000_000L;

    store.remember("wodencafe", "carol", "PRIVMSG", "old", "msg-old", now - 200_000L);

    String target = store.find("wodencafe", "PRIVMSG", "old", "msg-old", now);
    assertEquals("", target);
  }

  @Test
  void clearRemovesHints() {
    PircbotxPrivateTargetHintStore store = new PircbotxPrivateTargetHintStore();
    long now = 4_000_000L;

    store.remember("wodencafe", "dave", "PRIVMSG", "hey", "msg-2", now);
    store.clear();

    String target = store.find("wodencafe", "PRIVMSG", "hey", "msg-2", now + 100);
    assertEquals("", target);
  }
}
