package cafe.woden.ircclient.irc;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Ensures shared ZNC playback capture scheduler is released with the Spring context. */
@Component
@Lazy(false)
final class ZncPlaybackCaptureLifecycle {

  @PreDestroy
  void shutdown() {
    ZncPlaybackCaptureCoordinator.shutdownSharedScheduler();
  }
}
