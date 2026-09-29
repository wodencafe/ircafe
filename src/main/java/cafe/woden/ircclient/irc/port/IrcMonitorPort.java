package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Event stream, MONITOR support, and command delivery for MONITOR/ISON presence tracking. */
@SecondaryPort
@ApplicationLayer
public interface IrcMonitorPort {

  /** Observe routed IRC events; each consumer owns and disposes its subscription. */
  Flowable<ServerIrcEvent> events();

  /** Whether the backend supports native MONITOR rather than ISON fallback. */
  boolean isMonitorAvailable(String serverId);

  /** Return the backend's negotiated MONITOR limit. */
  int negotiatedMonitorLimit(String serverId);

  /** Send a MONITOR or ISON protocol command assembled by the application. */
  Completable sendRaw(String serverId, String rawLine);
}
