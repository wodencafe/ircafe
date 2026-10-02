package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.slf4j.LoggerFactory;

class PircbotxConnectSessionSupportTest {

  @Test
  void openSessionEmitsConnectingAndStartsHeartbeat() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    List<ServerIrcEvent> events = new ArrayList<>();
    bus.subscribe(events::add);
    PircbotxBridgeListenerFactory bridgeListenerFactory = mock(PircbotxBridgeListenerFactory.class);
    PircbotxBotFactory botFactory = mock(PircbotxBotFactory.class);
    PircbotxInputParserHookInstaller inputParserHookInstaller =
        mock(PircbotxInputParserHookInstaller.class);
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    PircbotxConnectSessionSupport support =
        new PircbotxConnectSessionSupport(
            bus, bridgeListenerFactory, botFactory, inputParserHookInstaller, timers, "ircafe");

    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    IrcProperties.Server server = server("irc.example.net", 6697, "Nick");
    ListenerAdapter listener = mock(ListenerAdapter.class);
    PircbotxLagAwareBot bot = mock(PircbotxLagAwareBot.class);
    @SuppressWarnings("unchecked")
    BiConsumer<PircbotxConnectionState, String> reconnectScheduler = mock(BiConsumer.class);
    PircbotxCtcpRequestHandler ctcpHandler = mock(PircbotxCtcpRequestHandler.class);
    when(bridgeListenerFactory.create(
            eq("libera"),
            same(connection),
            same(bus),
            any(),
            same(reconnectScheduler),
            same(ctcpHandler),
            eq(true)))
        .thenReturn(listener);
    when(botFactory.build(server, "ircafe", listener)).thenReturn(bot);

    PircBotX opened =
        support.openSession("libera", connection, server, ctcpHandler, reconnectScheduler, true);

    assertSame(bot, opened);
    assertSame(bot, connection.currentBot());
    verify(inputParserHookInstaller)
        .installIrcv3Hook(same(bot), eq("libera"), same(connection), any());
    verify(timers).startHeartbeat(connection);
    verify(bot).setLagProbeObserver(any());
    assertEquals(1, events.size());
    assertEquals("libera", events.getFirst().serverId());
    IrcEvent.Connecting connecting =
        assertInstanceOf(IrcEvent.Connecting.class, events.getFirst().event());
    assertEquals("irc.example.net", connecting.serverHost());
    assertEquals(6697, connecting.serverPort());
    assertEquals("Nick", connecting.nick());
  }

  @Test
  void runBotLoopReportsCrashAndSchedulesReconnect() throws Exception {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    List<ServerIrcEvent> events = new ArrayList<>();
    bus.subscribe(events::add);
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    PircbotxConnectSessionSupport support =
        new PircbotxConnectSessionSupport(
            bus,
            mock(PircbotxBridgeListenerFactory.class),
            mock(PircbotxBotFactory.class),
            mock(PircbotxInputParserHookInstaller.class),
            timers,
            "ircafe");

    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    connection.setBot(bot);
    @SuppressWarnings("unchecked")
    BiConsumer<PircbotxConnectionState, String> reconnectScheduler = mock(BiConsumer.class);
    RuntimeException failure = new RuntimeException("boom");
    doThrow(failure).when(bot).startBot();

    Logger logger = (Logger) LoggerFactory.getLogger(PircbotxConnectSessionSupport.class);
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
    try {
      support.runBotLoop("libera", connection, bot, reconnectScheduler);
      assertTrue(
          logs.list.stream()
              .anyMatch(
                  entry ->
                      entry.getFormattedMessage().contains("IRC session failed: server=libera")
                          && entry.getThrowableProxy() != null
                          && "boom".equals(entry.getThrowableProxy().getMessage())));
    } finally {
      logger.detachAppender(logs);
      logs.stop();
    }

    assertNull(connection.currentBot());
    verify(timers).stopHeartbeat(connection);
    verify(reconnectScheduler).accept(connection, "Bot crashed");
    assertEquals(1, events.size());
    IrcEvent.Error error = assertInstanceOf(IrcEvent.Error.class, events.getFirst().event());
    assertEquals("Bot crashed", error.message());
    assertSame(failure, error.cause());
  }

  @Test
  void runBotLoopSkipsReconnectAfterManualDisconnect() throws Exception {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    PircbotxConnectSessionSupport support =
        new PircbotxConnectSessionSupport(
            bus,
            mock(PircbotxBridgeListenerFactory.class),
            mock(PircbotxBotFactory.class),
            mock(PircbotxInputParserHookInstaller.class),
            timers,
            "ircafe");

    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.markManualDisconnect();
    PircBotX bot = mock(PircBotX.class);
    connection.setBot(bot);
    @SuppressWarnings("unchecked")
    BiConsumer<PircbotxConnectionState, String> reconnectScheduler = mock(BiConsumer.class);
    doThrow(new RuntimeException("boom")).when(bot).startBot();

    support.runBotLoop("libera", connection, bot, reconnectScheduler);

    verify(timers).stopHeartbeat(connection);
    verify(reconnectScheduler, never()).accept(any(), any());
  }

  private static IrcProperties.Server server(String host, int port, String nick) {
    return IrcPropertiesTestFixtures.serverBuilder("libera")
        .host(host)
        .port(port)
        .nick(nick)
        .login("login")
        .realName("Real Name")
        .build();
  }
}
