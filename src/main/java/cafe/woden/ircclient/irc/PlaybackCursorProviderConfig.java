package cafe.woden.ircclient.irc;

import java.util.OptionalLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hard fallback for {@link PlaybackCursorProvider}.
 *
 * <p>Some environments may not component-scan optional providers (or may be running an older
 * build). This ensures the app always has a safe default provider so Spring Boot can start.
 */
@Configuration
public class PlaybackCursorProviderConfig {

  @Bean
  @ConditionalOnMissingBean(PlaybackCursorProvider.class)
  public PlaybackCursorProvider noOpPlaybackCursorProvider() {
    return (String serverId) -> OptionalLong.empty();
  }
}
