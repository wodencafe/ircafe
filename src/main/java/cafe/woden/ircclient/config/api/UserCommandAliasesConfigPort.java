package cafe.woden.ircclient.config.api;

import cafe.woden.ircclient.model.UserCommandAlias;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract used to seed user-command alias state. */
@SecondaryPort
@ApplicationLayer
public interface UserCommandAliasesConfigPort {

  List<UserCommandAlias> readUserCommandAliases();

  boolean readUnknownCommandAsRawEnabled(boolean defaultValue);
}
