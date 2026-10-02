package cafe.woden.ircclient.app.outbound.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.adapter.IrcTargetMembershipPortAdapter;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundJoinPartCommandServiceTest {

  private final IrcBackendRuntimeClientService irc = mock(IrcBackendRuntimeClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);
  private final IrcSessionRuntimeConfigPort runtimeConfig = mock(IrcSessionRuntimeConfigPort.class);
  private final JoinRoutingPort joinRoutingState = mock(JoinRoutingPort.class);
  private final PartCommandSupport partCommandSupport =
      new PartCommandSupport(
          new IrcTargetMembershipPortAdapter(irc),
          ui,
          connectionCoordinator,
          targetCoordinator,
          commandTargetPolicy);
  private final OutboundJoinPartCommandService service =
      new OutboundJoinPartCommandService(
          new IrcTargetMembershipPortAdapter(irc),
          ui,
          connectionCoordinator,
          targetCoordinator,
          commandTargetPolicy,
          runtimeConfig,
          joinRoutingState,
          partCommandSupport);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void joinAlreadyAttachedChannelSelectsItWithoutSendingJoin() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef channel = new TargetRef("libera", "##channel");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(ui.hasTarget(channel)).thenReturn(true);
    when(irc.joinChannel("libera", "##channel")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "  ##channel  ", "");

    verify(targetCoordinator).joinChannel(channel);
    verify(irc, never()).joinChannel(anyString(), anyString());
    verify(irc, never()).sendRaw(anyString(), anyString());
    verify(joinRoutingState, never()).rememberOrigin(anyString(), anyString(), any());
  }

  @Test
  void joinAlreadyAttachedChannelWithKeySelectsItWithoutSendingJoin() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef channel = new TargetRef("libera", "##channel");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(ui.hasTarget(channel)).thenReturn(true);
    when(irc.sendRaw("libera", "JOIN ##channel secret")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "##channel", "secret");

    verify(targetCoordinator).joinChannel(channel);
    verify(irc, never()).joinChannel(anyString(), anyString());
    verify(irc, never()).sendRaw(anyString(), anyString());
  }

  @Test
  void joinDetachedChannelStillRequestsJoinAndWaitsForConfirmation() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef channel = new TargetRef("libera", "##channel");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(ui.hasTarget(channel)).thenReturn(true);
    when(ui.isChannelDisconnected(channel)).thenReturn(true);
    when(irc.joinChannel("libera", "##channel")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "##channel", "");

    verify(irc).joinChannel("libera", "##channel");
    verify(targetCoordinator, never()).joinChannel(any());
    verify(ui, never()).selectTarget(any());
  }

  @Test
  void joinWhileDisconnectedQueuesChannelInsteadOfSelectingStaleTarget() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(ui.hasTarget(new TargetRef("libera", "##channel"))).thenReturn(true);

    service.handleJoin(disposables, "##channel", "");

    verify(runtimeConfig).rememberJoinedChannel("libera", "##channel");
    verify(targetCoordinator, never()).joinChannel(any());
    verify(irc, never()).joinChannel(anyString(), anyString());
    verify(ui).appendStatus(status, "(conn)", "Not connected (join queued in config only)");
  }

  @Test
  void joinWithKeySendsRawJoinLine() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "JOIN #secret hunter2")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "#secret", "hunter2");

    verify(runtimeConfig).rememberJoinedChannel("libera", "#secret");
    verify(targetCoordinator).syncRuntimeAutoJoinForReconnect("libera");
    verify(joinRoutingState).rememberOrigin("libera", "#secret", status);
    verify(irc).sendRaw("libera", "JOIN #secret hunter2");
  }

  @Test
  void joinWithKeyOnMatrixBackendDelegatesToRawJoin() {
    TargetRef status = new TargetRef("matrix", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(irc.sendRaw("matrix", "JOIN #room:example.org hunter2"))
        .thenReturn(Completable.complete());

    service.handleJoin(disposables, "#room:example.org", "hunter2");

    verify(runtimeConfig).rememberJoinedChannel("matrix", "#room:example.org");
    verify(targetCoordinator).syncRuntimeAutoJoinForReconnect("matrix");
    verify(joinRoutingState).rememberOrigin("matrix", "#room:example.org", status);
    verify(irc).sendRaw("matrix", "JOIN #room:example.org hunter2");
  }

  @Test
  void joinFromUiOnlyTargetRoutesJoinOriginToStatus() {
    TargetRef listTarget = TargetRef.channelList("libera");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(listTarget);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.joinChannel("libera", "#ircafe")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "#ircafe", "");

    verify(joinRoutingState).rememberOrigin("libera", "#ircafe", status);
    verify(targetCoordinator).syncRuntimeAutoJoinForReconnect("libera");
    verify(irc).joinChannel("libera", "#ircafe");
  }

  @Test
  void joinOnQuasselBackendUsesRegularJoinPath() {
    TargetRef status = new TargetRef("quassel", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("quassel")).thenReturn(true);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.joinChannel("quassel", "#ircafe")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "#ircafe", "");

    verify(runtimeConfig, never()).rememberJoinedChannel("quassel", "#ircafe");
    verify(targetCoordinator, never()).syncRuntimeAutoJoinForReconnect("quassel");
    verify(joinRoutingState).rememberOrigin("quassel", "#ircafe", status);
    verify(irc).joinChannel("quassel", "#ircafe");
  }

  @Test
  void partWithoutActiveTargetPromptsToSelectServer() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);

    service.handlePart(disposables, "", "");

    verify(ui).appendStatus(status, "(part)", "Select a server first.");
    verify(targetCoordinator, never()).partChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partWithoutExplicitChannelClosesActiveChannelWithTrimmedReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    service.handlePart(disposables, "", "  be right back  ");

    verify(targetCoordinator).partChannel(chan, "be right back");
  }

  @Test
  void partWithoutExplicitChannelRejectsNonChannelSelection() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "", "bye");

    verify(ui)
        .appendStatus(
            status, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
    verify(targetCoordinator, never()).partChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partWithExplicitChannelClosesChannelOnActiveServer() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef expected = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "  #ircafe ", "  later ");

    verify(targetCoordinator).partChannel(expected, "later");
  }

  @Test
  void partWithMatrixRoomIdClosesChannelLikeTarget() {
    TargetRef status = new TargetRef("matrix", "status");
    TargetRef room = new TargetRef("matrix", "!abc123:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "!abc123:matrix.org", "later");

    verify(targetCoordinator).partChannel(room, "later");
  }

  @Test
  void partWithMatrixRoomIdEncodedInReasonClosesChannelLikeTarget() {
    TargetRef status = new TargetRef("matrix", "status");
    TargetRef room = new TargetRef("matrix", "!abc123:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "", "!abc123:matrix.org later");

    verify(targetCoordinator).partChannel(room, "later");
  }

  @Test
  void partWithReasonPrefixedMatrixRoomOnIrcBackendShowsUsage() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "", "!abc123:matrix.org later");

    verify(ui)
        .appendStatus(
            status, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
    verify(targetCoordinator, never()).partChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partFromActiveMatrixRoomIdClosesChannelLikeTarget() {
    TargetRef room = new TargetRef("matrix", "!abc123:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(room);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "", "later");

    verify(targetCoordinator).partChannel(room, "later");
  }

  @Test
  void partFromActiveChannelWithReasonPrefixedMatrixRoomClosesExplicitRoom() {
    TargetRef activeRoom = new TargetRef("matrix", "!active:matrix.org");
    TargetRef explicitRoom = new TargetRef("matrix", "!other:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(activeRoom);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "", "!other:matrix.org later");

    verify(targetCoordinator).partChannel(explicitRoom, "later");
    verify(targetCoordinator, never()).partChannel(activeRoom, "!other:matrix.org later");
  }

  @Test
  void partWithExplicitNonChannelShowsUsageAndDoesNotClose() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "alice", "bye");

    verify(ui).appendStatus(status, "(part)", "Usage: /part [#channel] [reason]");
    verify(targetCoordinator, never()).partChannel(any(TargetRef.class), anyString());
  }

  private static IrcProperties.Server serverWithBackend(
      String id, IrcProperties.Server.Backend backend) {
    return IrcPropertiesTestFixtures.serverBuilder(id)
        .host("core.example.net")
        .port(4242)
        .tls(false)
        .backend(backend)
        .build();
  }
}
