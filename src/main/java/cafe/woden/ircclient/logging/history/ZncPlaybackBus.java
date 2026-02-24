package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** In-memory bus that carries ZNC Playback module ranges. */
@Component
public final class ZncPlaybackBus extends AbstractTargetWaiterBus<ZncPlaybackBus.PlaybackEvent> {

  private static final Logger log = LoggerFactory.getLogger(ZncPlaybackBus.class);

  public record PlaybackEvent(
      String serverId,
      String target,
      Instant fromInclusive,
      Instant toInclusive,
      List<ChatHistoryEntry> entries,
      long earliestTsEpochMs,
      long latestTsEpochMs) {
    public PlaybackEvent {
      entries = entries == null ? List.of() : List.copyOf(entries);
      fromInclusive = fromInclusive == null ? Instant.EPOCH : fromInclusive;
      toInclusive = toInclusive == null ? Instant.EPOCH : toInclusive;
    }
  }

  public ZncPlaybackBus(
      @Qualifier(ExecutorConfig.ZNC_PLAYBACK_BUS_SCHEDULER) ScheduledExecutorService scheduler) {
    super(scheduler, "ZNC playback", "ZNC playback bus completion failed", log);
  }

  @Override
  protected String serverIdOf(PlaybackEvent event) {
    return event.serverId();
  }

  @Override
  protected String targetOf(PlaybackEvent event) {
    return event.target();
  }
}
