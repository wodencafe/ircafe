package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxPrivateConversationSupport;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import org.junit.jupiter.api.Test;

class PircbotxPrivateConversationSupportTest {

  @Test
  void deriveConversationTargetUsesSenderWhenDestinationIsSelf() {
    PircbotxPrivateConversationSupport support =
        new PircbotxPrivateConversationSupport(new PircbotxConnectionState("libera"));

    String target = support.deriveConversationTarget("me", "alice", "me");

    assertEquals("alice", target);
  }

  @Test
  void inferPrivateDestinationFromHintsUsesConnectionState() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxPrivateConversationSupport support = new PircbotxPrivateConversationSupport(conn);
    long now = System.currentTimeMillis();
    conn.rememberPrivateTargetHint("me", "alice", "PRIVMSG", "hello", "msg-1", now);

    String target = support.inferPrivateDestinationFromHints("me", "PRIVMSG", "hello", "msg-1");

    assertEquals("alice", target);
  }

  @Test
  void shouldSuppressSelfBootstrapMessageMatchesPlaybackAndStatusCommands() {
    PircbotxPrivateConversationSupport support =
        new PircbotxPrivateConversationSupport(new PircbotxConnectionState("libera"));

    assertTrue(support.shouldSuppressSelfBootstrapMessage(true, "*playback", "play * 19"));
    assertTrue(support.shouldSuppressSelfBootstrapMessage(true, "*status", "ListNetworks"));
    assertFalse(support.shouldSuppressSelfBootstrapMessage(true, "alice", "hello"));
    assertFalse(support.shouldSuppressSelfBootstrapMessage(false, "*playback", "play * 19"));
  }
}
