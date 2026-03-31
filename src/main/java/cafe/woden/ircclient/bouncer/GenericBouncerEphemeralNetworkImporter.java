package cafe.woden.ircclient.bouncer;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.BouncerDiscoveryConfigPort;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Generic backend handler for bouncer discovery events. */
@Component
@ApplicationLayer
public class GenericBouncerEphemeralNetworkImporter implements BouncerBackendDiscoveryHandler {

  private static final Logger log =
      LoggerFactory.getLogger(GenericBouncerEphemeralNetworkImporter.class);

  private final BouncerNetworkDiscoveryOrchestrator orchestrator;

  public GenericBouncerEphemeralNetworkImporter(
      GenericBouncerNetworkMappingStrategy mappingStrategy,
      ServerRegistry serverRegistry,
      EphemeralServerRegistry ephemeralServers,
      GenericBouncerAutoConnectStore autoConnect,
      BouncerDiscoveryConfigPort runtimeConfig,
      BouncerConnectionPort connectionPort) {
    this.orchestrator =
        new BouncerNetworkDiscoveryOrchestrator(
            log,
            Objects.requireNonNull(mappingStrategy, "mappingStrategy"),
            serverRegistry,
            ephemeralServers,
            autoConnect,
            runtimeConfig,
            connectionPort);
  }

  @Override
  public String backendId() {
    return orchestrator.backendId();
  }

  @Override
  public void onNetworkDiscovered(BouncerDiscoveredNetwork network) {
    orchestrator.onNetworkDiscovered(network);
  }

  @Override
  public void onOriginDisconnected(String originServerId) {
    orchestrator.onOriginDisconnected(originServerId);
  }
}
