package cafe.woden.ircclient.irc.znc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZncEphemeralNetworkImporterTest {

  @Test
  void upsertsEphemeralServerForDiscoveredNetwork() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "znc",
            "bouncer.example",
            6697,
            true,
            "pass",
            "nick",
            "user@ircafe",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    ZncAutoConnectStore autoConnect =
        new ZncAutoConnectStore(
            new ZncProperties(Map.of(), new ZncProperties.Discovery(true)), runtime);
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect(anyString())).thenReturn(Completable.complete());

    ZncEphemeralNetworkImporter importer =
        new ZncEphemeralNetworkImporter(configured, ephemeral, autoConnect, irc);

    importer.onNetworkDiscovered(new ZncNetwork("znc", "Libera.Chat", true));

    assertTrue(ephemeral.containsId("znc:znc:libera.chat"));
    IrcProperties.Server imported = ephemeral.require("znc:znc:libera.chat");
    assertEquals("user@ircafe/Libera.Chat", imported.login());
    assertEquals("user@ircafe/Libera.Chat", imported.sasl().username());
    assertEquals("znc", ephemeral.originOf(imported.id()).orElseThrow());
  }

  @Test
  void callingTwiceDoesNotCreateDuplicate() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "znc",
            "bouncer.example",
            6697,
            true,
            "pass",
            "nick",
            "user",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    ZncAutoConnectStore autoConnect =
        new ZncAutoConnectStore(
            new ZncProperties(Map.of(), new ZncProperties.Discovery(true)), runtime);
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect(anyString())).thenReturn(Completable.complete());

    ZncEphemeralNetworkImporter importer =
        new ZncEphemeralNetworkImporter(configured, ephemeral, autoConnect, irc);
    ZncNetwork net = new ZncNetwork("znc", "oftc", false);

    importer.onNetworkDiscovered(net);
    importer.onNetworkDiscovered(net);

    assertEquals(1, ephemeral.serverIds().size());
    assertTrue(ephemeral.containsId("znc:znc:oftc"));
  }

  @Test
  void originDisconnectRemovesEphemerals() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "znc",
            "bouncer.example",
            6697,
            true,
            "pass",
            "nick",
            "user",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    ZncAutoConnectStore autoConnect =
        new ZncAutoConnectStore(
            new ZncProperties(Map.of(), new ZncProperties.Discovery(true)), runtime);
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect(anyString())).thenReturn(Completable.complete());

    ZncEphemeralNetworkImporter importer =
        new ZncEphemeralNetworkImporter(configured, ephemeral, autoConnect, irc);

    importer.onNetworkDiscovered(new ZncNetwork("znc", "libera", true));
    importer.onNetworkDiscovered(new ZncNetwork("znc", "oftc", true));

    assertEquals(2, ephemeral.serverIds().size());

    importer.onOriginDisconnected("znc");

    assertEquals(0, ephemeral.serverIds().size());
  }
}
