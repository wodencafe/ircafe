package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.pircbotx.*;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputIRC;

class PircbotxShutdownSupportTest {

  @Test
  void shutdownConnectionQuitsAndClosesConnectedBot() {
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    PircbotxShutdownSupport support = new PircbotxShutdownSupport(timers);
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.isConnected()).thenReturn(true);
    when(bot.sendIRC()).thenReturn(outputIrc);
    connection.setBot(bot);
    connection.beginLagProbe("tok", 123L);

    support.shutdownConnection(connection, "bye");

    verify(timers).cancelReconnect(connection);
    verify(timers).stopHeartbeat(connection);
    verify(outputIrc).quitServer("bye");
    verify(bot).stopBotReconnect();
    verify(bot).close();
    assertTrue(connection.manualDisconnectRequested());
    assertNull(connection.currentBot());
    assertEquals("", connection.currentLagProbeToken());
  }

  @Test
  void shutdownConnectionSkipsQuitWhenBotIsNotConnected() {
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    PircbotxShutdownSupport support = new PircbotxShutdownSupport(timers);
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.isConnected()).thenReturn(false);
    when(bot.sendIRC()).thenReturn(outputIrc);
    connection.setBot(bot);

    support.shutdownConnection(connection, "bye");

    verify(outputIrc, never()).quitServer("bye");
    verify(bot).stopBotReconnect();
    verify(bot).close();
    assertNull(connection.currentBot());
  }
}
