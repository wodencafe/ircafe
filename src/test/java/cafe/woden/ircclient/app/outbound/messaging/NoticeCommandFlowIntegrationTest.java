package cafe.woden.ircclient.app.outbound.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3MultilineFeatureSupport;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandCatalog;
import cafe.woden.ircclient.app.commands.BackendNamedCommandParser;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.FilterCommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundConnectionStatusSupport;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.Test;

class NoticeCommandFlowIntegrationTest {

  @Test
  void noticeFromServerTabParsesAndSendsThroughMessagingFlow() {
    IrcBackendRuntimeClientService irc = mock(IrcBackendRuntimeClientService.class);
    UiPort ui = mock(UiPort.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
    PendingEchoMessagePort pendingEchoMessageState = mock(PendingEchoMessagePort.class);
    OutboundBackendCapabilityPolicy backendCapabilityPolicy =
        mock(OutboundBackendCapabilityPolicy.class);
    Ircv3MultilineFeatureSupport multilineFeatureSupport =
        new Ircv3MultilineFeatureSupport(
            backendCapabilityPolicy, IrcNegotiatedFeaturePort.from(irc));
    OutboundMessagingCommandService messaging =
        new OutboundMessagingCommandService(
            irc,
            IrcEchoCapabilityPort.from(irc),
            new OutboundMultilineMessageSupport(multilineFeatureSupport, ui),
            new OutboundConnectionStatusSupport(ui, connectionCoordinator),
            ui,
            targetCoordinator,
            pendingEchoMessageState);
    CommandParser parser =
        new CommandParser(
            new FilterCommandParser(),
            new BackendNamedCommandParser(BackendNamedCommandCatalog.empty()));
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(irc.sendNotice("libera", "alice", "heads up")).thenReturn(Completable.complete());

    ParsedInput.Notice notice =
        assertInstanceOf(ParsedInput.Notice.class, parser.parse("/notice alice heads up"));
    CompositeDisposable disposables = new CompositeDisposable();
    try {
      messaging.handleNotice(disposables, notice.target(), notice.body());
    } finally {
      disposables.dispose();
    }

    assertEquals("alice", notice.target());
    assertEquals("heads up", notice.body());
    verify(irc).sendNotice("libera", "alice", "heads up");
    verify(ui, never()).appendError(any(TargetRef.class), anyString(), anyString());
  }
}
