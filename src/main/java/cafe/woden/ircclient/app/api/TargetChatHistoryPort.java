package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned contract for target-scoped history actions. */
@ApplicationLayer
public interface TargetChatHistoryPort {

  void onTargetSelected(TargetRef target);

  void reset(TargetRef target);
}
