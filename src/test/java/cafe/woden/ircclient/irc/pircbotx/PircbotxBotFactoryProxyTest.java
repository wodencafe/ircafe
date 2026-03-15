package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.net.DeferredConnectSocksSocketFactory;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.hooks.ListenerAdapter;

class PircbotxBotFactoryProxyTest {

  @Test
  void buildUsesProxyPlanTimeoutsAndDeferredSocksFactory() {
    IrcProperties.Proxy proxyCfg =
        new IrcProperties.Proxy(true, "127.0.0.1", 9050, "", "", true, 11_111, 22_222);
    ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
    when(proxyResolver.planForServer("libera")).thenReturn(ProxyPlan.from(proxyCfg));

    PircbotxBotFactory factory =
        new PircbotxBotFactory(proxyResolver, new SojuProperties(Map.of(), null), null);

    Configuration cfg =
        factory
            .build(server("libera", true), "IRCafe test", new ListenerAdapter() {})
            .getConfiguration();

    assertEquals(11_111, cfg.getSocketConnectTimeout());
    assertEquals(22_222, cfg.getSocketTimeout());
    assertInstanceOf(DeferredConnectSocksSocketFactory.class, cfg.getSocketFactory());
  }

  private static IrcProperties.Server server(String id, boolean tls) {
    return new IrcProperties.Server(
        id,
        "irc.libera.chat",
        6697,
        tls,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null),
        new IrcProperties.Server.Nickserv(false, "", "", false),
        List.of("#ircafe"),
        List.of(),
        null);
  }
}
