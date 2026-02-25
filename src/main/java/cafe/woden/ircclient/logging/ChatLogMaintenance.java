package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.api.TargetRef;

public interface ChatLogMaintenance {

  boolean enabled();

  void clearTarget(TargetRef target);
}
