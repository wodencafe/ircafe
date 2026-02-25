package cafe.woden.ircclient.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectionCoordinatorTest {

  private static final LogProperties LOG_PROPS =
      new LogProperties(false, true, true, true, true, 0, null);

  @Test
  void queuedConnectDuringDisconnectReconnectsAfterDisconnectedEvent() {
    IrcClientService irc = mock(IrcClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);
    when(irc.connect("libera")).thenReturn(Completable.complete());
    when(irc.disconnect("libera", null)).thenReturn(Completable.complete());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.connectOne("libera");
    coordinator.disconnectOne("libera");
    coordinator.connectOne("libera");

    verify(irc, times(1)).connect("libera");
    verify(irc, times(1)).disconnect("libera", null);

    coordinator.handleConnectivityEvent(
        "libera", new IrcEvent.Disconnected(Instant.now(), "client requested disconnect"), null);

    verify(irc, times(2)).connect("libera");
  }

  @Test
  void globalControlsReflectDesiredIntentAcrossServers() {
    IrcClientService irc = mock(IrcClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera", "oftc"));
    when(serverCatalog.containsId(anyString())).thenReturn(true);
    when(irc.connect(anyString())).thenReturn(Completable.complete());
    when(irc.disconnect(anyString(), any())).thenReturn(Completable.complete());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.connectOne("libera");
    coordinator.connectOne("oftc");
    coordinator.disconnectOne("libera");
    coordinator.disconnectOne("oftc");

    verify(ui, atLeastOnce()).setServerDesiredOnline("libera", true);
    verify(ui, atLeastOnce()).setServerDesiredOnline("libera", false);
    verify(ui, atLeastOnce()).setServerDesiredOnline("oftc", true);
    verify(ui, atLeastOnce()).setServerDesiredOnline("oftc", false);
    verify(ui, atLeastOnce()).setConnectionControlsEnabled(false, true);
    verify(ui, atLeastOnce()).setConnectionControlsEnabled(true, true);
    verify(ui, atLeastOnce()).setConnectionControlsEnabled(true, false);
  }

  @Test
  void externalConnectingEventPromotesDesiredIntentInsteadOfForcingDisconnect() {
    IrcClientService irc = mock(IrcClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "libera",
        new IrcEvent.Connecting(Instant.now(), "irc.libera.chat", 6697, "zimmedon"),
        null);

    verify(irc, never()).disconnect(anyString(), any());
    verify(ui, atLeastOnce()).setConnectionControlsEnabled(false, true);
  }

  @Test
  void reconnectingEventPublishesRetryDiagnostics() {
    IrcClientService irc = mock(IrcClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);
    when(irc.connect("libera")).thenReturn(Completable.complete());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);
    coordinator.connectOne("libera");

    coordinator.handleConnectivityEvent(
        "libera", new IrcEvent.Reconnecting(Instant.now(), 1, 5_000L, "Ping timeout"), null);

    verify(ui, atLeastOnce())
        .setServerConnectionDiagnostics(eq("libera"), eq("Ping timeout"), any());
  }

  @Test
  void connectedEventRestoresSavedPrivateMessageTargets() {
    IrcClientService irc = mock(IrcClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);
    when(runtimeConfig.readPrivateMessageTargets("libera")).thenReturn(List.of("Alice", "Bob"));
    when(runtimeConfig.readJoinedChannels("libera")).thenReturn(List.of("#ircafe"));

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "libera", new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "alice-me"), null);

    verify(ui, atLeastOnce()).ensureTargetExists(new TargetRef("libera", "Alice"));
    verify(ui, atLeastOnce()).ensureTargetExists(new TargetRef("libera", "Bob"));
    verify(ui, atLeastOnce()).ensureTargetExists(new TargetRef("libera", "#ircafe"));
    verify(ui, atLeastOnce()).setChannelDetached(new TargetRef("libera", "#ircafe"), true);
    verify(runtimeConfig, never()).rememberNick(anyString(), anyString());
  }

  @Test
  void connectedEventWithFallbackNickDoesNotPersistNickToConfig() {
    IrcClientService irc = mock(IrcClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "libera",
        new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "preferredNick1"),
        null);

    verify(ui).setChatCurrentNick("libera", "preferredNick1");
    verify(runtimeConfig, never()).rememberNick(anyString(), anyString());
  }

  @Test
  void constructorRestoresJoinedChannelsAsDetached() {
    IrcClientService irc = mock(IrcClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(runtimeConfig.readJoinedChannels("libera")).thenReturn(List.of("#ircafe", "#java"));

    new ConnectionCoordinator(
        irc, ui, serverRegistry, serverCatalog, runtimeConfig, LOG_PROPS, trayNotificationService);

    verify(ui, atLeastOnce()).ensureTargetExists(new TargetRef("libera", "#ircafe"));
    verify(ui, atLeastOnce()).setChannelDetached(new TargetRef("libera", "#ircafe"), true);
    verify(ui, atLeastOnce()).ensureTargetExists(new TargetRef("libera", "#java"));
    verify(ui, atLeastOnce()).setChannelDetached(new TargetRef("libera", "#java"), true);
  }
}
