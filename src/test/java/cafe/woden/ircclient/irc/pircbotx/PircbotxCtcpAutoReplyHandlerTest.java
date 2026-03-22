package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.output.OutputIRC;

class PircbotxCtcpAutoReplyHandlerTest {

  @Test
  void handleIfPresentReturnsFalseForNonCtcpMessage() {
    PircbotxCtcpAutoReplyHandler handler = newHandler(true, true, true, true);

    assertFalse(handler.handleIfPresent(mock(PircBotX.class), "alice", "hello"));
  }

  @Test
  void handleIfPresentSendsVersionReplyWhenEnabled() {
    PircbotxCtcpAutoReplyHandler handler = newHandler(true, true, true, true);
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);

    assertTrue(handler.handleIfPresent(bot, "alice", "\u0001VERSION\u0001"));

    verify(outputIrc).notice("alice", "\u0001VERSION IRCafe test\u0001");
  }

  @Test
  void handleIfPresentTreatsDisabledPingReplyAsHandledWithoutSending() {
    PircbotxCtcpAutoReplyHandler handler = newHandler(false, true, false, true);
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);

    assertTrue(handler.handleIfPresent(bot, "alice", "\u0001PING 123\u0001"));

    verify(outputIrc, never()).notice("alice", "\u0001PING 123\u0001");
  }

  @Test
  void handleIfPresentDropsSelfEcho() {
    PircbotxCtcpAutoReplyHandler handler = newHandler(true, true, true, true);
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    User userBot = mock(User.class);
    when(bot.sendIRC()).thenReturn(outputIrc);
    when(bot.getNick()).thenReturn("me");
    when(bot.getUserBot()).thenReturn(userBot);
    when(userBot.getNick()).thenReturn("me");

    assertTrue(handler.handleIfPresent(bot, "me", "\u0001VERSION\u0001"));

    verify(outputIrc, never()).notice("me", "\u0001VERSION IRCafe test\u0001");
  }

  private static PircbotxCtcpAutoReplyHandler newHandler(
      boolean enabled, boolean versionEnabled, boolean pingEnabled, boolean timeEnabled) {
    CtcpReplyRuntimeConfigPort runtimeConfig = mock(CtcpReplyRuntimeConfigPort.class);
    when(runtimeConfig.readCtcpAutoRepliesEnabled(true)).thenReturn(enabled);
    when(runtimeConfig.readCtcpAutoReplyVersionEnabled(true)).thenReturn(versionEnabled);
    when(runtimeConfig.readCtcpAutoReplyPingEnabled(true)).thenReturn(pingEnabled);
    when(runtimeConfig.readCtcpAutoReplyTimeEnabled(true)).thenReturn(timeEnabled);
    return new PircbotxCtcpAutoReplyHandler("IRCafe test", runtimeConfig);
  }
}
