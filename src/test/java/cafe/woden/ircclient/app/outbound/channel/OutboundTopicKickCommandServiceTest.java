package cafe.woden.ircclient.app.outbound.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.TestIrcv3RuntimeSupport;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundConnectionStatusSupport;
import cafe.woden.ircclient.app.outbound.support.OutboundRawCommandSupport;
import cafe.woden.ircclient.app.outbound.support.OutboundRawLineCorrelationService;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundTopicKickCommandServiceTest {

  private final IrcBackendRuntimeClientService irc = mock(IrcBackendRuntimeClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy =
      mock(OutboundBackendCapabilityPolicy.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final OutboundRawLineCorrelationService rawLineCorrelationService =
      TestIrcv3RuntimeSupport.rawLineCorrelation(
          backendCapabilityPolicy, labeledResponseRoutingState, () -> 1L);
  private final OutboundRawCommandSupport rawCommandSupport =
      new OutboundRawCommandSupport(rawLineCorrelationService);
  private final OutboundConnectionStatusSupport outboundConnectionStatusSupport =
      new OutboundConnectionStatusSupport(ui, connectionCoordinator);
  private final OutboundTargetMembershipCommandSupport targetMembershipCommandSupport =
      new OutboundTargetMembershipCommandSupport(
          IrcTargetMembershipPort.from(irc),
          ui,
          outboundConnectionStatusSupport,
          targetCoordinator,
          commandTargetPolicy,
          rawCommandSupport);
  private final OutboundTopicKickCommandService service =
      new OutboundTopicKickCommandService(commandTargetPolicy, targetMembershipCommandSupport);
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
  void kickUsesPreparedLabeledRawLineAndRemembersCorrelation() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(backendCapabilityPolicy.supportsLabeledResponse("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "@label=ircafe-libera-1 KICK #ircafe bob :reason"))
        .thenReturn(Completable.complete());

    service.handleKick(disposables, "", "bob", "reason");

    verify(labeledResponseRoutingState)
        .remember(
            eq("libera"),
            eq("ircafe-libera-1"),
            eq(channel),
            eq("KICK #ircafe bob :reason"),
            any(Instant.class));
    verify(irc).sendRaw("libera", "@label=ircafe-libera-1 KICK #ircafe bob :reason");
  }

  @Test
  void kickShowsUsageWhenNickMissing() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);

    service.handleKick(disposables, "", "", "");

    verify(ui).appendStatus(channel, "(kick)", "Usage: /kick [#channel] <nick> [reason]");
  }
}
