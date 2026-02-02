package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.TargetRef;

/**
 * Maintenance operations for persisted chat logs.
 *
 */
public interface ChatLogMaintenance {

  boolean enabled();

  void clearTarget(TargetRef target);
}
