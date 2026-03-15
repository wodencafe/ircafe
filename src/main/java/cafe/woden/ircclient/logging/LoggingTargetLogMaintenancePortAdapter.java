package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.model.TargetRef;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Logging-side adapter that implements the app-owned log maintenance port. */
@Component
@SecondaryAdapter
@InfrastructureLayer
@RequiredArgsConstructor
public class LoggingTargetLogMaintenancePortAdapter implements TargetLogMaintenancePort {

  @NonNull private final ChatLogMaintenance delegate;

  @Override
  public void clearTarget(TargetRef target) {
    delegate.clearTarget(target);
  }
}
