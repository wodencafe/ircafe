package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import java.util.Objects;
import org.pircbotx.PircBotX;

/** Performs best-effort local cleanup for client shutdown. */
final class PircbotxShutdownSupport {

  private final PircbotxConnectionTimersRx timers;

  PircbotxShutdownSupport(PircbotxConnectionTimersRx timers) {
    this.timers = Objects.requireNonNull(timers, "timers");
  }

  void shutdownConnection(PircbotxConnectionState connection, String quitReason) {
    connection.markManualDisconnect();
    timers.cancelReconnect(connection);
    timers.stopHeartbeat(connection);
    connection.resetLagProbeState();

    PircBotX bot = connection.takeBot();
    if (bot == null) {
      return;
    }

    try {
      if (bot.isConnected()) {
        try {
          bot.sendIRC().quitServer(quitReason);
        } catch (Exception ignored) {
        }
      }
    } catch (Exception ignored) {
    }

    try {
      bot.stopBotReconnect();
    } catch (Exception ignored) {
    }
    try {
      bot.close();
    } catch (Exception ignored) {
    }
  }
}
