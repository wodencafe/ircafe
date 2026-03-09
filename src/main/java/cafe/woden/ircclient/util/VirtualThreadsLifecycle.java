package cafe.woden.ircclient.util;

import jakarta.annotation.PreDestroy;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Fallback shutdown hook for any app-owned virtual-thread executors created via {@link
 * VirtualThreads}.
 */
@Component
@Lazy(false)
@ApplicationLayer
final class VirtualThreadsLifecycle {

  @PreDestroy
  void shutdown() {
    VirtualThreads.shutdownTrackedExecutorsNow();
  }
}
