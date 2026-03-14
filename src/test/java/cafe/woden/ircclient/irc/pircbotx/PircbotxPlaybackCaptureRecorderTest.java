package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PircbotxPlaybackCaptureRecorderTest {

  @Test
  void maybeCaptureReturnsFalseWithoutActiveCapture() {
    PircbotxPlaybackCaptureRecorder recorder =
        new PircbotxPlaybackCaptureRecorder(new PircbotxConnectionState("libera"));

    boolean captured =
        recorder.maybeCapture(
            "#ircafe",
            Instant.now(),
            ChatHistoryEntry.Kind.PRIVMSG,
            "alice",
            "hello",
            "msg-1",
            Map.of());

    assertFalse(captured);
  }

  @Test
  void maybeCaptureAddsEntryToActivePlaybackWindow() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxPlaybackCaptureRecorder recorder = new PircbotxPlaybackCaptureRecorder(conn);
    List<ServerIrcEvent> events = new ArrayList<>();
    conn.zncPlaybackCapture.start(
        "libera", "#ircafe", Instant.now().minusSeconds(60), null, events::add);

    boolean captured =
        recorder.maybeCapture(
            "#ircafe",
            Instant.now().minusSeconds(5),
            ChatHistoryEntry.Kind.PRIVMSG,
            "alice",
            "replayed line",
            "msg-2",
            Map.of());
    conn.zncPlaybackCapture.completeActive("test");

    assertTrue(captured);
    assertEquals(1, events.size());
    IrcEvent.ZncPlaybackBatchReceived batch =
        assertInstanceOf(IrcEvent.ZncPlaybackBatchReceived.class, events.getFirst().event());
    assertEquals("#ircafe", batch.target());
    assertEquals(1, batch.entries().size());
    assertEquals("replayed line", batch.entries().getFirst().text());
  }
}
