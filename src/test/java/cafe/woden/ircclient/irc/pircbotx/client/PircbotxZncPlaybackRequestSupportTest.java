package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputIRC;

class PircbotxZncPlaybackRequestSupportTest {

  @Test
  void requestPlaybackRangeStartsCaptureAndSendsPlaybackCommand() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    PircbotxZncPlaybackRequestSupport support = new PircbotxZncPlaybackRequestSupport(bus);
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);
    connection.setBot(bot);
    connection.setZncPlaybackCapAcked(true);

    support.requestPlaybackRange(
        "libera", connection, "#ircafe", Instant.ofEpochSecond(10), Instant.ofEpochSecond(20));

    verify(outputIrc).message("*playback", "play #ircafe 10 20");
    assertTrue(connection.isZncPlaybackCaptureActive());
    assertEquals(Optional.of("#ircafe"), connection.activeZncPlaybackCaptureTarget());
    connection.cancelZncPlaybackCapture("test");
  }

  @Test
  void requestPlaybackRangeCancelsCaptureWhenSendFails() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    PircbotxZncPlaybackRequestSupport support = new PircbotxZncPlaybackRequestSupport(bus);
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputIRC outputIrc = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(outputIrc);
    doThrow(new RuntimeException("boom")).when(outputIrc).message("*playback", "play #ircafe 10");
    connection.setBot(bot);
    connection.setZncPlaybackCapAcked(true);

    assertThrows(
        RuntimeException.class,
        () ->
            support.requestPlaybackRange(
                "libera", connection, "#ircafe", Instant.ofEpochSecond(10), null));

    assertFalse(connection.isZncPlaybackCaptureActive());
  }

  @Test
  void requestPlaybackRangeRequiresNegotiatedCapability() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    PircbotxZncPlaybackRequestSupport support = new PircbotxZncPlaybackRequestSupport(bus);
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                support.requestPlaybackRange(
                    "libera", connection, "#ircafe", Instant.ofEpochSecond(10), null));

    assertEquals("ZNC playback not negotiated (znc.in/playback): libera", ex.getMessage());
  }
}
