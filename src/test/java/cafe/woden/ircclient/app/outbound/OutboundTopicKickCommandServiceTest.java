package cafe.woden.ircclient.app.outbound;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundTopicKickCommandServiceTest {

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
  private final OutboundTopicKickCommandService service =
      new OutboundTopicKickCommandService(
          irc,
          ui,
          connectionCoordinator,
          targetCoordinator,
          commandTargetPolicy,
          rawLineCorrelationService);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void topicUsesActiveChannelWhenNoExplicitChannelProvided() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "TOPIC #ircafe :new topic")).thenReturn(Completable.complete());

    service.handleTopic(disposables, "new", "topic");

    verify(ui).ensureTargetExists(channel);
    verify(irc).sendRaw("libera", "TOPIC #ircafe :new topic");
  }

  @Test
  void topicShowsUsageWhenNotInChannelAndNoExplicitChannel() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handleTopic(disposables, "new", "topic");

    verify(ui).appendStatus(status, "(topic)", "Usage: /topic [#channel] [new topic...]");
  }

  @Test
  void kickUsesActiveChannelWhenNoExplicitChannelProvided() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "KICK #ircafe bob :reason")).thenReturn(Completable.complete());

    service.handleKick(disposables, "", "bob", "reason");

    verify(ui).ensureTargetExists(channel);
    verify(irc).sendRaw("libera", "KICK #ircafe bob :reason");
  }

  @Test
  void kickShowsUsageWhenNickMissing() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);

    service.handleKick(disposables, "", "", "");

    verify(ui).appendStatus(channel, "(kick)", "Usage: /kick [#channel] <nick> [reason]");
  }
}
