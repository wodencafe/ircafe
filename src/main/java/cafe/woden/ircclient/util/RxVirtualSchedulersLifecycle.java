package cafe.woden.ircclient.util;

import jakarta.annotation.PreDestroy;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Ensures shared Rx scheduler executors are shut down with the Spring context. */
@Component
@Lazy(false)
@ApplicationLayer
final class RxVirtualSchedulersLifecycle {

  @PreDestroy
  void shutdown() {
    RxVirtualSchedulers.shutdown();
  }
}
