package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.hooks.ListenerAdapter;

class PircbotxBotFactoryAuthTest {

  @Test
  void nickservSettingsAreAppliedToPircBotxConfiguration() {
    ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
    when(proxyResolver.planForServer("libera")).thenReturn(ProxyPlan.direct());

    PircbotxBotFactory factory =
        new PircbotxBotFactory(proxyResolver, new SojuProperties(Map.of(), null), null);

    IrcProperties.Server server =
        new IrcProperties.Server(
            "libera",
            "irc.libera.chat",
            6697,
            true,
            "",
            "ircafe",
            "ircafe",
            "IRCafe User",
            new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null),
            new IrcProperties.Server.Nickserv(true, "ns-secret", "AuthServ", true),
            List.of("#ircafe"),
            List.of(),
            null);

    Configuration cfg =
        factory.build(server, "IRCafe test", new ListenerAdapter() {}).getConfiguration();

    assertEquals("ns-secret", cfg.getNickservPassword());
    assertEquals("AuthServ", cfg.getNickservNick());
    assertTrue(cfg.isNickservDelayJoin());
  }

  @Test
  void enablingSaslAndNickservTogetherFailsFast() {
    ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
    when(proxyResolver.planForServer("libera")).thenReturn(ProxyPlan.direct());

    PircbotxBotFactory factory =
        new PircbotxBotFactory(proxyResolver, new SojuProperties(Map.of(), null), null);

    IrcProperties.Server server =
        new IrcProperties.Server(
            "libera",
            "irc.libera.chat",
            6697,
            true,
            "",
            "ircafe",
            "ircafe",
            "IRCafe User",
            new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null),
            new IrcProperties.Server.Nickserv(true, "ns-secret", "NickServ", true),
            List.of(),
            List.of(),
            null);

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> factory.build(server, "IRCafe test", new ListenerAdapter() {}));
    assertTrue(ex.getMessage().contains("cannot both be enabled"));
  }
}
