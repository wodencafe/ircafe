package cafe.woden.ircclient.app;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
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

class TargetCoordinatorHistoryResetTest {

  @Test
  void closePrivateTargetResetsHistoryState() {
    UiPort ui = mock(UiPort.class);
    TargetChatHistoryPort history = mock(TargetChatHistoryPort.class);
    TargetCoordinator coordinator =
        newCoordinator(
            ui,
            mock(IrcClientService.class),
            mock(ConnectionCoordinator.class),
            mock(RuntimeConfigStore.class),
            history);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);

    verify(history).reset(pm);
  }

  @Test
  void closePrivateTargetResetsHistoryBeforeUiClose() {
    UiPort ui = mock(UiPort.class);
    TargetChatHistoryPort history = mock(TargetChatHistoryPort.class);
    TargetCoordinator coordinator =
        newCoordinator(
            ui,
            mock(IrcClientService.class),
            mock(ConnectionCoordinator.class),
            mock(RuntimeConfigStore.class),
            history);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);

    InOrder inOrder = inOrder(history, ui);
    inOrder.verify(history).reset(pm);
    inOrder.verify(ui).closeTarget(pm);
  }

  @Test
  void selectingStillClosedPrivateTargetDoesNotTriggerHistoryPreload() {
    UiPort ui = mock(UiPort.class);
    TargetChatHistoryPort history = mock(TargetChatHistoryPort.class);
    TargetCoordinator coordinator =
        newCoordinator(
            ui,
            mock(IrcClientService.class),
            mock(ConnectionCoordinator.class),
            mock(RuntimeConfigStore.class),
            history);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);
    coordinator.onTargetSelected(pm);

    verify(history).reset(pm);
    verify(history, never()).onTargetSelected(pm);
  }

  @Test
  void reopeningClosedPrivateTargetThenSelectingTriggersHistoryPreloadAgain() {
    UiPort ui = mock(UiPort.class);
    TargetChatHistoryPort history = mock(TargetChatHistoryPort.class);
    TargetCoordinator coordinator =
        newCoordinator(
            ui,
            mock(IrcClientService.class),
            mock(ConnectionCoordinator.class),
            mock(RuntimeConfigStore.class),
            history);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);
    coordinator.openPrivateConversation(new PrivateMessageRequest("libera", "alice"));
    coordinator.onTargetSelected(pm);

    InOrder inOrder = inOrder(history);
    inOrder.verify(history).reset(pm);
    inOrder.verify(history).onTargetSelected(pm);
    verify(history, times(1)).onTargetSelected(pm);
  }

  @Test
  void closeChannelResetsHistoryState() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetChatHistoryPort history = mock(TargetChatHistoryPort.class);
    TargetCoordinator coordinator =
        newCoordinator(ui, irc, connectionCoordinator, runtimeConfig, history);

    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(ui.isChannelDisconnected(channel)).thenReturn(false);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe", null)).thenReturn(Completable.complete());

    coordinator.closeChannel(channel);

    verify(history).reset(channel);
    verify(irc).partChannel(eq("libera"), eq("#ircafe"), isNull());
    verify(runtimeConfig).forgetJoinedChannel("libera", "#ircafe");
    verify(ui).appendStatus(eq(new TargetRef("libera", "status")), eq("(ui)"), anyString());
  }

  @Test
  void closeChannelResetsHistoryBeforeUiClose() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetChatHistoryPort history = mock(TargetChatHistoryPort.class);
    TargetCoordinator coordinator =
        newCoordinator(ui, irc, connectionCoordinator, runtimeConfig, history);

    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(ui.isChannelDisconnected(channel)).thenReturn(true);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    coordinator.closeChannel(channel);

    InOrder inOrder = inOrder(history, ui);
    inOrder.verify(history).reset(channel);
    inOrder.verify(ui).closeTarget(channel);
  }

  @Test
  void closeAndReselectChannelTriggersHistoryPreloadAgain() {
    UiPort ui = mock(UiPort.class);
    IrcClientService irc = mock(IrcClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetChatHistoryPort history = mock(TargetChatHistoryPort.class);
    TargetCoordinator coordinator =
        newCoordinator(ui, irc, connectionCoordinator, runtimeConfig, history);

    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(ui.isChannelDisconnected(channel)).thenReturn(true);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    coordinator.onTargetSelected(channel);
    coordinator.closeChannel(channel);
    coordinator.onTargetSelected(channel);

    InOrder inOrder = inOrder(history);
    inOrder.verify(history).onTargetSelected(channel);
    inOrder.verify(history).reset(channel);
    inOrder.verify(history).onTargetSelected(channel);
  }

  private static TargetCoordinator newCoordinator(
      UiPort ui,
      IrcClientService irc,
      ConnectionCoordinator connectionCoordinator,
      RuntimeConfigStore runtimeConfig,
      TargetChatHistoryPort history) {
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
        history,
        mock(TargetLogMaintenancePort.class),
        mock(java.util.concurrent.ExecutorService.class),
        mock(java.util.concurrent.ScheduledExecutorService.class));
  }
}
