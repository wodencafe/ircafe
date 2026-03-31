package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.time.Instant;
import java.util.Objects;
import org.pircbotx.PircBotX;

/** Sends ZNC playback requests and coordinates their capture lifecycle. */
final class PircbotxZncPlaybackRequestSupport {

  private final FlowableProcessor<ServerIrcEvent> bus;

  PircbotxZncPlaybackRequestSupport(FlowableProcessor<ServerIrcEvent> bus) {
    this.bus = Objects.requireNonNull(bus, "bus");
  }

  void requestPlaybackRange(
      String serverId,
      PircbotxConnectionState connection,
      String target,
      Instant fromInclusive,
      Instant toInclusive) {
    if (!connection.isZncPlaybackCapAcked()) {
      throw new IllegalStateException("ZNC playback not negotiated (znc.in/playback): " + serverId);
    }

    String renderedTarget = target == null ? "" : target.trim();
    if (renderedTarget.isEmpty()) {
      throw new IllegalArgumentException("target is blank");
    }

    Instant fromCap = fromInclusive == null ? Instant.EPOCH : fromInclusive;
    Instant toCap = toInclusive == null ? Instant.now() : toInclusive;
    connection.startZncPlaybackCapture(serverId, renderedTarget, fromCap, toCap, bus::onNext);

    try {
      String sanitizedTarget = sanitizeTarget(renderedTarget);
      long from = fromCap.getEpochSecond();
      long to = toInclusive == null ? 0L : toInclusive.getEpochSecond();
      String command =
          to > 0L
              ? "play " + sanitizedTarget + " " + from + " " + to
              : "play " + sanitizedTarget + " " + from;
      requireConnectedBot(serverId, connection).sendIRC().message("*playback", command);
    } catch (Exception ex) {
      connection.cancelZncPlaybackCapture("send-failed");
      throw ex;
    }
  }

  private static PircBotX requireConnectedBot(String serverId, PircbotxConnectionState connection) {
    PircBotX bot = connection.currentBot();
    if (bot == null) {
      throw new IllegalStateException("Not connected: " + serverId);
    }
    return bot;
  }

  private static String sanitizeTarget(String target) {
    if (target.startsWith("#") || target.startsWith("&")) {
      return PircbotxUtil.sanitizeChannel(target);
    }
    return PircbotxUtil.sanitizeNick(target);
  }
}
