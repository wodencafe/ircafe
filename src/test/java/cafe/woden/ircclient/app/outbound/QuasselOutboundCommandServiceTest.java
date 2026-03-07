package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.irc.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QuasselOutboundCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      new OutboundBackendFeatureRegistry(List.of(new QuasselOutboundBackendFeatureAdapter()));
  private final OutboundBackendCapabilityPolicy outboundBackendCapabilityPolicy =
      new OutboundBackendCapabilityPolicy(commandTargetPolicy, outboundBackendFeatureRegistry);
  private final QuasselOutboundCommandSupport quasselCommandSupport =
      new QuasselOutboundCommandSupport(serverCatalog, outboundBackendCapabilityPolicy);
  private final QuasselOutboundCommandService service =
      new QuasselOutboundCommandService(
          irc, ui, connectionCoordinator, targetCoordinator, quasselCommandSupport);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void quasselSetupRequestsPromptThenSubmitsAndReconnects() {
    TargetRef status = new TargetRef("quassel", "status");
    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        new QuasselCoreControlPort.QuasselCoreSetupPrompt(
            "quassel", "setup required", List.of("SQLite"), List.of("Database"), Map.of());
    QuasselCoreControlPort.QuasselCoreSetupRequest request =
        new QuasselCoreControlPort.QuasselCoreSetupRequest(
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
    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        new QuasselCoreControlPort.QuasselCoreSetupPrompt(
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
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

    ArgumentCaptor<QuasselCoreControlPort.QuasselCoreNetworkCreateRequest> requestCaptor =
        ArgumentCaptor.forClass(QuasselCoreControlPort.QuasselCoreNetworkCreateRequest.class);
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

    ArgumentCaptor<QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest> requestCaptor =
        ArgumentCaptor.forClass(QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest.class);
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    QuasselCoreControlPort.QuasselCoreNetworkCreateRequest request =
        new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest request =
        new QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest(
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
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
    QuasselCoreControlPort.QuasselCoreNetworkSummary summary =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
            2, "libera", false, true, 1, "irc.libera.chat", 6697, true, Map.of());
    QuasselCoreControlPort.QuasselCoreNetworkCreateRequest request =
        new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
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
