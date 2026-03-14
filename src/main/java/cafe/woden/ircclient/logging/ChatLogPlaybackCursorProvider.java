package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.irc.PlaybackCursorProvider;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Playback cursor based on the embedded chat log DB. */
@Component
@Primary
@ConditionalOnProperty(prefix = "ircafe.logging", name = "enabled", havingValue = "true")
@InfrastructureLayer
@RequiredArgsConstructor
public class ChatLogPlaybackCursorProvider implements PlaybackCursorProvider {

  private final ChatLogRepository repo;

  @Override
  public OptionalLong lastSeenEpochSeconds(String serverId) {
    OptionalLong maxMs = repo.maxTimestampForServer(serverId);
    if (maxMs.isEmpty()) return OptionalLong.empty();
    long sec = maxMs.getAsLong() / 1000L;
    if (sec < 0) sec = 0;
    return OptionalLong.of(sec);
  }
}
