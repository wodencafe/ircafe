package cafe.woden.ircclient.app.outbound.backend.spi;

import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Handles backend-specific named commands parsed from slash input. */
@SecondaryPort
@ApplicationLayer
public interface BackendNamedOutboundCommandHandler {

  Set<String> supportedCommandNames();

  void handle(CompositeDisposable disposables, ParsedInput.BackendNamed command);
}
