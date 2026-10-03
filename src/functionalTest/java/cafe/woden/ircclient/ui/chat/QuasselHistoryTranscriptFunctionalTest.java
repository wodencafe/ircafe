package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.core.MediatorHistoryIngestOrchestrator;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.ChatHistoryEntry.Kind;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort.QueryMode;
import cafe.woden.ircclient.testutil.SwingComponentSnapshotSupport;
import cafe.woden.ircclient.ui.WrapTextPane;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import java.awt.geom.Rectangle2D;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

/** Exercises core-style native IDs through history routing and the real Swing transcript. */
class QuasselHistoryTranscriptFunctionalTest {
  private static final TargetRef FIRST = new TargetRef("quassel", "#shared{net:first}");
  private static final TargetRef SECOND = new TargetRef("quassel", "#shared{net:second}");

  @Test
  void overlappingBacklogPagesAndLiveMessagesRenderOnceInChronologicalOrder() throws Exception {
    onEdt(
        () -> {
          try (Fixture fixture = new Fixture()) {
            fixture.live(FIRST, 103, "live boundary");
            List<ChatHistoryEntry> page =
                List.of(
                    entry(FIRST, 103, Kind.PRIVMSG, "replayed live body"),
                    entry(FIRST, 102, Kind.NOTICE, "history notice"),
                    entry(FIRST, 101, Kind.ACTION, "history action"),
                    entry(FIRST, 100, Kind.PRIVMSG, "history chat"),
                    entry(FIRST, 101, Kind.ACTION, "duplicate action body"));
            fixture.history(FIRST, QueryMode.BEFORE, "quassel-backlog-sync-1", page);
            fixture.live(FIRST, 104, "new live message");
            fixture.history(
                FIRST,
                QueryMode.BEFORE,
                "quassel-backlog-sync-2",
                List.of(
                    entry(FIRST, 100, Kind.PRIVMSG, "duplicate earlier body"),
                    entry(FIRST, 99, Kind.PRIVMSG, "older chat"),
                    entry(FIRST, 98, Kind.ACTION, "older action")));
            fixture.history(FIRST, QueryMode.LATEST, "quassel-backlog-sync-3", page);
            fixture.live(FIRST, 103, "duplicate live body");

            String text = fixture.text(FIRST);
            List<String> bodies =
                List.of(
                    "older action",
                    "older chat",
                    "history chat",
                    "history action",
                    "history notice",
                    "live boundary",
                    "new live message");
            for (String body : bodies) {
              assertTrue(text.contains(body), "missing transcript body: " + body);
              assertEquals(text.indexOf(body), text.lastIndexOf(body), "duplicate body: " + body);
            }
            assertFalse(text.contains("duplicate"));
            assertFalse(text.contains("replayed"), "backlog must preserve the existing live line");
            fixture.assertVisibleOrder(FIRST, List.of(98L, 99L, 100L, 101L, 102L, 103L, 104L));
          }
        });
  }

  @Test
  void sameChannelNamesHaveSeparateHistoryRequestsDocumentsAndMessageIds() throws Exception {
    onEdt(
        () -> {
          try (Fixture fixture = new Fixture()) {
            fixture.live(FIRST, 203, "first live");
            fixture.live(SECOND, 203, "second live");
            fixture.remember(FIRST, QueryMode.BEFORE);
            fixture.remember(SECOND, QueryMode.BEFORE);
            fixture.receive(
                SECOND,
                "quassel-backlog-sync-second",
                List.of(
                    entry(SECOND, 202, Kind.PRIVMSG, "second history"),
                    entry(SECOND, 203, Kind.PRIVMSG, "second duplicate")));
            fixture.receive(
                FIRST,
                "quassel-backlog-sync-first",
                List.of(
                    entry(FIRST, 202, Kind.PRIVMSG, "first history"),
                    entry(FIRST, 203, Kind.PRIVMSG, "first duplicate")));

            assertTrue(fixture.text(FIRST).contains("first history"));
            assertTrue(fixture.text(SECOND).contains("second history"));
            assertFalse(fixture.text(FIRST).contains("second"));
            assertFalse(fixture.text(SECOND).contains("first"));
            assertFalse(fixture.text(FIRST).contains("duplicate"));
            assertFalse(fixture.text(SECOND).contains("duplicate"));
            fixture.assertVisibleOrder(FIRST, List.of(202L, 203L));
            fixture.assertVisibleOrder(SECOND, List.of(202L, 203L));
          }
        });
  }

  private static ChatHistoryEntry entry(TargetRef target, long id, Kind kind, String text) {
    return new ChatHistoryEntry(
        Instant.ofEpochSecond(1_700_000_000L + id),
        kind,
        target.target(),
        "alice",
        text,
        Long.toString(id),
        Map.of());
  }

  private static void onEdt(ThrowingRunnable action) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            action.run();
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
        });
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class Fixture implements AutoCloseable {
    private final ChatTranscriptStore store;
    private final ChatHistoryRequestRoutingState requests = new ChatHistoryRequestRoutingState();
    private final MediatorHistoryIngestOrchestrator history;
    private final WrapTextPane pane = new WrapTextPane();

    Fixture() {
      ChatStyles styles = new ChatStyles(null);
      store =
          new ChatTranscriptStore(
              styles,
              new ChatRichTextRenderer(null, null, styles, null),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
      IrcCurrentNickPort nick = mock(IrcCurrentNickPort.class);
      when(nick.currentNick("quassel")).thenReturn(Optional.of("me"));
      history =
          new MediatorHistoryIngestOrchestrator(
              mock(UiPort.class),
              mock(ChatHistoryIngestionPort.class),
              mock(ChatHistoryIngestEventsPort.class),
              mock(ChatHistoryBatchEventsPort.class),
              mock(ZncPlaybackEventsPort.class),
              requests,
              store,
              nick);
      pane.setSize(720, 480);
    }

    void live(TargetRef target, long id, String text) {
      ChatHistoryEntry message = entry(target, id, Kind.PRIVMSG, text);
      store.appendChatAt(
          target,
          message.from(),
          message.text(),
          false,
          message.at().toEpochMilli(),
          message.messageId(),
          message.ircv3Tags());
    }

    void remember(TargetRef target, QueryMode mode) {
      requests.remember(target.serverId(), target.target(), target, 50, "*", Instant.now(), mode);
    }

    void history(TargetRef target, QueryMode mode, String batchId, List<ChatHistoryEntry> entries) {
      remember(target, mode);
      receive(target, batchId, entries);
    }

    void receive(TargetRef target, String batchId, List<ChatHistoryEntry> entries) {
      history.onChatHistoryBatchReceived(
          target.serverId(),
          new IrcEvent.ChatHistoryBatchReceived(Instant.now(), target.target(), batchId, entries));
    }

    String text(TargetRef target) throws Exception {
      StyledDocument document = store.document(target);
      return document.getText(0, document.getLength());
    }

    void assertVisibleOrder(TargetRef target, List<Long> ids) throws Exception {
      pane.setDocument(store.document(target));
      SwingComponentSnapshotSupport.capture(pane);
      int previousOffset = -1;
      double previousY = -1;
      for (long id : ids) {
        int offset = store.messageOffsetById(target, Long.toString(id));
        assertTrue(
            offset > previousOffset, "message IDs must appear in chronological order: " + id);
        Rectangle2D bounds = pane.modelToView2D(offset);
        assertNotNull(bounds, "message must be laid out: " + id);
        assertTrue(bounds.getY() > previousY, "message must occupy a later visible line: " + id);
        previousOffset = offset;
        previousY = bounds.getY();
      }
    }

    @Override
    public void close() {
      pane.setDocument(new DefaultStyledDocument());
      store.closeTarget(FIRST);
      store.closeTarget(SECOND);
      requests.clearServer("quassel");
    }
  }
}
