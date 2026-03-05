package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.model.TargetRef;

public interface ChatLogMaintenance {

  boolean enabled();

  void clearTarget(TargetRef target);
}
