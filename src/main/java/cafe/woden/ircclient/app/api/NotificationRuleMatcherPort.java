package cafe.woden.ircclient.app.api;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-facing port for matching incoming text against notification highlight rules. */
@ApplicationLayer
public interface NotificationRuleMatcherPort {

  List<NotificationRuleMatch> matchAll(String message);
}
