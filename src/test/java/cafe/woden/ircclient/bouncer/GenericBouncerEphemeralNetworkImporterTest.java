package cafe.woden.ircclient.bouncer;

import static cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures.bouncerDiscoveryPort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.spi.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.config.servers.EphemeralServerRegistry;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import io.reactivex.rxjava3.core.Completable;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericBouncerEphemeralNetworkImporterTest {

  @TempDir Path tempDir;

  @Test
  void originDisconnectClearsAutoConnectQueueForRediscovery() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "base-user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("bouncer-1")
            .host("bouncer.example")
            .nick("nick")
            .login("base-user")
            .realName("Real")
            .sasl(sasl)
            .build();

    IrcProperties props = IrcPropertiesTestFixtures.properties(bouncer);
    RuntimeConfigStore runtime =
        RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"), props);
    runtime.rememberGenericBouncerAutoConnectNetwork("bouncer-1", "Libera", true);

    ServerRegistry configured =
        new ServerRegistry(props, RuntimeConfigStoreTestFixtures.serverRegistryPort(runtime));
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    GenericBouncerAutoConnectStore autoConnect =
        new GenericBouncerAutoConnectStore(bouncerDiscoveryPort(runtime));

    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    GenericBouncerEphemeralNetworkImporter importer =
        new GenericBouncerEphemeralNetworkImporter(
            new GenericBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);

    BouncerDiscoveredNetwork network =
        new BouncerDiscoveredNetwork(
            "generic", "bouncer-1", "net1", "Libera", "Libera", null, java.util.Set.of(), Map.of());

    importer.onNetworkDiscovered(network);
    importer.onOriginDisconnected("bouncer-1");
    importer.onNetworkDiscovered(network);

    verify(connectionPort, times(2)).connect("bouncer:bouncer-1:net1");
  }
}
