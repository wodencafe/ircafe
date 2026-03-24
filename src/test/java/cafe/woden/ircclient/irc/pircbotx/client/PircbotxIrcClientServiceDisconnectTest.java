package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.GenericBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.*;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.irc.soju.SojuBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.znc.ZncBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.state.ServerIsupportState;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PircbotxIrcClientServiceDisconnectTest {

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void disconnectWithoutActiveBotStillPublishesDisconnectedEvent() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    PircbotxInputParserHookInstaller inputParserHookInstaller =
        mock(PircbotxInputParserHookInstaller.class);
    PircbotxBotFactory botFactory = mock(PircbotxBotFactory.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    Ircv3StsPolicyService stsPolicies = mock(Ircv3StsPolicyService.class);
    BouncerBackendRegistry bouncerBackends = mock(BouncerBackendRegistry.class);
    BouncerDiscoveryEventPort bouncerDiscoveryEvents = mock(BouncerDiscoveryEventPort.class);
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    org.mockito.Mockito.when(bouncerBackends.backendIds())
        .thenReturn(
            Set.of(
                SojuBouncerNetworkMappingStrategy.BACKEND_ID,
                ZncBouncerNetworkMappingStrategy.BACKEND_ID,
                GenericBouncerNetworkMappingStrategy.BACKEND_ID));
    ServerIsupportState serverIsupportState = new ServerIsupportState();
    PircbotxBridgeListenerFactory bridgeListenerFactory =
        new PircbotxBridgeListenerFactory(
            bouncerBackends,
            bouncerDiscoveryEvents,
            new NoOpPlaybackCursorProvider(),
            serverIsupportState,
            new SojuProperties(null, null),
            new ZncProperties(null, null));

    PircbotxIrcClientService service =
        new PircbotxIrcClientService(
            new IrcProperties(null, List.of()),
            serverCatalog,
            inputParserHookInstaller,
            botFactory,
            bridgeListenerFactory,
            (CtcpReplyRuntimeConfigPort) runtimeConfig,
            (ChatCommandRuntimeConfigPort) runtimeConfig,
            stsPolicies,
            bouncerBackends,
            bouncerDiscoveryEvents,
            timers,
            serverIsupportState);

    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.disconnect("libera").blockingAwait();

    events.awaitCount(1);
    events.assertValueCount(1);
    ServerIrcEvent emitted = events.values().getFirst();
    assertEquals("libera", emitted.serverId());
    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, emitted.event());
    assertEquals("Client requested disconnect", disconnected.reason());

    verify(timers).cancelReconnect(any(PircbotxConnectionState.class));
    verify(timers).stopHeartbeat(any(PircbotxConnectionState.class));
    verify(bouncerDiscoveryEvents)
        .onOriginDisconnected(eq(SojuBouncerNetworkMappingStrategy.BACKEND_ID), eq("libera"));
    verify(bouncerDiscoveryEvents)
        .onOriginDisconnected(eq(ZncBouncerNetworkMappingStrategy.BACKEND_ID), eq("libera"));
    verify(bouncerDiscoveryEvents)
        .onOriginDisconnected(eq(GenericBouncerNetworkMappingStrategy.BACKEND_ID), eq("libera"));
  }

  @Test
  void reconnectDisconnectDoesNotClearBouncerOriginNetworks() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    PircbotxInputParserHookInstaller inputParserHookInstaller =
        mock(PircbotxInputParserHookInstaller.class);
    PircbotxBotFactory botFactory = mock(PircbotxBotFactory.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    Ircv3StsPolicyService stsPolicies = mock(Ircv3StsPolicyService.class);
    BouncerBackendRegistry bouncerBackends = mock(BouncerBackendRegistry.class);
    BouncerDiscoveryEventPort bouncerDiscoveryEvents = mock(BouncerDiscoveryEventPort.class);
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    ServerIsupportState serverIsupportState = new ServerIsupportState();
    PircbotxBridgeListenerFactory bridgeListenerFactory =
        new PircbotxBridgeListenerFactory(
            bouncerBackends,
            bouncerDiscoveryEvents,
            new NoOpPlaybackCursorProvider(),
            serverIsupportState,
            new SojuProperties(null, null),
            new ZncProperties(null, null));

    PircbotxIrcClientService service =
        new PircbotxIrcClientService(
            new IrcProperties(null, List.of()),
            serverCatalog,
            inputParserHookInstaller,
            botFactory,
            bridgeListenerFactory,
            (CtcpReplyRuntimeConfigPort) runtimeConfig,
            (ChatCommandRuntimeConfigPort) runtimeConfig,
            stsPolicies,
            bouncerBackends,
            bouncerDiscoveryEvents,
            timers,
            serverIsupportState);

    TestSubscriber<ServerIrcEvent> events = service.events().test();

    service.disconnect("libera", null, DisconnectRequestSource.RECONNECT).blockingAwait();

    events.awaitCount(1);
    events.assertValueCount(1);
    ServerIrcEvent emitted = events.values().getFirst();
    assertEquals("libera", emitted.serverId());
    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, emitted.event());
    assertEquals("Client requested disconnect", disconnected.reason());

    verify(timers).cancelReconnect(any(PircbotxConnectionState.class));
    verify(timers).stopHeartbeat(any(PircbotxConnectionState.class));
    verifyNoInteractions(bouncerDiscoveryEvents);
  }
}
