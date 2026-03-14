package cafe.woden.ircclient.irc.backend;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Contract for applying heartbeat setting changes to active IRC transports. */
@ApplicationLayer
public interface IrcHeartbeatMaintenanceService {

  /** Re-apply heartbeat scheduling for currently active connections. */
  void rescheduleActiveHeartbeats();
}
