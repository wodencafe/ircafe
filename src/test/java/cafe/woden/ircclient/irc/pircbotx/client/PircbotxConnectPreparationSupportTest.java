package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
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
        IrcPropertiesTestFixtures.serverBuilder("libera")
            .host("irc.example.net")
            .port(6667)
            .tls(false)
            .nick("OldNick")
            .login("loginuser@loginclient")
            .realName("Old Real")
            .sasl(
                new IrcProperties.Server.Sasl(
                    true, "sasluser@saslclient/netb", "pw", "PLAIN", false))
            .build();
    IrcProperties.Server secured =
        IrcPropertiesTestFixtures.serverBuilder("libera")
            .host("irc.secure.example.net")
            .port(6697)
            .tls(true)
            .nick("NewNick")
            .login(configured.login())
            .realName(configured.realName())
            .sasl(configured.sasl())
            .nickserv(configured.nickserv())
            .autoJoin(configured.autoJoin())
            .perform(configured.perform())
            .proxy(configured.proxy())
            .backend(configured.backend())
            .build();
    when(serverCatalog.require("libera")).thenReturn(configured);
    when(stsPolicies.applyPolicy(configured)).thenReturn(secured);

    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.markManualDisconnect();
    connection.setReconnectAttempts(5L);
    connection.setBatchCapAcked(true);
    connection.setMessageTagsCapAcked(true);
    connection.setConnectedEndpoint("old.example.net", false);
    connection.setSelfNickHint("staleNick");
    connection.markZncDetected();
    connection.markZncDetectionLogged();
    connection.setZncLoginContext("staleUser", "staleClient", "staleNetwork");
    connection.beginZncPlaybackRequest();
    connection.beginZncListNetworksRequest();
    connection.storeSojuDiscoveredNetwork(
        "net", mock(cafe.woden.ircclient.bouncer.spi.BouncerDiscoveredNetwork.class));
    connection.storeGenericBouncerDiscoveredNetwork(
        "generic", mock(cafe.woden.ircclient.bouncer.spi.BouncerDiscoveredNetwork.class));
    connection.beginSojuListNetworksRequest();
    connection.setSojuBouncerNetId("bound-net");

    PircbotxConnectPreparationSupport.PreparedConnect prepared =
        support.prepare("libera", connection);

    verify(serverIsupportState).clearServer("libera");
    verify(timers).cancelReconnect(connection);
    assertEquals(secured, prepared.server());
    assertFalse(prepared.disconnectOnSaslFailure());
    assertFalse(connection.manualDisconnectRequested());
    assertEquals(5L, connection.reconnectAttempts());
    assertFalse(connection.capabilitySnapshot().batchCapAcked());
    assertFalse(connection.capabilitySnapshot().messageTagsCapAcked());
    assertEquals("irc.secure.example.net", connection.connectedHost());
    assertTrue(connection.connectedWithTls());
    assertEquals("NewNick", connection.selfNickHint());
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
