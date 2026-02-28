package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.UserhostQueryService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;

class TargetCoordinatorChannelDetachPolicyTest {

  @Test
  void closingChannelDetachesInsteadOfClosingBuffer() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe", null)).thenReturn(Completable.complete());

    coordinator.closeTarget(chan);

    verify(ui, atLeastOnce()).setChannelDisconnected(chan, true);
    verify(irc).partChannel(eq("libera"), eq("#ircafe"), isNull());
    verify(ui, never()).closeTarget(chan);
    verify(runtimeConfig, never()).forgetJoinedChannel(anyString(), anyString());
  }

  @Test
  void closeChannelForgetsPersistedChannelAndClosesBuffer() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(ui.isChannelDisconnected(chan)).thenReturn(false);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe", null)).thenReturn(Completable.complete());

    coordinator.closeChannel(chan);

    verify(runtimeConfig).forgetJoinedChannel("libera", "#ircafe");
    verify(ui).closeTarget(chan);
    verify(irc).partChannel(eq("libera"), eq("#ircafe"), isNull());
  }

  @Test
  void closeDetachedChannelForgetsAndClosesWithoutSendingPart() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(ui.isChannelDisconnected(chan)).thenReturn(true);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);

    coordinator.closeChannel(chan);

    verify(runtimeConfig).forgetJoinedChannel("libera", "#ircafe");
    verify(ui).closeTarget(chan);
    verify(irc, never()).partChannel(eq("libera"), eq("#ircafe"), isNull());
    verify(irc, never()).partChannel("libera", "#ircafe");
  }

  @Test
  void closeChannelDoesNotReopenAsDetachedOnSubsequentMembershipLoss() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(ui.isChannelDisconnected(chan)).thenReturn(false);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe", null)).thenReturn(Completable.complete());

    coordinator.closeChannel(chan);
    clearInvocations(ui);

    coordinator.onChannelMembershipLost("libera", "#ircafe", true);

    verify(ui, never()).setChannelDisconnected(chan, true);
    verify(ui, never()).ensureTargetExists(chan);
  }

  @Test
  void joinedChannelWhileSuppressedIsPartedAgainAndRemainsDetached() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe")).thenReturn(Completable.complete());

    coordinator.onChannelMembershipLost("libera", "#ircafe", true);
    boolean accepted = coordinator.onJoinedChannel("libera", "#ircafe");

    assertFalse(accepted);
    verify(ui, atLeastOnce()).setChannelDisconnected(chan, true);
    verify(irc).partChannel("libera", "#ircafe");
  }

  @Test
  void manualJoinClearsSuppressionAndAttachedStateOnJoinedEvent() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.joinChannel("libera", "#ircafe")).thenReturn(Completable.complete());

    coordinator.onChannelMembershipLost("libera", "#ircafe", true);
    coordinator.joinChannel(chan);
    boolean accepted = coordinator.onJoinedChannel("libera", "#ircafe");

    assertTrue(accepted);
    verify(runtimeConfig).rememberJoinedChannel("libera", "#ircafe");
    verify(irc).joinChannel("libera", "#ircafe");
    verify(ui, atLeastOnce()).setChannelDisconnected(chan, false);
  }

  @Test
  void detachChannelWhileDisconnectedDoesNotSendPart() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#offline-detach");
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    coordinator.disconnectChannel(chan);

    verify(ui, atLeastOnce()).setChannelDisconnected(chan, true);
    verify(irc, never()).partChannel(eq("libera"), eq("#offline-detach"), isNull());
    verify(irc, never()).partChannel("libera", "#offline-detach");
  }

  @Test
  void joinChannelWhileDisconnectedPersistsAndStaysDetached() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#queued-join");
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    coordinator.joinChannel(chan);

    verify(runtimeConfig).rememberJoinedChannel("libera", "#queued-join");
    verify(ui, atLeastOnce()).setChannelDisconnected(chan, true);
    verify(irc, never()).joinChannel("libera", "#queued-join");
  }

  @Test
  void joinChannelErrorKeepsDetachedState() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#join-error");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.joinChannel("libera", "#join-error"))
        .thenReturn(Completable.error(new RuntimeException("join failed")));

    coordinator.joinChannel(chan);

    verify(runtimeConfig).rememberJoinedChannel("libera", "#join-error");
    verify(ui, atLeastOnce()).setChannelDisconnected(chan, true);
    verify(ui, atLeastOnce())
        .appendError(eq(new TargetRef("libera", "status")), eq("(join-error)"), anyString());
  }

  @Test
  void joinedChannelWithoutSuppressionAttachesNormally() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#attached");

    boolean accepted = coordinator.onJoinedChannel("libera", "#attached");

    assertTrue(accepted);
    verify(ui, atLeastOnce()).setChannelDisconnected(chan, false);
    verify(irc, never()).partChannel("libera", "#attached");
    verify(irc, never()).partChannel(eq("libera"), eq("#attached"), isNull());
  }

  @Test
  void bouncerDetachUsesSojuDetachReasonAndReattachesOnJoin() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isSojuBouncerAvailable("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe", "detach")).thenReturn(Completable.complete());

    coordinator.bouncerDetachChannel(chan);
    boolean accepted = coordinator.onJoinedChannel("libera", "#ircafe");

    assertTrue(accepted);
    verify(irc).partChannel("libera", "#ircafe", "detach");
    verify(irc, never()).partChannel("libera", "#ircafe");
    verify(ui, atLeastOnce()).setChannelDisconnected(chan, false);
  }

  @Test
  void bouncerDetachUsesZncDetachCommandWhenDetected() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#znc");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isZncBouncerDetected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "DETACH #znc")).thenReturn(Completable.complete());

    coordinator.bouncerDetachChannel(chan);

    verify(irc).sendRaw("libera", "DETACH #znc");
    verify(irc, never()).partChannel("libera", "#znc", null);
  }

  @Test
  void disconnectWithoutReasonKeepsRegularPartEvenWhenBouncerIsAvailable() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#reasonless");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isSojuBouncerAvailable("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#reasonless", null)).thenReturn(Completable.complete());

    coordinator.disconnectChannel(chan);

    verify(irc).partChannel("libera", "#reasonless", null);
    verify(irc, never()).partChannel("libera", "#reasonless", "detach");
    verify(irc, never()).sendRaw("libera", "DETACH #reasonless");
  }

  @Test
  void explicitPartReasonKeepsRegularPartEvenWhenBouncerIsAvailable() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);

    TargetRef chan = new TargetRef("libera", "#reason");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isSojuBouncerAvailable("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#reason", "brb")).thenReturn(Completable.complete());

    coordinator.disconnectChannel(chan, "brb");

    verify(irc).partChannel("libera", "#reason", "brb");
    verify(irc, never()).partChannel("libera", "#reason", "detach");
  }

  private static TargetCoordinator newCoordinator(
      UiPort ui,
      IrcClientService irc,
      ConnectionCoordinator connectionCoordinator,
      RuntimeConfigStore runtimeConfig) {
    return new TargetCoordinator(
        ui,
        mock(UserListStore.class),
        irc,
        mock(ServerRegistry.class),
        runtimeConfig,
        connectionCoordinator,
        mock(IgnoreListQueryPort.class),
        mock(UserhostQueryService.class),
        mock(UserInfoEnrichmentService.class),
        mock(TargetChatHistoryPort.class),
        mock(TargetLogMaintenancePort.class),
        mock(java.util.concurrent.ExecutorService.class),
        mock(java.util.concurrent.ScheduledExecutorService.class));
  }
}
