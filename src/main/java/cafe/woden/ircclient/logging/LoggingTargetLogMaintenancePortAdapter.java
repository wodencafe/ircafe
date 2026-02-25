package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Logging-side adapter that implements the app-owned log maintenance port. */
@Component
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
