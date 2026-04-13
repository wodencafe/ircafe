package cafe.woden.ircclient.app.outbound.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundConnectionStatusSupport;
import cafe.woden.ircclient.app.outbound.support.OutboundRawCommandSupport;
import cafe.woden.ircclient.app.outbound.support.OutboundRawLineCorrelationService;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundModeCommandServiceTest {

  private final IrcTargetMembershipPort irc = mock(IrcTargetMembershipPort.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);
  private final ModeRoutingPort modeRoutingState = mock(ModeRoutingPort.class);
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy =
      mock(OutboundBackendCapabilityPolicy.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final OutboundRawLineCorrelationService rawLineCorrelationService =
      new OutboundRawLineCorrelationService(backendCapabilityPolicy, labeledResponseRoutingState);
  private final OutboundRawCommandSupport rawCommandSupport =
      new OutboundRawCommandSupport(rawLineCorrelationService);
  private final OutboundConnectionStatusSupport outboundConnectionStatusSupport =
      new OutboundConnectionStatusSupport(ui, connectionCoordinator);
  private final OutboundTargetMembershipCommandSupport targetMembershipCommandSupport =
      new OutboundTargetMembershipCommandSupport(
          irc,
          ui,
          outboundConnectionStatusSupport,
          targetCoordinator,
          commandTargetPolicy,
          rawCommandSupport);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final OutboundModeCommandService service =
      new OutboundModeCommandService(
          targetMembershipCommandSupport, commandTargetPolicy, modeRoutingState);

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void modeQueryFromChannelTracksPendingTargetAndSendsRawModeLine() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "MODE #ircafe")).thenReturn(Completable.complete());

    service.handleMode(disposables, "", "");

    verify(modeRoutingState).putPendingModeTarget("libera", "#ircafe", channel);
    verify(ui).ensureTargetExists(channel);
    verify(ui).appendStatus(channel, "(mode)", "→ MODE #ircafe");
    verify(irc).sendRaw("libera", "MODE #ircafe");
  }

  @Test
  void opUsesPreparedLabeledRawLineAndRemembersCorrelation() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef channel = new TargetRef("libera", "#ircafe");
    LabeledResponseRoutingPort.PreparedRawLine prepared =
        new LabeledResponseRoutingPort.PreparedRawLine(
            "@label=req-1 MODE #ircafe +o alice", "req-1", true);

    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(backendCapabilityPolicy.supportsLabeledResponse("libera")).thenReturn(true);
    when(labeledResponseRoutingState.prepareOutgoingRaw("libera", "MODE #ircafe +o alice"))
        .thenReturn(prepared);
    when(irc.sendRaw("libera", "@label=req-1 MODE #ircafe +o alice"))
        .thenReturn(Completable.complete());

    service.handleOp(disposables, "#ircafe", List.of("alice"));

    verify(labeledResponseRoutingState)
        .remember(
            eq("libera"),
            eq("req-1"),
            eq(channel),
            eq("MODE #ircafe +o alice"),
            any(Instant.class));
    verify(ui).ensureTargetExists(channel);
    verify(ui).appendStatus(channel, "(mode)", "→ MODE #ircafe +o alice {label=req-1}");
    verify(irc).sendRaw("libera", "@label=req-1 MODE #ircafe +o alice");
  }

  @Test
  void banShapesNickIntoHostmaskAndPreservesExistingMasks() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "MODE #ircafe +b trouble!*@*")).thenReturn(Completable.complete());
    when(irc.sendRaw("libera", "MODE #ircafe +b bad@host")).thenReturn(Completable.complete());

    service.handleBan(disposables, "", List.of("trouble", "bad@host"));

    verify(ui).ensureTargetExists(channel);
    verify(irc).sendRaw("libera", "MODE #ircafe +b trouble!*@*");
    verify(irc).sendRaw("libera", "MODE #ircafe +b bad@host");
  }

  @Test
  void modeWhenDisconnectedShowsConnectionStatusAndDoesNotSend() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    service.handleMode(disposables, "#ircafe", "+m");

    verify(ui).appendStatus(new TargetRef("libera", "status"), "(conn)", "Not connected");
    verify(irc, never()).sendRaw(anyString(), anyString());
  }

  @Test
  void modeOnMatrixBackendSendsRawModeLine() {
    TargetRef channel = new TargetRef("matrix", "#room:example.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(irc.sendRaw("matrix", "MODE #room:example.org +m")).thenReturn(Completable.complete());

    service.handleMode(disposables, "#room:example.org", "+m");

    verify(irc).sendRaw("matrix", "MODE #room:example.org +m");
  }

  private static IrcProperties.Server serverWithBackend(
      String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
        "core.example.net",
        4242,
        false,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backend);
  }
}
