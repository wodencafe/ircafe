package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.*;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;

/** Opens and supervises a single bot session for a connection attempt. */
final class PircbotxConnectSessionSupport {

  private final FlowableProcessor<ServerIrcEvent> bus;
  private final PircbotxBridgeListenerFactory bridgeListenerFactory;
  private final PircbotxBotFactory botFactory;
  private final PircbotxInputParserHookInstaller inputParserHookInstaller;
  private final PircbotxConnectionTimersRx timers;
  private final String version;

  PircbotxConnectSessionSupport(
      FlowableProcessor<ServerIrcEvent> bus,
      PircbotxBridgeListenerFactory bridgeListenerFactory,
      PircbotxBotFactory botFactory,
      PircbotxInputParserHookInstaller inputParserHookInstaller,
      PircbotxConnectionTimersRx timers,
      String version) {
    this.bus = Objects.requireNonNull(bus, "bus");
    this.bridgeListenerFactory =
        Objects.requireNonNull(bridgeListenerFactory, "bridgeListenerFactory");
    this.botFactory = Objects.requireNonNull(botFactory, "botFactory");
    this.inputParserHookInstaller =
        Objects.requireNonNull(inputParserHookInstaller, "inputParserHookInstaller");
    this.timers = Objects.requireNonNull(timers, "timers");
    this.version = Objects.requireNonNull(version, "version");
  }

  PircBotX openSession(
      String serverId,
      PircbotxConnectionState connection,
      IrcProperties.Server server,
      PircbotxCtcpRequestHandler ctcpHandler,
      BiConsumer<PircbotxConnectionState, String> reconnectScheduler,
      boolean disconnectOnSaslFailure) {
    bus.onNext(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.Connecting(Instant.now(), server.host(), server.port(), server.nick())));

    ListenerAdapter listener =
        bridgeListenerFactory.create(
            serverId,
            connection,
            bus,
            timers::stopHeartbeat,
            reconnectScheduler,
            ctcpHandler,
            disconnectOnSaslFailure);

    PircBotX bot = botFactory.build(server, version, listener);
    if (bot instanceof PircbotxLagAwareBot lagAwareBot) {
      lagAwareBot.setLagProbeObserver(connection::beginLagProbe);
    }
    connection.setBot(bot);
    inputParserHookInstaller.installIrcv3Hook(bot, serverId, connection, bus::onNext);
    timers.startHeartbeat(connection);
    return bot;
  }

  void runBotLoop(
      String serverId,
      PircbotxConnectionState connection,
      PircBotX bot,
      BiConsumer<PircbotxConnectionState, String> reconnectScheduler) {
    boolean crashed = false;
    try {
      bot.startBot();
    } catch (Exception e) {
      crashed = true;
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), "Bot crashed", e)));
    } finally {
      if (connection.clearBotIf(bot)) {
        timers.stopHeartbeat(connection);
      }
      if (crashed && !connection.manualDisconnectRequested()) {
        reconnectScheduler.accept(connection, "Bot crashed");
      }
    }
  }
}
