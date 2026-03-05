package cafe.woden.ircclient.irc.soju;

import cafe.woden.ircclient.bouncer.BouncerBackendDiscoveryHandler;
import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.BouncerNetworkDiscoveryOrchestrator;
import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates/updates ephemeral server entries for Soju-discovered networks.
 *
 * <p>These servers are not persisted to the runtime YAML; they exist only for the duration of the
 * bouncer control session.
 */
@Component
@ApplicationLayer
public class SojuEphemeralNetworkImporter implements BouncerBackendDiscoveryHandler {

  private static final Logger log = LoggerFactory.getLogger(SojuEphemeralNetworkImporter.class);

  private final BouncerNetworkDiscoveryOrchestrator orchestrator;
  private final SojuBouncerDiscoveryAdapter discoveryAdapter = new SojuBouncerDiscoveryAdapter();

  public SojuEphemeralNetworkImporter(
      SojuBouncerNetworkMappingStrategy mappingStrategy,
      ServerRegistry serverRegistry,
      EphemeralServerRegistry ephemeralServers,
      SojuAutoConnectStore autoConnect,
      RuntimeConfigStore runtimeConfig,
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

  public void onSojuNetworkDiscovered(SojuNetwork network) {
    BouncerDiscoveredNetwork discovered = discoveryAdapter.fromSojuNetwork(network);
    onNetworkDiscovered(discovered);
  }

  public void onSojuOriginDisconnected(String originServerId) {
    onOriginDisconnected(originServerId);
  }
}
