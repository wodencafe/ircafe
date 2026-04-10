package cafe.woden.ircclient.app.outbound.dcc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import java.io.BufferedWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DccOfferCommandSupportTest {

  private final UiPort ui = mock(UiPort.class);
  private final IrcMediatorInteractionPort mediatorIrc = mock(IrcMediatorInteractionPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final DccTransferStore dccTransferStore = mock(DccTransferStore.class);
  private final ExecutorService io = mock(ExecutorService.class);
  private final DccRuntimeRegistry dccRuntimeRegistry = new DccRuntimeRegistry();

  private final DccCommandSupport dccCommandSupport =
      new DccCommandSupport(ui, targetCoordinator, dccTransferStore);
  private final DccChatSessionSupport dccChatSessionSupport =
      new DccChatSessionSupport(ui, mediatorIrc, io, dccCommandSupport, dccRuntimeRegistry);
  private final DccFileTransferIoSupport dccFileTransferIoSupport =
      new DccFileTransferIoSupport(ui, dccCommandSupport);
  private final DccOfferCommandSupport support =
      new DccOfferCommandSupport(
          ui,
          mediatorIrc,
          connectionCoordinator,
          io,
          dccCommandSupport,
          dccChatSessionSupport,
          dccFileTransferIoSupport,
          dccRuntimeRegistry);

  @Test
  void acceptChatOfferReportsWhenNoPendingOfferExists() {
    TargetRef out = new TargetRef("libera", "#chat");

    support.acceptChatOffer("libera", out, "alice");

    verify(ui).appendStatus(out, "(dcc)", "No pending DCC CHAT offer from alice.");
  }

  @Test
  void listDccStateReportsCountsAndEntries() throws Exception {
    TargetRef out = new TargetRef("libera", "#chat");
    dccRuntimeRegistry
        .chatSessions()
        .put(
            DccCommandSupport.peerKey("libera", "carol"),
            new DccChatSession(
                "libera",
                "carol",
                mock(Socket.class),
                mock(BufferedWriter.class),
                new Object(),
                new AtomicBoolean(false)));
    dccRuntimeRegistry
        .pendingChatOffers()
        .put(
            DccCommandSupport.peerKey("libera", "alice"),
            new PendingChatOffer(
                "libera",
                "alice",
                InetAddress.getByName("203.0.113.7"),
                4000,
                Instant.parse("2026-03-20T00:00:00Z")));
    dccRuntimeRegistry
        .pendingSendOffers()
        .put(
            DccCommandSupport.peerKey("libera", "bob"),
            new PendingSendOffer(
                "libera",
                "bob",
                "notes.txt",
                InetAddress.getByName("203.0.113.8"),
                5000,
                2048L,
                Instant.parse("2026-03-20T00:00:01Z")));

    support.listDccState("libera", out);

    verify(ui)
        .appendStatus(
            out, "(dcc)", "DCC state: active chats=1, pending chat offers=1, pending sends=1");
    verify(ui).appendStatus(out, "(dcc)", "Pending CHAT from alice at 203.0.113.7:4000");
    verify(ui).appendStatus(out, "(dcc)", "Pending SEND from bob: notes.txt (2.0 KiB)");
    verify(ui).appendStatus(out, "(dcc)", "Active CHAT with carol");
  }
}
