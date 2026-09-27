package cafe.woden.ircclient.app.outbound.monitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.TestIrcv3RuntimeSupport;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendFeatureRegistry;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.adapter.IrcNegotiatedFeaturePortAdapter;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.irc.ircv3.Ircv3MonitorCommandRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandRequest;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OutboundMonitorCommandServiceTest {

  private final IrcBackendRuntimeClientService irc =
      Mockito.mock(IrcBackendRuntimeClientService.class);
  private final UiPort ui = Mockito.mock(UiPort.class);
  private final TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
  private final ConnectionCoordinator connectionCoordinator =
      Mockito.mock(ConnectionCoordinator.class);
  private final ServerCatalog serverCatalog = Mockito.mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.builtInOutboundBackendFeatureRegistry();
  private final OutboundBackendCapabilityPolicy outboundBackendCapabilityPolicy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy,
          outboundBackendFeatureRegistry,
          new IrcNegotiatedFeaturePortAdapter(irc),
          irc,
          cafe.woden.ircclient.app.api.AvailableBackendIdsPort.builtInsOnly());
  private final MonitorRosterPort monitorRosterPort = Mockito.mock(MonitorRosterPort.class);
  private final MonitorFallbackPort monitorFallbackPort = Mockito.mock(MonitorFallbackPort.class);
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final OutboundMonitorCommandSupport monitorCommandSupport =
      new OutboundMonitorCommandSupport(
          irc,
          ui,
          targetCoordinator,
          connectionCoordinator,
          monitorFallbackPort,
          outboundBackendCapabilityPolicy);

  private final OutboundMonitorCommandService service =
      new OutboundMonitorCommandService(
          monitorRosterPort, monitorCommandSupport, TestIrcv3RuntimeSupport.monitor());

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void addPersistsAndSendsMonitorPlusWhenConnected() {
    TargetRef active = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(monitorRosterPort.parseNickInput("alice,bob"))
        .thenReturn(java.util.List.of("alice", "bob"));
    when(monitorRosterPort.addNicks(eq("libera"), eq(java.util.List.of("alice", "bob"))))
        .thenReturn(2);
    when(irc.isMonitorAvailable("libera")).thenReturn(true);
    when(irc.negotiatedMonitorLimit("libera")).thenReturn(100);
    when(irc.sendRaw("libera", "MONITOR +alice,bob")).thenReturn(Completable.complete());

    service.handleMonitor(disposables, "+alice,bob");

    verify(monitorRosterPort).addNicks("libera", java.util.List.of("alice", "bob"));
    verify(irc).sendRaw("libera", "MONITOR +alice,bob");
  }

  @Test
  void installedRuntimeProviderCanReplaceMonitorAddRendering() {
    TargetRef active = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(monitorRosterPort.parseNickInput("alice,bob")).thenReturn(List.of("alice", "bob"));
    when(monitorRosterPort.addNicks("libera", List.of("alice", "bob"))).thenReturn(2);
    when(irc.isMonitorAvailable("libera")).thenReturn(true);
    when(irc.negotiatedMonitorLimit("libera")).thenReturn(25);
    when(irc.sendRaw("libera", "PLUGIN MONITOR alice|bob")).thenReturn(Completable.complete());

    Ircv3OutboundCommandProvider provider =
        new Ircv3OutboundCommandProvider() {
          @Override
          public String providerId() {
            return "monitor-plugin";
          }

          @Override
          public Set<Ircv3OutboundCommandOperation> operations() {
            return Set.of(Ircv3OutboundCommandOperation.MONITOR_ADD);
          }

          @Override
          public List<String> build(
              Ircv3OutboundCommandOperation operation, Ircv3OutboundCommandRequest request) {
            return List.of("PLUGIN MONITOR " + String.join("|", request.values()));
          }
        };
    OutboundMonitorCommandService runtimeService =
        new OutboundMonitorCommandService(
            monitorRosterPort,
            monitorCommandSupport,
            new Ircv3MonitorCommandRuntimeSupport(
                Ircv3OutboundCommandRuntimeCatalog.fromProviders(List.of(provider))));

    runtimeService.handleMonitor(disposables, "+alice,bob");

    verify(irc).sendRaw("libera", "PLUGIN MONITOR alice|bob");
  }

  @Test
  void listShowsLocalNicksAndDoesNotSendWhenDisconnected() {
    TargetRef active = new TargetRef("libera", "status");
    TargetRef monitor = TargetRef.monitorGroup("libera");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);
    when(monitorRosterPort.listNicks("libera")).thenReturn(java.util.List.of("alice"));

    service.handleMonitor(disposables, "list");

    verify(ui).appendStatus(monitor, "(monitor)", "Monitored nicks (1): alice");
    verify(irc, never()).sendRaw(any(), any());
  }

  @Test
  void statusUsesIsonFallbackWhenMonitorUnavailable() {
    TargetRef active = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(monitorFallbackPort.isFallbackActive("libera")).thenReturn(true);

    service.handleMonitor(disposables, "status");

    verify(monitorFallbackPort).requestImmediateRefresh("libera");
    verify(irc, never()).sendRaw(eq("libera"), any());
  }

  @Test
  void addShowsUnavailableMessageWhenMonitorCapabilityMissing() {
    TargetRef active = new TargetRef("matrix", "#room:example.org");
    TargetRef status = new TargetRef("matrix", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(monitorRosterPort.parseNickInput("alice")).thenReturn(java.util.List.of("alice"));
    when(monitorRosterPort.addNicks(eq("matrix"), eq(java.util.List.of("alice")))).thenReturn(1);
    when(irc.isMonitorAvailable("matrix")).thenReturn(false);
    when(monitorFallbackPort.isFallbackActive("matrix")).thenReturn(false);

    service.handleMonitor(disposables, "+alice");

    verify(ui)
        .appendStatus(status, "(monitor)", "MONITOR capability is unavailable on this server.");
    verify(irc, never()).sendRaw(eq("matrix"), any());
  }

  @Test
  void addShowsBackendAvailabilityReasonWhenMonitorUnavailableAndReasonProvided() {
    TargetRef active = new TargetRef("matrix", "#room:example.org");
    TargetRef status = new TargetRef("matrix", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(monitorRosterPort.parseNickInput("alice")).thenReturn(java.util.List.of("alice"));
    when(monitorRosterPort.addNicks(eq("matrix"), eq(java.util.List.of("alice")))).thenReturn(1);
    when(irc.isMonitorAvailable("matrix")).thenReturn(false);
    when(irc.backendAvailabilityReason("matrix"))
        .thenReturn("Matrix backend monitor bridge is not available");
    when(monitorFallbackPort.isFallbackActive("matrix")).thenReturn(false);

    service.handleMonitor(disposables, "+alice");

    verify(ui).appendStatus(status, "(monitor)", "Matrix backend monitor bridge is not available.");
    verify(irc, never()).sendRaw(eq("matrix"), any());
  }
}
