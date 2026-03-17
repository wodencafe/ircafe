package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned contract for target-scoped history actions. */
@SecondaryPort
@ApplicationLayer
public interface TargetChatHistoryPort {

  void onTargetSelected(TargetRef target);

  void reset(TargetRef target);
}
