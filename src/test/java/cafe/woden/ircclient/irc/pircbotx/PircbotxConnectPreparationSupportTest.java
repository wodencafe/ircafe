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
    connection.connectedHost.set("old.example.net");
    connection.connectedWithTls.set(false);
    connection.selfNickHint.set("staleNick");
    connection.zncDetected.set(true);
    connection.zncDetectedLogged.set(true);
    connection.zncBaseUser.set("staleUser");
    connection.zncClientId.set("staleClient");
    connection.zncNetwork.set("staleNetwork");
    connection.zncPlaybackRequestedThisSession.set(true);
    connection.zncListNetworksRequestedThisSession.set(true);
    connection.sojuNetworksByNetId.put(
        "net", mock(cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork.class));
    connection.genericBouncerNetworksById.put(
        "generic", mock(cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork.class));
    connection.sojuListNetworksRequestedThisSession.set(true);
    connection.sojuBouncerNetId.set("bound-net");

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
    assertEquals("irc.secure.example.net", connection.connectedHost.get());
    assertTrue(connection.connectedWithTls.get());
    assertEquals("NewNick", connection.selfNickHint.get());
    assertEquals("loginuser", connection.zncBaseUser.get());
    assertEquals("loginclient", connection.zncClientId.get());
    assertEquals("netb", connection.zncNetwork.get());
    assertFalse(connection.zncDetected.get());
    assertFalse(connection.zncDetectedLogged.get());
    assertFalse(connection.zncPlaybackRequestedThisSession.get());
    assertFalse(connection.zncListNetworksRequestedThisSession.get());
    assertTrue(connection.sojuNetworksByNetId.isEmpty());
    assertTrue(connection.genericBouncerNetworksById.isEmpty());
    assertFalse(connection.sojuListNetworksRequestedThisSession.get());
    assertEquals("", connection.sojuBouncerNetId.get());
  }
}
