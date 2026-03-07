package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Set;

/** Handles backend-specific named commands parsed from slash input. */
interface BackendNamedOutboundCommandHandler {

  Set<String> supportedCommandNames();

  void handle(CompositeDisposable disposables, ParsedInput.BackendNamed command);
}
