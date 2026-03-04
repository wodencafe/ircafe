package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Real Quassel Core integration coverage.
 *
 * <p>Disabled by default. Enable explicitly with:
 *
 * <pre>
 * ./gradlew integrationTest --tests '*QuasselCoreRealServerIntegrationTest' \
 *   -Dquassel.it.enabled=true \
 *   -Dquassel.it.host=127.0.0.1 \
 *   -Dquassel.it.port=4242 \
 *   -Dquassel.it.login=alice \
 *   -Dquassel.it.password=secret
 * </pre>
 *
 * <p>Environment variable equivalents are also supported:
 *
 * <pre>
 * QUASSEL_IT_ENABLED=true
 * QUASSEL_IT_HOST=127.0.0.1
 * QUASSEL_IT_PORT=4242
 * QUASSEL_IT_LOGIN=alice
 * QUASSEL_IT_PASSWORD=secret
 * </pre>
 */
class QuasselCoreRealServerIntegrationTest {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration LAG_TIMEOUT = Duration.ofSeconds(20);
  private static final long POLL_INTERVAL_MS = 50L;

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void connectAuthSyncHeartbeatDisconnectReconnectAgainstRealCore() throws Exception {
    RealCoreConfig cfg = RealCoreConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Real Quassel Core integration test disabled. Set -Dquassel.it.enabled=true.");
    Assumptions.assumeTrue(
        !cfg.login().isBlank(),
        "Missing Quassel login. Set -Dquassel.it.login or QUASSEL_IT_LOGIN.");

    QuasselCoreIrcClientService service = newService(cfg);
    TestSubscriber<ServerIrcEvent> events = service.events().test();

    try {
      String sid = cfg.serverId();

      int readyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
      service.connect(sid).blockingAwait();
      awaitNextEvent(events, sid, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);
      assertTrue(service.currentNick(sid).isPresent(), "currentNick should be set after connect");
      assertEquals("", service.backendAvailabilityReason(sid));

      service.requestLagProbe(sid).blockingAwait();
      OptionalLong firstLag = awaitLagSample(service, sid, LAG_TIMEOUT);
      assertTrue(firstLag.isPresent(), "expected lag sample after heartbeat probe");
      assertTrue(firstLag.orElseThrow() >= 0L);

      int disconnectedCount = countEvents(events, sid, IrcEvent.Disconnected.class);
      String disconnectReason = "integration test disconnect";
      service.disconnect(sid, disconnectReason).blockingAwait();
      IrcEvent.Disconnected disconnected =
          awaitNextEvent(
              events, sid, IrcEvent.Disconnected.class, disconnectedCount, CONNECT_TIMEOUT);
      assertEquals(disconnectReason, disconnected.reason());
      assertTrue(service.currentNick(sid).isEmpty(), "currentNick should clear on disconnect");
      assertFalse(service.isQuasselCoreSetupPending(sid));

      int reconnectReadyCount = countEvents(events, sid, IrcEvent.ConnectionReady.class);
      service.connect(sid).blockingAwait();
      awaitNextEvent(
          events, sid, IrcEvent.ConnectionReady.class, reconnectReadyCount, CONNECT_TIMEOUT);
      assertTrue(
          service.currentNick(sid).isPresent(), "currentNick should be restored after reconnect");
      assertEquals("", service.backendAvailabilityReason(sid));

      service.requestLagProbe(sid).blockingAwait();
      OptionalLong secondLag = awaitLagSample(service, sid, LAG_TIMEOUT);
      assertTrue(secondLag.isPresent(), "expected lag sample after reconnect");
      assertTrue(secondLag.orElseThrow() >= 0L);
    } finally {
      try {
        service.disconnect(cfg.serverId(), "integration test shutdown").blockingAwait();
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

  private static QuasselCoreIrcClientService newService(RealCoreConfig cfg) {
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
                new IrcProperties.Reconnect(true, 250, 1_000, 1.5, 0, 3),
                null,
                null,
                null),
            List.of(server));

    return new QuasselCoreIrcClientService(
        serverCatalog, socketConnector, protocolProbe, authHandshake, datastreamCodec, props);
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
    fail("Timed out waiting for " + eventType.getSimpleName() + " on server '" + serverId + "'");
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

  private record RealCoreConfig(
      boolean enabled,
      String serverId,
      String host,
      int port,
      boolean tls,
      String login,
      String password,
      String nick,
      String realName) {
    private static final String DEFAULT_SERVER_ID = "quassel-it";
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 4242;
    private static final boolean DEFAULT_TLS = false;

    static RealCoreConfig fromSystem() {
      boolean enabled = readBoolean("quassel.it.enabled", "QUASSEL_IT_ENABLED", false);
      if (!enabled) {
        return new RealCoreConfig(
            false, DEFAULT_SERVER_ID, DEFAULT_HOST, DEFAULT_PORT, DEFAULT_TLS, "", "", "", "");
      }

      String serverId =
          readString("quassel.it.server-id", "QUASSEL_IT_SERVER_ID", DEFAULT_SERVER_ID);
      String host = readString("quassel.it.host", "QUASSEL_IT_HOST", DEFAULT_HOST);
      int port = readInt("quassel.it.port", "QUASSEL_IT_PORT", DEFAULT_PORT);
      boolean tls = readBoolean("quassel.it.tls", "QUASSEL_IT_TLS", DEFAULT_TLS);
      String login = readString("quassel.it.login", "QUASSEL_IT_LOGIN", "");
      String password = readString("quassel.it.password", "QUASSEL_IT_PASSWORD", "");
      String nick = readString("quassel.it.nick", "QUASSEL_IT_NICK", login);
      String realName = readString("quassel.it.real-name", "QUASSEL_IT_REAL_NAME", "IRCafe IT");

      return new RealCoreConfig(
          true,
          safeTrim(serverId, DEFAULT_SERVER_ID),
          safeTrim(host, DEFAULT_HOST),
          port,
          tls,
          safeTrim(login, ""),
          Objects.toString(password, ""),
          safeTrim(nick, "ircafe-it"),
          safeTrim(realName, "IRCafe IT"));
    }

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

    private static String readString(String propName, String envName, String fallback) {
      String prop = System.getProperty(propName);
      if (prop != null) return prop;
      String env = System.getenv(envName);
      if (env != null) return env;
      return fallback;
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

    private static boolean readBoolean(String propName, String envName, boolean fallback) {
      String raw = readString(propName, envName, Boolean.toString(fallback));
      if (raw == null) return fallback;
      return switch (raw.trim().toLowerCase(Locale.ROOT)) {
        case "1", "true", "yes", "y", "on" -> true;
        case "0", "false", "no", "n", "off" -> false;
        default -> fallback;
      };
    }

    private static String safeTrim(String value, String fallback) {
      String trimmed = Objects.toString(value, "").trim();
      return trimmed.isEmpty() ? fallback : trimmed;
    }
  }
}
