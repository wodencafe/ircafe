package cafe.woden.ircclient.app.outbound.help.spi;

import cafe.woden.ircclient.model.TargetRef;
import java.util.Map;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Contributes backend-specific help snippets and topic handlers to outbound chat help. */
@ApplicationLayer
public interface OutboundHelpContributor {

  default void appendGeneralHelp(TargetRef out) {}

  default Map<String, Consumer<TargetRef>> topicHelpHandlers() {
    return Map.of();
  }
}
