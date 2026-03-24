package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxConnectPreparationSupportTest {

  @Test
  void prepareResetsSessionStateAndAppliesStsAdjustedServerMetadata() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    Ircv3StsPolicyService stsPolicies = mock(Ircv3StsPolicyService.class);
    ServerIsupportStatePort serverIsupportState = mock(ServerIsupportStatePort.class);
    PircbotxConnectionTimersRx timers = mock(PircbotxConnectionTimersRx.class);
    PircbotxConnectPreparationSupport support =
        new PircbotxConnectPreparationSupport(
            serverCatalog, stsPolicies, serverIsupportState, timers);

    IrcProperties.Server configured =
        new IrcProperties.Server(
            "libera",
            "irc.example.net",
            6667,
            false,
            "",
            "OldNick",
            "loginuser@loginclient",
            "Old Real",
            new IrcProperties.Server.Sasl(true, "sasluser@saslclient/netb", "pw", "PLAIN", false),
            null,
            List.of(),
            List.of(),
            null);
    IrcProperties.Server secured =
        new IrcProperties.Server(
            "libera",
            "irc.secure.example.net",
            6697,
            true,
            "",
            "NewNick",
            configured.login(),
            configured.realName(),
            configured.sasl(),
            configured.nickserv(),
            configured.autoJoin(),
            configured.perform(),
            configured.proxy(),
            configured.backend());
    when(serverCatalog.require("libera")).thenReturn(configured);
    when(stsPolicies.applyPolicy(configured)).thenReturn(secured);

    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.manualDisconnect.set(true);
    connection.reconnectAttempts.set(5L);
    connection.batchCapAcked.set(true);
    connection.messageTagsCapAcked.set(true);
    connection.setConnectedEndpoint("old.example.net", false);
    connection.selfNickHint.set("staleNick");
    connection.markZncDetected();
    connection.markZncDetectionLogged();
    connection.zncBaseUser.set("staleUser");
    connection.zncClientId.set("staleClient");
    connection.zncNetwork.set("staleNetwork");
    connection.beginZncPlaybackRequest();
    connection.beginZncListNetworksRequest();
    connection.storeSojuDiscoveredNetwork(
        "net", mock(cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork.class));
    connection.storeGenericBouncerDiscoveredNetwork(
        "generic", mock(cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork.class));
    connection.beginSojuListNetworksRequest();
    connection.setSojuBouncerNetId("bound-net");

    PircbotxConnectPreparationSupport.PreparedConnect prepared =
        support.prepare("libera", connection);

    verify(serverIsupportState).clearServer("libera");
    verify(timers).cancelReconnect(connection);
    assertEquals(secured, prepared.server());
    assertFalse(prepared.disconnectOnSaslFailure());
    assertFalse(connection.manualDisconnect.get());
    assertEquals(0L, connection.reconnectAttempts.get());
    assertFalse(connection.batchCapAcked.get());
    assertFalse(connection.messageTagsCapAcked.get());
    assertEquals("irc.secure.example.net", connection.connectedHost());
    assertTrue(connection.connectedWithTls());
    assertEquals("NewNick", connection.selfNickHint.get());
    assertEquals("loginuser", connection.zncBaseUser());
    assertEquals("loginclient", connection.zncClientId());
    assertEquals("netb", connection.zncNetwork());
    assertFalse(connection.isZncDetected());
    assertFalse(connection.zncDetectionLogged());
    assertFalse(connection.zncPlaybackRequestedThisSession());
    assertFalse(connection.zncListNetworksRequestedThisSession());
    assertFalse(connection.hasAnySojuDiscoveredNetworks());
    assertFalse(connection.hasAnyGenericBouncerDiscoveredNetworks());
    assertFalse(connection.sojuListNetworksRequestedThisSession());
    assertEquals("", connection.sojuBouncerNetId());
  }
}
