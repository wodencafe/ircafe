package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.roster.UserhostQueryService;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TargetCoordinatorLifecycleMockVerifyTest {

  @Test
  void detachChannelMarksDetachedBeforeSendingPart() {
    UiPort ui = mock(UiPort.class);
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);
    TargetRef channel = new TargetRef("libera", "#ircafe");

    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.partChannel("libera", "#ircafe", null)).thenReturn(Completable.complete());

    coordinator.disconnectChannel(channel);

    InOrder inOrder = inOrder(ui, irc);
    inOrder.verify(ui).setChannelDisconnected(channel, true);
    inOrder.verify(irc).partChannel("libera", "#ircafe", null);
  }

  @Test
  void closeAttachedChannelForgetsAndClosesBeforeParting() {
    UiPort ui = mock(UiPort.class);
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);
    TargetRef channel = new TargetRef("libera", "#ircafe");

    when(ui.isChannelDisconnected(channel)).thenReturn(false);
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
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
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
    inOrder.verify(ui).setChannelDisconnected(channel, true);
    inOrder.verify(irc).joinChannel("libera", "#ircafe");
    inOrder.verify(ui).setChannelDisconnected(channel, false);
    verify(irc, never()).partChannel("libera", "#ircafe");
  }

  @Test
  void joinOnQuasselServerDoesNotPersistJoinedChannel() {
    UiPort ui = mock(UiPort.class);
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    when(serverRegistry.find("quassel"))
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
    TargetCoordinator coordinator =
        newCoordinator(
            ui,
            mock(UserListStore.class),
            irc,
            connectionCoordinator,
            runtimeConfig,
            serverRegistry);
    TargetRef channel = new TargetRef("quassel", "#ircafe{net:5}");

    when(connectionCoordinator.isConnected("quassel")).thenReturn(true);
    when(irc.joinChannel("quassel", "#ircafe{net:5}")).thenReturn(Completable.complete());

    coordinator.joinChannel(channel);

    verify(runtimeConfig, never()).rememberJoinedChannel("quassel", "#ircafe{net:5}");
    verify(irc).joinChannel("quassel", "#ircafe{net:5}");
    verify(ui).setChannelDisconnected(channel, true);
  }

  @Test
  void observedChannelActivityClearsDisconnectedStateForAttachedChannel() {
    UiPort ui = mock(UiPort.class);
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);
    TargetRef channel = new TargetRef("quassel", "#ircafe{net:5}");

    when(ui.isChannelDisconnected(channel)).thenReturn(true);

    coordinator.onChannelActivityObserved("quassel", "#ircafe{net:5}");

    verify(ui).setChannelDisconnected(channel, false);
    verify(irc, never()).partChannel("quassel", "#ircafe{net:5}", null);
  }

  @Test
  void observedChannelActivityDoesNotReattachUserDetachedChannel() {
    UiPort ui = mock(UiPort.class);
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator = newCoordinator(ui, irc, connectionCoordinator, runtimeConfig);
    TargetRef channel = new TargetRef("quassel", "#ircafe{net:5}");

    when(connectionCoordinator.isConnected("quassel")).thenReturn(true);
    when(irc.partChannel("quassel", "#ircafe{net:5}", null)).thenReturn(Completable.complete());

    coordinator.disconnectChannel(channel);
    coordinator.onChannelActivityObserved("quassel", "#ircafe{net:5}");

    verify(ui, never()).setChannelDisconnected(channel, false);
  }

  @Test
  void matrixSetNameObservationRefreshesTranscriptLabelsWithoutRosterDelta() {
    UiPort ui = mock(UiPort.class);
    UserListStore userListStore = mock(UserListStore.class);
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TargetCoordinator coordinator =
        newCoordinator(ui, userListStore, irc, connectionCoordinator, runtimeConfig);

    IrcEvent.UserSetNameObserved event =
        new IrcEvent.UserSetNameObserved(
            Instant.now(),
            "@bob:matrix.example.org",
            "Bob",
            IrcEvent.UserSetNameObserved.Source.EXTENDED_JOIN);

    when(userListStore.updateRealNameAcrossChannels("matrix", "@bob:matrix.example.org", "Bob"))
        .thenReturn(Set.of());
    when(userListStore.isNickPresentOnServer("matrix", "@bob:matrix.example.org"))
        .thenReturn(false);

    coordinator.onUserSetNameObserved("matrix", event);

    verify(ui).refreshMatrixTranscriptDisplayName("matrix", "@bob:matrix.example.org");
  }

  private static TargetCoordinator newCoordinator(
      UiPort ui,
      IrcBackendClientService irc,
      ConnectionCoordinator connectionCoordinator,
      RuntimeConfigStore runtimeConfig) {
    return newCoordinator(
        ui,
        mock(UserListStore.class),
        irc,
        connectionCoordinator,
        runtimeConfig,
        mock(ServerRegistry.class));
  }

  private static TargetCoordinator newCoordinator(
      UiPort ui,
      UserListStore userListStore,
      IrcBackendClientService irc,
      ConnectionCoordinator connectionCoordinator,
      RuntimeConfigStore runtimeConfig) {
    return newCoordinator(
        ui, userListStore, irc, connectionCoordinator, runtimeConfig, mock(ServerRegistry.class));
  }

  private static TargetCoordinator newCoordinator(
      UiPort ui,
      UserListStore userListStore,
      IrcBackendClientService irc,
      ConnectionCoordinator connectionCoordinator,
      RuntimeConfigStore runtimeConfig,
      ServerRegistry serverRegistry) {
    return new TargetCoordinator(
        ui,
        userListStore,
        IrcTargetMembershipPort.from(irc),
        irc,
        serverRegistry,
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
