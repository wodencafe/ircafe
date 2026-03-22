package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for invite auto-join settings. */
@SecondaryPort
@ApplicationLayer
public interface InviteAutoJoinConfigPort {

  boolean readInviteAutoJoinEnabled(boolean defaultValue);

  void rememberInviteAutoJoinEnabled(boolean enabled);
}
