package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.config.properties.ConfigPropertyKeys;
import cafe.woden.ircclient.irc.playback.PlaybackCursorProvider;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Playback cursor based on received conversation messages in the embedded chat log DB. */
@Component
@Primary
@ConditionalOnProperty(
    prefix = ConfigPropertyKeys.IRCAFE_LOGGING_PREFIX,
    name = ConfigPropertyKeys.ENABLED_PROPERTY,
    havingValue = ConfigPropertyKeys.TRUE_VALUE)
@InfrastructureLayer
@RequiredArgsConstructor
public class ChatLogPlaybackCursorProvider implements PlaybackCursorProvider {

  private final ChatLogRepository repo;

  @Override
  public OptionalLong lastSeenEpochSeconds(String serverId) {
    OptionalLong maxMs = repo.maxMessageTimestampForServer(serverId);
    if (maxMs.isEmpty()) return OptionalLong.empty();
    long sec = maxMs.getAsLong() / 1000L;
    if (sec < 0) sec = 0;
    return OptionalLong.of(sec);
  }
}
