package cafe.woden.ircclient.config.api;

import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted per-nick color overrides. */
@ApplicationLayer
public interface NickColorOverridesConfigPort {

  void rememberNickColorOverrides(Map<String, String> overrides);
}
