package cafe.woden.ircclient.app.outbound;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundNamesWhoListCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy =
      mock(OutboundBackendCapabilityPolicy.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final OutboundRawLineCorrelationService rawLineCorrelationService =
      new OutboundRawLineCorrelationService(backendCapabilityPolicy, labeledResponseRoutingState);
  private final OutboundRawCommandSupport rawCommandSupport =
      new OutboundRawCommandSupport(rawLineCorrelationService);
  private final OutboundNamesWhoListCommandService service =
      new OutboundNamesWhoListCommandService(
          IrcTargetMembershipPort.from(irc),
          ui,
          connectionCoordinator,
          targetCoordinator,
          commandTargetPolicy,
          rawCommandSupport);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
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
  void listOnMatrixBackendSendsRawListLine() {
    TargetRef status = new TargetRef("matrix", "status");
    TargetRef channelList = TargetRef.channelList("matrix");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(irc.sendRaw("matrix", "LIST >10")).thenReturn(Completable.complete());

    service.handleList(disposables, ">10");

    verify(ui).ensureTargetExists(channelList);
    verify(ui).beginChannelList("matrix", "Loading channel list (>10)...");
    verify(ui).selectTarget(channelList);
    verify(irc).sendRaw("matrix", "LIST >10");
  }

  @Test
  void listUsesQualifiedChannelListTargetWhenActiveTargetIsNetworkQualified() {
    TargetRef qualifiedChannel = new TargetRef("quassel", "#ircafe{net:libera}");
    TargetRef qualifiedChannelList = TargetRef.channelList("quassel", "libera");
    when(targetCoordinator.getActiveTarget()).thenReturn(qualifiedChannel);
    when(connectionCoordinator.isConnected("quassel")).thenReturn(true);
    when(irc.sendRaw("quassel", "LIST")).thenReturn(Completable.complete());

    service.handleList(disposables, "");

    verify(ui).ensureTargetExists(qualifiedChannelList);
    verify(ui).beginChannelList("quassel", "Loading channel list...");
    verify(ui).selectTarget(qualifiedChannelList);
    verify(irc).sendRaw("quassel", "LIST");
  }

  @Test
  void namesOnMatrixBackendRequestsNames() {
    TargetRef room = new TargetRef("matrix", "!room:example.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(room);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(serverWithBackend("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(irc.requestNames("matrix", "!room:example.org")).thenReturn(Completable.complete());

    service.handleNames(disposables, "");

    verify(ui).ensureTargetExists(room);
    verify(ui).appendStatus(room, "(names)", "Requesting NAMES for !room:example.org...");
    verify(irc).requestNames("matrix", "!room:example.org");
  }

  @Test
  void whoUsesActiveChannelWhenNoArgsProvided() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "WHO #ircafe")).thenReturn(Completable.complete());

    service.handleWho(disposables, "");

    verify(ui).ensureTargetExists(channel);
    verify(irc).sendRaw("libera", "WHO #ircafe");
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
