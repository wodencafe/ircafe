package cafe.woden.ircclient.config.api;

import cafe.woden.ircclient.model.InterceptorDefinition;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted interceptor definitions and custom asset paths. */
@ApplicationLayer
public interface InterceptorConfigPort extends RuntimeConfigPathPort {

  Map<String, List<InterceptorDefinition>> readInterceptorDefinitions();

  void rememberInterceptorDefinitions(Map<String, List<InterceptorDefinition>> defsByServer);
}
