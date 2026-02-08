package cafe.woden.ircclient.irc;

import java.util.OptionalLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default bouncer cursor provider when no persistence-backed provider is available.
 */
@Component
@ConditionalOnMissingBean(PlaybackCursorProvider.class)
public class NoOpPlaybackCursorProvider implements PlaybackCursorProvider {

  @Override
  public OptionalLong lastSeenEpochSeconds(String serverId) {
    return OptionalLong.empty();
  }
}
