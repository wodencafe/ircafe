package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;

class TargetCoordinatorLifecycleMockVerifyTest {

  @Test
  void detachChannelMarksDetachedBeforeSendingPart() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);
    TargetRef channel = new TargetRef("libera", "#ircafe");

    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe", null)).thenReturn(Completable.complete());

    coordinator.detachChannel(channel);

    InOrder inOrder = inOrder(ui, irc);
    inOrder.verify(ui).setChannelDetached(channel, true);
    inOrder.verify(irc).partChannel("libera", "#ircafe", null);
  }

  @Test
  void closeAttachedChannelForgetsAndClosesBeforeParting() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);
    TargetRef channel = new TargetRef("libera", "#ircafe");

    when(ui.isChannelDetached(channel)).thenReturn(false);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe", null)).thenReturn(Completable.complete());

    coordinator.closeChannel(channel);

    InOrder inOrder = inOrder(runtimeConfig, ui, irc);
    inOrder.verify(runtimeConfig).forgetJoinedChannel("libera", "#ircafe");
    inOrder.verify(ui).closeTarget(channel);
    inOrder.verify(irc).partChannel("libera", "#ircafe", null);
  }

  @Test
  void joinThenJoinedEventTransitionsFromDetachedToAttachedWithoutPart() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);
    TargetRef channel = new TargetRef("libera", "#ircafe");

    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.joinChannel("libera", "#ircafe")).thenReturn(Completable.complete());

    coordinator.joinChannel(channel);
    boolean accepted = coordinator.onJoinedChannel("libera", "#ircafe");

    assertTrue(accepted);
    verify(runtimeConfig).rememberJoinedChannel("libera", "#ircafe");
    InOrder inOrder = inOrder(ui, irc);
    inOrder.verify(ui).setChannelDetached(channel, true);
    inOrder.verify(irc).joinChannel("libera", "#ircafe");
    inOrder.verify(ui).setChannelDetached(channel, false);
    verify(irc, never()).partChannel("libera", "#ircafe");
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
