package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;
import org.pircbotx.PircBotX;

/** Sends and reads client-driven lag probe state for a single connection. */
final class PircbotxLagProbeSupport {

  void requestLagProbe(String serverId, PircbotxConnectionState connection) {
    PircBotX bot = connection.currentBot();
    if (bot == null) {
      throw new IllegalStateException("Not connected: " + serverId);
    }
    if (!connection.registrationComplete()) {
      throw new IllegalStateException("Registration not complete: " + serverId);
    }

    String token =
        "ircafe-lag-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
    connection.beginLagProbe(token, System.currentTimeMillis());
    bot.sendRaw().rawLine("PING :" + token);
  }

  boolean isLagProbeReady(PircbotxConnectionState connection) {
    return connection != null && connection.hasBot() && connection.registrationComplete();
  }

  OptionalLong lastMeasuredLagMs(PircbotxConnectionState connection, long nowMs) {
    if (connection == null) {
      return OptionalLong.empty();
    }
    long lagMs = connection.lagMsIfFresh(nowMs);
    return lagMs >= 0L ? OptionalLong.of(lagMs) : OptionalLong.empty();
  }
}
