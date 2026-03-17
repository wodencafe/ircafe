package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.JoinModeBurstService;
import cafe.woden.ircclient.app.ModeFormattingService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.state.ChannelFlagModeState;
import cafe.woden.ircclient.state.ModeRoutingState;
import cafe.woden.ircclient.state.RecentStatusModeState;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.testutil.FunctionalTestWiringSupport;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.settings.ChatThemeSettingsBus;
import cafe.woden.ircclient.ui.shell.StatusBar;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import javax.swing.SwingUtilities;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

class ModeSnapshotSuppressionFunctionalTest {

  @Test
  void liveSnapshotAfterModeDeltaDoesNotEchoModeSummaryInTranscript() throws Exception {
    Fixture fixture = createFixture();
    TargetRef chan = new TargetRef("libera", "#functional-mode");

    onEdt(
        () -> {
          fixture.handler.handleChannelModeObserved(
              "libera",
              new IrcEvent.ChannelModeObserved(
                  Instant.now(),
                  chan.target(),
                  "FurBot",
                  "+o Arca",
                  IrcEvent.ChannelModeKind.DELTA,
                  IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT));

          fixture.handler.handleChannelModeObserved(
              "libera",
              new IrcEvent.ChannelModeObserved(
                  Instant.now(),
                  chan.target(),
                  "",
                  "+nrf [10j#R10]:5",
                  IrcEvent.ChannelModeKind.SNAPSHOT,
                  IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT));
        });
    flushEdt();

    String text = transcriptText(fixture.transcripts.document(chan));
    assertTrue(text.contains("FurBot gives channel operator privileges to Arca."));
    assertFalse(text.contains("Channel modes: no outside messages, registered only, +f"));
  }

  private static Fixture createFixture() {
    ChatThemeSettingsBus chatThemeBus = new ChatThemeSettingsBus(null);
    ChatStyles chatStyles = new ChatStyles(chatThemeBus);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, chatStyles, null);
    ChatTranscriptStore transcripts =
        new ChatTranscriptStore(
            chatStyles, renderer, null, null, null, null, null, null, null, null);

    SwingUiPort ui =
        new SwingUiPort(
            mock(ServerTreeDockable.class),
            mock(ChatDockable.class),
            transcripts,
            mock(MentionPatternRegistry.class),
            new NotificationStore(),
            mock(UserListDockable.class),
            mock(StatusBar.class),
            new ConnectButton(),
            new DisconnectButton(),
            new TargetActivationBus(),
            new OutboundLineBus(),
            mock(ChatDockManager.class),
            new ActiveInputRouter());

    JoinModeBurstService joinModeBurstService = mock(JoinModeBurstService.class);
    when(joinModeBurstService.handleChannelModeChanged(anyString(), anyString(), anyString()))
        .thenReturn(false);
    when(joinModeBurstService.shouldSuppressModesListedSummary(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(joinModeBurstService.hasActiveJoinModeBuffer(anyString(), anyString())).thenReturn(false);

    ServerIsupportStatePort serverIsupportState =
        FunctionalTestWiringSupport.fallbackIsupportState();
    ModeFormattingService modeFormattingService =
        FunctionalTestWiringSupport.newModeFormattingService(serverIsupportState);

    InboundModeEventHandler handler =
        FunctionalTestWiringSupport.newInboundModeEventHandler(
            ui,
            new ModeRoutingState(),
            joinModeBurstService,
            modeFormattingService,
            new ChannelFlagModeState(),
            new RecentStatusModeState(),
            serverIsupportState);

    return new Fixture(handler, transcripts);
  }

  private static String transcriptText(StyledDocument doc) {
    if (doc == null) return "";
    try {
      return doc.getText(0, doc.getLength());
    } catch (Exception e) {
      return "";
    }
  }

  private static void onEdt(ThrowingRunnable runnable)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        runnable.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private record Fixture(InboundModeEventHandler handler, ChatTranscriptStore transcripts) {}
}
