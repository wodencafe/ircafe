package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import java.io.BufferedWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DccChatSessionSupportTest {

  private final UiPort ui = mock(UiPort.class);
  private final IrcMediatorInteractionPort mediatorIrc = mock(IrcMediatorInteractionPort.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final DccTransferStore dccTransferStore = mock(DccTransferStore.class);
  private final ExecutorService io = mock(ExecutorService.class);
  private final ConcurrentMap<String, DccChatSession> chatSessions = new ConcurrentHashMap<>();

  private final DccChatSessionSupport support =
      new DccChatSessionSupport(
          ui,
          mediatorIrc,
          io,
          new DccCommandSupport(ui, targetCoordinator, dccTransferStore),
          chatSessions);

  @Test
  void sendChatMessageReturnsFalseWhenSessionIsMissing() {
    assertFalse(support.sendChatMessage("libera", "alice", "hello"));
  }

  @Test
  void sendChatMessageWritesToSessionAndEchoesToUi() throws Exception {
    StringWriter sink = new StringWriter();
    BufferedWriter writer = new BufferedWriter(sink);
    Socket socket = mock(Socket.class);
    TargetRef pm = new TargetRef("libera", "alice");

    chatSessions.put(
        DccCommandSupport.peerKey("libera", "alice"),
        new DccChatSession(
            "libera", "alice", socket, writer, new Object(), new AtomicBoolean(false)));
    when(mediatorIrc.currentNick("libera")).thenReturn(Optional.of("chris"));

    assertTrue(support.sendChatMessage("libera", "alice", "hello world"));

    verify(ui).ensureTargetExists(pm);
    verify(ui).appendChat(eq(pm), eq("(chris)"), eq("[DCC] hello world"), eq(true));
    assertTrue(sink.toString().contains("hello world\r\n"));
  }

  @Test
  void closeChatSessionAnnouncesAndUpdatesTransferState() {
    Socket socket = mock(Socket.class);
    TargetRef pm = new TargetRef("libera", "alice");

    chatSessions.put(
        DccCommandSupport.peerKey("libera", "alice"),
        new DccChatSession(
            "libera",
            "alice",
            socket,
            mock(BufferedWriter.class),
            new Object(),
            new AtomicBoolean(false)));

    assertTrue(support.closeChatSession("libera", "alice", "Closed DCC CHAT session.", true));

    verify(ui).ensureTargetExists(pm);
    verify(ui).appendStatus(pm, "(dcc)", "Closed DCC CHAT session.");
    verify(dccTransferStore)
        .upsert(
            "libera",
            DccCommandSupport.transferEntryId("libera", "alice", "chat-active"),
            "alice",
            "Chat",
            "Closed",
            "",
            "",
            null,
            DccTransferStore.ActionHint.NONE);
  }
}
