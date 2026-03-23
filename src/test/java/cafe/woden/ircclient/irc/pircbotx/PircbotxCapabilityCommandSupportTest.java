package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryCommandBuilder;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputRaw;

class PircbotxCapabilityCommandSupportTest {

  private final PircbotxCapabilityCommandSupport support = new PircbotxCapabilityCommandSupport();

  @Test
  void sendTypingSendsNormalizedTagmsgWhenAvailable() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);
    connection.botRef.set(bot);
    connection.messageTagsCapAcked.set(true);
    connection.typingCapAcked.set(true);

    support.sendTyping("libera", connection, "#ircafe", "composing");

    verify(outputRaw).rawLine("@+typing=active TAGMSG #ircafe");
  }

  @Test
  void sendTypingExplainsCapabilityFailure() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.botRef.set(mock(PircBotX.class));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> support.sendTyping("libera", connection, "#ircafe", "active"));

    assertEquals(
        "Typing indicators not available (requires message-tags and server allowing +typing) (message-tags not negotiated): libera",
        ex.getMessage());
  }

  @Test
  void sendReadMarkerFormatsTimestamp() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);
    connection.botRef.set(bot);
    connection.readMarkerCapAcked.set(true);

    support.sendReadMarker("libera", connection, "#ircafe", Instant.parse("2026-03-23T12:05:00Z"));

    verify(outputRaw).rawLine("MARKREAD #ircafe timestamp=2026-03-23T12:05:00.000Z");
  }

  @Test
  void requestChatHistoryLatestSendsBuiltCommand() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);
    connection.botRef.set(bot);
    connection.chatHistoryCapAcked.set(true);
    connection.batchCapAcked.set(true);

    support.requestChatHistoryLatest("libera", connection, "#ircafe", "timestamp=123", 50);

    verify(outputRaw)
        .rawLine(Ircv3ChatHistoryCommandBuilder.buildLatest("#ircafe", "timestamp=123", 50));
  }

  @Test
  void requestChatHistoryBeforeRequiresBatchCapability() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.chatHistoryCapAcked.set(true);

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                support.requestChatHistoryBefore(
                    "libera", connection, "#ircafe", "timestamp=123", 25));

    assertEquals("CHATHISTORY requires IRCv3 batch to be negotiated: libera", ex.getMessage());
  }
}
