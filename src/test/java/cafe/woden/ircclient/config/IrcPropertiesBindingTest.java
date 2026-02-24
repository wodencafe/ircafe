package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IrcPropertiesBindingTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(IrcPropertiesTestConfig.class);

  @Test
  void defaultsAreAppliedWhenNoIrcPropertiesProvided() {
    runner.run(
        ctx -> {
          IrcProperties props = ctx.getBean(IrcProperties.class);
          assertNotNull(props.client());
          assertFalse(props.client().version().isBlank());
          assertTrue(props.servers().isEmpty());

          IrcProperties.Reconnect reconnect = props.client().reconnect();
          assertTrue(reconnect.enabled());
          assertEquals(1_000, reconnect.initialDelayMs());
          assertEquals(120_000, reconnect.maxDelayMs());
          assertEquals(2.0, reconnect.multiplier());
          assertEquals(0.20, reconnect.jitterPct());
          assertEquals(0, reconnect.maxAttempts());

          IrcProperties.Heartbeat heartbeat = props.client().heartbeat();
          assertTrue(heartbeat.enabled());
          assertEquals(15_000, heartbeat.checkPeriodMs());
          assertEquals(360_000, heartbeat.timeoutMs());

          IrcProperties.Proxy proxy = props.client().proxy();
          assertFalse(proxy.enabled());
          assertEquals("", proxy.host());
          assertEquals(0, proxy.port());
          assertFalse(proxy.hasAuth());
          assertEquals(20_000, proxy.connectTimeoutMs());
          assertEquals(30_000, proxy.readTimeoutMs());

          assertFalse(props.client().tls().trustAllCertificates());
        });
  }

  @Test
  void explicitValuesBindToNestedClientAndServerSections() {
    runner
        .withPropertyValues(
            "irc.client.reconnect.enabled=false",
            "irc.client.reconnect.initial-delay-ms=2500",
            "irc.client.reconnect.max-delay-ms=7500",
            "irc.client.reconnect.multiplier=3.0",
            "irc.client.reconnect.jitter-pct=0.33",
            "irc.client.reconnect.max-attempts=5",
            "irc.client.heartbeat.enabled=false",
            "irc.client.heartbeat.check-period-ms=7000",
            "irc.client.heartbeat.timeout-ms=8000",
            "irc.client.proxy.enabled=true",
            "irc.client.proxy.host=127.0.0.1",
            "irc.client.proxy.port=1080",
            "irc.client.proxy.username=alice",
            "irc.client.proxy.password=secret",
            "irc.client.proxy.remote-dns=false",
            "irc.client.proxy.connect-timeout-ms=1111",
            "irc.client.proxy.read-timeout-ms=2222",
            "irc.client.tls.trust-all-certificates=true",
            "irc.servers[0].id=libera",
            "irc.servers[0].host=irc.libera.chat",
            "irc.servers[0].port=6697",
            "irc.servers[0].tls=true",
            "irc.servers[0].nick=ircafe-user",
            "irc.servers[0].login=ircafe",
            "irc.servers[0].real-name=IRCafe User")
        .run(
            ctx -> {
              IrcProperties props = ctx.getBean(IrcProperties.class);

              IrcProperties.Reconnect reconnect = props.client().reconnect();
              assertFalse(reconnect.enabled());
              assertEquals(2500, reconnect.initialDelayMs());
              assertEquals(7500, reconnect.maxDelayMs());
              assertEquals(3.0, reconnect.multiplier());
              assertEquals(0.33, reconnect.jitterPct());
              assertEquals(5, reconnect.maxAttempts());

              IrcProperties.Heartbeat heartbeat = props.client().heartbeat();
              assertFalse(heartbeat.enabled());
              assertEquals(7000, heartbeat.checkPeriodMs());
              assertEquals(8000, heartbeat.timeoutMs());

              IrcProperties.Proxy proxy = props.client().proxy();
              assertTrue(proxy.enabled());
              assertEquals("127.0.0.1", proxy.host());
              assertEquals(1080, proxy.port());
              assertEquals("alice", proxy.username());
              assertEquals("secret", proxy.password());
              assertFalse(proxy.remoteDns());
              assertTrue(proxy.hasAuth());
              assertEquals(1111, proxy.connectTimeoutMs());
              assertEquals(2222, proxy.readTimeoutMs());

              assertTrue(props.client().tls().trustAllCertificates());

              List<IrcProperties.Server> servers = props.servers();
              assertEquals(1, servers.size());
              IrcProperties.Server server = servers.getFirst();
              assertEquals("libera", server.id());
              assertEquals("irc.libera.chat", server.host());
              assertEquals(6697, server.port());
              assertTrue(server.tls());
              assertEquals("ircafe-user", server.nick());
              assertEquals("ircafe", server.login());
              assertEquals("IRCafe User", server.realName());
            });
  }

  @Test
  void enablingProxyWithoutHostFailsFast() {
    runner
        .withPropertyValues("irc.client.proxy.enabled=true", "irc.client.proxy.port=1080")
        .run(
            ctx -> {
              Throwable startupFailure = ctx.getStartupFailure();
              assertNotNull(startupFailure);
              Throwable root = rootCause(startupFailure);
              assertNotNull(root.getMessage());
              assertTrue(root.getMessage().contains("irc.client.proxy.enabled=true but host is blank"));
            });
  }

  @Test
  void outOfRangeValuesAreClampedToSafeDefaults() {
    runner
        .withPropertyValues(
            "irc.client.reconnect.initial-delay-ms=0",
            "irc.client.reconnect.max-delay-ms=5",
            "irc.client.reconnect.multiplier=1.0",
            "irc.client.reconnect.jitter-pct=9.0",
            "irc.client.reconnect.max-attempts=-7",
            "irc.client.heartbeat.check-period-ms=0",
            "irc.client.heartbeat.timeout-ms=1",
            "irc.client.proxy.connect-timeout-ms=0",
            "irc.client.proxy.read-timeout-ms=-9")
        .run(
            ctx -> {
              IrcProperties props = ctx.getBean(IrcProperties.class);

              IrcProperties.Reconnect reconnect = props.client().reconnect();
              assertEquals(1_000, reconnect.initialDelayMs());
              assertEquals(1_000, reconnect.maxDelayMs());
              assertEquals(2.0, reconnect.multiplier());
              assertEquals(0.75, reconnect.jitterPct());
              assertEquals(0, reconnect.maxAttempts());

              IrcProperties.Heartbeat heartbeat = props.client().heartbeat();
              assertEquals(15_000, heartbeat.checkPeriodMs());
              assertEquals(30_000, heartbeat.timeoutMs());

              IrcProperties.Proxy proxy = props.client().proxy();
              assertEquals(20_000, proxy.connectTimeoutMs());
              assertEquals(30_000, proxy.readTimeoutMs());
            });
  }

  private static Throwable rootCause(Throwable t) {
    Throwable current = t;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(IrcProperties.class)
  static class IrcPropertiesTestConfig {}
}
