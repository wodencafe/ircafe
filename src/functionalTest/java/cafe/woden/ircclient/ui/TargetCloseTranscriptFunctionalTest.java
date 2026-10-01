package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.roster.UserhostQueryService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTimestampFormatter;
import io.reactivex.rxjava3.core.Completable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTextPaneUI;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class TargetCloseTranscriptFunctionalTest {

  @ParameterizedTest
  @CsvSource({
    "#channel, true, false",
    "#channel, true, true",
    "#channel, false, false",
    "Alice, false, false"
  })
  void closingActiveTargetDoesNotRebuildStatusTranscriptDuringAppend(
      String targetName, boolean connected, boolean detached) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            verifyClose(targetName, connected, detached);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static void verifyClose(String targetName, boolean connected, boolean detached)
      throws Exception {
    TargetRef target = new TargetRef("libera", targetName);
    TargetRef status = new TargetRef("libera", "status");
    ChatStyles styles = new ChatStyles(null);
    ChatTranscriptStore transcripts =
        new ChatTranscriptStore(
            styles,
            new ChatRichTextRenderer(null, null, styles, null),
            new ChatTimestampFormatter(null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    StyledDocument statusDocument = transcripts.document(status);
    String history = "server history: שלום עולם مرحبا بالعالم\n".repeat(300);
    statusDocument.insertString(0, history, styles.status());
    assertEquals(Boolean.TRUE, statusDocument.getProperty("i18n"));

    WrapTextPane pane = new WrapTextPane();
    CountingTextPaneUI textUi = new CountingTextPaneUI();
    pane.setUI(textUi);
    AtomicInteger rebuildsDuringAppend = new AtomicInteger();
    UiPort ui = mock(UiPort.class);
    doAnswer(
            invocation -> {
              pane.setDocument(transcripts.document(invocation.getArgument(0)));
              return null;
            })
        .when(ui)
        .setChatActiveTarget(any());
    doAnswer(
            invocation -> {
              int before = textUi.rebuilds;
              transcripts.appendStatus(
                  invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
              rebuildsDuringAppend.addAndGet(textUi.rebuilds - before);
              return null;
            })
        .when(ui)
        .appendStatus(any(), anyString(), anyString());
    doAnswer(
            invocation -> {
              transcripts.closeTarget(invocation.getArgument(0));
              return null;
            })
        .when(ui)
        .closeTarget(any());
    when(ui.isChannelDisconnected(target)).thenReturn(detached);
    ConnectionCoordinator connections = mock(ConnectionCoordinator.class);
    when(connections.isConnected("libera")).thenReturn(connected);
    IrcTargetMembershipPort membership = mock(IrcTargetMembershipPort.class);
    when(membership.partChannel("libera", targetName, null)).thenReturn(Completable.complete());
    when(membership.requestNames(anyString(), anyString())).thenReturn(Completable.complete());
    TargetChatHistoryPort historyPort = mock(TargetChatHistoryPort.class);
    IrcSessionRuntimeConfigPort config =
        mock(
            IrcSessionRuntimeConfigPort.class,
            withSettings().extraInterfaces(ServerTreeChannelStateConfigPort.class));
    TargetCoordinator coordinator =
        new TargetCoordinator(
            ui,
            new UserListStore(),
            membership,
            mock(IrcBouncerPlaybackPort.class),
            mock(ServerRegistry.class),
            config,
            connections,
            mock(IgnoreListQueryPort.class),
            mock(UserhostQueryService.class),
            mock(UserInfoEnrichmentService.class),
            historyPort,
            mock(TargetLogMaintenancePort.class),
            mock(ExecutorService.class),
            mock(ScheduledExecutorService.class));
    try {
      coordinator.onTargetSelected(target);
      if (target.isChannel()) {
        coordinator.closeChannel(target);
        verify(config).forgetJoinedChannel("libera", targetName);
      } else {
        coordinator.closeTarget(target);
      }

      assertSame(statusDocument, pane.getDocument());
      assertEquals(status, coordinator.getActiveTarget());
      String text = statusDocument.getText(0, statusDocument.getLength());
      assertTrue(text.startsWith(history), "existing Unicode history must be preserved");
      assertTrue(text.contains("(ui): Closed " + targetName));
      assertEquals(
          0,
          rebuildsDuringAppend.get(),
          "closing must not reshape all status history during append");
      verify(historyPort).reset(target);
      verify(ui).closeTarget(target);
      assertEquals(0, transcripts.document(target).getLength());
      if (target.isChannel() && connected && !detached) {
        verify(membership).partChannel("libera", targetName, null);
      } else {
        verify(membership, never()).partChannel(anyString(), anyString(), any());
      }
    } finally {
      ReflectionTestUtils.invokeMethod(coordinator, "shutdown");
      ReflectionTestUtils.invokeMethod(transcripts, "shutdown");
    }
  }

  private static final class CountingTextPaneUI extends BasicTextPaneUI {
    private int rebuilds;

    @Override
    protected void modelChanged() {
      rebuilds++;
      super.modelChanged();
    }
  }
}
