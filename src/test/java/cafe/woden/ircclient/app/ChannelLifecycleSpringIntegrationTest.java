package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.IrcMediator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import io.reactivex.rxjava3.core.Completable;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class ChannelLifecycleSpringIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  @MockitoBean ChatHistoryIngestionPort chatHistoryIngestionPort;

  @MockitoBean ChatHistoryIngestEventsPort chatHistoryIngestEventsPort;

  @MockitoBean ChatHistoryBatchEventsPort chatHistoryBatchEventsPort;

  @MockitoBean ZncPlaybackEventsPort zncPlaybackEventsPort;

  @MockitoBean TargetChatHistoryPort targetChatHistoryPort;

  @MockitoBean TargetLogMaintenancePort targetLogMaintenancePort;

  @MockitoBean DccTransferStore dccTransferStore;

  private final IrcMediator mediator;
  private final TargetCoordinator targetCoordinator;
  private final ConnectionCoordinator connectionCoordinator;
  private final RuntimeConfigStore runtimeConfig;
  private final ServerRegistry serverRegistry;
  private final IrcClientService ircClientService;
  private final UiPort swingUiPort;
  private final Method onServerIrcEvent;

  ChannelLifecycleSpringIntegrationTest(
      IrcMediator mediator,
      TargetCoordinator targetCoordinator,
      ConnectionCoordinator connectionCoordinator,
      RuntimeConfigStore runtimeConfig,
      ServerRegistry serverRegistry,
      IrcClientService ircClientService,
      @Qualifier("swingUiPort") UiPort swingUiPort)
      throws NoSuchMethodException {
    this.mediator = mediator;
    this.targetCoordinator = targetCoordinator;
    this.connectionCoordinator = connectionCoordinator;
    this.runtimeConfig = runtimeConfig;
    this.serverRegistry = serverRegistry;
    this.ircClientService = ircClientService;
    this.swingUiPort = swingUiPort;
    this.onServerIrcEvent =
        IrcMediator.class.getDeclaredMethod("onServerIrcEvent", ServerIrcEvent.class);
    this.onServerIrcEvent.setAccessible(true);
  }

  @BeforeEach
  void clearMockHistory() {
    clearInvocations(ircClientService, swingUiPort);
  }

  @Test
  void joinedDetachReconnectKeepsChannelDetachedUntilManualJoin() {
    String sid = primaryServerId();
    String channel = "#it-detach-reconnect";
    TargetRef ref = new TargetRef(sid, channel);

    runtimeConfig.forgetJoinedChannel(sid, channel);
    stubJoinAndPart(sid, channel);
    markConnected(sid);

    targetCoordinator.detachChannel(ref);
    connectionCoordinator.handleConnectivityEvent(
        sid, new IrcEvent.Disconnected(Instant.now(), "test disconnect"), null);
    markConnected(sid);

    boolean accepted = targetCoordinator.onJoinedChannel(sid, channel);

    assertFalse(accepted);
    verify(swingUiPort, atLeastOnce()).setChannelDetached(ref, true);
    verify(ircClientService, atLeastOnce()).partChannel(sid, channel);
  }

  @Test
  void kickedFromChannelEventMovesTargetIntoDetachedState() {
    String sid = primaryServerId();
    String channel = "#it-kick-detach";
    TargetRef ref = new TargetRef(sid, channel);

    runtimeConfig.forgetJoinedChannel(sid, channel);
    emitServerEvent(sid, new IrcEvent.KickedFromChannel(Instant.now(), channel, "chanop", "bye"));

    verify(swingUiPort, atLeastOnce()).setChannelDetached(eq(ref), eq(true), anyString());
    assertFalse(targetCoordinator.onJoinedChannel(sid, channel));
  }

  @Test
  void manualJoinClearsDetachedSuppressionAndAcceptsJoin() {
    String sid = primaryServerId();
    String channel = "#it-manual-join";
    TargetRef ref = new TargetRef(sid, channel);

    runtimeConfig.forgetJoinedChannel(sid, channel);
    stubJoinAndPart(sid, channel);
    markConnected(sid);

    targetCoordinator.onChannelMembershipLost(sid, channel, true);
    targetCoordinator.joinChannel(ref);

    boolean accepted = targetCoordinator.onJoinedChannel(sid, channel);

    assertTrue(accepted);
    verify(ircClientService, atLeastOnce()).joinChannel(sid, channel);
    verify(swingUiPort, atLeastOnce()).setChannelDetached(ref, false);
  }

  @Test
  void connectedEventRestoresPersistedJoinedChannelsAsDetachedTargets() {
    String sid = primaryServerId();
    String channel = "#it-restore-detached";
    TargetRef ref = new TargetRef(sid, channel);

    runtimeConfig.rememberJoinedChannel(sid, channel);
    clearInvocations(swingUiPort);

    markConnected(sid);

    verify(swingUiPort, atLeastOnce()).ensureTargetExists(ref);
    verify(swingUiPort, atLeastOnce()).setChannelDetached(ref, true);
  }

  private void emitServerEvent(String serverId, IrcEvent event) {
    try {
      onServerIrcEvent.invoke(mediator, new ServerIrcEvent(serverId, event));
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject IRC event into mediator", e);
    }
  }

  private void markConnected(String serverId) {
    connectionCoordinator.handleConnectivityEvent(
        serverId, new IrcEvent.Connected(Instant.now(), "irc.example.net", 6697, "tester"), null);
  }

  private void stubJoinAndPart(String serverId, String channel) {
    when(ircClientService.joinChannel(serverId, channel)).thenReturn(Completable.complete());
    when(ircClientService.partChannel(serverId, channel, null)).thenReturn(Completable.complete());
    when(ircClientService.partChannel(serverId, channel)).thenReturn(Completable.complete());
  }

  private String primaryServerId() {
    return serverRegistry.serverIds().stream().findFirst().orElse("default");
  }
}
