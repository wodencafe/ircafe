package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputRaw;

class PircbotxMultilineMessageSupportTest {

  @Test
  void singleLinePrivmsgUsesSingleRawLine() {
    PircbotxMultilineMessageSupport support = new PircbotxMultilineMessageSupport();
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);

    support.send(bot, connection, "libera", "#ircafe", "hello", false);

    verify(outputRaw).rawLine("PRIVMSG #ircafe :hello");
  }

  @Test
  void multilineMessageRequiresNegotiatedCapability() {
    PircbotxMultilineMessageSupport support = new PircbotxMultilineMessageSupport();
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);

    assertThrows(
        IllegalArgumentException.class,
        () -> support.send(bot, connection, "libera", "#ircafe", "hello\nworld", false));
  }
}
