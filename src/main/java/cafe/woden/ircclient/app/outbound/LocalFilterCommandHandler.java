package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.FilterCommand;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned handler contract for local /filter commands. */
@ApplicationLayer
public interface LocalFilterCommandHandler {

  void handle(FilterCommand command);
}
