package cafe.woden.ircclient.logging.history;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.concurrent.ExecutorService;
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
            irc, bouncerPlayback, batchBus, null, transcripts, chatHistoryFeatureSupport, exec);
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(chatHistoryFeatureSupport.isRemoteHistoryAvailable("libera")).thenReturn(true);

    assertTrue(service.canReloadRecent(chan));
    assertFalse(service.canReloadRecent(new TargetRef("libera", "status")));
  }

  @Test
  void canLoadOlderUsesRemoteHistoryAvailabilitySupport() throws Exception {
    RemoteOnlyChatHistoryService service =
        new RemoteOnlyChatHistoryService(
            irc, bouncerPlayback, batchBus, null, transcripts, chatHistoryFeatureSupport, exec);
    TargetRef chan = new TargetRef("libera", "#ircafe");
    DefaultStyledDocument doc = new DefaultStyledDocument();
    doc.insertString(0, "hello", null);

    when(chatHistoryFeatureSupport.isRemoteHistoryAvailable("libera")).thenReturn(true);
    when(transcripts.document(chan)).thenReturn(doc);

    assertTrue(service.canLoadOlder(chan));
  }
}
