package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.irc.IrcClientService;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboundDccCommandServiceControlMessagesTest {

  private final UiPort ui = mock(UiPort.class);
  private final IrcClientService irc = mock(IrcClientService.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final DccTransferStore dccTransferStore = mock(DccTransferStore.class);
  private final ExecutorService io = mock(ExecutorService.class);

  private final OutboundDccCommandService service =
      new OutboundDccCommandService(
          ui, irc, targetCoordinator, connectionCoordinator, dccTransferStore, io);

  @Test
  void inboundResumeControlIsRecognizedAndNotReportedAsUnsupported() {
    Instant at = Instant.parse("2026-02-26T00:00:00Z");
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#elsewhere"));

    boolean consumed =
        service.handleInboundDccOffer(
            at, "libera", "alice", "RESUME \"test file.bin\" 5500 1024", false);

    assertTrue(consumed);
    verify(ui).ensureTargetExists(pm);
    verify(ui).markUnread(pm);
    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(ui).appendStatusAt(eq(pm), eq(at), eq("(dcc)"), text.capture());
    assertTrue(text.getValue().contains("DCC RESUME control"));
    assertFalse(text.getValue().toLowerCase().contains("unsupported"));
  }

  @Test
  void inboundAcceptControlIsRecognizedAndNotReportedAsUnsupported() {
    Instant at = Instant.parse("2026-02-26T00:00:01Z");
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#elsewhere"));

    boolean consumed =
        service.handleInboundDccOffer(
            at, "libera", "alice", "ACCEPT \"test file.bin\" 5500 1024", false);

    assertTrue(consumed);
    verify(ui).ensureTargetExists(pm);
    verify(ui).markUnread(pm);
    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(ui).appendStatusAt(eq(pm), eq(at), eq("(dcc)"), text.capture());
    assertTrue(text.getValue().contains("DCC ACCEPT control"));
    assertFalse(text.getValue().toLowerCase().contains("unsupported"));
  }

  @Test
  void malformedResumeControlIsHandledAsMalformed() {
    Instant at = Instant.parse("2026-02-26T00:00:02Z");
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#elsewhere"));

    boolean consumed = service.handleInboundDccOffer(at, "libera", "alice", "RESUME oops", false);

    assertTrue(consumed);
    verify(ui).ensureTargetExists(pm);
    verify(ui).markUnread(pm);
    verify(ui).appendStatusAt(eq(pm), eq(at), eq("(dcc)"), any(String.class));
  }
}
