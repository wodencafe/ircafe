package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.QuasselNetworkManagerAction;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort.QueryMode;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboundChatCommandServiceTest {

  private final IrcClientService irc = mock(IrcClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final ChatCommandRuntimeConfigPort runtimeConfig =
      mock(ChatCommandRuntimeConfigPort.class);
  private final AwayRoutingPort awayRoutingState = mock(AwayRoutingPort.class);
  private final ChatHistoryRequestRoutingPort chatHistoryRequestRoutingState =
      mock(ChatHistoryRequestRoutingPort.class);
  private final JoinRoutingPort joinRoutingState = mock(JoinRoutingPort.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final PendingEchoMessagePort pendingEchoMessageState = mock(PendingEchoMessagePort.class);
  private final PendingInvitePort pendingInviteState = mock(PendingInvitePort.class);
  private final WhoisRoutingPort whoisRoutingState = mock(WhoisRoutingPort.class);
  private final IgnoreListCommandPort ignoreListService = mock(IgnoreListCommandPort.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final OutboundChatCommandService service =
      new OutboundChatCommandService(
          irc,
          ui,
          connectionCoordinator,
          targetCoordinator,
          serverCatalog,
          commandTargetPolicy,
          runtimeConfig,
          awayRoutingState,
          chatHistoryRequestRoutingState,
          joinRoutingState,
          labeledResponseRoutingState,
          pendingEchoMessageState,
          pendingInviteState,
          whoisRoutingState,
          ignoreListService);

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
    verify(joinRoutingState).rememberOrigin("libera", "#secret", status);
    verify(irc).sendRaw("libera", "JOIN #secret hunter2");
  }

  @Test
  void joinWithKeyOnMatrixBackendShowsUnsupportedMessageAndDoesNotSendRaw() {
    TargetRef status = new TargetRef("matrix", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handleJoin(disposables, "#room:example.org", "hunter2");

    verify(runtimeConfig).rememberJoinedChannel("matrix", "#room:example.org");
    verify(joinRoutingState).rememberOrigin("matrix", "#room:example.org", status);
    verify(ui).appendStatus(eq(status), eq("(join)"), contains("Matrix backend"));
    verify(irc, never()).sendRaw(anyString(), anyString());
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
    verify(irc).joinChannel("libera", "#ircafe");
  }

  @Test
  void joinOnQuasselBackendUsesRegularJoinPath() {
    TargetRef status = new TargetRef("quassel", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("quassel")).thenReturn(true);
    when(irc.joinChannel("quassel", "#ircafe")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "#ircafe", "");

    verify(runtimeConfig).rememberJoinedChannel("quassel", "#ircafe");
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
    verify(targetCoordinator, never()).disconnectChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partWithoutExplicitChannelDetachesActiveChannelWithTrimmedReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    service.handlePart(disposables, "", "  be right back  ");

    verify(targetCoordinator).disconnectChannel(chan, "be right back");
  }

  @Test
  void partWithoutExplicitChannelRejectsNonChannelSelection() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "", "bye");

    verify(ui)
        .appendStatus(
            status, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
    verify(targetCoordinator, never()).disconnectChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partWithExplicitChannelDetachesChannelOnActiveServer() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef expected = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "  #ircafe ", "  later ");

    verify(targetCoordinator).disconnectChannel(expected, "later");
  }

  @Test
  void partWithMatrixRoomIdDetachesChannelLikeTarget() {
    TargetRef status = new TargetRef("matrix", "status");
    TargetRef room = new TargetRef("matrix", "!abc123:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "!abc123:matrix.org", "later");

    verify(targetCoordinator).disconnectChannel(room, "later");
  }

  @Test
  void partFromActiveMatrixRoomIdDetachesChannelLikeTarget() {
    TargetRef room = new TargetRef("matrix", "!abc123:matrix.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(room);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handlePart(disposables, "", "later");

    verify(targetCoordinator).disconnectChannel(room, "later");
  }

  @Test
  void partWithExplicitNonChannelShowsUsageAndDoesNotDetach() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "alice", "bye");

    verify(ui).appendStatus(status, "(part)", "Usage: /part [#channel] [reason]");
    verify(targetCoordinator, never()).disconnectChannel(any(TargetRef.class), anyString());
  }

  @Test
  void connectWithoutArgUsesActiveServerContext() {
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(pm);

    service.handleConnect("");

    verify(connectionCoordinator).connectOne("libera");
  }

  @Test
  void connectAllKeywordConnectsAllServers() {
    when(targetCoordinator.getActiveTarget()).thenReturn(null);

    service.handleConnect("all");

    verify(connectionCoordinator).connectAll();
  }

  @Test
  void reconnectWithExplicitServerRoutesToCoordinator() {
    service.handleReconnect("oftc");

    verify(connectionCoordinator).reconnectOne("oftc");
  }

  @Test
  void quasselSetupRequestsPromptThenSubmitsAndReconnects() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreSetupPrompt prompt =
        new IrcClientService.QuasselCoreSetupPrompt(
            "quassel", "setup required", List.of("SQLite"), List.of("Database"), Map.of());
    IrcClientService.QuasselCoreSetupRequest request =
        new IrcClientService.QuasselCoreSetupRequest(
            "admin", "secret", "SQLite", "Database", Map.of(), Map.of());

    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.isQuasselCoreSetupPending("quassel")).thenReturn(true);
    when(irc.quasselCoreSetupPrompt("quassel")).thenReturn(Optional.of(prompt));
    when(ui.promptQuasselCoreSetup("quassel", prompt)).thenReturn(Optional.of(request));
    when(irc.submitQuasselCoreSetup("quassel", request)).thenReturn(Completable.complete());

    service.handleQuasselSetup(disposables, "quassel");

    verify(ui).promptQuasselCoreSetup("quassel", prompt);
    verify(irc).submitQuasselCoreSetup("quassel", request);
    verify(connectionCoordinator).markQuasselSetupSubmitted("quassel");
    verify(ui).appendStatus(status, "(qsetup)", "Quassel Core setup submitted. Reconnecting…");
    verify(connectionCoordinator).connectOne("quassel");
  }

  @Test
  void quasselSetupCancelReportsStatusAndDoesNotSubmitOrReconnect() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreSetupPrompt prompt =
        new IrcClientService.QuasselCoreSetupPrompt(
            "quassel", "setup required", List.of("SQLite"), List.of("Database"), Map.of());

    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.isQuasselCoreSetupPending("quassel")).thenReturn(true);
    when(irc.quasselCoreSetupPrompt("quassel")).thenReturn(Optional.of(prompt));
    when(ui.promptQuasselCoreSetup("quassel", prompt)).thenReturn(Optional.empty());

    service.handleQuasselSetup(disposables, "quassel");

    verify(ui).promptQuasselCoreSetup("quassel", prompt);
    verify(ui).appendStatus(status, "(qsetup)", "Quassel Core setup canceled.");
    verify(irc, never()).submitQuasselCoreSetup(anyString(), any());
    verify(connectionCoordinator, never()).connectOne(anyString());
  }

  @Test
  void quasselSetupReportsWhenNoPendingSetupExists() {
    TargetRef status = new TargetRef("quassel", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.isQuasselCoreSetupPending("quassel")).thenReturn(false);

    service.handleQuasselSetup(disposables, "quassel");

    verify(ui).appendStatus(status, "(qsetup)", "No pending Quassel Core setup for this server.");
    verify(irc, never()).submitQuasselCoreSetup(anyString(), any());
    verify(connectionCoordinator, never()).connectOne(anyString());
  }

  @Test
  void quasselSetupOnRegularIrcServerIsRejected() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("libera"))
        .thenReturn(Optional.of(serverWithBackend("libera", IrcProperties.Server.Backend.IRC)));

    service.handleQuasselSetup(disposables, "libera");

    verify(ui)
        .appendStatus(
            eq(status), eq("(qsetup)"), contains("does not use the Quassel Core backend"));
    verify(irc, never()).isQuasselCoreSetupPending(anyString());
    verify(irc, never()).submitQuasselCoreSetup(anyString(), any());
  }

  @Test
  void quasselNetworkListPrintsObservedNetworks() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", true, true, 1, "irc.libera.chat", 6697, true, Map.of());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));

    service.handleQuasselNetwork(disposables, "quassel list");

    verify(ui).appendStatus(status, "(qnet)", "Quassel networks:");
    verify(ui).appendStatus(eq(status), eq("(qnet)"), contains("[2] libera - connected"));
  }

  @Test
  void quasselNetworkAddBuildsCreateRequestAndSubmits() {
    TargetRef status = new TargetRef("quassel", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreCreateNetwork(eq("quassel"), any())).thenReturn(Completable.complete());

    service.handleQuasselNetwork(disposables, "quassel add libera irc.libera.chat 6697 tls");

    ArgumentCaptor<IrcClientService.QuasselCoreNetworkCreateRequest> requestCaptor =
        ArgumentCaptor.forClass(IrcClientService.QuasselCoreNetworkCreateRequest.class);
    verify(irc).quasselCoreCreateNetwork(eq("quassel"), requestCaptor.capture());
    assertEquals("libera", requestCaptor.getValue().networkName());
    assertEquals("irc.libera.chat", requestCaptor.getValue().serverHost());
    assertEquals(6697, requestCaptor.getValue().serverPort());
    assertTrue(requestCaptor.getValue().useTls());
  }

  @Test
  void quasselNetworkEditBuildsUpdateRequestAndSubmits() {
    TargetRef status = new TargetRef("quassel", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreUpdateNetwork(eq("quassel"), eq("libera"), any()))
        .thenReturn(Completable.complete());

    service.handleQuasselNetwork(disposables, "quassel edit libera irc2.libera.chat 6667 plain");

    ArgumentCaptor<IrcClientService.QuasselCoreNetworkUpdateRequest> requestCaptor =
        ArgumentCaptor.forClass(IrcClientService.QuasselCoreNetworkUpdateRequest.class);
    verify(irc).quasselCoreUpdateNetwork(eq("quassel"), eq("libera"), requestCaptor.capture());
    assertEquals("irc2.libera.chat", requestCaptor.getValue().serverHost());
    assertEquals(6667, requestCaptor.getValue().serverPort());
    assertFalse(requestCaptor.getValue().useTls());
  }

  @Test
  void quasselNetworkCommandOnRegularIrcServerIsRejected() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("libera"))
        .thenReturn(Optional.of(serverWithBackend("libera", IrcProperties.Server.Backend.IRC)));

    service.handleQuasselNetwork(disposables, "libera list");

    verify(ui)
        .appendStatus(eq(status), eq("(qnet)"), contains("does not use the Quassel Core backend"));
    verify(irc, never()).quasselCoreNetworks(anyString());
  }

  @Test
  void quasselNetworkManagerConnectActionInvokesBackendAndReopensPrompt() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));
    when(ui.promptQuasselNetworkManagerAction(eq("quassel"), anyList()))
        .thenReturn(Optional.of(QuasselNetworkManagerAction.connect("2")))
        .thenReturn(Optional.empty());
    when(irc.quasselCoreConnectNetwork("quassel", "2")).thenReturn(Completable.complete());

    service.handleQuasselNetworkManager(disposables, "quassel");

    verify(irc).quasselCoreConnectNetwork("quassel", "2");
    verify(ui, times(2)).promptQuasselNetworkManagerAction(eq("quassel"), anyList());
  }

  @Test
  void quasselNetworkManagerConnectErrorAppendsUiErrorAndReopensPrompt() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));
    when(ui.promptQuasselNetworkManagerAction(eq("quassel"), anyList()))
        .thenReturn(Optional.of(QuasselNetworkManagerAction.connect("2")))
        .thenReturn(Optional.empty());
    when(irc.quasselCoreConnectNetwork("quassel", "2"))
        .thenReturn(Completable.error(new IllegalStateException("connect boom")));

    service.handleQuasselNetworkManager(disposables, "quassel");

    verify(irc).quasselCoreConnectNetwork("quassel", "2");
    verify(ui, times(2)).promptQuasselNetworkManagerAction(eq("quassel"), anyList());
    verify(ui).appendError(eq(status), eq("(qnet-ui-error)"), contains("connect boom"));
  }

  @Test
  void quasselNetworkManagerDisconnectErrorAppendsUiErrorAndReopensPrompt() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", true, true, 1, "irc.libera.chat", 6697, true, Map.of());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));
    when(ui.promptQuasselNetworkManagerAction(eq("quassel"), anyList()))
        .thenReturn(Optional.of(QuasselNetworkManagerAction.disconnect("2")))
        .thenReturn(Optional.empty());
    when(irc.quasselCoreDisconnectNetwork("quassel", "2"))
        .thenReturn(Completable.error(new IllegalStateException("disconnect boom")));

    service.handleQuasselNetworkManager(disposables, "quassel");

    verify(irc).quasselCoreDisconnectNetwork("quassel", "2");
    verify(ui, times(2)).promptQuasselNetworkManagerAction(eq("quassel"), anyList());
    verify(ui).appendError(eq(status), eq("(qnet-ui-error)"), contains("disconnect boom"));
  }

  @Test
  void quasselNetworkManagerRemoveErrorAppendsUiErrorAndReopensPrompt() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", true, true, 1, "irc.libera.chat", 6697, true, Map.of());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));
    when(ui.promptQuasselNetworkManagerAction(eq("quassel"), anyList()))
        .thenReturn(Optional.of(QuasselNetworkManagerAction.remove("2")))
        .thenReturn(Optional.empty());
    when(irc.quasselCoreRemoveNetwork("quassel", "2"))
        .thenReturn(Completable.error(new IllegalStateException("remove boom")));

    service.handleQuasselNetworkManager(disposables, "quassel");

    verify(irc).quasselCoreRemoveNetwork("quassel", "2");
    verify(ui, times(2)).promptQuasselNetworkManagerAction(eq("quassel"), anyList());
    verify(ui).appendError(eq(status), eq("(qnet-ui-error)"), contains("remove boom"));
  }

  @Test
  void quasselNetworkManagerAddErrorAppendsUiErrorAndReopensPrompt() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    IrcClientService.QuasselCoreNetworkCreateRequest request =
        new IrcClientService.QuasselCoreNetworkCreateRequest(
            "libera", "irc.libera.chat", 6697, true, "", true, null, List.of());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));
    when(ui.promptQuasselNetworkManagerAction(eq("quassel"), anyList()))
        .thenReturn(Optional.of(QuasselNetworkManagerAction.add(request)))
        .thenReturn(Optional.empty());
    when(irc.quasselCoreCreateNetwork("quassel", request))
        .thenReturn(Completable.error(new IllegalStateException("add boom")));

    service.handleQuasselNetworkManager(disposables, "quassel");

    verify(irc).quasselCoreCreateNetwork("quassel", request);
    verify(ui, times(2)).promptQuasselNetworkManagerAction(eq("quassel"), anyList());
    verify(ui).appendError(eq(status), eq("(qnet-ui-error)"), contains("add boom"));
  }

  @Test
  void quasselNetworkManagerEditErrorAppendsUiErrorAndReopensPrompt() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    IrcClientService.QuasselCoreNetworkUpdateRequest request =
        new IrcClientService.QuasselCoreNetworkUpdateRequest(
            "", "irc2.libera.chat", 6667, false, "", true, null, null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));
    when(ui.promptQuasselNetworkManagerAction(eq("quassel"), anyList()))
        .thenReturn(Optional.of(QuasselNetworkManagerAction.edit("2", request)))
        .thenReturn(Optional.empty());
    when(irc.quasselCoreUpdateNetwork("quassel", "2", request))
        .thenReturn(Completable.error(new IllegalStateException("edit boom")));

    service.handleQuasselNetworkManager(disposables, "quassel");

    verify(irc).quasselCoreUpdateNetwork("quassel", "2", request);
    verify(ui, times(2)).promptQuasselNetworkManagerAction(eq("quassel"), anyList());
    verify(ui).appendError(eq(status), eq("(qnet-ui-error)"), contains("edit boom"));
  }

  @Test
  void quasselNetworkManagerRefreshActionReopensPromptWithoutNetworkMutation() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));
    when(ui.promptQuasselNetworkManagerAction(eq("quassel"), anyList()))
        .thenReturn(Optional.of(QuasselNetworkManagerAction.refresh()))
        .thenReturn(Optional.empty());

    service.handleQuasselNetworkManager(disposables, "quassel");

    verify(irc, times(2)).quasselCoreNetworks("quassel");
    verify(ui, times(2)).promptQuasselNetworkManagerAction(eq("quassel"), anyList());
    verify(irc, never()).quasselCoreConnectNetwork(anyString(), anyString());
    verify(irc, never()).quasselCoreDisconnectNetwork(anyString(), anyString());
    verify(irc, never()).quasselCoreCreateNetwork(anyString(), any());
    verify(irc, never()).quasselCoreUpdateNetwork(anyString(), anyString(), any());
    verify(irc, never()).quasselCoreRemoveNetwork(anyString(), anyString());
  }

  @Test
  void quasselNetworkManagerAddActionCreatesNetworkAndReopensPrompt() {
    TargetRef status = new TargetRef("quassel", "status");
    IrcClientService.QuasselCoreNetworkSummary summary =
        new IrcClientService.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    IrcClientService.QuasselCoreNetworkCreateRequest request =
        new IrcClientService.QuasselCoreNetworkCreateRequest(
            "libera", "irc.libera.chat", 6697, true, "", true, null, List.of());
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.quasselCoreNetworks("quassel")).thenReturn(List.of(summary));
    when(ui.promptQuasselNetworkManagerAction(eq("quassel"), anyList()))
        .thenReturn(Optional.of(QuasselNetworkManagerAction.add(request)))
        .thenReturn(Optional.empty());
    when(irc.quasselCoreCreateNetwork("quassel", request)).thenReturn(Completable.complete());

    service.handleQuasselNetworkManager(disposables, "quassel");

    verify(irc).quasselCoreCreateNetwork("quassel", request);
    verify(ui, times(2)).promptQuasselNetworkManagerAction(eq("quassel"), anyList());
    verify(ui)
        .appendStatus(eq(status), eq("(qnet-ui)"), contains("Requested Quassel network create"));
  }

  @Test
  void quasselNetworkManagerOnRegularIrcServerIsRejected() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(serverCatalog.find("libera"))
        .thenReturn(Optional.of(serverWithBackend("libera", IrcProperties.Server.Backend.IRC)));

    service.handleQuasselNetworkManager(disposables, "libera");

    verify(ui)
        .appendStatus(
            eq(status), eq("(qnet-ui)"), contains("does not use the Quassel Core backend"));
    verify(ui, never()).promptQuasselNetworkManagerAction(anyString(), anyList());
  }

  @Test
  void nickWhileConnectedRequestsChangeWithoutPersistingPreferredNick() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.changeNick("libera", "alice1")).thenReturn(Completable.complete());

    service.handleNick(disposables, "alice1");

    verify(irc).changeNick("libera", "alice1");
    verify(runtimeConfig, never()).rememberNick(anyString(), anyString());
  }

  @Test
  void nickWhileDisconnectedPersistsPreferredNickOnly() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    service.handleNick(disposables, "alice1");

    verify(runtimeConfig).rememberNick("libera", "alice1");
    verify(irc, never()).changeNick(anyString(), anyString());
    verify(ui)
        .appendStatus(
            new TargetRef("libera", "status"),
            "(nick)",
            "Not connected. Saved preferred nick for next connect.");
  }

  @Test
  void quitWithReasonDisconnectsCurrentServerUsingReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(targetCoordinator.safeStatusTarget()).thenReturn(new TargetRef("libera", "status"));

    service.handleQuit("gone for lunch");

    verify(connectionCoordinator).disconnectOne("libera", "gone for lunch");
  }

  @Test
  void sendsLocalEchoWhenEchoMessageIsUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "hello")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);

    service.handleSay(disposables, "hello");

    verify(ui).appendChat(chan, "(me)", "hello", true);
  }

  @Test
  void sendMessageOnQuasselBackendUsesRegularMessagePath() {
    TargetRef chan = new TargetRef("quassel", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("quassel")).thenReturn(true);
    when(irc.sendMessage("quassel", "#ircafe", "hello")).thenReturn(Completable.complete());
    when(irc.currentNick("quassel")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("quassel")).thenReturn(false);

    service.handleSay(disposables, "hello");

    verify(irc).sendMessage("quassel", "#ircafe", "hello");
    verify(ui).appendChat(chan, "(me)", "hello", true);
  }

  @Test
  void suppressesLocalEchoWhenEchoMessageIsAvailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    Instant createdAt = Instant.parse("2026-02-16T00:00:00Z");
    PendingEchoMessagePort.PendingOutboundChat pending =
        new PendingEchoMessagePort.PendingOutboundChat("pending-1", chan, "me", "hello", createdAt);
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "hello")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(pendingEchoMessageState.register(eq(chan), eq("me"), eq("hello"), any(Instant.class)))
        .thenReturn(pending);

    service.handleSay(disposables, "hello");

    verify(ui, never()).appendChat(eq(chan), any(), eq("hello"), eq(true));
    verify(ui).appendPendingOutgoingChat(chan, "pending-1", createdAt, "me", "hello");
  }

  @Test
  void multilineSendFallsBackToSplitLinesWhenNotNegotiatedAndUserConfirms() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isMultilineAvailable("libera")).thenReturn(false);
    when(ui.confirmMultilineSplitFallback(
            chan, 2, 17L, "IRCv3 multiline is not negotiated on this server."))
        .thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "line one")).thenReturn(Completable.complete());
    when(irc.sendMessage("libera", "#ircafe", "line two")).thenReturn(Completable.complete());

    service.handleSay(disposables, "line one\nline two");

    verify(irc).sendMessage("libera", "#ircafe", "line one");
    verify(irc).sendMessage("libera", "#ircafe", "line two");
    verify(irc, never()).sendMessage("libera", "#ircafe", "line one\nline two");
    verify(ui).appendStatus(eq(chan), eq("(send)"), contains("Sending as 2 separate lines."));
  }

  @Test
  void multilineSendCancelsWhenNotNegotiatedAndUserDeclinesFallback() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isMultilineAvailable("libera")).thenReturn(false);
    when(ui.confirmMultilineSplitFallback(
            chan, 2, 17L, "IRCv3 multiline is not negotiated on this server."))
        .thenReturn(false);

    service.handleSay(disposables, "line one\nline two");

    verify(irc, never()).sendMessage(eq("libera"), eq("#ircafe"), any());
    verify(ui).appendStatus(chan, "(send)", "Send canceled.");
  }

  @Test
  void multilineSendUsesBackendAvailabilityReasonWhenProvided() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.backendAvailabilityReason("libera"))
        .thenReturn("Quassel Core backend is not implemented yet");
    when(ui.confirmMultilineSplitFallback(
            chan, 2, 17L, "Quassel Core backend is not implemented yet."))
        .thenReturn(false);

    service.handleSay(disposables, "line one\nline two");

    verify(ui)
        .confirmMultilineSplitFallback(
            chan, 2, 17L, "Quassel Core backend is not implemented yet.");
    verify(irc, never()).sendMessage(eq("libera"), eq("#ircafe"), any());
  }

  @Test
  void multilineSendFallsBackToSplitLinesWhenOverNegotiatedMaxLines() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isMultilineAvailable("libera")).thenReturn(true);
    when(irc.negotiatedMultilineMaxLines("libera")).thenReturn(1);
    when(ui.confirmMultilineSplitFallback(eq(chan), eq(2), eq(17L), contains("max-lines is 1")))
        .thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "line one")).thenReturn(Completable.complete());
    when(irc.sendMessage("libera", "#ircafe", "line two")).thenReturn(Completable.complete());

    service.handleSay(disposables, "line one\nline two");

    verify(irc).sendMessage("libera", "#ircafe", "line one");
    verify(irc).sendMessage("libera", "#ircafe", "line two");
    verify(irc, never()).sendMessage("libera", "#ircafe", "line one\nline two");
  }

  @Test
  void multilineSendUsesBatchPathWhenNegotiatedAndWithinLimits() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isMultilineAvailable("libera")).thenReturn(true);
    when(irc.negotiatedMultilineMaxLines("libera")).thenReturn(5);
    when(irc.negotiatedMultilineMaxBytes("libera")).thenReturn(4096L);
    when(irc.sendMessage("libera", "#ircafe", "line one\nline two"))
        .thenReturn(Completable.complete());

    service.handleSay(disposables, "line one\nline two");

    verify(irc).sendMessage("libera", "#ircafe", "line one\nline two");
    verify(ui, never()).confirmMultilineSplitFallback(any(), anyInt(), anyLong(), any());
  }

  @Test
  void marksPendingMessageFailedWhenSendErrors() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    Instant createdAt = Instant.parse("2026-02-16T00:00:00Z");
    PendingEchoMessagePort.PendingOutboundChat pending =
        new PendingEchoMessagePort.PendingOutboundChat("pending-2", chan, "me", "hello", createdAt);
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "hello"))
        .thenReturn(Completable.error(new RuntimeException("boom")));
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(pendingEchoMessageState.register(eq(chan), eq("me"), eq("hello"), any(Instant.class)))
        .thenReturn(pending);

    service.handleSay(disposables, "hello");

    verify(pendingEchoMessageState).removeById("pending-2");
    verify(ui)
        .failPendingOutgoingChat(
            eq(chan), eq("pending-2"), any(Instant.class), eq("me"), eq("hello"), contains("boom"));
  }

  @Test
  void chatHistorySelectorUsesSelectorOverload() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.requestChatHistoryBefore("libera", "#ircafe", "msgid=abc123", 40))
        .thenReturn(Completable.complete());

    service.handleChatHistoryBefore(disposables, 40, "msgid=abc123");

    verify(irc).requestChatHistoryBefore("libera", "#ircafe", "msgid=abc123", 40);
    verify(chatHistoryRequestRoutingState)
        .remember(
            eq("libera"),
            eq("#ircafe"),
            eq(chan),
            eq(40),
            eq("msgid=abc123"),
            any(Instant.class),
            eq(QueryMode.BEFORE));
  }

  @Test
  void chatHistoryLatestRoutesThroughLatestRequest() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.requestChatHistoryLatest("libera", "#ircafe", "*", 55))
        .thenReturn(Completable.complete());

    service.handleChatHistoryLatest(disposables, 55, "*");

    verify(irc).requestChatHistoryLatest("libera", "#ircafe", "*", 55);
    verify(chatHistoryRequestRoutingState)
        .remember(
            eq("libera"),
            eq("#ircafe"),
            eq(chan),
            eq(55),
            eq("*"),
            any(Instant.class),
            eq(QueryMode.LATEST));
  }

  @Test
  void chatHistoryBetweenRoutesThroughBetweenRequest() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.requestChatHistoryBetween("libera", "#ircafe", "msgid=a", "msgid=b", 30))
        .thenReturn(Completable.complete());

    service.handleChatHistoryBetween(disposables, "msgid=a", "msgid=b", 30);

    verify(irc).requestChatHistoryBetween("libera", "#ircafe", "msgid=a", "msgid=b", 30);
    verify(chatHistoryRequestRoutingState)
        .remember(
            eq("libera"),
            eq("#ircafe"),
            eq(chan),
            eq(30),
            eq("msgid=a .. msgid=b"),
            any(Instant.class),
            eq(QueryMode.BETWEEN));
  }

  @Test
  void chatHistoryAroundRoutesThroughAroundRequest() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.requestChatHistoryAround("libera", "#ircafe", "msgid=anchor", 45))
        .thenReturn(Completable.complete());

    service.handleChatHistoryAround(disposables, "msgid=anchor", 45);

    verify(irc).requestChatHistoryAround("libera", "#ircafe", "msgid=anchor", 45);
    verify(chatHistoryRequestRoutingState)
        .remember(
            eq("libera"),
            eq("#ircafe"),
            eq(chan),
            eq(45),
            eq("msgid=anchor"),
            any(Instant.class),
            eq(QueryMode.AROUND));
  }

  @Test
  void listOpensChannelListTargetAndSendsListRawLine() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef channelList = TargetRef.channelList("libera");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "LIST >10")).thenReturn(Completable.complete());

    service.handleList(disposables, ">10");

    verify(ui).ensureTargetExists(channelList);
    verify(ui).beginChannelList("libera", "Loading channel list (>10)...");
    verify(ui).selectTarget(channelList);
    verify(irc).sendRaw("libera", "LIST >10");
  }

  @Test
  void listOnMatrixBackendShowsUnsupportedMessageAndDoesNotSendRaw() {
    TargetRef status = new TargetRef("matrix", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handleList(disposables, ">10");

    verify(ui).appendStatus(eq(status), eq("(list)"), contains("Matrix backend"));
    verify(ui, never()).beginChannelList(anyString(), anyString());
    verify(irc, never()).sendRaw(anyString(), anyString());
  }

  @Test
  void quoteInjectsLabelWhenLabeledResponseIsAvailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("libera")).thenReturn(true);
    when(labeledResponseRoutingState.prepareOutgoingRaw("libera", "MONITOR +nick"))
        .thenReturn(
            new LabeledResponseRoutingPort.PreparedRawLine(
                "@label=req-1 MONITOR +nick", "req-1", true));
    when(irc.sendRaw("libera", "@label=req-1 MONITOR +nick")).thenReturn(Completable.complete());

    service.handleQuote(disposables, "MONITOR +nick");

    verify(irc).sendRaw("libera", "@label=req-1 MONITOR +nick");
    verify(labeledResponseRoutingState)
        .remember(eq("libera"), eq("req-1"), eq(chan), eq("MONITOR +nick"), any(Instant.class));
    verify(ui)
        .appendStatus(
            eq(status), eq("(quote)"), argThat(s -> s != null && s.contains("{label=req-1}")));
  }

  @Test
  void quoteSendsOriginalRawLineWhenLabeledResponseIsUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("libera")).thenReturn(false);
    when(irc.sendRaw("libera", "MONITOR +nick")).thenReturn(Completable.complete());

    service.handleQuote(disposables, "MONITOR +nick");

    verify(irc).sendRaw("libera", "MONITOR +nick");
    verify(labeledResponseRoutingState, never()).prepareOutgoingRaw(any(), any());
    verify(labeledResponseRoutingState, never()).remember(any(), any(), any(), any(), any());
  }

  @Test
  void quoteOnMatrixBackendShowsUnsupportedMessageAndDoesNotSendRaw() {
    TargetRef chan = new TargetRef("matrix", "#room:example.org");
    TargetRef status = new TargetRef("matrix", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handleQuote(disposables, "MONITOR +nick");

    verify(ui).appendStatus(eq(status), eq("(quote)"), contains("Matrix backend"));
    verify(irc, never()).sendRaw(anyString(), anyString());
  }

  @Test
  void statusRawSendOnMatrixBackendShowsUnsupportedMessageAndDoesNotSendRaw() {
    TargetRef status = new TargetRef("matrix", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));

    service.handleSay(disposables, "WHO #room:example.org");

    verify(ui).appendStatus(eq(status), eq("(raw)"), contains("Matrix backend"));
    verify(irc, never()).sendRaw(anyString(), anyString());
  }

  @Test
  void statusRawSendCanInjectAndTrackLabel() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("libera")).thenReturn(true);
    when(labeledResponseRoutingState.prepareOutgoingRaw("libera", "WHO #ircafe"))
        .thenReturn(
            new LabeledResponseRoutingPort.PreparedRawLine(
                "@label=req-2 WHO #ircafe", "req-2", true));
    when(irc.sendRaw("libera", "@label=req-2 WHO #ircafe")).thenReturn(Completable.complete());

    service.handleSay(disposables, "WHO #ircafe");

    verify(irc).sendRaw("libera", "@label=req-2 WHO #ircafe");
    verify(labeledResponseRoutingState)
        .remember(eq("libera"), eq("req-2"), eq(status), eq("WHO #ircafe"), any(Instant.class));
    verify(ui)
        .appendStatus(
            eq(status), eq("(raw)"), argThat(s -> s != null && s.contains("{label=req-2}")));
  }

  @Test
  void replyCommandSendsTaggedPrivmsgWithoutQuotePrefill() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "@+draft/reply=abc123 PRIVMSG #ircafe :hello there"))
        .thenReturn(Completable.complete());

    service.handleReplyMessage(disposables, "abc123", "hello there");

    verify(irc).sendRaw("libera", "@+draft/reply=abc123 PRIVMSG #ircafe :hello there");
    verify(ui).appendChat(chan, "(me)", "hello there", true);
  }

  @Test
  void replyCommandUsesPendingStateWhenEchoMessageAvailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    Instant createdAt = Instant.parse("2026-02-16T00:00:00Z");
    PendingEchoMessagePort.PendingOutboundChat pending =
        new PendingEchoMessagePort.PendingOutboundChat(
            "pending-reply", chan, "me", "hello", createdAt);
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(pendingEchoMessageState.register(eq(chan), eq("me"), eq("hello"), any(Instant.class)))
        .thenReturn(pending);
    when(irc.sendRaw("libera", "@+draft/reply=abc123 PRIVMSG #ircafe :hello"))
        .thenReturn(Completable.complete());

    service.handleReplyMessage(disposables, "abc123", "hello");

    verify(ui).appendPendingOutgoingChat(chan, "pending-reply", createdAt, "me", "hello");
    verify(ui, never()).appendChat(eq(chan), any(), eq("hello"), eq(true));
  }

  @Test
  void reactCommandSendsTaggedTagmsgAndAppliesLocalReactionWhenEchoUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isDraftReactAvailable("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "@+draft/react=:+1:;+draft/reply=abc123 TAGMSG #ircafe"))
        .thenReturn(Completable.complete());

    service.handleReactMessage(disposables, "abc123", ":+1:");

    verify(irc).sendRaw("libera", "@+draft/react=:+1:;+draft/reply=abc123 TAGMSG #ircafe");
    verify(ui)
        .applyMessageReaction(eq(chan), any(Instant.class), eq("me"), eq("abc123"), eq(":+1:"));
  }

  @Test
  void unreactCommandSendsTaggedTagmsgAndRemovesLocalReactionWhenEchoUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isDraftUnreactAvailable("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "@+draft/unreact=:+1:;+draft/reply=abc123 TAGMSG #ircafe"))
        .thenReturn(Completable.complete());

    service.handleUnreactMessage(disposables, "abc123", ":+1:");

    verify(irc).sendRaw("libera", "@+draft/unreact=:+1:;+draft/reply=abc123 TAGMSG #ircafe");
    verify(ui)
        .removeMessageReaction(eq(chan), any(Instant.class), eq("me"), eq("abc123"), eq(":+1:"));
  }

  @Test
  void editCommandSendsTaggedPrivmsgAndAppliesLocalEditWhenEchoUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isMessageEditAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "@+draft/edit=abc123 PRIVMSG #ircafe :fixed text"))
        .thenReturn(Completable.complete());

    service.handleEditMessage(disposables, "abc123", "fixed text");

    verify(irc).sendRaw("libera", "@+draft/edit=abc123 PRIVMSG #ircafe :fixed text");
    verify(ui)
        .applyMessageEdit(
            eq(chan),
            any(Instant.class),
            eq("me"),
            eq("abc123"),
            eq("fixed text"),
            eq(""),
            eq(java.util.Map.of("draft/edit", "abc123")));
  }

  @Test
  void editCommandRejectsNonOwnedMessageBeforeSending() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageEditAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(false);

    service.handleEditMessage(disposables, "abc123", "fixed text");

    verify(ui).appendStatus(chan, "(edit)", "Can only edit your own messages in this buffer.");
    verify(irc, never()).sendRaw(eq("libera"), any());
    verify(ui, never()).applyMessageEdit(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void redactCommandSendsRedactAndAppliesLocalRedactionWhenEchoUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "REDACT #ircafe abc123")).thenReturn(Completable.complete());

    service.handleRedactMessage(disposables, "abc123", "");

    verify(irc).sendRaw("libera", "REDACT #ircafe abc123");
    verify(ui)
        .applyMessageRedaction(
            eq(chan),
            any(Instant.class),
            eq("me"),
            eq("abc123"),
            eq(""),
            eq(java.util.Map.of("draft/delete", "abc123")));
  }

  @Test
  void redactCommandWithReasonSendsTrailingReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(true);
    when(irc.sendRaw("libera", "REDACT #ircafe abc123 :cleanup old context"))
        .thenReturn(Completable.complete());

    service.handleRedactMessage(disposables, "abc123", "cleanup old context");

    verify(irc).sendRaw("libera", "REDACT #ircafe abc123 :cleanup old context");
  }

  @Test
  void redactCommandRejectsNonOwnedMessageBeforeSending() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(false);

    service.handleRedactMessage(disposables, "abc123", "");

    verify(ui).appendStatus(chan, "(redact)", "Can only redact your own messages in this buffer.");
    verify(irc, never()).sendRaw(eq("libera"), any());
    verify(ui, never()).applyMessageRedaction(any(), any(), any(), any(), any(), any());
  }

  @Test
  void markReadSendsReadMarkerAndClearsUnreadForActiveConversation() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class)))
        .thenReturn(Completable.complete());

    service.handleMarkRead(disposables);

    verify(ui).setReadMarker(eq(chan), anyLong());
    verify(ui).clearUnread(chan);
    verify(irc).sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class));
  }

  @Test
  void markReadShowsCapabilityHintWhenReadMarkerUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleMarkRead(disposables);

    verify(ui).appendStatus(status, "(markread)", "read-marker is not negotiated on this server.");
    verify(irc, never()).sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class));
  }

  @Test
  void markReadShowsBackendAvailabilityReasonWhenProvided() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);
    when(irc.backendAvailabilityReason("libera"))
        .thenReturn("Quassel Core backend is not implemented yet");

    service.handleMarkRead(disposables);

    verify(ui).appendStatus(status, "(markread)", "Quassel Core backend is not implemented yet.");
    verify(irc, never()).sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class));
  }

  @Test
  void markReadFallsBackToNegotiationReasonWhenBackendHasNoSpecificReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleMarkRead(disposables);

    verify(ui).appendStatus(status, "(markread)", "read-marker is not negotiated on this server.");
    verify(irc, never()).sendReadMarker(eq("libera"), eq("#ircafe"), any(Instant.class));
  }

  @Test
  void helpAnnotatesEditAndRedactAsUnavailableWhenCapsNotNegotiated() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(eq(chan), eq("(help)"), contains("/edit <msgid> <message> (unavailable:"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains("/redact <msgid> [reason] (alias: /delete) (unavailable:"));
    verify(ui).appendStatus(eq(chan), eq("(help)"), contains("/markread (unavailable:"));
  }

  @Test
  void helpUsesBackendAvailabilityReasonWhenPresent() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.backendAvailabilityReason("libera"))
        .thenReturn("Quassel Core backend is not implemented yet");
    when(irc.isMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/edit <msgid> <message> (unavailable: Quassel Core backend is not implemented yet)"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/redact <msgid> [reason] (alias: /delete) (unavailable: Quassel Core backend is not implemented yet)"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains("/markread (unavailable: Quassel Core backend is not implemented yet)"));
  }

  @Test
  void helpUsesNegotiationFallbackWhenBackendHasNoSpecificReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/edit <msgid> <message> (unavailable: requires negotiated draft/message-edit or message-edit)"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/redact <msgid> [reason] (alias: /delete) (unavailable: requires negotiated draft/message-redaction or message-redaction)"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/markread (unavailable: requires negotiated read-marker or draft/read-marker)"));
  }

  @Test
  void helpShowsEditAndRedactWithoutUnavailableSuffixWhenCapsNegotiated() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageEditAvailable("libera")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);

    service.handleHelp("edit");
    service.handleHelp("redact");
    service.handleHelp("markread");

    verify(ui).appendStatus(chan, "(help)", "/edit <msgid> <message>");
    verify(ui).appendStatus(chan, "(help)", "/redact <msgid> [reason] (alias: /delete)");
    verify(ui).appendStatus(chan, "(help)", "/markread");
  }

  @Test
  void helpDccShowsCommandsAndUiHint() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    service.handleHelp("dcc");

    verify(ui).appendStatus(chan, "(help)", "/dcc chat <nick>");
    verify(ui).appendStatus(chan, "(help)", "/dcc send <nick> <file-path>");
    verify(ui).appendStatus(chan, "(help)", "/dcc accept <nick>");
    verify(ui).appendStatus(chan, "(help)", "/dcc get <nick> [save-path]");
    verify(ui)
        .appendStatus(chan, "(help)", "/dcc msg <nick> <text>  (alias: /dccmsg <nick> <text>)");
    verify(ui).appendStatus(chan, "(help)", "/dcc close <nick>  /dcc list  /dcc panel");
    verify(ui).appendStatus(chan, "(help)", "UI: right-click a nick and use the DCC submenu.");
  }

  @Test
  void inviteBlockAddsMaskAndRemovesInviteWhenNickIsPresent() {
    TargetRef status = new TargetRef("libera", "status");
    PendingInvitePort.PendingInvite invite =
        new PendingInvitePort.PendingInvite(
            12L,
            Instant.parse("2026-02-16T00:00:00Z"),
            Instant.parse("2026-02-16T00:00:00Z"),
            "libera",
            "#ircafe",
            "alice",
            "me",
            "",
            true,
            1);
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
    PendingInvitePort.PendingInvite invite =
        new PendingInvitePort.PendingInvite(
            27L,
            Instant.parse("2026-02-16T00:00:00Z"),
            Instant.parse("2026-02-16T00:00:00Z"),
            "libera",
            "#ircafe",
            "alice",
            "me",
            "",
            true,
            1);
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
    PendingInvitePort.PendingInvite invite =
        new PendingInvitePort.PendingInvite(
            31L,
            Instant.parse("2026-02-16T00:00:00Z"),
            Instant.parse("2026-02-16T00:00:00Z"),
            "libera",
            "#ircafe",
            "server",
            "me",
            "",
            true,
            1);
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.latestForServer("libera")).thenReturn(invite);

    service.handleInviteBlock("last");

    verify(ignoreListService, never()).addMask(anyString(), anyString());
    verify(pendingInviteState, never()).remove(anyLong());
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
