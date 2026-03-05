package cafe.woden.ircclient.bouncer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import io.reactivex.rxjava3.core.Completable;
import java.nio.file.Path;
import java.util.List;
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
        new IrcProperties.Server(
            "bouncer-1",
            "bouncer.example",
            6697,
            true,
            "",
            "nick",
            "base-user",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime =
        new RuntimeConfigStore(tempDir.resolve("ircafe.yml").toString(), props);
    runtime.rememberGenericBouncerAutoConnectNetwork("bouncer-1", "Libera", true);

    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    GenericBouncerAutoConnectStore autoConnect = new GenericBouncerAutoConnectStore(runtime);

    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    GenericBouncerEphemeralNetworkImporter importer =
        new GenericBouncerEphemeralNetworkImporter(
            new GenericBouncerNetworkMappingStrategy(runtime),
            configured,
            ephemeral,
            autoConnect,
            runtime,
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
