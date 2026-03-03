package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@ContextConfiguration(
    classes = BackendRoutingIrcClientServiceSpringIntegrationTest.TestConfig.class)
class BackendRoutingIrcClientServiceSpringIntegrationTest {

  private final ApplicationContext applicationContext;
  private final IrcClientService ircClientService;
  private final IrcBackendClientService ircBackend;
  private final IrcBackendClientService quasselBackend;
  private final BackendStreamHandle ircStream;
  private final BackendStreamHandle quasselStream;
  private final Map<String, IrcProperties.Server> testServersById;

  BackendRoutingIrcClientServiceSpringIntegrationTest(
      ApplicationContext applicationContext,
      @Qualifier("ircClientService") IrcClientService ircClientService,
      @Qualifier("ircBackendService") IrcBackendClientService ircBackend,
      @Qualifier("quasselBackendService") IrcBackendClientService quasselBackend,
      @Qualifier("ircBackendStream") BackendStreamHandle ircStream,
      @Qualifier("quasselBackendStream") BackendStreamHandle quasselStream,
      @Qualifier("testServersById") Map<String, IrcProperties.Server> testServersById) {
    this.applicationContext = applicationContext;
    this.ircClientService = ircClientService;
    this.ircBackend = ircBackend;
    this.quasselBackend = quasselBackend;
    this.ircStream = ircStream;
    this.quasselStream = quasselStream;
    this.testServersById = testServersById;
  }

  @BeforeEach
  void resetMocksAndServers() {
    clearInvocations(ircBackend, quasselBackend);
    testServersById.clear();
    testServersById.put("irc", server("irc", IrcProperties.Server.Backend.IRC));
    testServersById.put("quassel", server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE));
    testServersById.put("hybrid", server("hybrid", IrcProperties.Server.Backend.IRC));
  }

  @Test
  void wiringExposesRoutingBeanAndBothBackendBeans() {
    Object bean = applicationContext.getBean("ircClientService");

    assertTrue(bean instanceof BackendRoutingIrcClientService);
    assertSame(bean, ircClientService);
    assertEquals(2, applicationContext.getBeansOfType(IrcBackendClientService.class).size());
  }

  @Test
  void connectUsesConfiguredBackendAndDisconnectUsesActiveOwnership() {
    ircClientService.connect("quassel").blockingAwait();
    verify(quasselBackend).connect("quassel");
    verify(ircBackend, never()).connect("quassel");

    // Simulate live config mutation after connect; disconnect should still use active owner.
    testServersById.put("quassel", server("quassel", IrcProperties.Server.Backend.IRC));
    ircClientService.disconnect("quassel").blockingAwait();

    verify(quasselBackend).disconnect("quassel");
    verify(ircBackend, never()).disconnect("quassel");
  }

  @Test
  void backendOwnershipFollowsReplayEventsAndClearsAfterDisconnected() {
    var events = ircClientService.events().test();

    quasselStream
        .processor()
        .onNext(
            new ServerIrcEvent(
                "hybrid", new IrcEvent.Connected(Instant.now(), "irc.example.net", 6697, "nick")));
    events.awaitCount(1).assertValueCount(1);

    ircClientService.sendRaw("hybrid", "PING :one").blockingAwait();
    verify(quasselBackend).sendRaw("hybrid", "PING :one");
    verify(ircBackend, never()).sendRaw("hybrid", "PING :one");

    quasselStream
        .processor()
        .onNext(new ServerIrcEvent("hybrid", new IrcEvent.Disconnected(Instant.now(), "bye")));
    events.awaitCount(2).assertValueCount(2);

    ircClientService.sendRaw("hybrid", "PING :two").blockingAwait();
    verify(ircBackend).sendRaw("hybrid", "PING :two");
  }

  @Test
  void unknownServerFallsBackToIrcBackend() {
    ircClientService.sendRaw("missing", "PING :fallback").blockingAwait();

    verify(ircBackend).sendRaw("missing", "PING :fallback");
    verify(quasselBackend, never()).sendRaw("missing", "PING :fallback");
  }

  @TestConfiguration
  @Import(BackendRoutingIrcClientService.class)
  static class TestConfig {

    @Bean("testServersById")
    Map<String, IrcProperties.Server> testServersById() {
      return new ConcurrentHashMap<>();
    }

    @Bean
    ServerCatalog serverCatalog(
        @Qualifier("testServersById") Map<String, IrcProperties.Server> servers) {
      ServerCatalog catalog = mock(ServerCatalog.class);
      when(catalog.find(anyString()))
          .thenAnswer(
              invocation -> {
                String sid = normalizeServerId(invocation.getArgument(0));
                if (sid.isEmpty()) return Optional.empty();
                return Optional.ofNullable(servers.get(sid));
              });
      when(catalog.containsId(anyString()))
          .thenAnswer(
              invocation -> {
                String sid = normalizeServerId(invocation.getArgument(0));
                return !sid.isEmpty() && servers.containsKey(sid);
              });
      when(catalog.require(anyString()))
          .thenAnswer(
              invocation -> {
                String sid = normalizeServerId(invocation.getArgument(0));
                IrcProperties.Server server = sid.isEmpty() ? null : servers.get(sid);
                if (server == null) {
                  throw new IllegalArgumentException("Unknown server id: " + sid);
                }
                return server;
              });
      return catalog;
    }

    @Bean("ircBackendStream")
    BackendStreamHandle ircBackendStream() {
      return new BackendStreamHandle(PublishProcessor.create());
    }

    @Bean("quasselBackendStream")
    BackendStreamHandle quasselBackendStream() {
      return new BackendStreamHandle(PublishProcessor.create());
    }

    @Bean("ircBackendService")
    IrcBackendClientService ircBackendService(
        @Qualifier("ircBackendStream") BackendStreamHandle stream) {
      return createBackend(IrcProperties.Server.Backend.IRC, stream);
    }

    @Bean("quasselBackendService")
    IrcBackendClientService quasselBackendService(
        @Qualifier("quasselBackendStream") BackendStreamHandle stream) {
      return createBackend(IrcProperties.Server.Backend.QUASSEL_CORE, stream);
    }

    private static IrcBackendClientService createBackend(
        IrcProperties.Server.Backend backendType, BackendStreamHandle stream) {
      IrcBackendClientService backend = mock(IrcBackendClientService.class);
      when(backend.backend()).thenReturn(backendType);
      when(backend.events()).thenReturn(stream.processor().onBackpressureBuffer());

      when(backend.connect(anyString())).thenReturn(Completable.complete());
      when(backend.disconnect(anyString())).thenReturn(Completable.complete());
      when(backend.disconnect(anyString(), anyString())).thenReturn(Completable.complete());
      when(backend.sendRaw(anyString(), anyString())).thenReturn(Completable.complete());

      return backend;
    }

    private static String normalizeServerId(String serverId) {
      return java.util.Objects.toString(serverId, "").trim();
    }
  }

  record BackendStreamHandle(PublishProcessor<ServerIrcEvent> processor) {}

  private static IrcProperties.Server server(String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "tester",
        "tester",
        "Tester",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backend);
  }
}
