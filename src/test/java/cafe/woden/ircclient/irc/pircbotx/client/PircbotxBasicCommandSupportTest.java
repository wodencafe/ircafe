package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputIRC;
import org.pircbotx.output.OutputRaw;

class PircbotxBasicCommandSupportTest {

  private final PircbotxBasicCommandSupport support = new PircbotxBasicCommandSupport();

  @Test
  void changeNickSanitizesAndDelegatesToOutputIrc() {
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);

    support.changeNick(bot, " Alice ");

    verify(outputIrc).changeNick("Alice");
  }

  @Test
  void setAwaySendsBareAwayWhenBlank() {
    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);

    support.setAway(bot, "  ");

    verify(outputRaw).rawLine("AWAY");
  }

  @Test
  void setAwayRejectsCrLf() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> support.setAway(mock(PircBotX.class), "gone\rnow"));

    assertEquals("away message contains CR/LF", ex.getMessage());
  }

  @Test
  void partChannelIncludesOptionalReason() {
    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);

    support.partChannel(bot, "#ircafe", "later");

    verify(outputRaw).rawLine("PART #ircafe :later");
  }

  @Test
  void sendRawTrimsAndSkipsBlankLines() {
    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);

    support.sendRaw(bot, "  WHO #ircafe  ");
    support.sendRaw(bot, "   ");

    verify(outputRaw).rawLine("WHO #ircafe");
    verify(outputRaw, never()).rawLine("");
  }
}
