package cafe.woden.ircclient.ui.input;

import jakarta.annotation.PreDestroy;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Ensures shared spellcheck worker resources are released with the Spring context. */
@Component
@InterfaceLayer
@Lazy(false)
final class MessageInputSpellcheckLifecycle {

  @PreDestroy
  void shutdown() {
    MessageInputSpellcheckSupport.shutdownSharedResources();
  }
}
