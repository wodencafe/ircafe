package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.config.properties.ZncProperties;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.listener.PircbotxBridgeListenerFactory;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.net.NetFloodProtectionContext;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.pircbotx.PircBotX;
import org.slf4j.LoggerFactory;

class PircbotxZncPlaybackIntegrationTest {
  private static final long LAST_SEEN_SECONDS = 1_700_000_000L;
  private static final long OFFLINE_SECONDS = LAST_SEEN_SECONDS + 1800;
  private static final long LIVE_SECONDS = LAST_SEEN_SECONDS + 3600;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void reconnectDeliversOfflineMessagesEvenWhenLiveTrafficArrivesDuringRegistration(
      boolean playbackCapability) throws Exception {
    var previous = NetFloodProtectionContext.settings();
    NetFloodProtectionContext.configure(new IrcProperties.FloodProtection(false, 100, 0));
    AtomicLong persistedCursor = new AtomicLong(LAST_SEEN_SECONDS);
    CountDownLatch liveReceived = new CountDownLatch(1);
    CountDownLatch backlogReceived = new CountDownLatch(1);
    var bus = PublishProcessor.<ServerIrcEvent>create().toSerialized();
    var subscription =
        bus.subscribe(
            event -> {
              if (event.event() instanceof IrcEvent.ChannelMessage message) {
                if (message.text().equals("live message")) {
                  persistedCursor.set(LIVE_SECONDS);
                  liveReceived.countDown();
                }
                if (message.text().equals("offline message")) backlogReceived.countDown();
              }
            });
    try (PlaybackLogs logs = new PlaybackLogs();
        ScriptedZnc server = new ScriptedZnc(playbackCapability, liveReceived)) {
      PircBotX bot = bot(server.port(), persistedCursor, bus);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread runner =
          Thread.startVirtualThread(
              () -> {
                try {
                  bot.startBot();
                } catch (Throwable error) {
                  failure.set(error);
                }
              });
      try {
        assertTrue(liveReceived.await(10, TimeUnit.SECONDS), "live messages must reach the bridge");
        if (playbackCapability) {
          String request = server.playbackRequests.poll(5, TimeUnit.SECONDS);
          assertEquals(
              "PRIVMSG *playback :play * " + (LAST_SEEN_SECONDS - 1),
              request,
              "traffic from the new session must not advance the reconnect cursor");
        }
        assertTrue(
            backlogReceived.await(5, TimeUnit.SECONDS), "buffered messages must reach the bridge");
        assertTrue(
            logs.completed.await(5, TimeUnit.SECONDS), "replay must publish a diagnostic summary");
        assertTrue(
            logs.summary
                .get()
                .contains(
                    "target=#one observedMessages=1 earliest=2023-11-14T22:43:20Z latest=2023-11-14T22:43:20Z"));
        assertFalse(logs.summary.get().contains("offline message"));
      } finally {
        bot.close();
        runner.join(3000);
      }
      assertFalse(runner.isAlive());
      assertNull(failure.get());
    } finally {
      subscription.dispose();
      NetFloodProtectionContext.configure(previous);
    }
  }

  private static final class PlaybackLogs extends AppenderBase<ILoggingEvent>
      implements AutoCloseable {
    private final Logger logger =
        (Logger) LoggerFactory.getLogger(PircbotxChatHistoryBatchCollector.class);
    private final Level previousLevel = logger.getLevel();
    private final CountDownLatch completed = new CountDownLatch(1);
    private final AtomicReference<String> summary = new AtomicReference<>();

    private PlaybackLogs() {
      start();
      logger.addAppender(this);
      logger.setLevel(Level.INFO);
    }

    @Override
    protected void append(ILoggingEvent event) {
      if (event.getFormattedMessage().contains("ZNC playback batch ended")) {
        summary.set(event.getFormattedMessage());
        completed.countDown();
      }
    }

    @Override
    public void close() {
      logger.detachAppender(this);
      logger.setLevel(previousLevel);
      stop();
    }
  }

  private static PircBotX bot(
      int port,
      AtomicLong persistedCursor,
      io.reactivex.rxjava3.processors.FlowableProcessor<ServerIrcEvent> bus) {
    var runtime = Ircv3RuntimeTestFixtures.runtime();
    var catalogs = runtime.catalogs();
    var connection = new PircbotxConnectionState("test");
    var soju = new SojuProperties(Map.of(), new SojuProperties.Discovery(false));
    var znc = new ZncProperties(Map.of(), new ZncProperties.Discovery(false));
    var listenerFactory =
        new PircbotxBridgeListenerFactory(
            new BouncerBackendRegistry(List.of()),
            BouncerDiscoveryEventPort.noOp(),
            serverId -> OptionalLong.of(persistedCursor.get()),
            mock(ServerIsupportStatePort.class),
            soju,
            znc,
            catalogs,
            runtime.serverTime(),
            runtime.messageTags());
    var listener =
        listenerFactory.create(
            "test",
            connection,
            bus,
            ignored -> {},
            (ignored, reason) -> {},
            (bot, nick, message) -> false,
            false);
    ServerProxyResolver resolver = mock(ServerProxyResolver.class);
    when(resolver.planForServer("test"))
        .thenReturn(
            ProxyPlan.from(new IrcProperties.Proxy(false, "", 0, "", "", true, 2000, 10000)));
    var factory =
        new PircbotxBotFactory(
            resolver, soju, null, Ircv3ExtensionCatalog.builtInCatalog(), catalogs);
    var config =
        IrcPropertiesTestFixtures.serverBuilder("test")
            .host(InetAddress.getLoopbackAddress().getHostAddress())
            .port(port)
            .tls(false)
            .nick("probe")
            .login("probe")
            .autoJoin(List.of())
            .sasl(new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null))
            .nickserv(new IrcProperties.Server.Nickserv(false, "", "", false))
            .build();
    PircBotX bot = factory.build(config, "test", listener);
    connection.setBot(bot);
    new PircbotxInputParserHookInstaller(mock(Ircv3StsPolicyService.class), catalogs)
        .installIrcv3Hook(bot, "test", connection, bus::onNext);
    return bot;
  }

  private static final class ScriptedZnc implements AutoCloseable {
    final ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    final BlockingQueue<String> playbackRequests = new LinkedBlockingQueue<>();
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final boolean playbackCapability;
    final CountDownLatch liveReceived;
    final Thread runner;
    volatile Socket socket;

    ScriptedZnc(boolean playbackCapability, CountDownLatch liveReceived) throws IOException {
      this.playbackCapability = playbackCapability;
      this.liveReceived = liveReceived;
      runner = Thread.startVirtualThread(this::serve);
    }

    int port() {
      return listener.getLocalPort();
    }

    void serve() {
      try (Socket client = listener.accept()) {
        socket = client;
        client.setSoTimeout(15000);
        var reader =
            new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        var writer =
            new BufferedWriter(
                new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("CAP LS")) {
            send(
                writer,
                ":znc CAP * LS :server-time message-tags batch"
                    + (playbackCapability ? " znc.in/playback" : ""));
          } else if (line.startsWith("CAP REQ :")) {
            send(writer, ":znc CAP probe ACK :" + line.substring(9));
          } else if (line.equals("CAP END")) {
            send(writer, ":znc 001 probe :Welcome to ZNC");
            // Native ZNC commonly forwards the upstream server version instead of its own.
            send(writer, ":znc 004 probe irc.example solanum-1.0 ao mtov");
            send(writer, ":znc 375 probe :- ZNC MOTD");
            send(writer, ":znc 372 probe :- Local replay test");
            send(writer, ":probe!user@host JOIN #one");
            send(
                writer,
                "@time=2023-11-14T23:13:20.000Z :alice!user@host PRIVMSG #one :live message");
            if (!liveReceived.await(5, TimeUnit.SECONDS))
              throw new AssertionError("live message not delivered");
            send(writer, ":znc 376 probe :End of MOTD");
            if (!playbackCapability) replay(writer);
          } else if (line.startsWith("PRIVMSG *playback :play * ")) {
            playbackRequests.add(line);
            long since = Long.parseLong(line.substring(line.lastIndexOf(' ') + 1));
            if (since < OFFLINE_SECONDS) replay(writer);
          }
        }
      } catch (Throwable error) {
        if (!(error instanceof IOException) || !listener.isClosed()) failure.set(error);
      }
    }

    void replay(BufferedWriter writer) throws IOException {
      send(writer, ":znc.in BATCH +buffer znc.in/playback #one");
      send(
          writer,
          "@batch=buffer;time=2023-11-14T22:43:20.000Z :alice!user@host PRIVMSG #one :offline message");
      send(writer, ":znc.in BATCH -buffer");
    }

    void send(BufferedWriter writer, String line) throws IOException {
      writer.write(line + "\r\n");
      writer.flush();
    }

    @Override
    public void close() throws Exception {
      listener.close();
      if (socket != null) socket.close();
      runner.join(3000);
      assertFalse(runner.isAlive());
      assertNull(failure.get(), "scripted server must complete without background failures");
      assertTrue(
          playbackRequests.isEmpty(),
          "request playback once when negotiated, otherwise use native replay");
    }
  }
}
