package cafe.woden.ircclient.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.core.MediatorHistoryIngestOrchestrator;
import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediatorHistoryIngestOrchestratorTest {

  private final UiPort ui = mock(UiPort.class);
  private final ChatHistoryIngestionPort ingestionPort = mock(ChatHistoryIngestionPort.class);
  private final ChatHistoryIngestEventsPort ingestEventsPort =
      mock(ChatHistoryIngestEventsPort.class);
  private final ChatHistoryBatchEventsPort batchEventsPort = mock(ChatHistoryBatchEventsPort.class);
  private final ZncPlaybackEventsPort playbackEventsPort = mock(ZncPlaybackEventsPort.class);
  private final ChatHistoryRequestRoutingState routingState =
      mock(ChatHistoryRequestRoutingState.class);
  private final ChatTranscriptHistoryPort transcripts = mock(ChatTranscriptHistoryPort.class);
  private final IrcClientService irc = mock(IrcClientService.class);

  private final MediatorHistoryIngestOrchestrator orchestrator =
      new MediatorHistoryIngestOrchestrator(
          ui,
          ingestionPort,
          ingestEventsPort,
          batchEventsPort,
          playbackEventsPort,
          routingState,
          transcripts,
          irc);

  @BeforeEach
  void setUp() {
    doAnswer(
            inv -> {
              @SuppressWarnings("unchecked")
              Consumer<ChatHistoryIngestionPort.IngestResult> cb =
                  inv.getArgument(4, Consumer.class);
              if (cb != null) {
                cb.accept(new ChatHistoryIngestionPort.IngestResult(true, null, 2, 2, 0L, 0L));
              }
              return null;
            })
        .when(ingestionPort)
        .ingestAsync(anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void rendersChatHistoryImmediatelyWhenPendingRequestExists() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(routingState.consumeIfFresh(eq("libera"), eq("#ircafe"), any(Duration.class)))
        .thenReturn(
            new ChatHistoryRequestRoutingState.PendingRequest(
                chan,
                Instant.now(),
                40,
                "timestamp=now",
                ChatHistoryRequestRoutingState.QueryMode.BEFORE));
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(transcripts.loadOlderInsertOffset(chan)).thenReturn(0);

    Instant t1 = Instant.parse("2026-02-16T00:00:01Z");
    Instant t2 = Instant.parse("2026-02-16T00:00:02Z");
    when(transcripts.insertChatFromHistoryAt(chan, 0, "alice", "hello", false, t1.toEpochMilli()))
        .thenReturn(1);
    when(transcripts.insertActionFromHistoryAt(chan, 1, "me", "waves", true, t2.toEpochMilli()))
        .thenReturn(2);

    ChatHistoryEntry chat =
        new ChatHistoryEntry(t1, ChatHistoryEntry.Kind.PRIVMSG, "#ircafe", "alice", "hello");
    ChatHistoryEntry action =
        new ChatHistoryEntry(t2, ChatHistoryEntry.Kind.ACTION, "#ircafe", "me", "waves");
    IrcEvent.ChatHistoryBatchReceived ev =
        new IrcEvent.ChatHistoryBatchReceived(
            Instant.now(), "#ircafe", "batch-1", List.of(action, chat, chat));

    orchestrator.onChatHistoryBatchReceived("libera", ev);

    verify(transcripts).beginHistoryInsertBatch(chan);
    verify(transcripts)
        .insertChatFromHistoryAt(chan, 0, "alice", "hello", false, t1.toEpochMilli());
    verify(transcripts).insertActionFromHistoryAt(chan, 1, "me", "waves", true, t2.toEpochMilli());
    verify(transcripts).endHistoryInsertBatch(chan);
    verify(ui, atLeastOnce())
        .appendStatus(
            eq(chan),
            eq("(history)"),
            argThat(msg -> msg != null && msg.contains("Displayed 2 now")));
  }

  @Test
  void rendersLatestHistoryByAppendingToTranscript() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(routingState.consumeIfFresh(eq("libera"), eq("#ircafe"), any(Duration.class)))
        .thenReturn(
            new ChatHistoryRequestRoutingState.PendingRequest(
                chan, Instant.now(), 20, "*", ChatHistoryRequestRoutingState.QueryMode.LATEST));
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));

    Instant t1 = Instant.parse("2026-02-16T01:00:01Z");
    Instant t2 = Instant.parse("2026-02-16T01:00:02Z");
    ChatHistoryEntry chat =
        new ChatHistoryEntry(t1, ChatHistoryEntry.Kind.PRIVMSG, "#ircafe", "alice", "hello");
    ChatHistoryEntry action =
        new ChatHistoryEntry(t2, ChatHistoryEntry.Kind.ACTION, "#ircafe", "me", "waves");
    IrcEvent.ChatHistoryBatchReceived ev =
        new IrcEvent.ChatHistoryBatchReceived(
            Instant.now(), "#ircafe", "batch-latest", List.of(chat, action));

    orchestrator.onChatHistoryBatchReceived("libera", ev);

    verify(transcripts, never()).loadOlderInsertOffset(any());
    verify(transcripts).appendChatFromHistory(chan, "alice", "hello", false, t1.toEpochMilli());
    verify(transcripts).appendActionFromHistory(chan, "me", "waves", true, t2.toEpochMilli());
  }

  @Test
  void keepsBatchAsPersistOnlyWhenNoPendingRequestExists() {
    when(routingState.consumeIfFresh(eq("libera"), eq("#ircafe"), any(Duration.class)))
        .thenReturn(null);

    ChatHistoryEntry chat =
        new ChatHistoryEntry(
            Instant.parse("2026-02-16T00:00:01Z"),
            ChatHistoryEntry.Kind.PRIVMSG,
            "#ircafe",
            "alice",
            "hello");
    IrcEvent.ChatHistoryBatchReceived ev =
        new IrcEvent.ChatHistoryBatchReceived(Instant.now(), "#ircafe", "batch-2", List.of(chat));

    orchestrator.onChatHistoryBatchReceived("libera", ev);

    TargetRef chan = new TargetRef("libera", "#ircafe");
    verify(transcripts, never()).beginHistoryInsertBatch(any());
    verify(ui)
        .appendStatus(
            chan,
            "(history)",
            "Received 1 history lines (batch batch-2). Persisting for scrollback.");
    verify(ui, atLeastOnce()).appendStatus(eq(chan), eq("(history)"), contains("Persisted"));
  }

  @Test
  void zncPlaybackStatusGoesToTargetAndUsesUnifiedHistoryTag() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(routingState.consumeIfFresh(eq("libera"), eq("#ircafe"), any(Duration.class)))
        .thenReturn(null);
    List<ChatHistoryEntry> entries =
        List.of(
            new ChatHistoryEntry(
                Instant.parse("2026-02-16T00:00:01Z"),
                ChatHistoryEntry.Kind.PRIVMSG,
                "#ircafe",
                "alice",
                "one"),
            new ChatHistoryEntry(
                Instant.parse("2026-02-16T00:00:02Z"),
                ChatHistoryEntry.Kind.PRIVMSG,
                "#ircafe",
                "bob",
                "two"),
            new ChatHistoryEntry(
                Instant.parse("2026-02-16T00:00:03Z"),
                ChatHistoryEntry.Kind.PRIVMSG,
                "#ircafe",
                "carol",
                "three"));
    IrcEvent.ZncPlaybackBatchReceived ev =
        new IrcEvent.ZncPlaybackBatchReceived(
            Instant.now(),
            "#ircafe",
            Instant.parse("2026-02-15T23:59:00Z"),
            Instant.parse("2026-02-16T00:04:00Z"),
            entries);

    orchestrator.onZncPlaybackBatchReceived("libera", ev);

    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(history)"),
            argThat(msg -> msg != null && msg.contains("Received 3 playback history lines")));
    verify(ui, never()).appendStatus(eq(status), eq("(history)"), anyString());
  }

  @Test
  void skipsRenderingRecentDuplicateHistoryLinesAcrossBatches() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    ChatHistoryRequestRoutingState.PendingRequest pending =
        new ChatHistoryRequestRoutingState.PendingRequest(
            chan, Instant.now(), 20, "*", ChatHistoryRequestRoutingState.QueryMode.LATEST);
    when(routingState.consumeIfFresh(eq("libera"), eq("#ircafe"), any(Duration.class)))
        .thenReturn(pending, pending);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));

    Instant t = Instant.parse("2026-02-16T02:00:01Z");
    ChatHistoryEntry chat =
        new ChatHistoryEntry(t, ChatHistoryEntry.Kind.PRIVMSG, "#ircafe", "alice", "hello");
    IrcEvent.ChatHistoryBatchReceived ev1 =
        new IrcEvent.ChatHistoryBatchReceived(Instant.now(), "#ircafe", "batch-a", List.of(chat));
    IrcEvent.ChatHistoryBatchReceived ev2 =
        new IrcEvent.ChatHistoryBatchReceived(Instant.now(), "#ircafe", "batch-b", List.of(chat));

    orchestrator.onChatHistoryBatchReceived("libera", ev1);
    orchestrator.onChatHistoryBatchReceived("libera", ev2);

    verify(transcripts, times(1))
        .appendChatFromHistory(chan, "alice", "hello", false, t.toEpochMilli());
  }
}
