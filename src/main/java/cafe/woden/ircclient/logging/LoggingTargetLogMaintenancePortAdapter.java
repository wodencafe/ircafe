package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Logging-side adapter that implements the app-owned log maintenance port. */
@Component
@InfrastructureLayer
public class LoggingTargetLogMaintenancePortAdapter implements TargetLogMaintenancePort {

  private final ChatLogMaintenance delegate;

  public LoggingTargetLogMaintenancePortAdapter(ChatLogMaintenance delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public void clearTarget(TargetRef target) {
    delegate.clearTarget(target);
  }
}
