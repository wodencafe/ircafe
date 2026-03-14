package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Containerized Quassel Core integration coverage.
 *
 * <p>Disabled by default. Enable explicitly with:
 *
 * <pre>
 * ./gradlew integrationTest --tests '*QuasselCoreContainerIntegrationTest' \
 *   -Dquassel.it.container.enabled=true
 * </pre>
 *
 * <p>Optional properties/env vars:
 *
 * <ul>
 *   <li>{@code quassel.it.container.image} / {@code QUASSEL_IT_CONTAINER_IMAGE}
 *   <li>{@code quassel.it.container.login} / {@code QUASSEL_IT_CONTAINER_LOGIN}
 *   <li>{@code quassel.it.container.password} / {@code QUASSEL_IT_CONTAINER_PASSWORD}
 *   <li>{@code quassel.it.container.startup-timeout-seconds}
 * </ul>
 */
class QuasselCoreContainerIntegrationTest {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration SETUP_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration LAG_TIMEOUT = Duration.ofSeconds(30);
  private static final long POLL_INTERVAL_MS = 50L;

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void containerizedQuasselCoreSupportsSetupConnectLagAndReconnect() throws Exception {
    ContainerConfig cfg = ContainerConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container Quassel integration test disabled. Set -Dquassel.it.container.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName image = DockerImageName.parse(cfg.image());
    try (GenericContainer<?> core =
        new GenericContainer<>(image)
            .withExposedPorts(cfg.containerPort())
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      core.start();

      RuntimeCoreConfig runtimeCfg =
          cfg.toRuntimeConfig(core.getHost(), core.getMappedPort(cfg.containerPort()));
      QuasselCoreIrcClientService service = newService(runtimeCfg);
      TestSubscriber<ServerIrcEvent> events = service.events().test();

      try {
        ensureConnectedWithSetupIfNeeded(service, events, runtimeCfg);
        // Fresh cores may have no upstream networks configured yet; currentNick can be empty.
        assertEquals("", service.backendAvailabilityReason(runtimeCfg.serverId()));

        service.requestLagProbe(runtimeCfg.serverId()).blockingAwait();
        OptionalLong lagMs = awaitLagSample(service, runtimeCfg.serverId(), LAG_TIMEOUT);
        if (lagMs.isPresent()) {
          assertTrue(lagMs.orElseThrow() >= 0L);
        }

        int disconnectedCount =
            countEvents(events, runtimeCfg.serverId(), IrcEvent.Disconnected.class);
        service
            .disconnect(runtimeCfg.serverId(), "container integration disconnect")
            .blockingAwait();
        IrcEvent.Disconnected disconnected =
            awaitNextEvent(
                events,
                runtimeCfg.serverId(),
                IrcEvent.Disconnected.class,
                disconnectedCount,
                CONNECT_TIMEOUT);
        assertEquals("container integration disconnect", disconnected.reason());

        int readyCount = countEvents(events, runtimeCfg.serverId(), IrcEvent.ConnectionReady.class);
        service.connect(runtimeCfg.serverId()).blockingAwait();
        awaitNextEvent(
            events,
            runtimeCfg.serverId(),
            IrcEvent.ConnectionReady.class,
            readyCount,
            CONNECT_TIMEOUT);
      } finally {
        try {
          service
              .disconnect(runtimeCfg.serverId(), "container integration shutdown")
              .blockingAwait();
        } catch (Exception ignored) {
        }
        try {
          events.cancel();
        } catch (Exception ignored) {
        }
        try {
          service.shutdownNow();
        } catch (Exception ignored) {
        }
      }
    }
  }

  @Test
  void containerizedQuasselCoreReconnectReemitsSyncReadyAndAllowsLagProbe() throws Exception {
    ContainerConfig cfg = ContainerConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container Quassel integration test disabled. Set -Dquassel.it.container.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName image = DockerImageName.parse(cfg.image());
    try (GenericContainer<?> core =
        new GenericContainer<>(image)
            .withExposedPorts(cfg.containerPort())
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      core.start();

      RuntimeCoreConfig runtimeCfg =
          cfg.toRuntimeConfig(core.getHost(), core.getMappedPort(cfg.containerPort()));
      QuasselCoreIrcClientService service = newService(runtimeCfg);
      TestSubscriber<ServerIrcEvent> events = service.events().test();

      try {
        String sid = runtimeCfg.serverId();
        ensureConnectedWithSetupIfNeeded(service, events, runtimeCfg);

        int disconnectedCount = countEvents(events, sid, IrcEvent.Disconnected.class);
        service.disconnect(sid, "container reconnect test disconnect").blockingAwait();
        awaitNextEvent(
            events, sid, IrcEvent.Disconnected.class, disconnectedCount, CONNECT_TIMEOUT);

        int readyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
        int syncReadyCount = countSyncReadyPhaseEvents(events, sid);
        service.connect(sid).blockingAwait();
        awaitNextEvent(events, sid, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);
        awaitNextSyncReadyPhaseEvent(events, sid, syncReadyCount, CONNECT_TIMEOUT);
        assertEquals("", service.backendAvailabilityReason(sid));

        service.requestLagProbe(sid).blockingAwait();
        OptionalLong lagMs = awaitLagSample(service, sid, LAG_TIMEOUT);
        if (lagMs.isPresent()) {
          assertTrue(lagMs.orElseThrow() >= 0L);
        }
      } finally {
        try {
          service
              .disconnect(runtimeCfg.serverId(), "container reconnect test shutdown")
              .blockingAwait();
        } catch (Exception ignored) {
        }
        try {
          events.cancel();
        } catch (Exception ignored) {
        }
        try {
          service.shutdownNow();
        } catch (Exception ignored) {
        }
      }
    }
  }

  @Test
  void containerizedQuasselCoreExposesExplicitSetupRequiredFlow() throws Exception {
    ContainerConfig cfg = ContainerConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container Quassel integration test disabled. Set -Dquassel.it.container.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName image = DockerImageName.parse(cfg.image());
    try (GenericContainer<?> core =
        new GenericContainer<>(image)
            .withExposedPorts(cfg.containerPort())
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      core.start();

      RuntimeCoreConfig runtimeCfg =
          cfg.toRuntimeConfig(core.getHost(), core.getMappedPort(cfg.containerPort()));
      QuasselCoreIrcClientService service = newService(runtimeCfg);
      TestSubscriber<ServerIrcEvent> events = service.events().test();

      try {
        String sid = runtimeCfg.serverId();
        int readyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
        service.connect(sid).blockingAwait();

        ConnectOutcome firstOutcome =
            awaitConnectOutcome(service, events, sid, readyCount, SETUP_TIMEOUT);
        assertEquals(
            ConnectOutcome.SETUP_REQUIRED,
            firstOutcome,
            "Fresh container core should require initial setup");
        assertTrue(service.isQuasselCoreSetupPending(sid));

        QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
            service
                .quasselCoreSetupPrompt(sid)
                .orElseThrow(
                    () -> new IllegalStateException("setup pending but no setup prompt found"));
        assertFalse(prompt.storageBackends().isEmpty(), "storage backend options should exist");
        assertFalse(prompt.authenticators().isEmpty(), "authenticator options should exist");

        QuasselCoreControlPort.QuasselCoreSetupRequest setup =
            new QuasselCoreControlPort.QuasselCoreSetupRequest(
                runtimeCfg.login(),
                runtimeCfg.password(),
                firstNonBlank(prompt.storageBackends(), "SQLite"),
                firstNonBlank(prompt.authenticators(), "Database"),
                Map.of("DatabaseName", "quassel-storage"),
                Map.of());
        service.submitQuasselCoreSetup(sid, setup).blockingAwait();
        assertFalse(service.isQuasselCoreSetupPending(sid));
        assertTrue(
            service.backendAvailabilityReason(sid).toLowerCase(Locale.ROOT).contains("setup"),
            "availability reason should mention setup completion before reconnect");

        int reconnectReadyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
        service.connect(sid).blockingAwait();
        awaitNextEvent(
            events, sid, IrcEvent.ConnectionReady.class, reconnectReadyCount, CONNECT_TIMEOUT);
        assertEquals("", service.backendAvailabilityReason(sid));
      } finally {
        try {
          service
              .disconnect(runtimeCfg.serverId(), "container setup-required test shutdown")
              .blockingAwait();
        } catch (Exception ignored) {
        }
        try {
          events.cancel();
        } catch (Exception ignored) {
        }
        try {
          service.shutdownNow();
        } catch (Exception ignored) {
        }
      }
    }
  }

  @Test
  void containerizedQuasselCoreRejectsBadCredentialsAndTlsMismatch() throws Exception {
    ContainerConfig cfg = ContainerConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container Quassel integration test disabled. Set -Dquassel.it.container.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName image = DockerImageName.parse(cfg.image());
    try (GenericContainer<?> core =
        new GenericContainer<>(image)
            .withExposedPorts(cfg.containerPort())
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      core.start();

      RuntimeCoreConfig runtimeCfg =
          cfg.toRuntimeConfig(core.getHost(), core.getMappedPort(cfg.containerPort()));
      bootstrapSetupIfNeeded(runtimeCfg);

      RuntimeCoreConfig badCredsCfg = runtimeCfg.withPassword(runtimeCfg.password() + "-wrong");
      QuasselCoreIrcClientService badCredsService = newService(badCredsCfg);
      TestSubscriber<ServerIrcEvent> badCredsEvents = badCredsService.events().test();
      try {
        String sid = badCredsCfg.serverId();
        badCredsService.connect(sid).blockingAwait();
        IrcEvent.Disconnected disconnected =
            awaitNextEvent(badCredsEvents, sid, IrcEvent.Disconnected.class, 0, CONNECT_TIMEOUT);
        String combined =
            (Objects.toString(disconnected.reason(), "")
                    + " "
                    + badCredsService.backendAvailabilityReason(sid))
                .toLowerCase(Locale.ROOT);
        assertTrue(
            combined.contains("connect failed")
                || combined.contains("login")
                || combined.contains("password"),
            "expected authentication/connect failure, got: " + combined);
      } finally {
        try {
          badCredsEvents.cancel();
        } catch (Exception ignored) {
        }
        try {
          badCredsService.shutdownNow();
        } catch (Exception ignored) {
        }
      }

      RuntimeCoreConfig tlsMismatchCfg = runtimeCfg.withTls(true);
      QuasselCoreIrcClientService tlsMismatchService = newService(tlsMismatchCfg);
      TestSubscriber<ServerIrcEvent> tlsMismatchEvents = tlsMismatchService.events().test();
      try {
        String sid = tlsMismatchCfg.serverId();
        tlsMismatchService.connect(sid).blockingAwait();
        IrcEvent.Disconnected disconnected =
            awaitNextEvent(tlsMismatchEvents, sid, IrcEvent.Disconnected.class, 0, CONNECT_TIMEOUT);
        String combined =
            (Objects.toString(disconnected.reason(), "")
                    + " "
                    + tlsMismatchService.backendAvailabilityReason(sid))
                .toLowerCase(Locale.ROOT);
        assertTrue(
            combined.contains("connect failed")
                || combined.contains("tls")
                || combined.contains("ssl")
                || combined.contains("handshake"),
            "expected TLS/protocol mismatch failure, got: " + combined);
      } finally {
        try {
          tlsMismatchEvents.cancel();
        } catch (Exception ignored) {
        }
        try {
          tlsMismatchService.shutdownNow();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private static void ensureConnectedWithSetupIfNeeded(
      QuasselCoreIrcClientService service,
      TestSubscriber<ServerIrcEvent> events,
      RuntimeCoreConfig runtimeCfg)
      throws Exception {
    String sid = runtimeCfg.serverId();
    int readyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
    service.connect(sid).blockingAwait();

    ConnectOutcome firstOutcome =
        awaitConnectOutcome(service, events, sid, readyCount, SETUP_TIMEOUT);
    if (firstOutcome == ConnectOutcome.READY) {
      return;
    }

    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        service
            .quasselCoreSetupPrompt(sid)
            .orElseThrow(
                () -> new IllegalStateException("setup pending but no setup prompt found"));
    QuasselCoreControlPort.QuasselCoreSetupRequest setup =
        new QuasselCoreControlPort.QuasselCoreSetupRequest(
            runtimeCfg.login(),
            runtimeCfg.password(),
            firstNonBlank(prompt.storageBackends(), "SQLite"),
            firstNonBlank(prompt.authenticators(), "Database"),
            Map.of("DatabaseName", "quassel-storage"),
            Map.of());
    service.submitQuasselCoreSetup(sid, setup).blockingAwait();

    int reconnectReadyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
    service.connect(sid).blockingAwait();
    awaitNextEvent(
        events, sid, IrcEvent.ConnectionReady.class, reconnectReadyCount, CONNECT_TIMEOUT);
  }

  private static ConnectOutcome awaitConnectOutcome(
      QuasselCoreIrcClientService service,
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      int alreadySeenReadyCount,
      Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      if (countEvents(events, serverId, IrcEvent.ConnectionReady.class) > alreadySeenReadyCount) {
        return ConnectOutcome.READY;
      }
      if (service.isQuasselCoreSetupPending(serverId)) {
        return ConnectOutcome.SETUP_REQUIRED;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail("Timed out waiting for Quassel connect outcome (ready or setup-required)");
    throw new IllegalStateException("unreachable");
  }

  private static OptionalLong awaitLagSample(
      QuasselCoreIrcClientService service, String serverId, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      OptionalLong lag = service.lastMeasuredLagMs(serverId);
      if (lag.isPresent()) return lag;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    return OptionalLong.empty();
  }

  private static String firstNonBlank(List<String> values, String fallback) {
    if (values != null) {
      for (String value : values) {
        String candidate = Objects.toString(value, "").trim();
        if (!candidate.isEmpty()) return candidate;
      }
    }
    return Objects.toString(fallback, "").trim();
  }

  private static int countSyncReadyPhaseEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId) {
    int count = 0;
    List<ServerIrcEvent> all = new ArrayList<>(events.values());
    for (ServerIrcEvent serverEvent : all) {
      if (serverEvent == null || !Objects.equals(serverId, serverEvent.serverId())) continue;
      if (!(serverEvent.event() instanceof IrcEvent.ConnectionFeaturesUpdated updated)) continue;
      if (!Objects.toString(updated.source(), "").startsWith("quassel-phase=sync-ready")) continue;
      count++;
    }
    return count;
  }

  private static void awaitNextSyncReadyPhaseEvent(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      int alreadySeenCount,
      Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      if (countSyncReadyPhaseEvents(events, serverId) > alreadySeenCount) {
        return;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for post-reconnect sync-ready phase event on server '"
            + serverId
            + "'; recent events: "
            + summarizeRecentEvents(events, serverId, 12));
  }

  private static <T extends IrcEvent> int countEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, Class<T> eventType) {
    return matchingEvents(events, serverId, eventType).size();
  }

  private static <T extends IrcEvent> T awaitNextEvent(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      Class<T> eventType,
      int alreadySeenCount,
      Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<T> matches = matchingEvents(events, serverId, eventType);
      if (matches.size() > alreadySeenCount) {
        return matches.get(alreadySeenCount);
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for "
            + eventType.getSimpleName()
            + " on server '"
            + serverId
            + "'; recent events: "
            + summarizeRecentEvents(events, serverId, 12));
    throw new IllegalStateException("unreachable");
  }

  private static <T extends IrcEvent> List<T> matchingEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, Class<T> eventType) {
    List<ServerIrcEvent> all = new ArrayList<>(events.values());
    ArrayList<T> out = new ArrayList<>();
    for (ServerIrcEvent serverEvent : all) {
      if (serverEvent == null || !Objects.equals(serverId, serverEvent.serverId())) continue;
      if (!eventType.isInstance(serverEvent.event())) continue;
      out.add(eventType.cast(serverEvent.event()));
    }
    return out;
  }

  private static String summarizeRecentEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, int limit) {
    if (limit <= 0) return "";
    List<ServerIrcEvent> all = new ArrayList<>(events.values());
    ArrayList<String> lines = new ArrayList<>();
    for (ServerIrcEvent serverEvent : all) {
      if (serverEvent == null || !Objects.equals(serverId, serverEvent.serverId())) continue;
      IrcEvent event = serverEvent.event();
      if (event == null) continue;
      String detail =
          switch (event) {
            case IrcEvent.Error err -> "Error(" + Objects.toString(err.message(), "") + ")";
            case IrcEvent.Disconnected disc ->
                "Disconnected(" + Objects.toString(disc.reason(), "") + ")";
            case IrcEvent.ConnectionFeaturesUpdated updated ->
                "Features(" + Objects.toString(updated.source(), "") + ")";
            case IrcEvent.Connected connected ->
                "Connected(" + Objects.toString(connected.nick(), "") + ")";
            case IrcEvent.Connecting ignored -> "Connecting";
            case IrcEvent.ConnectionReady ignored -> "ConnectionReady";
            case IrcEvent.Reconnecting reconnecting ->
                "Reconnecting(attempt="
                    + reconnecting.attempt()
                    + ",delayMs="
                    + reconnecting.delayMs()
                    + ")";
            default -> event.getClass().getSimpleName();
          };
      lines.add(detail);
    }
    if (lines.isEmpty()) return "<none>";
    int start = Math.max(0, lines.size() - limit);
    return String.join(" | ", lines.subList(start, lines.size()));
  }

  private static void bootstrapSetupIfNeeded(RuntimeCoreConfig runtimeCfg) throws Exception {
    QuasselCoreIrcClientService service = newService(runtimeCfg);
    TestSubscriber<ServerIrcEvent> events = service.events().test();
    try {
      ensureConnectedWithSetupIfNeeded(service, events, runtimeCfg);
      service.disconnect(runtimeCfg.serverId(), "container bootstrap disconnect").blockingAwait();
    } finally {
      try {
        events.cancel();
      } catch (Exception ignored) {
      }
      try {
        service.shutdownNow();
      } catch (Exception ignored) {
      }
    }
  }

  private static QuasselCoreIrcClientService newService(RuntimeCoreConfig cfg) {
    IrcProperties.Server server = cfg.toServer();

    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    when(serverCatalog.require(cfg.serverId())).thenReturn(server);
    when(serverCatalog.find(cfg.serverId())).thenReturn(Optional.of(server));

    ServerProxyResolver proxyResolver = new ServerProxyResolver(serverCatalog);
    QuasselCoreSocketConnector socketConnector = new QuasselCoreSocketConnector(proxyResolver);
    QuasselCoreProtocolProbe protocolProbe = new QuasselCoreProtocolProbe();
    QuasselCoreDatastreamCodec datastreamCodec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake authHandshake = new QuasselCoreAuthHandshake(datastreamCodec);

    IrcProperties props =
        new IrcProperties(
            new IrcProperties.Client(
                "IRCafe IT",
                new IrcProperties.Reconnect(true, 250, 1_000, 1.5, 0, 8),
                null,
                null,
                null),
            List.of(server));

    return new QuasselCoreIrcClientService(
        serverCatalog, socketConnector, protocolProbe, authHandshake, datastreamCodec, props);
  }

  private enum ConnectOutcome {
    READY,
    SETUP_REQUIRED
  }

  private record RuntimeCoreConfig(
      String serverId,
      String host,
      int port,
      boolean tls,
      String login,
      String password,
      String nick,
      String realName) {
    IrcProperties.Server toServer() {
      return new IrcProperties.Server(
          serverId,
          host,
          port,
          tls,
          password,
          nick,
          login,
          realName,
          null,
          null,
          List.of(),
          List.of(),
          new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000),
          IrcProperties.Server.Backend.QUASSEL_CORE);
    }

    RuntimeCoreConfig withPassword(String nextPassword) {
      return new RuntimeCoreConfig(serverId, host, port, tls, login, nextPassword, nick, realName);
    }

    RuntimeCoreConfig withTls(boolean nextTls) {
      return new RuntimeCoreConfig(serverId, host, port, nextTls, login, password, nick, realName);
    }
  }

  private record ContainerConfig(
      boolean enabled,
      String image,
      int containerPort,
      long startupTimeoutSeconds,
      String serverId,
      String login,
      String password,
      String nick,
      String realName) {
    private static final String DEFAULT_IMAGE = "linuxserver/quassel-core:0.14.0";
    private static final int DEFAULT_CONTAINER_PORT = 4242;
    private static final long DEFAULT_STARTUP_TIMEOUT_SECONDS = 180L;
    private static final String DEFAULT_SERVER_ID = "quassel-it-container";
    private static final String DEFAULT_LOGIN = "ircafe-it";
    private static final String DEFAULT_PASSWORD = "ircafe-it-password";
    private static final String DEFAULT_REAL_NAME = "IRCafe IT";

    static ContainerConfig fromSystem() {
      boolean enabled =
          readBoolean("quassel.it.container.enabled", "QUASSEL_IT_CONTAINER_ENABLED", false);
      String image =
          readString("quassel.it.container.image", "QUASSEL_IT_CONTAINER_IMAGE", DEFAULT_IMAGE);
      int containerPort =
          readInt("quassel.it.container.port", "QUASSEL_IT_CONTAINER_PORT", DEFAULT_CONTAINER_PORT);
      long timeoutSeconds =
          readLong(
              "quassel.it.container.startup-timeout-seconds",
              "QUASSEL_IT_CONTAINER_STARTUP_TIMEOUT_SECONDS",
              DEFAULT_STARTUP_TIMEOUT_SECONDS);
      String serverId =
          readString(
              "quassel.it.container.server-id",
              "QUASSEL_IT_CONTAINER_SERVER_ID",
              DEFAULT_SERVER_ID);
      String login =
          readString("quassel.it.container.login", "QUASSEL_IT_CONTAINER_LOGIN", DEFAULT_LOGIN);
      String password =
          readString(
              "quassel.it.container.password", "QUASSEL_IT_CONTAINER_PASSWORD", DEFAULT_PASSWORD);
      String nick = readString("quassel.it.container.nick", "QUASSEL_IT_CONTAINER_NICK", login);
      String realName =
          readString(
              "quassel.it.container.real-name",
              "QUASSEL_IT_CONTAINER_REAL_NAME",
              DEFAULT_REAL_NAME);

      return new ContainerConfig(
          enabled,
          safeTrim(image, DEFAULT_IMAGE),
          containerPort,
          Math.max(30L, timeoutSeconds),
          safeTrim(serverId, DEFAULT_SERVER_ID),
          safeTrim(login, DEFAULT_LOGIN),
          Objects.toString(password, DEFAULT_PASSWORD),
          safeTrim(nick, "ircafe-it"),
          safeTrim(realName, DEFAULT_REAL_NAME));
    }

    RuntimeCoreConfig toRuntimeConfig(String host, int mappedPort) {
      return new RuntimeCoreConfig(
          serverId, host, mappedPort, false, login, password, nick, realName);
    }

    private static String readString(String propName, String envName, String fallback) {
      String prop = System.getProperty(propName);
      if (prop != null) return prop;
      String env = System.getenv(envName);
      if (env != null) return env;
      return fallback;
    }

    private static boolean readBoolean(String propName, String envName, boolean fallback) {
      String raw = readString(propName, envName, Boolean.toString(fallback));
      if (raw == null) return fallback;
      return switch (raw.trim().toLowerCase(Locale.ROOT)) {
        case "1", "true", "yes", "y", "on" -> true;
        case "0", "false", "no", "n", "off" -> false;
        default -> fallback;
      };
    }

    private static int readInt(String propName, String envName, int fallback) {
      String raw = readString(propName, envName, Integer.toString(fallback)).trim();
      if (raw.isEmpty()) return fallback;
      try {
        return Integer.parseInt(raw);
      } catch (NumberFormatException nfe) {
        throw new IllegalArgumentException(
            "Invalid integer for " + propName + "/" + envName + ": '" + raw + "'", nfe);
      }
    }

    private static long readLong(String propName, String envName, long fallback) {
      String raw = readString(propName, envName, Long.toString(fallback)).trim();
      if (raw.isEmpty()) return fallback;
      try {
        return Long.parseLong(raw);
      } catch (NumberFormatException nfe) {
        throw new IllegalArgumentException(
            "Invalid long for " + propName + "/" + envName + ": '" + raw + "'", nfe);
      }
    }

    private static String safeTrim(String value, String fallback) {
      String trimmed = Objects.toString(value, "").trim();
      return trimmed.isEmpty() ? fallback : trimmed;
    }
  }
}
