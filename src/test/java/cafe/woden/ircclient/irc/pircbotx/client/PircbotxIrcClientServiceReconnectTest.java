package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.config.api.QuitMessageRuntimeConfigPort;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.config.properties.ZncProperties;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures;
import cafe.woden.ircclient.irc.pircbotx.listener.PircbotxBridgeListenerFactory;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.playback.NoOpPlaybackCursorProvider;
import cafe.woden.ircclient.state.ServerIsupportState;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.DisconnectEvent;

class PircbotxIrcClientServiceReconnectTest {

  private enum FailureMode {
    DISCONNECT,
    CRASH,
    DISCONNECT_THEN_CRASH,
    CRASH_THEN_DISCONNECT
  }

  @ParameterizedTest
  @EnumSource(FailureMode.class)
  void failedSessionsBackOffReachRetryLimitAndAllowManualRetry(FailureMode failureMode)
      throws Exception {
    try (Fixture fixture = new Fixture(failureMode)) {
      fixture.service.connect("znc").blockingAwait();

      for (int attempt = 1; attempt <= 3; attempt++) {
        IrcEvent.Reconnecting retry = fixture.retries.poll(5, TimeUnit.SECONDS);
        assertNotNull(retry, "expected retry " + attempt);
        assertEquals(attempt, retry.attempt());
        assertEquals(10L << (attempt - 1), retry.delayMs());
      }
      assertNotNull(
          fixture.aborted.poll(5, TimeUnit.SECONDS), "expected retry limit to stop the loop");
      assertEquals(4, fixture.sessions.get(), "one initial connection plus three retries");

      fixture.service.connect("znc").blockingAwait();
      IrcEvent.Reconnecting manualRetry = fixture.retries.poll(5, TimeUnit.SECONDS);
      assertNotNull(manualRetry, "manual connect should start a new retry sequence");
      assertEquals(1L, manualRetry.attempt());
      assertEquals(10L, manualRetry.delayMs());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final BlockingQueue<IrcEvent.Reconnecting> retries = new ArrayBlockingQueue<>(8);
    final BlockingQueue<IrcEvent.Error> aborted = new ArrayBlockingQueue<>(2);
    final AtomicInteger sessions = new AtomicInteger();
    final AtomicReference<Action> lateDisconnect = new AtomicReference<>();
    final ScheduledExecutorService heartbeatExec = Executors.newSingleThreadScheduledExecutor();
    final ScheduledExecutorService reconnectExec = Executors.newSingleThreadScheduledExecutor();
    final PircbotxConnectionTimersRx timers;
    final PircbotxIrcClientService service;
    final Disposable subscription;

    Fixture(FailureMode failureMode) throws Exception {
      IrcProperties.Server server = IrcPropertiesTestFixtures.server("znc");
      IrcProperties props =
          new IrcProperties(
              new IrcProperties.Client(
                  "IRCafe Test",
                  new IrcProperties.Reconnect(true, 10, 40, 2, 0, 3),
                  new IrcProperties.Heartbeat(false, 1000, 1000),
                  null,
                  null),
              java.util.List.of(server));
      ServerCatalog servers = mock(ServerCatalog.class);
      when(servers.require("znc")).thenReturn(server);
      when(servers.containsId("znc")).thenReturn(true);
      Ircv3RuntimeTestFixtures.Runtime runtime = Ircv3RuntimeTestFixtures.runtime();
      ServerIsupportState isupport = new ServerIsupportState();
      BouncerBackendRegistry backends = mock(BouncerBackendRegistry.class);
      when(backends.backendIds()).thenReturn(Set.of());
      BouncerDiscoveryEventPort discovery = BouncerDiscoveryEventPort.noOp();
      PircbotxBridgeListenerFactory listeners =
          new PircbotxBridgeListenerFactory(
              backends,
              discovery,
              new NoOpPlaybackCursorProvider(),
              isupport,
              new SojuProperties(Map.of(), new SojuProperties.Discovery(false)),
              new ZncProperties(Map.of(), new ZncProperties.Discovery(false)),
              runtime.catalogs(),
              runtime.serverTime(),
              runtime.messageTags());
      PircbotxBotFactory bots = mock(PircbotxBotFactory.class);
      when(bots.build(any(), anyString(), any()))
          .thenAnswer(
              invocation -> {
                sessions.incrementAndGet();
                ListenerAdapter listener = invocation.getArgument(2);
                PircBotX bot = mock(PircBotX.class);
                doAnswer(
                        unused -> {
                          IOException failure =
                              new IOException("ZNC connection closed before registration");
                          if (failureMode == FailureMode.CRASH_THEN_DISCONNECT) {
                            lateDisconnect.set(
                                () ->
                                    listener.onDisconnect(new DisconnectEvent(bot, null, failure)));
                          } else if (failureMode != FailureMode.CRASH) {
                            listener.onDisconnect(new DisconnectEvent(bot, null, failure));
                          }
                          if (failureMode != FailureMode.DISCONNECT) throw failure;
                          return null;
                        })
                    .when(bot)
                    .startBot();
                return bot;
              });
      timers = new PircbotxConnectionTimersRx(props, servers, heartbeatExec, reconnectExec);
      service =
          new PircbotxIrcClientService(
              props,
              servers,
              mock(PircbotxInputParserHookInstaller.class),
              bots,
              listeners,
              mock(CtcpReplyRuntimeConfigPort.class),
              mock(QuitMessageRuntimeConfigPort.class),
              runtime.stsPolicyService(),
              runtime.catalogs().outboundCommands(),
              backends,
              discovery,
              timers,
              isupport);
      subscription =
          service
              .events()
              .subscribe(
                  event -> {
                    if (event.event() instanceof IrcEvent.Reconnecting retry) {
                      Action callback = lateDisconnect.getAndSet(null);
                      if (callback != null) callback.run();
                      retries.offer(retry);
                    } else if (event.event() instanceof IrcEvent.Error error
                        && "Reconnect aborted (max attempts reached)".equals(error.message())) {
                      aborted.offer(error);
                    }
                  });
    }

    @Override
    public void close() throws Exception {
      service.shutdownNow();
      timers.shutdown();
      subscription.dispose();
      heartbeatExec.shutdownNow();
      reconnectExec.shutdownNow();
      heartbeatExec.awaitTermination(1, TimeUnit.SECONDS);
      reconnectExec.awaitTermination(1, TimeUnit.SECONDS);
    }
  }
}
