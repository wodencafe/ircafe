package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IrcPropertiesBindingTest {

  @Test
  void floodProtectionDefaultsPartialBindingAndBounds() {
    assertEquals(
        new IrcProperties.FloodProtection(true, 2000, 5000),
        IrcProperties.FloodProtection.defaults());
    runner.run(
        ctx ->
            assertEquals(
                new IrcProperties.FloodProtection(true, 2000, 5000),
                ctx.getBean(IrcProperties.class).client().floodProtection()));
    runner
        .withPropertyValues("irc.client.flood-protection.auto-join-delay-ms=0")
        .run(
            ctx ->
                assertEquals(
                    new IrcProperties.FloodProtection(2000, 0),
                    ctx.getBean(IrcProperties.class).client().floodProtection()));
    runner
        .withPropertyValues("irc.client.flood-protection.command-interval-ms=1500")
        .run(
            ctx ->
                assertEquals(
                    new IrcProperties.FloodProtection(1500, 5000),
                    ctx.getBean(IrcProperties.class).client().floodProtection()));
    assertEquals(
        new IrcProperties.FloodProtection(100, 120000),
        new IrcProperties.FloodProtection(1, Long.MAX_VALUE));
    assertEquals(
        new IrcProperties.FloodProtection(2000, 0), new IrcProperties.FloodProtection(-1, -1));
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(IrcPropertiesTestConfig.class);

  @Test
  void rateLimitingRequiresExplicitOptOutForExistingConfigurations() {
    runner
        .withPropertyValues(
            "irc.client.flood-protection.command-interval-ms=2200",
            "irc.client.flood-protection.auto-join-delay-ms=8000")
        .run(
            ctx ->
                assertEquals(
                    new IrcProperties.FloodProtection(true, 2200, 8000),
                    ctx.getBean(IrcProperties.class).client().floodProtection()));
    runner
        .withPropertyValues("irc.client.flood-protection.enabled=false")
        .run(
            ctx ->
                assertEquals(
                    new IrcProperties.FloodProtection(false, 2000, 5000),
                    ctx.getBean(IrcProperties.class).client().floodProtection()));
    assertTrue(IrcProperties.FloodProtection.defaults().enabled());
  }

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

          IrcProperties.Client.Translation translation = props.client().translation();
          assertFalse(translation.enabled());
          assertEquals(IrcProperties.Client.Translation.Mode.AUTO, translation.mode());
          assertEquals("", translation.backendId());
          assertEquals("", translation.endpoint());
          assertEquals("", translation.apiKey());
          assertEquals("auto", translation.sourceLanguage());
          assertEquals("", translation.targetLanguage());
          assertTrue(translation.translateUnknownMessages());
          assertTrue(translation.detectAllLanguages());
          assertTrue(translation.detectionLanguages().isEmpty());
          assertEquals(
              IrcProperties.Client.Translation.DisplayMode.BELOW_ORIGINAL,
              translation.displayMode());
          assertEquals(10_000, translation.requestTimeoutMs());
          assertEquals(4_000, translation.maxRequestChars());
          assertEquals(2, translation.maxConcurrentRequests());
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
            "irc.client.translation.enabled=true",
            "irc.client.translation.mode=manual",
            "irc.client.translation.backend=DeepL",
            "irc.client.translation.endpoint=https://api.deepl.com/v2/translate",
            "irc.client.translation.api-key=secret-key",
            "irc.client.translation.source-language=EN",
            "irc.client.translation.target-language=ES",
            "irc.client.translation.translate-unknown-messages=false",
            "irc.client.translation.detect-all-languages=false",
            "irc.client.translation.detection-languages[0]=EN",
            "irc.client.translation.detection-languages[1]=es",
            "irc.client.translation.display-mode=below-original",
            "irc.client.translation.request-timeout-ms=12345",
            "irc.client.translation.max-request-chars=777",
            "irc.client.translation.max-concurrent-requests=3",
            "irc.servers[0].id=libera",
            "irc.servers[0].host=irc.libera.chat",
            "irc.servers[0].port=6697",
            "irc.servers[0].tls=true",
            "irc.servers[0].nick=ircafe-user",
            "irc.servers[0].login=ircafe",
            "irc.servers[0].real-name=IRCafe User",
            "irc.servers[0].nickserv.enabled=true",
            "irc.servers[0].nickserv.password=nickserv-secret",
            "irc.servers[0].nickserv.service=AuthServ",
            "irc.servers[0].nickserv.delay-join-until-identified=false")
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

              IrcProperties.Client.Translation translation = props.client().translation();
              assertTrue(translation.enabled());
              assertEquals(IrcProperties.Client.Translation.Mode.MANUAL, translation.mode());
              assertEquals("deepl", translation.backendId());
              assertEquals("https://api.deepl.com/v2/translate", translation.endpoint());
              assertEquals("secret-key", translation.apiKey());
              assertEquals("en", translation.sourceLanguage());
              assertEquals("es", translation.targetLanguage());
              assertFalse(translation.translateUnknownMessages());
              assertFalse(translation.detectAllLanguages());
              assertEquals(List.of("en", "es"), translation.detectionLanguages());
              assertEquals(
                  IrcProperties.Client.Translation.DisplayMode.BELOW_ORIGINAL,
                  translation.displayMode());
              assertEquals(12345, translation.requestTimeoutMs());
              assertEquals(777, translation.maxRequestChars());
              assertEquals(3, translation.maxConcurrentRequests());

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
              assertTrue(server.nickserv().enabled());
              assertEquals("nickserv-secret", server.nickserv().password());
              assertEquals("AuthServ", server.nickserv().service());
              assertFalse(server.nickserv().delayJoinUntilIdentified());
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
              assertTrue(
                  root.getMessage().contains("irc.client.proxy.enabled=true but host is blank"));
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
            "irc.client.proxy.read-timeout-ms=-9",
            "irc.client.translation.request-timeout-ms=0",
            "irc.client.translation.max-request-chars=-9",
            "irc.client.translation.max-concurrent-requests=99")
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

              IrcProperties.Client.Translation translation = props.client().translation();
              assertEquals(10_000, translation.requestTimeoutMs());
              assertEquals(4_000, translation.maxRequestChars());
              assertEquals(16, translation.maxConcurrentRequests());
            });
  }

  @Test
  void enablingTranslationWithoutBackendFailsFast() {
    runner
        .withPropertyValues(
            "irc.client.translation.enabled=true", "irc.client.translation.target-language=es")
        .run(
            ctx -> {
              Throwable startupFailure = ctx.getStartupFailure();
              assertNotNull(startupFailure);
              Throwable root = rootCause(startupFailure);
              assertNotNull(root.getMessage());
              assertTrue(
                  root.getMessage()
                      .contains("irc.client.translation.enabled=true but backend is blank"));
            });
  }

  @Test
  void enablingTranslationWithoutTargetLanguageFailsFast() {
    runner
        .withPropertyValues(
            "irc.client.translation.enabled=true", "irc.client.translation.backend=test")
        .run(
            ctx -> {
              Throwable startupFailure = ctx.getStartupFailure();
              assertNotNull(startupFailure);
              Throwable root = rootCause(startupFailure);
              assertNotNull(root.getMessage());
              assertTrue(
                  root.getMessage()
                      .contains(
                          "irc.client.translation.enabled=true but target-language is blank"));
            });
  }

  @Test
  void enablingDeepLTranslationWithoutApiKeyFailsFast() {
    runner
        .withPropertyValues(
            "irc.client.translation.enabled=true",
            "irc.client.translation.backend=deepl",
            "irc.client.translation.target-language=es")
        .run(
            ctx -> {
              Throwable startupFailure = ctx.getStartupFailure();
              assertNotNull(startupFailure);
              Throwable root = rootCause(startupFailure);
              assertNotNull(root.getMessage());
              assertTrue(
                  root.getMessage()
                      .contains(
                          "irc.client.translation.enabled=true with backend=deepl but api-key is blank"));
            });
  }

  @Test
  void googleWebTranslationDoesNotRequireApiKeyAndUsesDefaultEndpoint() {
    runner
        .withPropertyValues(
            "irc.client.translation.enabled=true",
            "irc.client.translation.backend=google-web",
            "irc.client.translation.target-language=es")
        .run(
            ctx -> {
              assertNull(ctx.getStartupFailure());
              IrcProperties.Client.Translation translation =
                  ctx.getBean(IrcProperties.class).client().translation();
              assertTrue(translation.enabled());
              assertEquals("google-web", translation.backendId());
              assertEquals(
                  "https://translate.googleapis.com/translate_a/single", translation.endpoint());
              assertEquals("", translation.apiKey());
              assertEquals("es", translation.targetLanguage());
            });
  }

  @Test
  void serverNickservDefaultsApplyWhenSectionIsOmitted() {
    runner
        .withPropertyValues(
            "irc.servers[0].id=libera",
            "irc.servers[0].host=irc.libera.chat",
            "irc.servers[0].port=6697",
            "irc.servers[0].tls=true",
            "irc.servers[0].nick=ircafe-user")
        .run(
            ctx -> {
              IrcProperties.Server server = ctx.getBean(IrcProperties.class).servers().getFirst();
              assertFalse(server.nickserv().enabled());
              assertEquals("", server.nickserv().password());
              assertEquals("NickServ", server.nickserv().service());
              assertTrue(server.nickserv().delayJoinUntilIdentified());
            });
  }

  @Test
  void serverCertificateOptOutIsExplicitAndIndependentPerServer() {
    runner
        .withPropertyValues(
            "irc.servers[0].id=znc",
            "irc.servers[0].host=znc.example.net",
            "irc.servers[0].tls=true",
            "irc.servers[0].trust-all-certificates=true",
            "irc.servers[1].id=libera",
            "irc.servers[1].host=irc.libera.chat",
            "irc.servers[1].tls=true")
        .run(
            ctx -> {
              IrcProperties props = ctx.getBean(IrcProperties.class);
              assertTrue(props.servers().getFirst().trustAllCertificates());
              assertFalse(props.servers().get(1).trustAllCertificates());
              assertFalse(props.client().tls().trustAllCertificates());
            });
  }

  @Test
  void serverCopiesPreserveCertificateOptOut() {
    IrcProperties.Server server =
        IrcPropertiesTestFixtures.serverBuilder("znc").trustAllCertificates(true).build();
    assertTrue(server.withAutoJoin(List.of("#ircafe")).trustAllCertificates());
    assertTrue(server.withTransport(7000, true).trustAllCertificates());
    assertFalse(IrcPropertiesTestFixtures.server("libera").trustAllCertificates());
  }

  @Test
  void customBackendIdBindsFromExistingBackendProperty() {
    runner
        .withPropertyValues(
            "irc.servers[0].id=plugin",
            "irc.servers[0].host=plugin.example.net",
            "irc.servers[0].port=9000",
            "irc.servers[0].backend=custom-plugin")
        .run(
            ctx -> {
              IrcProperties.Server server = ctx.getBean(IrcProperties.class).servers().getFirst();
              assertEquals("custom-plugin", server.backendId());
              assertNull(server.backend());
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
