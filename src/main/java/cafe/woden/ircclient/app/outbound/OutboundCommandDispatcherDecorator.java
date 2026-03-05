package cafe.woden.ircclient.app.outbound;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** Base decorator for {@link OutboundCommandDispatcher}. */
public abstract class OutboundCommandDispatcherDecorator implements OutboundCommandDispatcher {

  protected final OutboundCommandDispatcher delegate;

  protected OutboundCommandDispatcherDecorator(OutboundCommandDispatcher delegate) {
    this.delegate = delegate;
  }

  @Override
  public void openQuasselSetup(CompositeDisposable disposables, String serverId) {
    delegate.openQuasselSetup(disposables, serverId);
  }

  @Override
  public void openQuasselNetworkManager(CompositeDisposable disposables, String serverId) {
    delegate.openQuasselNetworkManager(disposables, serverId);
  }
}
