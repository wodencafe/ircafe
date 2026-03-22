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
import cafe.woden.ircclient.app.outbound.CommandTargetPolicy;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundJoinPartCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final ChatCommandRuntimeConfigPort runtimeConfig =
      mock(ChatCommandRuntimeConfigPort.class);
  private final JoinRoutingPort joinRoutingState = mock(JoinRoutingPort.class);
  private final OutboundJoinPartCommandService service =
      new OutboundJoinPartCommandService(
          IrcTargetMembershipPort.from(irc),
          ui,
          connectionCoordinator,
          targetCoordinator,
          commandTargetPolicy,
          runtimeConfig,
          joinRoutingState);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
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
    verify(targetCoordinator, never()).closeChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partWithoutExplicitChannelClosesActiveChannelWithTrimmedReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    service.handlePart(disposables, "", "  be right back  ");

    verify(targetCoordinator).closeChannel(chan, "be right back");
  }

  @Test
  void partWithoutExplicitChannelRejectsNonChannelSelection() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "", "bye");

    verify(ui)
        .appendStatus(
            status, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
    verify(targetCoordinator, never()).closeChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partWithExplicitChannelClosesChannelOnActiveServer() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef expected = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "  #ircafe ", "  later ");

    verify(targetCoordinator).closeChannel(expected, "later");
  }

  @Test
  void partWithMatrixRoomIdClosesChannelLikeTarget() {
    TargetRef status = new TargetRef("matrix", "status");
    TargetRef room = new TargetRef("matrix", "!abc123:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "!abc123:matrix.org", "later");

    verify(targetCoordinator).closeChannel(room, "later");
  }

  @Test
  void partWithMatrixRoomIdEncodedInReasonClosesChannelLikeTarget() {
    TargetRef status = new TargetRef("matrix", "status");
    TargetRef room = new TargetRef("matrix", "!abc123:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "", "!abc123:matrix.org later");

    verify(targetCoordinator).closeChannel(room, "later");
  }

  @Test
  void partWithReasonPrefixedMatrixRoomOnIrcBackendShowsUsage() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "", "!abc123:matrix.org later");

    verify(ui)
        .appendStatus(
            status, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
    verify(targetCoordinator, never()).closeChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partFromActiveMatrixRoomIdClosesChannelLikeTarget() {
    TargetRef room = new TargetRef("matrix", "!abc123:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(room);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "", "later");

    verify(targetCoordinator).closeChannel(room, "later");
  }

  @Test
  void partFromActiveChannelWithReasonPrefixedMatrixRoomClosesExplicitRoom() {
    TargetRef activeRoom = new TargetRef("matrix", "!active:matrix.org");
    TargetRef explicitRoom = new TargetRef("matrix", "!other:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(activeRoom);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "", "!other:matrix.org later");

    verify(targetCoordinator).closeChannel(explicitRoom, "later");
    verify(targetCoordinator, never()).closeChannel(activeRoom, "!other:matrix.org later");
  }

  @Test
  void partWithExplicitNonChannelShowsUsageAndDoesNotClose() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "alice", "bye");

    verify(ui).appendStatus(status, "(part)", "Usage: /part [#channel] [reason]");
    verify(targetCoordinator, never()).closeChannel(any(TargetRef.class), anyString());
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
