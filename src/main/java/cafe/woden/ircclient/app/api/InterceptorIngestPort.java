package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-facing port for recording inbound events for interceptor evaluation. */
@SecondaryPort
@ApplicationLayer
public interface InterceptorIngestPort {

  void ingestEvent(
      String serverId,
      String channel,
      String fromNick,
      String fromHostmask,
      String text,
      InterceptorEventType eventType);
}
