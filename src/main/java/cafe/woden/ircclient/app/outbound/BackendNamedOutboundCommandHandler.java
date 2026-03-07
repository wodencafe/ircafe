package cafe.woden.ircclient.app.outbound;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** Handles backend-specific named commands parsed from slash input. */
interface BackendNamedOutboundCommandHandler {

  boolean supports(String commandName);

  void handle(CompositeDisposable disposables, String commandName, String args);
}
