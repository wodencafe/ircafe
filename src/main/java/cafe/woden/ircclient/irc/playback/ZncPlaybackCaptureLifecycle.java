package cafe.woden.ircclient.irc.playback;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import jakarta.annotation.PreDestroy;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Ensures shared ZNC playback capture scheduler is released with the Spring context. */
@Component
@Lazy(false)
@InfrastructureLayer
final class ZncPlaybackCaptureLifecycle {

  @PreDestroy
  void shutdown() {
    ZncPlaybackCaptureCoordinator.shutdownSharedScheduler();
  }
}
