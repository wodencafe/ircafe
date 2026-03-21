package cafe.woden.ircclient.config.api;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted monitor roster state. */
@ApplicationLayer
public interface MonitorRosterConfigPort {

  void replaceMonitorNicks(String serverId, List<String> nicks);

  List<String> readMonitorNicks(String serverId);
}
