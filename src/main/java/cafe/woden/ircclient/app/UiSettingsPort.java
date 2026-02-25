package cafe.woden.ircclient.app;

import java.beans.PropertyChangeListener;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned read/listen contract for UI settings consumed by application services. */
@ApplicationLayer
public interface UiSettingsPort {

  UiSettingsSnapshot get();

  void addListener(PropertyChangeListener listener);

  void removeListener(PropertyChangeListener listener);
}
