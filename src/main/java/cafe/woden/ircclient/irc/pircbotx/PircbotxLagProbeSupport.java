package cafe.woden.ircclient.irc.pircbotx;

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
    if (!connection.registrationComplete.get()) {
      throw new IllegalStateException("Registration not complete: " + serverId);
    }

    String token =
        "ircafe-lag-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
    connection.beginLagProbe(token, System.currentTimeMillis());
    bot.sendRaw().rawLine("PING :" + token);
  }

  boolean isLagProbeReady(PircbotxConnectionState connection) {
    return connection != null && connection.hasBot() && connection.registrationComplete.get();
  }

  OptionalLong lastMeasuredLagMs(PircbotxConnectionState connection, long nowMs) {
    if (connection == null) {
      return OptionalLong.empty();
    }
    long lagMs = connection.lagMsIfFresh(nowMs);
    return lagMs >= 0L ? OptionalLong.of(lagMs) : OptionalLong.empty();
  }
}
