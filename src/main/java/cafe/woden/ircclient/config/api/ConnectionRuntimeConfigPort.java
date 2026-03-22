package cafe.woden.ircclient.config.api;

import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract used by connection orchestration flows. */
@SecondaryPort
@ApplicationLayer
public interface ConnectionRuntimeConfigPort {

  Map<String, Boolean> readServerAutoConnectOnStartByServer();

  List<String> readPrivateMessageTargets(String serverId);

  List<String> readKnownChannels(String serverId);
}
