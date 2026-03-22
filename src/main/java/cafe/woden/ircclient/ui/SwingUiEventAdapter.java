package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.UiEventPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Flowable;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Primary Swing adapter exposing user-driven event streams to the app layer. */
@Component("swingUiEventPort")
@PrimaryAdapter
@InterfaceLayer
@Lazy
public class SwingUiEventAdapter implements UiEventPort {

  private final UiEventPort eventPort;

  @Autowired
  public SwingUiEventAdapter(SwingUiPortDelegates delegates) {
    this.eventPort = delegates.eventPort();
  }

  @Override
  public Flowable<TargetRef> targetSelections() {
    return eventPort.targetSelections();
  }

  @Override
  public Flowable<TargetRef> targetActivations() {
    return eventPort.targetActivations();
  }

  @Override
  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return eventPort.privateMessageRequests();
  }

  @Override
  public Flowable<UserActionRequest> userActionRequests() {
    return eventPort.userActionRequests();
  }

  @Override
  public Flowable<String> outboundLines() {
    return eventPort.outboundLines();
  }

  @Override
  public Flowable<Object> connectClicks() {
    return eventPort.connectClicks();
  }

  @Override
  public Flowable<Object> disconnectClicks() {
    return eventPort.disconnectClicks();
  }

  @Override
  public Flowable<String> connectServerRequests() {
    return eventPort.connectServerRequests();
  }

  @Override
  public Flowable<String> disconnectServerRequests() {
    return eventPort.disconnectServerRequests();
  }

  @Override
  public Flowable<ParsedInput.BackendNamed> backendNamedCommandRequests() {
    return eventPort.backendNamedCommandRequests();
  }

  @Override
  public Flowable<TargetRef> closeTargetRequests() {
    return eventPort.closeTargetRequests();
  }

  @Override
  public Flowable<TargetRef> joinChannelRequests() {
    return eventPort.joinChannelRequests();
  }

  @Override
  public Flowable<TargetRef> disconnectChannelRequests() {
    return eventPort.disconnectChannelRequests();
  }

  @Override
  public Flowable<TargetRef> bouncerDetachChannelRequests() {
    return eventPort.bouncerDetachChannelRequests();
  }

  @Override
  public Flowable<TargetRef> closeChannelRequests() {
    return eventPort.closeChannelRequests();
  }

  @Override
  public Flowable<TargetRef> clearLogRequests() {
    return eventPort.clearLogRequests();
  }

  @Override
  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return eventPort.ircv3CapabilityToggleRequests();
  }
}
