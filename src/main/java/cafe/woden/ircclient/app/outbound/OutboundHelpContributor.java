package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.model.TargetRef;
import java.util.Map;
import java.util.function.Consumer;

/** Contributes backend-specific help snippets and topic handlers to outbound chat help. */
interface OutboundHelpContributor {

  default void appendGeneralHelp(TargetRef out) {}

  default Map<String, Consumer<TargetRef>> topicHelpHandlers() {
    return Map.of();
  }
}
