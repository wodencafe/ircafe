package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for diagnostics settings and export directories. */
@ApplicationLayer
public interface DiagnosticsRuntimeConfigPort extends RuntimeConfigPathPort {

  boolean readApplicationJfrEnabled(boolean defaultValue);

  void rememberApplicationJfrEnabled(boolean enabled);
}
