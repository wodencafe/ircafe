package cafe.woden.ircclient.irc.pircbotx.emit;

import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.chatHistoryBatches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.ircv3.spi.*;
import cafe.woden.ircclient.irc.playback.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PircbotxChatHistoryBatchCollectorTest {

  @Test
  void nativePlaybackDiagnosticsCountMessagesWithoutCapturingOrLoggingTheirContent() {
    List<ServerIrcEvent> events = new ArrayList<>();
    var collector = chatHistoryBatches("znc", events::add);
    try (var logs = new DiagnosticLogs()) {
      collector.handleBatchControlLine(":znc BATCH +replay znc.in/playback ##Llamas");
      assertFalse(
          collector.appendIfActive(
              "replay",
              ChatHistoryEntry.Kind.PRIVMSG,
              Instant.parse("2026-10-02T22:39:00Z"),
              "##Llamas",
              "alice",
              "private-test-content",
              "msg-1",
              Map.of("batch", "replay")));
      assertFalse(
          collector.appendIfActive(
              "replay",
              ChatHistoryEntry.Kind.NOTICE,
              Instant.parse("2026-10-02T22:25:00Z"),
              "status",
              "bob",
              "private-test-content",
              "msg-2",
              Map.of("batch", "replay")));
      collector.handleBatchControlLine(":znc BATCH -replay");
      String capture = logs.text();
      assertTrue(
          capture.contains(
              "target=##Llamas observedMessages=2 earliest=2026-10-02T22:25:00Z latest=2026-10-02T22:39:00Z"));
      assertFalse(capture.contains("private-test-content"));
      assertFalse(capture.contains("alice"));
      assertTrue(capture.contains("target=##Llamas kind=NOTICE"));
      assertFalse(capture.contains("target=status"));
      assertTrue(events.isEmpty(), "native playback must still use the ordinary message path");
    }
  }

  @Test
  void nativePlaybackDiagnosticsReportEmptyBatchesAndClearUnfinishedBatches() {
    var collector = chatHistoryBatches("znc", ignored -> {});
    try (var logs = new DiagnosticLogs()) {
      collector.handleBatchControlLine(":znc BATCH +empty znc.in/playback ##Llamas");
      collector.handleBatchControlLine(":znc BATCH -empty");
      assertTrue(logs.text().contains("observedMessages=0 earliest=null latest=null"));
      collector.handleBatchControlLine(":znc BATCH +unfinished znc.in/playback ##Llamas");
      collector.clear();
      collector.handleBatchControlLine(":znc BATCH -unfinished");
      assertTrue(logs.text().contains("cleared unfinishedBatches=1"));
      assertFalse(logs.text().contains("batch ended id=unfinished"));
    }
  }

  @Test
  void nativePlaybackDiagnosticsEvictUnfinishedBatchesAtTheirLimit() {
    var collector = chatHistoryBatches("znc", ignored -> {});
    try (var logs = new DiagnosticLogs()) {
      for (int i = 0; i < 65; i++) {
        collector.handleBatchControlLine(":znc BATCH +b" + i + " znc.in/playback ##Llamas");
      }
      collector.handleBatchControlLine(":znc BATCH -b0");
      collector.clear();
      assertTrue(logs.text().contains("evicted unfinished batch id=b0"));
      assertTrue(logs.text().contains("cleared unfinishedBatches=64"));
      assertFalse(logs.text().contains("batch ended id=b0 "));
    }
  }

  private static final class DiagnosticLogs implements AutoCloseable {
    private final Logger logger =
        (Logger) LoggerFactory.getLogger(PircbotxChatHistoryBatchCollector.class);
    private final Level previousLevel = logger.getLevel();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private DiagnosticLogs() {
      appender.start();
      logger.addAppender(appender);
      logger.setLevel(Level.DEBUG);
    }

    private String text() {
      return appender.list.stream()
          .map(ILoggingEvent::getFormattedMessage)
          .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Override
    public void close() {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
      appender.stop();
    }
  }

  @Test
  void appendIfActiveBuffersEntriesUntilBatchEnds() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChatHistoryBatchCollector collector = chatHistoryBatches("libera", events::add);

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
  void runtimeProvidersOwnBatchControlAndReferenceInterpretation() {
    Ircv3InboundCommandSignalProvider commandProvider =
        new Ircv3InboundCommandSignalProvider() {
          @Override
          public String providerId() {
            return "custom-history";
          }

          @Override
          public Set<Ircv3InboundCommandOperation> inboundCommandOperations() {
            return Set.of(Ircv3InboundCommandOperation.HISTORY_BATCH_CONTROL);
          }

          @Override
          public List<Ircv3InboundCommandSignal> parse(
              Ircv3InboundCommandOperation operation, Ircv3InboundCommandRequest request) {
            if (request.rawLine().contains("end")) {
              return List.of(new Ircv3InboundCommandSignal.HistoryBatchEnded("runtime-1"));
            }
            return List.of(
                new Ircv3InboundCommandSignal.HistoryBatchStarted(
                    "runtime-1", "chathistory", "#runtime"));
          }
        };
    Ircv3InboundTagSignalProvider tagProvider =
        new Ircv3InboundTagSignalProvider() {
          @Override
          public String providerId() {
            return "custom-history";
          }

          @Override
          public Set<Ircv3InboundTagOperation> inboundTagOperations() {
            return Set.of(Ircv3InboundTagOperation.HISTORY_BATCH_REFERENCE);
          }

          @Override
          public List<Ircv3InboundTagSignal> parse(
              Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
            return List.of(
                Ircv3InboundTagSignal.of(
                    Ircv3InboundTagSignalType.HISTORY_BATCH_REFERENCE, "runtime-1"));
          }
        };

    List<ServerIrcEvent> events = new ArrayList<>();
    Ircv3RuntimeTestFixtures.Runtime runtime = Ircv3RuntimeTestFixtures.runtime();
    PircbotxChatHistoryBatchCollector collector =
        new PircbotxChatHistoryBatchCollector(
            "libera",
            events::add,
            Ircv3InboundCommandSignalRuntimeCatalog.fromProviders(List.of(commandProvider)),
            Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of(tagProvider)),
            runtime.serverTime(),
            runtime.messageTags());

    assertEquals(Optional.of("runtime-1"), collector.batchId(Map.of("batch", "ignored")));
    assertTrue(collector.handleBatchControlLine("custom start"));
    assertTrue(
        collector.appendIfActive(
            "runtime-1",
            ChatHistoryEntry.Kind.NOTICE,
            Instant.EPOCH,
            "#fallback",
            "server",
            "history",
            "msg-runtime",
            Map.of()));
    assertTrue(collector.handleBatchControlLine("custom end"));

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.getFirst().event());
    assertEquals("#runtime", batch.target());
    assertEquals("runtime-1", batch.batchId());
    assertEquals("history", batch.entries().getFirst().text());
  }

  @Test
  void maybeCaptureUnknownLineUsesBatchTagAndBuffersPrivmsgEntries() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChatHistoryBatchCollector collector = chatHistoryBatches("libera", events::add);

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
