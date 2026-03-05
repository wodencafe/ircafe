package cafe.woden.ircclient.irc.znc;

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
 * Creates/updates ephemeral server entries for ZNC-discovered networks.
 *
 * <p>These servers are not persisted to the runtime YAML; they exist only for the duration of the
 * bouncer control session.
 */
@Component
@ApplicationLayer
public class ZncEphemeralNetworkImporter implements BouncerBackendDiscoveryHandler {

  private static final Logger log = LoggerFactory.getLogger(ZncEphemeralNetworkImporter.class);

  private final BouncerNetworkDiscoveryOrchestrator orchestrator;
  private final ZncBouncerDiscoveryAdapter discoveryAdapter = new ZncBouncerDiscoveryAdapter();

  public ZncEphemeralNetworkImporter(
      ZncBouncerNetworkMappingStrategy mappingStrategy,
      ServerRegistry serverRegistry,
      EphemeralServerRegistry ephemeralServers,
      ZncAutoConnectStore autoConnect,
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

  public void onZncNetworkDiscovered(ZncNetwork network) {
    BouncerDiscoveredNetwork discovered = discoveryAdapter.fromZncNetwork(network);
    onNetworkDiscovered(discovered);
  }

  public void onZncOriginDisconnected(String originServerId) {
    onOriginDisconnected(originServerId);
  }
}
