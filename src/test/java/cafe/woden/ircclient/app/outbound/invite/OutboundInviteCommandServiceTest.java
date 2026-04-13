package cafe.woden.ircclient.app.outbound.invite;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundRawCommandSupport;
import cafe.woden.ircclient.app.outbound.support.OutboundRawLineCorrelationService;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundInviteCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy =
      mock(OutboundBackendCapabilityPolicy.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final OutboundRawLineCorrelationService rawLineCorrelationService =
      new OutboundRawLineCorrelationService(backendCapabilityPolicy, labeledResponseRoutingState);
  private final OutboundRawCommandSupport rawCommandSupport =
      new OutboundRawCommandSupport(rawLineCorrelationService);
  private final ChatCommandRuntimeConfigPort runtimeConfig =
      mock(ChatCommandRuntimeConfigPort.class);
  private final PendingInvitePort pendingInviteState = mock(PendingInvitePort.class);
  private final WhoisRoutingPort whoisRoutingState = mock(WhoisRoutingPort.class);
  private final IgnoreListCommandPort ignoreListService = mock(IgnoreListCommandPort.class);
  private final PendingInviteCommandSupport pendingInviteCommandSupport =
      new PendingInviteCommandSupport(
          IrcMediatorInteractionPort.from(irc),
          ui,
          connectionCoordinator,
          targetCoordinator,
          commandTargetPolicy,
          runtimeConfig,
          pendingInviteState,
          whoisRoutingState,
          ignoreListService);
  private final OutboundInviteCommandService service =
      new OutboundInviteCommandService(
          IrcMediatorInteractionPort.from(irc),
          ui,
          connectionCoordinator,
          targetCoordinator,
          commandTargetPolicy,
          rawCommandSupport,
          pendingInviteCommandSupport);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void inviteSendsRawInviteLineForActiveChannel() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "INVITE bob #ircafe")).thenReturn(Completable.complete());

    service.handleInvite(disposables, "bob", "");

    verify(ui).ensureTargetExists(channel);
    verify(irc).sendRaw("libera", "INVITE bob #ircafe");
  }

  @Test
  void inviteJoinRemembersChannelAndRemovesInviteOnSuccess() {
    TargetRef status = new TargetRef("libera", "status");
    PendingInvitePort.PendingInvite invite = pendingInvite(12L, "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.get(12L)).thenReturn(invite);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.joinChannel("libera", "#ircafe")).thenReturn(Completable.complete());

    service.handleInviteJoin(disposables, "12");

    verify(runtimeConfig).rememberJoinedChannel("libera", "#ircafe");
    verify(targetCoordinator).syncRuntimeAutoJoinForReconnect("libera");
    verify(irc).joinChannel("libera", "#ircafe");
    verify(pendingInviteState).remove(12L);
  }

  @Test
  void inviteJoinOnQuasselDoesNotPersistJoinedChannelLocally() {
    TargetRef status = new TargetRef("quassel", "status");
    PendingInvitePort.PendingInvite invite = pendingInvite(13L, "alice", "quassel");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.get(13L)).thenReturn(invite);
    when(connectionCoordinator.isConnected("quassel")).thenReturn(true);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(
                new IrcProperties.Server(
                    "quassel",
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
                    IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.joinChannel("quassel", "#ircafe")).thenReturn(Completable.complete());

    service.handleInviteJoin(disposables, "13");

    verify(runtimeConfig, never()).rememberJoinedChannel("quassel", "#ircafe");
    verify(targetCoordinator, never()).syncRuntimeAutoJoinForReconnect("quassel");
    verify(irc).joinChannel("quassel", "#ircafe");
    verify(pendingInviteState).remove(13L);
  }

  @Test
  void inviteWhoisRoutesWhoisFromInvite() {
    TargetRef status = new TargetRef("libera", "status");
    PendingInvitePort.PendingInvite invite = pendingInvite(12L, "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.get(12L)).thenReturn(invite);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.whois("libera", "alice")).thenReturn(Completable.complete());

    service.handleInviteWhois(disposables, "12");

    verify(whoisRoutingState).put("libera", "alice", status);
    verify(irc).whois("libera", "alice");
  }

  @Test
  void inviteListReportsEmptyForServer() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.listForServer("libera")).thenReturn(List.of());

    service.handleInviteList("");

    verify(ui).appendStatus(status, "(invite)", "No pending invites on libera.");
  }

  @Test
  void inviteBlockAddsMaskAndRemovesInviteWhenNickIsPresent() {
    TargetRef status = new TargetRef("libera", "status");
    PendingInvitePort.PendingInvite invite = pendingInvite(12L, "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.latestForServer("libera")).thenReturn(invite);
    when(ignoreListService.addMask("libera", "alice")).thenReturn(true);

    service.handleInviteBlock("last");

    verify(ignoreListService).addMask("libera", "alice");
    verify(pendingInviteState).remove(12L);
    verify(ui).appendStatus(status, "(invite)", "Blocked invites from alice (alice!*@*).");
  }

  @Test
  void inviteBlockReportsAlreadyBlockingWhenMaskAlreadyExists() {
    TargetRef status = new TargetRef("libera", "status");
    PendingInvitePort.PendingInvite invite = pendingInvite(27L, "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.latestForServer("libera")).thenReturn(invite);
    when(ignoreListService.addMask("libera", "alice")).thenReturn(false);

    service.handleInviteBlock("last");

    verify(ignoreListService).addMask("libera", "alice");
    verify(pendingInviteState).remove(27L);
    verify(ui).appendStatus(status, "(invite)", "Already blocking alice (alice!*@*).");
  }

  @Test
  void inviteBlockRejectsServerInviteWithoutNick() {
    TargetRef status = new TargetRef("libera", "status");
    PendingInvitePort.PendingInvite invite = pendingInvite(31L, "server");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.latestForServer("libera")).thenReturn(invite);

    service.handleInviteBlock("last");

    verify(ignoreListService, never()).addMask(anyString(), anyString());
    verify(pendingInviteState, never()).remove(31L);
    verify(ui).appendStatus(status, "(invite)", "No inviter nick available for invite #31.");
  }

  @Test
  void inviteAutoJoinToggleFlipsCurrentState() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.inviteAutoJoinEnabled()).thenReturn(false);

    service.handleInviteAutoJoin("toggle");

    verify(pendingInviteState).setInviteAutoJoinEnabled(true);
    verify(runtimeConfig).rememberInviteAutoJoinEnabled(true);
    verify(ui).appendStatus(status, "(invite)", "Invite auto-join is now enabled.");
  }

  @Test
  void inviteAutoJoinStatusMentionsAjinviteAlias() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.inviteAutoJoinEnabled()).thenReturn(true);

    service.handleInviteAutoJoin("status");

    verify(ui)
        .appendStatus(
            status,
            "(invite)",
            "Invite auto-join is enabled. Use /inviteautojoin on|off or /ajinvite.");
  }

  private static PendingInvitePort.PendingInvite pendingInvite(long id, String inviterNick) {
    return pendingInvite(id, inviterNick, "libera");
  }

  private static PendingInvitePort.PendingInvite pendingInvite(
      long id, String inviterNick, String serverId) {
    Instant now = Instant.parse("2026-02-16T00:00:00Z");
    return new PendingInvitePort.PendingInvite(
        id, now, now, serverId, "#ircafe", inviterNick, "me", "", true, 1);
  }
}
