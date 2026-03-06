package cafe.woden.ircclient.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.ConnectionRuntimeConfigPort;
import cafe.woden.ircclient.irc.BackendNotAvailableException;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectionCoordinatorTest {

  private static final LogProperties LOG_PROPS =
      new LogProperties(false, true, true, true, true, 0, 50_000, 250, null);

  @Test
  void queuedConnectDuringDisconnectReconnectsAfterDisconnectedEvent() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);
    when(irc.connect("libera")).thenReturn(Completable.complete());
    when(irc.disconnect("libera", null)).thenReturn(Completable.complete());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
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
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera", "oftc"));
    when(serverCatalog.containsId(anyString())).thenReturn(true);
    when(irc.connect(anyString())).thenReturn(Completable.complete());
    when(irc.disconnect(anyString(), any())).thenReturn(Completable.complete());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
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
  void startupConnectSkipsServersWithAutoConnectDisabled() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera", "oftc", "snoonet"));
    when(runtimeConfig.readServerAutoConnectOnStartByServer())
        .thenReturn(Map.of("libera", false, "snoonet", true));
    when(irc.connect(anyString())).thenReturn(Completable.complete());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.connectAutoConnectOnStartServers();

    verify(irc, never()).connect("libera");
    verify(irc).connect("oftc");
    verify(irc).connect("snoonet");
  }

  @Test
  void onServersUpdatedTriggersControlledReconnectWhenBackendChanges() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("hybrid"));
    when(serverRegistry.servers())
        .thenReturn(
            List.of(
                server("hybrid", "irc.example.net", 6697, true, IrcProperties.Server.Backend.IRC)));
    when(serverCatalog.containsId("hybrid")).thenReturn(true);
    when(irc.connect("hybrid")).thenReturn(Completable.complete());
    when(irc.disconnect("hybrid")).thenReturn(Completable.complete());
    when(runtimeConfig.readPrivateMessageTargets("hybrid")).thenReturn(List.of());
    when(runtimeConfig.readKnownChannels("hybrid")).thenReturn(List.of());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "hybrid", new IrcEvent.Connected(Instant.now(), "irc.example.net", 6697, "tester"), null);

    coordinator.onServersUpdated(
        List.of(
            server(
                "hybrid",
                "irc.example.net",
                6697,
                true,
                IrcProperties.Server.Backend.QUASSEL_CORE)),
        null);

    verify(irc, times(1)).disconnect("hybrid");
    verify(irc, times(1)).connect("hybrid");
  }

  @Test
  void connectFailureFromUnavailableBackendClearsDesiredIntent() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("quassel"));
    when(serverCatalog.containsId("quassel")).thenReturn(true);
    when(irc.connect("quassel"))
        .thenReturn(
            Completable.error(
                new BackendNotAvailableException(
                    IrcProperties.Server.Backend.QUASSEL_CORE,
                    "connect",
                    "quassel",
                    "not implemented yet")));

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.connectOne("quassel");

    verify(ui, timeout(1_000)).setServerDesiredOnline("quassel", true);
    verify(ui, timeout(1_000)).setServerDesiredOnline("quassel", false);
    verify(ui, timeout(1_000))
        .appendError(
            eq(new TargetRef("quassel", "status")),
            eq("(conn-error)"),
            eq("Quassel Core backend is not implemented yet (connect) for server 'quassel'"));
  }

  @Test
  void externalConnectingEventPromotesDesiredIntentInsteadOfForcingDisconnect() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
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
  void externalReconnectingEventPromotesDesiredIntentInsteadOfForcingDisconnect() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "libera", new IrcEvent.Reconnecting(Instant.now(), 1, 5_000L, "Ping timeout"), null);

    verify(irc, never()).disconnect(anyString(), any());
    verify(ui, atLeastOnce()).setServerDesiredOnline("libera", true);
    verify(ui, atLeastOnce()).setConnectionControlsEnabled(false, true);
  }

  @Test
  void reconnectingEventPublishesRetryDiagnostics() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);
    when(irc.connect("libera")).thenReturn(Completable.complete());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
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
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);
    when(runtimeConfig.readPrivateMessageTargets("libera")).thenReturn(List.of("Alice", "Bob"));
    when(runtimeConfig.readKnownChannels("libera")).thenReturn(List.of("#ircafe"));

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "libera", new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "alice-me"), null);

    verify(ui, timeout(2_000).atLeastOnce()).ensureTargetExists(new TargetRef("libera", "Alice"));
    verify(ui, timeout(2_000).atLeastOnce()).ensureTargetExists(new TargetRef("libera", "Bob"));
    verify(ui, timeout(2_000).atLeastOnce()).ensureTargetExists(new TargetRef("libera", "#ircafe"));
    verify(ui, timeout(2_000).atLeastOnce())
        .setChannelDisconnected(new TargetRef("libera", "#ircafe"), true);
  }

  @Test
  void connectedEventSkipsKnownCorruptPersistedPrivateMessageTargets() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);
    when(runtimeConfig.readPrivateMessageTargets("libera")).thenReturn(List.of("title", "Alice"));
    when(runtimeConfig.readKnownChannels("libera")).thenReturn(List.of());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "libera", new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "alice-me"), null);

    verify(ui, timeout(2_000).atLeastOnce()).ensureTargetExists(new TargetRef("libera", "Alice"));
    verify(ui, never()).ensureTargetExists(new TargetRef("libera", "title"));
  }

  @Test
  void connectedEventWithFallbackNickDoesNotPersistNickToConfig() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(serverCatalog.containsId("libera")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
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
  }

  @Test
  void connectedEventDefersUntilConnectionReadyWhenBackendIsNotReady() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("quassel"));
    when(serverCatalog.containsId("quassel")).thenReturn(true);
    when(irc.backendAvailabilityReason("quassel"))
        .thenReturn("Quassel protocol negotiated, but login/session handshake is not complete");
    when(irc.currentNick("quassel")).thenReturn(Optional.of("readyNick"));
    when(runtimeConfig.readPrivateMessageTargets("quassel")).thenReturn(List.of("Alice"));
    when(runtimeConfig.readKnownChannels("quassel")).thenReturn(List.of());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.Connected(Instant.now(), "core.local", 4242, "transportNick"),
        null);

    verify(ui).setChatCurrentNick("quassel", "transportNick");
    verify(ui, never()).ensureTargetExists(new TargetRef("quassel", "Alice"));

    coordinator.handleConnectivityEvent(
        "quassel", new IrcEvent.ConnectionReady(Instant.now()), null);

    verify(ui).setChatCurrentNick("quassel", "readyNick");
    verify(ui, timeout(2_000).atLeastOnce()).ensureTargetExists(new TargetRef("quassel", "Alice"));
  }

  @Test
  void connectionFeaturesSetupRequiredShowsNoticeAndTurnsServerOffline() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("quassel"));
    when(serverCatalog.containsId("quassel")).thenReturn(true);
    when(irc.connect("quassel")).thenReturn(Completable.never());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.connectOne("quassel");
    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(),
            "quassel-phase=setup-required;detail=Quassel Core setup is required before login"),
        null);

    TargetRef status = new TargetRef("quassel", "status");
    verify(ui, atLeastOnce()).ensureTargetExists(status);
    verify(ui, atLeastOnce()).setServerDesiredOnline("quassel", false);
    verify(ui)
        .enqueueStatusNotice(
            argThat(
                text ->
                    text != null
                        && text.contains("setup required")
                        && text.contains("/quasselsetup quassel")),
            eq(status));
    verify(ui)
        .appendStatusAt(
            eq(status),
            any(Instant.class),
            eq("(conn)"),
            eq("Quassel Core setup is required before this connection can log in."));
    verify(ui, atLeastOnce())
        .setConnectionStatusText(
            argThat(text -> text != null && text.contains("Quassel setup required")));
  }

  @Test
  void connectionFeaturesSetupRequiredAutoPromptsAndSubmitsSetup() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("quassel"));
    when(serverCatalog.containsId("quassel")).thenReturn(true);
    when(irc.connect("quassel")).thenReturn(Completable.never());
    when(irc.isQuasselCoreSetupPending("quassel")).thenReturn(true);
    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        new QuasselCoreControlPort.QuasselCoreSetupPrompt(
            "quassel", "setup required", List.of("SQLite"), List.of("Database"), Map.of());
    QuasselCoreControlPort.QuasselCoreSetupRequest request =
        new QuasselCoreControlPort.QuasselCoreSetupRequest(
            "admin", "secret", "SQLite", "Database", Map.of(), Map.of());
    when(irc.quasselCoreSetupPrompt("quassel")).thenReturn(Optional.of(prompt));
    when(ui.promptQuasselCoreSetup("quassel", prompt)).thenReturn(Optional.of(request));
    when(irc.submitQuasselCoreSetup("quassel", request)).thenReturn(Completable.complete());
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of());

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.connectOne("quassel");
    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(),
            "quassel-phase=setup-required;detail=Quassel Core setup is required before login"),
        null);
    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), "quassel-phase=sync-ready"),
        null);

    TargetRef status = new TargetRef("quassel", "status");
    verify(ui).promptQuasselCoreSetup("quassel", prompt);
    verify(irc).submitQuasselCoreSetup("quassel", request);
    verify(ui).appendStatus(status, "(qsetup)", "Quassel Core setup submitted. Reconnecting…");
    verify(ui)
        .appendStatus(
            eq(status),
            eq("(qsetup)"),
            argThat(text -> text != null && text.contains("Opening Quassel Network Manager")));
    verify(ui).openQuasselNetworkManager("quassel");
    verify(irc, times(2)).connect("quassel");
  }

  @Test
  void connectionFeaturesProtocolNegotiatedAppendsProgressStatus() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("quassel"));
    when(serverCatalog.containsId("quassel")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(), "quassel-phase=protocol-negotiated;detail=probe=datastream"),
        null);

    verify(ui)
        .appendStatusAt(
            eq(new TargetRef("quassel", "status")),
            any(Instant.class),
            eq("(conn)"),
            eq("Quassel protocol negotiated; authenticating core session…"));
  }

  @Test
  void connectionFeaturesSyncReadyAppendsProgressStatusToStatusAndActiveTarget() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("quassel"));
    when(serverCatalog.containsId("quassel")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    TargetRef active = new TargetRef("quassel", "#ircafe");
    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(), "quassel-phase=sync-ready;detail=backlog complete"),
        active);

    verify(ui)
        .appendStatusAt(
            eq(new TargetRef("quassel", "status")),
            any(Instant.class),
            eq("(conn)"),
            eq("Quassel sync complete; connection ready."));
    verify(ui)
        .appendStatusAt(
            eq(active),
            any(Instant.class),
            eq("(conn)"),
            eq("Quassel sync complete; connection ready."));
  }

  @Test
  void connectionFeaturesPhaseProgressionAppendsAllUserVisibleStatusMessages() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("quassel"));
    when(serverCatalog.containsId("quassel")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(), "quassel-phase=transport-connected;detail=tcp"),
        null);
    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(), "quassel-phase=protocol-negotiated;detail=probe=datastream"),
        null);
    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(), "quassel-phase=authenticating;detail=login"),
        null);
    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(), "quassel-phase=session-established;detail=session"),
        null);
    coordinator.handleConnectivityEvent(
        "quassel",
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(), "quassel-phase=sync-ready;detail=complete"),
        null);

    TargetRef status = new TargetRef("quassel", "status");
    verify(ui)
        .appendStatusAt(
            eq(status),
            any(Instant.class),
            eq("(conn)"),
            eq("Quassel transport connected; negotiating protocol…"));
    verify(ui)
        .appendStatusAt(
            eq(status),
            any(Instant.class),
            eq("(conn)"),
            eq("Quassel protocol negotiated; authenticating core session…"));
    verify(ui)
        .appendStatusAt(
            eq(status), any(Instant.class), eq("(conn)"), eq("Authenticating with Quassel Core…"));
    verify(ui)
        .appendStatusAt(
            eq(status),
            any(Instant.class),
            eq("(conn)"),
            eq("Quassel session established; waiting for sync…"));
    verify(ui)
        .appendStatusAt(
            eq(status),
            any(Instant.class),
            eq("(conn)"),
            eq("Quassel sync complete; connection ready."));
  }

  @Test
  void reconnectingAndDisconnectedEventsAppendClearStatusMessages() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("quassel"));
    when(serverCatalog.containsId("quassel")).thenReturn(true);

    ConnectionCoordinator coordinator =
        new ConnectionCoordinator(
            irc,
            irc,
            irc,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            LOG_PROPS,
            trayNotificationService);

    TargetRef active = new TargetRef("quassel", "#ircafe");
    coordinator.handleConnectivityEvent(
        "quassel", new IrcEvent.Reconnecting(Instant.now(), 2, 5_000L, "Ping timeout"), active);
    coordinator.handleConnectivityEvent(
        "quassel", new IrcEvent.Disconnected(Instant.now(), "Ping timeout"), active);

    verify(ui)
        .appendStatus(
            eq(new TargetRef("quassel", "status")),
            eq("(conn)"),
            eq("Reconnecting in 5s (attempt 2) — Ping timeout"));
    verify(ui)
        .appendStatus(
            eq(active), eq("(conn)"), eq("Reconnecting in 5s (attempt 2) — Ping timeout"));
    verify(ui)
        .appendStatus(
            eq(new TargetRef("quassel", "status")), eq("(conn)"), eq("Disconnected: Ping timeout"));
    verify(ui).appendStatus(eq(active), eq("(conn)"), eq("Disconnected: Ping timeout"));
  }

  @Test
  void constructorRestoresJoinedChannelsAsDetached() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    UiPort ui = mock(UiPort.class);
    ServerRegistry serverRegistry = mock(ServerRegistry.class);
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    ConnectionRuntimeConfigPort runtimeConfig = mock(ConnectionRuntimeConfigPort.class);
    TrayNotificationsPort trayNotificationService = mock(TrayNotificationsPort.class);

    when(serverRegistry.serverIds()).thenReturn(Set.of("libera"));
    when(runtimeConfig.readKnownChannels("libera")).thenReturn(List.of("#ircafe", "#java"));

    new ConnectionCoordinator(
        irc,
        irc,
        irc,
        ui,
        serverRegistry,
        serverCatalog,
        runtimeConfig,
        LOG_PROPS,
        trayNotificationService);

    verify(ui, atLeastOnce()).ensureTargetExists(new TargetRef("libera", "#ircafe"));
    verify(ui, atLeastOnce()).setChannelDisconnected(new TargetRef("libera", "#ircafe"), true);
    verify(ui, atLeastOnce()).ensureTargetExists(new TargetRef("libera", "#java"));
    verify(ui, atLeastOnce()).setChannelDisconnected(new TargetRef("libera", "#java"), true);
  }

  private static IrcProperties.Server server(
      String id, String host, int port, boolean tls, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id, host, port, tls, "", "tester", "tester", "Tester", null, null, List.of(), List.of(),
        null, backend);
  }
}
