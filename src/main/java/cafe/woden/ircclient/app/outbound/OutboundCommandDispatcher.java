package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Dispatches parsed outbound commands to concrete outbound command services. */
@PrimaryPort
@ApplicationLayer
public interface OutboundCommandDispatcher {

  void dispatch(CompositeDisposable disposables, ParsedInput input);
}
