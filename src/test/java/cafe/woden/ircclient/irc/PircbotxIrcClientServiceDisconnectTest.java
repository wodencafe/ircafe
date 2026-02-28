package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.irc.soju.SojuEphemeralNetworkImporter;
import cafe.woden.ircclient.irc.znc.ZncEphemeralNetworkImporter;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

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
    SojuEphemeralNetworkImporter sojuImporter = mock(SojuEphemeralNetworkImporter.class);
    ZncEphemeralNetworkImporter zncImporter = mock(ZncEphemeralNetworkImporter.class);
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);

    ObjectProvider<PlaybackCursorProvider> playbackCursorProviderProvider =
        new StaticListableBeanFactory().getBeanProvider(PlaybackCursorProvider.class);

    PircbotxIrcClientService service =
        new PircbotxIrcClientService(
            new IrcProperties(null, List.of()),
            serverCatalog,
            inputParserHookInstaller,
            botFactory,
            new SojuProperties(null, null),
            new ZncProperties(null, null),
            runtimeConfig,
            stsPolicies,
            sojuImporter,
            zncImporter,
            timers,
            playbackCursorProviderProvider);

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
    verify(sojuImporter).onOriginDisconnected(eq("libera"));
    verify(zncImporter).onOriginDisconnected(eq("libera"));
  }
}
