package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Narrow execution context exposed to ServiceLoader-backed backend named commands. */
@ApplicationLayer
public interface BackendNamedCommandExecutionContext {

  TargetRef activeTarget();

  TargetRef safeStatusTarget();

  default TargetRef statusTarget(String serverId) {
    String sid = serverId == null ? "" : serverId.trim();
    return sid.isEmpty() ? safeStatusTarget() : new TargetRef(sid, "status");
  }

  default TargetRef activeTargetOrSafeStatusTarget() {
    TargetRef active = activeTarget();
    return active != null ? active : safeStatusTarget();
  }

  boolean isConnected(String serverId);

  void appendStatus(TargetRef target, String prefix, String message);

  void appendError(TargetRef target, String prefix, String message);

  void ensureTargetExists(TargetRef target);

  void selectTarget(TargetRef target);

  Completable sendRaw(String serverId, String line);
}
