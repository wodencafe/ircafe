package cafe.woden.ircclient.ui.servertree.request;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Objects;

/** Base decorator for extending request emission behavior. */
public abstract class ServerTreeRequestEmitterDecorator implements ServerTreeRequestEmitter {

  private final ServerTreeRequestEmitter delegate;

  protected ServerTreeRequestEmitterDecorator(ServerTreeRequestEmitter delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  protected final ServerTreeRequestEmitter delegate() {
    return delegate;
  }

  @Override
  public void emitConnectServer(String serverId) {
    delegate.emitConnectServer(serverId);
  }

  @Override
  public void emitDisconnectServer(String serverId) {
    delegate.emitDisconnectServer(serverId);
  }

  @Override
  public void emitCloseTarget(TargetRef ref) {
    delegate.emitCloseTarget(ref);
  }

  @Override
  public void emitJoinChannel(TargetRef ref) {
    delegate.emitJoinChannel(ref);
  }

  @Override
  public void emitDisconnectChannel(TargetRef ref) {
    delegate.emitDisconnectChannel(ref);
  }

  @Override
  public void emitBouncerDetachChannel(TargetRef ref) {
    delegate.emitBouncerDetachChannel(ref);
  }

  @Override
  public void emitCloseChannel(TargetRef ref) {
    delegate.emitCloseChannel(ref);
  }

  @Override
  public void emitManagedChannelsChanged(String serverId) {
    delegate.emitManagedChannelsChanged(serverId);
  }

  @Override
  public void emitClearLog(TargetRef target) {
    delegate.emitClearLog(target);
  }

  @Override
  public void emitOpenPinnedChat(TargetRef ref) {
    delegate.emitOpenPinnedChat(ref);
  }

  @Override
  public void emitIrcv3CapabilityToggle(Ircv3CapabilityToggleRequest request) {
    delegate.emitIrcv3CapabilityToggle(request);
  }
}
