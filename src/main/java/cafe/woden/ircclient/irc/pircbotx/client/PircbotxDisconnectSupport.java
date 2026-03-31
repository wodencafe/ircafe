package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.irc.DisconnectRequestSource;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.time.Instant;
import java.util.Objects;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates disconnect-side lifecycle cleanup for a single connection. */
final class PircbotxDisconnectSupport {

  private static final Logger log = LoggerFactory.getLogger(PircbotxDisconnectSupport.class);

  private final FlowableProcessor<ServerIrcEvent> bus;
  private final ServerIsupportStatePort serverIsupportState;
  private final PircbotxConnectionTimersRx timers;
  private final BouncerBackendRegistry bouncerBackends;
  private final BouncerDiscoveryEventPort bouncerDiscoveryEvents;

  PircbotxDisconnectSupport(
      FlowableProcessor<ServerIrcEvent> bus,
      ServerIsupportStatePort serverIsupportState,
      PircbotxConnectionTimersRx timers,
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents) {
    this.bus = Objects.requireNonNull(bus, "bus");
    this.serverIsupportState = Objects.requireNonNull(serverIsupportState, "serverIsupportState");
    this.timers = Objects.requireNonNull(timers, "timers");
    this.bouncerBackends = Objects.requireNonNull(bouncerBackends, "bouncerBackends");
    this.bouncerDiscoveryEvents =
        Objects.requireNonNull(bouncerDiscoveryEvents, "bouncerDiscoveryEvents");
  }

  void disconnect(
      String serverId,
      PircbotxConnectionState connection,
      String reason,
      DisconnectRequestSource source) {
    DisconnectRequestSource requestSource =
        source == null ? DisconnectRequestSource.UNKNOWN : source;
    boolean clearDiscoveredNetworks = requestSource.clearDiscoveredBouncerNetworks();
    String renderedReason = Objects.toString(reason, "").trim();
    boolean hasBot = connection.hasBot();

    log.info(
        "[{}] disconnect requested: source={}, reason={}, hasBot={}, clearDiscoveredBouncerNetworks={}",
        serverId,
        requestSource,
        renderedReason.isEmpty() ? "(default)" : renderedReason,
        hasBot,
        clearDiscoveredNetworks);

    serverIsupportState.clearServer(serverId);
    connection.markManualDisconnect();
    timers.cancelReconnect(connection);
    timers.stopHeartbeat(connection);
    connection.resetLagProbeState();

    if (clearDiscoveredNetworks) {
      for (String backendId : bouncerBackends.backendIds()) {
        try {
          bouncerDiscoveryEvents.onOriginDisconnected(backendId, serverId);
        } catch (Exception ignored) {
        }
      }
    }

    PircBotX bot = connection.takeBot();
    if (bot == null) {
      emitDisconnected(serverId);
      return;
    }

    String quitReason = reason == null ? "" : reason.trim();
    if (quitReason.contains("\r") || quitReason.contains("\n")) {
      throw new IllegalArgumentException("quit reason contains CR/LF");
    }
    if (quitReason.isEmpty()) quitReason = "Client disconnect";

    try {
      bot.stopBotReconnect();
      try {
        bot.sendIRC().quitServer(quitReason);
      } catch (Exception ignored) {
      }
      try {
        bot.close();
      } catch (Exception ignored) {
      }
    } finally {
      emitDisconnected(serverId);
    }
  }

  private void emitDisconnected(String serverId) {
    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.Disconnected(Instant.now(), "Client requested disconnect")));
  }
}
