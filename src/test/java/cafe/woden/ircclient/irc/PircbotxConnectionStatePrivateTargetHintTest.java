package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PircbotxConnectionStatePrivateTargetHintTest {

  @Test
  void findsHintByMessageId() {
    PircbotxConnectionState state = new PircbotxConnectionState("znc");
    long now = 1_000_000L;

    state.rememberPrivateTargetHint("WodenCafe", "alice", "PRIVMSG", "hello there", "msg-1", now);

    String target =
        state.findPrivateTargetHint("wodencafe", "PRIVMSG", "different text", "msg-1", now + 1_000);
    assertEquals("alice", target);
  }

  @Test
  void fallsBackToPayloadFingerprintWhenMessageIdMissing() {
    PircbotxConnectionState state = new PircbotxConnectionState("znc");
    long now = 2_000_000L;

    state.rememberPrivateTargetHint("wodencafe", "bob", "ACTION", "waves", "", now);

    String target = state.findPrivateTargetHint("WODENCAFE", "action", "waves", "", now + 500);
    assertEquals("bob", target);
  }

  @Test
  void ignoresExpiredHints() {
    PircbotxConnectionState state = new PircbotxConnectionState("znc");
    long now = 3_000_000L;

    state.rememberPrivateTargetHint(
        "wodencafe", "carol", "PRIVMSG", "old", "msg-old", now - 200_000L);

    String target = state.findPrivateTargetHint("wodencafe", "PRIVMSG", "old", "msg-old", now);
    assertEquals("", target);
  }

  @Test
  void resetClearsHints() {
    PircbotxConnectionState state = new PircbotxConnectionState("znc");
    long now = 4_000_000L;

    state.rememberPrivateTargetHint("wodencafe", "dave", "PRIVMSG", "hey", "msg-2", now);
    state.resetNegotiatedCaps();

    String target = state.findPrivateTargetHint("wodencafe", "PRIVMSG", "hey", "msg-2", now + 100);
    assertEquals("", target);
  }
}
