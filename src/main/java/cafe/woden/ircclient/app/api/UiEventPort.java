package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Flowable;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** User-initiated UI event streams entering the application layer. */
@PrimaryPort
@ApplicationLayer
public interface UiEventPort {

  Flowable<TargetRef> targetSelections();

  Flowable<TargetRef> targetActivations();

  Flowable<PrivateMessageRequest> privateMessageRequests();

  Flowable<UserActionRequest> userActionRequests();

  Flowable<String> outboundLines();

  Flowable<Object> connectClicks();

  Flowable<Object> disconnectClicks();

  Flowable<String> connectServerRequests();

  Flowable<String> disconnectServerRequests();

  default Flowable<ParsedInput.BackendNamed> backendNamedCommandRequests() {
    return Flowable.empty();
  }

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
}
