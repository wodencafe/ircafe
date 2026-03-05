package cafe.woden.ircclient.bouncer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Routes generic discovery events to backend-specific handlers. */
@Component
@ApplicationLayer
public class BouncerDiscoveryEventDispatcher implements BouncerDiscoveryEventPort {

  private final Map<String, BouncerBackendDiscoveryHandler> handlersByBackend;

  public BouncerDiscoveryEventDispatcher(List<BouncerBackendDiscoveryHandler> handlers) {
    Objects.requireNonNull(handlers, "handlers");
    HashMap<String, BouncerBackendDiscoveryHandler> map = new HashMap<>();
    for (BouncerBackendDiscoveryHandler handler : handlers) {
      if (handler == null) continue;
      String backend = normalize(handler.backendId());
      if (backend == null) continue;
      map.putIfAbsent(backend, handler);
    }
    this.handlersByBackend = Map.copyOf(map);
  }

  @Override
  public void onNetworkDiscovered(BouncerDiscoveredNetwork network) {
    if (network == null) return;
    BouncerBackendDiscoveryHandler handler = handlersByBackend.get(normalize(network.backendId()));
    if (handler == null) return;
    handler.onNetworkDiscovered(network);
  }

  @Override
  public void onOriginDisconnected(String backendId, String originServerId) {
    BouncerBackendDiscoveryHandler handler = handlersByBackend.get(normalize(backendId));
    if (handler == null) return;
    handler.onOriginDisconnected(originServerId);
  }

  private static String normalize(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v.toLowerCase(Locale.ROOT);
  }
}
