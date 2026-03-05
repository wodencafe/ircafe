package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** Dispatches parsed outbound commands to concrete outbound command services. */
public interface OutboundCommandDispatcher {

  void dispatch(CompositeDisposable disposables, ParsedInput input);

  /** Opens Quassel setup flow for the given server id. */
  default void openQuasselSetup(CompositeDisposable disposables, String serverId) {}

  /** Opens dialog-driven Quassel network manager flow for the given server id. */
  default void openQuasselNetworkManager(CompositeDisposable disposables, String serverId) {}
}
