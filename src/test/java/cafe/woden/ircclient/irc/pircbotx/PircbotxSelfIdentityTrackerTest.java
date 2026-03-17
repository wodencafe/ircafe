package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.User;

class PircbotxSelfIdentityTrackerTest {

  @Test
  void nickMatchesSelfFallsBackToBotNickAndRemembersHint() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxSelfIdentityTracker tracker = new PircbotxSelfIdentityTracker(conn);
    PircBotX bot = botWithUserBotNick("me");

    assertTrue(tracker.nickMatchesSelf(bot, "Me"));
    assertEquals("me", conn.selfNickHint.get());
  }

  @Test
  void resolveSelfNickIgnoresInvalidHintAndUsesBotNick() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.selfNickHint.set("#ircafe");
    PircbotxSelfIdentityTracker tracker = new PircbotxSelfIdentityTracker(conn);
    PircBotX bot = mock(PircBotX.class);
    when(bot.getNick()).thenReturn("fallbackNick");

    assertEquals("fallbackNick", tracker.resolveSelfNick(bot));
    assertEquals("fallbackNick", conn.selfNickHint.get());
  }

  @Test
  void isSelfEchoedMatchesUserBotNickCaseInsensitively() {
    assertTrue(PircbotxSelfIdentityTracker.isSelfEchoed(botWithUserBotNick("Woden"), "woden"));
  }

  private static PircBotX botWithUserBotNick(String nick) {
    PircBotX bot = mock(PircBotX.class);
    User userBot = mock(User.class);
    when(userBot.getNick()).thenReturn(nick);
    when(bot.getUserBot()).thenReturn(userBot);
    return bot;
  }
}
