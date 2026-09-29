package cafe.woden.ircclient.logging.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.adapter.IrcChatHistoryPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcCurrentNickPortAdapter;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import org.junit.jupiter.api.Test;

class RemoteOnlyChatHistoryServiceTest {

  private final IrcClientService irc = mock(IrcClientService.class);
  private final IrcBouncerPlaybackPort bouncerPlayback = mock(IrcBouncerPlaybackPort.class);
  private final ChatHistoryBatchBus batchBus = mock(ChatHistoryBatchBus.class);
  private final ChatHistoryTranscriptPort transcripts = mock(ChatHistoryTranscriptPort.class);
  private final Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport =
      mock(Ircv3ChatHistoryFeatureSupport.class);
  private final ExecutorService exec = mock(ExecutorService.class);

  @Test
  void canReloadRecentUsesRemoteHistoryAvailabilitySupport() {
    RemoteOnlyChatHistoryService service =
        new RemoteOnlyChatHistoryService(
            new IrcChatHistoryPortAdapter(irc),
            new IrcCurrentNickPortAdapter(irc),
            bouncerPlayback,
            batchBus,
            null,
            transcripts,
            chatHistoryFeatureSupport,
            exec);
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(chatHistoryFeatureSupport.isRemoteHistoryAvailable("libera")).thenReturn(true);

    assertTrue(service.canReloadRecent(chan));
    assertFalse(service.canReloadRecent(new TargetRef("libera", "status")));
  }

  @Test
  void canLoadOlderUsesRemoteHistoryAvailabilitySupport() throws Exception {
    RemoteOnlyChatHistoryService service =
        new RemoteOnlyChatHistoryService(
            new IrcChatHistoryPortAdapter(irc),
            new IrcCurrentNickPortAdapter(irc),
            bouncerPlayback,
            batchBus,
            null,
            transcripts,
            chatHistoryFeatureSupport,
            exec);
    TargetRef chan = new TargetRef("libera", "#ircafe");
    DefaultStyledDocument doc = new DefaultStyledDocument();
    doc.insertString(0, "hello", null);

    when(chatHistoryFeatureSupport.isRemoteHistoryAvailable("libera")).thenReturn(true);
    when(transcripts.document(chan)).thenReturn(doc);

    assertTrue(service.canLoadOlder(chan));
  }

  @Test
  void pagesHistoryOffEdtAndPreservesOrderingDirectionAndCursor() throws Exception {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      RemoteOnlyChatHistoryService service =
          new RemoteOnlyChatHistoryService(
              new IrcChatHistoryPortAdapter(irc),
              new IrcCurrentNickPortAdapter(irc),
              bouncerPlayback,
              batchBus,
              null,
              transcripts,
              chatHistoryFeatureSupport,
              worker);
      TargetRef target = new TargetRef("libera", "#ircafe");
      when(transcripts.earliestTimestampEpochMs(target)).thenReturn(OptionalLong.of(3000L));
      when(irc.currentNick("libera")).thenReturn(Optional.of("ALICE"));
      when(chatHistoryFeatureSupport.isAvailable("libera")).thenReturn(true);
      var firstBatch = new CompletableFuture<ChatHistoryBatchBus.BatchEvent>();
      var lastBatch = new CompletableFuture<ChatHistoryBatchBus.BatchEvent>();
      when(batchBus.awaitNext(eq("libera"), eq("#ircafe"), any()))
          .thenReturn(firstBatch, lastBatch);
      AtomicBoolean sentOnEdt = new AtomicBoolean(true);
      when(irc.requestChatHistoryBefore("libera", "#ircafe", 3000L, 2))
          .thenReturn(
              Completable.fromAction(
                  () -> {
                    sentOnEdt.set(SwingUtilities.isEventDispatchThread());
                    firstBatch.complete(
                        new ChatHistoryBatchBus.BatchEvent(
                            "libera",
                            "#ircafe",
                            "batch-1",
                            List.of(
                                new ChatHistoryEntry(
                                    Instant.ofEpochMilli(2000L),
                                    ChatHistoryEntry.Kind.PRIVMSG,
                                    "#ircafe",
                                    "bob",
                                    "newer"),
                                new ChatHistoryEntry(
                                    Instant.ofEpochMilli(1000L),
                                    ChatHistoryEntry.Kind.PRIVMSG,
                                    "#ircafe",
                                    "alice",
                                    "older")),
                            1000L,
                            2000L));
                  }));
      when(irc.requestChatHistoryBefore("libera", "#ircafe", 999L, 2))
          .thenReturn(
              Completable.fromAction(
                  () ->
                      lastBatch.complete(
                          new ChatHistoryBatchBus.BatchEvent(
                              "libera", "#ircafe", "batch-2", List.of(), 0, 0))));

      PageReply first = loadFromEdt(service, target);
      assertFalse(sentOnEdt.get());
      assertTrue(first.callbackOnEdt());
      assertEquals(
          List.of("older", "newer"),
          first.result().linesOldestFirst().stream().map(line -> line.text()).toList());
      assertEquals(
          List.of(LogDirection.OUT, LogDirection.IN),
          first.result().linesOldestFirst().stream().map(line -> line.direction()).toList());
      assertEquals(new LogCursor(999L, 0), first.result().newOldestCursor());
      assertTrue(first.result().hasMore());
      var order = inOrder(batchBus, irc);
      order.verify(batchBus).awaitNext(eq("libera"), eq("#ircafe"), any());
      order.verify(irc).requestChatHistoryBefore("libera", "#ircafe", 3000L, 2);

      worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
      PageReply last = loadFromEdt(service, target);
      assertTrue(last.callbackOnEdt());
      assertTrue(last.result().linesOldestFirst().isEmpty());
      assertFalse(last.result().hasMore());
      assertEquals(new LogCursor(999L, 0), last.result().newOldestCursor());
    } finally {
      worker.shutdownNow();
      worker.awaitTermination(3, TimeUnit.SECONDS);
    }
  }

  private static PageReply loadFromEdt(RemoteOnlyChatHistoryService service, TargetRef target)
      throws Exception {
    CompletableFuture<PageReply> reply = new CompletableFuture<>();
    SwingUtilities.invokeAndWait(
        () ->
            service.loadOlder(
                target,
                2,
                result ->
                    reply.complete(new PageReply(result, SwingUtilities.isEventDispatchThread()))));
    return reply.get(5, TimeUnit.SECONDS);
  }

  private record PageReply(LoadOlderResult result, boolean callbackOnEdt) {}
}
