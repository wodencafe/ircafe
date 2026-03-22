package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-driven UI prompts and interactive dialogs. */
@SecondaryPort
@ApplicationLayer
public interface UiPromptPort {

  default boolean confirmMultilineSplitFallback(
      TargetRef target, int lineCount, long payloadUtf8Bytes, String reason) {
    return false;
  }

  default void openQuasselNetworkManager(String serverId) {}

  default Optional<QuasselCoreControlPort.QuasselCoreSetupRequest> promptQuasselCoreSetup(
      String serverId, QuasselCoreControlPort.QuasselCoreSetupPrompt prompt) {
    return Optional.empty();
  }

  default Optional<QuasselNetworkManagerAction> promptQuasselNetworkManagerAction(
      String serverId, List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    return Optional.empty();
  }
}
