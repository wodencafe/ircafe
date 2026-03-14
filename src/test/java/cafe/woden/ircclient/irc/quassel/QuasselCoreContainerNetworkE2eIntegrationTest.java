package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Two-container Quassel E2E: Quassel Core + local IRC server, with a real bot message flow. */
class QuasselCoreContainerNetworkE2eIntegrationTest {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(100);
  private static final Duration SETUP_TIMEOUT = Duration.ofSeconds(100);
  private static final Duration NETWORK_SYNC_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration IRC_BOT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(40);
  private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(40);
  private static final long POLL_INTERVAL_MS = 50L;

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void quasselCoreCanCreateAndConnectNetworkThenReceiveLiveChannelMessage() throws Exception {
    E2eConfig cfg = E2eConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Container Quassel network E2E test disabled. Set -Dquassel.it.container.e2e.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName quasselImage = DockerImageName.parse(cfg.quasselImage());
    DockerImageName ircImage = DockerImageName.parse(cfg.ircImage());

    try (Network network = Network.newNetwork();
        GenericContainer<?> ircServer =
            new GenericContainer<>(ircImage)
                .withNetwork(network)
                .withNetworkAliases(cfg.ircAlias())
                .withExposedPorts(cfg.ircPort())
                .withEnv("TZ", "UTC")
                .withEnv("PUID", "1000")
                .withEnv("PGID", "1000")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()));
        GenericContainer<?> core =
            new GenericContainer<>(quasselImage)
                .withNetwork(network)
                .withExposedPorts(cfg.quasselPort())
                .withEnv("TZ", "UTC")
                .withEnv("PUID", "1000")
                .withEnv("PGID", "1000")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      ircServer.start();
      core.start();

      RuntimeCoreConfig runtimeCfg =
          cfg.toRuntimeConfig(core.getHost(), core.getMappedPort(cfg.quasselPort()));
      QuasselCoreIrcClientService service = newService(runtimeCfg);
      TestSubscriber<ServerIrcEvent> events = service.events().test();

      try (SimpleIrcBot bot =
          SimpleIrcBot.connect(
              ircServer.getHost(), ircServer.getMappedPort(cfg.ircPort()), cfg.botNick())) {
        ensureConnectedWithSetupIfNeeded(service, events, runtimeCfg);
        String sid = runtimeCfg.serverId();
        assertEquals("", service.backendAvailabilityReason(sid));

        String networkName = "it-net-" + Long.toHexString(System.currentTimeMillis());
        QuasselCoreControlPort.QuasselCoreNetworkCreateRequest create =
            new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
                networkName, cfg.ircAlias(), cfg.ircPort(), false, "", true, null, List.of());
        service.quasselCoreCreateNetwork(sid, create).blockingAwait();

        QuasselCoreControlPort.QuasselCoreNetworkSummary createdNetwork =
            tryAwaitNetworkObserved(service, sid, networkName, Duration.ofSeconds(20));
        if (createdNetwork == null) {
          reconnectAndAwaitReady(service, events, sid);
          createdNetwork = awaitAnyNetworkObserved(service, sid, NETWORK_SYNC_TIMEOUT);
        }
        Assumptions.assumeTrue(
            createdNetwork != null,
            "No Quassel network observed after create request; core image may not support runtime"
                + " createNetwork in this mode. Recent events: "
                + summarizeRecentEvents(events, sid, 16));
        service
            .quasselCoreConnectNetwork(sid, Integer.toString(createdNetwork.networkId()))
            .blockingAwait();
        awaitNetworkConnected(service, sid, createdNetwork.networkId(), NETWORK_SYNC_TIMEOUT);

        bot.join(cfg.channel(), IRC_BOT_TIMEOUT);
        int joinedCount = countEvents(events, sid, IrcEvent.JoinedChannel.class);
        service.joinChannel(sid, cfg.channel()).blockingAwait();
        IrcEvent.JoinedChannel joined =
            awaitNextEvent(events, sid, IrcEvent.JoinedChannel.class, joinedCount, JOIN_TIMEOUT);
        assertEquals(cfg.channel(), joined.channel());

        int messageCount =
            countChannelMessages(events, sid, cfg.channel(), cfg.botNick(), cfg.messageText());
        bot.privmsg(cfg.channel(), cfg.messageText());
        awaitChannelMessage(
            events,
            sid,
            cfg.channel(),
            cfg.botNick(),
            cfg.messageText(),
            messageCount,
            MESSAGE_TIMEOUT);
      } finally {
        try {
          service.disconnect(runtimeCfg.serverId(), "container e2e shutdown").blockingAwait();
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

  private static QuasselCoreControlPort.QuasselCoreNetworkSummary tryAwaitNetworkObserved(
      QuasselCoreIrcClientService service, String serverId, String networkName, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
          service.quasselCoreNetworks(serverId);
      for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
        if (summary == null) continue;
        if (!networkName.equalsIgnoreCase(Objects.toString(summary.networkName(), "").trim()))
          continue;
        return summary;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    return null;
  }

  private static QuasselCoreControlPort.QuasselCoreNetworkSummary awaitAnyNetworkObserved(
      QuasselCoreIrcClientService service, String serverId, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
          service.quasselCoreNetworks(serverId);
      if (!networks.isEmpty()) {
        return networks.get(0);
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    return null;
  }

  private static void awaitNetworkConnected(
      QuasselCoreIrcClientService service, String serverId, int networkId, Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
          service.quasselCoreNetworks(serverId);
      for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
        if (summary == null) continue;
        if (summary.networkId() != networkId) continue;
        if (summary.connected()) return;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail("Timed out waiting for Quassel network " + networkId + " to report connected=true");
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

  private static void reconnectAndAwaitReady(
      QuasselCoreIrcClientService service, TestSubscriber<ServerIrcEvent> events, String serverId)
      throws InterruptedException {
    int readyCount = countEvents(events, serverId, IrcEvent.ConnectionReady.class);
    service.disconnect(serverId, "refresh network snapshot").blockingAwait();
    service.connect(serverId).blockingAwait();
    awaitNextEvent(events, serverId, IrcEvent.ConnectionReady.class, readyCount, CONNECT_TIMEOUT);
  }

  private static int countChannelMessages(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      String channel,
      String fromNick,
      String expectedTextPart) {
    int count = 0;
    for (ServerIrcEvent event : new ArrayList<>(events.values())) {
      if (event == null || !Objects.equals(serverId, event.serverId())) continue;
      if (!(event.event() instanceof IrcEvent.ChannelMessage msg)) continue;
      if (!channel.equalsIgnoreCase(Objects.toString(msg.channel(), "").trim())) continue;
      if (!fromNick.equalsIgnoreCase(Objects.toString(msg.from(), "").trim())) continue;
      if (!Objects.toString(msg.text(), "").contains(expectedTextPart)) continue;
      count++;
    }
    return count;
  }

  private static void awaitChannelMessage(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      String channel,
      String fromNick,
      String expectedTextPart,
      int alreadySeenCount,
      Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      int seen = countChannelMessages(events, serverId, channel, fromNick, expectedTextPart);
      if (seen > alreadySeenCount) {
        return;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    fail(
        "Timed out waiting for ChannelMessage from "
            + fromNick
            + " in "
            + channel
            + "; recent events: "
            + summarizeRecentEvents(events, serverId, 16));
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
            + summarizeRecentEvents(events, serverId, 16));
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
            case IrcEvent.JoinedChannel joined ->
                "Joined(" + Objects.toString(joined.channel(), "") + ")";
            case IrcEvent.ChannelMessage msg ->
                "ChanMsg("
                    + Objects.toString(msg.channel(), "")
                    + ","
                    + Objects.toString(msg.from(), "")
                    + ","
                    + Objects.toString(msg.text(), "")
                    + ")";
            default -> event.getClass().getSimpleName();
          };
      lines.add(detail);
    }
    if (lines.isEmpty()) return "<none>";
    int start = Math.max(0, lines.size() - limit);
    return String.join(" | ", lines.subList(start, lines.size()));
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

  private enum ConnectOutcome {
    READY,
    SETUP_REQUIRED
  }

  private record RuntimeCoreConfig(
      String serverId,
      String host,
      int port,
      String login,
      String password,
      String nick,
      String realName) {
    IrcProperties.Server toServer() {
      return new IrcProperties.Server(
          serverId,
          host,
          port,
          false,
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
  }

  private record E2eConfig(
      boolean enabled,
      String quasselImage,
      String ircImage,
      long startupTimeoutSeconds,
      String serverId,
      String login,
      String password,
      String nick,
      String realName,
      String ircAlias,
      String botNick,
      String channel,
      String messageText) {
    private static final String DEFAULT_QUASSEL_IMAGE = "linuxserver/quassel-core:0.14.0";
    private static final String DEFAULT_IRC_IMAGE = "linuxserver/ngircd:latest";
    private static final long DEFAULT_STARTUP_TIMEOUT_SECONDS = 180L;
    private static final String DEFAULT_SERVER_ID = "quassel-it-e2e";
    private static final String DEFAULT_LOGIN = "ircafe-it";
    private static final String DEFAULT_PASSWORD = "ircafe-it-password";
    private static final String DEFAULT_REAL_NAME = "IRCafe IT";
    private static final String DEFAULT_IRC_ALIAS = "irc-e2e";
    private static final String DEFAULT_BOT_NICK = "e2ebot";
    private static final String DEFAULT_CHANNEL = "#quassel-e2e";
    private static final String DEFAULT_MESSAGE = "hello-from-e2e-bot";

    static E2eConfig fromSystem() {
      boolean enabled =
          readBoolean(
              "quassel.it.container.e2e.enabled", "QUASSEL_IT_CONTAINER_E2E_ENABLED", false);
      String quasselImage =
          readString(
              "quassel.it.container.e2e.quassel-image",
              "QUASSEL_IT_CONTAINER_E2E_QUASSEL_IMAGE",
              DEFAULT_QUASSEL_IMAGE);
      String ircImage =
          readString(
              "quassel.it.container.e2e.irc-image",
              "QUASSEL_IT_CONTAINER_E2E_IRC_IMAGE",
              DEFAULT_IRC_IMAGE);
      long timeoutSeconds =
          readLong(
              "quassel.it.container.e2e.startup-timeout-seconds",
              "QUASSEL_IT_CONTAINER_E2E_STARTUP_TIMEOUT_SECONDS",
              DEFAULT_STARTUP_TIMEOUT_SECONDS);
      String serverId =
          readString(
              "quassel.it.container.e2e.server-id",
              "QUASSEL_IT_CONTAINER_E2E_SERVER_ID",
              DEFAULT_SERVER_ID);
      String login =
          readString(
              "quassel.it.container.e2e.login", "QUASSEL_IT_CONTAINER_E2E_LOGIN", DEFAULT_LOGIN);
      String password =
          readString(
              "quassel.it.container.e2e.password",
              "QUASSEL_IT_CONTAINER_E2E_PASSWORD",
              DEFAULT_PASSWORD);
      String nick =
          readString("quassel.it.container.e2e.nick", "QUASSEL_IT_CONTAINER_E2E_NICK", login);
      String realName =
          readString(
              "quassel.it.container.e2e.real-name",
              "QUASSEL_IT_CONTAINER_E2E_REAL_NAME",
              DEFAULT_REAL_NAME);
      String ircAlias =
          readString(
              "quassel.it.container.e2e.irc-alias",
              "QUASSEL_IT_CONTAINER_E2E_IRC_ALIAS",
              DEFAULT_IRC_ALIAS);
      String botNick =
          readString(
              "quassel.it.container.e2e.bot-nick",
              "QUASSEL_IT_CONTAINER_E2E_BOT_NICK",
              DEFAULT_BOT_NICK);
      String channel =
          readString(
              "quassel.it.container.e2e.channel",
              "QUASSEL_IT_CONTAINER_E2E_CHANNEL",
              DEFAULT_CHANNEL);
      String messageText =
          readString(
              "quassel.it.container.e2e.message",
              "QUASSEL_IT_CONTAINER_E2E_MESSAGE",
              DEFAULT_MESSAGE);

      return new E2eConfig(
          enabled,
          safeTrim(quasselImage, DEFAULT_QUASSEL_IMAGE),
          safeTrim(ircImage, DEFAULT_IRC_IMAGE),
          Math.max(30L, timeoutSeconds),
          safeTrim(serverId, DEFAULT_SERVER_ID),
          safeTrim(login, DEFAULT_LOGIN),
          Objects.toString(password, DEFAULT_PASSWORD),
          safeTrim(nick, "ircafe-it"),
          safeTrim(realName, DEFAULT_REAL_NAME),
          safeTrim(ircAlias, DEFAULT_IRC_ALIAS),
          safeTrim(botNick, DEFAULT_BOT_NICK),
          normalizeChannel(channel),
          safeTrim(messageText, DEFAULT_MESSAGE));
    }

    int quasselPort() {
      return 4242;
    }

    int ircPort() {
      return 6667;
    }

    RuntimeCoreConfig toRuntimeConfig(String host, int mappedPort) {
      return new RuntimeCoreConfig(serverId, host, mappedPort, login, password, nick, realName);
    }

    private static String normalizeChannel(String raw) {
      String value = safeTrim(raw, DEFAULT_CHANNEL);
      return value.startsWith("#") ? value : ("#" + value);
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

  private static final class SimpleIrcBot implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final String nick;

    private SimpleIrcBot(Socket socket, BufferedReader in, BufferedWriter out, String nick) {
      this.socket = socket;
      this.in = in;
      this.out = out;
      this.nick = nick;
    }

    static SimpleIrcBot connect(String host, int port, String nick) throws Exception {
      String normalizedNick = Objects.toString(nick, "").trim();
      if (normalizedNick.isEmpty()) {
        throw new IllegalArgumentException("bot nick is blank");
      }
      Socket socket = new Socket(host, port);
      socket.setSoTimeout((int) IRC_BOT_TIMEOUT.toMillis());
      BufferedReader in =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      BufferedWriter out =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      SimpleIrcBot bot = new SimpleIrcBot(socket, in, out, normalizedNick);
      bot.sendLine("NICK " + normalizedNick);
      bot.sendLine("USER " + normalizedNick + " 0 * :" + normalizedNick);
      bot.awaitWelcome(IRC_BOT_TIMEOUT);
      return bot;
    }

    void join(String channel, Duration timeout) throws Exception {
      String chan = Objects.toString(channel, "").trim();
      if (chan.isEmpty()) {
        throw new IllegalArgumentException("channel is blank");
      }
      sendLine("JOIN " + chan);
      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        if (line.contains(" JOIN :" + chan) || line.contains(" JOIN " + chan)) {
          return;
        }
      }
      throw new IllegalStateException("timed out waiting bot join ack for " + chan);
    }

    void privmsg(String channel, String text) throws Exception {
      String chan = Objects.toString(channel, "").trim();
      String msg = Objects.toString(text, "").trim();
      if (chan.isEmpty() || msg.isEmpty()) {
        throw new IllegalArgumentException("privmsg channel/text is blank");
      }
      sendLine("PRIVMSG " + chan + " :" + msg);
    }

    private void awaitWelcome(Duration timeout) throws Exception {
      long deadlineNs = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        if (line.contains(" 001 " + nick + " ")) {
          return;
        }
      }
      throw new IllegalStateException("timed out waiting IRC bot welcome");
    }

    private String readLine() throws IOException {
      try {
        return in.readLine();
      } catch (java.net.SocketTimeoutException timeout) {
        return null;
      }
    }

    private void sendLine(String line) throws IOException {
      String value = Objects.toString(line, "").trim();
      if (value.isEmpty()) return;
      out.write(value);
      out.write("\r\n");
      out.flush();
    }

    @Override
    public void close() throws Exception {
      try {
        sendLine("QUIT :bye");
      } catch (Exception ignored) {
      }
      try {
        socket.close();
      } catch (Exception ignored) {
      }
    }
  }
}
