package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** User-initiated UI event streams and interactive prompts. */
@ApplicationLayer
public interface UiInteractionPort {

  Flowable<TargetRef> targetSelections();

  Flowable<TargetRef> targetActivations();

  Flowable<PrivateMessageRequest> privateMessageRequests();

  Flowable<UserActionRequest> userActionRequests();

  Flowable<String> outboundLines();

  default boolean confirmMultilineSplitFallback(
      TargetRef target, int lineCount, long payloadUtf8Bytes, String reason) {
    return false;
  }

  Flowable<Object> connectClicks();

  Flowable<Object> disconnectClicks();

  Flowable<String> connectServerRequests();

  Flowable<String> disconnectServerRequests();

  default Flowable<ParsedInput.BackendNamed> backendNamedCommandRequests() {
    return Flowable.empty();
  }

  default void openQuasselNetworkManager(String serverId) {}

  Flowable<TargetRef> closeTargetRequests();

  default Flowable<TargetRef> joinChannelRequests() {
    return Flowable.empty();
  }

  default Flowable<TargetRef> disconnectChannelRequests() {
    return Flowable.empty();
  }

  default Flowable<TargetRef> bouncerDetachChannelRequests() {
    return Flowable.empty();
  }

  default Flowable<TargetRef> closeChannelRequests() {
    return Flowable.empty();
  }

  Flowable<TargetRef> clearLogRequests();

  default Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return Flowable.empty();
  }

  default Optional<QuasselCoreControlPort.QuasselCoreSetupRequest> promptQuasselCoreSetup(
      String serverId, QuasselCoreControlPort.QuasselCoreSetupPrompt prompt) {
    return Optional.empty();
  }

  default Optional<QuasselNetworkManagerAction> promptQuasselNetworkManagerAction(
      String serverId, List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    return Optional.empty();
  }
}
