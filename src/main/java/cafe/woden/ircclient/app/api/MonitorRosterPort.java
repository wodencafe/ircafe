package cafe.woden.ircclient.app.api;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-facing port for persisting and mutating monitor nick rosters. */
@ApplicationLayer
public interface MonitorRosterPort {

  List<String> listNicks(String serverId);

  int addNicks(String serverId, List<String> rawNicks);

  int removeNicks(String serverId, List<String> rawNicks);

  int clearNicks(String serverId);

  List<String> parseNickInput(String rawInput);
}
