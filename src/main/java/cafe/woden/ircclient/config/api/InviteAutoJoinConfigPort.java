package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for invite auto-join settings. */
@ApplicationLayer
public interface InviteAutoJoinConfigPort {

  boolean readInviteAutoJoinEnabled(boolean defaultValue);

  void rememberInviteAutoJoinEnabled(boolean enabled);
}
