package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class PircbotxChatHistoryBatchCollectorTest {

  @Test
  void appendIfActiveBuffersEntriesUntilBatchEnds() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChatHistoryBatchCollector collector =
        new PircbotxChatHistoryBatchCollector("libera", events::add);

    assertTrue(
        collector.handleBatchControlLine(":server.example BATCH +abc draft/chathistory #ircafe"));
    assertTrue(
        collector.appendIfActive(
            "abc",
            ChatHistoryEntry.Kind.PRIVMSG,
            Instant.parse("2026-03-13T12:00:00Z"),
            "#fallback",
            "alice",
            "hello",
            "msg-1",
            Map.of("msgid", "msg-1")));
    assertTrue(collector.handleBatchControlLine(":server.example BATCH -abc"));

    assertEquals(1, events.size());
    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getFirst().event());
    assertEquals("libera", events.getFirst().serverId());
    assertEquals("#ircafe", batch.target());
    assertEquals("abc", batch.batchId());
    assertEquals(1, batch.entries().size());
    assertEquals(ChatHistoryEntry.Kind.PRIVMSG, batch.entries().getFirst().kind());
    assertEquals("alice", batch.entries().getFirst().from());
    assertEquals("hello", batch.entries().getFirst().text());
  }

  @Test
  void maybeCaptureUnknownLineUsesBatchTagAndBuffersPrivmsgEntries() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChatHistoryBatchCollector collector =
        new PircbotxChatHistoryBatchCollector("libera", events::add);

    assertTrue(collector.handleBatchControlLine(":server.example BATCH +hist chathistory #ircafe"));
    assertTrue(
        collector.maybeCaptureUnknownLine(
            "@batch=hist;msgid=znc-1;time=2026-03-13T12:01:00Z "
                + ":alice!u@example PRIVMSG #ircafe :waves",
            ":alice!u@example PRIVMSG #ircafe :waves"));
    assertTrue(collector.handleBatchControlLine(":server.example BATCH -hist"));

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getFirst().event());
    assertEquals(1, batch.entries().size());
    ChatHistoryEntry entry = batch.entries().getFirst();
    assertEquals(ChatHistoryEntry.Kind.PRIVMSG, entry.kind());
    assertEquals("#ircafe", entry.target());
    assertEquals("alice", entry.from());
    assertEquals("waves", entry.text());
    assertEquals("znc-1", entry.messageId());
  }
}
